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

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;

/**
 * The BMC answered a command with an error completion code (IPMI 2.0 table 5-2).
 */
public class IpmiCompletionCodeException extends IpmiClientException {

	private static final long serialVersionUID = 1L;

	private final CompletionCode completionCode;
	private final int rawCompletionCode;
	private final NetworkFunction networkFunction;
	private final int command;

	/**
	 * Creates the exception for the error reply to a command.
	 *
	 * @param request the command the BMC answered
	 * @param cause the error reply
	 */
	public IpmiCompletionCodeException(IpmiCommandCoder<?> request, IPMIException cause) {
		this(request.getClass().getSimpleName(), request.getNetworkFunction(), request.getCommandCode() & 0xff, cause);
	}

	/**
	 * Creates the exception for an error reply received in the course of an operation, when the command the BMC
	 * answered is not known.
	 *
	 * @param what what the operation was doing
	 * @param cause the error reply
	 */
	public IpmiCompletionCodeException(String what, IPMIException cause) {
		this(what, null, -1, cause);
	}

	private IpmiCompletionCodeException(String what, NetworkFunction networkFunction, int command, IPMIException cause) {
		super(describe(what, cause), cause);
		this.completionCode = cause.getCompletionCode();
		this.rawCompletionCode = cause.getRawCode();
		this.networkFunction = networkFunction;
		this.command = command;
	}

	private static String describe(String what, IPMIException cause) {
		String description = what + " failed: " + cause.getMessage()
				+ String.format(" (completion code %02Xh)", cause.getRawCode());
		if (cause.getCompletionCode() == CompletionCode.InsufficentPrivilege) {
			description += ". The command requires a higher privilege than the session has: open the client with "
					+ "privilege(PrivilegeLevel.Operator) or privilege(PrivilegeLevel.Administrator)";
		}
		return description;
	}

	/**
	 * Returns the completion code, {@link CompletionCode#Unknown} when the library does not model it.
	 *
	 * @return the completion code
	 */
	public CompletionCode completionCode() {
		return completionCode;
	}

	/**
	 * Returns the completion code byte as the BMC sent it, for the codes the library does not model (the
	 * command-specific and OEM ones).
	 *
	 * @return the completion code, 0 to 255
	 */
	public int rawCompletionCode() {
		return rawCompletionCode;
	}

	/**
	 * Returns the network function of the command the BMC answered.
	 *
	 * @return the network function, or null when the command is not known
	 */
	public NetworkFunction networkFunction() {
		return networkFunction;
	}

	/**
	 * Returns the command code of the command the BMC answered.
	 *
	 * @return the command code, 0 to 255, or -1 when the command is not known
	 */
	public int command() {
		return command;
	}
}
