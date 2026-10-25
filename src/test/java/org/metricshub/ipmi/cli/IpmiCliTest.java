package org.metricshub.ipmi.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.ADMINISTRATOR;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHASSIS_STATUS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_CHASSIS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.OPEN_SESSION_REQUEST;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.SOL_DEACTIVATED;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SENSOR_READING;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.NETFN_SENSOR;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.COMPACT_SENSOR_RECORD;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.compactSensorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.fruDeviceLocatorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.record;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.withAccess;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.withId;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.withOwner;

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
		assertTrue(IpmiCli.usage().contains("~."), "the usage names the way out of the console");
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

		Result brokenConsole = run(null, () -> {
			throw new java.io.IOError(new IOException("tty gone"));
		}, "-h", "bmc", "-u", "admin", "chassis");
		assertEquals(IpmiCli.EXIT_IO, brokenConsole.code, "a console read failure at the password prompt");
		assertTrue(brokenConsole.err.contains("cannot read the password from the console"), brokenConsole.err);

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

			// The xRC4 suites, which the library does not implement, are refused by the builder (#202)
			Result fixed = run(bmc, NO_PROMPT, "-C", "4", "probe");
			assertEquals(IpmiCli.EXIT_USAGE, fixed.code, fixed.err);
			assertTrue(fixed.err.contains("xRC4"), fixed.err);
			assertEquals(IpmiCli.EXIT_USAGE, run(bmc, NO_PROMPT, "-C", "4,3", "probe", "--json").code);
			assertTrue(run(bmc, NO_PROMPT, "-C", "3", "probe").out.endsWith("Chosen cipher suite         3\n"));
		}
	}

	@Test
	@Timeout(30)
	void infoTextAndJson() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result info = login(bmc, "info");
			assertEquals(0, info.code, info.err);
			assertEquals(golden("info.txt"), info.out);
			assertEquals("", info.err);
			assertEquals(golden("info-json.txt"), login(bmc, "info", "--json").out);

			Result verbose = login(bmc, "-v", "info");
			assertEquals(golden("info.txt"), verbose.out);
			assertTrue(verbose.err.contains("with the User privilege"), verbose.err);
			assertTrue(verbose.err.contains("ipmicli: device id: "), verbose.err);
		}
	}

	@Test
	@Timeout(30)
	void rawTextJsonAndExitCodes() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			// Get DCMI Capabilities Info (Group Extension 2Ch), and an OEM command with more than 16 bytes of data
			bmc.handle(0x2c, 0x01, data -> Fixtures.bytes("00 dc 01 05 02 00 01 07"));
			bmc.handle(0x30, 0x01, data -> {
				byte[] reply = new byte[21];
				for (int i = 1; i < reply.length; i++) {
					reply[i] = (byte) (i - 1);
				}
				return reply;
			});

			Result dcmi = login(bmc, "-v", "raw", "0x2c", "1", "DC", "0x01");
			assertEquals(0, dcmi.code, dcmi.err);
			assertEquals("dc 01 05 02 00 01 07\n", dcmi.out);
			assertTrue(dcmi.err.contains("with the Administrator privilege"), dcmi.err);
			assertTrue(dcmi.err.contains("ipmicli: raw: "), dcmi.err);

			assertEquals(golden("raw.txt"), login(bmc, "raw", "30", "01").out);
			assertEquals(golden("raw-json.txt"), login(bmc, "raw", "0x30", "0x01", "--json").out);

			// An error completion code: nothing on stdout, the code on stderr, exit code 70
			Result unknown = login(bmc, "raw", "0x30", "0x42");
			assertEquals(IpmiCli.EXIT_PROTOCOL, unknown.code);
			assertEquals("", unknown.out);
			assertTrue(unknown.err.contains("raw 30h 42h failed"), unknown.err);
			assertTrue(unknown.err.contains("(completion code C1h)"), unknown.err);
			Result unknownJson = login(bmc, "raw", "0x30", "0x42", "--json");
			assertEquals(IpmiCli.EXIT_PROTOCOL, unknownJson.code);
			assertEquals("{\"completionCode\":193}\n", unknownJson.out);

			// Chassis Control at the User privilege: D4h, with the hint of the CLI
			int control = bmc.getChassisControl();
			Result refused = login(bmc, "-L", "user", "raw", "0x00", "0x02", "0x01");
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, refused.code);
			assertTrue(refused.err.contains("use -L operator or -L administrator"), refused.err);
			assertFalse(refused.err.contains("PrivilegeLevel.Operator"), refused.err);
			assertEquals(control, bmc.getChassisControl());

			// D4h at the default Administrator level: no level to raise to
			bmc.handle(0x30, 0x02, data -> new byte[] { (byte) 0xd4 });
			Result restricted = login(bmc, "raw", "0x30", "0x02");
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, restricted.code);
			assertTrue(restricted.err.contains("even at the Administrator level"), restricted.err);
			assertFalse(restricted.err.contains("-L"), restricted.err);
			Result operator = login(bmc, "-L", "operator", "raw", "0x30", "0x02");
			assertTrue(operator.err.contains("use -L administrator"), operator.err);
			assertFalse(operator.err.contains("-L operator"), operator.err);
			assertTrue(bmc.getErrors().isEmpty(), bmc.getErrors().toString());
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

			// The reserved identify state 11b is decoded, not thrown (#207)
			bmc.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> new byte[] { 0x00, 0x01, 0x00, 0x70 });
			Result reserved = login(bmc, "chassis");
			assertEquals(0, reserved.code, reserved.err);
			assertTrue(reserved.out.contains("Identify") && reserved.out.contains("Unknown"), reserved.out);
			assertTrue(login(bmc, "chassis", "--json").out.contains("\"identifyState\":\"Unknown\""));
		}
	}

	@Test
	@Timeout(30)
	void powerActionsAndIdentify() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result off = login(bmc, "-v", "power", "off");
			assertEquals(0, off.code, off.err);
			assertEquals("", off.out, "nothing to print: the exit code says the BMC accepted the command");
			assertTrue(off.err.contains("with the Operator privilege"), off.err);
			assertTrue(off.err.contains("ipmicli: chassis control: "), off.err);
			assertEquals(0, bmc.getChassisControl());
			assertEquals("off\n", login(bmc, "power", "status").out);

			Result reset = login(bmc, "power", "reset");
			assertEquals(IpmiCli.EXIT_PROTOCOL, reset.code, "D5h: a reset of a system that is off");
			assertTrue(reset.err.contains("completion code D5h"), reset.err);
			Result cycleOff = login(bmc, "power", "cycle");
			assertEquals(IpmiCli.EXIT_PROTOCOL, cycleOff.code, "a cycle of a system that is off is refused by the client");
			assertTrue(
					cycleOff.err
							.contains("ipmicli: the system is off: a power cycle would power it up on some BMCs, use 'power on'\n"),
					cycleOff.err);
			assertEquals("off\n", login(bmc, "power", "status").out, "nothing was sent");
			assertEquals(0, login(bmc, "power", "on").code);
			assertEquals(1, bmc.getChassisControl());
			assertEquals("on\n", login(bmc, "power", "status").out);
			assertEquals(0, login(bmc, "power", "cycle").code);
			assertEquals(2, bmc.getChassisControl());
			assertEquals(0, login(bmc, "power", "soft").code);
			assertEquals(5, bmc.getChassisControl());
			assertEquals("off\n", login(bmc, "power", "status").out);

			Result user = login(bmc, "-L", "user", "power", "on");
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, user.code, "D4h at the User privilege");
			assertTrue(user.err.contains("use -L operator or -L administrator"), user.err);
			assertEquals("off\n", login(bmc, "power", "status").out, "the refused command did nothing");

			// -L auto: User, raised to Operator on the D4h; the ceiling of the account has its own hint
			assertEquals(0, login(bmc, "-L", "auto", "power", "on").code);
			assertEquals("on\n", login(bmc, "power", "status").out);
			bmc.privilegeLimit(FakeRmcpPlusBmc.USER);
			Result capped = login(bmc, "-L", "auto", "power", "off");
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, capped.code, "D4h at the highest level of the account");
			assertTrue(capped.err.contains("at the highest level of the account"), capped.err);
			assertFalse(capped.err.contains("-L operator"), capped.err);
			assertEquals("on\n", login(bmc, "power", "status").out, "the refused command did nothing");
			bmc.privilegeLimit(ADMINISTRATOR);

			assertEquals(0, login(bmc, "identify").code);
			assertArrayEquals(new byte[] { 15 }, bmc.getChassisIdentify(), "the default interval of the specification");
			assertEquals(0, login(bmc, "identify", "5").code);
			assertArrayEquals(new byte[] { 5 }, bmc.getChassisIdentify());
			assertEquals(0, login(bmc, "identify", "force").code);
			assertArrayEquals(new byte[] { 0, 1 }, bmc.getChassisIdentify());
			Result identifyOff = login(bmc, "-v", "identify", "off");
			assertEquals(0, identifyOff.code);
			assertEquals("", identifyOff.out);
			assertTrue(identifyOff.err.contains("ipmicli: chassis identify: "), identifyOff.err);
			assertArrayEquals(new byte[] { 0 }, bmc.getChassisIdentify());
			assertEquals(IpmiCli.EXIT_AUTHENTICATION, login(bmc, "-L", "user", "identify").code);
			assertTrue(bmc.getErrors().isEmpty(), bmc.getErrors().toString());
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
					"ipmicli: SDR record 0006h (type 0Bh): Cannot decode the record: Reserved record type 0Bh\n",
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
	void sensorsShowThresholdsCrossedOemTypeCodesAndPercentages() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			byte[] percent = withId(Fixtures.FRONT_PANEL_FULL_RECORD.clone(), 2);
			percent[7] = 51; // sensor number
			percent[20] = 0x01; // Sensor Units 1 bit 0: percentage
			percent[21] = 18; // of RPM
			new FakeSdrRepository().add(Fixtures.FRONT_PANEL_FULL_RECORD)
					.add(percent)
					.add(compactSensorRecord(3, 0x30, EntityId.SystemBoard.getCode(), 1, 0xc1, 0x08, "OEM Presence"))
					// at or above UNC and UC
					.reading(Fixtures.TEMPERATURE_SENSOR, 0xb0, 0x40, 0x18)
					.reading(51, 0x9a, 0x40, 0x00)
					.reading(0x30, 0x00, 0x40, 0x02)
					.install(bmc);
			Result sensors = login(bmc, "sensors");
			assertEquals(0, sensors.code, sensors.err);
			assertEquals(
					"Name          Type         Reading  Unit      Status\n"
							+ "Ambient Temp  Temperature  44       DegreesC  critical\n"
							+ "Ambient Temp  Temperature  22       % Rpm     ok\n"
							+ "OEM Presence  Oem C1h                         Device Present\n",
					sensors.out);
			String[] json = login(bmc, "sensors", "--json").out.split("\n");
			assertTrue(
					json[0].contains(
							"\"status\":\"CRITICAL\",\"states\":[\"Upper Non Critical Going High\",\"Upper Critical Going High\"]"),
					json[0]);
			assertTrue(json[1].contains("\"unit\":\"Rpm\",\"percentage\":true,"), json[1]);
			assertTrue(json[2].contains("\"type\":\"Oem\",\"typeCode\":193,"), json[2]);
		}
	}

	@Test
	@Timeout(30)
	void aCompactRecordWithoutIdStringGivesAnEmptyName() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			// A Compact record cut at 31 bytes, before its ID string type/length byte
			byte[] full = compactSensorRecord(2, Fixtures.FAN_SENSOR, EntityId.Fan.getCode(), 1, 0x04, 0x08, "");
			new FakeSdrRepository().add(record(2, COMPACT_SENSOR_RECORD, Arrays.copyOfRange(full, 5, 31)))
					.reading(Fixtures.FAN_SENSOR, 0x00, 0x40, 0x02)
					.install(bmc);
			Result sensors = login(bmc, "sensors");
			assertEquals(0, sensors.code, sensors.err);
			assertEquals("Name  Type  Reading  Unit  Status\n      Fan                  Device Present\n", sensors.out);
			assertTrue(login(bmc, "sensors", "--json").out.startsWith("{\"name\":\"\",\"number\":16,"));
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
		try (
				FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(
						Fixtures.SUITES,
						Fixtures.USER,
						Fixtures.utf8(Fixtures.PASSWORD),
						null)) {
			// The FRU device of a memory module returns its SPD data
			new FakeSdrRepository()
					.add(fruDeviceLocatorRecord(1, Fixtures.DIMM_FRU, EntityId.MemoryDevice.getCode(), 1, "DIMM 1"))
					.fru(Fixtures.DIMM_FRU, Fixtures.DDR4_SPD)
					.install(bmc);
			Result dimm = login(bmc, "fru");
			assertEquals(0, dimm.code, dimm.err);
			assertEquals(
					"FRU 2: DIMM 1 (MemoryDevice 1)\n" + "  Memory type                DDR4 RDIMM ECC\n"
							+ "  Memory capacity            16384 MB\n" + "  Module manufacturer        Samsung\n"
							+ "  DRAM manufacturer          Samsung\n" + "  Module part number         M393A2K43BB1-CTD\n"
							+ "  Module serial number       409F3CA2\n" + "  Module mfg date            2019-W23\n",
					dimm.out);
			assertEquals(
					"{\"id\":2,\"address\":32,\"lun\":0,\"channel\":0,\"name\":\"DIMM 1\",\"entity\":\"MemoryDevice\",\"entityInstance\":1,\"manufacturer\":\"Samsung\","
							+ "\"model\":\"M393A2K43BB1-CTD\",\"serialNumber\":\"409F3CA2\",\"partNumber\":\"M393A2K43BB1-CTD\","
							+ "\"spd\":{\"memoryType\":\"DDR4\",\"moduleType\":\"RDIMM\",\"capacityMegabytes\":16384,\"ecc\":true,"
							+ "\"manufacturer\":\"Samsung\",\"dramManufacturer\":\"Samsung\",\"partNumber\":\"M393A2K43BB1-CTD\","
							+ "\"serialNumber\":\"409F3CA2\",\"mfgDate\":\"2019-W23\"},\"complete\":true}\n",
					login(bmc, "fru", "--json").out);

			// A year without a week is no date
			byte[] noWeek = Fixtures.DDR4_SPD.clone();
			noWeek[324] = 0;
			new FakeSdrRepository()
					.add(fruDeviceLocatorRecord(1, Fixtures.DIMM_FRU, EntityId.MemoryDevice.getCode(), 1, "DIMM 1"))
					.fru(Fixtures.DIMM_FRU, noWeek)
					.install(bmc);
			Result undated = login(bmc, "fru", "--json");
			assertFalse(undated.out.contains("mfgDate"), undated.out);
			assertTrue(undated.out.contains("\"serialNumber\":\"409F3CA2\"}"), undated.out);

			// The FRU device of a processor returns its processor information ROM
			new FakeSdrRepository()
					.add(fruDeviceLocatorRecord(1, Fixtures.CPU_FRU, EntityId.Processor.getCode(), 1, "PIROM 1"))
					.fru(Fixtures.CPU_FRU, Fixtures.PIROM)
					.install(bmc);
			Result cpu = login(bmc, "fru");
			assertEquals(0, cpu.code, cpu.err);
			assertEquals(
					"FRU 85: PIROM 1 (Processor 1)\n" + "  Processor S-spec           SR3GH\n"
							+ "  Processor part number      37608DC00416530\n" + "  Processor PPIN             634AFBC3354D3C6D\n"
							+ "  Processor CPUID            0654h\n" + "  Processor cores            8 with Hyper-Threading\n"
							+ "  Processor frequency        2100-3000 MHz\n" + "  Processor L2 cache         1024 KB per core\n"
							+ "  Processor L3 cache         11264 KB\n" + "  Processor NVM DIMM max     768 GB\n"
							+ "  Processor TDP              85 W\n" + "  Processor Tcase max        77 C\n",
					cpu.out);
			assertEquals(
					"{\"id\":85,\"address\":32,\"lun\":0,\"channel\":0,\"name\":\"PIROM 1\",\"entity\":\"Processor\",\"entityInstance\":1,\"manufacturer\":\"Intel\","
							+ "\"model\":\"SR3GH\",\"serialNumber\":\"634AFBC3354D3C6D\",\"partNumber\":\"37608DC00416530\","
							+ "\"pirom\":{\"sSpec\":\"SR3GH\",\"partNumber\":\"37608DC00416530\",\"ppin\":\"634AFBC3354D3C6D\","
							+ "\"cpuid\":\"0654\",\"cores\":8,\"hyperThreading\":true,\"baseFrequencyMhz\":2100,"
							+ "\"maxFrequencyMhz\":3000,\"l2KilobytesPerCore\":1024,\"l3Kilobytes\":11264,"
							+ "\"nvmDimmMaxCapacityGigabytes\":768,\"tdpWatts\":85,\"tcaseMax\":77,\"dtsMax\":90},\"complete\":true}\n",
					login(bmc, "fru", "--json").out);
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
	@Timeout(30)
	void selTextJsonAndSince() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			Result sel = login(bmc, "sel");
			assertEquals(0, sel.code, sel.err);
			assertEquals(golden("sel.txt"), sel.out);
			assertEquals("", sel.err);
			assertEquals(golden("sel-json.txt"), login(bmc, "sel", "--json").out);

			Result since = login(bmc, "-v", "sel", "--since", "4");
			assertEquals(0, since.code, since.err);
			assertEquals(
					"ID  Time         Type               Sensor  Event                                            Direction\n"
							+ "5                OemNonTimestamped          OEM data 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D\n"
							+ "6   pre-init 5s  Processor          2       IERR                                             assertion\n",
					since.out);
			assertTrue(
					since.err.contains(
							"ipmicli: SEL: 6 entries, last addition 2024-05-15T09:23:34Z, last erase unknown, SEL time UTC offset 0 min\n"),
					since.err);
			assertEquals(
					"ID  Time  Type  Sensor  Event  Direction\n",
					login(bmc, "sel", "--since=6").out,
					"nothing after the last entry: the header alone, as sensors prints for an empty selection");

			Result badId = login(bmc, "sel", "--since", "0");
			assertEquals(IpmiCli.EXIT_USAGE, badId.code);
			assertTrue(badId.err.startsWith("ipmicli: --since must be between 1 and 65534"), badId.err);
			Result wrongSubcommand = login(bmc, "sensors", "--since", "1");
			assertEquals(IpmiCli.EXIT_USAGE, wrongSubcommand.code);
			assertTrue(wrongSubcommand.err.startsWith("ipmicli: --since requires the sel subcommand"), wrongSubcommand.err);
			assertEquals(IpmiCli.EXIT_USAGE, login(bmc, "sel", "1").code, "sel takes no argument");
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

			// No reply: a loopback request can complete within the 1 ms deadline
			bmc.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> null);
			Result deadline = run(bmc, NO_PROMPT, "-u", "admin", "-p", "secret", "-t", "1", "-v", "chassis");
			assertEquals(IpmiCli.EXIT_TIMEOUT, deadline.code);
			assertTrue(deadline.err.contains("did not complete within"), deadline.err);
			assertTrue(deadline.err.contains("ipmicli: chassis status failed after "), "the time of a failed operation");

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
			awaitClose(bmc);
			assertEquals(0, sol(bmc, "..\r.B\r..", "sol", "--escape", ".").code, "a doubled . is a . with --escape .");
			assertEquals("root\r~\r\r~.\r.\r\r.", ascii(bmc.getSolReceived()), "'..' sends one '.', '.B' a break");
			awaitClose(bmc);
			// The fake BMC keeps the characters of the consoles before
			assertEquals("root\r~\r\r~.\r.\r\r.", ascii(bmc.getSolReceived()));
			awaitClose(bmc);

			Result third = sol(bmc, "~.\r", "sol", "--no-escape");
			assertEquals(0, third.code, "the end of the input closes");
			assertEquals("root\r~\r\r~.\r.\r\r.~.\r", ascii(bmc.getSolReceived()));
			awaitClose(bmc);

			assertEquals(0, sol(bmc, "data\r~", "sol").code, "a lone escape character at the end of the input is data");
			assertEquals("root\r~\r\r~.\r.\r\r.~.\rdata\r~", ascii(bmc.getSolReceived()));
		}
	}

	@Test
	void sensorsJsonNamesTheOwnerOfEachSensor() throws Exception {
		try (
				FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(
						Fixtures.SUITES,
						Fixtures.USER,
						Fixtures.utf8(Fixtures.PASSWORD),
						null)) {
			// The same sensor number on the BMC, at LUN 1 of the BMC, on a satellite controller and in the BIOS: the
			// owner is given as the low byte of a SEL generator ID (the 7-bit address shifted left, bit 0 for a
			// software ID), with the LUN and the channel
			byte[] sensor = Fixtures.FRONT_PANEL_FULL_RECORD;
			new FakeSdrRepository().add(withOwner(sensor, 0x20, 0, 0))
					.add(withOwner(withId(sensor, 2), 0x20, 0, 1))
					.add(withOwner(withId(sensor, 3), 0x2c, 6, 0))
					.add(withOwner(withId(sensor, 4), 0xb1, 0, 0))
					.install(bmc);
			String[] json = login(bmc, "sensors", "--json").out.split("\n");
			assertEquals(4, json.length, Arrays.toString(json));
			assertTrue(json[0].contains(",\"entityInstance\":1,\"ownerId\":32,\"ownerLun\":0,\"ownerChannel\":0,"), json[0]);
			assertTrue(json[1].contains(",\"ownerId\":32,\"ownerLun\":1,\"ownerChannel\":0,"), json[1]);
			assertTrue(json[2].contains(",\"ownerId\":44,\"ownerLun\":0,\"ownerChannel\":6,"), json[2]);
			assertTrue(json[3].contains(",\"ownerId\":177,\"ownerLun\":0,\"ownerChannel\":0,"), json[3]);
		}
	}

	@Test
	void frusOfAnotherControllerOrLunAreToldApart() throws Exception {
		try (
				FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(
						Fixtures.SUITES,
						Fixtures.USER,
						Fixtures.utf8(Fixtures.PASSWORD),
						null)) {
			// Three FRUs numbered 5: at LUN 0 and LUN 1 of the BMC, and on a controller behind it (none readable), and
			// the "BMC FRU" of a Supermicro BMC, at 20h on channel 2, which the client reads as a FRU of the BMC
			int psu = EntityId.PowerSupply.getCode();
			new FakeSdrRepository().add(fruDeviceLocatorRecord(1, 5, psu, 1, "BMC LUN 0"))
					.add(withAccess(fruDeviceLocatorRecord(2, 5, psu, 2, "BMC LUN 1"), 0x20, 0, 1))
					.add(withAccess(fruDeviceLocatorRecord(3, 5, psu, 3, "Satellite"), 0x2c, 6, 0))
					.add(withAccess(fruDeviceLocatorRecord(4, 0, EntityId.SystemBoard.getCode(), 1, "BMC FRU"), 0x20, 2, 0))
					.install(bmc);
			Result frus = login(bmc, "fru");
			assertTrue(frus.out.contains("FRU 5: BMC LUN 0 (PowerSupply 1)\n"), frus.out);
			assertTrue(frus.out.contains("FRU 5 at 20h LUN 1: BMC LUN 1 (PowerSupply 2)\n"), frus.out);
			assertTrue(frus.out.contains("FRU 5 at 2Ch channel 6: Satellite (PowerSupply 3)\n"), frus.out);
			assertTrue(frus.out.contains("FRU 0: BMC FRU (SystemBoard 1)\n"), "the channel of a FRU of the BMC is ignored");
			assertTrue(frus.err.contains("ipmicli: FRU 5: "), frus.err);
			assertTrue(frus.err.contains("ipmicli: FRU 5 at 20h LUN 1: "), frus.err);
			assertTrue(frus.err.contains("ipmicli: FRU 5 at 2Ch channel 6: "), frus.err);
			String[] json = login(bmc, "fru", "--json").out.split("\n");
			assertEquals(4, json.length, Arrays.toString(json));
			assertTrue(json[3].startsWith("{\"id\":0,\"address\":32,\"lun\":0,\"channel\":2,\"name\":\"BMC FRU\","), json[3]);
			assertTrue(
					json[0].startsWith("{\"id\":5,\"address\":32,\"lun\":0,\"channel\":0,\"name\":\"BMC LUN 0\","),
					json[0]);
			assertTrue(
					json[1].startsWith("{\"id\":5,\"address\":32,\"lun\":1,\"channel\":0,\"name\":\"BMC LUN 1\","),
					json[1]);
			assertTrue(
					json[2].startsWith("{\"id\":5,\"address\":44,\"lun\":0,\"channel\":6,\"name\":\"Satellite\","),
					json[2]);
		}
	}

	@Test
	void noEscapeNeedsARedirectedInput() throws Exception {
		// Refused before anything is sent: on a terminal in raw mode, nothing typed would close the console
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = IpmiCli
				.run(new String[]
				{ "-h", "bmc", "-u", "admin", "-p", "secret", "sol", "--no-escape" },
						new PrintStream(out, true, "UTF-8"),
						new PrintStream(err, true, "UTF-8"),
						NO_PROMPT,
						new ByteArrayInputStream(new byte[0]),
						true);
		assertEquals(IpmiCli.EXIT_USAGE, code);
		assertTrue(
				text(err).startsWith(
						"ipmicli: --no-escape needs a redirected input: on a terminal, ~. is the only way out"
								+ " (use --escape to pick another character)\n"),
				text(err));
		assertEquals("", text(out));
	}

	@Test
	void aConsoleIsATerminalOnlyWhenItSaysSo() {
		assertFalse(IpmiCli.isTerminal(null));
		// The standard streams of a forked test JVM are pipes: no console up to Java 21 and from 25, a console whose
		// isTerminal() is false on Java 22 to 24
		assertFalse(IpmiCli.isTerminal(System.console()));
	}

	@Test
	void theTextOfTheCliUsesCrLfOnARawTerminal() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream raw = IpmiCli.crlf(new PrintStream(bytes, true, "UTF-8"));
		raw.print("a\nb\r\n");
		raw.flush();
		assertEquals("a\r\nb\r\r\n", bytes.toString("UTF-8"));
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
				// The fake flags the payload active before its reply reaches the client: a packet sent at once could get
				// there before the console listens, and be dropped
				Thread.sleep(500);
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
					IpmiClient client = IpmiClient.builder(bmc.getAddress().getHostAddress())
							.port(bmc.getPort())
							.credentials(Fixtures.USER, Fixtures.PASSWORD.toCharArray())
							.privilege(PrivilegeLevel.Administrator)
							.messageTimeout(Duration.ofMillis(300))
							.build();
					SolConsole console = client.sol().open()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ByteArrayOutputStream err = new ByteArrayOutputStream();
				int code = SolBridge.run(
						console,
						new ByteArrayInputStream("~?\nls\r\nid\n~B\n~.".getBytes(StandardCharsets.US_ASCII)),
						new PrintStream(out, true, "UTF-8"),
						new PrintStream(err, true, "UTF-8"),
						'~',
						true,
						false);
				assertEquals(0, code);
				assertEquals("ls\rid\r", ascii(bmc.getSolReceived()), "the Enters that end ~? and ~B are not sent");
				assertTrue(err.toString("UTF-8").contains("~.  close the console"));
				boolean sentBreak = false;
				for (SolPacket packet : bmc.getSolPackets()) {
					sentBreak |= (packet.operation & 1 << SolOperation.Break.getOperationNumber()) != 0;
				}
				assertTrue(sentBreak, "the break of ~B reached the BMC, and ~. right after it closed the console");
			}
		}
	}

	@Test
	@Timeout(30)
	void aFailingInputExitsWith74() throws Exception {
		try (FakeRmcpPlusBmc bmc = Fixtures.bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			InputStream failing = new InputStream() {
				private boolean first = true;

				@Override
				public int read() throws IOException {
					if (first) {
						first = false;
						return 'x';
					}
					throw new IOException("disk error");
				}
			};
			Result sol = run(bmc, NO_PROMPT, failing, "-u", "admin", "-p", "secret", "sol");
			assertEquals(IpmiCli.EXIT_IO, sol.code);
			assertEquals("ipmicli: cannot read the input: disk error\n", sol.err);
			assertEquals("x", ascii(bmc.getSolReceived()), "what was read before the failure was sent");
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
		try (InputStream failing = new IpmiCli.LineInput(() -> {
			throw new java.io.IOError(new IOException("tty gone"));
		}, StandardCharsets.UTF_8)) {
			IOException failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, failing::read);
			assertTrue(
					failure.getMessage().contains("tty gone"),
					"a console read failure is an IOException the pump reports");
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
			full.addAll(
					Arrays.asList(
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
			int code = IpmiCli.run(
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
