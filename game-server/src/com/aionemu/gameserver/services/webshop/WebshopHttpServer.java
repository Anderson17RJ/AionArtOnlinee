package com.aionemu.gameserver.services.webshop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.aionemu.gameserver.configs.main.GSConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public final class WebshopHttpServer {

	private static final Logger log = LoggerFactory.getLogger("WEBSHOP_SECURITY_LOG");
	private static final String PATH = "/internal/webshop/deliver";
	private static HttpServer server;
	private static ExecutorService executor;

	private WebshopHttpServer() {
	}

	public static synchronized void start() {
		if (!GSConfig.WEBSHOP_API_ENABLE)
			return;
		if (GSConfig.WEBSHOP_API_SECRET == null || GSConfig.WEBSHOP_API_SECRET.length() < 32)
			throw new IllegalStateException("Webshop API secret must contain at least 32 characters");
		try {
			server = HttpServer.create(new InetSocketAddress(GSConfig.WEBSHOP_API_BIND_ADDRESS, GSConfig.WEBSHOP_API_PORT), 16);
			server.createContext(PATH, new DeliveryHandler());
			executor = Executors.newFixedThreadPool(4, runnable -> {
				Thread thread = new Thread(runnable, "webshop-api");
				thread.setDaemon(true);
				return thread;
			});
			server.setExecutor(executor);
			server.start();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not start webshop API", e);
		}
	}

	public static synchronized void stop() {
		if (server != null) {
			server.stop(1);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	private static final class DeliveryHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			try {
				if (!exchange.getRequestURI().getPath().equals(PATH) || !isAllowed(exchange.getRemoteAddress()) || !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
					log.warn("Rejected webshop request from {}", exchange.getRemoteAddress());
					send(exchange, 405, "{\"status\":\"REJECTED\"}");
					return;
				}
				byte[] body = readBody(exchange.getRequestBody());
				String signature = exchange.getRequestHeaders().getFirst("X-Webshop-Signature");
				if (!validSignature(body, signature)) {
					log.warn("Rejected webshop HMAC from {}", exchange.getRemoteAddress());
					send(exchange, 401, "{\"status\":\"UNAUTHORIZED\"}");
					return;
				}
				Map<String, String> fields = parseForm(new String(body, StandardCharsets.UTF_8));
				String orderId = required(fields, "order_id");
				String characterName = fields.get("character_name");
				Integer playerId = optionalInt(fields.get("player_id"));
				int itemId = Integer.parseInt(required(fields, "item_id"));
				long quantity = Long.parseLong(required(fields, "quantity"));
				WebshopDeliveryService.Result result = WebshopDeliveryService.getInstance().deliver(orderId, playerId, characterName, itemId, quantity);
				send(exchange, statusCode(result.status()), "{\"status\":\"" + escape(result.status()) + "\",\"message\":\"" + escape(result.message()) + "\"}");
			}
			catch (IllegalArgumentException e) {
				send(exchange, 400, "{\"status\":\"INVALID\",\"message\":\"invalid payload\"}");
			}
			catch (Exception e) {
				send(exchange, 500, "{\"status\":\"FAILED\"}");
			}
			finally {
				exchange.close();
			}
		}
	}

	private static boolean isAllowed(InetSocketAddress remote) {
		String address = remote.getAddress().getHostAddress();
		for (String allowed : GSConfig.WEBSHOP_API_ALLOWED_ADDRESSES.split(","))
			if (address.equals(allowed.trim()))
				return true;
		return false;
	}

	private static byte[] readBody(InputStream input) throws IOException {
		if (input.available() > 8192)
			throw new IllegalArgumentException("payload too large");
		java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int count;
		int total = 0;
		while ((count = input.read(buffer)) != -1) {
			total += count;
			if (total > 8192)
				throw new IllegalArgumentException("payload too large");
			output.write(buffer, 0, count);
		}
		return output.toByteArray();
	}

	private static boolean validSignature(byte[] body, String supplied) {
		if (supplied == null || supplied.length() != 64)
			return false;
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(GSConfig.WEBSHOP_API_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] expected = mac.doFinal(body);
			byte[] actual = hexToBytes(supplied);
			return MessageDigest.isEqual(expected, actual);
		}
		catch (Exception e) {
			return false;
		}
	}

	private static byte[] hexToBytes(String value) {
		byte[] result = new byte[value.length() / 2];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
		return result;
	}

	private static Map<String, String> parseForm(String body) {
		Map<String, String> fields = new HashMap<>();
		for (String pair : body.split("&", -1)) {
			String[] parts = pair.split("=", 2);
			if (parts.length != 2)
				throw new IllegalArgumentException("invalid field");
			String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
			String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
			if (fields.putIfAbsent(key, value) != null)
				throw new IllegalArgumentException("duplicate field");
		}
		return fields;
	}

	private static String required(Map<String, String> fields, String key) {
		String value = fields.get(key);
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("missing field");
		return value;
	}

	private static Integer optionalInt(String value) {
		return value == null || value.isBlank() ? null : Integer.valueOf(value);
	}

	private static int statusCode(String status) {
		return switch (status) {
			case "DELIVERED" -> 200;
			case "PROCESSING" -> 202;
			case "CONFLICT" -> 409;
			case "INVALID" -> 400;
			default -> 500;
		};
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private static void send(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}
}
