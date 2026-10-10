package org.metricshub.ipmi.core.api.async;

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

import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.connection.Connection;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Handle to the {@link Connection}
 */
public class ConnectionHandle {
	private int handle;
	private CipherSuite cipherSuite;
	private PrivilegeLevel privilegeLevel;
	private InetAddress remoteAddress;
	private int remotePort;
	private String user;
	/**
	 * The password of the session, as sent to the BMC, kept to open the Serial over LAN session.
	 */
	private byte[] password;

	public ConnectionHandle(int handle, InetAddress remoteAddress, int remotePort) {
		this.handle = handle;
		this.remoteAddress = remoteAddress;
		this.remotePort = remotePort;
	}

	public CipherSuite getCipherSuite() {
		return cipherSuite;
	}

	public void setCipherSuite(CipherSuite cipherSuite) {
		this.cipherSuite = cipherSuite;
	}

	public PrivilegeLevel getPrivilegeLevel() {
		return privilegeLevel;
	}

	public void setPrivilegeLevel(PrivilegeLevel privilegeLevel) {
		this.privilegeLevel = privilegeLevel;
	}

	public int getHandle() {
		return handle;
	}

	public InetAddress getRemoteAddress() {
		return remoteAddress;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * @return the password of the session, decoded from UTF-8, or null
	 */
	public String getPassword() {
		return password == null ? null : new String(password, StandardCharsets.UTF_8);
	}

	/**
	 * @param password the password of the session, sent to the BMC in UTF-8
	 */
	public void setPassword(String password) {
		this.password = password == null ? null : password.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * @return the password of the session, as sent to the BMC, or null
	 */
	public byte[] getPasswordBytes() {
		return password;
	}

	/**
	 * @param passwordBytes the password of the session, as sent to the BMC: the handle keeps a copy, so the caller can
	 *        clear the array
	 */
	public void setPasswordBytes(byte[] passwordBytes) {
		this.password = passwordBytes == null ? null : passwordBytes.clone();
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("ConnectionHandle{");
		sb.append("handle=").append(handle);
		sb.append(", cipherSuite=").append(cipherSuite);
		sb.append(", privilegeLevel=").append(privilegeLevel);
		sb.append(", remoteAddress=").append(remoteAddress);
		sb.append(", remotePort=").append(remotePort);
		sb.append(", user='").append(user).append('\'');
		sb.append('}');
		return sb.toString();
	}
}
