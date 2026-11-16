package com.aionemu.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;

/** Stores references to a player's stigma stones; it never stores or creates items. */
public final class PlayerStigmaSetDAO {

	private static final Logger log = LoggerFactory.getLogger(PlayerStigmaSetDAO.class);
	private static final String DELETE_QUERY = "DELETE FROM player_stigma_sets WHERE player_id = ? AND set_name = ?";
	private static final String INSERT_QUERY = "INSERT INTO player_stigma_sets (player_id, set_name, item_unique_id, item_id, equipment_slot) VALUES (?, ?, ?, ?, ?)";
	private static final String SELECT_QUERY = "SELECT item_unique_id, item_id, equipment_slot FROM player_stigma_sets WHERE player_id = ? AND set_name = ? ORDER BY equipment_slot";

	public record SavedStigma(int itemUniqueId, int itemId, long equipmentSlot) {
	}

	private PlayerStigmaSetDAO() {
	}

	public static boolean save(int playerId, String setName, List<SavedStigma> items) {
		try (Connection con = DatabaseFactory.getConnection()) {
			con.setAutoCommit(false);
			try (PreparedStatement delete = con.prepareStatement(DELETE_QUERY); PreparedStatement insert = con.prepareStatement(INSERT_QUERY)) {
				delete.setInt(1, playerId);
				delete.setString(2, setName);
				delete.executeUpdate();
				for (SavedStigma item : items) {
					insert.setInt(1, playerId);
					insert.setString(2, setName);
					insert.setInt(3, item.itemUniqueId());
					insert.setInt(4, item.itemId());
					insert.setLong(5, item.equipmentSlot());
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
			log.error("Could not save stigma set {} for player {}", setName, playerId, e);
			return false;
		}
	}

	public static List<SavedStigma> load(int playerId, String setName) {
		List<SavedStigma> items = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(SELECT_QUERY)) {
			stmt.setInt(1, playerId);
			stmt.setString(2, setName);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next())
					items.add(new SavedStigma(rs.getInt("item_unique_id"), rs.getInt("item_id"), rs.getLong("equipment_slot")));
			}
		} catch (Exception e) {
			log.error("Could not load stigma set {} for player {}", setName, playerId, e);
		}
		return items;
	}
}
