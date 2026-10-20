package org.metricshub.ipmi.core.api.sync;

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
import org.metricshub.ipmi.core.api.async.InboundMessageListener;
import org.metricshub.ipmi.core.api.async.IpmiAsyncConnector;
import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilitiesResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.Constants;
import org.metricshub.ipmi.core.connection.Connection;
import org.metricshub.ipmi.core.connection.ConnectionException;
import org.metricshub.ipmi.core.connection.ConnectionManager;
import org.metricshub.ipmi.core.connection.Ipmi20UnsupportedException;
import org.metricshub.ipmi.core.connection.Session;
import org.metricshub.ipmi.core.connection.SessionException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Random;

/**
 * <p>
 * Synchronous API for connecting to BMC via IPMI.
 * </p>
 * <br>
 * Creating connection consists of the following steps:
 * <ul>
 * <li>Create a {@link Connection} and get its {@link ConnectionHandle} with {@link #createConnection(InetAddress)}</li>
 * <li>Get {@link CipherSuite}s that are available for the connection via
 * {@link #getAvailableCipherSuites(ConnectionHandle)}</li>
 * <li>Pick {@link CipherSuite} and {@link PrivilegeLevel} that will
 * be used during session and get {@link GetChannelAuthenticationCapabilitiesResponseData} to find out allowed
 * authentication options via
 * {@link #getChannelAuthenticationCapabilities(ConnectionHandle, CipherSuite, PrivilegeLevel)}</li>
 * <li>Provide username,
 * password and (if the BMC needs it) the BMC Kg key and start session via
 * {@link #openSession(ConnectionHandle, String, String, byte[])} or
 * {@link #openSession(ConnectionHandle, String, byte[], byte[])} (password as bytes, which the caller can clear),
 * which also raises it to the requested Operator or Administrator privilege</li>
 * </ul>
 * <br>
 * <p>
 * Send commands with {@link #sendMessage(ConnectionHandle, IpmiCommandCoder)}, which returns the typed response of
 * the command, and close the session with {@link #closeSession(ConnectionHandle)}, in a finally block: BMCs have few
 * session slots.
 * </p>
 * <p>
 * When done with work, clean up via {@link #tearDown()}
 * </p>
 * <br>
 */
public class IpmiConnector {

	private IpmiAsyncConnector asyncConnector;

	/**
	 * Upper bound, in milliseconds, of the random pause before resending a request the BMC answered as busy (node busy,
	 * out of resources, initialization in progress).
	 */
	private static final int IDLE_TIME = 4000;

	private volatile int retries = IpmiAsyncConnector.DEFAULT_RETRIES;

	private Random random = new Random(System.currentTimeMillis());

	/**
	 * Starts {@link IpmiConnector} and initiates the {@link ConnectionManager} at the given port. Wildcard IP address
	 * will be used. Keep-alive messages are sent every 30 000 ms ({@link ConnectionManager#DEFAULT_PING_PERIOD}).
	 *
	 * @param port
	 *        - the local UDP port that {@link IpmiAsyncConnector} binds to communicate with the remote hosts, 0 for any
	 *        free port
	 * @throws IOException
	 *         when the port cannot be bound
	 */
	public IpmiConnector(int port) throws IOException {
		asyncConnector = new IpmiAsyncConnector(port);
	}

	/**
	 * Starts {@link IpmiConnector} and initiates the {@link ConnectionManager} at the given port and IP interface.
	 * Keep-alive messages are sent every 30 000 ms ({@link ConnectionManager#DEFAULT_PING_PERIOD}).
	 *
	 * @param port
	 *        - the local UDP port that {@link IpmiAsyncConnector} binds to communicate with the remote hosts, 0 for any
	 *        free port
	 * @param address
	 *        - the IP address that will be used by {@link IpmiAsyncConnector} to communicate with the remote hosts.
	 * @throws IOException
	 *         when the port cannot be bound
	 */
	public IpmiConnector(int port, InetAddress address) throws IOException {
		asyncConnector = new IpmiAsyncConnector(port, address);
	}

	/**
	 * Starts {@link IpmiConnector} and initiates the {@link ConnectionManager} at
	 * the given port. Wildcard IP address will be used.
	 *
	 * @param port the local UDP port that {@link IpmiAsyncConnector} binds to communicate with the remote hosts, 0 for
	 *        any free port
	 * @param pingPeriod the period in milliseconds used to send the keep alive
	 *        messages.<br>
	 *        0 If keep-alive messages should be disabled.
	 * @throws IOException When IpmiAsyncConnector cannot be created.
	 */
	public IpmiConnector(int port, long pingPeriod) throws IOException {
		asyncConnector = new IpmiAsyncConnector(port, pingPeriod);
	}

	/**
	 * Creates connection to the remote host on default IPMI port.
	 *
	 * @param address
	 *        - {@link InetAddress} of the remote host
	 * @return handle to the connection to the remote host
	 * @throws IOException
	 *         when the connection cannot be created
	 */
	public ConnectionHandle createConnection(InetAddress address) throws IOException {
		return createConnection(address, Constants.IPMI_PORT);
	}

	/**
	 * Creates connection to the remote host on specified port.
	 *
	 * @param address
	 *        - {@link InetAddress} of the remote host
	 * @param port
	 *        - remote UDP port
	 * @return handle to the connection to the remote host
	 * @throws IOException
	 *         when the connection cannot be created
	 */
	public ConnectionHandle createConnection(InetAddress address, int port) throws IOException {
		return asyncConnector.createConnection(address, port);
	}

	/**
	 * Creates connection to the remote host, with pre set {@link CipherSuite} and {@link PrivilegeLevel}, skipping the
	 * getAvailableCipherSuites and getChannelAuthenticationCapabilities phases.
	 *
	 * @param address
	 *        - {@link InetAddress} of the remote host
	 * @param cipherSuite
	 *        - the cipher suite of the session
	 * @param privilegeLevel
	 *        - the privilege level requested for the session
	 * @return handle to the connection to the remote host
	 * @throws IOException
	 *         when the connection cannot be created
	 */
	public ConnectionHandle createConnection(InetAddress address, CipherSuite cipherSuite, PrivilegeLevel privilegeLevel)
			throws IOException {
		return createConnection(address, Constants.IPMI_PORT, cipherSuite, privilegeLevel);
	}

	/**
	 * Creates connection to the remote host, with pre set {@link CipherSuite} and {@link PrivilegeLevel}, skipping the
	 * getAvailableCipherSuites and getChannelAuthenticationCapabilities phases.
	 *
	 * @param address
	 *        - {@link InetAddress} of the remote host
	 * @param port
	 *        - remote UDP port
	 * @param cipherSuite
	 *        - the cipher suite of the session
	 * @param privilegeLevel
	 *        - the privilege level requested for the session
	 * @return handle to the connection to the remote host
	 * @throws IOException
	 *         when the connection cannot be created
	 */
	public ConnectionHandle createConnection(
			InetAddress address,
			int port,
			CipherSuite cipherSuite,
			PrivilegeLevel privilegeLevel)
			throws IOException {
		return asyncConnector.createConnection(address, port, cipherSuite, privilegeLevel);
	}

	/**
	 * Gets {@link CipherSuite}s available for the connection with the remote host.
	 *
	 * @param connectionHandle
	 *        {@link ConnectionHandle} to the connection created before
	 * @see #createConnection(InetAddress)
	 * @return list of the {@link CipherSuite}s that are allowed during the connection
	 * @throws Ipmi20UnsupportedException
	 *         when the BMC does not support IPMI 2.0 (RMCP+): Get Channel Authentication Capabilities, sent first in
	 *         the IPMI v1.5 format, reports no IPMI v2.0 connections
	 * @throws Exception
	 *         when sending message to the managed system fails
	 */
	public List<CipherSuite> getAvailableCipherSuites(ConnectionHandle connectionHandle) throws Exception {
		return asyncConnector.getAvailableCipherSuites(connectionHandle);
	}

	/**
	 * Declares the cipher suite and the privilege of the session, and returns the authentication capabilities of the
	 * remote host that {@link #getAvailableCipherSuites(ConnectionHandle)} read (requested at the User level): sends
	 * nothing.
	 *
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the host
	 * @param cipherSuite
	 *        - {@link CipherSuite} that will be used during the connection
	 * @param requestedPrivilegeLevel
	 *        - {@link PrivilegeLevel} that is requested for the session
	 * @return - {@link GetChannelAuthenticationCapabilitiesResponseData}
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation.
	 * @throws Exception
	 *         when the state machine fails
	 */
	public GetChannelAuthenticationCapabilitiesResponseData getChannelAuthenticationCapabilities(
			ConnectionHandle connectionHandle,
			CipherSuite cipherSuite,
			PrivilegeLevel requestedPrivilegeLevel)
			throws Exception {
		return asyncConnector
				.getChannelAuthenticationCapabilities(
						connectionHandle,
						cipherSuite,
						requestedPrivilegeLevel);
	}

	/**
	 * Establishes the session with the remote host, and raises it to the requested privilege when that is Operator or
	 * Administrator (Set Session Privilege Level, IPMI 2.0 section 22.18: every session starts at User). For
	 * {@link PrivilegeLevel#MaximumAvailable}, the session is requested at Administrator, then at Operator and at User
	 * when the BMC refuses the role to the account, and set to User right after the handshake: the caller raises it
	 * when a command needs more.
	 *
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param username
	 *        - the username
	 * @param password
	 *        - password matching the username
	 * @param bmcKey
	 *        - the key that should be provided if the two-key authentication is enabled, null otherwise.
	 * @return object representing newly created {@link Session}
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation, or no reply came after
	 *         all the tries
	 * @throws SessionException
	 *         when the BMC refuses to raise the session to the requested privilege: the session is closed, and the
	 *         cause is the {@link IPMIException} of the completion code
	 * @throws Exception
	 *         when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
	 */
	public Session openSession(ConnectionHandle connectionHandle, String username, String password, byte[] bmcKey)
			throws Exception {
		return asyncConnector.openSession(connectionHandle, username, password, bmcKey);
	}

	/**
	 * Establishes the session with the remote host, with the password as bytes: the caller can then clear them
	 * once the session is open, which an immutable {@link String} does not allow. When the requested privilege is
	 * Operator or Administrator, the session is raised to it with Set Session Privilege Level right after the
	 * handshake (IPMI 2.0 section 22.18: every session starts at User); a refusal closes the session. For
	 * {@link PrivilegeLevel#MaximumAvailable}, the session is requested at Administrator, then at Operator and at User
	 * when the BMC refuses the role to the account, and set to User right after the handshake: the caller raises it
	 * when a command needs more.
	 *
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param username
	 *        - the username
	 * @param password
	 *        - password matching the username, as sent to the BMC (UTF-8 for a text password), at most 20 bytes
	 * @param bmcKey
	 *        - the key that should be provided if the two-key authentication is enabled, null otherwise.
	 * @return object representing newly created {@link Session}
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation, or no reply came after
	 *         all the tries
	 * @throws SessionException
	 *         when the BMC refuses to raise the session to the requested privilege: the session is closed, and the
	 *         cause is the {@link IPMIException} of the completion code
	 * @throws Exception
	 *         when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
	 */
	public Session openSession(ConnectionHandle connectionHandle, String username, byte[] password, byte[] bmcKey)
			throws Exception {
		return asyncConnector.openSession(connectionHandle, username, password, bmcKey);
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
	public Session getExistingSessionForCriteria(InetAddress remoteAddress, int remotePort, String user) {
		return asyncConnector.getExistingSessionForCriteria(remoteAddress, remotePort, user);
	}

	/**
	 * Closes the session with the remote host if it is currently in open state.
	 *
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation.
	 * @throws Exception
	 *         when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
	 */
	public void closeSession(ConnectionHandle connectionHandle) throws Exception {
		asyncConnector.closeSession(connectionHandle);
	}

	/**
	 * Sends the IPMI message to the remote host.
	 *
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param request
	 *        - {@link PayloadCoder} containing the request to be sent
	 * @return {@link ResponseData} for the <b>request</b>
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation.
	 * @throws Exception
	 *         when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
	 */
	public ResponseData sendMessage(ConnectionHandle connectionHandle, PayloadCoder request) throws Exception {
		return sendMessage(connectionHandle, request, true, retries);
	}

	/**
	 * Sends the IPMI command to the remote host and returns its typed response.
	 *
	 * @param <R>
	 *        the type of the response data of the command
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param request
	 *        - the command to send
	 * @return the response data of the command
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation.
	 * @throws IPMIException
	 *         when the BMC answers with an error completion code
	 * @throws Exception
	 *         when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
	 */
	@SuppressWarnings("unchecked")
	public <R extends ResponseData> R sendMessage(ConnectionHandle connectionHandle, IpmiCommandCoder<R> request)
			throws Exception {
		return (R) sendMessage(connectionHandle, request, true, retries);
	}

	/**
	 * Sends the IPMI command to the remote host with its own number of resends when no reply comes, instead of
	 * {@link #getRetries()}: 0 for a command the BMC must not receive twice (a chassis reset, whose reply can be lost
	 * after the BMC carried it out). The limit also covers a {@code C3h} reply (timeout on the BMC side, response
	 * unavailable), which says nothing about what was done; a busy reply, which says the command was not carried
	 * out, is still retried {@link #getRetries()} times.
	 *
	 * @param <R>
	 *        the type of the response data of the command
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param request
	 *        - the command to send
	 * @param resends
	 *        how many times the command is sent again when no reply comes within the timeout, 0 to send it once
	 * @return the response data of the command
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation, or no reply came
	 * @throws IPMIException
	 *         when the BMC answers with an error completion code
	 * @throws Exception
	 *         when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
	 */
	@SuppressWarnings("unchecked")
	public <R extends ResponseData> R sendMessage(
			ConnectionHandle connectionHandle,
			IpmiCommandCoder<R> request,
			int resends)
			throws Exception {
		if (resends < 0) {
			throw new IllegalArgumentException("resends must not be negative: " + resends);
		}
		return (R) sendMessage(connectionHandle, request, true, resends);
	}

	/**
	 * Sends the IPMI message to the remote host and doesn't wait for any response.
	 *
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param request
	 *        - {@link PayloadCoder} containing the request to be sent
	 * @throws ConnectionException
	 *         when connection is in the state that does not allow to perform this operation.
	 * @throws Exception
	 *         when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
	 */
	public void sendOneWayMessage(ConnectionHandle connectionHandle, PayloadCoder request) throws Exception {
		sendMessage(connectionHandle, request, false, retries);
	}

	/**
	 * Re-sends message with given tag having given {@link PayloadType}, using passed {@link ConnectionHandle}.
	 *
	 * @param connectionHandle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param tag
	 *        - tag of the message to retry
	 * @param messagePayloadType
	 *        - {@link PayloadType} of the message that should be retried
	 * @return {@link ResponseData} for the re-sent request or null if message could not be resent.
	 * @throws Exception
	 *         when sending message to the managed system fails
	 */
	public ResponseData retryMessage(ConnectionHandle connectionHandle, byte tag, PayloadType messagePayloadType)
			throws Exception {
		MessageListener listener = new MessageListener(connectionHandle);
		asyncConnector.registerListener(listener);
		try {
			int retryResult = asyncConnector.retry(connectionHandle, tag, messagePayloadType);

			return retryResult != -1 ? listener.waitForAnswer(tag) : null;
		} finally {
			asyncConnector.unregisterListener(listener);
		}
	}

	private ResponseData sendMessage(
			ConnectionHandle connectionHandle,
			PayloadCoder request,
			boolean waitForResponse,
			int resends)
			throws Exception {
		MessageListener listener = new MessageListener(connectionHandle);

		if (!waitForResponse) {
			return sendThroughAsyncConnector(request, connectionHandle, listener, false, resends);
		}

		// Unregistered whatever the outcome: a listener left behind would keep every later reply of the connection
		asyncConnector.registerListener(listener);
		try {
			return sendThroughAsyncConnector(request, connectionHandle, listener, true, resends);
		} finally {
			asyncConnector.unregisterListener(listener);
		}
	}

	private ResponseData sendThroughAsyncConnector(
			PayloadCoder request,
			ConnectionHandle connectionHandle,
			MessageListener listener,
			boolean waitForResponse,
			int resends)
			throws Exception {
		ResponseData responseData = null;

		int tries = 0;
		boolean messageSent = false;

		while (!messageSent) {
			try {
				++tries;

				// A fresh tag for every try: the request that timed out or was answered left the queue, or is about
				// to, and a reply to the same tag would then be dropped as an orphan
				int tag = asyncConnector.sendMessage(connectionHandle, request, !waitForResponse);

				if (waitForResponse) {
					responseData = listener.waitForAnswer(tag);
				}

				messageSent = true;
			} catch (IllegalArgumentException | IllegalStateException | InterruptedException e) {
				// A wrong request, a closed connection, an interruption: sending it again cannot help
				throw e;
			} catch (IPMIException e) {
				handleErrorResponse(tries, e, resends);
			} catch (Exception e) {
				// No reply in time: the BMC already had the whole message timeout, the request is sent again at once
				handleRetriesWhenException(tries, e, false, resends);
			}
		}

		return responseData;
	}

	/**
	 * Throws the exception when the request was already sent again {@code limit} times (limit + 1 tries), otherwise
	 * lets the caller send it again, after a random pause of up to {@link #IDLE_TIME} ms if asked.
	 */
	private void handleRetriesWhenException(int tries, Exception e, boolean pause, int limit) throws Exception {
		if (tries > limit) {
			throw e;
		}
		if (pause) {
			long sleepTime = (random.nextLong() % (IDLE_TIME / 2)) + (IDLE_TIME / 2);
			Thread.sleep(sleepTime);
		}
	}

	/**
	 * Retries a request the BMC answered with a transient completion code: after a pause when the BMC says it is busy
	 * (it did not carry the request out: up to {@link #retries} times whatever the request), at once after a timeout
	 * on its side (C3h), where it already waited for the device it could not reach. C3h says the response is
	 * unavailable, not that nothing was done, so it is retried {@code resends} times like a lost reply.
	 */
	private void handleErrorResponse(int tries, IPMIException e, int resends) throws Exception {
		if (e.getCompletionCode() == CompletionCode.Timeout) {
			handleRetriesWhenException(tries, e, false, resends);
		} else if (e.getCompletionCode().isTransient()) {
			handleRetriesWhenException(tries, e, true, retries);
		} else {
			throw e;
		}
	}

	/**
	 * Registers {@link InboundMessageListener} that will react on any request sent from remote system to the application.
	 *
	 * @param listener
	 *        listener to be registered.
	 */
	public void registerIncomingMessageListener(InboundMessageListener listener) {
		asyncConnector.registerIncomingPayloadListener(listener);
	}

	/**
	 * Unregisters an {@link InboundMessageListener}, which then no longer receives the requests of the remote systems.
	 *
	 * @param listener
	 *        listener to be unregistered.
	 */
	public void unregisterIncomingMessageListener(InboundMessageListener listener) {
		asyncConnector.unregisterIncomingPayloadListener(listener);
	}

	/**
	 * Closes the connection with the given handle, without closing its session: the requests still waiting for a
	 * reply on the connection fail, with a {@link ConnectionException} or an {@link IllegalStateException}.
	 *
	 * @param handle the handle of the connection
	 */
	public void closeConnection(ConnectionHandle handle) {
		asyncConnector.closeConnection(handle);
	}

	/**
	 * Finalizes the connector and closes all connections, without closing their sessions: the requests still waiting
	 * for a reply fail, with a {@link ConnectionException} or an {@link IllegalStateException}.
	 */
	public void tearDown() {
		asyncConnector.tearDown();
	}

	/**
	 * Changes the timeout value for connection with the given handle.
	 *
	 * @param handle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @param timeout
	 *        - new timeout value in ms
	 */
	public void setTimeout(ConnectionHandle handle, int timeout) {
		asyncConnector.setTimeout(handle, timeout);
	}

	/**
	 * Returns the timeout of a single message on the connection with the given handle.
	 *
	 * @param handle
	 *        - {@link ConnectionHandle} associated with the remote host.
	 * @return the timeout in ms after which a message without a reply is reported as timed out
	 */
	public int getTimeout(ConnectionHandle handle) {
		return asyncConnector.getTimeout(handle);
	}

	/**
	 * Returns how many times a message without a reply is sent again.
	 *
	 * @return the number of resends
	 */
	public int getRetries() {
		return retries;
	}

	/**
	 * Sets how many times a message without a reply, or a handshake step without a reply, is sent again.
	 *
	 * @param retries the number of resends, 0 to send every message once
	 * @throws IllegalArgumentException when negative
	 */
	public void setRetries(int retries) {
		asyncConnector.setRetries(retries);
		this.retries = retries;
	}

	/**
	 * Sets the timeout of the messages of the connections created from now on; {@link #setTimeout(ConnectionHandle, int)}
	 * changes it for an existing connection. 5 000 ms by default.
	 *
	 * @param timeout the time in milliseconds after which a message without a reply is reported as timed out
	 * @throws IllegalArgumentException when the timeout is less than one millisecond
	 */
	public void setTimeout(int timeout) {
		asyncConnector.setTimeout(timeout);
	}
}
