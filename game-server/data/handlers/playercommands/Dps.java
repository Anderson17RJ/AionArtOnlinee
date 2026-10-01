package playercommands;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.services.DpsCountService;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/** Controls the group DPS counter. */
public class Dps extends PlayerCommand {

	public Dps() {
		super("dps", "Liga, desliga ou consulta o contador de DPS do grupo.");
		setSyntaxInfo("<on | off | status> - Apenas o lider do grupo pode ligar ou desligar o contador.");
	}

	@Override
	public void execute(Player player, String... params) {
		PlayerGroup group = player.getPlayerGroup();
		if (group == null) {
			sendInfo(player, "Voce precisa estar em um grupo para usar este comando.");
			return;
		}
		if (params.length != 1) {
			sendInfo(player);
			return;
		}

		if (params[0].equalsIgnoreCase("status")) {
			sendInfo(player, "O contador de DPS do grupo esta " + (group.isDpsCountEnabled() ? "ligado." : "desligado."));
			return;
		}
		if (!group.isLeader(player)) {
			sendInfo(player, "Apenas o lider do grupo pode alterar o contador de DPS.");
			return;
		}

		boolean enabled;
		if (params[0].equalsIgnoreCase("on")) {
			enabled = true;
		} else if (params[0].equalsIgnoreCase("off")) {
			enabled = false;
		} else {
			sendInfo(player);
			return;
		}

		group.setDpsCountEnabled(enabled);
		if (!enabled)
			DpsCountService.getInstance().clearEncounters(group);
		group.sendPackets(new com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE(0, null,
			"O contador de DPS do grupo foi " + (enabled ? "ligado." : "desligado."), com.aionemu.gameserver.model.ChatType.BRIGHT_YELLOW));
	}
}
