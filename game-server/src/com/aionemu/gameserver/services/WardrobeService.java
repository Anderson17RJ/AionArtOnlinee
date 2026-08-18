package com.aionemu.gameserver.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.cache.HTMLCache;
import com.aionemu.gameserver.dao.PlayerWardrobeDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemMask;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.actions.ItemActions;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.model.templates.item.enums.ItemSubType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_UPDATE_PLAYER_APPEARANCE;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Handles wardrobe unlocks and the short-lived HTML selection sessions. */
public final class WardrobeService {

	private static final long SESSION_DURATION_MILLIS = 5 * 60 * 1000L;
	private static final Map<Integer, WardrobeSession> SESSIONS = new ConcurrentHashMap<>();

	private WardrobeService() {
	}

	public static boolean unlockSkin(Player player, Item sourceItem) {
		if (sourceItem == null || !sourceItem.isRemodelable())
			return false;
		ItemTemplate skin = sourceItem.getItemSkinTemplate();
		ItemActions actions = skin.getActions();
		if (sourceItem.isSkinnedItem() && actions != null && actions.getRemodelAction() != null && actions.getRemodelAction().getExtractType() == 2)
			return false;
		if (!PlayerWardrobeDAO.unlock(player.getObjectId(), skin.getTemplateId()))
			return false;
		player.getInventory().decreaseItemCount(sourceItem, 1);
		return true;
	}

	public static void open(Player player) {
		Map<Integer, Integer> targetItems = new HashMap<>();
		for (Item item : player.getEquipment().getEquippedItemsWithoutStigma()) {
			if (item.isRemodelable())
				targetItems.putIfAbsent(item.getItemId(), item.getObjectId());
		}
		if (targetItems.isEmpty()) {
			PacketSendUtility.sendMessage(player, "You have no equipped items that can be remodeled.");
			return;
		}
		sendSelection(player, targetItems, "Wardrobe", "Select the equipped item whose appearance you want to change.", SessionType.TARGET);
	}

	public static void open(Player player, Item targetItem) {
		if (targetItem == null || !targetItem.isEquipped() || !targetItem.isRemodelable()) {
			PacketSendUtility.sendMessage(player, "That equipped item cannot have its appearance changed.");
			return;
		}
		openSkinSelection(player, targetItem);
	}

	public static boolean handleResponse(Player player, int messageId, List<Integer> selectedItemIds) {
		WardrobeSession session = SESSIONS.remove(messageId);
		if (session == null)
			return false;
		if (session.playerId != player.getObjectId() || session.expiresAt < System.currentTimeMillis())
			return true;
		if (selectedItemIds.size() != 1 || !session.options.containsKey(selectedItemIds.getFirst())) {
			PacketSendUtility.sendMessage(player, "Please select exactly one wardrobe option.");
			return true;
		}

		int selection = selectedItemIds.getFirst();
		if (session.type == SessionType.TARGET) {
			Item target = player.getEquipment().getEquippedItemByObjId(session.options.get(selection));
			openSkinSelection(player, target);
		} else {
			applySkin(player, session.targetItemObjId, selection);
		}
		return true;
	}

	private static void openSkinSelection(Player player, Item targetItem) {
		if (targetItem == null || !targetItem.isEquipped() || !targetItem.isRemodelable()) {
			PacketSendUtility.sendMessage(player, "The selected item is no longer equipped.");
			return;
		}
		Map<Integer, Integer> skins = new HashMap<>();
		for (int skinId : PlayerWardrobeDAO.loadUnlockedSkins(player.getObjectId())) {
			ItemTemplate skin = DataManager.ITEM_DATA.getItemTemplate(skinId);
			if (skin != null && isCompatible(targetItem.getItemTemplate(), skin))
				skins.put(skinId, skinId);
		}
		if (skins.isEmpty()) {
			PacketSendUtility.sendMessage(player, "You have no unlocked skins compatible with " + targetItem.getItemTemplate().getL10n() + ".");
			return;
		}
		sendSelection(player, skins, "Wardrobe", "Select an appearance for " + targetItem.getItemTemplate().getL10n() + ".", SessionType.SKIN, targetItem.getObjectId());
	}

	private static void applySkin(Player player, int targetItemObjId, int skinId) {
		Item target = player.getEquipment().getEquippedItemByObjId(targetItemObjId);
		ItemTemplate skin = DataManager.ITEM_DATA.getItemTemplate(skinId);
		if (target == null || skin == null || !target.isRemodelable() || !PlayerWardrobeDAO.loadUnlockedSkins(player.getObjectId()).contains(skinId)
			|| !isCompatible(target.getItemTemplate(), skin)) {
			PacketSendUtility.sendMessage(player, "That wardrobe appearance can no longer be applied.");
			return;
		}
		target.setItemSkinTemplate(skin);
		ItemPacketService.updateItemAfterInfoChange(player, target);
		PacketSendUtility.broadcastPacket(player, new SM_UPDATE_PLAYER_APPEARANCE(player.getObjectId(), player.getEquipment().getEquippedForAppearance()), true);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_CHANGE_ITEM_SKIN_SUCCEED(target.getItemTemplate().getL10n()));
	}

	private static boolean isCompatible(ItemTemplate target, ItemTemplate skin) {
		ItemGroup targetGroup = target.getItemGroup();
		ItemGroup skinGroup = skin.getItemGroup();
		return (skin.getMask() & ItemMask.REMODELABLE) == ItemMask.REMODELABLE
			&& !targetGroup.getItemSubType().equals(ItemSubType.CLOTHES)
			&& (targetGroup == skinGroup || skinGroup.getItemSubType() == ItemSubType.CLOTHES
				|| skinGroup.getItemSubType() == ItemSubType.ALL_ARMOR && targetGroup.getValidEquipmentSlots() == skinGroup.getValidEquipmentSlots());
	}

	private static void sendSelection(Player player, Map<Integer, Integer> options, String title, String description, SessionType type) {
		sendSelection(player, options, title, description, type, 0);
	}

	private static void sendSelection(Player player, Map<Integer, Integer> options, String title, String description, SessionType type, int targetItemObjId) {
		int messageId = IDFactory.getInstance().nextId();
		SESSIONS.put(messageId, new WardrobeSession(player.getObjectId(), type, targetItemObjId, Map.copyOf(options), System.currentTimeMillis() + SESSION_DURATION_MILLIS));
		List<Integer> orderedOptions = new ArrayList<>(options.keySet());
		orderedOptions.sort(Comparator.naturalOrder());
		StringBuilder items = new StringBuilder();
		for (int itemId : orderedOptions)
			items.append("<item_id count='1'>").append(itemId).append("</item_id>\n");
		String html = HTMLCache.getInstance().getHTML("wardrobe.xhtml");
		if (html == null) {
			PacketSendUtility.sendMessage(player, "Wardrobe HTML template is missing.");
			SESSIONS.remove(messageId);
			return;
		}
		html = html.replace("%title%", title).replace("%description%", description).replace("%items%", items);
		HTMLService.sendData(player, messageId, html);
	}

	private enum SessionType {
		TARGET,
		SKIN
	}

	private record WardrobeSession(int playerId, SessionType type, int targetItemObjId, Map<Integer, Integer> options, long expiresAt) {
	}
}
