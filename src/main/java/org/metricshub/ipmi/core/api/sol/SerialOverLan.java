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
import org.metricshub.ipmi.core.api.async.InboundSolMessageListener;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.payload.ActivateSolPayload;
import org.metricshub.ipmi.core.coding.commands.payload.ActivateSolPayloadResponseData;
import org.metricshub.ipmi.core.coding.commands.payload.DeactivatePayload;
import org.metricshub.ipmi.core.coding.commands.payload.GetPayloadActivationStatus;
import org.metricshub.ipmi.core.coding.commands.payload.GetPayloadActivationStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.payload.GetPayloadInstanceInfo;
import org.metricshub.ipmi.core.coding.commands.payload.GetPayloadInstanceInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.session.SetSessionPrivilegeLevel;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.sol.SolAckState;
import org.metricshub.ipmi.core.coding.payload.sol.SolMessage;
import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;
import org.metricshub.ipmi.core.coding.payload.sol.SolStatus;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.sol.SolCoder;
import org.metricshub.ipmi.core.coding.sol.SolResponseData;
import org.metricshub.ipmi.core.common.Constants;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.connection.ConnectionException;
import org.metricshub.ipmi.core.connection.Session;
import org.metricshub.ipmi.core.connection.SessionException;
import org.metricshub.ipmi.core.connection.SessionManager;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Serial over LAN (SOL) console in a session of the protocol core: activates the SOL payload, reads and writes the
 * serial port of the server, and invokes the serial port operations. Applications that use
 * {@link org.metricshub.ipmi.client.IpmiClient} open a console with {@code sol().open()} instead.
 */
public class SerialOverLan implements Closeable {

	/**
	 * The most characters a packet carries: the BMC acknowledges them with a count of one byte (IPMI 2.0 table 15-2).
	 */
	private static final int MAX_PACKET_CHARACTERS = 255;

	/**
	 * The pause before sending again a packet that the BMC NACKed, multiplied by the number of the retry.
	 */
	private static final long NACK_PAUSE_MS = 100;

	/**
	 * Completion code of Activate Payload: the payload is already active in a session (IPMI 2.0 table 24-2).
	 */
	private static final int PAYLOAD_ALREADY_ACTIVE = 0x80;

	/**
	 * The operations that are serial line states, which the console holds in every packet (IPMI 2.0 table 15-2).
	 */
	private static final Set<SolOperation> LINE_OPERATIONS = Collections
			.unmodifiableSet(EnumSet.of(SolOperation.RingWOR, SolOperation.CTS, SolOperation.DCD_DSR));

	/**
	 * One write at a time: SOL allows a single packet in flight (IPMI 2.0 table 15-2), and the packets of a write must
	 * not mix with those of another.
	 */
	private final Object writeLock = new Object();

	private final IpmiConnector connector;
	private final Session session;
	private final InboundSolMessageListener inboundMessageListener;
	/**
	 * Notified on the receiving thread of the connector while the application may register or unregister listeners.
	 */
	private final List<SolEventListener> eventListeners = new CopyOnWriteArrayList<SolEventListener>();

	/**
	 * Whether this console owns the connector, which closing the console tears down: the case of the constructors that
	 * open their own session.
	 */
	private final boolean ownsConnector;

	/**
	 * The connections whose session this console opened, which closing the console closes.
	 */
	private final List<ConnectionHandle> ownedConnections = new ArrayList<ConnectionHandle>();

	/**
	 * The connection of the session where the payload was last activated, or null, and the instance activated there.
	 */
	private ConnectionHandle activatedConnection;
	/** The level the console raised a MaximumAvailable session of the payload to, or null. */
	private PrivilegeLevel raisedTo;
	private int activatedInstance;

	/**
	 * The session where Activate Payload was last sent, whether it was answered or not.
	 */
	private Session activationSession;
	private int payloadInstance;
	private int maxPayloadSize;
	private boolean closed;

	/**
	 * Creates connection with IPMI using given {@link IpmiConnector}, connected to remote machine on given address and
	 * port, and opens a new {@link Session} for SOL communication. The console owns the connector: closing the console,
	 * or failing to open it, tears the connector down.
	 *
	 * @param connector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param remoteHost
	 *        the host name or IP address of the BMC
	 * @param remotePort
	 *        UDP port number of the remote server
	 * @param user
	 *        IPMI user name
	 * @param password
	 *        IPMI password
	 * @param cipherSuiteSelectionHandler
	 *        {@link CipherSuiteSelectionHandler} that will allow to select {@link CipherSuite} among available ones.
	 * @throws SOLException when the SOL payload cannot be activated
	 * @throws SessionException when the session cannot be opened, or the account is not allowed the Administrator
	 *         privilege
	 */
	public SerialOverLan(IpmiConnector connector, String remoteHost, int remotePort, String user, String password,
			CipherSuiteSelectionHandler cipherSuiteSelectionHandler) throws SOLException, SessionException {
		this(connector, remoteHost, remotePort, user, password, null, cipherSuiteSelectionHandler);
	}

	/**
	 * Creates connection with IPMI using given {@link IpmiConnector}, connected to remote machine on given address and
	 * port, and opens a new {@link Session} for SOL communication, with the BMC key of a BMC configured for two-key
	 * logins. The console owns the connector: closing the console, or failing to open it, tears the connector down.
	 *
	 * @param connector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param remoteHost
	 *        the host name or IP address of the BMC
	 * @param remotePort
	 *        UDP port number of the remote server
	 * @param user
	 *        IPMI user name
	 * @param password
	 *        IPMI password, sent in UTF-8
	 * @param bmcKey
	 *        the BMC key (Kg) as raw bytes, at most 20, or null for a one-key login
	 * @param cipherSuiteSelectionHandler
	 *        {@link CipherSuiteSelectionHandler} that will allow to select {@link CipherSuite} among available ones.
	 * @throws SOLException when the SOL payload cannot be activated
	 * @throws SessionException when the session cannot be opened, or the account is not allowed the Administrator
	 *         privilege
	 */
	public SerialOverLan(IpmiConnector connector, String remoteHost, int remotePort, String user, String password,
			byte[] bmcKey, CipherSuiteSelectionHandler cipherSuiteSelectionHandler) throws SOLException, SessionException {
		this(
				connector,
				openSession(connector, remoteHost, remotePort, user, password, bmcKey, cipherSuiteSelectionHandler),
				true);
	}

	/**
	 * Creates connection with IPMI using given {@link IpmiConnector}, connected to remote machine on given address and
	 * default IPMI port, and opens a new {@link Session} for SOL communication. The console owns the connector: closing
	 * the console, or failing to open it, tears the connector down.
	 *
	 * @param connector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param remoteHost
	 *        the host name or IP address of the BMC
	 * @param user
	 *        IPMI user name
	 * @param password
	 *        IPMI password
	 * @param cipherSuiteSelectionHandler
	 *        {@link CipherSuiteSelectionHandler} that will allow to select {@link CipherSuite} among available ones.
	 * @throws SOLException when the SOL payload cannot be activated
	 * @throws SessionException when the session cannot be opened, or the account is not allowed the Administrator
	 *         privilege
	 */
	public SerialOverLan(IpmiConnector connector, String remoteHost, String user, String password,
			CipherSuiteSelectionHandler cipherSuiteSelectionHandler) throws SOLException, SessionException {
		this(connector, remoteHost, Constants.IPMI_PORT, user, password, cipherSuiteSelectionHandler);
	}

	/**
	 * Tries to open SOL communication on given existing session. When it appears that separate session must be opened
	 * to handle SOL messages (for example SOL payload must be activated on separate port), the new connection and session
	 * are automatically established. Closing the console leaves the given session and the connector open, and closes
	 * the session it opened, if any.
	 *
	 * @param connector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param session
	 *        Existing session that should be reused (if possible) for SOL communication.
	 * @throws SOLException when the SOL payload cannot be activated
	 * @throws SessionException when the session on the SOL payload port cannot be opened
	 */
	public SerialOverLan(IpmiConnector connector, Session session) throws SOLException, SessionException {
		this(connector, session, false);
	}

	/**
	 * Activates the SOL payload in the given session, or in a session on the port where the BMC serves it, and releases
	 * what it opened when it fails.
	 *
	 * @param ownsSession whether the console opened the given session, and then owns the connector too
	 */
	private SerialOverLan(IpmiConnector connector, Session session, boolean ownsSession)
			throws SOLException, SessionException {
		this.connector = connector;
		this.ownsConnector = ownsSession;
		if (ownsSession) {
			ownedConnections.add(session.getConnectionHandle());
		}

		try {
			int solPayloadPort = activatePayload(connector, session);

			this.session = resolveSession(connector, session, solPayloadPort);

			this.inboundMessageListener = new InboundSolMessageListener(
					connector,
					this.session.getConnectionHandle(),
					eventListeners);
			connector.registerIncomingMessageListener(inboundMessageListener);
		} catch (SOLException | SessionException | RuntimeException e) {
			deactivatePayloadOfOthers();
			release();
			throw e;
		}
	}

	/**
	 * Opens the session of a console that owns its connector, and tears the connector down when the session cannot be
	 * opened.
	 */
	private static Session openSession(
			IpmiConnector connector,
			String remoteHost,
			int remotePort,
			String user,
			String password,
			byte[] bmcKey,
			CipherSuiteSelectionHandler cipherSuiteSelectionHandler)
			throws SessionException {
		try {
			return SessionManager
					.establishSession(
							connector,
							remoteHost,
							remotePort,
							user,
							password == null ? null : password.getBytes(StandardCharsets.UTF_8),
							bmcKey,
							cipherSuiteSelectionHandler);
		} catch (SessionException | RuntimeException e) {
			connector.tearDown();
			throw e;
		}
	}

	/**
	 * Given potential session object and port on which SOL should be activated, decides what session should be finally
	 * used to SOL communication.
	 *
	 * @param ipmiConnector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param currentSession
	 *        Existing session that should be reused (if possible) for SOL communication.
	 * @param solPayloadPort
	 *        UDP port on which managed system listens for SOL communication.
	 * @return Actual session (existing or newly established), that should be used for SOL communication.
	 * @throws SOLException
	 *         If any unrecoverable error occurs.
	 * @throws SessionException
	 *         If new session could not be established.
	 */
	private Session resolveSession(IpmiConnector ipmiConnector, Session currentSession, int solPayloadPort)
			throws SOLException,
			SessionException {
		ConnectionHandle connectionHandle = currentSession.getConnectionHandle();
		if (solPayloadPort == connectionHandle.getRemotePort()) {
			return currentSession;
		}

		// The payload is actually activated in a session on the port of the payload, opened with the same credentials
		// (IPMI 2.0 sections 15.10 and 24.1), or in the session already open there
		Session alternativeSession = ipmiConnector
				.getExistingSessionForCriteria(
						connectionHandle.getRemoteAddress(),
						solPayloadPort,
						connectionHandle.getUser());

		if (alternativeSession == null) {
			// The credentials, suite and privilege level of the session given to the console, whose level may be enough
			// for the payload
			alternativeSession = SessionManager.establishSession(ipmiConnector, connectionHandle, solPayloadPort);
			ownedConnections.add(alternativeSession.getConnectionHandle());
		}

		activatePayload(ipmiConnector, alternativeSession);

		return alternativeSession;
	}

	/**
	 * Tries to activate SOL payload in the given session. When the BMC refuses it for insufficient privilege in a
	 * session opened with {@link PrivilegeLevel#MaximumAvailable}, which {@code openSession()} sets to User, raises the
	 * session to Operator, then to Administrator, and tries again after each raise.
	 *
	 * @param ipmiConnector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param payloadSession
	 *        the session where the payload is activated
	 * @return UDP port number on which managed system listens for SOL messages.
	 * @throws SOLException
	 *         when any unrecoverable error occurred.
	 */
	private int activatePayload(IpmiConnector ipmiConnector, Session payloadSession) throws SOLException {
		ConnectionHandle connectionHandle = payloadSession.getConnectionHandle();
		try {
			this.payloadInstance = getFirstAvailablePayloadInstance(ipmiConnector, connectionHandle);
			this.activationSession = payloadSession;

			ActivateSolPayload activatePayload = new ActivateSolPayload(payloadInstance);
			ActivateSolPayloadResponseData activatePayloadResponseData = getActivatePayloadResponse(
					ipmiConnector,
					payloadSession,
					activatePayload);
			this.activatedConnection = connectionHandle;
			this.activatedInstance = payloadInstance;

			this.maxPayloadSize = activatePayloadResponseData.getInboundPayloadSize();
			if (maxPayloadSize <= SolMessage.PAYLOAD_HEADER_LENGTH) {
				throw new SOLException(
						"The BMC announced an inbound SOL payload size of " + maxPayloadSize
								+ " bytes, which leaves no room for character data");
			}

			return activatePayloadResponseData.getPayloadUdpPortNumber();

		} catch (SOLException e) {
			throw e;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SOLException("Cannot activate SOL payload due to exception during activation process", e);
		}
	}

	/**
	 * Sends Activate Payload, and again after raising the privilege of a session opened with
	 * {@link PrivilegeLevel#MaximumAvailable} when it is too low (to Operator, then to Administrator, as far as the
	 * account allows), or after deactivating the payload when a lost reply left it active in this session.
	 * {@code openSession()} raised a session opened at Operator or Administrator, and a User session cannot go higher;
	 * the session the console opens on the port of the payload has the level of the given session, so a
	 * MaximumAvailable one is raised here too.
	 */
	private ActivateSolPayloadResponseData getActivatePayloadResponse(
			IpmiConnector ipmiConnector,
			Session payloadSession,
			ActivateSolPayload activatePayload)
			throws Exception {
		ConnectionHandle connectionHandle = payloadSession.getConnectionHandle();
		boolean onDemand = connectionHandle.getPrivilegeLevel() == PrivilegeLevel.MaximumAvailable;
		PrivilegeLevel level = PrivilegeLevel.User;
		boolean deactivated = false;
		while (true) {
			try {
				return ipmiConnector.sendMessage(connectionHandle, activatePayload);
			} catch (IPMIException e) {
				boolean alreadyActive = !deactivated && e.getRawCode() == PAYLOAD_ALREADY_ACTIVE;
				if (onDemand
						&& level != PrivilegeLevel.Administrator
						&& e.getCompletionCode() == CompletionCode.InsufficientPrivilege) {
					level = level == PrivilegeLevel.User ? PrivilegeLevel.Operator : PrivilegeLevel.Administrator;
					ipmiConnector.sendMessage(connectionHandle, new SetSessionPrivilegeLevel(level));
					raisedTo = level;
				} else if (alreadyActive && isActiveInSession(ipmiConnector, payloadSession)) {
					// The reply to Activate Payload was lost, and the request sent again found the payload active in this
					// session: the port and the sizes of the payload are only in that reply, which a new activation gives
					deactivatePayload(connectionHandle, payloadInstance);
					deactivated = true;
				} else {
					throw e;
				}
			}
		}
	}

	/**
	 * Tells whether the payload instance is active in the given session (Get Payload Instance Info, IPMI 2.0 section
	 * 24.5), rather than in the session of another console, which must keep it.
	 */
	private boolean isActiveInSession(IpmiConnector ipmiConnector, Session payloadSession) throws Exception {
		ConnectionHandle connectionHandle = payloadSession.getConnectionHandle();
		try {
			GetPayloadInstanceInfoResponseData info = ipmiConnector
					.sendMessage(
							connectionHandle,
							new GetPayloadInstanceInfo(PayloadType.Sol, payloadInstance));
			return info.getSessionId() != 0 && info.getSessionId() == payloadSession.getManagedSystemSessionId();
		} catch (IPMIException e) {
			// The command is optional
			return false;
		}
	}

	/**
	 * Checks for available SOL payload instances and, if any, returns first available.
	 *
	 * @param ipmiConnector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param connectionHandle
	 *        {@link ConnectionHandle} representing single connection to managed system.
	 * @return number of first available SOL payload instance.
	 * @throws Exception
	 *         If any exception occurred during communication or if no available instances were found.
	 */
	private int getFirstAvailablePayloadInstance(IpmiConnector ipmiConnector, ConnectionHandle connectionHandle)
			throws Exception {
		GetPayloadActivationStatus getPayloadActivationStatus = new GetPayloadActivationStatus(PayloadType.Sol);
		GetPayloadActivationStatusResponseData getActivationResponseData = ipmiConnector
				.sendMessage(
						connectionHandle,
						getPayloadActivationStatus);

		if (getActivationResponseData.getInstanceCapacity() <= 0
				|| getActivationResponseData.getAvailableInstances().isEmpty()) {
			throw new SOLException("Cannot activate SOL payload, as there are no available payload instances.");
		}

		return TypeConverter.byteToInt(getActivationResponseData.getAvailableInstances().get(0));
	}

	/**
	 * Returns the privilege the console raised the session of the payload to, when that session was opened with
	 * {@link PrivilegeLevel#MaximumAvailable} and the BMC refused to activate the payload at its level: the owner of the
	 * session (see {@link #getConnectionHandle()}) runs it at that level from then on.
	 *
	 * @return Operator or Administrator, or null when the console did not raise the session
	 */
	public PrivilegeLevel getRaisedPrivilegeLevel() {
		return raisedTo;
	}

	/**
	 * Returns the connection the console exchanges its packets on: the one of the session it was given, or the one
	 * of the session it opened on the port of the payload when the BMC serves SOL on another port.
	 *
	 * @return the connection handle of the payload session
	 */
	public ConnectionHandle getConnectionHandle() {
		return session.getConnectionHandle();
	}

	/**
	 * Writes single byte to the port.
	 * This operation blocks until all data can be sent to remote server and is either accepted or rejected by the server.
	 *
	 * @param singleByte
	 *        a byte to write
	 * @return true if byte was successfully sent and acknowledged by remote server, false otherwise.
	 */
	public boolean writeByte(byte singleByte) {
		return writeBytes(new byte[] { singleByte });
	}

	/**
	 * Writes bytes array to the port, in packets of the size the BMC accepts.
	 * This operation blocks until all data can be sent to remote server and is either accepted or rejected by the server.
	 * When it fails, the server may have received the first bytes already.
	 *
	 * @param buffer
	 *        an array of bytes to write
	 * @return true if all bytes were successfully sent and acknowledged by remote server, false otherwise.
	 */
	public boolean writeBytes(byte[] buffer) {
		int packetSize = Math.min(maxPayloadSize - SolMessage.PAYLOAD_HEADER_LENGTH, MAX_PACKET_CHARACTERS);
		synchronized (writeLock) {
			for (int from = 0; from < buffer.length; from += packetSize) {
				if (!sendMessage(Arrays.copyOfRange(buffer, from, Math.min(from + packetSize, buffer.length)))) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Writes single integer (in range from 0 to 255) to the port.
	 * This operation blocks until all data can be sent to remote server and is either accepted or rejected by the server.
	 *
	 * @param singleInt
	 *        an integer value to write (must be in range from 0 to 255)
	 * @return true if integer was successfully sent and acknowledged by remote server, false otherwise.
	 */
	public boolean writeInt(int singleInt) {
		return writeBytes(new byte[] { TypeConverter.intToByte(singleInt) });
	}

	/**
	 * Writes integers (in range from 0 to 255) array to the port.
	 * This operation blocks until all data can be sent to remote server and is either accepted or rejected by the server.
	 *
	 * @param buffer
	 *        an array of integer values to write (each must be in range from 0 to 255)
	 * @return true if all integers were successfully sent and acknowledged by remote server, false otherwise.
	 */
	public boolean writeIntArray(int[] buffer) {
		byte[] byteBuffer = new byte[buffer.length];

		for (int i = 0; i < buffer.length; i++) {
			byteBuffer[i] = TypeConverter.intToByte(buffer[i]);
		}

		return writeBytes(byteBuffer);
	}

	/**
	 * Writes {@link String} to port, using platform's default {@link Charset} when converting {@link String} to byte
	 * array.
	 * This operation blocks until all data can be sent to remote server and is either accepted or rejected by the server.
	 *
	 * @param string
	 *        a string to write to the port.
	 * @return true if whole string was successfully sent and acknowledged by remote server, false otherwise.
	 */
	public boolean writeString(String string) {
		return writeString(string, Charset.defaultCharset());
	}

	/**
	 * Writes {@link String} to port, using given {@link Charset} when converting {@link String} to byte array.
	 * This operation blocks until all data can be sent to remote server and is either accepted or rejected by the server.
	 *
	 * @param string
	 *        a string to write to port
	 * @param charset
	 *        {@link Charset} that the string is encoded in
	 * @return true if whole string was successfully sent and acknowledged by remote server, false otherwise.
	 */
	public boolean writeString(String string, Charset charset) {
		return writeBytes(string.getBytes(charset));
	}

	/**
	 * Read all available bytes from the port.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @return all bytes that could be read or empty array if no bytes were available.
	 */
	public byte[] readBytes() {
		return readBytes(inboundMessageListener.getAvailableBytesCount());
	}

	/**
	 * Reads at max given number of bytes from the port.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @param byteCount
	 *        maximum number of bytes that should be read
	 * @return byte array containing bytes that could be read, but no more than given byteCount.
	 *         Returns empty array if no bytes were available.
	 */
	public byte[] readBytes(int byteCount) {
		return inboundMessageListener.readBytes(byteCount);
	}

	/**
	 * Reads at max given number of bytes from the port.
	 * This operation blocks until given number of bytes is available to be read, the buffer of 64 KiB is full, or
	 * the given timeout is hit.
	 *
	 * @param byteCount
	 *        maximum number of bytes that should be read.
	 * @param timeout
	 *        maximum time in milliseconds that we want to wait for all available bytes
	 * @return byte array containing bytes that could be read, but no more than byteCount.
	 *         When the timeout is hit, returns just bytes that were available or empty array if no bytes were available.
	 */
	public byte[] readBytes(int byteCount, int timeout) {
		return inboundMessageListener.readBytes(byteCount, timeout);
	}

	/**
	 * Reads all available bytes from the port as integer (in range from 0 to 255) values array.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @return all bytes that could be read as int array (each value in range from 0 to 255) or empty array if no data was
	 *         available.
	 */
	public int[] readIntArray() {
		return readIntArray(inboundMessageListener.getAvailableBytesCount());
	}

	/**
	 * Reads at max given number of integer values (in range from 0 to 255) from the port.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @param byteCount
	 *        maximum number of bytes that should be read
	 * @return integer array containing integer values that could be read, but no more than given byteCount (each value in
	 *         range from 0 to 255).
	 *         Returns empty array if no data was available.
	 */
	public int[] readIntArray(int byteCount) {
		return toIntArray(readBytes(byteCount));
	}

	private static int[] toIntArray(byte[] bytesArray) {
		int[] intArray = new int[bytesArray.length];

		for (int i = 0; i < bytesArray.length; i++) {
			intArray[i] = TypeConverter.byteToInt(bytesArray[i]);
		}

		return intArray;
	}

	/**
	 * Reads at max given number of integer values (in range from 0 to 255) from the port.
	 * This operation blocks until given number of bytes is available to be read, the buffer of 64 KiB is full, or
	 * the given timeout is hit.
	 *
	 * @param byteCount
	 *        maximum number of bytes that should be read.
	 * @param timeout
	 *        maximum time in milliseconds that we want to wait for all available data
	 * @return integer array containing integer values that could be read, but no more than byteCount (each value in range
	 *         from 0 to 255).
	 *         When the timeout is hit, returns just data that was available or empty array if no data was available.
	 */
	public int[] readIntArray(int byteCount, int timeout) {
		return toIntArray(readBytes(byteCount, timeout));
	}

	/**
	 * Read all available bytes from the port and converts them to {@link String} using platform's default
	 * {@link Charset}.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @return all bytes that could be read as {@link String}.
	 */
	public String readString() {
		return readString(inboundMessageListener.getAvailableBytesCount());
	}

	/**
	 * Reads at max given number of bytes from the port, converting them to {@link String} using platform's default
	 * {@link Charset}.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @param byteCount
	 *        maximum number of bytes that should be read
	 * @return all bytes that could be read as {@link String}, but no more than given byteCount.
	 */
	public String readString(int byteCount) {
		return readString(Charset.defaultCharset(), byteCount);
	}

	/**
	 * Reads at max given number of bytes from the port, converting them to {@link String} using platform's default
	 * {@link Charset}.
	 * This operation blocks until given number of bytes is available to be read, the buffer of 64 KiB is full, or
	 * the given timeout is hit.
	 *
	 * @param byteCount
	 *        maximum number of bytes that should be read.
	 * @param timeout
	 *        maximum time in milliseconds that we want to wait for all available bytes
	 * @return all bytes that could be read as {@link String}, but no more than given byteCount.
	 *         When the timeout is hit, returns just bytes that were available.
	 */
	public String readString(int byteCount, int timeout) {
		return readString(Charset.defaultCharset(), byteCount, timeout);
	}

	/**
	 * Read all available bytes from the port and converts them to {@link String} using given {@link Charset}.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @param charset
	 *        {@link Charset} that will be used when converting bytes to {@link String}
	 * @return all bytes that could be read as {@link String}.
	 */
	public String readString(Charset charset) {
		return readString(charset, inboundMessageListener.getAvailableBytesCount());
	}

	/**
	 * Reads at max given number of bytes from the port, converting them to {@link String} using given {@link Charset}.
	 * Returns immediately, without waiting for data to be available.
	 *
	 * @param charset
	 *        {@link Charset} that will be used when converting bytes to {@link String}
	 * @param byteCount
	 *        maximum number of bytes that should be read
	 * @return all bytes that could be read as {@link String}, but no more than given byteCount.
	 */
	public String readString(Charset charset, int byteCount) {
		return new String(readBytes(byteCount), charset);
	}

	/**
	 * Reads at max given number of bytes from the port, converting them to {@link String} using given {@link Charset}.
	 * This operation blocks until given number of bytes is available to be read, the buffer of 64 KiB is full, or
	 * the given timeout is hit.
	 *
	 * @param charset
	 *        {@link Charset} that will be used when converting bytes to {@link String}
	 * @param byteCount
	 *        maximum number of bytes that should be read.
	 * @param timeout
	 *        maximum time in milliseconds that we want to wait for all available bytes
	 * @return all bytes that could be read as {@link String}, but no more than given byteCount.
	 *         When the timeout is hit, returns just bytes that were available.
	 */
	public String readString(Charset charset, int byteCount, int timeout) {
		return new String(readBytes(byteCount, timeout), charset);
	}

	/**
	 * Invokes given SOL-specific operations on remote serial port. {@link SolOperation#Break},
	 * {@link SolOperation#FlushInbound} and {@link SolOperation#FlushOutbound} act once; {@link SolOperation#CTS},
	 * {@link SolOperation#DCD_DSR} and {@link SolOperation#RingWOR} are line states, which the console holds in every
	 * packet it sends until the next call: call this method without them to release them.
	 *
	 * @param operations
	 *        a bunch of {@link SolOperation}s that should be invoked
	 * @return true if the packet was sent (its acknowledgement is not awaited: many BMCs never acknowledge a packet
	 *         without characters), false otherwise.
	 */
	public boolean invokeOperations(SolOperation... operations) {
		Set<SolOperation> operationSet = EnumSet.noneOf(SolOperation.class);
		operationSet.addAll(Arrays.asList(operations));
		Set<SolOperation> lineOperations = EnumSet.noneOf(SolOperation.class);
		lineOperations.addAll(operationSet);
		lineOperations.retainAll(LINE_OPERATIONS);

		synchronized (writeLock) {
			inboundMessageListener.setLineOperations(lineOperations);
			return sendMessage(operationSet);
		}
	}

	/**
	 * Registers new {@link SolEventListener} that will be informed about SOL events fired by remote system.
	 *
	 * @param listener
	 *        listener to be registered
	 */
	public void registerEventListener(SolEventListener listener) {
		eventListeners.add(listener);
	}

	/**
	 * Unregister given {@link SolEventListener}, preventing it from receiving SOL events from remote server.
	 *
	 * @param listener
	 *        listener to be unregistered
	 */
	public void unregisterEventListener(SolEventListener listener) {
		eventListeners.remove(listener);
	}

	/**
	 * Sends characters in a packet, then what the BMC did not accept of it in new packets (IPMI 2.0 table 15-2).
	 */
	private boolean sendMessage(byte[] characterData) {
		Set<SolOperation> lineOperations = inboundMessageListener.getLineOperations();
		SolResponseData responseData = sendPayload(
				characterPacket(characterData, lineOperations),
				characterData,
				lineOperations);

		byte[] remainingCharacterData = characterData;

		while (isNackForMessagePart(responseData, remainingCharacterData.length)) {
			remainingCharacterData = Arrays
					.copyOfRange(
							remainingCharacterData,
							responseData.getAcceptedCharactersNumber(),
							remainingCharacterData.length);

			responseData = sendPayload(
					characterPacket(remainingCharacterData, lineOperations),
					remainingCharacterData,
					lineOperations);
		}

		return responseData != null && responseData.getAcknowledgeState() == SolAckState.ACK;
	}

	/**
	 * @return a packet of characters, with the line states the console holds
	 */
	private SolCoder characterPacket(byte[] characterData, Set<SolOperation> lineOperations) {
		return new SolCoder(
				characterData,
				(byte) 0,
				(byte) 0,
				SolAckState.ACK,
				lineOperations);
	}

	/**
	 * Sends a packet of operations, without characters, and does not wait for its acknowledgement: many BMCs never
	 * acknowledge a packet without characters (seen on Supermicro, Cisco IMC and Fujitsu iRMC), and the BMC executes the
	 * operations even when it NACKs the packet (IPMI 2.0 table 15-2).
	 */
	private boolean sendMessage(Set<SolOperation> operations) {
		try {
			connector
					.sendOneWayMessage(
							session.getConnectionHandle(),
							new SolCoder(operations));
			return true;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	/**
	 * Reports the statuses of an answer of the BMC that were not reported for the packet yet.
	 */
	private void notifyResponseListeners(
			byte[] characterData,
			Set<SolOperation> solOperations,
			SolResponseData responseData,
			Set<SolStatus> reported) {
		if (responseData == null || responseData.getStatuses() == null) {
			return;
		}
		Set<SolStatus> statuses = EnumSet.noneOf(SolStatus.class);
		statuses.addAll(responseData.getStatuses());
		statuses.removeAll(reported);
		reported.addAll(statuses);
		if (!statuses.isEmpty()) {
			for (SolEventListener listener : eventListeners) {
				listener.processResponseEvent(statuses, characterData, solOperations);
			}
		}
	}

	/**
	 * Sends a packet, and sends it again while the BMC NACKs it without accepting a character (its buffer
	 * is full, or the server is off), after a growing pause that leaves the BMC time to pass its characters on. The
	 * statuses of each answer are reported, once for the packet.
	 *
	 * @param payloadCoder the packet
	 * @param characterData the characters of the packet, reported with the statuses of its answers
	 * @param lineOperations the line states of the packet, reported with the statuses of its answers
	 * @return the acknowledgement of the BMC, or null
	 */
	private SolResponseData sendPayload(
			PayloadCoder payloadCoder,
			byte[] characterData,
			Set<SolOperation> lineOperations) {
		ConnectionHandle connectionHandle = session.getConnectionHandle();

		try {
			SolResponseData responseData = (SolResponseData) connector.sendMessage(connectionHandle, payloadCoder);
			// The statuses of each answer: the next one may not repeat them
			Set<SolStatus> reported = EnumSet.noneOf(SolStatus.class);
			notifyResponseListeners(characterData, lineOperations, responseData, reported);

			for (int retry = 1; isNackForWholeMessage(responseData) && retry <= connector.getRetries(); retry++) {
				// ponytail: linear pause (100, 200, 300 ms by default), the SOL retry interval of the BMC is not read
				Thread.sleep(NACK_PAUSE_MS * retry);

				responseData = resendPayload(connectionHandle, payloadCoder, responseData.getRequestSequenceNumber());
				notifyResponseListeners(characterData, lineOperations, responseData, reported);
			}

			return responseData;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Deactivates the SOL payload, closes the sessions the console opened, and tears down the connector when the
	 * console owns it: these are released even when the deactivation fails.
	 *
	 * @throws IOException when the BMC does not confirm the deactivation
	 */
	@Override
	public synchronized void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		try {
			deactivatePayload(session.getConnectionHandle(), payloadInstance);
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IOException("Error while closing Serial over LAN instance", e);
		} finally {
			release();
		}
	}

	private void deactivatePayload(ConnectionHandle connectionHandle, int instance) throws Exception {
		connector
				.sendMessage(
						connectionHandle,
						new DeactivatePayload(PayloadType.Sol, instance));
	}

	/**
	 * Deactivates the payload activated in a session that the console does not close, when the console fails to open:
	 * where the activation was confirmed, or where Activate Payload got no reply but the payload is active in that very
	 * session (never in the session of another console).
	 */
	private void deactivatePayloadOfOthers() {
		try {
			if (activatedConnection != null && !ownedConnections.contains(activatedConnection)) {
				deactivatePayload(activatedConnection, activatedInstance);
			}
		} catch (Exception e) {
			restoreInterruptStatus(e);
		}
		try {
			if (activationSession != null) {
				ConnectionHandle attempted = activationSession.getConnectionHandle();
				if (attempted != activatedConnection
						&& !ownedConnections.contains(attempted)
						&& isActiveInSession(connector, activationSession)) {
					deactivatePayload(attempted, payloadInstance);
				}
			}
		} catch (Exception e) {
			restoreInterruptStatus(e);
		}
	}

	private static void restoreInterruptStatus(Exception e) {
		if (e instanceof InterruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Stops listening to the BMC, closes the sessions the console opened (the session on the payload port first), and
	 * tears down the connector when the console owns it.
	 */
	private void release() {
		if (inboundMessageListener != null) {
			connector.unregisterIncomingMessageListener(inboundMessageListener);
		}
		for (int i = ownedConnections.size() - 1; i >= 0; i--) {
			ConnectionHandle connectionHandle = ownedConnections.get(i);
			try {
				connector.closeSession(connectionHandle);
			} catch (Exception e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
			}
			connector.closeConnection(connectionHandle);
		}
		ownedConnections.clear();
		if (ownsConnector) {
			connector.tearDown();
		}
	}

	/**
	 * Sends a NACKed packet again, with its number: when the acknowledgement of that copy is lost, or the packet timed
	 * out during the pause, it is sent again as after any timeout, still with its number.
	 */
	private SolResponseData resendPayload(ConnectionHandle connectionHandle, PayloadCoder payloadCoder, byte tag)
			throws Exception {
		SolResponseData responseData;
		try {
			responseData = (SolResponseData) connector.retryMessage(connectionHandle, tag, PayloadType.Sol);
		} catch (ConnectionException e) {
			responseData = null;
		}
		return responseData != null ?
				responseData : (SolResponseData) connector.sendMessage(connectionHandle, payloadCoder);
	}

	private boolean isNackForWholeMessage(SolResponseData responseData) {
		return responseData != null
				&& responseData.getAcknowledgeState() == SolAckState.NACK
				&& responseData.getAcceptedCharactersNumber() == 0;
	}

	private boolean isNackForMessagePart(SolResponseData responseData, int previousMessageDataLength) {
		return responseData != null
				&& responseData.getAcknowledgeState() == SolAckState.NACK
				&& responseData.getAcceptedCharactersNumber() > 0
				&& responseData.getAcceptedCharactersNumber() < previousMessageDataLength;
	}

}
