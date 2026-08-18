package com.aionemu.loginserver.network.aion.clientpackets;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.loginserver.controller.AccountController;
import com.aionemu.loginserver.network.aion.AionAuthResponse;
import com.aionemu.loginserver.network.aion.AionClientPacket;
import com.aionemu.loginserver.network.aion.LoginConnection;
import com.aionemu.loginserver.network.aion.LoginConnection.State;
import com.aionemu.loginserver.network.aion.SessionKey;
import com.aionemu.loginserver.network.aion.serverpackets.SM_LOGIN_FAIL;
import com.aionemu.loginserver.network.aion.serverpackets.SM_LOGIN_OK;
import com.aionemu.loginserver.service.AutoLoginTokenService;

/**
 * Launcher-provided single-use token. Wire format: opcode 0x0B followed by an ASCII base64url token.
 */
public final class CM_EXTERNAL_TOKEN_LOGIN extends AionClientPacket {

	private static final Logger log = LoggerFactory.getLogger(CM_EXTERNAL_TOKEN_LOGIN.class);
	// AutoLoginTokenService creates 32 random bytes as unpadded base64url.
	// This representation is always 43 ASCII bytes. The native client packet
	// writer adds encrypted alignment/checksum bytes after the payload.
	private static final int TOKEN_LENGTH = 43;

	private String token;

	public CM_EXTERNAL_TOKEN_LOGIN(ByteBuffer buf, LoginConnection client, int opCode) {
		super(buf, client, opCode);
	}

	@Override
	protected void readImpl() {
		if (getRemainingBytes() < TOKEN_LENGTH) {
			return;
		}
		byte[] payload = readB(TOKEN_LENGTH);
		// Consume encrypted alignment/checksum bytes as well. They are not part
		// of the token, but leaving them in the packet buffer produces a noisy
		// "was not fully read" warning in the dispatcher.
		if (getRemainingBytes() > 0) {
			readB(getRemainingBytes());
		}
		for (byte value : payload) {
			if (!((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9') || value == '-' || value == '_')) {
				return;
			}
		}
		token = new String(payload, StandardCharsets.US_ASCII);
	}

	@Override
	protected void runImpl() {
		if (token == null) {
			log.warn("Rejected malformed external-token login packet from {}", getConnection().getIP());
			sendPacket(new SM_LOGIN_FAIL(AionAuthResponse.STR_L2AUTH_S_SYSTEM_ERROR));
			return;
		}

		String accountName = AutoLoginTokenService.consume(token);
		if (accountName == null) {
			log.info("Rejected expired or already-used external login token from {}", getConnection().getIP());
			sendPacket(new SM_LOGIN_FAIL(AionAuthResponse.STR_L2AUTH_S_INVALID_ACCOUT));
			return;
		}

		LoginConnection client = getConnection();
		AionAuthResponse response = AccountController.loginWithAutoLoginToken(accountName, client);
		if (response == null) {
			return;
		}
		if (response == AionAuthResponse.STR_L2AUTH_S_ALL_OK) {
			log.info("Accepted external-token login for {}", accountName);
			client.setState(State.AUTHED_LOGIN);
			client.setSessionKey(new SessionKey(client.getAccount()));
			client.sendPacket(new SM_LOGIN_OK(client.getSessionKey()));
		} else {
			client.sendPacket(new SM_LOGIN_FAIL(response));
		}
	}
}
