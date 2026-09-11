package com.aionemu.gameserver.model.gameobjects.player.skill;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aionemu.gameserver.dao.PlayerSkillSkinListDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.SkillSkinTemplate;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.taskmanager.tasks.ExpireTimerTask;

/** Server-side ownership and selection of skill skins. */
public class SkillSkinList {

	private final Map<Integer, SkillSkin> skins = new LinkedHashMap<>();
	private Player owner;

	public void setOwner(Player owner) { this.owner = owner; }
	public boolean contains(int skinId) { return skins.containsKey(skinId); }
	public Collection<SkillSkin> getSkins() { return skins.values(); }
	public int size() { return skins.size(); }

	public void addEntry(int skinId, int expireTime, boolean active) {
		validateTemplate(skinId);
		skins.put(skinId, new SkillSkin(skinId, expireTime, active));
	}

	public boolean addSkin(int skinId, int expireTime) {
		validateTemplate(skinId);
		if (skins.containsKey(skinId))
			return false;
		SkillSkin skin = new SkillSkin(skinId, expireTime, false);
		skins.put(skinId, skin);
		if (owner != null) {
			ExpireTimerTask.getInstance().registerExpirable(skin, owner);
			PlayerSkillSkinListDAO.storeSkin(owner, skin);
		}
		return true;
	}

	public boolean activate(int skinId) {
		SkillSkin selected = skins.get(skinId);
		if (selected == null || selected.isExpired())
			return false;
		SkillSkinTemplate template = validateTemplate(skinId);
		for (SkillSkin skin : skins.values()) {
			SkillSkinTemplate other = validateTemplate(skin.getId());
			if (template.getSkillGroup().equalsIgnoreCase(other.getSkillGroup()))
				skin.setActive(skin.getId() == skinId);
		}
		persist();
		return true;
	}

	public boolean deactivateForSkill(int skillId) {
		SkillTemplate skill = DataManager.SKILL_DATA.getSkillTemplate(skillId);
		if (skill == null || skill.getGroup() == null)
			return false;
		boolean changed = false;
		for (SkillSkin skin : skins.values()) {
			if (skin.isActive() && skill.getGroup().equalsIgnoreCase(validateTemplate(skin.getId()).getSkillGroup())) {
				skin.setActive(false);
				changed = true;
			}
		}
		if (changed)
			persist();
		return changed;
	}

	public int getSkinIdForSkill(int skillId) {
		SkillTemplate skill = DataManager.SKILL_DATA.getSkillTemplate(skillId);
		if (skill == null || skill.getGroup() == null)
			return 0;
		for (SkillSkin skin : skins.values())
			if (skin.isActive() && !skin.isExpired() && skill.getGroup().equalsIgnoreCase(validateTemplate(skin.getId()).getSkillGroup()))
				return skin.getId();
		return 0;
	}

	/**
	 * Returns the client-only clone skill to render, or the original skill id when no skin is active.
	 */
	public int getVisualSkillId(int skillId) {
		int skinId = getSkinIdForSkill(skillId);
		return skinId == 0 ? skillId : validateTemplate(skinId).getVisualSkillId(skillId);
	}

	public void removeSkin(int skinId) {
		if (skins.remove(skinId) != null && owner != null)
			PlayerSkillSkinListDAO.removeSkin(owner.getObjectId(), skinId);
	}

	private SkillSkinTemplate validateTemplate(int skinId) {
		SkillSkinTemplate template = DataManager.SKILL_SKIN_DATA.getSkillSkinTemplate(skinId);
		if (template == null)
			throw new IllegalArgumentException("Invalid skill skin id " + skinId);
		return template;
	}

	private void persist() {
		if (owner != null)
			PlayerSkillSkinListDAO.storeSkins(owner);
	}
}
