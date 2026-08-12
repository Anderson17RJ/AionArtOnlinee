package com.aionemu.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;

public final class WebshopDeliveryDAO {

	private static final Logger log = LoggerFactory.getLogger(WebshopDeliveryDAO.class);

	private WebshopDeliveryDAO() {
	}

	public static Delivery loadOrCreate(String orderId, int playerId, String characterName, int itemId, long quantity) {
		String insert = "INSERT INTO webshop_deliveries (order_id, player_id, character_name, item_id, quantity, status, created_at, updated_at) "
			+ "VALUES (?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE order_id=order_id";
		String select = "SELECT order_id, player_id, character_name, item_id, quantity, status, updated_at FROM webshop_deliveries WHERE order_id=?";
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement insertStmt = con.prepareStatement(insert); PreparedStatement selectStmt = con.prepareStatement(select)) {
			insertStmt.setString(1, orderId);
			insertStmt.setInt(2, playerId);
			insertStmt.setString(3, characterName);
			insertStmt.setInt(4, itemId);
			insertStmt.setLong(5, quantity);
			insertStmt.executeUpdate();
			selectStmt.setString(1, orderId);
			try (ResultSet rs = selectStmt.executeQuery()) {
				if (!rs.next())
					return null;
				return read(rs);
			}
		} catch (SQLException e) {
			log.error("Could not load webshop delivery " + orderId, e);
			return null;
		}
	}

	public static boolean claim(Delivery delivery, long processingTimeoutMillis) {
		String sql = "UPDATE webshop_deliveries SET status='PROCESSING', updated_at=CURRENT_TIMESTAMP "
			+ "WHERE order_id=? AND (status='PENDING' OR status='FAILED' OR (status='PROCESSING' AND updated_at < ?))";
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(sql)) {
			stmt.setString(1, delivery.orderId());
			stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis() - processingTimeoutMillis));
			return stmt.executeUpdate() == 1;
		} catch (SQLException e) {
			log.error("Could not claim webshop delivery " + delivery.orderId(), e);
			return false;
		}
	}

	public static boolean markDelivered(String orderId) {
		return updateStatus(orderId, "DELIVERED");
	}

	public static boolean markFailed(String orderId) {
		return updateStatus(orderId, "FAILED");
	}

	private static boolean updateStatus(String orderId, String status) {
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(
			"UPDATE webshop_deliveries SET status=?, updated_at=CURRENT_TIMESTAMP, delivered_at=IF(?='DELIVERED', CURRENT_TIMESTAMP, delivered_at) WHERE order_id=?")) {
			stmt.setString(1, status);
			stmt.setString(2, status);
			stmt.setString(3, orderId);
			return stmt.executeUpdate() == 1;
		} catch (SQLException e) {
			log.error("Could not set webshop delivery " + orderId + " to " + status, e);
			return false;
		}
	}

	public static List<Delivery> loadRecoverable(int playerId) {
		List<Delivery> result = new ArrayList<>();
		String sql = "SELECT order_id, player_id, character_name, item_id, quantity, status, updated_at FROM webshop_deliveries "
			+ "WHERE player_id=? AND status IN ('PENDING','PROCESSING','FAILED') ORDER BY created_at";
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement stmt = con.prepareStatement(sql)) {
			stmt.setInt(1, playerId);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next())
					result.add(read(rs));
			}
		} catch (SQLException e) {
			log.error("Could not load recoverable webshop deliveries for player " + playerId, e);
		}
		return result;
	}

	private static Delivery read(ResultSet rs) throws SQLException {
		return new Delivery(rs.getString("order_id"), rs.getInt("player_id"), rs.getString("character_name"), rs.getInt("item_id"),
			rs.getLong("quantity"), rs.getString("status"), rs.getTimestamp("updated_at"));
	}

	public record Delivery(String orderId, int playerId, String characterName, int itemId, long quantity, String status, Timestamp updatedAt) {
	}
}
