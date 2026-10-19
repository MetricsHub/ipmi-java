package org.metricshub.ipmi.client;

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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;

import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sol.SOLException;
import org.metricshub.ipmi.core.api.sol.SerialOverLan;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.app.GetDeviceId;
import org.metricshub.ipmi.core.coding.commands.app.SendMessage;
import org.metricshub.ipmi.core.coding.commands.chassis.ChassisControl;
import org.metricshub.ipmi.core.coding.commands.chassis.ChassisIdentify;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;
import org.metricshub.ipmi.core.coding.commands.payload.DeactivatePayload;
import org.metricshub.ipmi.core.coding.commands.payload.GetPayloadActivationStatus;
import org.metricshub.ipmi.core.coding.commands.payload.GetPayloadActivationStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.payload.GetPayloadInstanceInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrRepositoryInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrRepositoryInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReading;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingFactors;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AbstractSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AddressType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.commands.session.AuthenticationException;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilitiesResponseData;
import org.metricshub.ipmi.core.coding.commands.session.GetSessionInfo;
import org.metricshub.ipmi.core.coding.commands.session.GetSessionInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.session.Rakp1;
import org.metricshub.ipmi.core.coding.commands.session.SetSessionPrivilegeLevel;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanConstants;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.Constants;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.connection.ConnectionException;
import org.metricshub.ipmi.core.connection.Ipmi20UnsupportedException;
import org.metricshub.ipmi.core.connection.Session;
import org.metricshub.ipmi.core.connection.SessionException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A client of one BMC over IPMI 2.0 (RMCP+). It is built with {@link #builder(String)}, owns <b>one session</b>,
 * opened by the first operation that needs one and kept alive until {@link #close()}, and runs its operations one
 * at a time on that session.
 *
 * <pre>
 * IpmiClient.Builder builder = IpmiClient.builder("bmc.example.com").credentials("monitor", password);
 * try (IpmiClient bmc = builder.privilege(PrivilegeLevel.Operator).build()) {
 * 	System.out.println(bmc.deviceId()); // manufacturer, product, firmware revision
 * 	ChassisStatus chassis = bmc.chassisStatus();
 * 	if (!chassis.powerOn()) {
 * 		bmc.chassisControl(PowerCommand.PowerUp); // the Operator privilege set above
 * 	}
 * 	for (Sensor sensor : bmc.sensors().types(SensorType.Temperature).execute()) {
 * 		sensor.reading().ifPresent(value -&gt; System.out.println(sensor.name() + " = " + value + " " + sensor.unit()));
 * 	}
 * 	for (Fru fru : bmc.frus().execute()) {
 * 		System.out.println(fru.name() + ": " + fru.manufacturer() + " " + fru.model() + " S/N " + fru.serialNumber());
 * 	}
 * 	for (SelEntry entry : bmc.sel().execute()) {
 * 		System.out.println(entry);
 * 	}
 * }
 * </pre>
 * <p>
 * <b>Close the client</b>: an open client holds one of the few session slots of the BMC (typically 4 to 8) with its
 * keep-alive messages for as long as the JVM runs. Use try-with-resources.
 * </p>
 * <p>
 * <b>Session recovery</b>: when the session is lost (the BMC rebooted or expired it), the next operation opens a new
 * one. A read operation that fails because the session was lost while it ran is retried once on a new session;
 * {@link #send(IpmiCommandCoder)}, {@link #raw(int, int)} and the control operations are never replayed.
 * </p>
 * <p>
 * <b>Threads</b>: a client can be shared between threads; its operations are serialized. A BMC drops replies under
 * concurrent sessions, so the client never opens two. Clients of different BMCs run in parallel.
 * </p>
 * <p>
 * <b>Failures</b> are reported as {@link IpmiClientException}s, whose subclasses say what to act on.
 * </p>
 */
// EI_EXPOSE_REP also matches EI_EXPOSE_REP2
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The password char[] and BMC key byte[] are "
		+ "deliberately shared, not copied, so that the caller can wipe the only copy of the credentials")
public final class IpmiClient implements AutoCloseable {

	/** Deadline of each operation when the builder sets none: 2 minutes. */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

	/** Timeout of each message when the builder sets none: 5 seconds. */
	public static final Duration DEFAULT_MESSAGE_TIMEOUT = Duration.ofSeconds(5);

	/** Resends of a message without reply when the builder sets none: 3. */
	public static final int DEFAULT_RETRIES = 3;

	/** Period of the keep-alive messages when the builder sets none: 30 seconds. */
	public static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(30);

	/**
	 * The cipher suites the client may choose when the builder sets none, by order of preference (IPMI 2.0 table
	 * 22-20): those that sign every message, and encrypt it when possible (17: RAKP-HMAC-SHA256, HMAC-SHA256-128,
	 * AES-CBC-128; 3: RAKP-HMAC-SHA1, HMAC-SHA1-96, AES-CBC-128; 8: RAKP-HMAC-MD5, HMAC-MD5-128, AES-CBC-128; then
	 * 16, 2 and 7, the same without encryption; last 12 and 11, whose MD5-128 integrity is a plain MD5 of the
	 * password around the message, for the older BMCs that offer nothing better).
	 */
	private static final int[] DEFAULT_CIPHER_SUITES = { 17, 3, 8, 16, 2, 7, 12, 11 };

	/**
	 * The suites without integrity (0, 1, 6, 15), which the builder refuses: the list of suites comes unauthenticated,
	 * so falling back to them would let an attacker downgrade the session to one whose replies can be forged.
	 */
	private static final Set<Integer> SUITES_WITHOUT_INTEGRITY = new HashSet<Integer>(
			Arrays.asList(0, 1, 6, 15));

	private static final int LAST_STANDARD_SUITE = 19;
	private static final int MAX_PORT = 65535;

	/** How long {@link #close()} waits for the Close Session exchange, beyond the message timeout. */
	private static final long CLOSE_GRACE_MS = 1000;

	private final String host;
	private final int port;
	private final String user;
	private final char[] password;
	private final byte[] bmcKey;
	private final PrivilegeLevel privilege;
	private final int[] cipherSuites;
	private final boolean discoverCipherSuite;
	private final Duration timeout;
	private final int messageTimeoutMs;
	private final int retries;
	private final long keepAliveMs;
	private final boolean sdrCache;

	private final ExecutorService worker;
	/** Guards the submissions to the worker and the closed flag: no operation can queue behind the logout. */
	private final Object submission = new Object();
	private boolean closed;
	/** The consoles opened in the current session, to invalidate when the session is dropped. */
	private final List<SolConsole> consoles = new CopyOnWriteArrayList<SolConsole>();

	// Touched by the worker thread, and by close() once the worker is done
	private volatile IpmiConnector connector;
	private Link link;
	private SdrRepository repository;

	private IpmiClient(Builder builder) {
		this.host = builder.host;
		this.port = builder.port;
		this.user = builder.user;
		this.password = builder.password;
		this.bmcKey = builder.bmcKey;
		this.privilege = builder.privilege;
		this.cipherSuites = builder.cipherSuites;
		this.discoverCipherSuite = builder.discoverCipherSuite;
		this.timeout = builder.timeout;
		this.messageTimeoutMs = (int) builder.messageTimeout.toMillis();
		this.retries = builder.retries;
		this.keepAliveMs = builder.keepAlive.toMillis();
		this.sdrCache = builder.sdrCache;
		this.worker = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "ipmi-client-" + host);
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Starts building a client of the BMC at the given host.
	 *
	 * @param host the host name or IP address of the BMC
	 * @return the builder
	 */
	public static Builder builder(String host) {
		return new Builder(host);
	}

	/**
	 * Returns the host name or IP address of the BMC.
	 *
	 * @return the host
	 */
	public String host() {
		return host;
	}

	/**
	 * Reads what the BMC supports before any login: its authentication options and the cipher suites it offers (Get
	 * Channel Authentication Capabilities and Get Channel Cipher Suites). Needs no credentials and opens no session.
	 *
	 * @return the capabilities
	 * @throws IpmiConnectionException when the BMC does not answer
	 * @throws IpmiClientException when the BMC does not support IPMI 2.0 (RMCP+), or the replies cannot be decoded
	 */
	public BmcCapabilities capabilities() {
		return run("capabilities", timeout, false, false, this::readCapabilities);
	}

	/**
	 * Reads the state of the chassis: power, faults, intrusion, identify LED (Get Chassis Status).
	 *
	 * @return the chassis status
	 * @throws IpmiClientException when the session cannot be opened or the BMC does not answer
	 */
	public ChassisStatus chassisStatus() {
		return run("chassis status", timeout, true, true, () -> new ChassisStatus(link.send(new GetChassisStatus())));
	}

	/**
	 * Reads the identity of the BMC: manufacturer and product IDs, firmware revision, IPMI version, functions (Get
	 * Device ID). The first thing to read to tell the platform apart, before choosing the OEM decodings it needs.
	 *
	 * @return the device ID
	 * @throws IpmiClientException when the session cannot be opened or the BMC does not answer
	 */
	public DeviceId deviceId() {
		return run("device id", timeout, true, true, () -> new DeviceId(link.send(new GetDeviceId())));
	}

	/**
	 * Controls the power of the chassis (Chassis Control, IPMI 2.0 section 28.3): power up, power down, power
	 * cycle, hard reset, soft shutdown or diagnostic interrupt. The BMC answers once it accepts the command and may
	 * carry it out afterwards: {@link #chassisStatus()} tells when the power state changed. Needs the Operator
	 * privilege ({@link Builder#privilege(PrivilegeLevel)}).
	 * <p>
	 * The command is <b>sent once</b>, never again when its reply is lost or is a {@code C3h} (timeout on the BMC
	 * side, response unavailable), as the BMC may have carried it out and a reset sent again would reset the system
	 * twice, and never on a new session: a lost reply fails with {@link IpmiTimeoutException}, a {@code C3h} with
	 * {@link IpmiCompletionCodeException}, and the power state is then to be read with {@link #chassisStatus()}. A
	 * {@link PowerCommand#PowerCycle} is refused when the system is off, as the specification recommends: some BMCs
	 * would power the system up instead of doing nothing.
	 * </p>
	 *
	 * @param command the power command
	 * @throws IllegalStateException for a power cycle of a system that is off
	 * @throws IpmiCompletionCodeException when the BMC refuses the command: {@code D4h} at a session below Operator,
	 *         {@code D5h} for a command it cannot carry out in the present state (a reset of a system that is off),
	 *         {@code CCh} for an optional command it does not implement
	 * @throws IpmiTimeoutException when the BMC does not answer: whether it carried the command out is unknown
	 * @throws IpmiClientException when the session cannot be opened
	 */
	public void chassisControl(PowerCommand command) {
		Objects.requireNonNull(command, "command");
		run("chassis control " + command, timeout, true, false, () -> {
			if (command == PowerCommand.PowerCycle && !link.send(new GetChassisStatus()).isPowerOn()) {
				throw new IllegalStateException(
						"The system is off: a power cycle would power it up on some BMCs, use PowerUp to power it up");
			}
			return link.sendOnce(new ChassisControl(command));
		});
	}

	/**
	 * Makes the chassis identify itself (Chassis Identify, IPMI 2.0 section 28.5), typically with a blinking LED,
	 * for the given number of seconds, or turns the identify off. Needs the Operator privilege. Sent once, never
	 * again when its reply is lost nor on a new session: a lost reply fails with {@link IpmiTimeoutException}, and
	 * {@link ChassisStatus#identifyState()} tells what the BMC did.
	 *
	 * @param seconds how long the identify stays on, 1 to 255 (the BMC may stretch it by 20%); 0 turns it off
	 * @throws IllegalArgumentException when {@code seconds} is not between 0 and 255
	 * @throws IpmiCompletionCodeException when the BMC refuses the command: {@code D4h} at a session below Operator,
	 *         {@code C1h} when it does not implement it
	 * @throws IpmiTimeoutException when the BMC does not answer
	 * @throws IpmiClientException when the session cannot be opened
	 */
	public void chassisIdentify(int seconds) {
		ChassisIdentify command = new ChassisIdentify(seconds, false);
		run("chassis identify", timeout, true, false, () -> link.sendOnce(command));
	}

	/**
	 * Makes the chassis identify itself until {@link #chassisIdentify(int) chassisIdentify(0)} turns it off: the
	 * optional "force identify on" of Chassis Identify, which not every BMC implements. Needs the Operator privilege.
	 * Sent once, like {@link #chassisIdentify(int)}.
	 *
	 * @throws IpmiCompletionCodeException when the BMC refuses the command: {@code D4h} at a session below Operator,
	 *         {@code C1h} when it does not implement Chassis Identify, {@code CCh} when it does not implement the
	 *         force byte
	 * @throws IpmiTimeoutException when the BMC does not answer
	 * @throws IpmiClientException when the session cannot be opened
	 */
	public void chassisIdentifyForever() {
		run("chassis identify", timeout, true, false, () -> link.sendOnce(new ChassisIdentify(0, true)));
	}

	/**
	 * Reads the sensors of the BMC: {@code sensors().execute()} for all of them, with {@link SensorsRequest#types}
	 * and {@link SensorsRequest#names} to select some before their readings are requested.
	 *
	 * @return the request to complete
	 */
	public SensorsRequest sensors() {
		return new SensorsRequest(this);
	}

	/**
	 * Reads the FRU inventories of the system: {@code frus().execute()} for all of them, with
	 * {@link FrusRequest#ids} to select some. The SDR repository is walked once per client: {@link #sensors()} and
	 * {@link #frus()} share it until the BMC reports a change.
	 *
	 * @return the request to complete
	 */
	public FrusRequest frus() {
		return new FrusRequest(this);
	}

	/**
	 * Reads the System Event Log (SEL): {@code sel().execute()} for every entry, with
	 * {@link SelRequest#since(SelEntry)} for the entries added after one already read.
	 *
	 * @return the request to complete
	 */
	public SelRequest sel() {
		return new SelRequest(this);
	}

	/**
	 * Sends any command of the protocol core in the session and returns its typed reply, for the commands the client
	 * does not wrap: {@code bmc.send(new GetDeviceId())}. A command is never replayed on a new session.
	 *
	 * @param <R> the type of the reply
	 * @param command the command
	 * @return the reply
	 * @throws IpmiCompletionCodeException when the BMC answers with an error completion code
	 * @throws IpmiClientException when the session cannot be opened or the BMC does not answer
	 */
	public <R extends ResponseData> R send(IpmiCommandCoder<R> command) {
		return send(command, null);
	}

	/** Sends a command under the given deadline (the client's when null), never replayed on a new session. */
	<R extends ResponseData> R send(IpmiCommandCoder<R> command, Duration deadline) {
		Objects.requireNonNull(command, "command");
		return run(command.getClass().getSimpleName(), deadline, true, false, () -> link.send(command));
	}

	/**
	 * Sends any request in the session, like {@code ipmitool raw}, and returns its reply whatever its completion code:
	 * {@code bmc.raw(0x2C, 0x01).data(0xDC, 0x01).execute()} is Get DCMI Capabilities Info. Any request network
	 * function is accepted, the extension and OEM ones included (Group Extension 2Ch, whose first data byte is the
	 * defining body, DCh for DCMI; OEM/Group 2Eh, whose first three are an IANA enterprise number; controller-specific
	 * OEM 30h-3Eh). An error completion code is returned, not thrown, and not retried, a busy one ({@code C0h}) or
	 * {@code C3h} included. Like {@link #send(IpmiCommandCoder)}, the request is sent again when its reply is lost
	 * (the retries of the builder) and never replayed on a new session: a control request sent raw has none of the
	 * safeguards of {@link #chassisControl(PowerCommand)}.
	 *
	 * @param networkFunction the network function of the request: an even number from 00h to 3Eh (the odd ones are
	 *        those of the responses, IPMI 2.0 section 5.1)
	 * @param command the command code, 00h to FFh
	 * @return the request to complete
	 * @throws IllegalArgumentException when the network function or the command is out of range
	 */
	public RawRequest raw(int networkFunction, int command) {
		return new RawRequest(this, networkFunction, command);
	}

	/**
	 * Opens a Serial over LAN console in the session: {@code sol().open()}.
	 *
	 * @return the request to complete
	 */
	public SolRequest sol() {
		return new SolRequest(this);
	}

	/**
	 * Closes the session (Close Session, within the message timeout), releases the UDP port and the threads. Closing
	 * a closed client does nothing, and a BMC that does not answer the logout does not fail the caller.
	 */
	@Override
	public void close() {
		Future<?> logout;
		synchronized (submission) {
			if (closed) {
				return;
			}
			closed = true;
			logout = worker.submit(this::logout);
		}
		boolean interrupted = false;
		try {
			logout.get(messageTimeoutMs + CLOSE_GRACE_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			// An operation still running ahead of the logout, or a BMC that does not answer it
			logout.cancel(true);
		} catch (InterruptedException e) {
			logout.cancel(true);
			interrupted = true;
		} catch (ExecutionException e) {
			// Best effort: the port and the threads are released anyway
		}
		worker.shutdownNow();
		try {
			// The interrupted operation ends at its current wait; then the port can be released from here
			if (!worker.awaitTermination(CLOSE_GRACE_MS, TimeUnit.MILLISECONDS)) {
				// A call that cannot be interrupted (name resolution, a blocking send): the port is released all the
				// same, and that call fails when it returns
				interrupted = interrupted || Thread.currentThread().isInterrupted();
			}
		} catch (InterruptedException e) {
			interrupted = true;
		}
		releaseConnector();
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private synchronized void releaseConnector() {
		link = null;
		for (SolConsole console : consoles) {
			console.invalidate();
		}
		consoles.clear();
		if (connector != null) {
			connector.tearDown();
			connector = null;
		}
	}

	SensorList readSensors(BiPredicate<AbstractSensorRecord, String> selection, Duration deadline) {
		return run("sensors", deadline, true, true, () -> {
			SdrRepository sdr = repository();
			List<Sensor> sensors = new ArrayList<Sensor>();
			// The controllers behind the BMC, and their LUNs, that did not answer: the walk does not wait for them again
			Set<Integer> silentOwners = new HashSet<Integer>();
			for (SensorRecord record : sdr.records()) {
				// Only the Full and Compact sensor records have a reading (IPMI 2.0 section 43)
				if (!(record instanceof FullSensorRecord || record instanceof CompactSensorRecord)) {
					continue;
				}
				AbstractSensorRecord sensorRecord = (AbstractSensorRecord) record;
				// A Compact record with a share count of N describes N sensors with consecutive numbers (IPMI 2.0 table
				// 43-2, byte 24); a count that runs past sensor number 255 is cut there
				int first = TypeConverter.byteToInt(sensorRecord.getSensorNumber());
				for (int instance = 0; instance < Sensor.instances(sensorRecord) && first + instance <= 0xff; instance++) {
					if (selection.test(sensorRecord, Sensor.instanceName(sensorRecord, instance))) {
						sensors.add(readSensor(sensorRecord, instance, silentOwners));
					}
				}
			}
			return new SensorList(sensors, sdr.skippedRecords());
		});
	}

	/**
	 * Reads a sensor from its owner, as its record gives it (IPMI 2.0 section 5.4): the BMC, at the LUN of the record;
	 * another controller, through the BMC (Send Message); none for a sensor of system software, whose number the BMC
	 * would answer for one of its own sensors.
	 */
	private Sensor readSensor(AbstractSensorRecord record, int instance, Set<Integer> silentOwners) {
		if (record.getAddressType() == AddressType.SystemSoftwareId) {
			return Sensor
					.of(
							record,
							instance,
							null,
							null,
							String
									.format(
											"The sensor belongs to system software (owner ID %02Xh), not to a controller: it has no reading",
											ownerAddress(record) | 1));
		}
		boolean bridged = isBridged(record);
		int owner = TypeConverter.byteToInt(record.getChannelNumber()) << 10
				| ownerAddress(record) << 2
				| TypeConverter.byteToInt(record.getSensorOwnerLun());
		if (bridged && silentOwners.contains(owner)) {
			return Sensor.of(record, instance, null, null, "No reply from " + describeOwner(record) + " through the BMC");
		}
		int number = TypeConverter.byteToInt(record.getSensorNumber()) + instance;
		int lun = TypeConverter.byteToInt(record.getSensorOwnerLun());
		GetSensorReadingResponseData reading = null;
		try {
			reading = sendToOwner(record, new GetSensorReading(number, lun));
			if (record instanceof FullSensorRecord
					&& ((FullSensorRecord) record).isNonLinear()
					&& ((FullSensorRecord) record).hasAnalogReading()
					&& Sensor.isValid(reading)) {
				// IPMI 2.0 section 36.2: the factors of the record hold at the nominal reading only
				int raw = (int) reading.getPlainSensorReading();
				return Sensor
						.of(
								record,
								instance,
								reading,
								sendToOwner(record, new GetSensorReadingFactors(number, raw, lun)).getFactors(),
								null);
			}
			return Sensor.of(record, instance, reading, null, null);
		} catch (IpmiCompletionCodeException e) {
			// A reading the BMC refuses (not present, insufficient privilege...) costs that sensor, not the whole walk;
			// refused factors cost its reading only
			String meaning = bridged ? SendMessage.describeCompletionCode(e.rawCompletionCode()) : null;
			return Sensor
					.of(
							record,
							instance,
							reading,
							null,
							(reading == null ? "Get Sensor Reading" : "Get Sensor Reading Factors")
									+ (bridged ? " through the BMC to " + describeOwner(record) : "") + " failed: "
									+ (meaning == null ? e.completionCode().getMessage() : meaning)
									+ String.format(" (completion code %02Xh)", e.rawCompletionCode()));
		} catch (IpmiTimeoutException e) {
			if (!bridged || link.lost) {
				// The BMC stopped answering: the walk cannot go on
				throw e;
			}
			// The controller behind the BMC did not answer (absent, or the BMC does not forward its response): the
			// session is fine, and the sensors of the other owners are read. A controller that answered the reading and
			// not the factors only costs this sensor its conversion
			if (reading == null) {
				silentOwners.add(owner);
			}
			return Sensor
					.of(
							record,
							instance,
							reading,
							null,
							"No reply from " + describeOwner(record) + " through the BMC"
									+ (reading == null ? "" : " to Get Sensor Reading Factors"));
		} catch (IpmiConnectionException e) {
			throw e;
		} catch (IpmiClientException e) {
			// A reply the BMC malformed costs that sensor too
			return Sensor.of(record, instance, reading, null, e.getMessage());
		}
	}

	/**
	 * Sends a command to the controller that owns a sensor: the BMC itself, or another controller through the BMC
	 * (Send Message with response tracking, IPMI 2.0 section 6.13.3).
	 */
	private <R extends ResponseData> R sendToOwner(AbstractSensorRecord record, IpmiCommandCoder<R> command) {
		return isBridged(record) ?
				link
						.send(
								new SendMessage<R>(TypeConverter.byteToInt(record.getChannelNumber()), ownerAddress(record), command)) :
				link.send(command);
	}

	/**
	 * Tells whether a sensor belongs to another controller than the BMC: another IPMB address, or another channel.
	 */
	private static boolean isBridged(AbstractSensorRecord record) {
		return ownerAddress(record) != IpmiLanConstants.BMC_ADDRESS || record.getChannelNumber() != 0;
	}

	/**
	 * Returns the sensor owner ID of the record in its 8-bit form (IPMI 2.0 table 5-3), without the software ID bit.
	 */
	private static int ownerAddress(AbstractSensorRecord record) {
		return (TypeConverter.byteToInt(record.getSensorOwnerId()) & 0x7f) << 1;
	}

	private static String describeOwner(AbstractSensorRecord record) {
		return String.format("the controller at %02Xh on channel %d", ownerAddress(record), record.getChannelNumber())
				+ (record.getSensorOwnerLun() == 0 ? "" : ", LUN " + record.getSensorOwnerLun());
	}

	FruList readFrus(Set<Integer> ids, Duration deadline) {
		return run("FRUs", deadline, true, true, () -> {
			SdrRepository sdr = repository();
			return new FruList(FruReader.read(link, sdr.records(), ids), sdr.skippedRecords());
		});
	}

	SelEntryList readSel(int since, SelEntry sinceEntry, Duration deadline) {
		return run("SEL", deadline, true, true, () -> SelReader.read(link, host, since, sinceEntry));
	}

	SolConsole openSol(Duration deadline) {
		return run("SOL console", deadline, true, false, () -> {
			try {
				SolConsole console = new SolConsole(activateSol(), consoles::remove);
				consoles.add(console);
				return console;
			} catch (SOLException e) {
				if (e.getCause() instanceof IPMIException) {
					throw new IpmiCompletionCodeException("Activating the SOL payload", (IPMIException) e.getCause());
				}
				throw new IpmiClientException("Cannot open the SOL console of " + host + ": " + e.getMessage(), e);
			} catch (SessionException e) {
				if (e.getCause() instanceof SessionException) {
					// The BMC refused to raise the session of the SOL payload port to the privilege of the client
					throw translateHandshake((SessionException) e.getCause());
				}
				throw new IpmiConnectionException(
						"Cannot open the session of the SOL payload port of " + host + ": " + e.getMessage(),
						e);
			}
		});
	}

	/**
	 * Returns the SDR repository: the cached one when the BMC reports it unchanged (same record count and timestamps
	 * of the most recent addition and erase, IPMI 2.0 section 33.9), a new walk otherwise.
	 */
	/**
	 * Activates the SOL payload; when the BMC has no free instance, one whose session the BMC no longer knows (a
	 * console that was killed, which some BMCs keep active for good) is deactivated and the activation tried again.
	 */
	private SerialOverLan activateSol() throws SOLException, SessionException {
		SerialOverLan sol;
		try {
			sol = new SerialOverLan(link.connector, link.session);
		} catch (SOLException e) {
			if (e.getCause() != null || !reclaimStaleSol(e)) {
				throw e;
			}
			sol = new SerialOverLan(link.connector, link.session);
		}
		if (sol.getRaisedPrivilegeLevel() != null && sol.getConnectionHandle() == link.handle) {
			// The console raised the session of the client (not one of its own on the port of the payload): the
			// session runs at that level, and the next D4h is raised from there
			link.note(sol.getRaisedPrivilegeLevel());
		}
		return sol;
	}

	/**
	 * @return whether the activation is worth trying again: an instance held by a session the BMC does not have any
	 *         more was deactivated, or one was freed since the failed activation
	 * @throws SOLException when every instance is held by a session the BMC still has
	 */
	private boolean reclaimStaleSol(SOLException failure) throws SOLException {
		GetPayloadActivationStatusResponseData status = link.send(new GetPayloadActivationStatus(PayloadType.Sol));
		Set<Integer> free = new HashSet<Integer>();
		for (Byte instance : status.getAvailableInstances()) {
			free.add(TypeConverter.byteToInt(instance));
		}
		StringBuilder holders = new StringBuilder();
		for (int instance = 1; instance <= status.getInstanceCapacity(); instance++) {
			if (free.contains(instance)) {
				continue;
			}
			int holder = link.send(new GetPayloadInstanceInfo(PayloadType.Sol, instance)).getSessionId();
			GetSessionInfoResponseData session;
			try {
				session = link.send(new GetSessionInfo(holder));
			} catch (IpmiCompletionCodeException e) {
				// Only a code that says the session is not there (CBh, CCh) allows the deactivation: a refusal (D4h),
				// an unsupported command (C1h) or a busy BMC reaches the caller, and the holder keeps its console
				if (e.completionCode() != CompletionCode.DataNotPresent && e.completionCode() != CompletionCode.InvalidData) {
					throw e;
				}
				session = null;
			}
			if (holder == 0 || session == null || !session.isSessionFound()) {
				// Another client may have reclaimed the instance meanwhile: deactivate it only while the dead session
				// still holds it (one round trip of window: IPMI has no atomic form of this)
				if (link.send(new GetPayloadInstanceInfo(PayloadType.Sol, instance)).getSessionId() != holder) {
					return true;
				}
				link.send(new DeactivatePayload(PayloadType.Sol, instance));
				return true;
			}
			holders
					.append(holders.length() == 0 ? "" : ", ")
					.append(String.format("instance %d by session %08Xh", instance, holder))
					.append(" (user ID ")
					.append(session.getUserId())
					.append(", privilege ")
					.append(session.getPrivilegeLevel())
					.append(')');
		}
		if (holders.length() == 0) {
			// Every instance is free now: a console released its instance since the failed activation
			return !free.isEmpty();
		}
		throw new SOLException("The SOL payload is in use by another session: " + holders, failure);
	}

	private SdrRepository repository() {
		GetSdrRepositoryInfoResponseData info;
		try {
			info = link.send(new GetSdrRepositoryInfo());
		} catch (IpmiCompletionCodeException e) {
			// A BMC that refuses Get SDR Repository Info gets walked every time
			info = null;
		}
		if (sdrCache && repository != null && repository.isCurrent(info)) {
			return repository;
		}
		SdrRepository walked = info == null && holdsDeviceSdrsOnly() ?
				SdrWalker.walkDevice(link) : SdrWalker.walk(link, info);
		repository = sdrCache ? walked : null;
		return walked;
	}

	/**
	 * Tells whether the BMC, which refused Get SDR Repository Info, has no SDR repository but holds the SDRs of its
	 * sensors itself, to be read with Get Device SDR: the rule of ipmitool, from Get Device ID (IPMI 2.0 table 20-2).
	 */
	private boolean holdsDeviceSdrsOnly() {
		DeviceId id;
		try {
			id = new DeviceId(link.send(new GetDeviceId()));
		} catch (IpmiCompletionCodeException e) {
			return false;
		}
		Set<DeviceFunction> functions = id.functions();
		return id.providesDeviceSdrs()
				&& functions.contains(DeviceFunction.SENSOR_DEVICE)
				&& !functions.contains(DeviceFunction.SDR_REPOSITORY_DEVICE);
	}

	private BmcCapabilities readCapabilities() throws Exception {
		IpmiConnector ipmiConnector = connector();
		ConnectionHandle handle = ipmiConnector.createConnection(resolve(), port);
		try {
			List<CipherSuite> suites = ipmiConnector.getAvailableCipherSuites(handle);
			GetChannelAuthenticationCapabilitiesResponseData data = ipmiConnector
					.getChannelAuthenticationCapabilities(handle, CipherSuite.getEmpty(), privilege);
			return new BmcCapabilities(data, suites);
		} catch (Exception e) {
			throw translateHandshake(e);
		} finally {
			ipmiConnector.closeConnection(handle);
		}
	}

	/**
	 * Runs a task on the worker thread, under the given deadline. The task gets the session when it needs one, and
	 * runs again on a new session when it is replayable and the session was lost while it ran.
	 */
	private <T> T run(String operation, Duration deadline, boolean needsSession, boolean replayable, Callable<T> task) {
		Duration limit = deadline == null ? timeout : deadline;
		Future<T> future;
		synchronized (submission) {
			if (closed) {
				throw new IllegalStateException("The client of " + host + " is closed");
			}
			try {
				future = worker.submit(() -> perform(needsSession, replayable, task));
			} catch (RejectedExecutionException e) {
				throw new IllegalStateException("The client of " + host + " is closed", e);
			}
		}
		try {
			return future.get(limit.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			future.cancel(true);
			throw new IpmiTimeoutException(
					"The " + operation + " operation on " + host + " did not complete within " + limit);
		} catch (InterruptedException e) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new IpmiClientException("Interrupted while waiting for the " + operation + " operation on " + host, e);
		} catch (ExecutionException e) {
			throw unwrap(e.getCause());
		}
	}

	private static RuntimeException unwrap(Throwable cause) {
		if (cause instanceof RuntimeException) {
			return (RuntimeException) cause;
		}
		if (cause instanceof Error) {
			throw (Error) cause;
		}
		return new IpmiClientException(cause.getMessage(), cause);
	}

	private <T> T perform(boolean needsSession, boolean replayable, Callable<T> task) throws Exception {
		if (!needsSession) {
			return task.call();
		}
		try {
			session();
			return task.call();
		} catch (Exception e) {
			if (Thread.currentThread().isInterrupted() || e instanceof InterruptedException) {
				// The caller gave up at its deadline: the state of the session is unknown
				dropSession();
				throw e;
			}
			if (replayable && link != null && link.lost) {
				dropSession();
				session();
				return task.call();
			}
			throw e;
		}
	}

	private void session() throws Exception {
		if (link != null && link.lost) {
			dropSession();
		}
		if (link == null) {
			if (user == null) {
				throw new IllegalStateException(
						"The operation needs a session: give credentials(user, password) to the builder of the client");
			}
			link = Link.open(this, connector(), resolve());
		}
	}

	private void dropSession() {
		if (link == null) {
			return;
		}
		ConnectionHandle dropped = link.handle;
		connector.closeConnection(dropped);
		link = null;
		// The consoles of the dropped connection can receive nothing any more: their reads end. A console that the
		// BMC serves on another port has a session of its own, which stays
		for (SolConsole console : consoles) {
			if (console.usesConnection(dropped)) {
				console.invalidate();
				consoles.remove(console);
			}
		}
	}

	private IpmiConnector connector() throws IOException {
		if (connector == null) {
			// Port 0: any free UDP port
			connector = new IpmiConnector(0, keepAliveMs);
			connector.setTimeout(messageTimeoutMs);
			connector.setRetries(retries);
		}
		return connector;
	}

	private InetAddress resolve() {
		try {
			return InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			throw new IpmiConnectionException("Unknown host " + host, e);
		}
	}

	private void logout() {
		try {
			if (link != null && !link.lost) {
				// One try: a BMC that does not answer the logout must not hold the caller
				connector.setRetries(0);
				connector.closeSession(link.handle);
			}
		} catch (Exception e) {
			// Best effort: the BMC expires a session it was not told to close
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		} finally {
			releaseConnector();
		}
	}

	/**
	 * Translates a failure of the session setup: the BMC refused the login, did not answer, or answered something
	 * else than what the handshake expects.
	 */
	private IpmiClientException translateHandshake(Exception e) {
		if (e instanceof IpmiClientException) {
			return (IpmiClientException) e;
		}
		if (e instanceof AuthenticationException) {
			return new IpmiAuthenticationException(
					"The BMC " + host + " refused the login of " + user + ": wrong password or BMC key (" + e.getMessage() + ")",
					e);
		}
		if (e instanceof SessionException && e.getCause() instanceof IPMIException) {
			// openSession() logged in, then closed the session the BMC would not raise to the requested privilege
			IPMIException refusal = (IPMIException) e.getCause();
			PrivilegeLevel level = privilege == PrivilegeLevel.MaximumAvailable ? PrivilegeLevel.User : privilege;
			int code = refusal.getRawCode();
			if (code == SetSessionPrivilegeLevel.LEVEL_NOT_AVAILABLE
					|| code == SetSessionPrivilegeLevel.LEVEL_ABOVE_LIMIT
					|| isAuthenticationCode(refusal.getCompletionCode())) {
				return new IpmiAuthenticationException(
						"The BMC " + host + " refused the " + level + " privilege to " + user + ": "
								+ SetSessionPrivilegeLevel.describeRefusal(refusal),
						refusal.getCompletionCode(),
						e);
			}
			// A busy BMC, or another failure than a refusal of the level
			return new IpmiClientException(
					"The BMC " + host + " could not set the session to the " + level + " privilege: "
							+ SetSessionPrivilegeLevel.describeRefusal(refusal),
					e);
		}
		if (e instanceof IPMIException) {
			CompletionCode code = ((IPMIException) e).getCompletionCode();
			if (isAuthenticationCode(code)) {
				return new IpmiAuthenticationException(
						"The BMC " + host + " refused the login of " + user + ": " + code.getMessage(),
						code,
						e);
			}
			return new IpmiClientException("The BMC " + host + " refused the session: " + code.getMessage(), e);
		}
		if (e instanceof Ipmi20UnsupportedException) {
			// The BMC answered: not a network failure
			return new IpmiClientException(
					"The BMC " + host + " does not support IPMI 2.0 / RMCP+: its reply to Get Channel Authentication "
							+ "Capabilities reports no IPMI v2.0 connections",
					e);
		}
		if (e instanceof ConnectionException) {
			return new IpmiConnectionException(
					"No reply from " + host + ":" + port + " to the session setup after " + (retries + 1)
							+ " tries: wrong host, UDP port " + port + " filtered, or IPMI over LAN disabled (" + e.getMessage()
							+ ")",
					e);
		}
		if (e instanceof IOException) {
			return new IpmiConnectionException("Cannot connect to " + host + ":" + port + ": " + e.getMessage(), e);
		}
		if (e instanceof InterruptedException) {
			Thread.currentThread().interrupt();
			return new IpmiClientException("Interrupted while opening the session with " + host, e);
		}
		return new IpmiClientException("Cannot open the session with " + host + ": " + e.getMessage(), e);
	}

	private static boolean isAuthenticationCode(CompletionCode code) {
		switch (code) {
		case UnauthorizedName:
		case UnauthorizedRole:
		case InvalidRole:
		case InsufficientResourcesForRole:
		case UnauthorizedGuid:
		case InvalidIntegrityCheckValue:
		case InvalidNameLength:
		case InsufficentPrivilege:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Encodes the password in UTF-8 without going through an immutable {@link String}.
	 */
	static byte[] encode(char[] password) {
		if (password == null) {
			return null;
		}
		ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
		byte[] bytes = Arrays.copyOf(buffer.array(), buffer.limit());
		Arrays.fill(buffer.array(), (byte) 0);
		return bytes;
	}

	static Duration checkTimeout(Duration duration, String name) {
		Objects.requireNonNull(duration, name);
		// The timeouts are used in milliseconds
		if (duration.isNegative() || duration.toMillis() == 0) {
			throw new IllegalArgumentException(name + " must be at least one millisecond: " + duration);
		}
		if (duration.toMillis() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(name + " is too long: " + duration);
		}
		return duration;
	}

	/**
	 * Picks the first suite of the preference list that the BMC offers.
	 */
	static CipherSuite chooseCipherSuite(List<CipherSuite> offered, int[] preference, String host) {
		if (offered != null) {
			for (int id : preference) {
				for (CipherSuite suite : offered) {
					if (suite.getId() == id) {
						return suite;
					}
				}
			}
		}
		StringBuilder ids = new StringBuilder();
		if (offered != null) {
			for (CipherSuite suite : offered) {
				ids.append(ids.length() == 0 ? "" : ", ").append(suite.getId());
			}
		}
		throw new IpmiClientException(
				"The BMC " + host + " offers none of the cipher suites " + Arrays.toString(preference) + " (offered: "
						+ (ids.length() == 0 ? "none" : ids) + ")");
	}

	/**
	 * The session of the client: its connector, its connection and the session opened on it. A send that gets no
	 * reply marks it lost.
	 */
	private static final class Link implements Exchange {

		private final IpmiConnector connector;
		private final ConnectionHandle handle;
		private final Session session;
		private final String host;
		private final int tries;
		/** Raises the session on a D4h: the builder asked for {@link PrivilegeLevel#MaximumAvailable}. */
		private final boolean onDemand;
		/** The level the session runs at when raised on demand: User right after the handshake. */
		private PrivilegeLevel level = PrivilegeLevel.User;
		/** The highest level the BMC lets the session be raised to: Administrator until it refuses one. */
		private PrivilegeLevel ceiling = PrivilegeLevel.Administrator;
		private boolean lost;

		private Link(IpmiConnector connector, ConnectionHandle handle, Session session, String host, int tries) {
			this.connector = connector;
			this.handle = handle;
			this.session = session;
			this.host = host;
			this.tries = tries;
			this.onDemand = handle.getPrivilegeLevel() == PrivilegeLevel.MaximumAvailable;
		}

		/**
		 * Opens the session: cipher suite discovery unless the builder fixed the suite, Get Channel Authentication
		 * Capabilities, then {@link IpmiConnector#openSession(ConnectionHandle, String, byte[], byte[])}: Open Session,
		 * RAKP 1 to 4 and Set Session Privilege Level, to the level the builder asked for when that is more than User
		 * (IPMI 2.0 section 22.18: every session starts at User), to User for {@link PrivilegeLevel#MaximumAvailable}
		 * (some BMCs start the session at the requested maximum instead).
		 */
		static Link open(IpmiClient client, IpmiConnector connector, InetAddress address) {
			ConnectionHandle handle = null;
			try {
				if (client.discoverCipherSuite) {
					handle = connector.createConnection(address, client.port);
					CipherSuite suite = chooseCipherSuite(
							connector.getAvailableCipherSuites(handle),
							client.cipherSuites,
							client.host);
					connector.getChannelAuthenticationCapabilities(handle, suite, client.privilege);
				} else {
					handle = connector
							.createConnection(address, client.port, CipherSuite.forId(client.cipherSuites[0]), client.privilege);
				}
				Session session = connector.openSession(handle, client.user, encode(client.password), client.bmcKey);
				return new Link(connector, handle, session, client.host, client.retries + 1);
			} catch (Exception e) {
				if (handle != null) {
					connector.closeConnection(handle);
				}
				throw client.translateHandshake(e);
			}
		}

		private boolean isConnectionOpen() {
			try {
				connector.getTimeout(handle);
				return true;
			} catch (IllegalStateException e) {
				return false;
			}
		}

		@Override
		public <R extends ResponseData> R send(IpmiCommandCoder<R> command) {
			return exchange(command, false);
		}

		@Override
		public boolean isLost() {
			return lost;
		}

		/**
		 * Sends a command once, never again when its reply is lost or is a C3h: a control command the BMC may have
		 * carried out (a reset) must not reach it twice. A lost reply says nothing about the session, which is kept.
		 */
		<R extends ResponseData> R sendOnce(IpmiCommandCoder<R> command) {
			return exchange(command, true);
		}

		/**
		 * Exchanges the command, and again at a higher privilege when the BMC refuses it for insufficient privilege
		 * (D4h) and the session is raised on demand: the BMC did not carry a refused command out, so even a command sent
		 * once is safely sent again. A raw request returns its D4h instead of throwing it, and is raised the same way.
		 */
		private <R extends ResponseData> R exchange(IpmiCommandCoder<R> command, boolean once) {
			while (true) {
				try {
					R response = attempt(command, once);
					if (response instanceof RawResponse
							&& ((RawResponse) response).completionCode() == CompletionCode.InsufficentPrivilege
							&& raise()) {
						continue;
					}
					if (onDemand) {
						learn(command, response);
					}
					return response;
				} catch (IpmiCompletionCodeException e) {
					if (e.completionCode() != CompletionCode.InsufficentPrivilege || !raise()) {
						throw e;
					}
				}
			}
		}

		/**
		 * Raises a session raised on demand to the next level, Operator then Administrator, and tells whether it was:
		 * not when the session runs at a fixed level, at Administrator, or at the ceiling the BMC set. A refusal of the
		 * level is the ceiling: 80h or 81h when the account or the channel stops there (IPMI 2.0 table 22-24), and D4h,
		 * as a session the BMC revoked answers it to everything, Set Session Privilege Level included, and must not be
		 * asked again at every command. Any other error (a busy BMC) is the failure of the raise, thrown as such and
		 * tried again at the next D4h.
		 *
		 * @throws IpmiCompletionCodeException when the raise fails with another code than a refusal of the level
		 */
		private boolean raise() {
			PrivilegeLevel next = level == PrivilegeLevel.User ?
					PrivilegeLevel.Operator :
					level == PrivilegeLevel.Operator ? PrivilegeLevel.Administrator : null;
			if (!onDemand || next == null || next.compareTo(ceiling) > 0) {
				return false;
			}
			try {
				attempt(new SetSessionPrivilegeLevel(next), false);
			} catch (IpmiCompletionCodeException e) {
				int code = e.rawCompletionCode();
				if (code != SetSessionPrivilegeLevel.LEVEL_NOT_AVAILABLE
						&& code != SetSessionPrivilegeLevel.LEVEL_ABOVE_LIMIT
						&& e.completionCode() != CompletionCode.InsufficentPrivilege) {
					throw e;
				}
				ceiling = level;
				return false;
			}
			level = next;
			return true;
		}

		/**
		 * Keeps the level of a session raised on demand in step with a Set Session Privilege Level the caller sent
		 * itself, typed or raw: the BMC runs the session at the level it accepted, whatever the level this link reached.
		 */
		private void learn(IpmiCommandCoder<?> command, ResponseData response) {
			PrivilegeLevel set = null;
			if (command instanceof SetSessionPrivilegeLevel) {
				set = ((SetSessionPrivilegeLevel) command).getPrivilegeLevel();
			} else
				if (response instanceof RawResponse
						&& command.getNetworkFunctionCode() == NetworkFunction.ApplicationRequest.getCode()
						&& command.getCommandCode() == CommandCodes.SET_SESSION_PRIVILEGE_LEVEL
						&& ((RawResponse) response).completionCode() == CompletionCode.Ok
						&& ((RawResponse) response).data().length > 0) {
							// The reply gives the new level of the session (IPMI 2.0 table 22-24)
							switch (((RawResponse) response).data()[0] & 0x0f) {
							case 2:
								set = PrivilegeLevel.User;
								break;
							case 3:
								set = PrivilegeLevel.Operator;
								break;
							case 4:
								set = PrivilegeLevel.Administrator;
								break;
							default:
								break;
							}
						}
			if (set == PrivilegeLevel.User || set == PrivilegeLevel.Operator || set == PrivilegeLevel.Administrator) {
				note(set);
			}
		}

		/**
		 * Records the level the session runs at, set by the caller or by the SOL console: a level the BMC accepted is
		 * within the ceiling.
		 */
		void note(PrivilegeLevel set) {
			level = set;
			if (set.compareTo(ceiling) > 0) {
				ceiling = set;
			}
		}

		private <R extends ResponseData> R attempt(IpmiCommandCoder<R> command, boolean once) {
			String name = command.getClass().getSimpleName();
			try {
				return once ? connector.sendMessage(handle, command, 0) : connector.sendMessage(handle, command);
			} catch (IPMIException e) {
				throw new IpmiCompletionCodeException(command, e);
			} catch (ConnectionException e) {
				if (command instanceof SendMessage && ((SendMessage<?>) command).isAcknowledged()) {
					// The BMC forwarded the request, and the controller did not answer: it may be absent, and the session
					// is not in question. A BMC that did not even acknowledge the request is lost like for any command
					throw new IpmiTimeoutException(
							"No reply from the controller behind " + host + " to " + name + " after " + tries
									+ " tries: the BMC forwarded the request, the controller may be absent",
							e);
				}
				if (once) {
					throw new IpmiTimeoutException(
							"No reply from " + host + " to " + name + ", which is sent once and not again: whether the BMC"
									+ " carried it out is unknown",
							e);
				}
				lost = true;
				throw new IpmiTimeoutException(
						"No reply from " + host + " to " + name + " after " + tries + " tries: the session is lost",
						e);
			} catch (IllegalStateException e) {
				// The connection was closed, or a decoder rejected a malformed reply: only the former loses the session
				if (!isConnectionOpen()) {
					lost = true;
					throw new IpmiConnectionException("The connection to " + host + " is closed", e);
				}
				throw new IpmiClientException(name + " failed on " + host + ": " + e.getMessage(), e);
			} catch (InterruptedException e) {
				lost = true;
				Thread.currentThread().interrupt();
				throw new IpmiClientException("Interrupted while waiting for the reply of " + host + " to " + name, e);
			} catch (IpmiClientException e) {
				throw e;
			} catch (Exception e) {
				throw new IpmiClientException(name + " failed on " + host + ": " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Builds an {@link IpmiClient}: the BMC, the credentials and the session settings. {@link #build()} validates
	 * them and touches no network.
	 */
	public static final class Builder {

		private final String host;
		private int port = Constants.IPMI_PORT;
		private String user;
		private char[] password;
		private byte[] bmcKey;
		private PrivilegeLevel privilege = PrivilegeLevel.User;
		private int[] cipherSuites = DEFAULT_CIPHER_SUITES.clone();
		private boolean discoverCipherSuite = true;
		private Duration timeout = DEFAULT_TIMEOUT;
		private Duration messageTimeout = DEFAULT_MESSAGE_TIMEOUT;
		private int retries = DEFAULT_RETRIES;
		private Duration keepAlive = DEFAULT_KEEP_ALIVE;
		private boolean sdrCache = true;

		private Builder(String host) {
			if (host == null || host.trim().isEmpty()) {
				throw new IllegalArgumentException("The host of the BMC is required");
			}
			this.host = host.trim();
		}

		/**
		 * Sets the UDP port of the BMC; 623 by default.
		 *
		 * @param udpPort the port, 1 to 65535
		 * @return this builder
		 */
		public Builder port(int udpPort) {
			if (udpPort < 1 || udpPort > MAX_PORT) {
				throw new IllegalArgumentException("Invalid port: " + udpPort);
			}
			this.port = udpPort;
			return this;
		}

		/**
		 * Sets the user name and password of the session. The password is not copied: the caller can wipe it once
		 * the client is closed. Required by every operation but {@link IpmiClient#capabilities()}.
		 *
		 * @param userName the user name, at most 16 bytes in UTF-8
		 * @param userPassword the password, at most 20 bytes in UTF-8
		 * @return this builder
		 */
		public Builder credentials(String userName, char[] userPassword) {
			this.user = Objects.requireNonNull(userName, "userName");
			this.password = Objects.requireNonNull(userPassword, "userPassword");
			return this;
		}

		/**
		 * Sets the BMC key (Kg) of the two-key login, as raw bytes, for a BMC whose key is set.
		 *
		 * @param key the key, at most 20 bytes, or null for the usual single-key login
		 * @return this builder
		 */
		public Builder bmcKey(byte[] key) {
			this.bmcKey = key;
			return this;
		}

		/**
		 * Sets the maximum privilege of the session, requested at Open Session and RAKP 1 and set with Set Session
		 * Privilege Level right after the handshake (every session starts at User, IPMI 2.0 section 22.18). User by
		 * default, enough for every read operation; Operator for the power control; Administrator for Serial over
		 * LAN on most BMCs.
		 * <p>
		 * {@code MaximumAvailable} requests the highest level the account is allowed, then runs the session at User and
		 * raises it only when the BMC refuses a command for insufficient privilege ({@code D4h}): to Operator, then to
		 * Administrator, and the refused command, which the BMC did not carry out, is sent again. A level the BMC refuses
		 * ({@code 80h}, {@code 81h}) is the ceiling of the session, remembered for its lifetime, and the {@code D4h}
		 * reaches the caller as it does at a fixed level. The sensors of other controllers than the BMC, which some BMCs
		 * serve at the Operator or Administrator level only, are then read with any account that has the level, without
		 * running the whole session at it.
		 *
		 * @param level the privilege level: User, Operator, Administrator or MaximumAvailable ({@code Callback}, at
		 *        which no operation works, is refused)
		 * @return this builder
		 * @throws IllegalArgumentException for {@code Callback}
		 */
		public Builder privilege(PrivilegeLevel level) {
			Objects.requireNonNull(level, "level");
			if (level == PrivilegeLevel.Callback) {
				// No command of the client works at that level
				throw new IllegalArgumentException(
						"Choose the privilege level of the session: User, Operator, Administrator or MaximumAvailable");
			}
			this.privilege = level;
			return this;
		}

		/**
		 * Sets the cipher suites the client may use, by order of preference: the first one the BMC offers is used.
		 * Default: 17, 3, 8, 16, 2, 7, 12, 11. The suites without integrity (0, 1, 6, 15) are refused.
		 *
		 * @param ids the suite IDs (IPMI 2.0 table 22-20)
		 * @return this builder
		 */
		public Builder cipherSuites(int... ids) {
			this.cipherSuites = checkSuites(ids);
			this.discoverCipherSuite = true;
			return this;
		}

		/**
		 * Uses exactly the given cipher suite, without asking the BMC which ones it offers: saves the Get Channel
		 * Authentication Capabilities and Get Channel Cipher Suites round trips with a BMC known to support it. An
		 * IPMI 1.5-only BMC then fails with {@link IpmiConnectionException} (no reply to Open Session) instead of
		 * {@link IpmiClientException}.
		 *
		 * @param id the suite ID (IPMI 2.0 table 22-20), one with integrity
		 * @return this builder
		 */
		public Builder cipherSuite(int id) {
			this.cipherSuites = checkSuites(new int[] { id });
			this.discoverCipherSuite = false;
			return this;
		}

		private static int[] checkSuites(int[] ids) {
			Objects.requireNonNull(ids, "ids");
			if (ids.length == 0) {
				throw new IllegalArgumentException("At least one cipher suite is required");
			}
			for (int id : ids) {
				if (id < 0 || id > LAST_STANDARD_SUITE) {
					throw new IllegalArgumentException("Cipher suite " + id + " is not a standard suite (0 to 19)");
				}
				if (SUITES_WITHOUT_INTEGRITY.contains(id)) {
					throw new IllegalArgumentException(
							"Cipher suite " + id + " has no integrity algorithm: its replies could be forged");
				}
			}
			return ids.clone();
		}

		/**
		 * Sets the overall deadline of each operation, session setup included; 2 minutes by default. Each operation
		 * builder can override it.
		 *
		 * @param deadline the deadline
		 * @return this builder
		 */
		public Builder timeout(Duration deadline) {
			this.timeout = checkTimeout(deadline, "timeout");
			return this;
		}

		/**
		 * Sets how long a message waits for its reply before it is sent again; 5 seconds by default.
		 *
		 * @param duration the timeout of a message
		 * @return this builder
		 */
		public Builder messageTimeout(Duration duration) {
			this.messageTimeout = checkTimeout(duration, "messageTimeout");
			return this;
		}

		/**
		 * Sets how many times a message without reply is sent again; 3 by default.
		 *
		 * @param count the number of resends, 0 to send every message once
		 * @return this builder
		 */
		public Builder retries(int count) {
			if (count < 0) {
				throw new IllegalArgumentException("retries must not be negative: " + count);
			}
			this.retries = count;
			return this;
		}

		/**
		 * Sets the period of the keep-alive messages that keep the session open between operations; 30 seconds by
		 * default. {@link Duration#ZERO} disables them: the BMC then expires an idle session after its own timeout
		 * (60 seconds by default), and the next operation opens a new one.
		 *
		 * @param period the period, or {@link Duration#ZERO}
		 * @return this builder
		 */
		public Builder keepAlive(Duration period) {
			Objects.requireNonNull(period, "period");
			// The period is used in milliseconds, and 0 disables the keep-alive
			if (period.isNegative() || (!period.isZero() && period.toMillis() == 0)) {
				throw new IllegalArgumentException("keepAlive must be Duration.ZERO or at least one millisecond: " + period);
			}
			this.keepAlive = period;
			return this;
		}

		/**
		 * Tells whether the client caches the SDR repository it reads, so that {@link IpmiClient#sensors()} and
		 * {@link IpmiClient#frus()} walk it once; enabled by default. Before using the cache, each operation checks
		 * with Get SDR Repository Info that the record count and the timestamps of the most recent addition and
		 * erase did not change. Disable it for a BMC whose timestamps cannot be trusted.
		 *
		 * @param enabled whether to cache the repository
		 * @return this builder
		 */
		public Builder sdrCache(boolean enabled) {
			this.sdrCache = enabled;
			return this;
		}

		/**
		 * Validates the settings and creates the client. No session is opened: the first operation that needs one
		 * opens it.
		 *
		 * @return the client, to close when done
		 * @throws IllegalArgumentException when the user name is longer than 16 bytes, or the password or the BMC key
		 *         longer than 20 bytes
		 */
		public IpmiClient build() {
			if (user != null) {
				Rakp1.checkCredentials(user, encode(password), bmcKey);
			} else if (bmcKey != null) {
				Rakp1.checkCredentials("", null, bmcKey);
			}
			return new IpmiClient(this);
		}
	}
}
