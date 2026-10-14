package org.metricshub.ipmi.client;

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

import java.time.Duration;
import java.util.Objects;

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Sends any request, like {@code ipmitool raw}: {@code bmc.raw(0x2C, 0x01).data(0xDC, 0x01).execute()} is Get DCMI
 * Capabilities Info. The reply is returned whatever its completion code.
 */
public final class RawRequest {

	/** The last request network function: the odd codes are those of the responses (IPMI 2.0 section 5.1). */
	private static final int LAST_NETWORK_FUNCTION = 0x3E;
	private static final int MAX_BYTE = 0xFF;

	private final IpmiClient client;
	private final int networkFunction;
	private final int command;
	private byte[] data = new byte[0];
	private Duration timeout;

	RawRequest(IpmiClient client, int networkFunction, int command) {
		if (networkFunction < 0 || networkFunction > LAST_NETWORK_FUNCTION || networkFunction % 2 != 0) {
			throw new IllegalArgumentException(
					"A request network function is an even number from 00h to 3Eh: " + networkFunction);
		}
		checkByte(command, "command");
		this.client = client;
		this.networkFunction = networkFunction;
		this.command = command;
	}

	/**
	 * Sets the request data.
	 *
	 * @param bytes the data bytes, each 0 to 255
	 * @return this request
	 * @throws IllegalArgumentException when a byte is out of range
	 */
	public RawRequest data(int... bytes) {
		Objects.requireNonNull(bytes, "bytes");
		byte[] encoded = new byte[bytes.length];
		for (int i = 0; i < bytes.length; i++) {
			checkByte(bytes[i], "data byte");
			encoded[i] = (byte) bytes[i];
		}
		data = encoded;
		return this;
	}

	/**
	 * Overrides the deadline of the client for this request.
	 *
	 * @param deadline the time the request may take, session setup included
	 * @return this request
	 */
	public RawRequest timeout(Duration deadline) {
		this.timeout = IpmiClient.checkTimeout(deadline, "timeout");
		return this;
	}

	/**
	 * Sends the request and waits for its reply. An error completion code is not an exception: it is in the reply.
	 *
	 * @return the reply
	 * @throws IpmiTimeoutException when the BMC does not answer: whether it carried the request out is unknown
	 * @throws IpmiClientException when the session cannot be opened
	 */
	public RawResponse execute() {
		return client.send(new RawCommand(networkFunction, command, data), timeout);
	}

	private static void checkByte(int value, String what) {
		if (value < 0 || value > MAX_BYTE) {
			throw new IllegalArgumentException("A " + what + " is a byte, 0 to 255: " + value);
		}
	}

	/** The request as a command of the core, whose reply is a {@link RawResponse} whatever its completion code. */
	private static final class RawCommand extends IpmiCommandCoder<RawResponse> {

		private final int networkFunction;
		private final byte command;
		private final byte[] data;

		private RawCommand(int networkFunction, int command, byte[] data) {
			this.networkFunction = networkFunction;
			this.command = (byte) command;
			this.data = data;
		}

		@Override
		public int getNetworkFunctionCode() {
			return networkFunction;
		}

		@Override
		public NetworkFunction getNetworkFunction() {
			// Possibly one the enumeration does not list: getNetworkFunctionCode() is what is sent
			return null;
		}

		@Override
		public byte getCommandCode() {
			return command;
		}

		@Override
		protected IpmiLanMessage preparePayload(int sequenceNumber) {
			return new IpmiLanRequest(networkFunction, command, data, TypeConverter.intToByte(sequenceNumber));
		}

		@Override
		public RawResponse getResponseData(IpmiMessage message) {
			// The message handler delivers only the reply under this command and network function
			if (!(message.getPayload() instanceof IpmiLanResponse)) {
				throw new IllegalArgumentException("Invalid response payload");
			}
			IpmiLanResponse response = (IpmiLanResponse) message.getPayload();
			return new RawResponse(response.getCompletionCode(), response.getRawCompletionCode(), response.getData());
		}
	}
}
