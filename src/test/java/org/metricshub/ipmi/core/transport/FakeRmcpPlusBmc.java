package org.metricshub.ipmi.core.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A BMC on the loopback interface that speaks IPMI v2.0 RMCP+, written from the IPMI 2.0 specification with
 * javax.crypto only and none of the coders of the library: an independent oracle, which catches the bugs of the coders
 * rather than sharing them.
 * <p>
 * It answers Get Channel Cipher Suites (RMCP+ session-less, section 22.15) with the given cipher suite records and Get
 * Channel Authentication Capabilities (IPMI v1.5 session-less, section 22.13), opens the sessions of cipher suites 3
 * (RAKP-HMAC-SHA1, HMAC-SHA1-96, AES-CBC-128) and 17 (RAKP-HMAC-SHA256, HMAC-SHA256-128, AES-CBC-128) for one user,
 * with an optional Kg (sections 13.17 to 13.23, 13.31 and 13.32), and answers the IPMI requests of the session (section
 * 13.6, AES-CBC-128 per section 13.29): Get Device ID, Get Chassis Status, Close Session, Set Session Privilege Level
 * (sessions start at the User level, section 22.18) and the commands registered with
 * {@link #handle(int, int, Function)}. With {@link #enableSol(int, int, int)}, it also serves one instance of the
 * Serial over LAN payload (sections 15.9 to 15.11 and 24.1 to 24.4). What the console sends that a BMC would reject is
 * listed by {@link #getErrors()}.
 * </p>
 */
public class FakeRmcpPlusBmc implements AutoCloseable {

	/** Network function of the chassis commands. */
	public static final int NETFN_CHASSIS = 0x00;
	/** Network function of the application commands. */
	public static final int NETFN_APP = 0x06;
	/** Get Chassis Status (NetFn Chassis). */
	public static final int GET_CHASSIS_STATUS = 0x01;
	/** Chassis Control (NetFn Chassis). */
	public static final int CHASSIS_CONTROL = 0x02;
	/** Chassis Identify (NetFn Chassis). */
	public static final int CHASSIS_IDENTIFY = 0x04;
	/** Get Device ID (NetFn App). */
	public static final int GET_DEVICE_ID = 0x01;
	/** Get Channel Authentication Capabilities (NetFn App). */
	public static final int GET_CHANNEL_AUTHENTICATION_CAPABILITIES = 0x38;
	/** Set Session Privilege Level (NetFn App). */
	public static final int SET_SESSION_PRIVILEGE_LEVEL = 0x3b;
	/** Close Session (NetFn App). */
	public static final int CLOSE_SESSION = 0x3c;
	/** Activate Payload (NetFn App). */
	public static final int ACTIVATE_PAYLOAD = 0x48;
	/** Deactivate Payload (NetFn App). */
	public static final int DEACTIVATE_PAYLOAD = 0x49;
	/** Get Payload Activation Status (NetFn App). */
	public static final int GET_PAYLOAD_ACTIVATION_STATUS = 0x4a;
	/** Get Payload Instance Info (NetFn App). */
	public static final int GET_PAYLOAD_INSTANCE_INFO = 0x4b;
	/** Get Channel Cipher Suites (NetFn App). */
	public static final int GET_CHANNEL_CIPHER_SUITES = 0x54;
	/** Get Session Info. */
	public static final int GET_SESSION_INFO = 0x3d;

	/** Privilege level User. */
	public static final int USER = 2;
	/** Privilege level Operator. */
	public static final int OPERATOR = 3;
	/** Privilege level Administrator. */
	public static final int ADMINISTRATOR = 4;

	/** Status bit of a SOL packet from the BMC: the packet is NACKed (IPMI 2.0 table 15-2). */
	public static final int SOL_NACK = 0x40;
	/** Status bit of a SOL packet from the BMC: the server is off, characters cannot reach it. */
	public static final int SOL_CHARACTER_TRANSFER_UNAVAILABLE = 0x20;
	/** Status bit of a SOL packet from the BMC: SOL is deactivated. */
	public static final int SOL_DEACTIVATED = 0x10;
	/** Status bit of a SOL packet from the BMC: a break was detected. */
	public static final int SOL_BREAK = 0x04;
	/** Reply policy of {@link #replySol(int...)}: the packet is accepted, but its ACK is lost. */
	public static final int LOSE_ACK = -1;

	/** RMCP+ payload type of the Open Session Request (IPMI 2.0 table 13-16). */
	public static final int OPEN_SESSION_REQUEST = 0x10;
	/** RMCP+ payload type of the RAKP Message 1. */
	public static final int RAKP_1 = 0x12;
	/** RMCP+ payload type of the RAKP Message 3. */
	public static final int RAKP_3 = 0x14;

	/** Get Chassis Status response data the BMC answers by default: power on, no power event, no chassis flag. */
	public static final byte[] CHASSIS_STATUS = { 0x01, 0x00, 0x00 };

	/**
	 * Get Device ID response data the BMC answers (IPMI 2.0 table 20-2): device 20h revision 1 without Device SDRs,
	 * available, firmware 1.44, IPMI 2.0, every function but bridge, manufacturer 2 (IBM), product 001Ah, auxiliary
	 * firmware revision 01 02 03 04.
	 */
	public static final byte[] DEVICE_ID = {
			0x20,
			0x01,
			0x01,
			0x44,
			0x02,
			(byte) 0xbf,
			0x02,
			0x00,
			0x00,
			0x1a,
			0x00,
			0x01,
			0x02,
			0x03,
			0x04 };

	/**
	 * Faults the BMC injects: the handshake ones in every handshake ({@link #inject(Fault)}), the others in the reply to
	 * a given IPMI request of the session ({@link #inject(int, Fault)}).
	 */
	public enum Fault {
		/** Open Session Response with integrity algorithm none, whatever the request (a downgrade). */
		OPEN_SESSION_INTEGRITY_NONE,
		/** Open Session Response with confidentiality algorithm none, whatever the request (a downgrade). */
		OPEN_SESSION_CONFIDENTIALITY_NONE,
		/** Open Session Response with another authentication algorithm than requested (RAKP-HMAC-MD5). */
		OPEN_SESSION_OTHER_AUTHENTICATION,
		/** Open Session Response with the console session ID of another session. */
		OPEN_SESSION_OTHER_CONSOLE_SESSION_ID,
		/** Open Session Response with a null managed system session ID. */
		OPEN_SESSION_NULL_MANAGED_SYSTEM_SESSION_ID,
		/** RAKP Message 2 of another console session (its session ID field and its key exchange code). */
		RAKP_2_OTHER_CONSOLE_SESSION_ID,
		/** RAKP Message 4 with the console session ID of another session. */
		RAKP_4_OTHER_CONSOLE_SESSION_ID,
		/**
		 * Reply whose IV gets bit 7 of the bytes over the first two response data bytes flipped after signing: it still
		 * decrypts to a well-formed message (AES-CBC is malleable, and both flips cancel out in the checksum), with other
		 * data, and only the AuthCode tells.
		 */
		FLIP_PAYLOAD,
		/** Reply whose AuthCode gets a bit flipped. */
		FLIP_AUTH_CODE,
		/** Reply sent neither encrypted nor authenticated (payload type bits 7:6 clear, no session trailer). */
		PLAINTEXT,
		/** Reply signed with the session sequence number of the previous packet of the BMC, received already. */
		REUSED_SEQUENCE_NUMBER,
		/** No reply. */
		DROP
	}

	private static final byte[] RMCP_HEADER = { 0x06, 0x00, (byte) 0xff, 0x07 };
	private static final int CHANNEL = 0x01;
	private static final int MAX_KEY_LENGTH = 20;

	private final SecureRandom random = new SecureRandom();
	private final DatagramSocket socket;
	private final byte[] cipherSuiteRecords;
	private final byte[] user;
	private final byte[] kuid;
	private final byte[] kg;
	private final byte[] guid = new byte[16];
	private final Map<Integer, Function<byte[], byte[]>> handlers = new ConcurrentHashMap<>();
	private final Map<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();
	private final Set<Fault> handshakeFaults = ConcurrentHashMap.newKeySet();
	private final Map<Integer, Fault> replyFaults = new ConcurrentHashMap<>();
	private final AtomicInteger sessionRequests = new AtomicInteger();
	private final List<String> errors = new CopyOnWriteArrayList<>();
	private volatile Session session;
	private volatile SocketAddress console;

	private volatile boolean powerOn = true;
	/** The chassis control of the last Chassis Control the BMC accepted, -1 before the first. */
	private volatile int chassisControl = -1;
	/** The request data of the last Chassis Identify the BMC accepted, null before the first. */
	private volatile byte[] chassisIdentify;

	private volatile int solPort;
	private volatile int solInboundPayloadSize;
	private volatile int solPrivilege;
	private volatile boolean solActive;
	/** The managed system session ID of the session that activated the payload: a dead one keeps it, as on Supermicro. */
	private volatile int solSessionId;
	private volatile int solStatus;
	private final Queue<Integer> solReplies = new ConcurrentLinkedQueue<>();
	/** The status of each reply of {@link #solReplies}, -1 for {@link #solStatus}. */
	private final Queue<Integer> solReplyStatuses = new ConcurrentLinkedQueue<>();
	private final List<SolPacket> solPackets = new CopyOnWriteArrayList<>();
	private final ByteArrayOutputStream solReceived = new ByteArrayOutputStream();
	private int lastSolSequenceNumber;
	private boolean lastSolAccepted;

	/**
	 * A SOL packet received from the console (IPMI 2.0 table 15-2).
	 */
	public static final class SolPacket {
		/** Packet sequence number, 0 for an ACK-only packet. */
		public final int sequenceNumber;
		/** Sequence number of the packet of the BMC acknowledged, or 0. */
		public final int ackSequenceNumber;
		/** Accepted character count. */
		public final int acceptedCharacters;
		/** Operation/status byte. */
		public final int operation;
		/** Character data. */
		public final byte[] data;

		private SolPacket(byte[] packet) {
			sequenceNumber = packet[0] & 0xff;
			ackSequenceNumber = packet[1] & 0xff;
			acceptedCharacters = packet[2] & 0xff;
			operation = packet[3] & 0xff;
			data = Arrays.copyOfRange(packet, 4, packet.length);
		}

		/**
		 * @return whether the packet NACKs the packet of the BMC
		 */
		public boolean isNack() {
			return (operation & SOL_NACK) != 0;
		}

		@Override
		public String toString() {
			return String
					.format(
							"seq %d, ack %d/%d%s, operation %02x, %d bytes",
							sequenceNumber,
							ackSequenceNumber,
							acceptedCharacters,
							isNack() ? " NACK" : "",
							operation,
							data.length);
		}
	}

	/**
	 * State of the session being opened or open.
	 */
	private static final class Session {
		private final int consoleId;
		private final int managedId;
		private final int[] algorithms;
		/** HMAC of the authentication and integrity algorithms: HmacSHA1 for suite 3, HmacSHA256 for suite 17. */
		private final String mac;
		/** Length of the RAKP 4 Integrity Check Value and of the AuthCode: 12 for SHA1-96, 16 for SHA256-128. */
		private final int macLength;
		private byte[] rm;
		private byte[] rc;
		/** RoleM, ULengthM and UNameM of RAKP Message 1, as the key exchange codes and the SIK cover them. */
		private byte[] identity;
		private byte[] k1;
		private byte[] k2;
		private boolean active;
		private volatile boolean closed;
		/** Expired by the BMC: its packets are dropped without a word, as a real BMC does. */
		private volatile boolean expired;
		private int sequenceNumber;
		/** The privilege the console asked for in RAKP Message 1, the most the session can be raised to. */
		private int maxPrivilege;
		private volatile int privilege = USER;

		private Session(int consoleId, int managedId, int[] algorithms) {
			this.consoleId = consoleId;
			this.managedId = managedId;
			this.algorithms = algorithms;
			mac = algorithms[0] == 1 ? "HmacSHA1" : "HmacSHA256";
			macLength = algorithms[0] == 1 ? 12 : 16;
		}
	}

	/**
	 * @param cipherSuiteRecords the cipher suite record data the BMC lists (IPMI 2.0 table 22-19), after the channel
	 *        byte
	 * @param user the user name
	 * @param password the password of the user, as stored by the BMC (zero-padded to 20 bytes)
	 * @param kg the BMC key for two-key logins, or null (an all-zero Kg also means one-key logins)
	 * @throws SocketException when no loopback port is free
	 */
	public FakeRmcpPlusBmc(byte[] cipherSuiteRecords, String user, byte[] password, byte[] kg) throws SocketException {
		this.cipherSuiteRecords = cipherSuiteRecords.clone();
		this.user = user.getBytes(StandardCharsets.UTF_8);
		kuid = Arrays.copyOf(password, MAX_KEY_LENGTH);
		this.kg = kg == null || isZero(kg) ? null : Arrays.copyOf(kg, MAX_KEY_LENGTH);
		random.nextBytes(guid);
		handle(NETFN_APP, GET_CHANNEL_CIPHER_SUITES, this::getChannelCipherSuites);
		handle(NETFN_APP, GET_CHANNEL_AUTHENTICATION_CAPABILITIES, this::getChannelAuthenticationCapabilities);
		handle(NETFN_APP, CLOSE_SESSION, this::closeSession);
		handle(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL, this::setSessionPrivilegeLevel);
		handle(NETFN_APP, GET_SESSION_INFO, this::getSessionInfo);
		handle(NETFN_APP, GET_DEVICE_ID, data -> concat(new byte[1], DEVICE_ID));
		handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, this::getChassisStatus);
		handle(NETFN_CHASSIS, CHASSIS_CONTROL, this::chassisControl);
		handle(NETFN_CHASSIS, CHASSIS_IDENTIFY, this::chassisIdentify);
		socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(this::serve, "fake-rmcp-plus-bmc");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * @return the loopback address the fake BMC listens on
	 */
	public InetAddress getAddress() {
		return socket.getLocalAddress();
	}

	/**
	 * @return the UDP port the fake BMC listens on
	 */
	public int getPort() {
		return socket.getLocalPort();
	}

	/**
	 * Registers the handler of an IPMI command, in place of the default one if any.
	 *
	 * @param netFn the network function of the request
	 * @param command the command code
	 * @param handler computes the completion code and the response data from the request data
	 */
	public void handle(int netFn, int command, Function<byte[], byte[]> handler) {
		handlers.put(key(netFn, command), handler);
	}

	/**
	 * Injects a handshake fault in every handshake from now on.
	 *
	 * @param fault one of the {@code OPEN_SESSION_*} and {@code RAKP_*} faults
	 */
	public void inject(Fault fault) {
		handshakeFaults.add(fault);
	}

	/**
	 * Injects a fault in the reply to an IPMI request of the session.
	 *
	 * @param request the number of the request among the valid ones received in sessions, from 1
	 * @param fault one of the reply faults
	 */
	public void inject(int request, Fault fault) {
		replyFaults.put(request, fault);
	}

	/**
	 * @param payloadType {@link #OPEN_SESSION_REQUEST}, {@link #RAKP_1} or {@link #RAKP_3}
	 * @return how many messages of this payload type were received
	 */
	public int getCount(int payloadType) {
		AtomicInteger count = counts.get(payloadType);
		return count == null ? 0 : count.get();
	}

	/**
	 * @param netFn the network function
	 * @param command the command code
	 * @return how many requests of this command were received (with valid checksums, and in a session with a valid
	 *         AuthCode and padding)
	 */
	public int getCount(int netFn, int command) {
		return getCount(key(netFn, command));
	}

	/**
	 * @return the authentication, integrity and confidentiality algorithms of the last session opened (requested
	 *         in its Open Session Request), or null
	 */
	public int[] getAlgorithms() {
		Session current = session;
		return current == null ? null : current.algorithms.clone();
	}

	/**
	 * @return whether the last session was closed with a valid Close Session request
	 */
	public boolean isSessionClosed() {
		Session current = session;
		return current != null && current.closed;
	}

	/**
	 * Expires the current session, as a BMC does after its inactivity timeout or a reboot: its packets are dropped
	 * from now on, and a new session can be opened.
	 */
	public void expireSession() {
		Session current = session;
		if (current != null) {
			current.expired = true;
		}
	}

	/**
	 * @return what the console sent that a BMC would reject (malformed messages, wrong checksums, AuthCodes, pads or
	 *         RAKP 3 codes, unprotected packets in a session)
	 */
	public List<String> getErrors() {
		return new ArrayList<>(errors);
	}

	/**
	 * Serves one instance of the SOL payload: Get Payload Activation Status, Activate Payload (which requires the given
	 * privilege, and answers the given port: the payload is only active once activated in a session on that port),
	 * Deactivate Payload, and the SOL packets, which the BMC accepts as {@link #replySol(int...)} says.
	 *
	 * @param payloadPort the UDP port where the BMC serves SOL, which Activate Payload returns
	 * @param inboundPayloadSize the largest SOL payload the console may send, which Activate Payload returns
	 * @param privilege the privilege level the session needs to activate the payload
	 */
	public void enableSol(int payloadPort, int inboundPayloadSize, int privilege) {
		solPort = payloadPort;
		solInboundPayloadSize = inboundPayloadSize;
		solPrivilege = privilege;
		handle(NETFN_APP, GET_PAYLOAD_ACTIVATION_STATUS, this::getPayloadActivationStatus);
		handle(NETFN_APP, ACTIVATE_PAYLOAD, this::activatePayload);
		handle(NETFN_APP, DEACTIVATE_PAYLOAD, this::deactivatePayload);
		handle(NETFN_APP, GET_PAYLOAD_INSTANCE_INFO, this::getPayloadInstanceInfo);
	}

	/**
	 * Sets how the BMC answers the next SOL packets with a sequence number from the console, one value per packet: the
	 * number of characters accepted (all of them when greater than the data, a NACK when fewer or 0), or
	 * {@link #LOSE_ACK}. The packets after these are accepted. A packet sent again with the sequence number of a packet
	 * accepted already is acknowledged again, without taking its data again (IPMI 2.0 section 15.9).
	 *
	 * @param acceptedCharacters what the BMC accepts of each of the next packets
	 */
	public void replySol(int... acceptedCharacters) {
		for (int accepted : acceptedCharacters) {
			replySolWithStatus(accepted, -1);
		}
	}

	/**
	 * Sets how the BMC answers the next SOL packet with a sequence number from the console, as
	 * {@link #replySol(int...)}, with the given status bits in the reply.
	 *
	 * @param acceptedCharacters what the BMC accepts of the packet
	 * @param status the status bits of the reply (table 15-2)
	 */
	public void replySolWithStatus(int acceptedCharacters, int status) {
		synchronized (solReplies) {
			solReplies.add(acceptedCharacters);
			solReplyStatuses.add(status);
		}
	}

	/**
	 * Sets the status bits the BMC puts in its acknowledgements from now on (table 15-2), for example
	 * {@link #SOL_CHARACTER_TRANSFER_UNAVAILABLE} when the server is off.
	 *
	 * @param status the status bits
	 */
	public void setSolStatus(int status) {
		solStatus = status;
	}

	/**
	 * Sends a SOL packet to the console, in the session.
	 *
	 * @param sequenceNumber the packet sequence number, 0 for an informational packet
	 * @param status the status byte (table 15-2)
	 * @param data the character data
	 * @throws IOException when the packet cannot be sent
	 * @throws GeneralSecurityException when the packet cannot be sealed
	 */
	public void sendSol(int sequenceNumber, int status, byte[] data) throws IOException, GeneralSecurityException {
		byte[] packet = concat(new byte[] { (byte) sequenceNumber, 0, 0, (byte) status }, data);
		byte[] datagram = seal(session, 0x01, packet, nextSequenceNumber(session), true);
		socket.send(new DatagramPacket(datagram, datagram.length, console));
	}

	/**
	 * @return whether the SOL payload is active
	 */
	public boolean isSolActive() {
		return solActive;
	}

	/**
	 * @return how many valid IPMI requests were received in sessions so far: {@code inject(getSessionRequests() + 1,
	 *         fault)} faults the reply to the next one
	 */
	public int getSessionRequests() {
		return sessionRequests.get();
	}

	/**
	 * @return whether the system is powered on: on at the start, then as the last Chassis Control left it
	 */
	public boolean isPowerOn() {
		return powerOn;
	}

	/**
	 * @return the chassis control (IPMI 2.0 table 28-4) of the last Chassis Control the BMC accepted, -1 before the
	 *         first
	 */
	public int getChassisControl() {
		return chassisControl;
	}

	/**
	 * @return the request data of the last Chassis Identify the BMC accepted, null before the first
	 */
	public byte[] getChassisIdentify() {
		return chassisIdentify == null ? null : chassisIdentify.clone();
	}

	/**
	 * Get Chassis Status (IPMI 2.0 table 28-3): {@link #CHASSIS_STATUS} with the current power state.
	 */
	private byte[] getChassisStatus(byte[] data) {
		byte[] status = CHASSIS_STATUS.clone();
		status[0] = (byte) (powerOn ? status[0] | 0x01 : status[0] & ~0x01);
		return concat(new byte[1], status);
	}

	/**
	 * Chassis Control (IPMI 2.0 table 28-4): Operator privilege; a power cycle or a hard reset of a system that is
	 * off is refused with D5h, as the specification recommends.
	 */
	private byte[] chassisControl(byte[] data) {
		if (session.privilege < OPERATOR) {
			return new byte[] { (byte) 0xd4 }; // insufficient privilege
		}
		if (data.length != 1 || (data[0] & 0x0f) > 5) {
			errors.add("Unexpected Chassis Control request: " + hex(data));
			return new byte[] { (byte) 0xcc }; // invalid data field
		}
		int control = data[0] & 0x0f;
		if ((control == 2 || control == 3) && !powerOn) {
			return new byte[] { (byte) 0xd5 }; // not supported in present state
		}
		chassisControl = control;
		if (control == 0 || control == 5) {
			powerOn = false;
		} else if (control == 1 || control == 2) {
			powerOn = true;
		}
		return new byte[1];
	}

	/**
	 * Chassis Identify (IPMI 2.0 table 28-6): Operator privilege; the interval byte and the optional force byte.
	 */
	private byte[] chassisIdentify(byte[] data) {
		if (session.privilege < OPERATOR) {
			return new byte[] { (byte) 0xd4 }; // insufficient privilege
		}
		if (data.length > 2) {
			errors.add("Unexpected Chassis Identify request: " + hex(data));
			return new byte[] { (byte) 0xcc }; // invalid data field
		}
		chassisIdentify = data.clone();
		return new byte[1];
	}

	/**
	 * @return the SOL packets received from the console, in order
	 */
	public List<SolPacket> getSolPackets() {
		return new ArrayList<>(solPackets);
	}

	/**
	 * @return the characters of the console that the BMC accepted, in order
	 */
	public byte[] getSolReceived() {
		synchronized (solReceived) {
			return solReceived.toByteArray();
		}
	}

	@Override
	public void close() {
		socket.close();
	}

	private void serve() {
		byte[] buffer = new byte[2048];
		while (!socket.isClosed()) {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			try {
				socket.receive(packet);
			} catch (IOException e) {
				return; // closed
			}
			try {
				console = packet.getSocketAddress();
				byte[] reply = receive(Arrays.copyOf(packet.getData(), packet.getLength()));
				if (reply != null) {
					socket.send(new DatagramPacket(reply, reply.length, packet.getSocketAddress()));
				}
			} catch (IOException | GeneralSecurityException | RuntimeException e) {
				errors.add(e.toString());
			}
		}
	}

	/**
	 * Handles a datagram (IPMI 2.0 table 13-8) and returns the reply, or null.
	 */
	private byte[] receive(byte[] datagram) throws GeneralSecurityException {
		if (datagram.length < 14 || datagram[3] != 0x07) {
			errors.add("Not an IPMI message: " + hex(datagram));
			return null;
		}
		if (datagram[4] != 0x06) {
			return receiveV15(datagram);
		}
		if (datagram.length < 16) {
			errors.add("Truncated RMCP+ session header: " + hex(datagram));
			return null;
		}
		ByteBuffer in = ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN);
		if (in.getInt(6) != 0) {
			return receiveInSession(datagram);
		}
		int payloadType = datagram[5] & 0xff;
		byte[] payload = Arrays.copyOfRange(datagram, 16, 16 + (in.getShort(14) & 0xffff));
		count(payloadType);
		switch (payloadType) {
		case 0x00:
			byte[] response = respond(payload);
			return response == null ? null : packet(0x00, 0, 0, response);
		case OPEN_SESSION_REQUEST:
			return packet(0x11, 0, 0, openSession(payload));
		case RAKP_1:
			return packet(0x13, 0, 0, rakp2(payload));
		case RAKP_3:
			byte[] rakp4 = rakp4(payload);
			return rakp4 == null ? null : packet(0x15, 0, 0, rakp4);
		default:
			errors.add("Unexpected session-less payload type " + payloadType);
			return null;
		}
	}

	/**
	 * Handles an IPMI v1.5 session-less message: Auth Type none, Session Sequence Number, Session ID, Message Length,
	 * IPMI message.
	 */
	private byte[] receiveV15(byte[] datagram) {
		if (datagram[4] != 0x00 || datagram.length < 14 + (datagram[13] & 0xff)) {
			errors.add("Unexpected IPMI v1.5 message: " + hex(datagram));
			return null;
		}
		byte[] response = respond(Arrays.copyOfRange(datagram, 14, 14 + (datagram[13] & 0xff)));
		if (response == null) {
			return null;
		}
		return ByteBuffer
				.allocate(14 + response.length)
				.put(RMCP_HEADER)
				.put((byte) 0x00)
				.putInt(0)
				.putInt(0)
				.put((byte) response.length)
				.put(response)
				.array();
	}

	/**
	 * Open Session Request (IPMI 2.0 table 13-9), Open Session Response (table 13-10).
	 */
	private byte[] openSession(byte[] request) {
		ByteBuffer in = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN);
		int consoleId = in.getInt(4);
		int[] algorithms = { request[12] & 0x3f, request[20] & 0x3f, request[28] & 0x3f };
		ByteBuffer out = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
		out.put(request[0]);
		if (!Arrays.equals(algorithms, new int[] { 1, 1, 1 }) && !Arrays.equals(algorithms, new int[] { 3, 4, 1 })) {
			return out.put((byte) 0x11).putShort((short) 0).putInt(consoleId).array(); // no cipher suite match
		}
		Session opened = new Session(consoleId, random.nextInt(Integer.MAX_VALUE - 1) + 1, algorithms);
		session = opened;
		int role = request[1] & 0x0f;
		out
				.put((byte) 0x00)
				.put((byte) (role == 0 ? 4 : role))
				.put((byte) 0x00)
				.putInt(has(Fault.OPEN_SESSION_OTHER_CONSOLE_SESSION_ID) ? consoleId + 1 : consoleId)
				.putInt(has(Fault.OPEN_SESSION_NULL_MANAGED_SYSTEM_SESSION_ID) ? 0 : opened.managedId);
		int[] answered = {
				has(Fault.OPEN_SESSION_OTHER_AUTHENTICATION) ? 2 : algorithms[0],
				has(Fault.OPEN_SESSION_INTEGRITY_NONE) ? 0 : algorithms[1],
				has(Fault.OPEN_SESSION_CONFIDENTIALITY_NONE) ? 0 : algorithms[2] };
		for (int type = 0; type < 3; type++) {
			out.put(new byte[] { (byte) type, 0, 0, 0x08, (byte) answered[type], 0, 0, 0 });
		}
		return out.array();
	}

	/**
	 * RAKP Message 1 (IPMI 2.0 table 13-11), RAKP Message 2 (table 13-12) with HMAC-Kuid(SIDm, SIDc, Rm, Rc, GUIDc,
	 * RoleM, ULengthM, UNameM) (section 13.31).
	 */
	private byte[] rakp2(byte[] request) throws GeneralSecurityException {
		Session current = session;
		ByteBuffer out = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).put(request[0]);
		if (current == null || ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN).getInt(4) != current.managedId) {
			return out.put((byte) 0x02).array(); // invalid session ID, null console session ID
		}
		int nameLength = request[27] & 0xff;
		byte[] name = Arrays.copyOfRange(request, 28, 28 + nameLength);
		if (!Arrays.equals(name, user)) {
			return out.put((byte) 0x0d).putShort((short) 0).putInt(current.consoleId).array(); // unauthorized name
		}
		current.rm = Arrays.copyOfRange(request, 8, 24);
		// RoleM: the maximum privilege requested, 0 for the highest one the user has
		current.maxPrivilege = (request[24] & 0x0f) == 0 ? ADMINISTRATOR : request[24] & 0x0f;
		current.rc = new byte[16];
		random.nextBytes(current.rc);
		current.identity = concat(new byte[] { request[24], request[27] }, name);
		int consoleId = has(Fault.RAKP_2_OTHER_CONSOLE_SESSION_ID) ? current.consoleId + 1 : current.consoleId;
		byte[] code = hmac(
				current.mac,
				kuid,
				le(consoleId),
				le(current.managedId),
				current.rm,
				current.rc,
				guid,
				current.identity);
		return ByteBuffer
				.allocate(40 + code.length)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put(request[0])
				.put((byte) 0x00)
				.putShort((short) 0)
				.putInt(consoleId)
				.put(current.rc)
				.put(guid)
				.put(code)
				.array();
	}

	/**
	 * RAKP Message 3 (IPMI 2.0 table 13-13), checked against HMAC-Kuid(Rc, SIDm, RoleM, ULengthM, UNameM), RAKP Message 4
	 * (table 13-14) with HMAC-SIK(Rm, SIDc, GUIDc) truncated as the integrity algorithm; SIK = HMAC-KG(Rm, Rc, RoleM,
	 * ULengthM, UNameM), Kuid in place of an all-zero KG (section 13.31); K1 and K2 = HMAC-SIK of 20 bytes 01h and 02h
	 * (section 13.32).
	 */
	private byte[] rakp4(byte[] request) throws GeneralSecurityException {
		Session current = session;
		ByteBuffer out = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).put(request[0]);
		if (current == null
				|| current.rc == null
				|| ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN).getInt(4) != current.managedId) {
			return out.put((byte) 0x02).array(); // invalid session ID
		}
		if (request[1] != 0) {
			return null; // the console reports an error: the BMC terminates the exchange without RAKP 4
		}
		byte[] expected = hmac(current.mac, kuid, current.rc, le(current.consoleId), current.identity);
		if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(request, 8, request.length))) {
			errors.add("RAKP Message 3: invalid key exchange authentication code");
			return out.put((byte) 0x0f).putShort((short) 0).putInt(current.consoleId).array(); // invalid integrity check
		}
		byte[] sik = hmac(current.mac, kg == null ? kuid : kg, current.rm, current.rc, current.identity);
		current.k1 = hmac(current.mac, sik, filled(1));
		current.k2 = hmac(current.mac, sik, filled(2));
		current.active = true;
		byte[] icv = Arrays
				.copyOf(hmac(current.mac, sik, current.rm, le(current.managedId), guid), current.macLength);
		return ByteBuffer
				.allocate(8 + icv.length)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put(request[0])
				.put((byte) 0x00)
				.putShort((short) 0)
				.putInt(has(Fault.RAKP_4_OTHER_CONSOLE_SESSION_ID) ? current.consoleId + 1 : current.consoleId)
				.put(icv)
				.array();
	}

	/**
	 * Handles a packet of the session: checks the session trailer and the AuthCode (IPMI 2.0 table 13-8, section
	 * 13.28.4), decrypts the payload (section 13.29), executes the request and seals the reply, with the faults to
	 * inject.
	 */
	private byte[] receiveInSession(byte[] datagram) throws GeneralSecurityException {
		Session current = session;
		ByteBuffer in = ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN);
		if (current != null && current.expired && in.getInt(6) == current.managedId) {
			return null;
		}
		if (current == null || !current.active || current.closed || in.getInt(6) != current.managedId) {
			errors.add("Packet of no open session: " + hex(datagram));
			return null;
		}
		// An IPMI message (0h) or a SOL packet (1h), encrypted and authenticated
		int payloadType = datagram[5] & 0x3f;
		if ((datagram[5] & 0xc0) != 0xc0 || payloadType > 0x01) {
			errors.add("Packet neither encrypted nor authenticated in the session: " + hex(datagram));
			return null;
		}
		// Integrity PAD (FFh) so that AuthType/Format to Next Header is a multiple of 4, Pad Length, Next Header (07h)
		int end = 16 + (in.getShort(14) & 0xffff);
		int pad = (4 - (end - 4 + 2) % 4) % 4;
		int authCode = end + pad + 2;
		if (datagram.length != authCode + current.macLength
				|| datagram[authCode - 2] != pad
				|| datagram[authCode - 1] != 0x07
				|| !isFilled(Arrays.copyOfRange(datagram, end, end + pad), 0xff)) {
			errors.add("Bad session trailer: " + hex(datagram));
			return null;
		}
		if (!MessageDigest
				.isEqual(authCode(current, datagram, authCode), Arrays.copyOfRange(datagram, authCode, datagram.length))) {
			errors.add("Bad AuthCode: " + hex(datagram));
			return null;
		}
		byte[] request = decrypt(current, Arrays.copyOfRange(datagram, 16, end));
		if (request != null && payloadType == 0x01) {
			byte[] solReply = sol(request);
			return solReply == null ? null : seal(current, 0x01, solReply, nextSequenceNumber(current), true);
		}
		byte[] response = request == null ? null : respond(request);
		if (response == null) {
			return null;
		}
		Fault fault = replyFaults.get(sessionRequests.incrementAndGet());
		if (fault == Fault.DROP) {
			return null;
		}
		if (fault == Fault.PLAINTEXT) {
			return seal(current, 0x00, response, nextSequenceNumber(current), false);
		}
		byte[] reply = seal(
				current,
				0x00,
				response,
				fault == Fault.REUSED_SEQUENCE_NUMBER ? current.sequenceNumber : nextSequenceNumber(current),
				true);
		if (fault == Fault.FLIP_AUTH_CODE) {
			reply[reply.length - 1] ^= 0x01;
		} else if (fault == Fault.FLIP_PAYLOAD) {
			// The IV starts the payload, at 16: its bytes 7 and 8 are XORed into the response data bytes 1 and 2
			reply[16 + 7] ^= (byte) 0x80;
			reply[16 + 8] ^= (byte) 0x80;
		}
		return reply;
	}

	/**
	 * Decrypts an AES-CBC-128 payload: IV, then the data, the Confidentiality Pad (01h, 02h...) and the Pad Length, with
	 * the first 16 bytes of K2 (IPMI 2.0 section 13.29).
	 */
	private byte[] decrypt(Session current, byte[] payload) throws GeneralSecurityException {
		if (payload.length < 32 || payload.length % 16 != 0) {
			errors.add("Bad AES-CBC-128 payload length: " + payload.length);
			return null;
		}
		Cipher aes = Cipher.getInstance("AES/CBC/NoPadding");
		aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(current.k2, 0, 16, "AES"), new IvParameterSpec(payload, 0, 16));
		byte[] plain = aes.doFinal(payload, 16, payload.length - 16);
		int pad = plain[plain.length - 1] & 0xff;
		for (int i = 1; i <= pad; i++) {
			if (pad > 15 || plain[plain.length - 2 - pad + i] != i) {
				errors.add("Bad confidentiality pad: " + hex(plain));
				return null;
			}
		}
		return Arrays.copyOf(plain, plain.length - pad - 1);
	}

	/**
	 * Handles a SOL packet of the console (IPMI 2.0 table 15-2): the BMC acknowledges every packet with a non-zero
	 * sequence number (section 15.11) in an ACK-only packet, and returns it, or null.
	 */
	private byte[] sol(byte[] packet) {
		if (packet.length < 4) {
			errors.add("Truncated SOL packet: " + hex(packet));
			return null;
		}
		if (!solActive) {
			errors.add("SOL packet while the payload is not active: " + hex(packet));
			return null;
		}
		SolPacket received = new SolPacket(packet);
		solPackets.add(received);
		if (received.sequenceNumber == 0) {
			return null; // ACK-only packets are not acknowledged
		}
		int accepted;
		Integer reply;
		int status = solStatus;
		boolean nack;
		synchronized (solReceived) {
			if (received.sequenceNumber == lastSolSequenceNumber && lastSolAccepted) {
				// A retry of a packet accepted already, whose ACK was lost: its content need not be checked
				accepted = received.data.length;
				reply = null;
			} else {
				synchronized (solReplies) {
					reply = solReplies.poll();
					Integer replyStatus = solReplyStatuses.poll();
					if (replyStatus != null && replyStatus >= 0) {
						status = replyStatus;
					}
				}
				accepted = reply == null || reply < 0 ? received.data.length : Math.min(reply, received.data.length);
				solReceived.write(received.data, 0, accepted);
			}
			// A NACK when the BMC takes fewer characters than the packet carries, or none (even of an empty packet)
			nack = reply != null && reply != LOSE_ACK && reply < Math.max(received.data.length, 1);
			lastSolSequenceNumber = received.sequenceNumber;
			lastSolAccepted = !nack;
		}
		if (reply != null && reply == LOSE_ACK) {
			return null;
		}
		return new byte[] {
				0x00,
				(byte) received.sequenceNumber,
				(byte) accepted,
				(byte) ((nack ? SOL_NACK : 0x00) | status) };
	}

	/**
	 * Get Payload Activation Status (IPMI 2.0 table 24-6): one SOL instance.
	 */
	private byte[] getPayloadActivationStatus(byte[] data) {
		return new byte[] { 0x00, 0x01, (byte) (solActive ? 0x01 : 0x00), 0x00 };
	}

	/**
	 * Activate Payload (IPMI 2.0 table 24-2): payload type, instance, auxiliary data; answers the auxiliary data, the
	 * inbound and outbound payload sizes, the UDP port and the VLAN, LS-byte first.
	 */
	private byte[] activatePayload(byte[] data) {
		Session current = session;
		if (data.length != 6 || (data[0] & 0x3f) != 0x01 || (data[1] & 0x0f) != 1) {
			errors.add("Unexpected Activate Payload request: " + hex(data));
			return new byte[] { (byte) 0xcc }; // invalid data field
		}
		if (current.privilege < solPrivilege) {
			return new byte[] { (byte) 0xd4 }; // insufficient privilege
		}
		if (solActive) {
			return new byte[] { (byte) 0x80 }; // payload already active
		}
		// The payload is only activated in a session on its port (section 24.1); the SOL sequence numbers start over
		// with it (section 15.9), so a packet numbered like one of a previous activation is not a resend
		solActive = solPort == getPort();
		solSessionId = current.managedId;
		synchronized (solReceived) {
			lastSolSequenceNumber = 0;
			lastSolAccepted = false;
		}
		return ByteBuffer
				.allocate(13)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x00)
				.putInt(0)
				.putShort((short) solInboundPayloadSize)
				.putShort((short) solInboundPayloadSize)
				.putShort((short) solPort)
				.putShort((short) 0xffff)
				.array();
	}

	/**
	 * Deactivate Payload (IPMI 2.0 table 24-3).
	 */
	private byte[] deactivatePayload(byte[] data) {
		if (!solActive) {
			return new byte[] { (byte) 0x80 }; // payload already deactivated
		}
		solActive = false;
		return new byte[] { 0x00 };
	}

	/**
	 * Get Payload Instance Info (IPMI 2.0 table 24-7): the managed system session ID of the session where the instance
	 * is active, or 0, and 8 bytes of payload-specific information.
	 */
	private byte[] getPayloadInstanceInfo(byte[] data) {
		Session current = session;
		return ByteBuffer
				.allocate(13)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x00)
				.putInt(solActive ? solSessionId : 0)
				.put(new byte[8])
				.array();
	}

	/**
	 * Get Session Info (IPMI 2.0 table 22-26), by session ID (index FFh) or for the current session (index 0): the
	 * handle, the capacity and the count, then the user ID, privilege and channel when the session is the current one
	 * and still active; a session the BMC expired or closed is not found (three bytes, handle 0), as on Supermicro.
	 */
	private byte[] getSessionInfo(byte[] data) {
		Session current = session;
		boolean alive = current != null && !current.expired && !current.closed;
		int lookedUp = data.length >= 5 && (data[0] & 0xff) == 0xff ?
				ByteBuffer.wrap(data, 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() : current == null ? 0 : current.managedId;
		if (alive && lookedUp == current.managedId) {
			return new byte[] { 0x00, 0x01, 0x10, 0x01, 0x02, (byte) current.privilege, 0x01 };
		}
		return new byte[] { 0x00, 0x00, 0x10, (byte) (alive ? 1 : 0) };
	}

	/**
	 * Set Session Privilege Level (IPMI 2.0 table 22-24): up to the privilege requested in RAKP Message 1.
	 */
	private byte[] setSessionPrivilegeLevel(byte[] data) {
		Session current = session;
		int requested = data[0] & 0x0f;
		if (requested > current.maxPrivilege) {
			return new byte[] { (byte) 0x81 }; // exceeds the privilege limit
		}
		if (requested != 0) {
			current.privilege = requested;
		}
		return new byte[] { 0x00, (byte) current.privilege };
	}

	private synchronized int nextSequenceNumber(Session current) {
		return ++current.sequenceNumber;
	}

	/**
	 * Builds a packet of the session for the console, of the given payload type, encrypted and authenticated, or
	 * neither.
	 */
	private byte[] seal(Session current, int payloadType, byte[] message, int sequenceNumber, boolean protect)
			throws GeneralSecurityException {
		if (!protect) {
			return packet(payloadType, current.consoleId, sequenceNumber, message);
		}
		int confidentialityPad = 15 - message.length % 16;
		byte[] plain = Arrays.copyOf(message, message.length + confidentialityPad + 1);
		for (int i = 1; i <= confidentialityPad; i++) {
			plain[message.length + i - 1] = (byte) i;
		}
		plain[plain.length - 1] = (byte) confidentialityPad;
		byte[] iv = new byte[16];
		random.nextBytes(iv);
		Cipher aes = Cipher.getInstance("AES/CBC/NoPadding");
		aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(current.k2, 0, 16, "AES"), new IvParameterSpec(iv));
		byte[] payload = concat(iv, aes.doFinal(plain));
		int integrityPad = (4 - (12 + payload.length + 2) % 4) % 4;
		ByteBuffer out = ByteBuffer
				.allocate(16 + payload.length + integrityPad + 2 + current.macLength)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put(RMCP_HEADER)
				.put((byte) 0x06)
				.put((byte) (0xc0 | payloadType))
				.putInt(current.consoleId)
				.putInt(sequenceNumber)
				.putShort((short) payload.length)
				.put(payload);
		for (int i = 0; i < integrityPad; i++) {
			out.put((byte) 0xff);
		}
		out.put((byte) integrityPad).put((byte) 0x07);
		return out.put(authCode(current, out.array(), out.position())).array();
	}

	/**
	 * @return the AuthCode of a packet: HMAC-K1 of AuthType/Format up to Next Header, truncated (IPMI 2.0 section
	 *         13.28.4)
	 */
	private static byte[] authCode(Session current, byte[] datagram, int end) throws GeneralSecurityException {
		return Arrays.copyOf(hmac(current.mac, current.k1, Arrays.copyOfRange(datagram, 4, end)), current.macLength);
	}

	/**
	 * Builds an RMCP+ packet without session trailer.
	 */
	private static byte[] packet(int payloadType, int sessionId, int sequenceNumber, byte[] payload) {
		return ByteBuffer
				.allocate(16 + payload.length)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put(RMCP_HEADER)
				.put((byte) 0x06)
				.put((byte) payloadType)
				.putInt(sessionId)
				.putInt(sequenceNumber)
				.putShort((short) payload.length)
				.put(payload)
				.array();
	}

	/**
	 * Executes an IPMI request message (rsAddr, netFn/rsLUN, checksum, rqAddr, rqSeq/rqLUN, cmd, data, checksum: IPMI
	 * 2.0 section 13.8) and returns the response message.
	 */
	private byte[] respond(byte[] request) {
		if (request.length < 7 || checksum(request, 0, 3) != 0 || checksum(request, 3, request.length) != 0) {
			errors.add("Bad IPMI request message: " + hex(request));
			return null;
		}
		int netFn = (request[1] & 0xff) >> 2;
		int command = request[5] & 0xff;
		count(key(netFn, command));
		Function<byte[], byte[]> handler = handlers.get(key(netFn, command));
		byte[] data = Arrays.copyOfRange(request, 6, request.length - 1);
		byte[] result = handler == null ? new byte[] { (byte) 0xc1 } : handler.apply(data); // C1h: invalid command
		byte[] response = new byte[7 + result.length];
		response[0] = request[3];
		response[1] = (byte) ((netFn + 1) << 2 | request[4] & 0x03);
		response[2] = checksum(response, 0, 2);
		response[3] = request[0];
		response[4] = (byte) (request[4] & 0xfc | request[1] & 0x03);
		response[5] = request[5];
		System.arraycopy(result, 0, response, 6, result.length);
		response[response.length - 1] = checksum(response, 3, response.length - 1);
		return response;
	}

	/**
	 * Get Channel Cipher Suites (IPMI 2.0 table 22-18): 16 bytes of record data per list index.
	 */
	private byte[] getChannelCipherSuites(byte[] data) {
		int from = Math.min(16 * (data[2] & 0x3f), cipherSuiteRecords.length);
		byte[] records = Arrays.copyOfRange(cipherSuiteRecords, from, Math.min(from + 16, cipherSuiteRecords.length));
		return concat(new byte[] { 0x00, CHANNEL }, records);
	}

	/**
	 * Get Channel Authentication Capabilities (IPMI 2.0 table 22-15): extended capabilities, MD5 and straight
	 * password, Kg status, non-null user names, IPMI v1.5 and v2.0 connections, no OEM.
	 */
	private byte[] getChannelAuthenticationCapabilities(byte[] data) {
		return new byte[] { 0x00, CHANNEL, (byte) 0x94, (byte) (kg == null ? 0x04 : 0x24), 0x03, 0x00, 0x00, 0x00, 0x00 };
	}

	/**
	 * Close Session (IPMI 2.0 table 22-23): the session ID to close.
	 */
	private byte[] closeSession(byte[] data) {
		Session current = session;
		if (current == null
				|| data.length < 4
				|| ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt() != current.managedId) {
			return new byte[] { (byte) 0x87 }; // invalid session ID
		}
		current.closed = true;
		// The payloads of a session end with it (IPMI 2.0 section 24.2): those of this session, not of another one
		if (solSessionId == current.managedId) {
			solActive = false;
		}
		return new byte[] { 0x00 };
	}

	private boolean has(Fault fault) {
		return handshakeFaults.contains(fault);
	}

	private void count(int key) {
		counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
	}

	private static int key(int netFn, int command) {
		return 0x10000 | netFn << 8 | command;
	}

	private static byte[] hmac(String algorithm, byte[] key, byte[]... data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		for (byte[] part : data) {
			mac.update(part);
		}
		return mac.doFinal();
	}

	private static byte checksum(byte[] bytes, int from, int to) {
		int sum = 0;
		for (int i = from; i < to; i++) {
			sum += bytes[i];
		}
		return (byte) -sum;
	}

	private static byte[] le(int value) {
		return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
	}

	private static byte[] filled(int value) {
		byte[] bytes = new byte[MAX_KEY_LENGTH];
		Arrays.fill(bytes, (byte) value);
		return bytes;
	}

	private static boolean isFilled(byte[] bytes, int value) {
		for (byte b : bytes) {
			if (b != (byte) value) {
				return false;
			}
		}
		return true;
	}

	private static boolean isZero(byte[] bytes) {
		return isFilled(bytes, 0);
	}

	private static byte[] concat(byte[] first, byte[] second) {
		byte[] bytes = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, bytes, first.length, second.length);
		return bytes;
	}

	private static String hex(byte[] bytes) {
		StringBuilder builder = new StringBuilder();
		for (byte b : bytes) {
			builder.append(String.format("%02x", b));
		}
		return builder.toString();
	}
}
