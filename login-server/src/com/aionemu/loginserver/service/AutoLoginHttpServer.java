package com.aionemu.loginserver.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.aionemu.loginserver.configs.Config;
import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only loopback API. Production must expose an equivalent endpoint through HTTPS.
 */
public final class AutoLoginHttpServer {

	private static final Logger log = LoggerFactory.getLogger(AutoLoginHttpServer.class);
	private static final int MAX_REQUEST_BYTES = 4096;
	private static HttpServer server;
	private static ExecutorService executor;

	private AutoLoginHttpServer() {
	}

	public static synchronized void start() {
		if (!Config.AUTO_LOGIN_HTTP_ENABLED || server != null) {
			return;
		}
		try {
			InetSocketAddress address = Config.AUTO_LOGIN_HTTP_SOCKET_ADDRESS;
			if (!address.getAddress().isLoopbackAddress()) {
				throw new IllegalStateException("Auto-login development API must bind to a loopback address");
			}
			server = HttpServer.create(address, 0);
			executor = Executors.newCachedThreadPool();
			server.setExecutor(executor);
			server.createContext("/autologin/token", AutoLoginHttpServer::handleTokenRequest);
			server.start();
			log.info("Auto-login development API listening on http://{}:{}/autologin/token", address.getHostString(), address.getPort());
		} catch (IOException e) {
			throw new IllegalStateException("Could not start auto-login development API", e);
		}
	}

	public static synchronized void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	private static void handleTokenRequest(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!"POST".equals(exchange.getRequestMethod())) {
				writeJson(exchange, 405, Map.of("error", "method_not_allowed"));
				return;
			}
			byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
			if (body.length == 0 || body.length > MAX_REQUEST_BYTES) {
				writeJson(exchange, 400, Map.of("error", "invalid_request"));
				return;
			}
			TokenRequest request = JSON.parseObject(new String(body, StandardCharsets.UTF_8), TokenRequest.class);
			if (request == null || request.username() == null || request.password() == null || request.username().isBlank() || request.password().isEmpty()) {
				writeJson(exchange, 400, Map.of("error", "invalid_request"));
				return;
			}
			String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
			String token = AutoLoginTokenService.issue(request.username(), request.password(), clientIp);
			if (token == null) {
				writeJson(exchange, 401, Map.of("error", "invalid_credentials"));
				return;
			}
			writeJson(exchange, 200, Map.of("token", token, "expiresInSeconds", Math.max(5, Config.AUTO_LOGIN_TOKEN_TTL_SECONDS)));
		} catch (Exception e) {
			log.warn("Invalid auto-login token request", e);
			writeJson(exchange, 400, Map.of("error", "invalid_request"));
		}
	}

	private static void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
		byte[] data = JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, data.length);
		exchange.getResponseBody().write(data);
	}

	private record TokenRequest(String username, String password) {
	}
}
