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

/**
 * The privilege level of a session (IPMI 2.0 section 6.8), declared from the lowest to the highest: Callback, User,
 * Operator, Administrator. {@link #MaximumAvailable}, declared last, is not a level but a request for the highest
 * level the account is allowed.
 */
public enum PrivilegeLevel {
	/**
	 * The lowest level: only the commands that initiate a callback. The client refuses it
	 * ({@link org.metricshub.ipmi.client.IpmiClient.Builder#privilege(PrivilegeLevel)}), as none of its operations
	 * works at this level.
	 */
	Callback,
	/**
	 * The level every session but a Callback one starts at (IPMI 2.0 section 22.18): the commands that read the BMC,
	 * such as the chassis status, the sensors, the FRUs and the SEL. On most BMCs, it is not enough for the sensors and
	 * FRUs of the controllers other than the BMC, which the BMC reaches with Send Message.
	 */
	User,
	/**
	 * The User commands and the commands that change the state of the system, such as Chassis Control and Chassis
	 * Identify, but not the configuration commands that change the behavior of the out-of-band interfaces. Most BMCs
	 * accept Send Message at this level, some at the Administrator level only.
	 */
	Operator,
	/**
	 * Every command, the configuration of the BMC included; Serial over LAN needs this level on most BMCs.
	 */
	Administrator,
	/**
	 * The highest level the account is allowed. {@code openSession()} of
	 * {@link org.metricshub.ipmi.core.api.sync.IpmiConnector} and
	 * {@link org.metricshub.ipmi.core.api.async.IpmiAsyncConnector} requests Administrator at Open Session and RAKP
	 * Message 1, then Operator, then User when the BMC refuses the role to the account, and sets the session to User
	 * with Set Session Privilege Level: the caller raises it when a command needs more, as
	 * {@link org.metricshub.ipmi.client.IpmiClient} and {@link org.metricshub.ipmi.core.api.sol.SerialOverLan} do.
	 * Encoded by a command itself, this value is role 0h: in Open Session and RAKP Message 1, the highest level that
	 * matches the proposed algorithms (IPMI 2.0 section 13.17); in Set Session Privilege Level, a request for the
	 * present level of the session, which stays unchanged.
	 */
	MaximumAvailable
}
