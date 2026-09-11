package com.aionemu.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DB;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.database.ParamReadStH;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.skill.SkillSkin;
import com.aionemu.gameserver.model.gameobjects.player.skill.SkillSkinList;

public class PlayerSkillSkinListDAO {

	private static final Logger log = LoggerFactory.getLogger(PlayerSkillSkinListDAO.class);
	private static final String LOAD_QUERY = "SELECT skin_id, expire_time, active FROM player_skill_skins WHERE player_id=?";
	private static final String UPSERT_QUERY = "INSERT INTO player_skill_skins (player_id, skin_id, expire_time, active) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE expire_time=VALUES(expire_time), active=VALUES(active)";
	private static final String DELETE_QUERY = "DELETE FROM player_skill_skins WHERE player_id=? AND skin_id=?";

	public static SkillSkinList loadSkillSkinList(int playerId) {
		SkillSkinList result = new SkillSkinList();
		DB.select(LOAD_QUERY, new ParamReadStH() {
			@Override public void setParams(PreparedStatement stmt) throws SQLException { stmt.setInt(1, playerId); }
			@Override public void handleRead(ResultSet rs) throws SQLException {
				while (rs.next())
					result.addEntry(rs.getInt("skin_id"), rs.getInt("expire_time"), rs.getBoolean("active"));
			}
		});
		return result;
	}

	public static void storeSkin(Player player, SkillSkin skin) {
		store(player.getObjectId(), skin);
	}

	public static void storeSkins(Player player) {
		for (SkillSkin skin : player.getSkillSkinList().getSkins())
			store(player.getObjectId(), skin);
	}

	private static void store(int playerId, SkillSkin skin) {
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(UPSERT_QUERY)) {
			stmt.setInt(1, playerId);
			stmt.setInt(2, skin.getId());
			stmt.setInt(3, skin.getExpireTime());
			stmt.setBoolean(4, skin.isActive());
			stmt.executeUpdate();
		} catch (SQLException e) {
			log.error("Could not store skill skin {} for player {}", skin.getId(), playerId, e);
		}
	}

	public static void removeSkin(int playerId, int skinId) {
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(DELETE_QUERY)) {
			stmt.setInt(1, playerId);
			stmt.setInt(2, skinId);
			stmt.executeUpdate();
		} catch (SQLException e) {
			log.error("Could not remove skill skin {} for player {}", skinId, playerId, e);
		}
	}
}
