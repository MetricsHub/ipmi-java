package org.metricshub.ipmi.cli;

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

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.FilterOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import org.metricshub.ipmi.client.BmcCapabilities;
import org.metricshub.ipmi.client.ChassisStatus;
import org.metricshub.ipmi.client.DeviceFunction;
import org.metricshub.ipmi.client.DeviceId;
import org.metricshub.ipmi.client.Fru;
import org.metricshub.ipmi.client.FruList;
import org.metricshub.ipmi.client.FrusRequest;
import org.metricshub.ipmi.client.IpmiAuthenticationException;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientException;
import org.metricshub.ipmi.client.IpmiCompletionCodeException;
import org.metricshub.ipmi.client.IpmiConnectionException;
import org.metricshub.ipmi.client.IpmiTimeoutException;
import org.metricshub.ipmi.client.PowerEvent;
import org.metricshub.ipmi.client.RawResponse;
import org.metricshub.ipmi.client.SelEntry;
import org.metricshub.ipmi.client.SelEntryList;
import org.metricshub.ipmi.client.SelRequest;
import org.metricshub.ipmi.client.Sensor;
import org.metricshub.ipmi.client.SensorList;
import org.metricshub.ipmi.client.SensorsRequest;
import org.metricshub.ipmi.client.SkippedRecord;
import org.metricshub.ipmi.client.SolConsole;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AbstractSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AddressType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorUnit;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.PiromInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.SpdInfo;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.SecurityConstants;

/**
 * The command-line client: {@code java -jar ipmi-java-<version>-standalone.jar [options] <subcommand> [...]}. Every
 * subcommand is a thin layer over one operation of {@link IpmiClient}; the text output is for humans, {@code --json}
 * prints JSON Lines, diagnostics go to stderr only, and the exit codes are those of the MetricsHub command-line
 * clients: the sysexits values, 1 for a partial result and 124 for a timeout. See the
 * <a href="https://metricshub.org/ipmi-java/cli.html">manual</a>.
 */
public final class IpmiCli {

	static final int EXIT_PARTIAL = 1;
	static final int EXIT_USAGE = 64;
	static final int EXIT_NOT_FOUND = 66;
	static final int EXIT_CONNECTION = 69;
	static final int EXIT_PROTOCOL = 70;
	static final int EXIT_IO = 74;
	static final int EXIT_AUTHENTICATION = 77;
	static final int EXIT_TIMEOUT = 124;

	private static final String NAME = "ipmicli";
	private static final String PROGRAM_NAME_VARIABLE = "IPMICLI_PROGRAM_NAME";
	private static final int LABEL_WIDTH = 28;
	private static final int FRU_LABEL_WIDTH = 27;
	private static final String FRU_INDENT = "  ";
	private static final long NANOS_PER_MILLI = 1_000_000L;
	private static final long KEY_READER_START_MS = 500L;
	private static final int DECIMALS = 3;
	private static final int RAW_BYTES_PER_LINE = 16;
	/** Event/reading type code of the threshold sensors (IPMI 2.0 table 42-1). */
	private static final int THRESHOLD_EVENT_READING_TYPE = 1;
	/** The 8-bit slave address of the BMC. */
	private static final int BMC_ADDRESS = 0x20;
	private static final int OWNER_ID_MASK = 0x7f;
	private static final int LUN_MASK = 0x03;
	private static final int CHANNEL_MASK = 0x0f;

	private IpmiCli() {}

	/**
	 * Runs the CLI on the standard streams and exits with its exit code.
	 *
	 * @param args the command line
	 */
	public static void main(String[] args) {
		System
				.exit(run(args, System.out, System.err, IpmiCli::readConsolePassword, System.in, isTerminal(System.console())));
	}

	/**
	 * Tells whether a console is an interactive terminal. Up to Java 21, {@code System.console()} is null when stdin or
	 * stdout is redirected; Java 22 to 24 return a console even then (JDK-8308591) and added {@code isTerminal()} to
	 * tell, which is called by reflection as the library compiles for Java 8.
	 */
	static boolean isTerminal(Console console) {
		if (console == null) {
			return false;
		}
		try {
			return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
		} catch (ReflectiveOperationException e) {
			// Java 8 to 21: a console exists only on a terminal
			return true;
		}
	}

	/**
	 * Runs the CLI.
	 *
	 * @param args the command line
	 * @param out the standard output
	 * @param err the standard error
	 * @param passwordReader asked for the password when no password option is given
	 * @param stdin the standard input (the SOL console)
	 * @param terminal whether the standard streams are an interactive terminal
	 * @return the exit code
	 */
	static int run(
			String[] args,
			PrintStream out,
			PrintStream err,
			PasswordReader passwordReader,
			InputStream stdin,
			boolean terminal) {
		try (CliArguments arguments = CliArguments.parse(args)) {
			if (arguments.subcommand() == CliArguments.Subcommand.HELP) {
				out.print(usage());
				return out.checkError() ? outputFailed(err) : 0;
			}
			if (arguments.subcommand() == CliArguments.Subcommand.VERSION) {
				out.println("ipmi-java " + version());
				return out.checkError() ? outputFailed(err) : 0;
			}
			if (terminal && arguments.subcommand() == CliArguments.Subcommand.SOL && arguments.escape() < 0) {
				// In raw mode every keystroke is for the BMC, Ctrl+C and Ctrl+D included: without an escape sequence, only
				// a kill from another terminal would close the console
				throw new CliUsageException(
						"--no-escape needs a redirected input: on a terminal, " + CliArguments.DEFAULT_ESCAPE
								+ ". is the only way out (use --escape to pick another character)");
			}
			if (arguments.subcommand() != CliArguments.Subcommand.PROBE && arguments.password() == null) {
				char[] password;
				try {
					password = passwordReader.readPassword();
				} catch (IOError e) {
					diagnostic(err, "cannot read the password from the console: " + e.getMessage());
					return EXIT_IO;
				}
				if (password == null) {
					throw new CliUsageException("no password was entered");
				}
				arguments.replacePassword(password);
			}
			IpmiClient client;
			try {
				// The builder and build() refuse a setting (a suite without integrity, credentials too long for a BMC)
				client = builder(arguments, err).build();
			} catch (IllegalArgumentException e) {
				throw new CliUsageException(e.getMessage(), e);
			}
			try (IpmiClient open = client) {
				int code = execute(arguments, open, out, err, stdin, terminal);
				return out.checkError() ? outputFailed(err) : code;
			} catch (IpmiCompletionCodeException e) {
				return completionCodeFailure(e, err, arguments.privilege());
			}
		} catch (CliUsageException e) {
			diagnostic(err, e.getMessage());
			err.println("Try '" + programName() + " --help' for usage.");
			return EXIT_USAGE;
		} catch (IpmiAuthenticationException e) {
			diagnostic(err, e.getMessage());
			return EXIT_AUTHENTICATION;
		} catch (IpmiConnectionException e) {
			diagnostic(err, e.getMessage());
			return EXIT_CONNECTION;
		} catch (IpmiTimeoutException e) {
			diagnostic(err, e.getMessage());
			return EXIT_TIMEOUT;
		} catch (IpmiClientException | IllegalStateException e) {
			diagnostic(err, e.getMessage());
			return EXIT_PROTOCOL;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			diagnostic(err, "interrupted");
			return EXIT_PROTOCOL;
		}
	}

	/**
	 * Reports an error completion code: exit code 77 for {@code D4h}, with the {@code -L} to use above the privilege of
	 * the session, or none at the Administrator level, where the BMC restricts the command, or at {@code -L auto}, where
	 * the account has not the level; 70 for the others.
	 */
	private static int completionCodeFailure(IpmiCompletionCodeException e, PrintStream err, PrivilegeLevel privilege) {
		String message = e.getMessage();
		if (e.completionCode() == CompletionCode.InsufficientPrivilege) {
			// The message of the library ends with a hint for Java callers: replace it with the CLI's
			int hint = message.indexOf(". The command requires");
			String reason;
			if (privilege == PrivilegeLevel.Administrator) {
				reason = ": the BMC refuses the command even at the Administrator level (it restricts or disables it)";
			} else if (privilege == PrivilegeLevel.MaximumAvailable) {
				reason = ": the BMC refuses the command at the highest level of the account (the account has not the level"
						+ " the command needs, or the BMC restricts or disables the command)";
			} else {
				reason = ": the session has not the privilege the command needs, use "
						+ (privilege == PrivilegeLevel.Operator ? "" : "-L operator or ") + "-L administrator";
			}
			diagnostic(err, (hint > 0 ? message.substring(0, hint) : message) + reason);
			return EXIT_AUTHENTICATION;
		}
		diagnostic(err, message);
		return EXIT_PROTOCOL;
	}

	private static IpmiClient.Builder builder(CliArguments arguments, PrintStream err) {
		IpmiClient.Builder builder = IpmiClient.builder(arguments.hostname())
				.port(arguments.port())
				.privilege(arguments.privilege())
				.timeout(Duration.ofMillis(arguments.timeoutMs()))
				.messageTimeout(Duration.ofMillis(arguments.messageTimeoutMs()))
				.retries(arguments.retries());
		if (arguments.subcommand() != CliArguments.Subcommand.PROBE) {
			builder.credentials(arguments.username(), arguments.password());
		}
		if (arguments.bmcKey() != null) {
			builder.bmcKey(arguments.bmcKey());
		}
		int[] suites = arguments.cipherSuites();
		if (suites != null && suites.length == 1) {
			builder.cipherSuite(suites[0]);
		} else if (suites != null) {
			builder.cipherSuites(suites);
		}
		if (arguments.verbose()) {
			diagnostic(
					err,
					"connecting to " + arguments.hostname() + ":" + arguments.port()
							+ (arguments.subcommand() == CliArguments.Subcommand.PROBE ?
									"" :
									" as '" + arguments.username() + "' with the " + arguments.privilege() + " privilege")
							+ ", cipher suite"
							+ (suites == null ?
									"s " + join(CliArguments.DEFAULT_CIPHER_SUITES) :
									suites.length == 1 ? " " + suites[0] + " without discovery" : "s " + join(suites))
							+ ", message timeout " + arguments.messageTimeoutMs() + " ms, " + arguments.retries() + " retries");
		}
		return builder;
	}

	private static int execute(
			CliArguments arguments,
			IpmiClient client,
			PrintStream out,
			PrintStream err,
			InputStream stdin,
			boolean terminal)
			throws InterruptedException {
		switch (arguments.subcommand()) {
		case PROBE:
			probe(arguments, timed(arguments, err, "probe", client::capabilities), out);
			return 0;
		case INFO:
			info(arguments, timed(arguments, err, "device id", client::deviceId), out);
			return 0;
		case CHASSIS:
			chassis(arguments, timed(arguments, err, "chassis status", client::chassisStatus), out);
			return 0;
		case POWER:
			if (arguments.powerCommand() == null) {
				out.println(timed(arguments, err, "chassis status", client::chassisStatus).powerOn() ? "on" : "off");
			} else {
				// Nothing to print: the exit code says whether the BMC accepted the command
				try {
					timed(arguments, err, "chassis control", () -> {
						client.chassisControl(arguments.powerCommand());
						return null;
					});
				} catch (IllegalStateException e) {
					if (arguments.powerCommand() != PowerCommand.PowerCycle) {
						throw e;
					}
					// The message of the client names the Java constant: say the CLI action instead
					throw new IllegalStateException(
							"the system is off: a power cycle would power it up on some BMCs, use 'power on'",
							e);
				}
			}
			return 0;
		case IDENTIFY:
			timed(arguments, err, "chassis identify", () -> {
				if (arguments.identifyForever()) {
					client.chassisIdentifyForever();
				} else {
					client.chassisIdentify(arguments.identifySeconds());
				}
				return null;
			});
			return 0;
		case SENSORS:
			return sensors(arguments, client, out, err);
		case FRU:
			return frus(arguments, client, out, err);
		case SEL:
			return sel(arguments, client, out, err);
		case SOL:
			return sol(arguments, client, out, err, stdin, terminal);
		case RAW:
			return raw(arguments, client, out, err);
		default:
			throw new IllegalStateException("Unexpected subcommand " + arguments.subcommand());
		}
	}

	private static <T> T timed(CliArguments arguments, PrintStream err, String step, Supplier<T> operation) {
		long start = System.nanoTime();
		boolean done = false;
		try {
			T result = operation.get();
			done = true;
			return result;
		} finally {
			// The time of a failed operation (a deadline, a refused command) is worth as much as that of a successful one
			if (arguments.verbose()) {
				diagnostic(
						err,
						step + (done ? ": " : " failed after ") + (System.nanoTime() - start) / NANOS_PER_MILLI + " ms");
			}
		}
	}

	private static void probe(CliArguments arguments, BmcCapabilities capabilities, PrintStream out) {
		int[] preference = arguments.cipherSuites() == null ? CliArguments.DEFAULT_CIPHER_SUITES : arguments.cipherSuites();
		// The choice the client makes with the same -C: a single ID is used as is, without discovery; several are a
		// preference among the suites the BMC offers (the builder refuses the suites the library does not implement)
		CipherSuite chosen = null;
		boolean offered = false;
		if (arguments.cipherSuites() != null && preference.length == 1) {
			chosen = CipherSuite.forId(preference[0]);
		}
		for (int id : preference) {
			for (CipherSuite suite : capabilities.cipherSuites()) {
				if (suite.getId() == id && (chosen == null || chosen.getId() == id)) {
					chosen = chosen == null ? suite : chosen;
					offered = true;
				}
			}
		}
		String problem = chosen != null && !offered ? "not offered by the BMC: the login would fail" : null;
		if (arguments.json()) {
			Map<String, Object> fields = new LinkedHashMap<String, Object>();
			fields.put("ipmi20", capabilities.ipmi20());
			fields.put("bmcKeyRequired", capabilities.bmcKeyRequired());
			fields.put("anonymousLogin", capabilities.anonymousLogin());
			fields.put("nullUserNames", capabilities.nullUserNames());
			fields.put("nonNullUserNames", capabilities.nonNullUserNames());
			fields.put("perMessageAuthentication", capabilities.perMessageAuthentication());
			fields.put("userLevelAuthentication", capabilities.userLevelAuthentication());
			List<Map<String, Object>> suites = new ArrayList<Map<String, Object>>();
			for (CipherSuite suite : capabilities.cipherSuites()) {
				Map<String, Object> entry = new LinkedHashMap<String, Object>();
				entry.put("id", (int) suite.getId());
				entry.put("authentication", authenticationName(suite.getAuthenticationAlgorithmCode()));
				entry.put("integrity", integrityName(suite.getIntegrityAlgorithmCode()));
				entry.put("confidentiality", confidentialityName(suite.getConfidentialityAlgorithmCode()));
				entry.put("supported", suite.isSupported());
				suites.add(entry);
			}
			fields.put("cipherSuites", suites);
			if (chosen != null) {
				fields.put("chosenCipherSuite", (int) chosen.getId());
				if (problem != null) {
					fields.put("chosenCipherSuiteProblem", problem);
				}
			}
			JsonLinesWriter.write(fields, out);
			return;
		}
		line(out, "", LABEL_WIDTH, "IPMI 2.0", capabilities.ipmi20() ? "supported" : "not supported");
		line(out, "", LABEL_WIDTH, "BMC key (Kg)", capabilities.bmcKeyRequired() ? "required" : "not required");
		line(out, "", LABEL_WIDTH, "Anonymous login", enabled(capabilities.anonymousLogin()));
		line(out, "", LABEL_WIDTH, "Null user names", enabled(capabilities.nullUserNames()));
		line(out, "", LABEL_WIDTH, "Non-null user names", enabled(capabilities.nonNullUserNames()));
		line(out, "", LABEL_WIDTH, "Per-message authentication", enabled(capabilities.perMessageAuthentication()));
		line(out, "", LABEL_WIDTH, "User-level authentication", enabled(capabilities.userLevelAuthentication()));
		out.println("Cipher suites");
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] { "  ID", "Authentication", "Integrity", "Confidentiality", "Library" });
		for (CipherSuite suite : capabilities.cipherSuites()) {
			rows.add(
					new String[]
					{
							"  " + suite.getId(),
							authenticationName(suite.getAuthenticationAlgorithmCode()),
							integrityName(suite.getIntegrityAlgorithmCode()),
							confidentialityName(suite.getConfidentialityAlgorithmCode()),
							suite.isSupported() ? "supported" : "not implemented" });
		}
		out.print(table(rows));
		line(
				out,
				"",
				LABEL_WIDTH,
				"Chosen cipher suite",
				chosen == null ?
						"none (preference " + join(preference) + ")" :
						chosen.getId() + (problem == null ? "" : " (" + problem + ")"));
	}

	private static void info(CliArguments arguments, DeviceId device, PrintStream out) {
		List<String> functions = new ArrayList<String>();
		for (DeviceFunction function : device.functions()) {
			functions.add(function.name());
		}
		byte[] auxiliary = device.auxiliaryFirmwareRevision();
		if (arguments.json()) {
			Map<String, Object> fields = new LinkedHashMap<String, Object>();
			fields.put("id", device.id());
			fields.put("revision", device.revision());
			fields.put("firmwareRevision", device.firmwareRevision());
			fields.put("ipmiVersion", device.ipmiVersion());
			fields.put("manufacturerId", device.manufacturerId());
			fields.put("productId", device.productId());
			fields.put("available", device.available());
			fields.put("providesDeviceSdrs", device.providesDeviceSdrs());
			fields.put("functions", functions);
			put(fields, "auxiliaryFirmwareRevision", auxiliary.length == 0 ? null : hex(auxiliary));
			JsonLinesWriter.write(fields, out);
			return;
		}
		line(out, "", LABEL_WIDTH, "Device ID", String.valueOf(device.id()));
		line(out, "", LABEL_WIDTH, "Device revision", String.valueOf(device.revision()));
		line(out, "", LABEL_WIDTH, "Firmware revision", device.firmwareRevision());
		line(out, "", LABEL_WIDTH, "IPMI version", device.ipmiVersion());
		line(out, "", LABEL_WIDTH, "Manufacturer ID", String.valueOf(device.manufacturerId()));
		line(out, "", LABEL_WIDTH, "Product ID", String.valueOf(device.productId()));
		line(out, "", LABEL_WIDTH, "Device available", yesNo(device.available()));
		line(out, "", LABEL_WIDTH, "Provides device SDRs", yesNo(device.providesDeviceSdrs()));
		line(out, "", LABEL_WIDTH, "Functions", functions.isEmpty() ? "none" : String.join(", ", functions));
		if (auxiliary.length > 0) {
			line(out, "", LABEL_WIDTH, "Aux firmware revision", hex(auxiliary));
		}
	}

	private static int raw(CliArguments arguments, IpmiClient client, PrintStream out, PrintStream err) {
		RawResponse response = timed(
				arguments,
				err,
				"raw",
				() -> client.raw(arguments.rawNetworkFunction(), arguments.rawCommand()).data(arguments.rawData()).execute());
		byte[] data = response.data();
		if (arguments.json()) {
			Map<String, Object> fields = new LinkedHashMap<String, Object>();
			fields.put("completionCode", response.rawCompletionCode());
			put(fields, "data", hex(data));
			JsonLinesWriter.write(fields, out);
		} else {
			for (int start = 0; start < data.length; start += RAW_BYTES_PER_LINE) {
				StringBuilder line = new StringBuilder();
				for (int i = start; i < Math.min(start + RAW_BYTES_PER_LINE, data.length); i++) {
					line.append(i == start ? "" : " ").append(String.format("%02x", data[i]));
				}
				out.println(line);
			}
		}
		if (response.rawCompletionCode() == 0) {
			return 0;
		}
		// An error completion code is part of the reply of raw(), not an exception: it exits like one
		return completionCodeFailure(
				new IpmiCompletionCodeException(
						String.format("raw %02Xh %02Xh", arguments.rawNetworkFunction(), arguments.rawCommand()),
						new IPMIException(response.completionCode(), response.rawCompletionCode())),
				err,
				arguments.privilege());
	}

	private static void chassis(CliArguments arguments, ChassisStatus status, PrintStream out) {
		List<String> events = new ArrayList<String>();
		for (PowerEvent event : status.lastPowerEvents()) {
			events.add(event.name());
		}
		if (arguments.json()) {
			Map<String, Object> fields = new LinkedHashMap<String, Object>();
			fields.put("powerOn", status.powerOn());
			fields.put("powerRestorePolicy", status.powerRestorePolicy().name());
			fields.put("lastPowerEvents", events);
			fields.put("powerFault", status.powerFault());
			fields.put("powerOverload", status.powerOverload());
			fields.put("interlock", status.interlock());
			fields.put("powerControlFault", status.powerControlFault());
			fields.put("coolingFault", status.coolingFault());
			fields.put("driveFault", status.driveFault());
			fields.put("intrusion", status.intrusion());
			fields.put("frontPanelLockout", status.frontPanelLockout());
			status.identifyState().ifPresent(state -> fields.put("identifyState", state.name()));
			JsonLinesWriter.write(fields, out);
			return;
		}
		line(out, "", LABEL_WIDTH, "Power", status.powerOn() ? "on" : "off");
		line(out, "", LABEL_WIDTH, "Power restore policy", status.powerRestorePolicy().name());
		line(out, "", LABEL_WIDTH, "Last power events", events.isEmpty() ? "none" : String.join(", ", events));
		line(out, "", LABEL_WIDTH, "Power fault", yesNo(status.powerFault()));
		line(out, "", LABEL_WIDTH, "Power overload", yesNo(status.powerOverload()));
		line(out, "", LABEL_WIDTH, "Interlock", yesNo(status.interlock()));
		line(out, "", LABEL_WIDTH, "Power control fault", yesNo(status.powerControlFault()));
		line(out, "", LABEL_WIDTH, "Cooling fault", yesNo(status.coolingFault()));
		line(out, "", LABEL_WIDTH, "Drive fault", yesNo(status.driveFault()));
		line(out, "", LABEL_WIDTH, "Intrusion", yesNo(status.intrusion()));
		line(out, "", LABEL_WIDTH, "Front panel lockout", yesNo(status.frontPanelLockout()));
		status.identifyState().ifPresent(state -> line(out, "", LABEL_WIDTH, "Identify", state.name()));
	}

	private static int sensors(CliArguments arguments, IpmiClient client, PrintStream out, PrintStream err) {
		SensorsRequest request = client.sensors();
		if (arguments.types() != null) {
			request.types(arguments.types().toArray(new SensorType[0]));
		}
		if (!arguments.names().isEmpty()) {
			request.names(arguments.names().toArray(new String[0]));
		}
		SensorList sensors = timed(arguments, err, "sensors", request::execute);
		skipped(sensors.skippedRecords(), err);
		if (arguments.json()) {
			for (Sensor sensor : sensors) {
				JsonLinesWriter.write(sensorFields(sensor), out);
			}
			return 0;
		}
		List<String[]> rows = new ArrayList<String[]>();
		boolean longFormat = arguments.longFormat();
		rows.add(
				longFormat ? new String[]
				{
						"Name",
						"Number",
						"Type",
						"Entity",
						"Reading",
						"Unit",
						"Status",
						"LNR",
						"LC",
						"LNC",
						"UNC",
						"UC",
						"UNR",
						"Problem" } : new String[]
						{ "Name", "Type", "Reading", "Unit", "Status" });
		for (Sensor sensor : sensors) {
			String reading = number(sensor.reading());
			String unit = unit(sensor);
			// A threshold sensor keeps its status (critical...) in the column, its states being the thresholds crossed
			String status = sensor.states().isEmpty() || isThreshold(sensor) ?
					sensor.status().name().toLowerCase(Locale.ROOT).replace('_', '-') :
					String.join(", ", sensor.states());
			String type = typeName(sensor.type(), sensor.typeCode());
			rows.add(
					longFormat ? new String[]
					{
							sensor.name(),
							String.valueOf(sensor.number()),
							type,
							sensor.entity() + " " + sensor.entityInstance(),
							reading,
							unit,
							status,
							number(sensor.lowerNonRecoverable()),
							number(sensor.lowerCritical()),
							number(sensor.lowerNonCritical()),
							number(sensor.upperNonCritical()),
							number(sensor.upperCritical()),
							number(sensor.upperNonRecoverable()),
							sensor.problem().orElse("") } : new String[]
							{ sensor.name(), type, reading, unit, status });
		}
		out.print(table(rows));
		return 0;
	}

	private static boolean isThreshold(Sensor sensor) {
		return sensor.record() instanceof AbstractSensorRecord
				&& ((AbstractSensorRecord) sensor.record()).getEventReadingType() == THRESHOLD_EVENT_READING_TYPE;
	}

	/**
	 * Names the unit of a sensor as {@code ipmitool} does: {@code percent} for a percentage of no unit, {@code % }
	 * followed by the unit for a percentage of it, nothing for no unit.
	 */
	private static String unit(Sensor sensor) {
		if (sensor.percentage()) {
			return sensor.unit() == SensorUnit.Unspecified ? "percent" : "% " + sensor.unit().name();
		}
		return sensor.unit() == SensorUnit.Unspecified ? "" : sensor.unit().name();
	}

	/**
	 * Names a sensor type, with its code for an OEM or reserved one ({@code Oem C1h}), which the constant does not
	 * keep.
	 */
	private static String typeName(SensorType type, int code) {
		return type == SensorType.Oem || type == SensorType.Reserved ? String.format("%s %02Xh", type, code) : type.name();
	}

	private static Map<String, Object> sensorFields(Sensor sensor) {
		Map<String, Object> fields = new LinkedHashMap<String, Object>();
		fields.put("name", sensor.name());
		fields.put("number", sensor.number());
		fields.put("type", sensor.type().name());
		fields.put("typeCode", sensor.typeCode());
		fields.put("entity", sensor.entity().name());
		fields.put("entityInstance", sensor.entityInstance());
		if (sensor.record() instanceof AbstractSensorRecord) {
			// The owner as the low byte of a SEL generator ID: the 7-bit address shifted left, bit 0 set for a software
			// ID (IPMI 2.0 table 32-1), so that a script joins sel --json on it as SelEntry.isFrom() does
			AbstractSensorRecord owner = (AbstractSensorRecord) sensor.record();
			fields.put(
					"ownerId",
					(owner.getSensorOwnerId() & OWNER_ID_MASK) << 1
							| (owner.getAddressType() == AddressType.SystemSoftwareId ? 1 : 0));
			fields.put("ownerLun", owner.getSensorOwnerLun() & LUN_MASK);
			fields.put("ownerChannel", owner.getChannelNumber() & CHANNEL_MASK);
		}
		if (sensor.unit() != SensorUnit.Unspecified) {
			fields.put("unit", sensor.unit().name());
		}
		if (sensor.percentage()) {
			fields.put("percentage", true);
		}
		round(sensor.reading()).ifPresent(value -> fields.put("reading", value));
		sensor.rawReading().ifPresent(value -> fields.put("rawReading", value));
		fields.put("status", sensor.status().name());
		fields.put("states", sensor.states());
		round(sensor.lowerNonRecoverable()).ifPresent(value -> fields.put("lowerNonRecoverable", value));
		round(sensor.lowerCritical()).ifPresent(value -> fields.put("lowerCritical", value));
		round(sensor.lowerNonCritical()).ifPresent(value -> fields.put("lowerNonCritical", value));
		round(sensor.upperNonCritical()).ifPresent(value -> fields.put("upperNonCritical", value));
		round(sensor.upperCritical()).ifPresent(value -> fields.put("upperCritical", value));
		round(sensor.upperNonRecoverable()).ifPresent(value -> fields.put("upperNonRecoverable", value));
		sensor.problem().ifPresent(problem -> fields.put("problem", problem));
		return fields;
	}

	private static int frus(CliArguments arguments, IpmiClient client, PrintStream out, PrintStream err) {
		FrusRequest request = client.frus();
		List<Integer> wanted = arguments.fruIds();
		if (!wanted.isEmpty()) {
			int[] ids = new int[wanted.size()];
			for (int i = 0; i < ids.length; i++) {
				ids[i] = wanted.get(i);
			}
			request.ids(ids);
		}
		FruList frus = timed(arguments, err, "FRUs", request::execute);
		skipped(frus.skippedRecords(), err);
		int code = 0;
		List<Integer> found = new ArrayList<Integer>();
		for (Fru fru : frus) {
			if (arguments.json()) {
				JsonLinesWriter.write(fruFields(fru), out);
			} else {
				if (!found.isEmpty()) {
					out.println();
				}
				fru(fru, out);
			}
			found.add(fru.id());
			for (String problem : fru.problems()) {
				diagnostic(err, fruLabel(fru) + ": " + problem);
				code = EXIT_PARTIAL;
			}
		}
		for (int id : wanted) {
			if (!found.contains(id)) {
				diagnostic(err, "no such FRU " + id);
				code = EXIT_NOT_FOUND;
			}
		}
		return code;
	}

	/** The 8-bit slave address of the controller that holds a FRU: 20h for the BMC. */
	private static int fruAddress(Fru fru) {
		return fru.locator().getDeviceAccessAddress() << 1;
	}

	/**
	 * Names a FRU by its ID, with the address, LUN and channel of its controller when it is not held by the BMC at
	 * LUN 0 ({@code FRU 5 at 2Ch}, {@code FRU 5 at 20h LUN 1}): since two FRUs on different controllers or LUNs may
	 * have the same ID, the ID alone does not tell them apart. The channel of a FRU held by the BMC itself is ignored,
	 * as the client ignores it (a Supermicro BMC locates its own FRU at 20h on channel 2).
	 */
	private static String fruLabel(Fru fru) {
		int address = fruAddress(fru);
		int lun = fru.locator().getAccessLun();
		int channel = address == BMC_ADDRESS ? 0 : fru.locator().getManagementChannelNumber();
		if (address == BMC_ADDRESS && lun == 0) {
			return "FRU " + fru.id();
		}
		return String.format(Locale.ROOT, "FRU %d at %02Xh", fru.id(), address) + (lun == 0 ? "" : " LUN " + lun)
				+ (channel == 0 ? "" : " channel " + channel);
	}

	private static void fru(Fru fru, PrintStream out) {
		out.println(fruLabel(fru) + ": " + printable(fru.name()) + " (" + fru.entity() + " " + fru.entityInstance() + ")");
		Optional<ChassisInfo> chassis = fru.chassis();
		if (chassis.isPresent()) {
			ChassisInfo area = chassis.get();
			field(out, "Chassis type", area.getChassisType() == null ? null : area.getChassisType().name());
			field(out, "Chassis part number", area.getChassisPartNumber());
			field(out, "Chassis serial number", area.getChassisSerialNumber());
			fields(out, "Chassis custom", area.getCustomChassisInfo());
		}
		Optional<BoardInfo> board = fru.board();
		if (board.isPresent()) {
			BoardInfo area = board.get();
			field(out, "Board manufacturer", area.getBoardManufacturer());
			field(out, "Board product", area.getBoardProductName());
			field(out, "Board serial number", area.getBoardSerialNumber());
			field(out, "Board part number", area.getBoardPartNumber());
			field(out, "Board mfg date", date(area.getMfgDate()));
			fields(out, "Board custom", area.getCustomBoardInfo());
		}
		Optional<ProductInfo> product = fru.product();
		if (product.isPresent()) {
			ProductInfo area = product.get();
			field(out, "Product manufacturer", area.getManufacturerName());
			field(out, "Product name", area.getProductName());
			field(out, "Product part/model number", area.getProductModelNumber());
			field(out, "Product version", area.getProductVersion());
			field(out, "Product serial number", area.getProductSerialNumber());
			field(out, "Product asset tag", area.getAssetTag());
			fields(out, "Product custom", area.getCustomProductInfo());
		}
		Optional<SpdInfo> spd = fru.spd();
		if (spd.isPresent()) {
			SpdInfo module = spd.get();
			field(out, "Memory type", module.getMemoryType() + " " + module.getModuleType() + (module.isEcc() ? " ECC" : ""));
			field(out, "Memory capacity", module.getCapacityMegabytes() + " MB");
			field(out, "Module manufacturer", module.getManufacturer());
			field(out, "DRAM manufacturer", module.getDramManufacturer());
			field(out, "Module part number", module.getPartNumber());
			field(out, "Module serial number", module.getSerialNumber());
			field(out, "Module mfg date", mfgDate(module));
		}
		Optional<PiromInfo> pirom = fru.pirom();
		if (pirom.isPresent()) {
			PiromInfo cpu = pirom.get();
			field(out, "Processor S-spec", cpu.getSSpec());
			field(out, "Processor part number", cpu.getPartNumber());
			field(out, "Processor PPIN", cpu.getPpin());
			field(out, "Processor CPUID", cpu.getCpuid() == 0 ? null : String.format(Locale.ROOT, "%04Xh", cpu.getCpuid()));
			field(
					out,
					"Processor cores",
					cpu.getCores() == 0 ? null : cpu.getCores() + (cpu.isHyperThreading() ? " with Hyper-Threading" : ""));
			field(out, "Processor frequency", frequency(cpu));
			field(
					out,
					"Processor L2 cache",
					cpu.getL2KilobytesPerCore() == 0 ? null : cpu.getL2KilobytesPerCore() + " KB per core");
			field(out, "Processor L3 cache", cpu.getL3Kilobytes() == 0 ? null : cpu.getL3Kilobytes() + " KB");
			field(
					out,
					"Processor NVM DIMM max",
					cpu.getNvmDimmMaxCapacityGigabytes() == 0 ? null : cpu.getNvmDimmMaxCapacityGigabytes() + " GB");
			field(out, "Processor TDP", cpu.getTdpWatts() == 0 ? null : cpu.getTdpWatts() + " W");
			field(out, "Processor Tcase max", cpu.getTcaseMax() == 0 ? null : cpu.getTcaseMax() + " C");
		}
	}

	/** The base and turbo frequencies of a processor, "2100-3000 MHz", null when unknown. */
	private static String frequency(PiromInfo cpu) {
		if (cpu.getBaseFrequencyMhz() == 0) {
			return null;
		}
		return cpu.getMaxFrequencyMhz() > cpu.getBaseFrequencyMhz() ?
				cpu.getBaseFrequencyMhz() + "-" + cpu.getMaxFrequencyMhz() + " MHz" :
				cpu.getBaseFrequencyMhz() + " MHz";
	}

	/** The manufacturing year and week of a memory module as an ISO-8601 week date (2019-W23), null when unknown. */
	private static String mfgDate(SpdInfo module) {
		int week = module.getManufacturingWeek();
		return module.getManufacturingYear() == 0 || week < 1 || week > 53 ?
				null :
				String.format(Locale.ROOT, "%d-W%02d", module.getManufacturingYear(), week);
	}

	private static Map<String, Object> fruFields(Fru fru) {
		Map<String, Object> fields = new LinkedHashMap<String, Object>();
		fields.put("id", fru.id());
		fields.put("address", fruAddress(fru));
		fields.put("lun", fru.locator().getAccessLun());
		fields.put("channel", fru.locator().getManagementChannelNumber());
		fields.put("name", fru.name());
		fields.put("entity", fru.entity().name());
		fields.put("entityInstance", fru.entityInstance());
		put(fields, "manufacturer", fru.manufacturer());
		put(fields, "model", fru.model());
		put(fields, "serialNumber", fru.serialNumber());
		put(fields, "partNumber", fru.partNumber());
		fru.chassis().ifPresent(area -> {
			Map<String, Object> chassis = new LinkedHashMap<String, Object>();
			put(chassis, "type", area.getChassisType() == null ? null : area.getChassisType().name());
			put(chassis, "partNumber", area.getChassisPartNumber());
			put(chassis, "serialNumber", area.getChassisSerialNumber());
			put(chassis, "custom", area.getCustomChassisInfo());
			fields.put("chassis", chassis);
		});
		fru.board().ifPresent(area -> {
			Map<String, Object> board = new LinkedHashMap<String, Object>();
			put(board, "manufacturer", area.getBoardManufacturer());
			put(board, "productName", area.getBoardProductName());
			put(board, "serialNumber", area.getBoardSerialNumber());
			put(board, "partNumber", area.getBoardPartNumber());
			put(board, "mfgDate", date(area.getMfgDate()));
			put(board, "custom", area.getCustomBoardInfo());
			fields.put("board", board);
		});
		fru.product().ifPresent(area -> {
			Map<String, Object> product = new LinkedHashMap<String, Object>();
			put(product, "manufacturerName", area.getManufacturerName());
			put(product, "productName", area.getProductName());
			put(product, "modelNumber", area.getProductModelNumber());
			put(product, "version", area.getProductVersion());
			put(product, "serialNumber", area.getProductSerialNumber());
			put(product, "assetTag", area.getAssetTag());
			put(product, "custom", area.getCustomProductInfo());
			fields.put("product", product);
		});
		fru.spd().ifPresent(module -> {
			Map<String, Object> spd = new LinkedHashMap<String, Object>();
			spd.put("memoryType", module.getMemoryType());
			spd.put("moduleType", module.getModuleType());
			spd.put("capacityMegabytes", module.getCapacityMegabytes());
			spd.put("ecc", module.isEcc());
			put(spd, "manufacturer", module.getManufacturer());
			put(spd, "dramManufacturer", module.getDramManufacturer());
			put(spd, "partNumber", module.getPartNumber());
			put(spd, "serialNumber", module.getSerialNumber());
			put(spd, "mfgDate", mfgDate(module));
			fields.put("spd", spd);
		});
		fru.pirom().ifPresent(cpu -> {
			Map<String, Object> pirom = new LinkedHashMap<String, Object>();
			put(pirom, "sSpec", cpu.getSSpec());
			put(pirom, "partNumber", cpu.getPartNumber());
			put(pirom, "ppin", cpu.getPpin());
			put(pirom, "cpuid", cpu.getCpuid() == 0 ? null : String.format(Locale.ROOT, "%04X", cpu.getCpuid()));
			// A value of 0 is a field the ROM does not give (an absent section): omitted, like an empty FRU field
			if (cpu.getCores() != 0) {
				pirom.put("cores", cpu.getCores());
				pirom.put("hyperThreading", cpu.isHyperThreading());
			}
			put(pirom, "baseFrequencyMhz", cpu.getBaseFrequencyMhz());
			put(pirom, "maxFrequencyMhz", cpu.getMaxFrequencyMhz());
			put(pirom, "l2KilobytesPerCore", cpu.getL2KilobytesPerCore());
			put(pirom, "l3Kilobytes", cpu.getL3Kilobytes());
			put(pirom, "nvmDimmMaxCapacityGigabytes", cpu.getNvmDimmMaxCapacityGigabytes());
			put(pirom, "tdpWatts", cpu.getTdpWatts());
			put(pirom, "tcaseMax", cpu.getTcaseMax());
			put(pirom, "dtsMax", cpu.getDtsMax());
			fields.put("pirom", pirom);
		});
		fields.put("complete", fru.complete());
		if (!fru.complete()) {
			fields.put("problems", fru.problems());
		}
		return fields;
	}

	private static int sel(CliArguments arguments, IpmiClient client, PrintStream out, PrintStream err) {
		SelRequest request = client.sel();
		if (arguments.since() >= 0) {
			request.since(arguments.since());
		}
		SelEntryList entries = timed(arguments, err, "SEL", request::execute);
		if (arguments.verbose() && entries.totalEntries().isPresent()) {
			diagnostic(
					err,
					"SEL: " + entries.totalEntries().getAsInt() + " entries, last addition "
							+ entries.lastAddition().map(Instant::toString).orElse("unknown") + ", last erase "
							+ entries.lastErase().map(Instant::toString).orElse("unknown") + ", SEL time UTC offset "
							+ entries.utcOffsetMinutes() + " min");
		}
		if (arguments.json()) {
			for (SelEntry entry : entries) {
				JsonLinesWriter.write(selFields(entry), out);
			}
			return 0;
		}
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] { "ID", "Time", "Type", "Sensor", "Event", "Direction" });
		for (SelEntry entry : entries) {
			rows.add(
					new String[]
					{
							String.valueOf(entry.id()),
							entry.timestamp().map(Instant::toString).orElse(preInit(entry)),
							entry.sensorType()
									.map(type -> typeName(type, entry.sensorTypeCode().getAsInt()))
									.orElse(entry.type().name()),
							entry.sensorNumber().isPresent() ? String.valueOf(entry.sensorNumber().getAsInt()) : "",
							entry.description(),
							entry.direction().map(direction -> direction.name().toLowerCase(Locale.ROOT)).orElse("") });
		}
		out.print(table(rows));
		return 0;
	}

	private static String preInit(SelEntry entry) {
		return entry.preInitSeconds().isPresent() ? "pre-init " + entry.preInitSeconds().getAsLong() + "s" : "";
	}

	private static Map<String, Object> selFields(SelEntry entry) {
		Map<String, Object> fields = new LinkedHashMap<String, Object>();
		fields.put("id", entry.id());
		fields.put("type", entry.type().name());
		entry.timestamp().ifPresent(time -> fields.put("timestamp", time.toString()));
		entry.preInitSeconds().ifPresent(seconds -> fields.put("preInitSeconds", seconds));
		entry.sensorType().ifPresent(type -> fields.put("sensorType", type.name()));
		entry.sensorTypeCode().ifPresent(code -> fields.put("sensorTypeCode", code));
		entry.sensorNumber().ifPresent(number -> fields.put("sensorNumber", number));
		entry.generatorId().ifPresent(id -> fields.put("generatorId", id));
		entry.eventType().ifPresent(type -> fields.put("eventType", type));
		entry.event().ifPresent(event -> fields.put("event", event.name()));
		fields.put("description", entry.description());
		entry.direction().ifPresent(direction -> fields.put("direction", direction.name()));
		if (entry.eventData().length > 0) {
			fields.put("eventData", hex(entry.eventData()));
		}
		entry.manufacturerId().ifPresent(id -> fields.put("manufacturerId", id));
		if (entry.oemData().length > 0) {
			fields.put("oemData", hex(entry.oemData()));
		}
		return fields;
	}

	private static String hex(byte[] bytes) {
		StringBuilder text = new StringBuilder();
		for (byte b : bytes) {
			text.append(String.format("%02x", b));
		}
		return text.toString();
	}

	private static int sol(
			CliArguments arguments,
			IpmiClient client,
			PrintStream out,
			PrintStream err,
			InputStream stdin,
			boolean terminal)
			throws InterruptedException {
		SolConsole console = timed(arguments, err, "SOL console", () -> client.sol().open());
		RawInput raw = terminal ? rawInput(stdin, err) : null;
		// stty raw turns the newline translation of the terminal off: the text of the CLI itself needs CR LF there (the
		// key reader of Windows leaves the console output as it is)
		PrintStream local = raw == null || isWindows() ? err : crlf(err);
		// A kill (Ctrl+C in line mode) must give the terminal back and free the payload on the BMC: some BMCs keep
		// a payload active after its session is gone, so the console is deactivated before the session is closed
		Thread hook = new Thread(() -> {
			if (raw != null) {
				raw.release.run();
			}
			console.close();
			client.close();
		}, "ipmicli-sol-shutdown");
		try {
			// Inside the protected region: a registration that fails must still give the terminal back
			Runtime.getRuntime().addShutdownHook(hook);
			if (terminal) {
				// Like ipmitool: an idle serial console prints nothing until the server does, so say the console is open
				diagnostic(
						local,
						"SOL console open"
								+ (raw == null ?
										", line-oriented: the input is sent when Enter is pressed and echoed by the remote console only" :
										"")
								+ "; " + (char) arguments.escape() + ". at the start of a line closes it, " + (char) arguments.escape()
								+ "? lists the escape sequences");
			}
			boolean lineMode = terminal && raw == null;
			// In line mode the console would echo the typed line locally, and the remote console echoes it again (or
			// hides it: a password prompt): read the lines without echo, so that only the remote echo shows
			InputStream input = lineMode ?
					new LineInput(() -> System.console().readPassword(), Charset.defaultCharset()) :
					raw == null ? stdin : raw.input;
			return SolBridge.run(console, input, out, local, arguments.escape(), lineMode, arguments.verbose());
		} finally {
			if (raw != null) {
				raw.release.run();
			}
			// Already closed by the bridge, unless the hook could not be registered: the payload must not stay active
			console.close();
			try {
				Runtime.getRuntime().removeShutdownHook(hook);
			} catch (IllegalStateException e) {
				// The JVM is shutting down: the hook runs by itself
			}
		}
	}

	/** The keystrokes of the terminal, one by one and without echo, and how to give the terminal back. */
	private static final class RawInput {
		private final InputStream input;
		private final Runnable release;

		private RawInput(InputStream input, Runnable release) {
			this.input = input;
			this.release = release;
		}
	}

	/**
	 * Takes the keystrokes of the terminal one by one, without local echo, so that every key (arrow keys, Ctrl+C,
	 * function keys) goes to the BMC: {@code stty raw -echo} on {@code /dev/tty} on Unix, a PowerShell child reading
	 * the keys of the console on Windows, where Java cannot switch the console to raw mode.
	 *
	 * @return the keystrokes and the release of the terminal, {@code null} when neither works (line mode then)
	 */
	private static RawInput rawInput(InputStream stdin, PrintStream err) {
		try {
			if (isWindows()) {
				return windowsKeys();
			}
			String saved = stty("-g").trim();
			stty("raw -echo");
			return new RawInput(stdin, () -> restoreTerminal(saved, err));
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			diagnostic(err, "cannot take the keystrokes of the terminal (" + e.getMessage() + ")");
			return null;
		}
	}

	/**
	 * The PowerShell program that reads the keys of the console ({@code ReadKey(true)}: no echo, Ctrl+C included) and
	 * writes them to its standard output as the bytes a terminal sends: the character as UTF-8 (ESC before it with
	 * Alt), the cursor, editing and function keys as their ANSI escape sequences with the xterm modifier parameter
	 * (2 Shift, 3 Alt, 5 Control, and their sums), Shift+Tab as CSI Z.
	 */
	static final String KEY_READER = "$csi = @{ UpArrow = 'A'; DownArrow = 'B'; RightArrow = 'C'; LeftArrow = 'D'; Home = 'H'; End = 'F'; F1 = 'P'; F2 = 'Q'; F3 = 'R'; F4 = 'S' }\n"
			+ "$tilde = @{ Insert = 2; Delete = 3; PageUp = 5; PageDown = 6; F5 = 15; F6 = 17; F7 = 18; F8 = 19; F9 = 20; F10 = 21; F11 = 23; F12 = 24 }\n"
			+ "function Encode($k) {\n" + "  $shift = ($k.Modifiers -band [ConsoleModifiers]::Shift) -ne 0\n"
			+ "  $alt = ($k.Modifiers -band [ConsoleModifiers]::Alt) -ne 0\n"
			+ "  $ctrl = ($k.Modifiers -band [ConsoleModifiers]::Control) -ne 0\n"
			+ "  $m = 1 + [int]$shift + 2 * [int]$alt + 4 * [int]$ctrl\n" + "  $key = \"$($k.Key)\"\n" + "  $b = @()\n"
			+ "  if ($csi.ContainsKey($key)) {\n" + "    $final = [int][char]$csi[$key]\n"
			+ "    if ($m -eq 1) { $b = @(27, $(if ($key -like 'F?') { 79 } else { 91 }), $final) }\n"
			+ "    else { $b = @(27, 91, 49, 59) + [Text.Encoding]::ASCII.GetBytes(\"$m\") + @($final) }\n"
			+ "  } elseif ($tilde.ContainsKey($key)) {\n"
			+ "    $b = @(27, 91) + [Text.Encoding]::ASCII.GetBytes(\"$($tilde[$key])\")\n"
			+ "    if ($m -gt 1) { $b = $b + @(59) + [Text.Encoding]::ASCII.GetBytes(\"$m\") }\n" + "    $b = $b + @(126)\n"
			+ "  } elseif ($key -eq 'Tab' -and $shift) {\n" + "    $b = @(27, 91, 90)\n"
			+ "  } elseif ($ctrl -and -not $alt -and [int]$k.KeyChar -eq 0) {\n" + "    $b = @(0)\n"
			+ "  } elseif ([int]$k.KeyChar -ne 0) {\n"
			+ "    if ([char]::IsHighSurrogate($k.KeyChar)) { $script:high = $k.KeyChar; return [byte[]]@() }\n"
			+ "    $text = if ($script:high -and [char]::IsLowSurrogate($k.KeyChar)) { [string]$script:high + [string]$k.KeyChar }"
			+ " else { [string]$k.KeyChar }\n" + "    $script:high = $null\n"
			+ "    $b = [Text.Encoding]::UTF8.GetBytes($text)\n" + "    if ($alt -and -not $ctrl) { $b = @(27) + $b }\n"
			+ "  }\n" + "  return [byte[]]$b\n" + "}\n" + "[Console]::TreatControlCAsInput = $true\n"
			+ "$out = [Console]::OpenStandardOutput()\n" + "while ($true) {\n" + "  $b = Encode([Console]::ReadKey($true))\n"
			+ "  if ($b.Count -gt 0) { $out.Write($b, 0, $b.Count); $out.Flush() }\n" + "}\n";

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static RawInput windowsKeys() throws IOException {
		// The child inherits the console, so ReadKey reads the keys of the terminal of the user
		Process keys = new ProcessBuilder(
				"powershell.exe",
				"-NoProfile",
				"-NoLogo",
				"-EncodedCommand",
				Base64.getEncoder().encodeToString(KEY_READER.getBytes(StandardCharsets.UTF_16LE)))
				.redirectInput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT)
				.start();
		// A reader that cannot read the keys (no console, a restricted PowerShell) says so and exits at once
		try {
			if (keys.waitFor(KEY_READER_START_MS, TimeUnit.MILLISECONDS)) {
				throw new IOException("the PowerShell key reader ended with exit code " + keys.exitValue());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			keys.destroy();
			throw new IOException("interrupted while starting the PowerShell key reader", e);
		}
		return new RawInput(new KeyReaderInput(keys), keys::destroy);
	}

	/** The keys of the PowerShell reader; its end is a failure of the reader, not the end of the input of the user. */
	private static final class KeyReaderInput extends InputStream {
		private final Process reader;

		private KeyReaderInput(Process reader) {
			this.reader = reader;
		}

		@Override
		public int read() throws IOException {
			return ended(reader.getInputStream().read());
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			return ended(reader.getInputStream().read(buffer, offset, length));
		}

		private int ended(int result) throws IOException {
			if (result < 0) {
				throw new IOException("the PowerShell key reader ended" + exitCode());
			}
			return result;
		}

		private String exitCode() {
			try {
				return reader.waitFor(KEY_READER_START_MS, TimeUnit.MILLISECONDS) ?
						" with exit code " + reader.exitValue() :
						"";
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return "";
			}
		}
	}

	/** A stream that writes CR LF for every LF: the text of the CLI on a terminal in raw mode. */
	static PrintStream crlf(PrintStream stream) {
		try {
			return new PrintStream(new FilterOutputStream(stream) {
				@Override
				public void write(int b) throws IOException {
					if (b == '\n') {
						out.write('\r');
					}
					out.write(b);
				}

				@Override
				public void write(byte[] buffer, int offset, int length) throws IOException {
					for (int i = offset; i < offset + length; i++) {
						write(buffer[i]);
					}
				}
			}, true, Charset.defaultCharset().name());
		} catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Gives the terminal its saved settings back, or sane ones, and says when neither works. */
	private static void restoreTerminal(String saved, PrintStream err) {
		try {
			stty(saved);
		} catch (IOException e) {
			try {
				stty("sane");
			} catch (IOException sane) {
				diagnostic(err, "cannot restore the terminal (" + sane.getMessage() + "): run stty sane");
			} catch (InterruptedException sane) {
				Thread.currentThread().interrupt();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String stty(String arguments) throws IOException, InterruptedException {
		Process process = new ProcessBuilder("sh", "-c", "stty " + arguments + " < /dev/tty").redirectErrorStream(true)
				.start();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[256];
		try (InputStream stream = process.getInputStream()) {
			int count = stream.read(buffer);
			while (count >= 0) {
				output.write(buffer, 0, count);
				count = stream.read(buffer);
			}
		}
		String text = output.toString("UTF-8");
		if (process.waitFor() != 0) {
			throw new IOException("stty " + arguments + ": " + text.trim());
		}
		return text;
	}

	private static void skipped(List<SkippedRecord> records, PrintStream err) {
		for (SkippedRecord record : records) {
			diagnostic(err, record.toString());
		}
	}

	private static int outputFailed(PrintStream err) {
		diagnostic(err, "cannot write to standard output");
		return EXIT_IO;
	}

	static void diagnostic(PrintStream err, String message) {
		err.println(NAME + ": " + message);
	}

	private static void line(PrintStream out, String indent, int width, String label, String value) {
		out.println(indent + pad(label, width) + value);
	}

	private static void field(PrintStream out, String label, String value) {
		if (value != null && !value.trim().isEmpty()) {
			line(out, FRU_INDENT, FRU_LABEL_WIDTH, label, printable(value.trim()));
		}
	}

	private static void fields(PrintStream out, String label, String[] values) {
		if (values != null) {
			for (int i = 0; i < values.length; i++) {
				field(out, label + " " + (i + 1), values[i]);
			}
		}
	}

	private static void put(Map<String, Object> fields, String key, String value) {
		if (value != null && !value.trim().isEmpty()) {
			fields.put(key, value.trim());
		}
	}

	/** Puts a number the source gives, 0 standing for a value it does not give. */
	private static void put(Map<String, Object> fields, String key, int value) {
		if (value != 0) {
			fields.put(key, value);
		}
	}

	private static void put(Map<String, Object> fields, String key, String[] values) {
		if (values != null && values.length > 0) {
			List<String> list = new ArrayList<String>();
			for (String value : values) {
				list.add(value == null ? "" : value.trim());
			}
			fields.put(key, list);
		}
	}

	/** Aligns columns on their widest cell, two spaces apart, without trailing spaces. */
	static String table(List<String[]> rows) {
		int columns = 0;
		for (String[] row : rows) {
			columns = Math.max(columns, row.length);
		}
		int[] widths = new int[columns];
		for (String[] row : rows) {
			for (int i = 0; i < row.length; i++) {
				widths[i] = Math.max(widths[i], row[i].length());
			}
		}
		StringBuilder text = new StringBuilder();
		for (String[] row : rows) {
			StringBuilder line = new StringBuilder();
			for (int i = 0; i < row.length; i++) {
				line.append(i + 1 < row.length ? pad(printable(row[i]), widths[i] + 2) : printable(row[i]));
			}
			int end = line.length();
			while (end > 0 && line.charAt(end - 1) == ' ') {
				end--;
			}
			text.append(line, 0, end).append(System.lineSeparator());
		}
		return text.toString();
	}

	private static String pad(String text, int width) {
		StringBuilder padded = new StringBuilder(text);
		while (padded.length() < width) {
			padded.append(' ');
		}
		return padded.toString();
	}

	/** A reading rounded to three decimals, without trailing zeros: {@code 22}, {@code 3.38}, {@code 6600}. */
	static String number(OptionalDouble value) {
		OptionalDouble rounded = round(value);
		return rounded.isPresent() ? BigDecimal.valueOf(rounded.getAsDouble()).stripTrailingZeros().toPlainString() : "";
	}

	/**
	 * Rounds a reading to three decimals (the conversion formula of the SDR leaves binary noise in the double); an
	 * infinite value, which the exponential or reciprocal linearization of a record can give, is no reading.
	 */
	static OptionalDouble round(OptionalDouble value) {
		if (!value.isPresent() || Double.isInfinite(value.getAsDouble()) || Double.isNaN(value.getAsDouble())) {
			return OptionalDouble.empty();
		}
		// BigDecimal: a scaled long would saturate on a large reading
		return OptionalDouble
				.of(BigDecimal.valueOf(value.getAsDouble()).setScale(DECIMALS, RoundingMode.HALF_UP).doubleValue());
	}

	/** Replaces the control characters of a BMC string, which would drive the terminal, with {@code ?}. */
	static String printable(String text) {
		StringBuilder clean = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			clean.append(Character.isISOControl(c) ? '?' : c);
		}
		return clean.toString();
	}

	private static String date(Date date) {
		return date == null ? null : date.toInstant().toString();
	}

	private static String enabled(boolean flag) {
		return flag ? "enabled" : "disabled";
	}

	private static String yesNo(boolean flag) {
		return flag ? "yes" : "no";
	}

	private static String join(int[] values) {
		StringBuilder text = new StringBuilder();
		for (int value : values) {
			text.append(text.length() == 0 ? "" : ",").append(value);
		}
		return text.toString();
	}

	private static String authenticationName(byte code) {
		switch (code) {
		case SecurityConstants.AA_RAKP_NONE:
			return "RAKP-none";
		case SecurityConstants.AA_RAKP_HMAC_SHA1:
			return "RAKP-HMAC-SHA1";
		case SecurityConstants.AA_RAKP_HMAC_MD5:
			return "RAKP-HMAC-MD5";
		case SecurityConstants.AA_RAKP_HMAC_SHA256:
			return "RAKP-HMAC-SHA256";
		default:
			return String.format("%02Xh", code);
		}
	}

	private static String integrityName(byte code) {
		switch (code) {
		case SecurityConstants.IA_NONE:
			return "none";
		case SecurityConstants.IA_HMAC_SHA1_96:
			return "HMAC-SHA1-96";
		case SecurityConstants.IA_HMAC_MD5_128:
			return "HMAC-MD5-128";
		case SecurityConstants.IA_MD5_128:
			return "MD5-128";
		case SecurityConstants.IA_HMAC_SHA256_128:
			return "HMAC-SHA256-128";
		default:
			return String.format("%02Xh", code);
		}
	}

	private static String confidentialityName(byte code) {
		switch (code) {
		case SecurityConstants.CA_NONE:
			return "none";
		case SecurityConstants.CA_AES_CBC128:
			return "AES-CBC-128";
		case SecurityConstants.CA_XRC4_128:
			return "xRC4-128";
		case SecurityConstants.CA_XRC4_40:
			return "xRC4-40";
		default:
			return String.format("%02Xh", code);
		}
	}

	private static char[] readConsolePassword() throws CliUsageException {
		Console console = System.console();
		if (!isTerminal(console)) {
			throw new CliUsageException("no interactive console to ask the password: use --password-file");
		}
		return console.readPassword("Password: ");
	}

	/** The version of the build, from the manifest of the jar. */
	static String version() {
		Package cliPackage = IpmiCli.class.getPackage();
		String version = cliPackage == null ? null : cliPackage.getImplementationVersion();
		return version == null ? "development" : version;
	}

	/** The command the usage names: the launcher of the installer when it sets the variable, the jar otherwise. */
	static String programName() {
		String name = System.getenv(PROGRAM_NAME_VARIABLE);
		return name == null || name.trim().isEmpty() ? "java -jar ipmi-java-" + version() + "-standalone.jar" : name.trim();
	}

	static String usage() {
		String program = programName();
		// Wrapped for an 80-column terminal (the descriptions start at column 33), except the usage lines, which name
		// the program and run longer with java -jar
		return "Usage:\n" + "  " + program + " -h <host> [options] probe [--json]\n" + "  " + program
				+ " -h <host> -u <user> [options] <subcommand> [arguments]\n" + "  " + program + " --help | --version\n" + "\n"
				+ "<subcommand> [arguments]:\n" + "  info [--json]\n" + "  chassis [--json]\n"
				+ "  power status | on | off | cycle | reset | soft | diag\n" + "  identify [<seconds> | force | off]\n"
				+ "  sensors [--type <type>,...] [--name <glob>,...] [--long] [--json]\n" + "  fru [<id>...] [--json]\n"
				+ "  sel [--since <id>] [--json]\n" + "  sol [--escape <char> | --no-escape]\n"
				+ "  raw <netfn> <command> [<data byte>...] [--json]\n" + "\n" + "Connection options (before the subcommand):\n"
				+ "  -h, --hostname <host>         BMC host name or IP address (required)\n"
				+ "  -u, --username <user>         User name (required, except for probe); the\n"
				+ "                                empty name \"\" is the null user name\n"
				+ "  -p, --password <password>     Password (visible to local processes; avoid\n"
				+ "                                in automation)\n"
				+ "  -pf, --password-file <file>   Read a UTF-8 password from a file\n"
				+ "                                (preferred for automation)\n"
				+ "  -k, --bmc-key <hex>           BMC key (Kg) of a two-key login, in\n"
				+ "                                hexadecimal (at most 20 bytes)\n"
				+ "  -kf, --bmc-key-file <file>    Read the hexadecimal BMC key from a file\n"
				+ "  -P, --port <port>             UDP port of the BMC (default: 623)\n"
				+ "  -L, --privilege <level>       user, operator, administrator or auto\n"
				+ "                                (default: the lowest the subcommand needs);\n"
				+ "                                auto runs at user and raises the session\n"
				+ "                                when a command needs more\n"
				+ "  -C, --cipher-suite <id>[,..]  One ID: use exactly this suite; several:\n"
				+ "                                preference order (default: 17,3,8,16,2,7,12,11)\n"
				+ "  -t, --timeout <ms>            Deadline of the subcommand (default:\n"
				+ "                                120000); for sol, of its setup\n"
				+ "      --message-timeout <ms>    Per-message timeout (default: 5000)\n"
				+ "      --retries <n>             Resends of a message without reply\n"
				+ "                                (default: 3)\n"
				+ "  -v, --verbose                 Print the connection details and the time\n"
				+ "                                of each step to stderr\n"
				+ "      --help                    Show this help\n"
				+ "      --version                 Show the project version\n" + "\n"
				+ "Subcommand options (after the subcommand, in any order with its arguments):\n"
				+ "      --json                    probe, info, chassis, sensors, fru, sel,\n"
				+ "                                raw: print JSON Lines instead of text\n"
				+ "      --type <type>,...         sensors: only these sensor types\n"
				+ "                                (Temperature, Fan, Voltage, ...)\n"
				+ "      --name <glob>,...         sensors: only the sensors whose name\n"
				+ "                                matches (* and ?, case-insensitive)\n"
				+ "      --long                    sensors: add the number, the entity, the\n"
				+ "                                thresholds and the problem\n"
				+ "      --since <id>              sel: only the entries after this record ID\n"
				+ "                                (the last one read before)\n"
				+ "      --escape <char>           sol: the escape character (default: ~); at\n"
				+ "                                the start of a line, ~. closes the console\n"
				+ "                                and ~? lists the sequences\n"
				+ "      --no-escape               sol: no escape sequences (binary\n"
				+ "                                transfers); the end of the input closes\n"
				+ "                                the console\n" + "\n"
				+ "Default privilege: user for info, chassis, power status, sensors, fru and\n"
				+ "sel; operator for the other power actions and identify; administrator for\n" + "sol and raw.\n"
				+ "raw takes bytes in hexadecimal, 0x optional: raw 0x06 0x01 is Get Device ID.\n"
				+ "If neither password option is given, the password is requested from the\n" + "interactive console.\n" + "\n"
				+ "Full manual (output formats, SOL console, exit codes, ipmitool equivalents):\n"
				+ "  https://metricshub.org/ipmi-java/cli.html\n";
	}

	/** Asks the user for the password. */
	@FunctionalInterface
	interface PasswordReader {
		char[] readPassword() throws CliUsageException;
	}

	/**
	 * The lines of a line-oriented terminal, read without local echo ({@code Console.readPassword()}), each followed by
	 * a CR as Enter on a serial console; {@code null} from the reader is the end of the input.
	 */
	static final class LineInput extends InputStream {
		private final Supplier<char[]> lines;
		private final Charset charset;
		private byte[] pending = new byte[0];
		private int position;
		private boolean ended;

		LineInput(Supplier<char[]> lines, Charset charset) {
			this.lines = lines;
			this.charset = charset;
		}

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if (length == 0) {
				return 0;
			}
			if (position == pending.length) {
				if (ended) {
					return -1;
				}
				char[] line;
				try {
					line = lines.get();
				} catch (IOError e) {
					// Console.readPassword(): the terminal read failed
					throw new IOException("Cannot read the console: " + e.getMessage(), e);
				}
				if (line == null) {
					ended = true;
					return -1;
				}
				char[] withEnter = Arrays.copyOf(line, line.length + 1);
				withEnter[line.length] = '\r';
				ByteBuffer encoded = charset.encode(CharBuffer.wrap(withEnter));
				pending = Arrays.copyOf(encoded.array(), encoded.limit());
				position = 0;
				Arrays.fill(line, '\0');
				Arrays.fill(withEnter, '\0');
				Arrays.fill(encoded.array(), (byte) 0);
			}
			int count = Math.min(length, pending.length - position);
			System.arraycopy(pending, position, buffer, offset, count);
			// The line may be a password: forget it as soon as it is handed out
			Arrays.fill(pending, position, position + count, (byte) 0);
			position += count;
			return count;
		}
	}
}
