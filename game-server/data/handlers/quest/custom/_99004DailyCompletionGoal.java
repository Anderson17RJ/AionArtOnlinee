package quest.custom;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

import static com.aionemu.gameserver.model.DialogAction.*;

public class _99004DailyCompletionGoal extends AbstractQuestHandler {
	
	private static final int QUEST_ID = 99004;
	private static final int[] START_NPC_IDS = {882333, 882334};
	private static final int[] TARGET_WORLD_ID = { 301120000, 300040000 }; // Replace with the map ID that should count kills.
	private static final int REQUIRED_KILLS = 50;
	
	public _99004DailyCompletionGoal() { super(QUEST_ID); }
	
	@Override
	public void register() {
		for (int npcId: START_NPC_IDS) {
			qe.registerQuestNpc(npcId).addOnQuestStart(questId);
			qe.registerQuestNpc(npcId).addOnTalkEvent(questId);
		}
		for (int mapId : TARGET_WORLD_ID) {
			qe.registerOnKillNpcInWorld(mapId, questId);
		}
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
		
		if (qs == null || qs.isStartable()) {
			switch (env.getDialogActionId()) {
				case QUEST_SELECT:
					return sendQuestDialog(env, 4762);
				default: 
					return sendQuestStartDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (env.getDialogActionId() == QUEST_SELECT)
				return sendQuestDialog(env, 2375);
			return sendQuestEndDialog(env);
		}
		return false;
	}
	
	@Override
	public boolean onKillNpcInWorldEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;

		Npc killedNpc = (Npc) env.getVisibleObject();
		if (!killedNpc.isEnemy(player))
			return false;

		int kills = qs.getQuestVarById(1);
		if (kills >= REQUIRED_KILLS)
			return false;

		kills++;
		qs.setQuestVarById(1, kills);
		if (kills == REQUIRED_KILLS) {
			qs.setQuestVarById(0, 1);
			qs.setStatus(QuestStatus.REWARD);
		}
		updateQuestStatus(env);
		return true;
	}
}
