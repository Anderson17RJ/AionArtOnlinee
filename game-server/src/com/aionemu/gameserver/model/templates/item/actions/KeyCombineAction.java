package com.aionemu.gameserver.model.templates.item.actions;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.utils.PacketSendUtility;

public class KeyCombineAction extends AbstractItemAction {
	
	private static final int KEY_ITEM_ID1 = 185000114;
	private static final int KEY_ITEM_ID2 = 185000115;
	private static final int KEY_ITEM_ID3 = 185000116;
	private static final int REQUIRED_COUNT = 3;
	private static final int QUESTION_ID = 906490;
	private static final float KEY_COMBINE_CHANCE = 40.0f;
	
	@Override
	public boolean canAct(Player player, Item parentItem, Item targetItem, Object... params) {
		int sourceItemId = parentItem.getItemId();
		
		if (sourceItemId != KEY_ITEM_ID1 && sourceItemId != KEY_ITEM_ID2) {
			return false;
		}
		
		long count = player.getInventory().getItemCountByItemId(sourceItemId);
		
		if (count < REQUIRED_COUNT) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_ITEM_IS_NOT_USABLE());
			return false;
		}

		RequestResponseHandler<Creature> requestResponseHandler = new RequestResponseHandler<Creature>(null) {
			@Override
			public void acceptRequest(Creature requester, Player responder) {
				int sourceItemId = parentItem.getItemId();
				
				int resultItemId;
				boolean success = true;
				
				if (sourceItemId == KEY_ITEM_ID1) {
					resultItemId = KEY_ITEM_ID2;
				} else if (sourceItemId == KEY_ITEM_ID2) {
					resultItemId = KEY_ITEM_ID3;
					success = Rnd.chance() < KEY_COMBINE_CHANCE;
				} else {
					return;
				}
				
				long currentCount = responder.getInventory().getItemCountByItemId(sourceItemId);
				
				if (currentCount < REQUIRED_COUNT) {
					PacketSendUtility.sendPacket(responder, SM_SYSTEM_MESSAGE.STR_ITEM_IS_NOT_USABLE());
					return;
				}
				
				if (!responder.getInventory().decreaseByItemId(sourceItemId, REQUIRED_COUNT)) {
					return;
				}
				
				if (success) {
					ItemService.addItem(responder, resultItemId, 1, false);
				}
				
			}
			
			@Override
			public void denyRequest(Creature requester, Player responder) {
				
			}
		};
		
		if (player.getResponseRequester().putRequest(QUESTION_ID, requestResponseHandler)) {
			PacketSendUtility.sendPacket(player, new SM_QUESTION_WINDOW(QUESTION_ID, 0, 0));
		}
		
		return false;
	}
	
	@Override
	public void act(Player player, Item parentItem, Item targetItem, Object...params) {
		
	}
}
