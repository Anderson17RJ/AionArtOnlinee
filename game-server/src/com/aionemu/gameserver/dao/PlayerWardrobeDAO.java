package com.aionemu.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;

/** Persists cosmetic skin template IDs unlocked by a player. */
public final class PlayerWardrobeDAO {

	private static final Logger log = LoggerFactory.getLogger(PlayerWardrobeDAO.class);

	private PlayerWardrobeDAO() {
	}

	public static boolean unlock(int playerId, int skinItemId) {
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(
			"INSERT IGNORE INTO player_wardrobe_skins (player_id, skin_item_id) VALUES (?, ?)")) {
			stmt.setInt(1, playerId);
			stmt.setInt(2, skinItemId);
			return stmt.executeUpdate() == 1;
		} catch (Exception e) {
			log.error("Could not unlock wardrobe skin {} for player {}", skinItemId, playerId, e);
			return false;
		}
	}

	public static Set<Integer> loadUnlockedSkins(int playerId) {
		Set<Integer> skins = new HashSet<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(
			"SELECT skin_item_id FROM player_wardrobe_skins WHERE player_id = ?")) {
			stmt.setInt(1, playerId);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next())
					skins.add(rs.getInt("skin_item_id"));
			}
		} catch (Exception e) {
			log.error("Could not load wardrobe skins for player {}", playerId, e);
		}
		return skins;
	}
}
