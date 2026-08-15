package playercommands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.aionemu.gameserver.dao.PlayerGearSetDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Equipment;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/** Saves and applies named equipment presets. */
public class Changegear extends PlayerCommand {

	private static final Pattern SET_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

	public Changegear() {
		super("changegear", "Saves and instantly equips a named gear set.");
		setSyntaxInfo("save <set name> - Saves your currently equipped gear to the named set.", "load <set name> - Equips the saved set.");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params.length != 2 || !(params[0].equalsIgnoreCase("save") || params[0].equalsIgnoreCase("load")) || !SET_NAME.matcher(params[1]).matches()) {
			sendInfo(player);
			return;
		}

		String setName = params[1].toLowerCase();
		if (params[0].equalsIgnoreCase("save"))
			save(player, setName);
		else
			load(player, setName);
	}

	private void save(Player player, String setName) {
		Map<Integer, Long> items = new LinkedHashMap<>();
		for (Item item : player.getEquipment().getEquippedItems()) {
			if (item.getItemTemplate().isStigma() || item.getItemTemplate().getItemGroup() == ItemGroup.POWER_SHARDS)
				continue;
			items.put(item.getObjectId(), item.getEquipmentSlot());
		}
		if (items.isEmpty()) {
			sendInfo(player, "You have no gear equipped to save.");
			return;
		}
		if (PlayerGearSetDAO.save(player.getObjectId(), setName, items))
			sendInfo(player, "Gear set '" + setName + "' saved (" + items.size() + " items).");
		else
			sendInfo(player, "Could not save gear set '" + setName + "'.");
	}

	private void load(Player player, String setName) {
		Map<Integer, Long> items = PlayerGearSetDAO.load(player.getObjectId(), setName);
		if (items.isEmpty()) {
			sendInfo(player, "Gear set '" + setName + "' does not exist or is empty.");
			return;
		}
		Equipment.GearSetLoadResult result = player.getEquipment().loadGearSet(items);
		if (result == Equipment.GearSetLoadResult.SUCCESS)
			sendInfo(player, "Gear set '" + setName + "' equipped.");
		else
			sendInfo(player, "Gear set '" + setName + "' was not equipped: " + result.getMessage());
	}
}
