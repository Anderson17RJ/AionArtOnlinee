package com.aionemu.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;

/** Stores references to a player's equipment; it never stores or creates items. */
public final class PlayerGearSetDAO {

	private static final Logger log = LoggerFactory.getLogger(PlayerGearSetDAO.class);
	private static final String DELETE_QUERY = "DELETE FROM player_gear_sets WHERE player_id = ? AND set_name = ?";
	private static final String INSERT_QUERY = "INSERT INTO player_gear_sets (player_id, set_name, item_unique_id, equipment_slot) VALUES (?, ?, ?, ?)";
	private static final String SELECT_QUERY = "SELECT item_unique_id, equipment_slot FROM player_gear_sets WHERE player_id = ? AND set_name = ? ORDER BY equipment_slot";

	private PlayerGearSetDAO() {
	}

	public static boolean save(int playerId, String setName, Map<Integer, Long> items) {
		try (Connection con = DatabaseFactory.getConnection()) {
			con.setAutoCommit(false);
			try (PreparedStatement delete = con.prepareStatement(DELETE_QUERY); PreparedStatement insert = con.prepareStatement(INSERT_QUERY)) {
				delete.setInt(1, playerId);
				delete.setString(2, setName);
				delete.executeUpdate();
				for (Map.Entry<Integer, Long> item : items.entrySet()) {
					insert.setInt(1, playerId);
					insert.setString(2, setName);
					insert.setInt(3, item.getKey());
					insert.setLong(4, item.getValue());
					insert.addBatch();
				}
				insert.executeBatch();
				con.commit();
				return true;
			} catch (Exception e) {
				con.rollback();
				throw e;
			}
		} catch (Exception e) {
			log.error("Could not save gear set {} for player {}", setName, playerId, e);
			return false;
		}
	}

	public static Map<Integer, Long> load(int playerId, String setName) {
		Map<Integer, Long> items = new LinkedHashMap<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(SELECT_QUERY)) {
			stmt.setInt(1, playerId);
			stmt.setString(2, setName);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next())
					items.put(rs.getInt("item_unique_id"), rs.getLong("equipment_slot"));
			}
		} catch (Exception e) {
			log.error("Could not load gear set {} for player {}", setName, playerId, e);
		}
		return items;
	}
}
