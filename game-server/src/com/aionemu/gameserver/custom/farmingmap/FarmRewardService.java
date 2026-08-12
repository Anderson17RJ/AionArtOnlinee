package com.aionemu.gameserver.custom.farmingmap;

import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.instancescore.DarkPoetaScore;
import com.aionemu.gameserver.services.mail.SystemMailService;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FarmRewardService {
	
	private static final FarmRewardService INSTANCE = new FarmRewardService();
	
	private final Map<Integer, Set<Integer>> receivedRewards = new ConcurrentHashMap<>();
	
	public static FarmRewardService getInstance() {
		return INSTANCE;
	}
	
	private FarmRewardService() {
	}
	
	public void checkReward(Player player, DarkPoetaScore score) {
		
		int points = score.getPoints();
		
		if (points >= 100 && !hasReceived(player, 100)) {
			sendReward(player, new RewardItem(186000247, 10));
			addReceived(player, 100);
		}
		
		if (points >= 250 && !hasReceived(player, 250)) {
			sendReward(player, new RewardItem(186000242, 50));
			addReceived(player, 250);
		}
	}
	
	public record RewardItem(int itemId, int count) {
		
	}
	
	private boolean hasReceived(Player player, int points) {
		
		return receivedRewards.getOrDefault(player.getObjectId(), Collections.emptySet()).contains(points);
	}
	
	private void addReceived(Player player, int points) {
		receivedRewards.computeIfAbsent(player.getObjectId(), id -> ConcurrentHashMap.newKeySet()).add(points);
	}
	
	private void sendReward(Player player, RewardItem... rewards) {

		for (RewardItem reward : rewards) {
			SystemMailService.sendMail("Farm Map",
				player.getName(),
				"Farm Map Reward",
				"Congratulations Daeva!\n\nYou have reached the required score in Farm Map.",
				reward.itemId(),
				reward.count(),
				0,
				LetterType.EXPRESS);
		}
	}
	
}
