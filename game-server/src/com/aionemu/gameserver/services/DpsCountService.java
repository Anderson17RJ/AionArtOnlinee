package com.aionemu.gameserver.services;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;

/**
 * Tracks the damage caused by a group to a dungeon boss for the duration of one encounter.
 */
public class DpsCountService {

	private final Map<Integer, BossDpsEncounter> encounters = new ConcurrentHashMap<>();

	private DpsCountService() {
	}

	public void onDamage(Creature target, Creature attacker, int damage, boolean targetDied) {
		if (!isTrackedBoss(target))
			return;

		int bossObjectId = target.getObjectId();
		BossDpsEncounter encounter = encounters.get(bossObjectId);
		Player player = resolvePlayer(attacker);
		if (player != null && damage > 0) {
			PlayerGroup group = player.getPlayerGroup();
			if (encounter == null && group != null && group.isDpsCountEnabled()) {
				BossDpsEncounter newEncounter = new BossDpsEncounter((Npc) target, group);
				BossDpsEncounter previousEncounter = encounters.putIfAbsent(bossObjectId, newEncounter);
				encounter = previousEncounter == null ? newEncounter : previousEncounter;
			}
			if (encounter != null && encounter.belongsTo(group))
				encounter.addDamage(player, damage);
		}

		if (targetDied && encounter != null && encounters.remove(bossObjectId, encounter))
			sendResult(encounter);
	}

	/** Removes unfinished encounters when the group leader disables the feature. */
	public void clearEncounters(PlayerGroup group) {
		encounters.entrySet().removeIf(entry -> entry.getValue().belongsTo(group));
	}

	/** Discards an encounter when its boss resets or despawns before dying. */
	public void clearEncounter(Creature target) {
		if (isTrackedBoss(target))
			encounters.remove(target.getObjectId());
	}

	private boolean isTrackedBoss(Creature target) {
		return GroupConfig.DPS_COUNT_ENABLED && target instanceof Npc npc && npc.isBoss()
			&& npc.getPosition().getWorldMapInstance().getTemplate().isInstance();
	}

	private Player resolvePlayer(Creature attacker) {
		Creature master = attacker.getMaster();
		return master instanceof Player player ? player : null;
	}

	private void sendResult(BossDpsEncounter encounter) {
		String result = encounter.formatResult();
		for (Integer playerId : encounter.getRecipientIds()) {
			Player player = World.getInstance().getPlayer(playerId);
			if (player != null)
				PacketSendUtility.sendMessage(player, result, ChatType.BRIGHT_YELLOW);
		}
	}

	public static DpsCountService getInstance() {
		return SingletonHolder.instance;
	}

	private static class SingletonHolder {

		private static final DpsCountService instance = new DpsCountService();
	}

	private static class BossDpsEncounter {

		private final PlayerGroup group;
		private final String bossName;
		private final long startedAt = System.currentTimeMillis();
		private final Map<Integer, PlayerDpsEntry> entries = new HashMap<>();
		private final List<Integer> recipientIds;

		private BossDpsEncounter(Npc boss, PlayerGroup group) {
			this.group = group;
			this.bossName = boss.getName();
			this.recipientIds = group.getMembers().stream().map(Player::getObjectId).toList();
		}

		private boolean belongsTo(PlayerGroup group) {
			return this.group == group;
		}

		private synchronized void addDamage(Player player, int damage) {
			entries.computeIfAbsent(player.getObjectId(), ignored -> new PlayerDpsEntry(player.getName())).damage += damage;
		}

		private List<Integer> getRecipientIds() {
			return recipientIds;
		}

		private synchronized String formatResult() {
			long totalDamage = entries.values().stream().mapToLong(entry -> entry.damage).sum();
			if (totalDamage == 0)
				return "DPS Count: nenhum dano valido foi registrado para " + bossName + ".";

			long durationSeconds = Math.max(1, (System.currentTimeMillis() - startedAt) / 1000);
			List<PlayerDpsEntry> ranking = new ArrayList<>(entries.values());
			ranking.sort(Comparator.comparingLong((PlayerDpsEntry entry) -> entry.damage).reversed());

			NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.of("pt", "BR"));
			StringBuilder result = new StringBuilder();
			result.append("========== DPS COUNT ==========")
				.append("\nBoss: ").append(bossName)
				.append("\nTime: ").append(formatDuration(durationSeconds))
				.append("\nTotal Damage: ").append(numberFormat.format(totalDamage));
			for (int rank = 0; rank < ranking.size(); rank++) {
				PlayerDpsEntry entry = ranking.get(rank);
				long dps = entry.damage / durationSeconds;
				double percentage = entry.damage * 100d / totalDamage;
				result.append("\n").append(rank + 1).append(". ").append(entry.name)
					.append(" - ").append(numberFormat.format(entry.damage))
					.append(" (").append(String.format(Locale.US, "%.1f", percentage)).append("%)")
					.append(" | ").append(numberFormat.format(dps)).append(" DPS");
			}
			return result.append("\n================================").toString();
		}

		private String formatDuration(long seconds) {
			return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
		}
	}

	private static class PlayerDpsEntry {

		private final String name;
		private long damage;

		private PlayerDpsEntry(String name) {
			this.name = name;
		}
	}
}
