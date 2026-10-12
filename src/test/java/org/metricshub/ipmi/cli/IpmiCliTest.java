package org.metricshub.ipmi.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.ADMINISTRATOR;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.OPEN_SESSION_REQUEST;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.SOL_DEACTIVATED;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SENSOR_READING;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.NETFN_SENSOR;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.fruDeviceLocatorRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.client.Fixtures;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.SolConsole;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.SolPacket;
import org.metricshub.ipmi.core.transport.FakeSdrRepository;

/**
 * The CLI end to end on the fake BMC: the text and JSON output of every subcommand against golden files, the exit
 * code of each failure, and the SOL console.
 */
class IpmiCliTest {

	private static final IpmiCli.PasswordReader NO_PROMPT = () -> {
		throw new CliUsageException("no interactive console to ask the password: use --password-file");
	};

	@Test
	void helpAndVersionDoNotConnect() {
		Result help = run(null, NO_PROMPT, "--help");
		assertEquals(0, help.code);
		assertTrue(help.out.startsWith("Usage:"), help.out);
		assertTrue(help.out.contains("-pf, --password-file"), help.out);
		assertEquals("", help.err);

		Result version = run(null, NO_PROMPT, "--version");
		assertEquals(0, version.code);
		assertEquals("ipmi-java development\n", version.out);

		assertEquals(0, run(null, NO_PROMPT, "sensors", "--help").code, "--help after the subcommand");
	}

	@Test
	void usageErrorsExitWith64AndNeverPrintTheSecrets() {
		Result missing = run(null, NO_PROMPT);
		assertEquals(IpmiCli.EXIT_USAGE, missing.code);
		assertTrue(missing.err.startsWith("ipmicli: missing subcommand"), missing.err);
		assertTrue(missing.err.contains("Try 'java -jar ipmi-java-development-standalone.jar --help'"), missing.err);
		assertEquals("", missing.out);

		Result secret = run(
				null,
				NO_PROMPT,
				"-h",
				"bmc",
				"-u",
				"admin",
				"-p",
				"s3cret",
				"-k",
				"0102",
				"--bogus",
				"chassis");
		assertEquals(IpmiCli.EXIT_USAGE, secret.code);
		assertTrue(secret.err.contains("unknown option '--bogus'"), secret.err);
		assertFalse(secret.err.contains("s3cret"), secret.err);
		assertFalse(secret.err.contains("0102"), secret.err);

		Result builder = run(null, NO_PROMPT, "-h", "bmc", "-u", "admin", "-p", "x", "-C", "1", "chassis");
		assertEquals(IpmiCli.EXIT_USAGE, builder.code, "the validation of the client builder is a usage error");
		assertTrue(builder.err.contains("no integrity algorithm"), builder.err);

		Result tooLong = run(null, NO_PROMPT, "-h", "bmc", "-u", "admin", "-p", "123456789012345678901", "chassis");
		assertEquals(IpmiCli.EXIT_USAGE, tooLong.code, "a password a BMC cannot hold is refused by build()");
		assertTrue(tooLong.err.contains("Password is too long"), tooLong.err);
		assertFalse(tooLong.err.contains("123456789012345678901"), tooLong.err);

		Result noConsole = run(null, NO_PROMPT, "-h", "bmc", "-u", "admin", "chassis");
		assertEquals(IpmiCli.EXIT_USAGE, noConsole.code);
		assertTrue(noConsole.err.contains("--password-file"), noConsole.err);
	}

	@Test
	@Timeout(30)
	void probeNeedsNoCredentials() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result text = run(bmc, NO_PROMPT, "probe");
			assertEquals(0, text.code, text.err);
			assertEquals(golden("probe.txt"), text.out);
			assertEquals("", text.err);
			assertEquals(golden("probe-json.txt"), run(bmc, NO_PROMPT, "probe", "--json").out);
			assertEquals(0, bmc.getCount(OPEN_SESSION_REQUEST), "no session");

			Result fixed = run(bmc, NO_PROMPT, "-C", "4", "probe");
			assertTrue(
					fixed.out.endsWith("Chosen cipher suite         4 (not implemented by the library: the login would fail)\n"),
					fixed.out);
			Result preference = run(bmc, NO_PROMPT, "-C", "4,3", "probe", "--json");
			assertTrue(
					preference.out
							.endsWith(
									"\"chosenCipherSuite\":4,\"chosenCipherSuiteProblem\":\"not implemented by the "
											+ "library: the login would fail\"}\n"),
					"the client takes the first offered suite, implemented or not");
			assertTrue(run(bmc, NO_PROMPT, "-C", "3", "probe").out.endsWith("Chosen cipher suite         3\n"));
		}
	}

	@Test
	@Timeout(30)
	void chassisAndPowerStatus() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result chassis = login(bmc, "chassis");
			assertEquals(0, chassis.code, chassis.err);
			assertEquals(golden("chassis.txt"), chassis.out);
			assertEquals(golden("chassis-json.txt"), login(bmc, "chassis", "--json").out);
			assertEquals("on\n", login(bmc, "power", "status").out);

			Result verbose = login(bmc, "-v", "chassis");
			assertEquals(golden("chassis.txt"), verbose.out, "the verbose output goes to stderr only");
			assertTrue(verbose.err.contains("ipmicli: connecting to "), verbose.err);
			assertTrue(verbose.err.contains("with the User privilege"), verbose.err);
			assertTrue(verbose.err.contains("ipmicli: chassis status: "), verbose.err);
			assertFalse(verbose.err.contains("secret"), verbose.err);
		}
	}

	@Test
	@Timeout(30)
	void sensorsTextLongAndJson() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result sensors = login(bmc, "sensors");
			assertEquals(0, sensors.code, sensors.err);
			assertEquals(golden("sensors.txt"), sensors.out);
			assertEquals(
					"ipmicli: SDR record 0006h (type 0Bh): Cannot decode the record: Invalid record type: 11\n",
					sensors.err);
			assertEquals(golden("sensors-long.txt"), login(bmc, "sensors", "--long").out);
			assertEquals(golden("sensors-json.txt"), login(bmc, "sensors", "--json").out);

			int readings = bmc.getCount(NETFN_SENSOR, GET_SENSOR_READING);
			Result selected = login(bmc, "sensors", "--type", "fan,TEMPERATURE", "--name", "fan*");
			assertEquals("Name   Type  Reading  Unit  Status\nFan 1  Fan                  Device Present\n", selected.out);
			assertEquals(readings + 1, bmc.getCount(NETFN_SENSOR, GET_SENSOR_READING), "selected before the readings");
		}
	}

	@Test
	@Timeout(30)
	void frusTextJsonSelectionAndFailures() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result frus = login(bmc, "fru");
			assertEquals(0, frus.code, frus.err);
			assertEquals(golden("fru.txt"), frus.out);
			assertEquals(golden("fru-json.txt"), login(bmc, "fru", "--json").out);

			Result one = login(bmc, "fru", "1");
			assertEquals(0, one.code);
			assertTrue(one.out.startsWith("FRU 1: PSU 1 (PowerSupply 1)\n"), one.out);
			assertFalse(one.out.contains("FRU 0"), one.out);

			Result missing = login(bmc, "fru", "7", "1");
			assertEquals(IpmiCli.EXIT_NOT_FOUND, missing.code);
			assertTrue(missing.out.startsWith("FRU 1: PSU 1"), "the FRUs found are printed");
			assertTrue(missing.err.endsWith("ipmicli: no such FRU 7\n"), missing.err);
		}
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			// FRU 1 is declared but absent: the BMC answers C3h to its Get FRU Inventory Area Info
			new FakeSdrRepository()
					.add(fruDeviceLocatorRecord(4, Fixtures.PSU_FRU, EntityId.PowerSupply.getCode(), 1, "PSU 1"))
					.install(bmc);
			Result partial = login(bmc, "fru");
			assertEquals(IpmiCli.EXIT_PARTIAL, partial.code);
			assertEquals("FRU 1: PSU 1 (PowerSupply 1)\n", partial.out);
			assertTrue(partial.err.startsWith("ipmicli: FRU 1: "), partial.err);
			assertTrue(partial.err.contains("C3h"), partial.err);
			assertTrue(login(bmc, "fru", "--json").out.contains("\"complete\":false,\"problems\":[\""));
		}
	}

	@Test
	@Timeout(60)
	void eachFailureHasItsExitCode() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result password = run(bmc, NO_PROMPT, "-u", "admin", "-p", "wrong", "chassis");
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, password.code);
			assertTrue(password.err.contains("wrong password or BMC key"), password.err);
			assertFalse(password.err.contains("wrong)"), "the password is never printed");

			Result user = run(bmc, NO_PROMPT, "-u", "nobody", "-p", "secret", "chassis");
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, user.code);
			assertTrue(user.err.contains("Unauthorized name"), user.err);

			Result prompted = run(bmc, () -> "secret".toCharArray(), "-u", "admin", "power", "status");
			assertEquals(0, prompted.code, prompted.err);
			assertEquals("on\n", prompted.out);

			Result deadline = run(bmc, NO_PROMPT, "-u", "admin", "-p", "secret", "-t", "1", "chassis");
			assertEquals(IpmiCli.EXIT_TIMEOUT, deadline.code);
			assertTrue(deadline.err.contains("did not complete within"), deadline.err);

			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			Result privilege = run(bmc, NO_PROMPT, "-u", "admin", "-p", "secret", "-L", "user", "sol");
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, privilege.code);
			assertTrue(privilege.err.contains("D4h"), privilege.err);
			assertTrue(privilege.err.contains("use -L operator or -L administrator"), privilege.err);
			assertFalse(privilege.err.contains("PrivilegeLevel.Operator"), privilege.err);
		}
		Result host = run(null, NO_PROMPT, "-h", "no-such-host.invalid", "-u", "admin", "-p", "x", "chassis");
		assertEquals(IpmiCli.EXIT_CONNECTION, host.code);
		assertTrue(host.err.contains("Unknown host"), host.err);
	}

	@Test
	@Timeout(30)
	void solEscapeSequences() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			// ~? prints the help, ~~ sends one ~, ~B sends a break, ~. closes: "rest" is never sent
			Result sol = sol(bmc, "root\r~?~~\r~B\r~.rest", "sol");
			assertEquals(0, sol.code, sol.err);
			assertEquals("", sol.out);
			assertTrue(sol.err.contains("~.  close the console"), sol.err);
			assertEquals("root\r~\r\r", ascii(bmc.getSolReceived()));
			boolean sentBreak = false;
			for (SolPacket packet : bmc.getSolPackets()) {
				sentBreak |= (packet.operation & 1 << SolOperation.Break.getOperationNumber()) != 0;
			}
			assertTrue(sentBreak, "the break operation reached the BMC");
			assertFalse(bmc.isSolActive(), "the payload is deactivated");
			awaitClose(bmc);

			assertEquals(0, sol(bmc, "~.\r!.", "sol", "--escape", "!").code);
			// The fake BMC keeps the characters of the consoles before
			assertEquals("root\r~\r\r~.\r", ascii(bmc.getSolReceived()));
			awaitClose(bmc);

			Result third = sol(bmc, "~.\r", "sol", "--no-escape");
			assertEquals(0, third.code, "the end of the input closes");
			assertEquals("root\r~\r\r~.\r~.\r", ascii(bmc.getSolReceived()));
			awaitClose(bmc);

			assertEquals(0, sol(bmc, "data\r~", "sol").code, "a lone escape character at the end of the input is data");
			assertEquals("root\r~\r\r~.\r~.\rdata\r~", ascii(bmc.getSolReceived()));
		}
	}

	@Test
	void readingsAreRoundedAndInfiniteOnesAreAbsent() {
		assertEquals("11.748", IpmiCli.number(java.util.OptionalDouble.of(11.748000000000001)));
		assertEquals("6600", IpmiCli.number(java.util.OptionalDouble.of(6600.0)));
		assertEquals("10000000000000000", IpmiCli.number(java.util.OptionalDouble.of(1.0e16)), "no saturation");
		assertEquals("-0.001", IpmiCli.number(java.util.OptionalDouble.of(-0.0005)));
		assertEquals("", IpmiCli.number(java.util.OptionalDouble.of(Double.POSITIVE_INFINITY)));
		assertEquals("", IpmiCli.number(java.util.OptionalDouble.of(Double.NaN)));
		assertEquals("", IpmiCli.number(java.util.OptionalDouble.empty()));
		assertEquals("a?b?c?", IpmiCli.printable("a\u0000b\u009bc\u007f"), "C0, C1 and DEL controls");
	}

	@Test
	@Timeout(30)
	void solOutputAndDeactivation() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			BlockingInput stdin = new BlockingInput();
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try {
				Future<Result> result = executor.submit(() -> run(bmc, NO_PROMPT, stdin, "-u", "admin", "-p", "secret", "sol"));
				await(bmc::isSolActive, "the console is open");
				bmc.sendSol(1, 0, "login: ".getBytes(StandardCharsets.US_ASCII));
				bmc.sendSol(2, SOL_DEACTIVATED, new byte[0]);
				Result sol = result.get(20, TimeUnit.SECONDS);
				assertEquals(IpmiCli.EXIT_PROTOCOL, sol.code);
				assertEquals("login: ", sol.out, "the bytes of the BMC go to stdout unmodified");
				assertEquals("ipmicli: the console was deactivated by the BMC\n", sol.err);
			} finally {
				stdin.release();
				executor.shutdownNow();
			}
		}
	}

	@Test
	@Timeout(30)
	void aLineOrientedTerminalSendsEnterAsCarriageReturn() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			try (
					IpmiClient client = IpmiClient
							.builder(bmc.getAddress().getHostAddress())
							.port(bmc.getPort())
							.credentials(Fixtures.USER, Fixtures.PASSWORD.toCharArray())
							.privilege(PrivilegeLevel.Administrator)
							.messageTimeout(Duration.ofMillis(300))
							.build();
					SolConsole console = client.sol().open()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ByteArrayOutputStream err = new ByteArrayOutputStream();
				int code = SolBridge
						.run(
								console,
								new ByteArrayInputStream("ls\r\nid\n~.".getBytes(StandardCharsets.US_ASCII)),
								new PrintStream(out, true, "UTF-8"),
								new PrintStream(err, true, "UTF-8"),
								'~',
								true);
				assertEquals(0, code);
				assertEquals("ls\rid\r", ascii(bmc.getSolReceived()));
			}
		}
	}

	@Test
	void lineInputAppendsEnterToEachLineReadWithoutEcho() throws Exception {
		java.util.Iterator<char[]> lines = Arrays.asList("ls".toCharArray(), "p\u00e4ss".toCharArray(), null).iterator();
		try (InputStream input = new IpmiCli.LineInput(lines::next, StandardCharsets.UTF_8)) {
			byte[] buffer = new byte[2];
			assertEquals(2, input.read(buffer));
			assertEquals("ls", new String(buffer, StandardCharsets.UTF_8));
			assertEquals('\r', input.read());
			buffer = new byte[16];
			assertEquals(6, input.read(buffer), "p\u00e4ss as UTF-8, then CR");
			assertEquals("p\u00e4ss\r", new String(buffer, 0, 6, StandardCharsets.UTF_8));
			assertEquals(-1, input.read(), "null ends the input");
			assertEquals(-1, input.read(buffer));
		}
	}

	private static Result sol(FakeRmcpPlusBmc bmc, String input, String... args) {
		List<String> full = new ArrayList<String>(Arrays.asList("-u", "admin", "-p", "secret"));
		full.addAll(Arrays.asList(args));
		return run(
				bmc,
				NO_PROMPT,
				new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
				full.toArray(new String[0]));
	}

	private static Result login(FakeRmcpPlusBmc bmc, String... args) {
		List<String> full = new ArrayList<String>(Arrays.asList("-u", "admin", "-pf", passwordFile()));
		full.addAll(Arrays.asList(args));
		return run(bmc, NO_PROMPT, full.toArray(new String[0]));
	}

	private static String passwordFile() {
		try {
			java.nio.file.Path file = java.nio.file.Files.createTempFile("ipmicli", ".txt");
			file.toFile().deleteOnExit();
			java.nio.file.Files.write(file, (Fixtures.PASSWORD + "\r\n").getBytes(StandardCharsets.UTF_8));
			return file.toString();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Result run(FakeRmcpPlusBmc bmc, IpmiCli.PasswordReader reader, String... args) {
		return run(bmc, reader, new ByteArrayInputStream(new byte[0]), args);
	}

	/** Runs the CLI on the fake BMC (its address and port prepended, short message timeout), or without one. */
	private static Result run(FakeRmcpPlusBmc bmc, IpmiCli.PasswordReader reader, InputStream stdin, String... args) {
		List<String> full = new ArrayList<String>();
		if (bmc != null) {
			full
					.addAll(
							Arrays
									.asList(
											"-h",
											bmc.getAddress().getHostAddress(),
											"-P",
											String.valueOf(bmc.getPort()),
											"--message-timeout",
											"300",
											"--retries",
											"1"));
		}
		full.addAll(Arrays.asList(args));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		try {
			int code = IpmiCli
					.run(
							full.toArray(new String[0]),
							new PrintStream(out, true, "UTF-8"),
							new PrintStream(err, true, "UTF-8"),
							reader,
							stdin,
							false);
			return new Result(code, text(out), text(err));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String text(ByteArrayOutputStream stream) throws IOException {
		return stream.toString("UTF-8").replace("\r\n", "\n");
	}

	private static String ascii(byte[] bytes) {
		return new String(bytes, StandardCharsets.US_ASCII);
	}

	private static String golden(String name) throws IOException {
		try (InputStream stream = IpmiCliTest.class.getResourceAsStream(name)) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int count = stream.read(buffer);
			while (count >= 0) {
				bytes.write(buffer, 0, count);
				count = stream.read(buffer);
			}
			return text(bytes);
		}
	}

	private static void awaitClose(FakeRmcpPlusBmc bmc) throws InterruptedException {
		await(bmc::isSessionClosed, "the session was closed");
	}

	private static void await(BooleanSupplier condition, String message) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertTrue(condition.getAsBoolean(), message);
	}

	private static final class Result {
		private final int code;
		private final String out;
		private final String err;

		private Result(int code, String out, String err) {
			this.code = code;
			this.out = out;
			this.err = err;
		}
	}

	/** A standard input that never ends until released: a terminal nobody types on. */
	private static final class BlockingInput extends InputStream {
		private final CountDownLatch released = new CountDownLatch(1);

		@Override
		public int read() throws IOException {
			try {
				released.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return -1;
		}

		void release() {
			released.countDown();
		}
	}
}
