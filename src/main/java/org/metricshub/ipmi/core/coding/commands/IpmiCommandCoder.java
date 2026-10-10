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
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * A wrapper for IPMI command.
 * Parameterless constructors in classes derived from IpmiCommandCoder are meant
 * to be used for decoding. To avoid omitting setting an important parameter
 * when encoding message use parametered constructors rather than the
 * parameterless ones.
 */
public abstract class IpmiCommandCoder extends PayloadCoder {

	public IpmiCommandCoder() {

	}

	public IpmiCommandCoder(IpmiVersion version, CipherSuite cipherSuite,
			AuthenticationType authenticationType) {
		super(version, cipherSuite, authenticationType);
	}

	@Override
	public PayloadType getSupportedPayloadType() {
		return PayloadType.Ipmi;
	}

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
	 * @return network function
	 * @see NetworkFunction
	 */
	public abstract NetworkFunction getNetworkFunction();

	/**
	 * Used in several derived classes - converts {@link PrivilegeLevel} to
	 * byte.
	 *
	 * @param privilegeLevel
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
