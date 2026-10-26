package org.metricshub.ipmi.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;

class CliArgumentsTest {

	@Test
	void shortAndLongOptionsInBothForms() throws Exception {
		try (
				CliArguments arguments = CliArguments.parse(new String[]
				{
						"-h",
						"bmc",
						"-u",
						"admin",
						"-p",
						"secret",
						"-k",
						"0x0102",
						"-P",
						"1623",
						"-L",
						"OPERATOR",
						"-C",
						"17,3",
						"-t",
						"5000",
						"--message-timeout=200",
						"--retries",
						"0",
						"-v",
						"sensors" })) {
			assertEquals(CliArguments.Subcommand.SENSORS, arguments.subcommand());
			assertEquals("bmc", arguments.hostname());
			assertEquals("admin", arguments.username());
			assertArrayEquals("secret".toCharArray(), arguments.password());
			assertArrayEquals(new byte[] { 1, 2 }, arguments.bmcKey());
			assertEquals(1623, arguments.port());
			assertEquals(PrivilegeLevel.Operator, arguments.privilege());
			assertArrayEquals(new int[] { 17, 3 }, arguments.cipherSuites());
			assertEquals(5000, arguments.timeoutMs());
			assertEquals(200, arguments.messageTimeoutMs());
			assertEquals(0, arguments.retries());
			assertTrue(arguments.verbose());
		}
		try (
				CliArguments arguments = CliArguments.parse(new String[]
				{
						"--hostname=bmc",
						"--username",
						"",
						"--password=",
						"--bmc-key",
						"0102030405060708090a0b0c0d0e0f1011121314",
						"--port=623",
						"--privilege=user",
						"--cipher-suite=3",
						"--timeout=1",
						"chassis" })) {
			assertEquals("", arguments.username(), "the null user");
			assertEquals(0, arguments.password().length, "an empty password, not a prompt");
			assertEquals(20, arguments.bmcKey().length);
			assertArrayEquals(new int[] { 3 }, arguments.cipherSuites());
			assertEquals(PrivilegeLevel.User, arguments.privilege());
		}
	}

	@Test
	void defaults() throws Exception {
		try (CliArguments arguments = CliArguments.parse(new String[] { "-h", "bmc", "-u", "admin", "chassis" })) {
			assertNull(arguments.password(), "to be asked");
			assertNull(arguments.bmcKey());
			assertNull(arguments.cipherSuites());
			assertEquals(623, arguments.port());
			assertEquals(CliArguments.DEFAULT_TIMEOUT_MS, arguments.timeoutMs());
			assertEquals(CliArguments.DEFAULT_MESSAGE_TIMEOUT_MS, arguments.messageTimeoutMs());
			assertEquals(CliArguments.DEFAULT_RETRIES, arguments.retries());
			assertFalse(arguments.verbose());
			assertFalse(arguments.json());
			assertFalse(arguments.longFormat());
			assertNull(arguments.types());
			assertTrue(arguments.names().isEmpty());
			assertTrue(arguments.fruIds().isEmpty());
			assertEquals('~', arguments.escape());
		}
	}

	@Test
	void thePrivilegeDefaultsToTheLowestTheSubcommandNeeds() throws Exception {
		assertEquals(PrivilegeLevel.User, parse("chassis").privilege());
		assertEquals(PrivilegeLevel.User, parse("info").privilege());
		assertEquals(PrivilegeLevel.User, parse("power", "status").privilege());
		assertEquals(PrivilegeLevel.Operator, parse("power", "on").privilege());
		assertEquals(PrivilegeLevel.Operator, parse("identify").privilege());
		assertEquals(PrivilegeLevel.User, parse("-L", "user", "power", "on").privilege());
		assertEquals(PrivilegeLevel.User, parse("sensors").privilege());
		assertEquals(PrivilegeLevel.User, parse("fru").privilege());
		assertEquals(PrivilegeLevel.User, parse("probe").privilege());
		assertEquals(PrivilegeLevel.Administrator, parse("sol").privilege());
		assertEquals(PrivilegeLevel.Administrator, parse("raw", "6", "1").privilege());
		assertEquals(PrivilegeLevel.User, parse("-L", "user", "raw", "6", "1").privilege());
		assertEquals(PrivilegeLevel.Administrator, parse("-L", "Administrator", "chassis").privilege());
	}

	@Test
	void subcommandOptionsInAnyOrderWithTheArguments() throws Exception {
		CliArguments sensors = parse(
				"sensors",
				"--type",
				"fan,Temperature",
				"--long",
				"--json",
				"--type=voltage",
				"--name",
				"CPU*,*DTS*");
		assertEquals(EnumSet.of(SensorType.Fan, SensorType.Temperature, SensorType.Voltage), sensors.types());
		assertEquals(Arrays.asList("CPU*", "*DTS*"), sensors.names());
		assertTrue(sensors.longFormat());
		assertTrue(sensors.json());

		CliArguments fru = parse("fru", "1", "--json", "0", "255");
		assertEquals(Arrays.asList(1, 0, 255), fru.fruIds());
		assertTrue(fru.json());

		assertEquals('!', parse("sol", "--escape", "!").escape());
		assertEquals(-1, parse("sol", "--no-escape").escape());
		assertTrue(parse("probe", "--json").json());
		assertEquals(CliArguments.Subcommand.HELP, parse("fru", "--help").subcommand());
		assertEquals(CliArguments.Subcommand.VERSION, CliArguments.parse(new String[] { "--version" }).subcommand());
	}

	@Test
	void systemFirmwareProgressInBothSpellings() throws Exception {
		assertEquals(
				EnumSet.of(SensorType.SystemFirmwareProgress),
				parse("sensors", "--type", "SystemFirmwareProgress").types());
		assertEquals(
				EnumSet.of(SensorType.SystemFirmwareProgress),
				parse("sensors", "--type", "systemfirmwareprogess").types(),
				"the 2.0.00 misspelling");
	}

	@Test
	void rawBytesInHexadecimal() throws Exception {
		CliArguments dcmi = parse("raw", "0x2c", "01", "--json", "DC", "0X1");
		assertEquals(CliArguments.Subcommand.RAW, dcmi.subcommand());
		assertEquals(0x2c, dcmi.rawNetworkFunction());
		assertEquals(1, dcmi.rawCommand());
		assertArrayEquals(new int[] { 0xdc, 0x01 }, dcmi.rawData());
		assertTrue(dcmi.json());

		CliArguments deviceId = parse("raw", "6", "1");
		assertEquals(6, deviceId.rawNetworkFunction());
		assertEquals(0, deviceId.rawData().length);
		assertEquals(0x3e, parse("raw", "3E", "FF", "ff", "0").rawNetworkFunction());
	}

	@Test
	void doubleDashEndsTheOptions() throws Exception {
		assertEquals(CliArguments.Subcommand.PROBE, parse("--", "probe").subcommand());
		assertEquals("missing subcommand (" + CliArguments.SUBCOMMANDS + ")", usageError("-h", "bmc", "--"));
	}

	@Test
	void usageErrors() throws Exception {
		assertEquals("missing subcommand (" + CliArguments.SUBCOMMANDS + ")", usageError("-h", "bmc"));
		assertEquals("unknown subcommand 'reboot' (" + CliArguments.SUBCOMMANDS + ")", usageError("-h", "bmc", "reboot"));
		assertEquals("missing required option --hostname", usageError("-u", "admin", "chassis"));
		assertEquals("missing required option --username", usageError("-h", "bmc", "chassis"));
		assertEquals("unknown option '--https'", usageError("-h", "bmc", "--https", "chassis"));
		assertEquals("unknown option '-x'", usageError("-h", "bmc", "-u", "u", "chassis", "-x"));
		assertEquals("--hostname requires a value", usageError("-u", "admin", "--hostname"));
		assertEquals("--password and --password-file are mutually exclusive", usageError("-p", "a", "-pf", "f"));
		assertEquals("--bmc-key and --bmc-key-file are mutually exclusive", usageError("-k", "01", "-kf", "f"));
		// A secret given twice is a mistake, in either form of the option; the other options take their last value
		assertEquals("--password is given twice", usageError("--password=a", "-p", "b"));
		assertEquals("--bmc-key is given twice", usageError("-k", "01", "-k", "02", "-p", "x"));
		assertEquals("bmc2", parse("-h", "bmc2", "chassis").hostname(), "the last -h wins");
		assertEquals(
				"--escape and --no-escape are mutually exclusive",
				usageError(login("sol", "--escape", "!", "--no-escape")));
		assertEquals("--escape requires a single printable ASCII character", usageError(login("sol", "--escape", "~~")));
		assertEquals("--escape requires a single printable ASCII character", usageError(login("sol", "--escape", "é")));
		assertEquals("--escape requires a single printable ASCII character", usageError(login("sol", "--escape", " ")));
		assertEquals(
				"--json requires the probe or info or chassis or sensors or fru or sel or raw subcommand",
				usageError(login("sol", "--json")));
		assertEquals("--type requires the sensors subcommand", usageError(login("fru", "--type", "Fan")));
		assertEquals("raw requires <netfn> <command> [<data byte>...]", usageError(login("raw")));
		assertEquals("raw requires <netfn> <command> [<data byte>...]", usageError(login("raw", "6")));
		for (String netFn : new String[] { "0x", "zz", "100", "+6", "" }) {
			assertEquals(
					"raw <netfn> must be a byte in hexadecimal (06, 0x06 or 6)",
					usageError(login("raw", netFn, "1")),
					netFn);
		}
		for (String netFn : new String[] { "07", "0x40", "ff" }) {
			assertEquals(
					"raw <netfn> must be a request network function: an even byte from 00 to 3e",
					usageError(login("raw", netFn, "1")),
					netFn);
		}
		assertEquals("raw <command> must be a byte in hexadecimal (06, 0x06 or 6)", usageError(login("raw", "6", "0x100")));
		assertEquals(
				"raw <data byte> must be a byte in hexadecimal (06, 0x06 or 6)",
				usageError(login("raw", "6", "1", "1ff")));
		assertEquals("--long requires the sensors subcommand", usageError(login("chassis", "--long")));
		assertEquals("--no-escape requires the sol subcommand", usageError(login("chassis", "--no-escape")));
		assertEquals("unknown sensor type 'Fans'", usageError(login("sensors", "--type", "Fans")));
		assertEquals("--name requires a glob", usageError(login("sensors", "--name", ",")));
		assertEquals("--type requires a sensor type", usageError(login("sensors", "--type", ",")));
		assertEquals("--type requires a sensor type", usageError(login("sensors", "--type", "Fan,")));
		assertEquals("--type requires a sensor type", usageError(login("sensors", "--type", "")));
		assertEquals("--name requires a glob", usageError(login("sensors", "--name", "")));
		assertEquals("--name requires a glob", usageError(login("sensors", "--name", "CPU*,,Fan*")));
		assertEquals("--name requires a glob", usageError(login("sensors", "--name", "CPU*,")));
		assertNull(parse("power", "status").powerCommand());
		assertEquals(PowerCommand.PowerUp, parse("power", "on").powerCommand());
		assertEquals(PowerCommand.PowerDown, parse("power", "off").powerCommand());
		assertEquals(PowerCommand.PowerCycle, parse("power", "cycle").powerCommand());
		assertEquals(PowerCommand.HardReset, parse("power", "reset").powerCommand());
		assertEquals(PowerCommand.SoftShutdown, parse("power", "soft").powerCommand());
		assertEquals(PowerCommand.DiagnosticInterrupt, parse("power", "diag").powerCommand());
		assertEquals("power requires one action: status, on, off, cycle, reset, soft or diag", usageError(login("power")));
		assertEquals(
				"power requires one action: status, on, off, cycle, reset, soft or diag",
				usageError(login("power", "On")));
		assertEquals(
				"power requires one action: status, on, off, cycle, reset, soft or diag",
				usageError(login("power", "on", "off")));
		assertEquals(15, parse("identify").identifySeconds());
		assertFalse(parse("identify").identifyForever());
		assertEquals(5, parse("identify", "5").identifySeconds());
		assertEquals(0, parse("identify", "off").identifySeconds());
		assertTrue(parse("identify", "force").identifyForever());
		assertEquals("identify <seconds> must be between 1 and 255", usageError(login("identify", "0")));
		assertEquals("identify <seconds> must be between 1 and 255", usageError(login("identify", "256")));
		assertEquals("identify <seconds> must be a number", usageError(login("identify", "forever")));
		assertEquals(
				"identify takes one argument at most: <seconds>, force or off",
				usageError(login("identify", "5", "force")));
		assertEquals("chassis takes no argument", usageError(login("chassis", "status")));
		assertEquals("fru <id> must be a number", usageError(login("fru", "psu")));
		assertEquals("fru <id> must be between 0 and 255", usageError(login("fru", "256")));
		assertEquals("-P must be between 1 and 65535", usageError("-P", "0"));
		assertEquals("--port must be a number", usageError("--port", "six"));
		assertEquals("-t must be between 1 and 2147483647", usageError("-t", "0"));
		assertEquals("--retries must be between 0 and 2147483647", usageError("--retries", "-1"));
		assertEquals("-L must be user, operator, administrator or auto", usageError("-L", "ADMIN"));
		assertEquals("-C must be between 0 and 19", usageError("-C", "17,20"));
		assertEquals("-C must be a number", usageError("-C", ""));
		assertEquals("-C must be a number", usageError("-C", "17,"));
		assertEquals("--json takes no value", usageError(login("chassis", "--json=false")));
		assertEquals("--no-escape takes no value", usageError(login("sol", "--no-escape=false")));
		assertEquals("--verbose takes no value", usageError("--verbose=true"));
		assertEquals("--long takes no value", usageError(login("sensors", "--long=1")));
	}

	@Test
	void hexadecimalBmcKey() throws Exception {
		assertArrayEquals(new byte[] { (byte) 0xab, 0x01 }, parse("-k", "AB01", "chassis").bmcKey());
		assertArrayEquals(new byte[] { (byte) 0xab, 0x01 }, parse("-k", "0Xab01", "chassis").bmcKey());
		String tooLong = "-k must be an even number of hexadecimal digits, at most 20 bytes";
		assertEquals(tooLong, usageError("-k", "0102030405060708090a0b0c0d0e0f101112131415"));
		assertEquals(tooLong, usageError("-k", "012"));
		assertEquals(tooLong, usageError("-k", ""));
		assertEquals(tooLong, usageError("-k", "0x"));
		assertEquals("-k must be hexadecimal", usageError("-k", "01zz"));
	}

	@Test
	void secretFiles(@TempDir Path directory) throws Exception {
		Path lf = file(directory, "lf", "päss\n");
		Path crlf = file(directory, "crlf", "päss\r\n");
		Path cr = file(directory, "cr", "päss\r");
		Path bare = file(directory, "bare", "päss");
		Path twoLines = file(directory, "two", "päss\n\n");
		Path spaces = file(directory, "spaces", " päss \n");
		for (Path file : Arrays.asList(lf, crlf, cr, bare)) {
			assertArrayEquals("päss".toCharArray(), parse("-pf", file.toString(), "chassis").password(), file.toString());
		}
		assertArrayEquals("päss\n".toCharArray(), parse("--password-file=" + twoLines, "chassis").password());
		assertArrayEquals(" päss ".toCharArray(), parse("-pf", spaces.toString(), "chassis").password());

		Path latin1 = directory.resolve("latin1");
		Files.write(latin1, new byte[] { 'p', (byte) 0xe4, '\n' });
		assertEquals("-pf is not valid UTF-8", usageError("-pf", latin1.toString()));
		assertEquals(
				"cannot read --password-file file",
				usageError("--password-file", directory.resolve("none").toString()));

		Path key = file(directory, "key", "0x0a0B\r\n");
		assertArrayEquals(new byte[] { 0x0a, 0x0b }, parse("-kf", key.toString(), "chassis").bmcKey());
		assertEquals("cannot read -kf file", usageError("-kf", directory.resolve("none").toString()));
		assertEquals(
				"--bmc-key-file must be hexadecimal",
				usageError("--bmc-key-file", file(directory, "zz", "zzzz\n").toString()));
	}

	@Test
	void closeWipesTheSecretsAndTheArgumentArray() throws Exception {
		String[] args = { "-h", "bmc", "-u", "admin", "-p", "secret", "--bmc-key=0102", "chassis" };
		CliArguments arguments = CliArguments.parse(args);
		assertEquals("", args[5], "the password argument is blanked once read");
		assertEquals("--bmc-key", args[6], "and so is the BMC key argument");
		char[] password = arguments.password();
		byte[] key = arguments.bmcKey();
		arguments.close();
		assertArrayEquals(new char[6], password);
		assertArrayEquals(new byte[2], key);
		assertNull(arguments.password());
		assertNull(arguments.bmcKey());

		String[] joined = { "-h", "bmc", "-u", "admin", "-p=secret", "-x" };
		assertThrows(CliUsageException.class, () -> CliArguments.parse(joined));
		assertEquals("-p", joined[4], "blanked even when the parsing fails later");

		String[] twice = { "-h", "bmc", "-u", "admin", "-p", "one", "-p", "two", "-k", "01", "--bmc-key=02", "chassis" };
		assertEquals("--password is given twice", usageError(twice));
		assertEquals("", twice[7], "a rejected duplicate secret is blanked too");
		assertEquals("", twice[5], "and the accepted one");
		String[] keys = { "-h", "bmc", "-u", "admin", "-k", "01", "--bmc-key=02", "chassis" };
		assertEquals("--bmc-key is given twice", usageError(keys));
		assertEquals("--bmc-key", keys[6]);
	}

	private static CliArguments parse(String... args) throws CliUsageException {
		return CliArguments.parse(login(args));
	}

	private static String[] login(String... args) {
		String[] full = new String[args.length + 4];
		full[0] = "-h";
		full[1] = "bmc";
		full[2] = "-u";
		full[3] = "admin";
		System.arraycopy(args, 0, full, 4, args.length);
		return full;
	}

	private static String usageError(String... args) {
		return assertThrows(CliUsageException.class, () -> CliArguments.parse(args)).getMessage();
	}

	private static Path file(Path directory, String name, String content) throws IOException {
		Path file = directory.resolve(name);
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file;
	}
}
