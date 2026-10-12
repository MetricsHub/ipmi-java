package org.metricshub.ipmi.core.coding.commands.session;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
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
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Get Session Info (IPMI 2.0 section 22.20), looking a session up by its session ID: what the BMC knows of it. The
 * reply names the session when the BMC has it ({@link GetSessionInfoResponseData#isSessionFound()}), and only the
 * session counts when it has not.
 */
public class GetSessionInfo extends IpmiCommandCoder<GetSessionInfoResponseData> {

	/** Session index FFh: look the session up by its session ID (table 22-26). */
	private static final byte LOOKUP_BY_SESSION_ID = (byte) 0xff;
	private static final int SESSION_FOUND_LENGTH = 6;

	private final int sessionId;

	/**
	 * Creates a Get Session Info request for the session with the given session ID.
	 *
	 * @param sessionId the managed system session ID of the session to look up
	 */
	public GetSessionInfo(int sessionId) {
		this.sessionId = sessionId;
	}

	@Override
	public byte getCommandCode() {
		return CommandCodes.GET_SESSION_INFO;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ApplicationRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) {
		byte[] id = TypeConverter.intToLittleEndianByteArray(sessionId);
		byte[] data = { LOOKUP_BY_SESSION_ID, id[0], id[1], id[2], id[3] };
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), data, TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public GetSessionInfoResponseData getResponseData(IpmiMessage message) throws IPMIException {
		byte[] data = validateResponse(message);
		// Session handle, possible active sessions, current active sessions; then, for a session the BMC has, the user
		// ID, the privilege level and the channel (table 22-26). The channel-specific bytes that may follow are not read
		if (data.length < 3) {
			throw new IllegalArgumentException("Invalid response length");
		}
		GetSessionInfoResponseData response = new GetSessionInfoResponseData();
		response.setSessionHandle(TypeConverter.byteToInt(data[0]));
		response.setMaxSessions(TypeConverter.byteToInt(data[1]) & 0x3f);
		response.setActiveSessions(TypeConverter.byteToInt(data[2]) & 0x3f);
		if (data.length >= SESSION_FOUND_LENGTH) {
			response.setSessionFound(true);
			response.setUserId(TypeConverter.byteToInt(data[3]) & 0x3f);
			response.setPrivilegeLevel(TypeConverter.byteToInt(data[4]) & 0x0f);
			response.setChannel(TypeConverter.byteToInt(data[5]) & 0x0f);
		}
		return response;
	}
}
