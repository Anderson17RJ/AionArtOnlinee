package quest.custom;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * Monthly quest that is completed by finishing a configured daily quest repeatedly.
 *
 * Replace the placeholder constants before enabling this handler.
 */
public class _99003MonthlyDailyCompletionGoal extends AbstractQuestHandler {

	private static final int QUEST_ID = 99003;
	private static final int[] START_NPC_IDS = {882333, 882334};
	private static final int DAILY_QUEST_ID = 99004; // Disabled temporarily for the client-dialog test.
	private static final int REQUIRED_DAILY_COMPLETIONS = 20; // TODO: replace with the required monthly total

	public _99003MonthlyDailyCompletionGoal() {
		super(QUEST_ID);
	}

	@Override
	public void register() {
		for (int npcId: START_NPC_IDS) {
			qe.registerQuestNpc(npcId).addOnQuestStart(questId);
			qe.registerQuestNpc(npcId).addOnTalkEvent(questId);
		}
		qe.registerOnQuestCompleted(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);

		boolean isStartNpc = false;
		for (int npcId : START_NPC_IDS) {
			if (env.getTargetId() == npcId) {
				isStartNpc = true;
				break;
			}
		}

		if (!isStartNpc)
			return false;

		if (env.getDialogActionId() == USE_OBJECT) {
			// The client's NPC quest list is built from SM_NEARBY_QUESTS. Make sure
			// this quest is indexed even when the handler was loaded/reloaded after
			// the NPC had already spawned in the map instance.
			player.getPosition().getMapRegion().getParent().getQuestIds().add(questId);
			player.getController().updateNearbyQuests();
		}

		if (qs == null || qs.isStartable()) {
			switch (env.getDialogActionId()) {
				case QUEST_SELECT:
					return sendQuestDialog(env, 4762);
				case ASK_QUEST_ACCEPT:
				case QUEST_ACCEPT:
				case QUEST_ACCEPT_1:
				case QUEST_ACCEPT_SIMPLE:
					return sendQuestStartDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			return sendQuestEndDialog(env);
		}
		return false;
	}

	@Override
	public void onQuestCompletedEvent(QuestEnv env) {
		if (env.getQuestId() != DAILY_QUEST_ID)
			return;

		QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return;

		int completions = qs.getQuestVarById(1);
		if (completions >= REQUIRED_DAILY_COMPLETIONS)
			return;

		int next = completions + 1;
		qs.setQuestVarById(1, next);
		if (next >= REQUIRED_DAILY_COMPLETIONS)
			qs.setStatus(QuestStatus.REWARD);

		updateQuestStatus(env);
	}
}
