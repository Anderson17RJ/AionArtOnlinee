package com.aionemu.gameserver.services.webshop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.dao.MailDAO;
import com.aionemu.gameserver.dao.WebshopDeliveryDAO;
import com.aionemu.gameserver.dao.WebshopDeliveryDAO.Delivery;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.services.mail.SystemMailService;
import com.aionemu.gameserver.services.player.PlayerService;

public final class WebshopDeliveryService {

	private static final Logger log = LoggerFactory.getLogger("WEBSHOP_AUDIT_LOG");
	private static final WebshopDeliveryService INSTANCE = new WebshopDeliveryService();

	private WebshopDeliveryService() {
	}

	public static WebshopDeliveryService getInstance() {
		return INSTANCE;
	}

	public Result deliver(String orderId, Integer playerId, String characterName, int itemId, long quantity) {
		if (orderId == null || !orderId.matches("[A-Za-z0-9._:-]{1,64}") || quantity <= 0 || itemId <= 0)
			return Result.invalid("invalid order or item data");
		if (playerId == null && (characterName == null || characterName.isBlank()))
			return Result.invalid("player_id or character_name is required");

		PlayerCommonData player = playerId != null ? PlayerService.getOrLoadPlayerCommonData(playerId) : PlayerService.getOrLoadPlayerCommonData(characterName);
		if (player == null)
			return Result.invalid("character not found");
		if (playerId != null && characterName != null && !characterName.isBlank() && !player.getName().equals(characterName))
			return Result.invalid("player_id and character_name do not match");
		if (DataManager.ITEM_DATA.getItemTemplate(itemId) == null)
			return Result.invalid("item not found");
		if (quantity > 2_000_000_000L)
			return Result.invalid("quantity is too large");

		String resolvedName = player.getName();
		Delivery delivery = WebshopDeliveryDAO.loadOrCreate(orderId, player.getPlayerObjId(), resolvedName, itemId, quantity);
		if (delivery == null)
			return Result.failed("delivery storage unavailable");
		if (delivery.playerId() != player.getPlayerObjId() || delivery.itemId() != itemId || delivery.quantity() != quantity
			|| !delivery.characterName().equals(resolvedName))
			return Result.conflict("order_id already exists with different payload");
		if ("DELIVERED".equals(delivery.status()))
			return Result.delivered("already delivered");

		return process(delivery);
	}

	public void reconcile(PlayerCommonData player) {
		if (player == null)
			return;
		for (Delivery delivery : WebshopDeliveryDAO.loadRecoverable(player.getPlayerObjId()))
			process(delivery);
	}

	private Result process(Delivery delivery) {
		String marker = marker(delivery.orderId());
		if (MailDAO.hasSystemMailMarker(delivery.playerId(), marker)) {
			WebshopDeliveryDAO.markDelivered(delivery.orderId());
			return Result.delivered("mail already exists");
		}

		long timeout = Math.max(30, GSConfig.WEBSHOP_DELIVERY_PROCESSING_TIMEOUT_SECONDS) * 1000L;
		if (!WebshopDeliveryDAO.claim(delivery, timeout))
			return Result.processing("delivery is already being processed");

		try {
			boolean sent = SystemMailService.sendMail("$$WEBSHOP", delivery.characterName(), "Webshop delivery", marker, delivery.itemId(), delivery.quantity(), 0,
				LetterType.BLACKCLOUD);
			if (sent || MailDAO.hasSystemMailMarker(delivery.playerId(), marker)) {
				WebshopDeliveryDAO.markDelivered(delivery.orderId());
				log.info("order_id={} player_id={} item_id={} quantity={} status=DELIVERED", delivery.orderId(), delivery.playerId(), delivery.itemId(), delivery.quantity());
				return Result.delivered("delivered");
			}
		} catch (RuntimeException e) {
			log.error("Failed to deliver webshop order " + delivery.orderId(), e);
		}
		WebshopDeliveryDAO.markFailed(delivery.orderId());
		log.warn("order_id={} player_id={} item_id={} quantity={} status=FAILED", delivery.orderId(), delivery.playerId(), delivery.itemId(), delivery.quantity());
		return Result.failed("delivery failed");
	}

	private static String marker(String orderId) {
		return "WEBSHOP_ORDER:" + orderId;
	}

	public record Result(String status, String message) {
		public static Result delivered(String message) { return new Result("DELIVERED", message); }
		public static Result processing(String message) { return new Result("PROCESSING", message); }
		public static Result invalid(String message) { return new Result("INVALID", message); }
		public static Result conflict(String message) { return new Result("CONFLICT", message); }
		public static Result failed(String message) { return new Result("FAILED", message); }
	}
}
