package ai;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIRequest;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.custom.battleground.Battleground1x1Service;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;

/** Dedicated AI for the temporary 1x1 battleground registration portal. */
@AIName("battleground_portal")
public class BattlegroundPortalAI extends NpcAI {

	public BattlegroundPortalAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleDialogStart(Player player) {
		AIActions.addRequest(this, player, SM_QUESTION_WINDOW.STR_ASK_PASS_BY_SVS_DIRECT_PORTAL, new AIRequest() {

			@Override
			public void acceptRequest(Creature requester, Player responder, int requestId) {
				Battleground1x1Service.getInstance().tryRegister(responder, getOwner());
			}
		});
	}
}
