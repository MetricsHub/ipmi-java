package org.metricshub.ipmi.core.coding.commands;

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

import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * A wrapper for an IPMI command: encodes its request and decodes its response, of type {@code R}. The session the
 * command is sent in sets its IPMI version, cipher suite and authentication type
 * ({@link #setSessionParameters(IpmiVersion, CipherSuite, AuthenticationType)}) when it encodes the request.
 *
 * @param <R> the type of the response data of the command
 */
public abstract class IpmiCommandCoder<R extends ResponseData> extends PayloadCoder {

	/**
	 * Creates the command. The session that sends it sets its IPMI version, cipher suite and authentication type.
	 */
	protected IpmiCommandCoder() {}

	@Override
	public PayloadType getSupportedPayloadType() {
		return PayloadType.Ipmi;
	}

	@Override
	public abstract R getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException;

	/**
	 * Checks if given message contains response command specific for this
	 * class.
	 *
	 * @param message {@link IpmiMessage} wrapping the IPMI message
	 * @return True if message contains response command specific for this
	 *         class, false otherwise.
	 */
	public boolean isCommandResponse(IpmiMessage message) {
		if (message.getPayload() instanceof IpmiLanResponse) {
			return ((IpmiLanResponse) message.getPayload()).getCommand() == getCommandCode();
		}
		return message.getPayload() instanceof PlainMessage;
	}

	/**
	 * Tells whether a reply received under the tag of this request answers it: the same command code, under the
	 * response network function of the request (the request one plus one, IPMI 2.0 section 5.1). The codes are
	 * compared as numbers, so that the reply to a request of a network function {@link NetworkFunction} does not list
	 * (Transport 0Ch, Group Extension 2Ch, OEM 30h-3Eh) is matched like any other. A reply that does not answer the
	 * request (the late reply of an older request that had the same tag) is ignored, and the request keeps waiting.
	 *
	 * @param reply the IPMI message received under the tag of this request
	 * @return whether the reply answers this request
	 */
	public boolean answers(IpmiLanMessage reply) {
		return getCommandCode() == reply.getCommand() && getNetworkFunctionCode() + 1 == reply.getNetworkFunctionCode();
	}

	/**
	 * Checks the LUN a request is addressed to: 2 bits (IPMI 2.0 section 5.1).
	 *
	 * @param lun the LUN, 0 to 3
	 * @return the LUN as a byte
	 * @throws IllegalArgumentException when the LUN is not 0 to 3
	 */
	protected static byte checkLun(int lun) {
		if (lun < 0 || lun > 3) {
			throw new IllegalArgumentException("Invalid LUN: " + lun);
		}
		return TypeConverter.intToByte(lun);
	}

	/**
	 * Encodes the request of a command for a command that carries it in its own data (Send Message).
	 *
	 * @param command the command to encode
	 * @param sequenceNumber the sequence number of the request
	 * @return the request, addressed to the BMC
	 * @throws IllegalArgumentException when the command is not an IPMI request (an RMCP+ session setup message)
	 * @throws NoSuchAlgorithmException when the command cannot be encoded
	 * @throws InvalidKeyException when the command cannot be encoded
	 */
	protected static IpmiLanRequest encodeRequest(IpmiCommandCoder<?> command, int sequenceNumber)
			throws NoSuchAlgorithmException,
			InvalidKeyException {
		IpmiPayload payload = command.preparePayload(sequenceNumber);
		if (!(payload instanceof IpmiLanRequest)) {
			throw new IllegalArgumentException(command.getClass().getSimpleName() + " is not an IPMI request");
		}
		return (IpmiLanRequest) payload;
	}

	/**
	 * Checks that the message is a successful response to this command.
	 *
	 * @param message {@link IpmiMessage} wrapping the IPMI response
	 * @return the IPMI command data of the response
	 * @throws IllegalArgumentException when the message is not an IPMI LAN response to this command
	 * @throws IPMIException when the completion code of the response is not {@link CompletionCode#Ok}
	 */
	protected byte[] validateResponse(IpmiMessage message) throws IPMIException {
		if (!isCommandResponse(message)) {
			throw new IllegalArgumentException("This is not a response for " + getClass().getSimpleName() + " command");
		}
		if (!(message.getPayload() instanceof IpmiLanResponse)) {
			throw new IllegalArgumentException("Invalid response payload");
		}
		IpmiLanResponse response = (IpmiLanResponse) message.getPayload();
		CompletionCode completionCode = response.getCompletionCode();
		if (completionCode == CompletionCode.Unknown) {
			completionCode = decodeCommandSpecificCompletionCode(response.getRawCompletionCode());
		}
		if (completionCode != CompletionCode.Ok) {
			throw new IPMIException(completionCode, response.getRawCompletionCode());
		}
		return response.getIpmiCommandData();
	}

	/**
	 * Checks an RMCP+ session setup response (Open Session Response, RAKP Message 2 or 4): its payload holds at least
	 * the message tag and the status code, and the status code reports no error.
	 *
	 * @param message the response
	 * @return the payload of the response
	 * @throws IPMIException when the status code reports an error
	 * @throws IllegalArgumentException when the payload is too short
	 */
	protected static byte[] validateSessionSetupResponse(IpmiMessage message) throws IPMIException {
		byte[] payload = message.getPayload().getPayloadData();
		if (payload.length < 2) {
			throw new IllegalArgumentException("Invalid payload length");
		}
		if (payload[1] != 0) {
			throw new IPMIException(CompletionCode.parseInt(TypeConverter.byteToInt(payload[1])));
		}
		return payload;
	}

	/**
	 * Gives a meaning to an OEM or command-specific completion code (01h-7Eh and 80h-BEh, IPMI 2.0 Table 5-2) of this
	 * command. The default knows none of them.
	 *
	 * @param rawCode the completion code byte of the response
	 * @return the matching {@link CompletionCode}, or {@link CompletionCode#Unknown}
	 */
	protected CompletionCode decodeCommandSpecificCompletionCode(int rawCode) {
		return CompletionCode.Unknown;
	}

	/**
	 * Retrieves command code specific for command represented by this class
	 *
	 * @return command code
	 */
	public abstract byte getCommandCode();

	/**
	 * Retrieves network function specific for command represented by this
	 * class.
	 *
	 * @return network function, or null for a command of a network function {@link NetworkFunction} does not list,
	 *         which overrides {@link #getNetworkFunctionCode()} instead
	 * @see NetworkFunction
	 */
	public abstract NetworkFunction getNetworkFunction();

	/**
	 * Returns the network function code of the request (6 bits, IPMI 2.0 table 5-1): the code of
	 * {@link #getNetworkFunction()} by default. A command of a network function {@link NetworkFunction} does not list
	 * (Transport 0Ch, Group Extension 2Ch such as DCMI, OEM/Group 2Eh, controller-specific OEM 30h-3Eh) overrides it
	 * and builds its request with
	 * {@link org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest#IpmiLanRequest(int, byte, byte[], byte)}: its
	 * reply is the one under this code plus one.
	 *
	 * @return the network function code of the request, an even number from 00h to 3Eh
	 */
	public int getNetworkFunctionCode() {
		return getNetworkFunction().getCode();
	}

	/**
	 * Used in several derived classes - converts {@link PrivilegeLevel} to
	 * byte.
	 *
	 * @param privilegeLevel the privilege level to encode
	 * @return privilegeLevel encoded as a byte due to {@link CommandsConstants}
	 */
	protected byte encodePrivilegeLevel(PrivilegeLevel privilegeLevel) {
		switch (privilegeLevel) {
		case MaximumAvailable:
			return CommandsConstants.AL_HIGHEST_AVAILABLE;
		case Callback:
			return CommandsConstants.AL_CALLBACK;
		case User:
			return CommandsConstants.AL_USER;
		case Operator:
			return CommandsConstants.AL_OPERATOR;
		case Administrator:
			return CommandsConstants.AL_ADMINISTRATOR;
		default:
			throw new IllegalArgumentException("Invalid privilege level");
		}
	}
}
