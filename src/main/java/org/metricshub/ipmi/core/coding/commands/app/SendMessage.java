package org.metricshub.ipmi.core.coding.commands.app;

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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Send Message with response tracking (IPMI 2.0 sections 6.13.3 and 22.7): carries the request of another command to
 * a controller behind the BMC, a satellite management controller on the IPMB for instance, and returns the response
 * of that controller, decoded by that command. {@code new SendMessage<>(0, 0x2C, new GetSensorReading(5, 0))} reads
 * sensor 5 of the controller at slave address 2Ch on the primary IPMB.
 * <p>
 * The BMC answers the Send Message request at once, then forwards the response of the controller under the sequence
 * number of the carried request, which is the one of the Send Message request. Some BMCs put the response of the
 * controller in their response to Send Message instead: both are accepted. A Send Message request that gets no reply
 * says nothing about the session: the controller may be absent or busy.
 * </p>
 *
 * @param <R> the type of the response of the carried command
 */
public class SendMessage<R extends ResponseData> extends IpmiCommandCoder<R> {

	/** Command code of Send Message (IPMI 2.0 table G-1). */
	public static final byte COMMAND_CODE = 0x34;

	/** Bits [7:6] = 01b of the channel byte: the BMC tracks the request to route the response back (table 22-9). */
	private static final int TRACK_REQUEST = 0x40;

	private static final int MAX_CHANNEL = 0x0f;

	private static final int MAX_ADDRESS = 0xff;

	private final int channel;

	private final int address;

	private final IpmiCommandCoder<R> command;

	/**
	 * Creates the command.
	 *
	 * @param channel the channel the controller is on, as its SDR records give it: 0 for the primary IPMB
	 * @param address the slave address of the controller in its 8-bit form (the 7-bit address shifted left, bit 0
	 *        clear), 2Ch for instance
	 * @param command the command to carry to the controller; its LUN is the one it is built with
	 * @throws IllegalArgumentException when the channel is not 0 to 15, or the address is not an even number from 00h
	 *         to FEh (an odd one is a software ID, which no controller answers to)
	 */
	public SendMessage(int channel, int address, IpmiCommandCoder<R> command) {
		if (channel < 0 || channel > MAX_CHANNEL) {
			throw new IllegalArgumentException("Invalid channel number: " + channel);
		}
		if (address < 0 || address > MAX_ADDRESS || (address & 1) != 0) {
			throw new IllegalArgumentException(String.format("Invalid slave address: %02Xh", address));
		}
		this.channel = channel;
		this.address = address;
		this.command = Objects.requireNonNull(command, "command");
	}

	/**
	 * Returns the command carried to the controller.
	 *
	 * @return the command
	 */
	public IpmiCommandCoder<R> getCommand() {
		return command;
	}

	@Override
	public byte getCommandCode() {
		return COMMAND_CODE;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ApplicationRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
		// The IPMB message of table 22-10, addressed to the controller. The requester stays the remote console (81h)
		// with the same sequence number: the BMC saves both to route the response back (section 6.13.4)
		IpmiLanRequest request = encodeRequest(command, sequenceNumber);
		request.setResponderAddress(TypeConverter.intToByte(address));
		byte[] message = request.getPayloadData();
		byte[] data = new byte[1 + message.length];
		data[0] = TypeConverter.intToByte(TRACK_REQUEST | channel);
		System.arraycopy(message, 0, data, 1, message.length);
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), data, TypeConverter.intToByte(sequenceNumber));
	}

	/**
	 * Tells whether a reply ends the exchange: the response of the controller, or the response to Send Message when
	 * the BMC refused to forward the request or put the response of the controller in it. A successful response to
	 * Send Message without data only says that the BMC forwarded the request: the response of the controller follows.
	 *
	 * @param reply the IPMI message received under the tag of this request
	 * @return whether the reply answers this request
	 */
	@Override
	public boolean answers(IpmiLanMessage reply) {
		if (super.answers(reply)) {
			return !(reply instanceof IpmiLanResponse)
					|| ((IpmiLanResponse) reply).getCompletionCode() != CompletionCode.Ok
					|| reply.getData() != null;
		}
		return command.answers(reply);
	}

	@Override
	public R getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
		if (message.getPayload() instanceof IpmiLanMessage && super.answers((IpmiLanMessage) message.getPayload())) {
			// The response to Send Message: an error of the BMC, or the response of the controller in its data
			byte[] embedded = validateResponse(message);
			if (embedded == null) {
				throw new IllegalArgumentException("The response to Send Message carries no response of the controller");
			}
			message.setPayload(new IpmiLanResponse(embedded));
		}
		return command.getResponseData(message);
	}

	/**
	 * Describes a completion code specific to Send Message (IPMI 2.0 table 22-9).
	 *
	 * @param rawCode the completion code byte
	 * @return the description, or null for a code that is not specific to Send Message
	 */
	public static String describeCompletionCode(int rawCode) {
		switch (rawCode) {
		case 0x80:
			return "Invalid session handle";
		case 0x81:
			return "Lost arbitration on the bus of the controller";
		case 0x82:
			return "Bus error on the bus of the controller";
		case 0x83:
			return "NAK on write: no controller acknowledged the address";
		default:
			return null;
		}
	}
}
