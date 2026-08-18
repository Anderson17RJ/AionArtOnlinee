package playercommands;

import java.util.List;

import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.WardrobeService;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

public class Wardrobe extends PlayerCommand {

	public Wardrobe() {
		super("wardrobe", "Opens your collection of unlocked appearances.");
		setSyntaxInfo(" - Select an equipped item and then an unlocked appearance.", "unlock <item> - Consumes an inventory item and permanently unlocks its appearance.",
			"<equipped item> - Opens the appearance selection directly for that item.");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params.length == 0) {
			WardrobeService.open(player);
			return;
		}
		if (params.length == 2 && params[0].equalsIgnoreCase("unlock")) {
			int skinItemId = ChatUtil.getItemId(params[1]);
			Item source = player.getInventory().getFirstItemByItemId(skinItemId);
			if (!WardrobeService.unlockSkin(player, source))
				sendInfo(player, "That item cannot be unlocked, or its appearance is already in your wardrobe.");
			else
				sendInfo(player, "Appearance unlocked and source item consumed.");
			return;
		}
		if (params.length != 1) {
			sendInfo(player);
			return;
		}
		int itemId = ChatUtil.getItemId(params[0]);
		if (itemId == 0) {
			sendInfo(player);
			return;
		}
		List<Item> items = player.getEquipment().getEquippedItemsByItemId(itemId);
		if (items.size() != 1) {
			sendInfo(player, items.isEmpty() ? "That item is not equipped." : "More than one matching item is equipped. Use .wardrobe and select the item in the HTML.");
			return;
		}
		WardrobeService.open(player, items.getFirst());
	}
}
