package org.metricshub.ipmi.client;

import org.metricshub.ipmi.core.common.Constants;

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

/**
 * IPMI configuration including the required credentials that need to be used to establish the
 * communication with the IPMI interface.
 *
 */
public class IpmiClientConfiguration {

	private String hostname;
	private String username;
	private char[] password;
	private byte[] bmcKey;
	private boolean skipAuth;
	private int port = Constants.IPMI_PORT;
	private long timeout;
	private long pingPeriod = -1;

	/**
	 * Instantiates a new {@link IpmiClientConfiguration} in order to query the IPMI host.
	 * 
	 * @param hostname IP Address or host name of the remote IPMI host.
	 * @param username Name used to establish the connection with the host via the IPMI protocol.
	 * @param password Password used to establish the connection with the host via the IPMI protocol.
	 * @param bmcKey   The key that should be provided if the two-key authentication is enabled, null otherwise.
	 * @param skipAuth Whether the client should skip authentication
	 * @param timeout  Timeout used for each IPMI request.
	 */
	public IpmiClientConfiguration(String hostname, String username, char[] password,
			byte[] bmcKey, boolean skipAuth, long timeout) {
		this.hostname = hostname;
		this.username = username;
		this.password = password;
		this.bmcKey = bmcKey;
		this.skipAuth = skipAuth;
		this.timeout = timeout;
	}

	/**
	 * Instantiates a new {@link IpmiClientConfiguration} in order to query the IPMI host.
	 * 
	 * @param hostname IP Address or host name of the remote IPMI host.
	 * @param port UDP port number of the remote IPMI host.
	 * @param username Name used to establish the connection with the host via the IPMI protocol.
	 * @param password Password used to establish the connection with the host via the IPMI protocol.
	 * @param bmcKey   The key that should be provided if the two-key authentication is enabled, null otherwise.
	 * @param skipAuth Whether the client should skip authentication
	 * @param timeout  Timeout used for each IPMI request.
	 */
	public IpmiClientConfiguration(String hostname, int port, String username, char[] password,
			byte[] bmcKey, boolean skipAuth, long timeout) {
		this(hostname, username, password, bmcKey, skipAuth, timeout);
		this.port = port;
	}

	/**
	 * Instantiates a new {@link IpmiClientConfiguration} in order to query the IPMI host.
	 * 
	 * @param hostname   IP Address or host name of the remote IPMI host.
	 * @param username   Name used to establish the connection with the host via the IPMI protocol.
	 * @param password   Password used to establish the connection with the host via the IPMI protocol.
	 * @param bmcKey     The key that should be provided if the two-key authentication is enabled, null otherwise.
	 * @param skipAuth   Whether the client should skip authentication
	 * @param timeout    Timeout used for each IPMI request.
	 * @param pingPeriod The period in milliseconds used to send the keep alive messages.<br>
	 *                   Set pingPeriod to 0 to turn off keep-alive messages sent to the remote host.
	 */
	public IpmiClientConfiguration(String hostname, String username, char[] password,
			byte[] bmcKey, boolean skipAuth, long timeout, long pingPeriod) {
		this(hostname, username, password, bmcKey, skipAuth, timeout);
		this.pingPeriod = pingPeriod;
	}

	/**
	 * Returns the IP Address or host name of the remote IPMI host.
	 * 
	 * @return IP Address or host name of the remote IPMI host.
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Sets the IP Address or host name of the remote IPMI host.
	 * 
	 * @param hostname IP Address or host name of the remote IPMI host.
	 */
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	/**
	 * Returns the UDP port number of the remote IPMI host.
	 * 
	 * @return UDP port number of the remote IPMI host.
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Sets the UDP port number of the remote IPMI host.
	 * 
	 * @param port UDP port number of the remote IPMI host.
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Returns the name used to establish the connection with the host via the IPMI
	 * protocol.
	 * 
	 * @return Name used to establish the connection with the host via the IPMI protocol.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Sets the name used to establish the connection with the host via the IPMI
	 * protocol.
	 * 
	 * @param username Name used to establish the connection with the host via the
	 *                 IPMI protocol.
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Returns the password used to establish the connection with the host via the
	 * IPMI protocol.
	 * 
	 * @return Password used to establish the connection with the host via the IPMI protocol.
	 */
	public char[] getPassword() {
		return password;
	}

	/**
	 * Sets the password used to establish the connection with the host via the IPMI
	 * protocol.
	 * 
	 * @param password Password used to establish the connection with the host via the IPMI protocol.
	 */
	public void setPassword(char[] password) {
		this.password = password;
	}

	/**
	 * Returns the key that should be provided if the two-key authentication is
	 * enabled, null otherwise.
	 * 
	 * @return The key that should be provided if the two-key authentication is
	 *         enabled, null otherwise.
	 */
	public byte[] getBmcKey() {
		return bmcKey;
	}

	/**
	 * Sets the key that should be provided if the two-key authentication is
	 * enabled, null otherwise.
	 * 
	 * @param bmcKey The key that should be provided if the two-key authentication
	 *               is enabled, null otherwise.
	 */
	public void setBmcKey(byte[] bmcKey) {
		this.bmcKey = bmcKey;
	}

	/**
	 * Returns whether the client should skip authentication.
	 * 
	 * @return Whether the client should skip authentication.
	 */
	public boolean isSkipAuth() {
		return skipAuth;
	}

	/**
	 * Sets whether the client should skip authentication.
	 * 
	 * @param skipAuth Whether the client should skip authentication.
	 */
	public void setSkipAuth(boolean skipAuth) {
		this.skipAuth = skipAuth;
	}

	/**
	 * Returns the timeout used for each IPMI request.
	 * 
	 * @return The timeout used for each IPMI request.
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Sets the timeout used for each IPMI request.
	 * 
	 * @param timeout The timeout used for each IPMI request.
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	/**
	 * Returns the period in milliseconds used to send the keep alive messages.
	 * 
	 * @return The period in milliseconds used to send the keep alive messages.
	 */
	public long getPingPeriod() {
		return pingPeriod;
	}

	/**
	 * Sets the period in milliseconds used to send the keep alive messages.<br>
	 * Set pingPeriod to 0 to turn off keep-alive messages sent to the remote host.
	 * 
	 * @param pingPeriod The period in milliseconds used to send the keep alive messages.
	 */
	public void setPingPeriod(long pingPeriod) {
		this.pingPeriod = pingPeriod;
	}

}
