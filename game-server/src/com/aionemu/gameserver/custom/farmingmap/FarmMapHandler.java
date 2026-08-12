package com.aionemu.gameserver.custom.farmingmap;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.controllers.observer.ActionObserver;
import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.custom.pvpmap.PvpMapService;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.InstanceProgressionType;
import com.aionemu.gameserver.model.instance.instancescore.DarkPoetaScore;
import com.aionemu.gameserver.network.aion.instanceinfo.DarkPoetaScoreWriter;
import com.aionemu.gameserver.network.aion.serverpackets.SM_BIND_POINT_TELEPORT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INSTANCE_SCORE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.PvpService;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.BindPointTeleportService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.spawnengine.StaticDoorSpawnManager;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldPosition;
import com.aionemu.gameserver.world.zone.ZoneInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class FarmMapHandler  extends GeneralInstanceHandler {
	
	private final Map<Integer, WorldPosition> origins = new HashMap<>();
	private final Map<Race, List<WorldPosition>> respawnLocations = new HashMap<>();
	
	private final List<Future<?>> tasks = new ArrayList<>();
	private Future<?> supplyTask, despawnTask;
	private Future<?> instanceTimer;
	private long startTime;
	
	private final Map<Integer, DarkPoetaScore> playerScores = new ConcurrentHashMap<>();
	
	private InstanceProgressionType progressionType;
	
	private static final Logger log = LoggerFactory.getLogger(FarmMapHandler.class);
	
	
	
	public FarmMapHandler(WorldMapInstance instance) { super(instance); }
	
	@Override
	public void onInstanceCreate() {

		SpawnEngine.spawnInstance(instance, (byte) 0, 0);
		
		StaticDoorSpawnManager.spawnTemplate(instance);
		instance.forEachDoor(door -> door.setOpen(true));
		progressionType = InstanceProgressionType.PREPARING;
		addRespawnLocations();
		startTime = System.currentTimeMillis();
		instanceTimer = ThreadPoolManager.getInstance().schedule(() -> onStart(false), 60000);
	}
	
	private void addRespawnLocations() {
		respawnLocations.clear();
		respawnLocations.put(Race.ELYOS, new ArrayList<>());
		respawnLocations.get(Race.ELYOS).add(new WorldPosition(mapId, 1225.9075f, 419.7187f, 140.18988f, (byte) 0));
		
		respawnLocations.put(Race.ASMODIANS, new ArrayList<>());
		//need to do respawns in asmos side
	}	
	
	
	private boolean canJoin(Player p) {
		if (!p.isStaff()) {
			if (p.getLevel() < 60) {
				PacketSendUtility.sendMessage(p, "You not ready to enter in map. need lv 60");
				return false;
			} else if (p.getController().isInCombat()) {
				PacketSendUtility.sendMessage(p, "You cannot enter while in combat.");
				return false;
			}
		}
		return true;
	}
	
	private void startTeleportation(Player p, boolean isLeaving) {
		ActionObserver observer = getAllObserver(p);
		PacketSendUtility.broadcastPacket(p, new SM_BIND_POINT_TELEPORT(1, p.getObjectId(), 1, 0), true);
		p.getObserveController().attach(observer);
		
		p.getController().addTask(TaskId.SKILL_USE, ThreadPoolManager.getInstance().schedule(() -> {
			PacketSendUtility.broadcastPacket(p, new SM_BIND_POINT_TELEPORT(3, p.getObjectId(), 1, 0), true);
			ThreadPoolManager.getInstance().schedule(() -> {
				p.getObserveController().removeObserver(observer);
				p.getController().cancelTask(TaskId.SKILL_USE);
				if (!p.getController().isInCombat() && !p.getLifeStats().isAboutToDie() && !p.isDead()) {
					if (isLeaving) {
						removePlayer(p);
					} else {
						updateOrigin(p);
						instance.register(p.getObjectId());
						WorldPosition pos = Rnd.get(respawnLocations.get(p.getRace()));
						TeleportService.teleportTo(p, instance, pos.getX(), pos.getY(), pos.getZ(), pos.getHeading(), TeleportAnimation.BATTLEGROUND);
					}
				}
			}, 1000);
		}, 10000));
	}
	
	private synchronized void updateOrigin(Player p) {
		origins.put(p.getObjectId(), p.getPosition());
	}
	
	private synchronized void removePlayer(Player p) {
		if (p.isDead())
			revive(p);
		WorldPosition position = origins.remove(p.getObjectId());
		if (position != null)
			TeleportService.moveToBindLocation(p);
	}
	
	private ActionObserver getAllObserver(final Player p) {
		return new ItemUseObserver() {
			@Override
			public void abort() {
				BindPointTeleportService.cancelTeleport(p, 1);
			}
		};
	}
	
	private void revive(Player player) {
		PlayerReviveService.revive(player, 100, 100, false, 0);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REBIRTH_MASSAGE_ME());
		player.getGameStats().updateStatsAndSpeedVisually();
		player.unsetResPosState();
	}
	
	
	public void join(Player p) {
		if (canJoin(p)) {
			startTeleportation(p, false);
		}
	}
	
	public void leave(Player p) {
		if (p.getController().hasScheduledTask(TaskId.SKILL_USE)) {
			PacketSendUtility.sendMessage(p, "You cannot leave the area in your current state.");
			return;
		}
		startTeleportation(p, true);
	}
	
	
	public int getParticipantsSize() {
		int playerCount = 0;
		for (Player p : instance.getPlayersInside()) {
			if (!p.isStaff()) {
				playerCount++;
			}
		}
		return playerCount;
	}
	
	public boolean isOnMap(Creature creature) {
		return instance != null && instance.getObject(creature.getObjectId()) != null;
	}
	
	
	@Override
	public boolean onReviveEvent(Player player) {
		revive(player);
		if (respawnLocations.isEmpty()) {
			if (instance.getPlayer(player.getObjectId()) != null) {
				removePlayer(player);
			}
		} else {
			WorldPosition pos = Rnd.get(respawnLocations.get(player.getRace()));
			TeleportService.teleportTo(player, instance, pos.getX(), pos.getY(), pos.getZ(), pos.getHeading(), TeleportAnimation.BATTLEGROUND);
		}
		return true;
	}
	
	private String getZoneNameL10n(Player player) {
		for (ZoneInstance zone : player.findZones()) {
			int zoneNameL10nId = getZoneNameL10nId(zone.getAreaTemplate().getZoneName().name());
			if (zoneNameL10nId > 0) {
				return ChatUtil.l10n(zoneNameL10nId);
			}
		}
		return null;
	}
	
	private int getZoneNameL10nId(String zoneName) {
		return switch (zoneName) {
			case "ANCILLARY_SENTRY_POST_301220000" -> 404085;
			default -> 0;
		};
	}
	
	private void announceDeath(final Player player) {
		if (!player.isStaff() && player.getAbyssRank() != null) {
			String zoneNameL10n = getZoneNameL10n(player);
			if (zoneNameL10n != null)
				PacketSendUtility.broadcastToMap(instance, SM_SYSTEM_MESSAGE.STR_ABYSS_ORDER_RANKER_DIE(player, zoneNameL10n));
			else
				PacketSendUtility.broadcastToMap(instance, SM_SYSTEM_MESSAGE.STR_ABYSS_ORDER_RANKER_DIE(player));
		}
	}
	
	private void cancelTasks() {
		if (supplyTask != null && !supplyTask.isCancelled()) {
			supplyTask.cancel(true);
		}
		if (despawnTask != null && !despawnTask.isCancelled()) {
			despawnTask.cancel(true);
		}
		tasks.stream().filter(task -> task != null && !task.isCancelled()).forEach(task -> task.cancel(true));
	}
	
	private int getTime() {
		int current = (int) (System.currentTimeMillis() - startTime);
		
		return switch (progressionType) {
			case PREPARING -> 120000 - current;
			case START_PROGRESS, END_PROGRESS -> 14400000 - current;
			default -> 0;
		};
	}
	
	private void sendPacket(Player player, Npc npc, int points) {
		
		if (player == null)
			return;
		
		if (npc != null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_GET_SCORE(npc.getObjectTemplate().getL10n(), points));
		}
		
		DarkPoetaScore score = getPlayerScore(player);
		
		PacketSendUtility.sendPacket(player, new SM_INSTANCE_SCORE(instance.getMapId(), new DarkPoetaScoreWriter(score), getTime()));
	}
	
	private int checkRank(int totalPoints) {
		int timeRemain = getTime();
		int rank = 8;
		if (timeRemain > 7200000 && totalPoints >= 17817) {
			rank = 1;
		} else if (timeRemain > 5400000 && totalPoints >= 15219) {
			rank = 2;
		} else if (timeRemain > 3600000 && totalPoints > 10913) {
			rank = 3;
		} else if (timeRemain > 1800000 && totalPoints > 6656) {
			rank = 4;
		} else if (timeRemain > 1) {
			rank = 5;
		}
		return rank;
	}
	
	private int calculatePointsReward(Npc npc) {
		int pointsReward = 0;
		
		switch (npc.getObjectTemplate().getRating()) {
			case HERO:
				switch (npc.getObjectTemplate().getHpGauge()) {
					case 21:
						pointsReward = 786;
						break;
					default:
						pointsReward = 300;
				}
				break;
			default:
				if (npc.getObjectTemplate().getRace() == null) {
					break;
				}
				
				switch (npc.getObjectTemplate().getRace().getRaceId()) {
					case 22:
						pointsReward = 12;
						break;
					case 9:
						pointsReward = 18;
						break;
					case 6:
						pointsReward = 240;
						break;
					case 8:
					case 18:
					case 24:
						pointsReward = 500;
						break;
					default:
						if (npc.getNpcId() != 281178)
							pointsReward = 11;
						break;
				}
		}
		
		// Special npcs
		switch (npc.getNpcId()) {
			case 700520:
				pointsReward = 52;
				break;
			case 700517:
			case 700518:
			case 700556:
			case 700558:
				pointsReward = 156;
				break;
			case 214885:
				pointsReward = 21;
				break;
			case 214841:
				pointsReward = -209;
				break;
			case 281116:
				pointsReward = 1241;
				break;
			case 215431:
				pointsReward = 208;
				break;
			case 215429:
			case 215430:
				pointsReward = 190;
				break;
			case 214842:
			case 215432:
				pointsReward = 357;
				break;
			case 214871:
			case 215386:
			case 215428:
				pointsReward = 204;
				break;
			case 214849:
			case 214850:
			case 214851:
				pointsReward = 377;
				break;
			case 214897:
				pointsReward = 330;
				break;
			case 214843:
				pointsReward = 456;
				break;
			case 214864:
			case 214880:
			case 214894:
			case 215387:
			case 215388:
			case 215389:
				pointsReward = 789;
				break;
			case 214904:
				pointsReward = 954;
				break;
		}
		return pointsReward;
	}
	
	private void onStart(boolean manually) {
		playerScores.values().forEach(score -> score.setInstanceProgressionType(InstanceProgressionType.START_PROGRESS));
		
		startTime = System.currentTimeMillis();
		
		progressionType = InstanceProgressionType.START_PROGRESS;
		
		instance.forEachPlayer(player -> sendPacket(player, null, 0));
		
		if (!manually)
			instance.forEachDoor(d -> d.setOpen(true));
	}
	
	private DarkPoetaScore getPlayerScore(Player player) {
		return playerScores.computeIfAbsent(
			player.getObjectId(),
			id -> {
				DarkPoetaScore score = new DarkPoetaScore();
				score.setInstanceProgressionType(InstanceProgressionType.PREPARING);
				return score;
			}
		);
	}
	
	@Override
	public boolean onDie(Player player, Creature lastAttacker) {
		PvpService.getInstance().doReward(player, CustomConfig.PVP_MAP_AP_MULTIPLIER);
		announceDeath(player);
		return true;
	}
	
	@Override
	public void onDie(Npc npc) {
		Creature master = npc.getMaster();
		if (master instanceof Player)
			return;
		
		Player killer = npc.getAggroList().getMostPlayerDamage();
		
		if (killer == null)
			return;
		
		DarkPoetaScore playerScore = getPlayerScore(killer);
		
		int npcId = npc.getNpcId();
		int points = calculatePointsReward(npc);
		
		if (progressionType.isStartProgress()) {
			playerScore.addNpcKill();
			playerScore.addPoints(points);
			
			FarmRewardService.getInstance().checkReward(killer, playerScore);
			
			sendPacket(killer, npc, points);
		}
	}
	
	@Override
	public void onEnterInstance(Player player) {
		
		getPlayerScore(player);
		
		instance.forEachPlayer(p -> {
			if (!p.equals(player))
				PacketSendUtility.sendMessage(p, "A new player has joined!", ChatType.BRIGHT_YELLOW);
		});
		
		PacketSendUtility.broadcastToWorld(new SM_MESSAGE(0, null, "An player has entered on DarkPoeta Map", ChatType.BRIGHT_YELLOW), p -> p.getLevel() >= 60 && !p.isInInstance() && p.getRace() != player.getRace());
		
		sendPacket(player, null, 0);
	}
	
	@Override
	public void onLeaveInstance(Player player) {
		super.onLeaveInstance(player);
		playerScores.remove(player.getObjectId());
	}
	
	@Override
	public void onInstanceDestroy() {
		FarmMapService.getInstance().onInstanceDestroy();
		cancelTasks();
	}
	
}
