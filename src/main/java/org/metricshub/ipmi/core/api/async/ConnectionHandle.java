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
import java.util.Arrays;

/**
 * Identifies one {@link Connection} (one BMC) of a connector: its index in the connector, the address and UDP port of
 * the BMC, the cipher suite and privilege level of its session, and the user name, password and BMC key (Kg) the
 * session was opened with, which the connector keeps to open another session on a payload port of the same BMC (Serial
 * over LAN). Returned by {@code createConnection()} of {@link org.metricshub.ipmi.core.api.sync.IpmiConnector} and
 * {@link IpmiAsyncConnector}, which fill it in as the connection is set up.
 * <p>
 * The responses delivered to an {@link IpmiResponseListener} carry a new {@code ConnectionHandle} with only the
 * index, address and port of the connection: compare {@link #getHandle()} to match a response with its connection.
 * </p>
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
	/**
	 * The BMC key (Kg) of the session, kept to open the Serial over LAN session.
	 */
	private byte[] bmcKey;

	/**
	 * Creates the handle of a connection. The connector creates it: see
	 * {@link org.metricshub.ipmi.core.api.sync.IpmiConnector#createConnection(InetAddress)}.
	 *
	 * @param handle the index of the connection in its connector
	 * @param remoteAddress the address of the BMC
	 * @param remotePort the UDP port of the BMC
	 */
	public ConnectionHandle(int handle, InetAddress remoteAddress, int remotePort) {
		this.handle = handle;
		this.remoteAddress = remoteAddress;
		this.remotePort = remotePort;
	}

	/**
	 * Returns the cipher suite of the session, declared with {@code getChannelAuthenticationCapabilities()} or
	 * {@code createConnection()}.
	 *
	 * @return the cipher suite, or null before it is declared
	 */
	public CipherSuite getCipherSuite() {
		return cipherSuite;
	}

	/**
	 * Sets the cipher suite of the session. The connector sets it; changing it once the session is open has no effect
	 * on that session.
	 *
	 * @param cipherSuite the cipher suite of the session
	 */
	public void setCipherSuite(CipherSuite cipherSuite) {
		this.cipherSuite = cipherSuite;
	}

	/**
	 * Returns the maximum privilege level requested for the session, declared with
	 * {@code getChannelAuthenticationCapabilities()} or {@code createConnection()}:
	 * {@link PrivilegeLevel#MaximumAvailable} stays {@code MaximumAvailable}, whatever level the session runs at.
	 *
	 * @return the privilege level, or null before it is declared
	 */
	public PrivilegeLevel getPrivilegeLevel() {
		return privilegeLevel;
	}

	/**
	 * Sets the maximum privilege level requested for the session. The connector sets it; changing it once the session
	 * is open has no effect on that session.
	 *
	 * @param privilegeLevel the privilege level of the session
	 */
	public void setPrivilegeLevel(PrivilegeLevel privilegeLevel) {
		this.privilegeLevel = privilegeLevel;
	}

	/**
	 * Returns the index of the connection in its connector, which is not reused once the connection is closed. The
	 * response delivered to an {@link IpmiResponseListener} carries a new {@code ConnectionHandle} object, and this
	 * class does not override {@code equals()}: match a response with its connection by comparing
	 * {@code response.getHandle().getHandle()} with this index.
	 *
	 * @return the index of the connection
	 */
	public int getHandle() {
		return handle;
	}

	/**
	 * Returns the address of the BMC.
	 *
	 * @return the address of the BMC
	 */
	public InetAddress getRemoteAddress() {
		return remoteAddress;
	}

	/**
	 * Returns the UDP port of the BMC.
	 *
	 * @return the UDP port of the BMC
	 */
	public int getRemotePort() {
		return remotePort;
	}

	/**
	 * Returns the user name of the session, set by {@code openSession()}.
	 *
	 * @return the user name, or null before the session is opened
	 */
	public String getUser() {
		return user;
	}

	/**
	 * Sets the user name of the session. {@code openSession()} sets it.
	 *
	 * @param user the user name of the session
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Returns the password of the session, decoded from UTF-8.
	 *
	 * @return the password of the session, decoded from UTF-8, or null
	 */
	public String getPassword() {
		return password == null ? null : new String(password, StandardCharsets.UTF_8);
	}

	/**
	 * Sets the password of the session, encoded in UTF-8.
	 *
	 * @param password the password of the session, sent to the BMC in UTF-8
	 */
	public void setPassword(String password) {
		this.password = password == null ? null : password.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Returns the password of the session as bytes: the array the handle holds, not a copy, so do not modify it.
	 *
	 * @return the password of the session, as sent to the BMC, or null
	 */
	public byte[] getPasswordBytes() {
		return password;
	}

	/**
	 * Sets the password of the session as bytes. {@code openSession()} sets it.
	 *
	 * @param passwordBytes the password of the session, as sent to the BMC: the handle keeps a copy, so the caller can
	 *        clear the array
	 */
	public void setPasswordBytes(byte[] passwordBytes) {
		this.password = passwordBytes == null ? null : passwordBytes.clone();
	}

	/**
	 * Returns the BMC key (Kg) of the session: the array the handle holds, not a copy, so do not modify it.
	 *
	 * @return the BMC key (Kg) of the session, or null for a one-key login
	 */
	public byte[] getBmcKey() {
		return bmcKey;
	}

	/**
	 * Sets the BMC key (Kg) of the session. {@code openSession()} sets it.
	 *
	 * @param bmcKey the BMC key (Kg) of the session, or null: the handle keeps a copy, so the caller can clear the array
	 */
	public void setBmcKey(byte[] bmcKey) {
		this.bmcKey = bmcKey == null ? null : bmcKey.clone();
	}

	/**
	 * Wipes the password and the BMC key (Kg) of the session, and the password that MD5-128 integrity keeps, once the
	 * connection is closed. {@code closeConnection()} calls it.
	 */
	public void wipeCredentials() {
		if (password != null) {
			Arrays.fill(password, (byte) 0);
		}
		if (bmcKey != null) {
			Arrays.fill(bmcKey, (byte) 0);
		}
		if (cipherSuite != null) {
			cipherSuite.wipeKeys();
		}
	}

	/**
	 * Returns the index, address and port of the connection, and the cipher suite, privilege level and user name of
	 * its session, without the password or the BMC key.
	 */
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
