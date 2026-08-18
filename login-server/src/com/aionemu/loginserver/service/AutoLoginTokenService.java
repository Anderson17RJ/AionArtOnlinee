package com.aionemu.loginserver.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.loginserver.configs.Config;
import com.aionemu.loginserver.controller.AccountController;

/**
 * Issues opaque, single-use tokens for the launcher. Tokens are held only in memory and are invalidated on restart.
 */
public final class AutoLoginTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

	private AutoLoginTokenService() {
	}

	public static String issue(String username, String password, String ipAddress) {
		String accountName = AccountController.validateAutoLoginCredentials(username, password, ipAddress);
		if (accountName == null) {
			return null;
		}

		removeExpired();
		byte[] randomBytes = new byte[32];
		RANDOM.nextBytes(randomBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		Instant expiresAt = Instant.now().plus(Duration.ofSeconds(Math.max(5, Config.AUTO_LOGIN_TOKEN_TTL_SECONDS)));
		tokens.put(token, new TokenEntry(accountName, expiresAt));
		return token;
	}

	/**
	 * Atomically consumes a token. It can never be reused, including after expiry.
	 */
	public static String consume(String token) {
		if (token == null || token.length() > 128) {
			return null;
		}
		TokenEntry entry = tokens.remove(token);
		if (entry == null || !entry.expiresAt().isAfter(Instant.now())) {
			return null;
		}
		return entry.accountName();
	}

	private static void removeExpired() {
		Instant now = Instant.now();
		tokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
	}

	private record TokenEntry(String accountName, Instant expiresAt) {
	}
}
