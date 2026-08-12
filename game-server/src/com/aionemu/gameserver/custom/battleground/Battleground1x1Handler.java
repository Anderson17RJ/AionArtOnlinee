package com.aionemu.gameserver.custom.battleground;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aionemu.gameserver.controllers.effect.EffectController;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.CustomPlayerState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_COOLDOWN;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SKILL_COOLDOWN;
import com.aionemu.gameserver.services.StaticDoorService;
import com.aionemu.gameserver.services.abyss.GloryPointsService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.skillengine.model.SkillTargetSlot;
import com.aionemu.gameserver.spawnengine.StaticDoorSpawnManager;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldPosition;

/** Controls one best-of-three duel. */
public final class Battleground1x1Handler extends GeneralInstanceHandler {

	private static final int PREPARATION_SECONDS = 10;
	private static final int ROUND_SECONDS = 180;
	private static final int WINNER_REWARD_ITEM_ID = 186000242; // provisional
	private static final int WINNER_REWARD_COUNT = 2;
	public static final int WINNER_REWARD_GP = 50;
	private static final int LOSER_REWARD_ITEM_ID = 186000242; // provisional
	private static final int LOSER_REWARD_COUNT = 1;
	private static final int LOSER_REWARD_GP = 25;

	private final Player first;
	private final Player second;
	private final Battleground1x1Service.ArenaMap arena;
	private final WorldPosition firstOrigin;
	private final WorldPosition secondOrigin;
	private final List<Future<?>> tasks = new ArrayList<>();
	private volatile int firstWins;
	private volatile int secondWins;
	private int round;
	private boolean started;
	private boolean roundActive;
	private boolean finished;

	public Battleground1x1Handler(WorldMapInstance instance, Player first, Player second, Battleground1x1Service.ArenaMap arena) {
		super(instance);
		this.first = first;
		this.second = second;
		this.arena = arena;
		this.firstOrigin = copyPosition(first);
		this.secondOrigin = copyPosition(second);
	}

	@Override
	public synchronized void onEnterInstance(Player player) {
		if (!started && instance.getPlayersInside().contains(first) && instance.getPlayersInside().contains(second)) {
			started = true;
			prepareInitialEntry(first);
			prepareInitialEntry(second);
			startRound();
		}
	}
	
	@Override
	public synchronized void onInstanceCreate() {
		StaticDoorSpawnManager.spawnTemplate(instance);
	}

	@Override
	public synchronized boolean onDie(Player player, Creature lastAttacker) {
		if (!finished && roundActive && isParticipant(player)) {
			roundActive = false;
			finishRound(opponentOf(player), player, "was defeated");
		}
		return true;
	}

	@Override
	public boolean allowSelfReviveBySkill() {
		return false;
	}

	@Override
	public boolean allowSelfReviveByItem() {
		return false;
	}

	@Override
	public boolean allowInstanceRevive() {
		return false;
	}

	@Override
	public boolean suppressResurrectionOptions() {
		return true;
	}

	@Override
	public synchronized void onPlayerLogout(Player player) {
		forfeit(player, "disconnected");
	}

	@Override
	public synchronized void onLeaveInstance(Player player) {
		if (!finished)
			forfeit(player, "abandoned the battleground");
		super.onLeaveInstance(player);
	}

	@Override
	public synchronized void onInstanceDestroy() {
		finished = true;
		cancelTasks();
		clearBattlegroundState(first);
		clearBattlegroundState(second);
	}

	private void prepareInitialEntry(Player player) {
		removeAllNonPassiveEffects(player);
		resetCooldowns(player);
		restore(player);
		player.setCustomState(CustomPlayerState.ENEMY_OF_ALL_PLAYERS);
	}

	private void startRound() {
		if (finished)
			return;
		round++;
		prepareRoundPlayer(first, arena.firstSpawn());
		prepareRoundPlayer(second, arena.secondSpawn());
		broadcast("[Battleground 1x1] Round " + round + " start in " + PREPARATION_SECONDS + " seconds. Score: " + firstWins + " x " + secondWins);
		final int startingRound = round;
		schedule(() -> {
			if (finished || round != startingRound)
				return;
			setParalyzed(first, false);
			setParalyzed(second, false);
			instance.forEachDoor(door -> door.setOpen(true));
			roundActive = true;
			startRoundTimer();
			broadcast("[Battleground 1x1] Round " + round + " Started! Time: 3 minutes.");
			schedule(() -> onRoundTimeout(startingRound), ROUND_SECONDS, TimeUnit.SECONDS);
		}, PREPARATION_SECONDS, TimeUnit.SECONDS);
	}

	private void prepareRoundPlayer(Player player, WorldPosition spawn) {
		if (player.isDead())
			PlayerReviveService.revive(player, 100, 100, false, 0);
		removeDebuffs(player);
		restore(player);
		setParalyzed(player, true);
		setPetrification(player, false);
		TeleportService.teleportTo(player, instance.getMapId(), instance.getInstanceId(), spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getHeading(), TeleportAnimation.BATTLEGROUND);
	}

	private synchronized void onRoundTimeout(int timedRound) {
		if (finished || !roundActive || round != timedRound)
			return;
		roundActive = false;
		Player winner = first.getLifeStats().getHpPercentage() >= second.getLifeStats().getHpPercentage() ? first : second;
		finishRound(winner, opponentOf(winner), "won by a higher percentage of HP at the end of the time limit");
	}

	private synchronized void finishRound(Player winner, Player loser, String reason) {
		if (finished)
			return;
		stopRoundTimer();
		setPetrification(winner, true);
		setPetrification(loser, true);
		if (winner.equals(first))
			firstWins++;
		else
			secondWins++;
		String result = "was defeated".equals(reason) ? loser.getName() + " was defeated. " + winner.getName() + " won the round."
			: winner.getName() + " " + reason + ".";
		broadcast("[Battleground 1x1] " + result + " Score: " + firstWins + " x " + secondWins);
		if (firstWins == 2 || secondWins == 2 || round == 3) {
			schedule(() -> finishMatch(firstWins >= secondWins ? first : second, firstWins >= secondWins ? second : first), 3, TimeUnit.SECONDS);
			//finishMatch(firstWins >= secondWins ? first : second, firstWins >= secondWins ? second : first);
		} else {
			schedule(this::startRound, 3, TimeUnit.SECONDS);
		}
	}

	private void forfeit(Player player, String reason) {
		if (!finished && isParticipant(player)) {
			roundActive = false;
			finishMatch(opponentOf(player), player);
			broadcast("[Battleground 1x1] " + player.getName() + " " + reason + ".");
		}
	}

	private synchronized void finishMatch(Player winner, Player loser) {
		if (finished)
			return;
		finished = true;
		cancelTasks();
		stopRoundTimer();
		setParalyzed(first, false);
		setParalyzed(second, false);
		broadcast("[Battleground 1x1] " + winner.getName() + " Won Battleground!");
		ItemService.addItem(winner, WINNER_REWARD_ITEM_ID, WINNER_REWARD_COUNT, true);
		ItemService.addItem(loser, LOSER_REWARD_ITEM_ID, LOSER_REWARD_COUNT, true);
		GloryPointsService.addGp(winner.getObjectId(), WINNER_REWARD_GP);
		GloryPointsService.addGp(loser.getObjectId(), LOSER_REWARD_GP);
		schedule(() -> {
			setPetrification(first, false);
			setPetrification(second, false);
			returnPlayer(first, firstOrigin);
			returnPlayer(second, secondOrigin);
			schedule(() -> InstanceService.destroyInstance(instance), 2, TimeUnit.SECONDS);
		}, 3, TimeUnit.SECONDS);
	}

	private void returnPlayer(Player player, WorldPosition origin) {
		clearBattlegroundState(player);
		if (player.isDead())
			PlayerReviveService.revive(player, 100, 100, false, 0);
		TeleportService.teleportTo(player, origin.getMapId(), origin.getInstanceId(), origin.getX(), origin.getY(), origin.getZ(), origin.getHeading(), TeleportAnimation.NONE);
	}

	private void clearBattlegroundState(Player player) {
		player.unsetCustomState(CustomPlayerState.ENEMY_OF_ALL_PLAYERS);
		setParalyzed(player, false);
	}

	private void resetCooldowns(Player player) {
		if (player.getSkillCoolDowns() != null) {
			List<Integer> cooldowns = new ArrayList<>(player.getSkillCoolDowns().keySet());
			player.getSkillCoolDowns().clear();
			PacketSendUtility.sendPacket(player, new SM_SKILL_COOLDOWN(player, cooldowns));
		}
		if (!player.getItemCoolDowns().isEmpty()) {
			player.getItemCoolDowns().clear();
			PacketSendUtility.sendPacket(player, new SM_ITEM_COOLDOWN(player.getItemCoolDowns()));
		}
	}

	private void removeAllNonPassiveEffects(Player player) {
		player.getEffectController().getAllEffects().stream().filter(effect -> !effect.isPassive()).forEach(effect -> effect.endEffect());
	}

	private void removeDebuffs(Player player) {
		player.getEffectController().getAllEffects().stream().filter(effect -> effect.getTargetSlot() == SkillTargetSlot.DEBUFF).forEach(effect -> effect.endEffect());
	}

	private void restore(Player player) {
		player.getLifeStats().setCurrentHpPercent(100);
		player.getLifeStats().setCurrentMpPercent(100);
	}

	private void setParalyzed(Player player, boolean paralyzed) {
		EffectController effects = player.getEffectController();
		if (paralyzed)
			effects.setAbnormal(AbnormalState.PARALYZE);
		else
			effects.unsetAbnormal(AbnormalState.PARALYZE);
		effects.broadCastEffects(null);
		player.getEffectController().updatePlayerEffectIcons(null);
	}
	
	private void setPetrification(Player player, boolean petrified) {
		EffectController effects = player.getEffectController();
		if (petrified)
			effects.setAbnormal(AbnormalState.PETRIFICATION);
		else
			effects.unsetAbnormal(AbnormalState.PETRIFICATION);
		effects.broadCastEffects(null);
		player.getEffectController().updatePlayerEffectIcons(null);
	}

	private void startRoundTimer() {
		PacketSendUtility.broadcastToMap(instance, new SM_QUEST_ACTION(0, ROUND_SECONDS));
	}

	private void stopRoundTimer() {
		PacketSendUtility.broadcastToMap(instance, new SM_QUEST_ACTION(0, 0));
	}

	private void broadcast(String text) {
		PacketSendUtility.broadcastToMap(instance, new SM_MESSAGE(0, null, text, ChatType.BRIGHT_YELLOW_CENTER));
	}

	private void schedule(Runnable task, long delay, TimeUnit unit) {
		tasks.add(ThreadPoolManager.getInstance().schedule(task, delay, unit));
	}

	private void cancelTasks() {
		tasks.forEach(task -> task.cancel(false));
		tasks.clear();
	}
	
	public int getWins(Player player) {
		if (player.equals(first))
			return firstWins;
		if (player.equals(second))
			return secondWins;
		return 0;
	}

	private boolean isParticipant(Player player) {
		return player.equals(first) || player.equals(second);
	}

	private Player opponentOf(Player player) {
		return player.equals(first) ? second : first;
	}

	private static WorldPosition copyPosition(Player player) {
		return new WorldPosition(player.getWorldId(), player.getX(), player.getY(), player.getZ(), player.getHeading(), player.getPosition().getMapRegion());
	}
}
