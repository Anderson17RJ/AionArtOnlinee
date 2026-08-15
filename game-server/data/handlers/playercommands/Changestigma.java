package playercommands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.aionemu.gameserver.dao.PlayerStigmaSetDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Equipment;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/** Saves and applies named stigma presets. */
public class Changestigma extends PlayerCommand {

	private static final Pattern SET_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

	public Changestigma() {
		super("changestigma", "Saves and equips a named stigma set.");
		setSyntaxInfo("save <set name> - Saves your currently equipped stigmas.", "load <set name> - Equips the saved stigmas and charges the normal Kinah cost.");
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
		for (Item stigma : player.getEquipment().getEquippedItemsAllStigma())
			items.put(stigma.getObjectId(), stigma.getEquipmentSlot());
		if (items.isEmpty()) {
			sendInfo(player, "You have no stigmas equipped to save.");
			return;
		}
		if (PlayerStigmaSetDAO.save(player.getObjectId(), setName, items))
			sendInfo(player, "Stigma set '" + setName + "' saved (" + items.size() + " stigmas).");
		else
			sendInfo(player, "Could not save stigma set '" + setName + "'.");
	}

	private void load(Player player, String setName) {
		Map<Integer, Long> items = PlayerStigmaSetDAO.load(player.getObjectId(), setName);
		if (items.isEmpty()) {
			sendInfo(player, "Stigma set '" + setName + "' does not exist or is empty.");
			return;
		}
		Equipment.StigmaSetLoadResult result = player.getEquipment().loadStigmaSet(items);
		if (result == Equipment.StigmaSetLoadResult.SUCCESS)
			sendInfo(player, "Stigma set '" + setName + "' equipped.");
		else
			sendInfo(player, "Stigma set '" + setName + "' was not equipped: " + result.getMessage());
	}
}
