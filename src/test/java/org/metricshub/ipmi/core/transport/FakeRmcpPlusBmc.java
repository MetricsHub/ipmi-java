package org.metricshub.ipmi.core.transport;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * 13.6, AES-CBC-128 per section 13.29): Get Chassis Status, Close Session and the commands registered with
 * {@link #handle(int, int, Function)}. What the console sends that a BMC would reject is listed by
 * {@link #getErrors()}.
 * </p>
 */
public class FakeRmcpPlusBmc implements AutoCloseable {

	/** Network function of the chassis commands. */
	public static final int NETFN_CHASSIS = 0x00;
	/** Network function of the application commands. */
	public static final int NETFN_APP = 0x06;
	/** Get Chassis Status (NetFn Chassis). */
	public static final int GET_CHASSIS_STATUS = 0x01;
	/** Get Channel Authentication Capabilities (NetFn App). */
	public static final int GET_CHANNEL_AUTHENTICATION_CAPABILITIES = 0x38;
	/** Close Session (NetFn App). */
	public static final int CLOSE_SESSION = 0x3c;
	/** Get Channel Cipher Suites (NetFn App). */
	public static final int GET_CHANNEL_CIPHER_SUITES = 0x54;

	/** RMCP+ payload type of the Open Session Request (IPMI 2.0 table 13-16). */
	public static final int OPEN_SESSION_REQUEST = 0x10;
	/** RMCP+ payload type of the RAKP Message 1. */
	public static final int RAKP_1 = 0x12;
	/** RMCP+ payload type of the RAKP Message 3. */
	public static final int RAKP_3 = 0x14;

	/** Get Chassis Status response data the BMC answers by default: power on, no power event, no chassis flag. */
	public static final byte[] CHASSIS_STATUS = { 0x01, 0x00, 0x00 };

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
		private int sequenceNumber;

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
		handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> concat(new byte[1], CHASSIS_STATUS));
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
	 * @return what the console sent that a BMC would reject (malformed messages, wrong checksums, AuthCodes, pads or
	 *         RAKP 3 codes, unprotected packets in a session)
	 */
	public List<String> getErrors() {
		return new ArrayList<>(errors);
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
		if (current == null || !current.active || current.closed || in.getInt(6) != current.managedId) {
			errors.add("Packet of no open session: " + hex(datagram));
			return null;
		}
		if ((datagram[5] & 0xff) != 0xc0) {
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
		byte[] response = request == null ? null : respond(request);
		if (response == null) {
			return null;
		}
		Fault fault = replyFaults.get(sessionRequests.incrementAndGet());
		if (fault == Fault.DROP) {
			return null;
		}
		if (fault == Fault.PLAINTEXT) {
			return seal(current, response, ++current.sequenceNumber, false);
		}
		byte[] reply = seal(
				current,
				response,
				fault == Fault.REUSED_SEQUENCE_NUMBER ?
						current.sequenceNumber : ++current.sequenceNumber,
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
	 * Builds a packet of the session for the console, encrypted and authenticated, or neither.
	 */
	private byte[] seal(Session current, byte[] message, int sequenceNumber, boolean protect)
			throws GeneralSecurityException {
		if (!protect) {
			return packet(0x00, current.consoleId, sequenceNumber, message);
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
				.put((byte) 0xc0)
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
