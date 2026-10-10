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
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
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
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.sol.SolCoder;
import org.metricshub.ipmi.core.coding.sol.SolResponseData;
import org.metricshub.ipmi.core.common.Constants;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.connection.Session;
import org.metricshub.ipmi.core.connection.SessionException;
import org.metricshub.ipmi.core.connection.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point for the Serial Over LAN (SOL) communication. Use all SOL operations through this class.
 */
public class SerialOverLan implements Closeable {

	private static final Logger LOGGER = LoggerFactory.getLogger(SerialOverLan.class);

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
	 * The connection of the session where the payload was last activated, or null.
	 */
	private ConnectionHandle activatedConnection;

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
	 *        IP address of the remote server
	 * @param remotePort
	 *        UDP port number of the remote server
	 * @param user
	 *        IPMI user name
	 * @param password
	 *        IPMI password
	 * @param cipherSuiteSelectionHandler
	 *        {@link CipherSuiteSelectionHandler} that will allow to select {@link CipherSuite} among available ones.
	 * @throws SOLException when the SOL payload cannot be activated
	 * @throws SessionException when the session cannot be opened
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
	 *        IP address of the remote server
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
	 * @throws SessionException when the session cannot be opened
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
	 *        IP address of the remote server
	 * @param user
	 *        IPMI user name
	 * @param password
	 *        IPMI password
	 * @param cipherSuiteSelectionHandler
	 *        {@link CipherSuiteSelectionHandler} that will allow to select {@link CipherSuite} among available ones.
	 * @throws SOLException when the SOL payload cannot be activated
	 * @throws SessionException when the session cannot be opened
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
			alternativeSession = SessionManager
					.establishSession(
							ipmiConnector,
							connectionHandle.getRemoteAddress().getHostAddress(),
							solPayloadPort,
							connectionHandle.getUser(),
							connectionHandle.getPasswordBytes(),
							connectionHandle.getBmcKey(),
							// A suite of its own: opening the session keys the algorithms of its suite
							new SpecificCipherSuiteSelector(connectionHandle.getCipherSuite().copy()));
			ownedConnections.add(alternativeSession.getConnectionHandle());
		}

		activatePayload(ipmiConnector, alternativeSession);

		return alternativeSession;
	}

	/**
	 * Tries to activate SOL payload in the given session.
	 * If first activation try fails due to insufficient privileges, raises the session privileges
	 * to the level the session was opened for and tries to activate payload once again.
	 *
	 * @param ipmiConnector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param payloadSession
	 *        the session where the payload is activated
	 * @return UDP port number on which managed system listenes for SOL messages.
	 * @throws SOLException
	 *         when any unrecoverable error occurred.
	 */
	private int activatePayload(IpmiConnector ipmiConnector, Session payloadSession) throws SOLException {
		ConnectionHandle connectionHandle = payloadSession.getConnectionHandle();
		try {
			this.payloadInstance = getFirstAvailablePayloadInstance(ipmiConnector, connectionHandle);
			this.activationSession = payloadSession;

			ActivateSolPayload activatePayload = new ActivateSolPayload(connectionHandle.getCipherSuite(), payloadInstance);
			ActivateSolPayloadResponseData activatePayloadResponseData = getActivatePayloadResponse(
					ipmiConnector,
					payloadSession,
					activatePayload);
			this.activatedConnection = connectionHandle;

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
	 * Sends Activate Payload, and once more after raising the privilege of the session when it is too low, or after
	 * deactivating the payload when a lost reply left it active in this session.
	 */
	private ActivateSolPayloadResponseData getActivatePayloadResponse(
			IpmiConnector ipmiConnector,
			Session payloadSession,
			ActivateSolPayload activatePayload)
			throws Exception {
		ConnectionHandle connectionHandle = payloadSession.getConnectionHandle();
		boolean raised = false;
		boolean deactivated = false;
		while (true) {
			try {
				return (ActivateSolPayloadResponseData) ipmiConnector.sendMessage(connectionHandle, activatePayload);
			} catch (IPMIException e) {
				boolean alreadyActive = !deactivated && e.getRawCode() == PAYLOAD_ALREADY_ACTIVE;
				if (!raised && e.getCompletionCode() == CompletionCode.InsufficentPrivilege) {
					raiseSessionPrivileges(ipmiConnector, connectionHandle);
					raised = true;
				} else if (alreadyActive && isActiveInSession(ipmiConnector, payloadSession)) {
					// The reply to Activate Payload was lost, and the request sent again found the payload active in this
					// session: the port and the sizes of the payload are only in that reply, which a new activation gives
					deactivatePayload(connectionHandle);
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
			GetPayloadInstanceInfoResponseData info = (GetPayloadInstanceInfoResponseData) ipmiConnector
					.sendMessage(
							connectionHandle,
							new GetPayloadInstanceInfo(connectionHandle.getCipherSuite(), PayloadType.Sol, payloadInstance));
			return info.getSessionId() != 0 && info.getSessionId() == payloadSession.getManagedSystemSessionId();
		} catch (IPMIException e) {
			// The command is optional
			LOGGER.debug("Get Payload Instance Info failed", e);
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
		GetPayloadActivationStatus getPayloadActivationStatus = new GetPayloadActivationStatus(
				connectionHandle.getCipherSuite(),
				PayloadType.Sol);
		GetPayloadActivationStatusResponseData getActivationResponseData = (GetPayloadActivationStatusResponseData) ipmiConnector
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
	 * Sends proper command to managed system in order to raise user privileges in given session to the level the session
	 * was opened for (Administrator when unknown): the BMC refuses a higher one.
	 *
	 * @param ipmiConnector
	 *        {@link IpmiConnector} that will be used for communication
	 * @param connectionHandle
	 *        {@link ConnectionHandle} representing single connection to managed system.
	 * @throws Exception
	 *         If any exception occurred during communication.
	 */
	private void raiseSessionPrivileges(IpmiConnector ipmiConnector, ConnectionHandle connectionHandle) throws Exception {
		PrivilegeLevel privilegeLevel = connectionHandle.getPrivilegeLevel();
		if (privilegeLevel == null || privilegeLevel == PrivilegeLevel.MaximumAvailable) {
			privilegeLevel = PrivilegeLevel.Administrator;
		}
		SetSessionPrivilegeLevel setSessionPrivilegeLevel = new SetSessionPrivilegeLevel(
				IpmiVersion.V20,
				connectionHandle.getCipherSuite(),
				AuthenticationType.RMCPPlus,
				privilegeLevel);
		ipmiConnector.sendMessage(connectionHandle, setSessionPrivilegeLevel);
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
		SolResponseData responseData = sendPayload(characterPacket(characterData));

		notifyResponseListeners(characterData, new HashSet<SolOperation>(), responseData);

		byte[] remainingCharacterData = characterData;

		while (isNackForMessagePart(responseData, remainingCharacterData.length)) {
			remainingCharacterData = Arrays
					.copyOfRange(
							remainingCharacterData,
							responseData.getAcceptedCharactersNumber(),
							remainingCharacterData.length);

			responseData = sendPayload(characterPacket(remainingCharacterData));

			notifyResponseListeners(remainingCharacterData, new HashSet<SolOperation>(), responseData);
		}

		return responseData != null && responseData.getAcknowledgeState() == SolAckState.ACK;
	}

	/**
	 * @return a packet of characters, with the line states the console holds
	 */
	private SolCoder characterPacket(byte[] characterData) {
		return new SolCoder(
				characterData,
				(byte) 0,
				(byte) 0,
				SolAckState.ACK,
				inboundMessageListener.getLineOperations(),
				session.getConnectionHandle().getCipherSuite());
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
							new SolCoder(operations, session.getConnectionHandle().getCipherSuite()));
			return true;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			LOGGER.error("Error while sending SOL operations", e);
			return false;
		}
	}

	private void notifyResponseListeners(
			byte[] characterData,
			Set<SolOperation> solOperations,
			SolResponseData responseData) {
		if (responseData != null && responseData.getStatuses() != null && !responseData.getStatuses().isEmpty()) {
			for (SolEventListener listener : eventListeners) {
				listener.processResponseEvent(responseData.getStatuses(), characterData, solOperations);
			}
		}
	}

	/**
	 * Sends a packet, and sends it again while the BMC NACKs it without accepting a character (its buffer
	 * is full, or the server is off), after a growing pause that leaves the BMC time to pass its characters on.
	 *
	 * @return the acknowledgement of the BMC, or null
	 */
	private SolResponseData sendPayload(PayloadCoder payloadCoder) {
		ConnectionHandle connectionHandle = session.getConnectionHandle();

		try {
			SolResponseData responseData = (SolResponseData) connector.sendMessage(connectionHandle, payloadCoder);

			for (int retry = 1; isNackForWholeMessage(responseData) && retry <= connector.getRetries(); retry++) {
				// ponytail: linear pause (100, 200, 300 ms by default), the SOL retry interval of the BMC is not read
				Thread.sleep(NACK_PAUSE_MS * retry);

				responseData = (SolResponseData) connector
						.retryMessage(
								connectionHandle,
								responseData.getRequestSequenceNumber(),
								PayloadType.Sol);
			}

			return responseData;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			LOGGER.error("Error while sending message", e);
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
			deactivatePayload(session.getConnectionHandle());
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IOException("Error while closing Serial over LAN instance", e);
		} finally {
			release();
		}
	}

	private void deactivatePayload(ConnectionHandle connectionHandle) throws Exception {
		connector
				.sendMessage(
						connectionHandle,
						new DeactivatePayload(connectionHandle.getCipherSuite(), PayloadType.Sol, payloadInstance));
	}

	/**
	 * Deactivates the payload activated in a session that the console does not close, when the console fails to open:
	 * where the activation was confirmed, or where Activate Payload got no reply but the payload is active in that very
	 * session (never in the session of another console).
	 */
	private void deactivatePayloadOfOthers() {
		try {
			if (activatedConnection != null && !ownedConnections.contains(activatedConnection)) {
				deactivatePayload(activatedConnection);
			}
			if (activationSession != null) {
				ConnectionHandle attempted = activationSession.getConnectionHandle();
				if (attempted != activatedConnection
						&& !ownedConnections.contains(attempted)
						&& isActiveInSession(connector, activationSession)) {
					deactivatePayload(attempted);
				}
			}
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			LOGGER.warn("Cannot deactivate the SOL payload after a failed activation", e);
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
				LOGGER.warn("Cannot close the session of the Serial over LAN console", e);
			}
			connector.closeConnection(connectionHandle);
		}
		ownedConnections.clear();
		if (ownsConnector) {
			connector.tearDown();
		}
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
