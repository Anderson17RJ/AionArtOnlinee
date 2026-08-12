package com.aionemu.gameserver.custom.battleground;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;



import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldPosition;

/** Automatic 1x1 battleground queue. Map, portal and reward values are intentionally provisional. */
public final class Battleground1x1Service {

	public static final int PORTAL_NPC_ID = 205437;
	private static final String PORTAL_AI_NAME = "battleground_portal";
	private static final long REGISTRATION_INTERVAL_MINUTES = 1;
	private static final long REGISTRATION_INTERVAL_HOURS = 1;
	private static final long REGISTRATION_DURATION_SECONDS = 30;
	private static final long REGISTRATION_DURATION_MINUTES = 30;
	private static final long MATCHMAKING_INTERVAL_SECONDS = 30;
	private Future<?> matchmakingTask;
	
	private static final List<ArenaMap> ARENA_MAPS = List.of(
		new ArenaMap(300520000, 
			new WorldPosition(300520000, 503, 563, 417, (byte) 90), 
			new WorldPosition(300520000, 504, 466, 417, (byte) 30)),
		new ArenaMap(301110000, 
			new WorldPosition(301110000, 240, 278, 241, (byte) 102),
			new WorldPosition(301110000, 275, 238, 241, (byte) 43)),
		new ArenaMap(300520000, 
			new WorldPosition(300520000, 503, 488, 240, (byte) 30),
			new WorldPosition(300520000, 503, 544, 240, (byte) 89)),
		new ArenaMap(310080000,
			new WorldPosition(310080000, 232, 240, 158, (byte) 0),
			new WorldPosition(310080000, 319.0f, 240.0f, 159.0f, (byte) 59)));
	public static final int TEST_ARENA_INDEX = -1;
	private static final PortalLocation ELYOS_PORTAL = new PortalLocation(110010000, 1439, 1490, 573, (byte) 11);
	private static final PortalLocation ASMODIAN_PORTAL = new PortalLocation(120010000, 1628, 1384, 193, (byte) 45);
	
	String sanctum = ChatUtil.position("Sanctum", ELYOS_PORTAL.mapId, ELYOS_PORTAL.x, ELYOS_PORTAL.y, ELYOS_PORTAL.z);
	String panda = ChatUtil.position("Pandaemonium", ASMODIAN_PORTAL.mapId, ASMODIAN_PORTAL.x, ASMODIAN_PORTAL.y, ASMODIAN_PORTAL.z);
	

	private final List<Player> queue = new ArrayList<>();
	private final Map<Integer, Npc> portals = new ConcurrentHashMap<>();
	private boolean registrationOpen;
	private Future<?> nextRegistrationTask;
	private final List<Future<?>> tasks = new ArrayList<>();

	private Battleground1x1Service() {
	}

	public static Battleground1x1Service getInstance() {
		return Holder.INSTANCE;
	}

	public synchronized void init() {
		if (nextRegistrationTask == null)
			scheduleRegistrationOpen(REGISTRATION_INTERVAL_MINUTES, TimeUnit.MINUTES);
	}

	public synchronized boolean tryRegister(Player player, Npc npc) {
		if (!portals.containsKey(npc.getObjectId()))
			return false;
		if (!registrationOpen) {
			PacketSendUtility.sendMessage(player, "Registration for the 1x1 Battleground is closed.");
			return true;
		}
		if (player.isInInstance() || player.isDead()) {
			PacketSendUtility.sendMessage(player, "You cannot join the queue in your current state.");
			return true;
		}
		if (player.isInGroup() || player.isInAlliance() || player.isInLeague()) {
			PacketSendUtility.sendMessage(player, "You cannot join the queue in group/alliance.");
			return true;
		}
		if (queue.stream().anyMatch(p -> p.getObjectId() == player.getObjectId())) {
			PacketSendUtility.sendMessage(player, "You are already registered for the 1x1 Battleground.");
			return true;
		}
		queue.add(player);
		PacketSendUtility.sendMessage(player, "Registration completed for the 1v1 Battleground.", ChatType.BRIGHT_YELLOW_CENTER);
		return true;
	}

	private synchronized void scheduleRegistrationOpen(long delay, TimeUnit unit) {
		nextRegistrationTask = ThreadPoolManager.getInstance().schedule(this::openRegistration, delay, unit);
	}

	private synchronized void openRegistration() {
		registrationOpen = true;
		spawnPortals();
		PacketSendUtility.broadcastToWorld(new SM_MESSAGE(0, null,
			"[Battleground 1x1]Registration is open for 30 minutes. " + sanctum + " | " + panda, ChatType.BRIGHT_YELLOW_CENTER));
		matchmakingTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(
			this::createQueueMatches,
			MATCHMAKING_INTERVAL_SECONDS * 1000,
			MATCHMAKING_INTERVAL_SECONDS * 1000
		);
		ThreadPoolManager.getInstance().schedule(this::closeRegistration, REGISTRATION_DURATION_MINUTES, TimeUnit.MINUTES);
	}
	
	private synchronized void createQueueMatches() {
		queue.removeIf(player -> !player.isOnline() || player.isDead() || player.isInInstance());
		
		Collections.shuffle(queue);
		
		while (queue.size() >= 2) {
			Player first = queue.removeFirst();
			Player second = queue.removeFirst();
			createMatch(first, second);
		}
	}

	private synchronized void closeRegistration() {
		registrationOpen = false;
		if (matchmakingTask != null) {
			matchmakingTask.cancel(false);
			matchmakingTask = null;
		}
		despawnPortals();
		List<Player> participants = new ArrayList<>(queue);
		queue.clear();
		participants.removeIf(p -> !p.isOnline() || p.isDead() || p.isInInstance());
		Collections.shuffle(participants);
		for (int i = 0; i + 1 < participants.size(); i += 2)
			createMatch(participants.get(i), participants.get(i + 1));
		if (participants.size() % 2 != 0)
			PacketSendUtility.sendMessage(participants.getLast(), "No opponent was found for you in this round of 1v1 Battleground.", ChatType.BRIGHT_YELLOW_CENTER);
		scheduleRegistrationOpen(REGISTRATION_INTERVAL_HOURS, TimeUnit.HOURS);
	}

	private void createMatch(Player first, Player second) {
		//ArenaMap arena = Rnd.get(ARENA_MAPS);
		ArenaMap arena = TEST_ARENA_INDEX < 0 ? Rnd.get(ARENA_MAPS) : ARENA_MAPS.get(TEST_ARENA_INDEX);
		WorldMapInstance instance = InstanceService.getNextAvailableInstance(arena.mapId(), 0, (byte) 0,
			wmi -> new Battleground1x1Handler(wmi, first, second, arena), 2, false);
		instance.register(first.getObjectId());
		instance.register(second.getObjectId());
		TeleportService.teleportTo(first, instance.getMapId(), instance.getInstanceId(), arena.firstSpawn().getX(), arena.firstSpawn().getY(), arena.firstSpawn().getZ(),
			arena.firstSpawn().getHeading(), TeleportAnimation.BATTLEGROUND);
		TeleportService.teleportTo(second, instance.getMapId(), instance.getInstanceId(), arena.secondSpawn().getX(), arena.secondSpawn().getY(), arena.secondSpawn().getZ(),
			arena.secondSpawn().getHeading(), TeleportAnimation.BATTLEGROUND);			
	}

	private void schedule(Runnable task, long delay, TimeUnit unit) {
		tasks.add(ThreadPoolManager.getInstance().schedule(task, delay, unit));
	}

	private void spawnPortals() {
		spawnPortal(ELYOS_PORTAL);
		spawnPortal(ASMODIAN_PORTAL);
	}

	private void spawnPortal(PortalLocation location) {
		VisibleObject object = SpawnEngine.spawnObject(
			SpawnEngine.newSingleTimeSpawn(location.mapId(), PORTAL_NPC_ID, location.x(), location.y(), location.z(), location.heading(), null, PORTAL_AI_NAME), 1);
		if (object instanceof Npc npc)
			portals.put(npc.getObjectId(), npc);
	}

	private void despawnPortals() {
		portals.values().forEach(npc -> npc.getController().deleteIfAliveOrCancelRespawn());
		portals.clear();
	}

	public record ArenaMap(int mapId, WorldPosition firstSpawn, WorldPosition secondSpawn) {
	}

	private record PortalLocation(int mapId, float x, float y, float z, byte heading) {
	}

	private static class Holder {
		private static final Battleground1x1Service INSTANCE = new Battleground1x1Service();
	}
}
