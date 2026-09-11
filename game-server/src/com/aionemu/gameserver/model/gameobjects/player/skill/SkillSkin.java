package com.aionemu.gameserver.model.gameobjects.player.skill;

import com.aionemu.gameserver.model.Expirable;
import com.aionemu.gameserver.model.gameobjects.player.Player;

public class SkillSkin implements Expirable {

	private final int id;
	private final int expireTime;
	private boolean active;

	public SkillSkin(int id, int expireTime, boolean active) {
		this.id = id;
		this.expireTime = expireTime;
		this.active = active;
	}

	public int getId() { return id; }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }

	@Override
	public int getExpireTime() { return expireTime; }

	@Override
	public void onExpire(Player player) {
		player.getSkillSkinList().removeSkin(id);
	}
}
