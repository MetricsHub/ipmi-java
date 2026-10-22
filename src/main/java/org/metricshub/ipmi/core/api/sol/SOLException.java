package org.metricshub.ipmi.core.api.sol;

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

import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;

/**
 * Exception representing problem that might have been encountered during Serial over LAN communication.
 */
public class SOLException extends Exception {

	private static final long serialVersionUID = 1L;

	/** The command that failed, or null: not serializable, like the connection. */
	private final transient IpmiCommandCoder<?> command;

	/** The connection the failed command was sent on, or null. */
	private final transient ConnectionHandle connectionHandle;

	/**
	 * Creates the exception.
	 *
	 * @param message the description of the failure
	 */
	public SOLException(String message) {
		this(message, null);
	}

	/**
	 * Creates the exception with its cause.
	 *
	 * @param message the description of the failure
	 * @param cause the exception that made the operation fail
	 */
	public SOLException(String message, Throwable cause) {
		this(message, cause, null, null);
	}

	/**
	 * Creates the exception for a command of the console that failed.
	 *
	 * @param message the description of the failure
	 * @param cause the exception that made the command fail: an {@code IPMIException} for an error reply, a
	 *        {@code ConnectionException} when the BMC did not answer
	 * @param command the command that failed
	 * @param connectionHandle the connection the command was sent on
	 */
	public SOLException(String message, Throwable cause, IpmiCommandCoder<?> command, ConnectionHandle connectionHandle) {
		super(message, cause);
		this.command = command;
		this.connectionHandle = connectionHandle;
	}

	/**
	 * Returns the command that failed: Get Payload Activation Status, Activate Payload, Set Session Privilege Level,
	 * Get Payload Instance Info or Deactivate Payload.
	 *
	 * @return the command, or null when the failure is not the one of a command (or the exception was deserialized)
	 */
	public IpmiCommandCoder<?> getCommand() {
		return command;
	}

	/**
	 * Returns the connection the failed command was sent on: the one of the session given to the console, or the one
	 * of the session the console opened on the port of the payload.
	 *
	 * @return the connection, or null when the failure is not the one of a command (or the exception was deserialized)
	 */
	public ConnectionHandle getConnectionHandle() {
		return connectionHandle;
	}
}
