package org.metricshub.ipmi.core.coding.commands.session;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Wrapper class for Set Session Privilege Level command
 */
public class SetSessionPrivilegeLevel extends IpmiCommandCoder<SetSessionPrivilegeLevelResponseData> {

	/** Completion code: the requested level is not available for this user (IPMI 2.0 table 22-24). */
	public static final int LEVEL_NOT_AVAILABLE = 0x80;

	/** Completion code: the requested level exceeds the privilege limit of the channel or of the user. */
	public static final int LEVEL_ABOVE_LIMIT = 0x81;

	private PrivilegeLevel privilegeLevel;

	/**
	 * Says why the BMC refused the level: the meaning of the command-specific completion codes of Set Session
	 * Privilege Level (IPMI 2.0 table 22-24), or the message of the error reply for the other codes, then the code.
	 *
	 * @param refusal the error reply
	 * @return the reason, ending with the completion code in hexadecimal
	 */
	public static String describeRefusal(IPMIException refusal) {
		String reason;
		switch (refusal.getRawCode()) {
		case LEVEL_NOT_AVAILABLE:
			reason = "the level is not available for this user";
			break;
		case LEVEL_ABOVE_LIMIT:
			reason = "the level exceeds the privilege limit of the channel or of the user";
			break;
		default:
			reason = refusal.getMessage();
			break;
		}
		return reason + String.format(" (completion code %02Xh)", refusal.getRawCode());
	}

	/**
	 * Initiates {@link SetSessionPrivilegeLevel} for encoding and decoding
	 *
	 * @param privilegeLevel
	 *        - Requested {@link PrivilegeLevel} to acquire. Can not be higher than level declared during starting
	 *        session.
	 */
	public SetSessionPrivilegeLevel(PrivilegeLevel privilegeLevel) {
		this.privilegeLevel = privilegeLevel;
	}

	@Override
	public byte getCommandCode() {
		return CommandCodes.SET_SESSION_PRIVILEGE_LEVEL;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ApplicationRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
		byte[] requestData = new byte[1];

		requestData[0] = TypeConverter.intToByte(getRequestedPrivilegeLevelEncoded());

		return new IpmiLanRequest(
				getNetworkFunction(),
				getCommandCode(),
				requestData,
				TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public SetSessionPrivilegeLevelResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {
		validateResponse(message);

		return new SetSessionPrivilegeLevelResponseData();
	}

	private byte getRequestedPrivilegeLevelEncoded() {
		switch (privilegeLevel) {
		case MaximumAvailable:
			return 0;
		case Callback:
			return TypeConverter.intToByte(0x1);
		case User:
			return TypeConverter.intToByte(0x2);
		case Operator:
			return TypeConverter.intToByte(0x3);
		case Administrator:
			return TypeConverter.intToByte(0x4);
		default:
			throw new IllegalArgumentException("Invalid privilege level");
		}
	}
}
