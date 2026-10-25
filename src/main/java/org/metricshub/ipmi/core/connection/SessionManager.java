package org.metricshub.ipmi.core.connection;

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
import org.metricshub.ipmi.core.api.sol.CipherSuiteSelectionHandler;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.Randomizer;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages multiple {@link Session}s.
 */
public class SessionManager {

	private static final int SESSION_ID_LIMIT = Integer.MAX_VALUE / 4;

	/**
	 * Console session IDs: unique within the JVM, starting at a random value so that they cannot be guessed from
	 * outside.
	 */
	private static final AtomicInteger SESSION_ID = new AtomicInteger(
			Math.floorMod(Randomizer.getInt(), SESSION_ID_LIMIT));

	/**
	 * Generates the console session ID of a new session.
	 *
	 * @return a session ID between 1 and {@code Integer.MAX_VALUE / 4} (never 0, the ID of session-less messages),
	 *         different from the previous ones
	 */
	public static int generateSessionId() {
		return SESSION_ID.updateAndGet(id -> id % SESSION_ID_LIMIT + 1);
	}

	/**
	 * Opens a new connection and session, at the Administrator privilege level, raised to it right after the login.
	 *
	 * @param connector the connector of the connection
	 * @param remoteHost the address of the BMC
	 * @param remotePort the UDP port of the BMC
	 * @param user the user name
	 * @param password the password, sent in UTF-8
	 * @param cipherSuiteSelectionHandler chooses the cipher suite among those the BMC offers
	 * @return the new session
	 * @throws SessionException when the session cannot be opened, or the account is not allowed the Administrator
	 *         privilege
	 */
	public static Session establishSession(
			IpmiConnector connector,
			String remoteHost,
			int remotePort,
			String user,
			String password,
			CipherSuiteSelectionHandler cipherSuiteSelectionHandler)
			throws SessionException {
		return establishSession(
				connector,
				remoteHost,
				remotePort,
				user,
				password == null ? null : password.getBytes(StandardCharsets.UTF_8),
				null,
				cipherSuiteSelectionHandler);
	}

	/**
	 * Opens a new connection and session, at the Administrator privilege level, raised to it right after the login.
	 *
	 * @param connector the connector of the connection
	 * @param remoteHost the address of the BMC
	 * @param remotePort the UDP port of the BMC
	 * @param user the user name
	 * @param password the password, as sent to the BMC
	 * @param bmcKey the BMC key (Kg) for a two-key login, or null
	 * @param cipherSuiteSelectionHandler chooses the cipher suite among those the BMC offers
	 * @return the new session
	 * @throws SessionException when the session cannot be opened, or the account is not allowed the Administrator
	 *         privilege
	 */
	public static Session establishSession(
			IpmiConnector connector,
			String remoteHost,
			int remotePort,
			String user,
			byte[] password,
			byte[] bmcKey,
			CipherSuiteSelectionHandler cipherSuiteSelectionHandler)
			throws SessionException {
		ConnectionHandle handle = null;

		try {
			handle = connector.createConnection(InetAddress.getByName(remoteHost), remotePort);

			List<CipherSuite> availableCipherSuites = connector.getAvailableCipherSuites(handle);
			CipherSuite cipherSuite = cipherSuiteSelectionHandler.choose(availableCipherSuites);

			// Never fall back to suite 0: the offer is unauthenticated, so that would be a downgrade path
			if (cipherSuite == null) {
				throw new IllegalArgumentException(
						"The cipher suite selector chose none of the suites the BMC offers (offered: "
								+ CipherSuite.listIds(availableCipherSuites) + ")");
			}

			connector.getChannelAuthenticationCapabilities(handle, cipherSuite, PrivilegeLevel.Administrator);

			return connector.openSession(handle, user, password, bmcKey);
		} catch (Exception e) {
			closeConnection(connector, handle);

			throw new SessionException("Cannot create new session: " + e.getMessage(), e);
		}
	}

	/**
	 * Opens a new connection and session on another UDP port of the BMC of the given connection (the port of a
	 * payload, IPMI 2.0 section 24.1), with its user name, password, BMC key and privilege level, and a copy of its
	 * cipher suite: opening the session keys the algorithms of its suite.
	 *
	 * @param connector the connector of the connection
	 * @param connectionHandle the connection whose credentials, cipher suite and privilege level the session takes
	 * @param remotePort the UDP port of the BMC
	 * @return the new session
	 * @throws SessionException when the session cannot be opened, or the account is not allowed the privilege level
	 */
	public static Session establishSession(IpmiConnector connector, ConnectionHandle connectionHandle, int remotePort)
			throws SessionException {
		ConnectionHandle handle = null;
		try {
			handle = connector.createConnection(connectionHandle.getRemoteAddress(), remotePort);
			connector.getAvailableCipherSuites(handle);
			connector.getChannelAuthenticationCapabilities(
					handle,
					connectionHandle.getCipherSuite().copy(),
					connectionHandle.getPrivilegeLevel());
			return connector.openSession(
					handle,
					connectionHandle.getUser(),
					connectionHandle.getPasswordBytes(),
					connectionHandle.getBmcKey());
		} catch (Exception e) {
			closeConnection(connector, handle);
			throw new SessionException("Cannot create new session: " + e.getMessage(), e);
		}
	}

	/**
	 * Closes the session and the connection that failed to establish a session, leaving the connector and its other
	 * connections untouched.
	 */
	private static void closeConnection(IpmiConnector connector, ConnectionHandle handle) {
		if (connector == null || handle == null) {
			return;
		}
		try {
			connector.closeSession(handle);
		} catch (Exception e) {
			// Best effort: the connection is closed below, and the failure of the session is what the caller gets
		}
		connector.closeConnection(handle);
	}

	private final ConcurrentHashMap<Integer, Session> sessionsPerConnectionHandle;

	public SessionManager() {
		this.sessionsPerConnectionHandle = new ConcurrentHashMap<Integer, Session>();
	}

	/**
	 * Either returns the {@link Session} registered for given connection, or creates new IPMI {@link Session}
	 * and registers it for the given connection handle, if no session was found for the connection.
	 *
	 * @param connectionHandle
	 *        handle of the connection, for which this session should be registered.
	 * @return newly created session object or session already registered for the connection
	 */
	public Session registerSession(int newSessionId, ConnectionHandle connectionHandle) {
		return registerSession(newSessionId, 0, connectionHandle);
	}

	/**
	 * Either returns the {@link Session} registered for given connection, or creates new IPMI {@link Session}
	 * and registers it for the given connection handle, if no session was found for the connection.
	 *
	 * @param newSessionId
	 *        the remote console session ID of the new session
	 * @param managedSystemSessionId
	 *        the managed system session ID of the new session, which the BMC generated
	 * @param connectionHandle
	 *        handle of the connection, for which this session should be registered.
	 * @return newly created session object or session already registered for the connection
	 */
	public Session registerSession(int newSessionId, int managedSystemSessionId, ConnectionHandle connectionHandle) {
		if (connectionHandle.getUser() == null || connectionHandle.getRemoteAddress() == null) {
			throw new IllegalArgumentException("Given connection handle is incomplete (lacks user or remote address)");
		}

		Session newSession = new Session(newSessionId, managedSystemSessionId, connectionHandle);
		Session currentSession = sessionsPerConnectionHandle.putIfAbsent(connectionHandle.getHandle(), newSession);

		return currentSession != null ? currentSession : newSession;
	}

	/**
	 * Unregisters session for given connection handle, and wipes the credentials of the handle it was registered with.
	 *
	 * @param connectionHandle
	 *        handle of the connection, for which the session should be unregistered.
	 */
	public void unregisterSession(ConnectionHandle connectionHandle) {
		Session session = sessionsPerConnectionHandle.remove(connectionHandle.getHandle());
		// The handle registered with the session, which holds the credentials, may be another object than the argument
		if (session != null) {
			session.getConnectionHandle().wipeCredentials();
		}
	}

	/**
	 * Wipes the credentials of the connections of all the registered sessions, when their connector tears down
	 * without closing each connection.
	 */
	public void wipeCredentials() {
		for (Session session : sessionsPerConnectionHandle.values()) {
			session.getConnectionHandle().wipeCredentials();
		}
	}

	/**
	 * Returns session already bound to given connection handle fulfilling given criteria.
	 *
	 * @param remoteAddress
	 *        IP addres of the managed system
	 * @param remotePort
	 *        UDP port of the managed system
	 * @param user
	 *        IPMI user for whom the connection is established
	 * @return session object fulfilling given criteria, or null if no session was registered for such connection.
	 */
	public Session getSessionForCriteria(InetAddress remoteAddress, int remotePort, String user) {
		for (Session session : sessionsPerConnectionHandle.values()) {
			ConnectionHandle sessionConnectionHandle = session.getConnectionHandle();

			if (sessionConnectionHandle.getUser().equals(user)
					&& sessionConnectionHandle.getRemoteAddress().equals(remoteAddress)
					&& sessionConnectionHandle.getRemotePort() == remotePort) {
				return session;
			}
		}

		return null;
	}
}
