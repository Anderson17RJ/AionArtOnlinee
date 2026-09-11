package com.aionemu.gameserver.model.templates.item.actions;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SkillSkinAction")
public class SkillSkinAction extends AbstractItemAction {

	@XmlAttribute(name = "skin_id", required = true)
	private int skinId;
	@XmlAttribute
	private Integer minutes;

	@Override
	public boolean canAct(Player player, Item parentItem, Item targetItem, Object... params) {
		return parentItem != null && skinId > 0 && !player.getSkillSkinList().contains(skinId);
	}

	@Override
	public void act(Player player, Item parentItem, Item targetItem, Object... params) {
		int expireTime = minutes == null ? 0 : (int) (System.currentTimeMillis() / 1000) + minutes * 60;
		if (!player.getSkillSkinList().addSkin(skinId, expireTime)) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_TOOLTIP_LEARNED_TITLE());
			return;
		}
		player.getSkillSkinList().activate(skinId);
		ItemTemplate itemTemplate = parentItem.getItemTemplate();
		PacketSendUtility.broadcastPacket(player, new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), parentItem.getObjectId(), itemTemplate.getTemplateId()), true);
		player.getInventory().delete(parentItem);
	}
}
