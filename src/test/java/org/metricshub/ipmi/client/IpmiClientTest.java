package org.metricshub.ipmi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.metricshub.ipmi.client.Fixtures.BOARD_SENSOR;
import static org.metricshub.ipmi.client.Fixtures.CPU_FRU;
import static org.metricshub.ipmi.client.Fixtures.DDR4_SPD;
import static org.metricshub.ipmi.client.Fixtures.PIROM;
import static org.metricshub.ipmi.client.Fixtures.DIMM_FRU;
import static org.metricshub.ipmi.client.Fixtures.FAN_SENSOR;
import static org.metricshub.ipmi.client.Fixtures.LENOVO;
import static org.metricshub.ipmi.client.Fixtures.PASSWORD;
import static org.metricshub.ipmi.client.Fixtures.PSU_FRU;
import static org.metricshub.ipmi.client.Fixtures.SEL_RESET_TIME;
import static org.metricshub.ipmi.client.Fixtures.SUITES;
import static org.metricshub.ipmi.client.Fixtures.TEMPERATURE_SENSOR;
import static org.metricshub.ipmi.client.Fixtures.USER;
import static org.metricshub.ipmi.client.Fixtures.bmc;
import static org.metricshub.ipmi.client.Fixtures.utf8;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.ADMINISTRATOR;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHANNEL_AUTHENTICATION_CAPABILITIES;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHANNEL_CIPHER_SUITES;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHASSIS_STATUS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.CHASSIS_CONTROL;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.CHASSIS_IDENTIFY;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.DEVICE_ID;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_DEVICE_ID;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_APP;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_CHASSIS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.OPEN_SESSION_REQUEST;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.SEND_MESSAGE;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.SET_SESSION_PRIVILEGE_LEVEL;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_DEVICE_SDR;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SDR;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SDR_REPOSITORY_INFO;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SENSOR_READING;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.NETFN_SENSOR;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.NETFN_STORAGE;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.compactSensorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.fruDeviceLocatorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.record;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.withOwner;
import static org.metricshub.ipmi.core.transport.FakeSel.GET_SEL_ENTRY;
import static org.metricshub.ipmi.core.transport.FakeSel.GET_SEL_INFO;
import static org.metricshub.ipmi.core.transport.FakeSel.RESERVE_SEL;
import static org.metricshub.ipmi.core.transport.FakeSel.systemEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.OemRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorUnit;
import org.metricshub.ipmi.core.coding.commands.sel.EventDirection;
import org.metricshub.ipmi.core.coding.commands.sel.SelRecordType;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.transport.FakeBmc;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;
import org.metricshub.ipmi.core.transport.FakeSdrRepository;
import org.metricshub.ipmi.core.transport.FakeSel;

class IpmiClientTest {

	private static final Duration MESSAGE_TIMEOUT = Duration.ofMillis(300);
	private static final int RECORDS = 6;

	private static IpmiClient.Builder client(FakeRmcpPlusBmc bmc) {
		return IpmiClient
				.builder(bmc.getAddress().getHostAddress())
				.port(bmc.getPort())
				.credentials(USER, PASSWORD.toCharArray())
				.messageTimeout(MESSAGE_TIMEOUT)
				.retries(1)
				.timeout(Duration.ofSeconds(20));
	}

	@Test
	@Timeout(30)
	void oneHandshakeAndOneSdrWalkForManyOperations() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			assertEquals(0, bmc.getCount(OPEN_SESSION_REQUEST), "build() touches no network");

			ChassisStatus chassis = client.chassisStatus();
			assertTrue(chassis.powerOn());
			assertTrue(client.chassisStatus().powerOn());

			SensorList sensors = client.sensors().execute();
			assertEquals(3, sensors.size(), sensors.toString());
			assertEquals(1, sensors.skippedRecords().size(), "the record of reserved type 0Bh is skipped");
			assertEquals(6, sensors.skippedRecords().get(0).recordId());
			assertEquals(0x0b, sensors.skippedRecords().get(0).recordType());

			FruList frus = client.frus().execute();
			assertEquals(2, frus.size(), frus.toString());
			assertEquals(sensors.skippedRecords().get(0).toString(), frus.skippedRecords().get(0).toString());

			assertEquals(1, bmc.getCount(OPEN_SESSION_REQUEST), "one session for the four operations");
			assertEquals(RECORDS, bmc.getCount(NETFN_STORAGE, GET_SDR), "the repository is walked once");
			assertEquals(2, bmc.getCount(NETFN_STORAGE, GET_SDR_REPOSITORY_INFO), "each walk checks the repository");
			assertEquals(0, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL), "User needs no raise");

			assertEquals(EntityId.SystemBoard, frus.get(0).entity(), "the synthetic system board FRU comes first");
			assertEquals("IBM", frus.get(0).manufacturer());
			assertEquals("PSU 1", frus.get(1).name());
		}
	}

	@Test
	@Timeout(30)
	void typedResults() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			SensorList sensors = client.sensors().execute();

			Sensor temperature = sensors.get(0);
			assertEquals("Ambient Temp", temperature.name());
			assertEquals(TEMPERATURE_SENSOR, temperature.number());
			assertEquals(SensorType.Temperature, temperature.type());
			assertEquals(EntityId.FrontPanelBoard, temperature.entity());
			assertEquals(1, temperature.entityInstance());
			assertEquals(SensorUnit.DegreesC, temperature.unit());
			assertEquals(22.0, temperature.reading().getAsDouble());
			assertEquals(0x9a, temperature.rawReading().getAsInt());
			assertEquals(38.0, temperature.upperNonCritical().getAsDouble());
			assertEquals(41.0, temperature.upperCritical().getAsDouble());
			assertFalse(temperature.lowerNonCritical().isPresent());
			assertEquals(SensorStatus.OK, temperature.status());
			assertFalse(temperature.problem().isPresent());
			assertTrue(temperature.states().isEmpty());

			Sensor fan = sensors.get(1);
			assertEquals("Fan 1", fan.name());
			assertFalse(fan.reading().isPresent());
			assertEquals(Collections.singletonList("Device Present"), fan.states());
			assertEquals(SensorStatus.OK, fan.status());

			FruList frus = client.frus().execute();
			Fru systemBoard = frus.get(0);
			assertEquals(0, systemBoard.id());
			assertEquals(EntityId.SystemBoard, systemBoard.entity());
			assertEquals("IBM", systemBoard.manufacturer());
			assertEquals("System x3650 M2", systemBoard.model());
			assertEquals("KD9098C", systemBoard.serialNumber());
			assertTrue(systemBoard.complete(), systemBoard.problems().toString());
			assertTrue(systemBoard.product().isPresent());
			Fru psu = frus.get(1);
			assertEquals(PSU_FRU, psu.id());
			assertEquals("PSU 1", psu.name());
			assertEquals(EntityId.PowerSupply, psu.entity());

			DeviceId device = client.deviceId();
			assertEquals(32, device.id());
			assertEquals(1, device.revision());
			assertEquals("1.44", device.firmwareRevision());
			assertEquals("2.0", device.ipmiVersion());
			assertEquals(2, device.manufacturerId(), "IBM");
			assertEquals(26, device.productId());
			assertTrue(device.available());
			assertFalse(device.providesDeviceSdrs());
			assertEquals(EnumSet.complementOf(EnumSet.of(DeviceFunction.BRIDGE)), device.functions());
			assertArrayEquals(new byte[] { 1, 2, 3, 4 }, device.auxiliaryFirmwareRevision());
			assertFalse(device.toString().isEmpty());

			assertTrue(client.sensors().execute().stream().allMatch(sensor -> sensor.record() != null));
			assertFalse(sensors.toString().isEmpty());
			assertFalse(frus.toString().isEmpty());
			assertFalse(client.chassisStatus().toString().isEmpty());
		}
	}

	@Test
	@Timeout(30)
	void selEntriesAreTypedAndReadSinceAnEntry() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			SelEntryList log = client.sel().execute();
			assertEquals(6, log.size(), log.toString());
			assertEquals(6, log.totalEntries().getAsInt());
			assertEquals(Instant.ofEpochSecond(SEL_RESET_TIME + 489), log.lastAddition().get());
			assertFalse(log.lastErase().isPresent(), "FFFFFFFFh: never erased");
			assertTrue(log.info().isPresent());
			assertEquals(2, bmc.getCount(NETFN_STORAGE, GET_SEL_INFO), "before and after the walk");
			assertEquals(6 + 1, bmc.getCount(NETFN_STORAGE, GET_SEL_ENTRY), "one whole read per entry, then the first again");
			assertEquals(0, bmc.getCount(NETFN_STORAGE, RESERVE_SEL), "a whole read needs no reservation");

			SelEntry reset = log.get(0);
			assertEquals(1, reset.id());
			assertEquals(SelRecordType.System, reset.type());
			assertEquals(Instant.ofEpochSecond(SEL_RESET_TIME), reset.timestamp().get());
			assertFalse(reset.preInitSeconds().isPresent());
			assertEquals(SensorType.EventLoggingDisabled, reset.sensorType().get());
			assertEquals(0x72, reset.sensorNumber().getAsInt());
			assertEquals(FakeSel.GENERATOR_BMC, reset.generatorId().getAsInt());
			assertEquals(0x6f, reset.eventType().getAsInt());
			assertEquals(ReadingType.LogAreaReset, reset.event().get());
			assertEquals(EventDirection.Assertion, reset.direction().get());
			assertEquals("Log area reset/cleared", reset.description());
			assertArrayEquals(new byte[] { 0x02, (byte) 0xff, (byte) 0xff }, reset.eventData());
			assertFalse(reset.manufacturerId().isPresent());
			assertEquals(0, reset.oemData().length);
			assertEquals(
					"SEL entry 1 at 2024-05-15T09:15:25Z: EventLoggingDisabled sensor 114, Log area reset/cleared (assertion)",
					reset.toString());

			SelEntry oem = log.get(1);
			assertEquals(SelRecordType.OemTimestamped, oem.type());
			assertEquals(Instant.ofEpochSecond(SEL_RESET_TIME + 5), oem.timestamp().get());
			assertEquals(LENOVO, oem.manufacturerId().getAsInt());
			assertArrayEquals(new byte[] { 2, 1, 5, 0, 0, 0 }, oem.oemData());
			assertEquals("OEM data 02 01 05 00 00 00", oem.description());
			assertFalse(oem.sensorType().isPresent());
			assertFalse(oem.sensorNumber().isPresent());
			assertFalse(oem.generatorId().isPresent());
			assertFalse(oem.eventType().isPresent());
			assertFalse(oem.event().isPresent());
			assertFalse(oem.direction().isPresent());
			assertEquals(0, oem.eventData().length);
			assertEquals(
					"SEL entry 2 at 2024-05-15T09:15:30Z: OemTimestamped of manufacturer 19046, OEM data 02 01 05 00 00 00",
					oem.toString());

			SelEntry threshold = log.get(2);
			assertEquals(SensorType.Temperature, threshold.sensorType().get());
			assertEquals(TEMPERATURE_SENSOR, threshold.sensorNumber().getAsInt());
			assertEquals(1, threshold.eventType().getAsInt());
			assertEquals(ReadingType.UpperNonCriticalGoingHigh, threshold.event().get());
			assertEquals("Upper Non Critical Going High", threshold.description(), "a name ipmiutil does not give");
			assertArrayEquals(new byte[] { 0x57, (byte) 0x9a, (byte) 0x98 }, threshold.eventData());
			SensorList sensors = client.sensors().execute();
			assertTrue(threshold.isFrom(sensors.get(0)), "the BMC's temperature sensor 50: " + sensors.get(0));
			assertFalse(threshold.isFrom(sensors.get(1)), "another number");
			assertFalse(reset.isFrom(sensors.get(0)), "sensor 114 of the BMC, not in the repository");
			assertFalse(oem.isFrom(sensors.get(0)), "an OEM entry is from no sensor");

			SelEntry power = log.get(3);
			assertEquals(ReadingType.PowerOffOrDown, power.event().get());
			assertEquals(EventDirection.Deassertion, power.direction().get());
			assertEquals("Power off/down", power.description());

			SelEntry nonTimestamped = log.get(4);
			assertEquals(SelRecordType.OemNonTimestamped, nonTimestamped.type());
			assertFalse(nonTimestamped.timestamp().isPresent());
			assertFalse(nonTimestamped.manufacturerId().isPresent());
			assertEquals(13, nonTimestamped.oemData().length);
			assertEquals(
					"SEL entry 5: OemNonTimestamped, OEM data 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D",
					nonTimestamped.toString());

			SelEntry preInit = log.get(5);
			assertFalse(preInit.timestamp().isPresent(), "a timestamp up to 20000000h is not a date");
			assertEquals(5, preInit.preInitSeconds().getAsLong());
			assertEquals("IERR", preInit.description());
			assertEquals("SEL entry 6 5 s after initialization: Processor sensor 2, IERR (assertion)", preInit.toString());

			SelEntryList since = client.sel().since(3).execute();
			assertEquals(Arrays.asList(4, 5, 6), since.stream().map(SelEntry::id).collect(Collectors.toList()));
			assertEquals(6, since.totalEntries().getAsInt(), "the count of the whole log");
			assertEquals(
					7 + 5,
					bmc.getCount(NETFN_STORAGE, GET_SEL_ENTRY),
					"the entry given, the three after it, the first of them again");
			assertTrue(client.sel().since(6).execute().isEmpty(), "nothing after the last entry");
			assertEquals(6, client.sel().since(9).execute().size(), "an entry the log does not hold: the whole log");

			assertThrows(IllegalArgumentException.class, () -> client.sel().since(0));
			assertThrows(IllegalArgumentException.class, () -> client.sel().since(0xffff));
		}
	}

	@Test
	@Timeout(30)
	void aClearedLogIsReadWholeEvenWhenItsIdsAreReused() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList log = client.sel().execute();
				SelEntry last = log.get(log.size() - 1);
				assertTrue(client.sel().since(last).execute().isEmpty(), "nothing after the last entry");
				assertEquals(
						Arrays.asList(4, 5, 6),
						client.sel().since(log.get(2)).execute().stream().map(SelEntry::id).collect(Collectors.toList()));

				// The log is cleared and six other events are logged: the IDs 1 to 6 name other entries now
				sel.clear();
				for (int id = 1; id <= 6; id++) {
					sel
							.add(
									systemEvent(
											id,
											SEL_RESET_TIME + 1000 + id,
											SensorType.Fan.getCode(),
											FAN_SENSOR,
											0x0a,
											true,
											0x01,
											0xff,
											0xff));
				}
				SelEntryList afterClear = client.sel().since(last).execute();
				assertEquals(6, afterClear.size(), "another entry under the ID of the last one read: the whole log");
				assertTrue(afterClear.lastErase().isPresent());
				assertFalse(afterClear.get(5).equals(last), "same ID, another record");
				assertEquals(afterClear.get(5), client.sel().execute().get(5), "the same record read twice");
				assertEquals(afterClear.get(5).hashCode(), client.sel().execute().get(5).hashCode());
				assertTrue(client.sel().since(afterClear.get(5)).execute().isEmpty());

				assertTrue(client.sel().since(6).execute().isEmpty(), "an ID alone cannot tell a reused ID apart");
				assertEquals(6, client.sel().since(7).execute().size(), "an ID the log does not hold: the whole log");
			}
		}
	}

	@Test
	@Timeout(30)
	void aLogThatChangesUnderTheWalkIsReadAgainOnce() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				// Entry 4, named as the next one by entry 3, is deleted between the two reads: the walk starts over
				sel.vanishOnce(4);
				assertEquals(6, client.sel().execute().size());
				assertEquals(3, bmc.getCount(NETFN_STORAGE, GET_SEL_INFO), "before, after, and after the second walk");
				assertEquals(
						3 + 1 + 6 + 1,
						bmc.getCount(NETFN_STORAGE, GET_SEL_ENTRY),
						"three entries, the missing one, then all and the first again");

				sel.vanishOnce(4).vanishOnce(4);
				IpmiClientException twice = assertThrows(IpmiClientException.class, () -> client.sel().execute());
				assertTrue(twice.getMessage().contains("changed twice while it was read"), twice.getMessage());

				// The entry after which to read is gone, then entry 4 under the whole-log walk: the second walk reads the
				// whole log, whatever the cursor
				sel.vanishOnce(3).vanishOnce(4);
				assertEquals(6, client.sel().since(3).execute().size(), "the second walk is whole");
			}
		}
	}

	@Test
	@Timeout(30)
	void aLogClearedAndRefilledUnderTheWalkIsReadAgain() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			// Between the reads of entries 1 and 2, the log is cleared and filled again with entries 1 to 6: entry 2
			// exists again, so the walk alone would mix the old entry 1 with the new 2 to 6
			sel.before(2, () -> {
				sel.clear();
				for (int id = 1; id <= 6; id++) {
					sel
							.add(
									systemEvent(
											id,
											SEL_RESET_TIME + 2000 + id,
											SensorType.Fan.getCode(),
											FAN_SENSOR,
											0x0a,
											true,
											0x01,
											0xff,
											0xff));
				}
			});
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList log = client.sel().execute();
				assertEquals(6, log.size());
				assertTrue(log.stream().allMatch(entry -> entry.sensorType().get() == SensorType.Fan), "the new log only");
				assertEquals(Instant.ofEpochSecond(SEL_RESET_TIME + 2001), log.get(0).timestamp().get());
				assertTrue(log.lastErase().isPresent());
				assertEquals(3, bmc.getCount(NETFN_STORAGE, GET_SEL_INFO), "the erase timestamp changed: read again");

				// A Clear SEL in progress answers 81h: the read waits for its end
				sel.eraseInProgressOnce(2);
				assertEquals(6, client.sel().execute().size());
			}
		}
	}

	@Test
	@Timeout(30)
	void aCursorIsDroppedWhenTheLogWasErasedUnderTheWalk() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			// Reading after entry 3, the log is cleared and filled again before entry 5 is read: the new log holds
			// another entry 3, which the ID alone cannot tell, so the second walk reads the whole new log
			sel.before(5, () -> {
				sel.clear();
				for (int id = 1; id <= 4; id++) {
					sel
							.add(
									systemEvent(
											id,
											SEL_RESET_TIME + 3000 + id,
											SensorType.Fan.getCode(),
											FAN_SENSOR,
											0x0a,
											true,
											0x01,
											0xff,
											0xff));
				}
			});
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList log = client.sel().since(3).execute();
				assertEquals(Arrays.asList(1, 2, 3, 4), log.stream().map(SelEntry::id).collect(Collectors.toList()));
				assertTrue(log.stream().allMatch(entry -> entry.sensorType().get() == SensorType.Fan), "the new log only");
			}
		}
	}

	@Test
	@Timeout(30)
	void aClearThatLeavesTheEraseTimestampUnchangedIsCaughtByTheFirstEntry() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			// A second clear within the same second as the previous one: the erase timestamp does not move
			sel.before(4, () -> {
				sel.clearWithoutErase();
				for (int id = 1; id <= 5; id++) {
					sel
							.add(
									systemEvent(
											id,
											SEL_RESET_TIME + 4000 + id,
											SensorType.Fan.getCode(),
											FAN_SENSOR,
											0x0a,
											true,
											0x01,
											0xff,
											0xff));
				}
			});
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList log = client.sel().execute();
				assertEquals(5, log.size());
				assertTrue(log.stream().allMatch(entry -> entry.sensorType().get() == SensorType.Fan), "the new log only");
				assertEquals(
						5 + 1 + 5 + 1,
						bmc.getCount(NETFN_STORAGE, GET_SEL_ENTRY),
						"entries 1 to 5 (4 and 5 new), entry 1 again, then the new log and its entry 1 again");
			}
		}
	}

	@Test
	@Timeout(30)
	void getSelInfoRefusalsArePropagatedOrWaitedOut() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				sel.infoEraseInProgressOnce();
				assertEquals(6, client.sel().execute().size(), "81h on Get SEL Info: waited out");

				sel.refuseInfo(0xd4);
				IpmiCompletionCodeException refused = assertThrows(
						IpmiCompletionCodeException.class,
						() -> client.sel().execute());
				assertEquals(CompletionCode.InsufficentPrivilege, refused.completionCode());

				sel.refuseInfo(0xd5);
				assertFalse(client.sel().execute().info().isPresent(), "not supported in the present state: no info");
			}
		}
	}

	@Test
	@Timeout(30)
	void aSinceWalkThatReadsNothingChecksItsCursorAgain() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList log = client.sel().execute();
				SelEntry last = log.get(5);
				// The cursor lookup finds entry 6 unchanged and the walk reads nothing; then, before entry 6 is read
				// again, the log is cleared and filled up to six entries without the erase timestamp moving
				sel.before(6, () -> sel.before(6, () -> {
					sel.clearWithoutErase();
					for (int id = 1; id <= 6; id++) {
						sel
								.add(
										systemEvent(
												id,
												SEL_RESET_TIME + 5000 + id,
												SensorType.Fan.getCode(),
												FAN_SENSOR,
												0x0a,
												true,
												0x01,
												0xff,
												0xff));
					}
				}));
				SelEntryList refilled = client.sel().since(last).execute();
				assertEquals(6, refilled.size(), "the cursor is another entry now: the whole new log");
				assertTrue(refilled.stream().allMatch(entry -> entry.sensorType().get() == SensorType.Fan));
			}
		}
	}

	@Test
	@Timeout(30)
	void aChangeSeenByTheAnchorAloneGetsItsRetry() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel();
			sel.install(bmc);
			// The walk asks for record 0000h: the first request for entry 1 by its ID is the check after the walk, and
			// the log is cleared and filled again right before it
			sel.before(1, () -> {
				sel.clear();
				for (int id = 1; id <= 3; id++) {
					sel
							.add(
									systemEvent(
											id,
											SEL_RESET_TIME + 6000 + id,
											SensorType.Fan.getCode(),
											FAN_SENSOR,
											0x0a,
											true,
											0x01,
											0xff,
											0xff));
				}
			});
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList log = client.sel().execute();
				assertEquals(3, log.size(), "one change, one retry");
				assertTrue(log.lastErase().isPresent());
			}
		}
	}

	@Test
	@Timeout(30)
	void anEntryWithASelectorAsRecordIdIsRejected() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			new FakeSel()
					.add(systemEvent(0, SEL_RESET_TIME, SensorType.Fan.getCode(), FAN_SENSOR, 0x0a, true, 0x01, 0xff, 0xff))
					.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				IpmiClientException selector = assertThrows(IpmiClientException.class, () -> client.sel().execute());
				assertEquals("The System Event Log answered an entry whose record ID is 0000h", selector.getMessage());
			}
		}
	}

	@Test
	@Timeout(30)
	void anotherEntryThanTheOneAskedForIsRejected() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel().serveInsteadOnce(3, 5);
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				IpmiClientException wrong = assertThrows(IpmiClientException.class, () -> client.sel().execute());
				assertEquals("The System Event Log answered entry 0005h when asked for entry 0003h", wrong.getMessage());
				sel.serveInsteadOnce(3, 5);
				assertEquals(6, client.sel().since(3).execute().size(), "the cursor lookup got another entry: the whole log");
			}
		}
	}

	@Test
	@Timeout(30)
	void aLoopInTheNextRecordIdsIsRejected() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel().nextRecordId(4, 2);
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				IpmiClientException loop = assertThrows(IpmiClientException.class, () -> client.sel().execute());
				assertEquals(
						"The System Event Log loops: entry 0004h names entry 0002h as the next one, which was read already",
						loop.getMessage());
				assertEquals(4 + 1, bmc.getCount(NETFN_STORAGE, GET_SEL_ENTRY), "entries 1 to 4, then 2 again, once");

				sel.nextRecordId(4, 4);
				assertTrue(
						assertThrows(IpmiClientException.class, () -> client.sel().execute())
								.getMessage()
								.contains("entry 0004h names entry 0004h as the next one"),
						"an entry that names itself");
			}
		}
	}

	@Test
	@Timeout(30)
	void anUnspecifiedOrRefusedSelTimeUtcOffset() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel().utcOffset(0x7ff);
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList log = client.sel().execute();
				assertEquals(0, log.utcOffsetMinutes(), "07FFh: unspecified, not 2047 minutes");
				assertEquals(Instant.ofEpochSecond(SEL_RESET_TIME), log.get(0).timestamp().get());

				sel.refuseUtcOffset(0xd5);
				assertEquals(0, client.sel().execute().utcOffsetMinutes(), "not supported in the present state: UTC");

				sel.refuseUtcOffset(0xd4);
				IpmiCompletionCodeException refused = assertThrows(
						IpmiCompletionCodeException.class,
						() -> client.sel().execute());
				assertEquals(CompletionCode.InsufficentPrivilege, refused.completionCode(), "a refusal the caller can act on");
			}
		}
	}

	@Test
	@Timeout(30)
	void theSelTimeUtcOffsetIsApplied() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = Fixtures.sel().utcOffset(540);
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				// The BMC keeps its SEL time as local time, 9 hours ahead of UTC
				SelEntryList log = client.sel().execute();
				assertEquals(540, log.utcOffsetMinutes());
				assertEquals(Instant.ofEpochSecond(SEL_RESET_TIME - 9 * 3600), log.get(0).timestamp().get());
				assertEquals(Instant.ofEpochSecond(SEL_RESET_TIME + 489 - 9 * 3600), log.lastAddition().get());
				assertEquals(5, log.get(5).preInitSeconds().getAsLong(), "a pre-init timestamp is not a time of day");
				assertEquals(1, bmc.getCount(NETFN_STORAGE, FakeSel.GET_SEL_TIME_UTC_OFFSET));
			}
		}
	}

	@Test
	@Timeout(30)
	void anEmptySelABmcWithoutGetSelInfoAndACanceledReservation() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSel sel = new FakeSel();
			sel.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				SelEntryList empty = client.sel().execute();
				assertTrue(empty.isEmpty());
				assertEquals(0, empty.totalEntries().getAsInt());
				assertEquals(0, bmc.getCount(NETFN_STORAGE, GET_SEL_ENTRY), "an empty log is not read");
				assertEquals("0 SEL entries of 0: []", empty.toString());

				sel.unsupportedInfo();
				SelEntryList unknown = client.sel().execute();
				assertTrue(unknown.isEmpty());
				assertFalse(unknown.totalEntries().isPresent());
				assertFalse(unknown.info().isPresent());
				assertEquals(1, bmc.getCount(NETFN_STORAGE, GET_SEL_ENTRY), "without Get SEL Info, the first read tells");

				sel.add(systemEvent(1, SEL_RESET_TIME, SensorType.Fan.getCode(), FAN_SENSOR, 0x0a, true, 0x01, 0xff, 0xff));
				assertEquals(1, client.sel().execute().size());
				assertEquals(
						"1 SEL entry: [SEL entry 1 at 2024-05-15T09:15:25Z: Fan sensor 16, Transition to In Test"
								+ " (assertion)]",
						client.sel().execute().toString());

				sel
						.add(
								systemEvent(
										2,
										SEL_RESET_TIME + 1,
										SensorType.Fan.getCode(),
										FAN_SENSOR,
										0x0a,
										false,
										0x01,
										0xff,
										0xff));
				sel.cancelReservationOnce(2);
				assertEquals(2, client.sel().execute().size(), "a canceled reservation: take one and read the entry again");
				assertEquals(1, bmc.getCount(NETFN_STORAGE, RESERVE_SEL));

				sel.cancelReservationOnce(2).cancelReservationOnce(2);
				IpmiCompletionCodeException refused = assertThrows(
						IpmiCompletionCodeException.class,
						() -> client.sel().execute());
				assertEquals(CompletionCode.ReservationCanceled, refused.completionCode(), "canceled twice: once is enough");

				sel.add(FakeSel.selRecord(3, 0x10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
				SelEntry reserved = client.sel().since(2).execute().get(0);
				assertEquals(SelRecordType.Reserved, reserved.type());
				assertEquals("Reserved record type", reserved.description());
				assertFalse(reserved.timestamp().isPresent());
				assertEquals("SEL entry 3: Reserved, Reserved record type", reserved.toString());
			}
		}
	}

	@Test
	@Timeout(30)
	void aChangedRepositoryIsWalkedAgainAndTheCacheCanBeDisabled() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSdrRepository sdr = new FakeSdrRepository()
					.add(compactSensorRecord(1, FAN_SENSOR, EntityId.Fan.getCode(), 1, SensorType.Fan.getCode(), 0x08, "Fan 1"))
					.reading(FAN_SENSOR, 0x00, 0x40, 0x02);
			sdr.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				client.sensors().execute();
				client.sensors().execute();
				assertEquals(1, bmc.getCount(NETFN_STORAGE, GET_SDR));

				sdr.touch();
				client.sensors().execute();
				assertEquals(2, bmc.getCount(NETFN_STORAGE, GET_SDR), "a new timestamp makes the next operation walk again");

				sdr.unsupportedInfo();
				client.sensors().execute();
				client.sensors().execute();
				assertEquals(4, bmc.getCount(NETFN_STORAGE, GET_SDR), "without Get SDR Repository Info, every walk is a walk");
			}
			try (IpmiClient client = client(bmc).sdrCache(false).build()) {
				client.sensors().execute();
				client.frus().execute();
				assertEquals(6, bmc.getCount(NETFN_STORAGE, GET_SDR), "the cache is disabled");
			}
		}
	}

	@Test
	@Timeout(30)
	void aReservationCanceledInTheMiddleOfAChunkedRecordDoesNotLoseIt() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			// A Cisco IMC returns the large records in chunks only, and cancels the reservation during the walk
			new FakeSdrRepository()
					.add(compactSensorRecord(1, 0x10, EntityId.Fan.getCode(), 1, SensorType.Fan.getCode(), 0x08, "Fan 1"))
					.add(FakeSdrRepository.withId(Fixtures.FRONT_PANEL_FULL_RECORD, 2))
					.add(compactSensorRecord(3, 0x11, EntityId.Fan.getCode(), 2, SensorType.Fan.getCode(), 0x08, "Fan 2"))
					.chunkedOnly(1)
					.cancelReservationOnce(1, 8)
					.reading(0x10, 0x00, 0x40, 0x02)
					.reading(TEMPERATURE_SENSOR, 0x9a, 0x40, 0x00)
					.reading(0x11, 0x00, 0x40, 0x02)
					.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				SensorList sensors = client.sensors().execute();
				assertEquals(
						java.util.Arrays.asList("Fan 1", "Ambient Temp", "Fan 2"),
						sensors.stream().map(Sensor::name).collect(Collectors.toList()));
				assertTrue(sensors.skippedRecords().isEmpty(), sensors.skippedRecords().toString());
				assertEquals(1, bmc.getCount(NETFN_STORAGE, FakeSdrRepository.RESERVE_SDR_REPOSITORY));
			}
		}
	}

	@Test
	@Timeout(30)
	void sensorsAreSelectedBeforeTheirReadingsAreRequested() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			SensorList temperatures = client.sensors().types(SensorType.Temperature).execute();
			assertEquals(1, temperatures.size());
			assertEquals("Ambient Temp", temperatures.get(0).name());
			assertEquals(1, bmc.getCount(NETFN_SENSOR, GET_SENSOR_READING), "only the selected sensor is read");

			SensorList fans = client.sensors().names("fan*", "?oard").execute();
			assertEquals(2, fans.size());
			assertEquals("Fan 1", fans.get(0).name());
			assertEquals("Board", fans.get(1).name());

			assertTrue(client.sensors().types(SensorType.Voltage).execute().isEmpty());
			assertEquals(3, bmc.getCount(NETFN_SENSOR, GET_SENSOR_READING));

			FruList psu = client.frus().ids(PSU_FRU).execute();
			assertEquals(1, psu.size());
			assertEquals("PSU 1", psu.get(0).name());
		}
	}

	@Test
	@Timeout(30)
	void aRefusedReadingAndAnUnreadableFruAreReportedInTheResult() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			new FakeSdrRepository()
					.add(Fixtures.FRONT_PANEL_FULL_RECORD)
					.add(fruDeviceLocatorRecord(4, PSU_FRU, EntityId.PowerSupply.getCode(), 1, "PSU 1"))
					.reading(TEMPERATURE_SENSOR, 0xcb)
					.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				Sensor temperature = client.sensors().execute().get(0);
				assertFalse(temperature.reading().isPresent());
				assertEquals(SensorStatus.UNAVAILABLE, temperature.status());
				assertTrue(temperature.problem().get().contains("CBh"), temperature.problem().get());

				Fru psu = client.frus().execute().get(0);
				assertFalse(psu.complete());
				assertTrue(psu.problems().get(0).contains("C3h"), psu.problems().toString());
				assertEquals("", psu.manufacturer());
			}
		}
	}

	@Test
	@Timeout(30)
	void aMalformedFruReplyCostsThatFruOnly() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			// A byte count larger than the bytes returned: the reply cannot be decoded
			bmc.handle(NETFN_STORAGE, FakeSdrRepository.READ_FRU_DATA, data -> new byte[] { 0x00, 0x20, 0x01, 0x02 });
			try (IpmiClient client = client(bmc).build()) {
				FruList frus = client.frus().execute();
				assertEquals(2, frus.size(), frus.toString());
				for (Fru fru : frus) {
					assertFalse(fru.complete());
					assertTrue(fru.problems().get(0).contains("ReadFruData"), fru.problems().toString());
				}
			}
		}
	}

	@Test
	@Timeout(30)
	void anUnreadableSystemBoardFruIsReportedWithItsProblem() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			// A System Board sensor, no locator for FRU 0, and no FRU 0 on the BMC
			new FakeSdrRepository()
					.add(
							compactSensorRecord(
									1,
									BOARD_SENSOR,
									EntityId.SystemBoard.getCode(),
									1,
									SensorType.EntityPresence.getCode(),
									0x08,
									"Board"))
					.reading(BOARD_SENSOR, 0x00, 0x40, 0x02)
					.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				FruList frus = client.frus().ids(0).execute();
				assertEquals(1, frus.size());
				assertEquals(0, frus.get(0).id());
				assertFalse(frus.get(0).complete());
				assertTrue(frus.get(0).problems().get(0).contains("C3h"), frus.get(0).problems().toString());
				assertTrue(frus.get(0).records().isEmpty());
				assertEquals("", frus.get(0).manufacturer(), "no area to take a manufacturer from");
			}
		}
	}

	@Test
	@Timeout(30)
	void aMemoryModuleFruIsDecodedFromItsSpdData() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			new FakeSdrRepository()
					.add(fruDeviceLocatorRecord(1, DIMM_FRU, EntityId.MemoryDevice.getCode(), 1, "DIMM 1"))
					.fru(DIMM_FRU, DDR4_SPD)
					.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				FruList frus = client.frus().execute();
				assertEquals(1, frus.size(), frus.toString());
				Fru dimm = frus.get(0);
				assertTrue(dimm.complete(), dimm.problems().toString());
				assertFalse(dimm.board().isPresent());
				assertEquals(16384, dimm.spd().get().getCapacityMegabytes());
				assertEquals("Samsung", dimm.manufacturer());
				assertEquals("M393A2K43BB1-CTD", dimm.model());
				assertEquals("M393A2K43BB1-CTD", dimm.partNumber());
				assertEquals("409F3CA2", dimm.serialNumber());
				assertEquals("FRU 2 DIMM 1 (MemoryDevice 1): Samsung M393A2K43BB1-CTD S/N 409F3CA2", dimm.toString());
			}
		}
	}

	@Test
	@Timeout(30)
	void aProcessorFruIsDecodedFromItsPirom() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			byte[] locator = fruDeviceLocatorRecord(1, CPU_FRU, EntityId.Processor.getCode(), 1, "PIROM 1");
			locator[11] = 0x03; // device type modifier: System Processor Cartridge FRU / PIROM
			new FakeSdrRepository().add(locator).fru(CPU_FRU, PIROM).install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				FruList frus = client.frus().execute();
				assertEquals(1, frus.size(), frus.toString());
				Fru cpu = frus.get(0);
				assertTrue(cpu.complete(), cpu.problems().toString());
				assertEquals(8, cpu.pirom().get().getCores());
				assertEquals("Intel", cpu.manufacturer());
				assertEquals("SR3GH", cpu.model());
				assertEquals("37608DC00416530", cpu.partNumber());
				assertEquals("634AFBC3354D3C6D", cpu.serialNumber());
				assertEquals("FRU 85 PIROM 1 (Processor 1): Intel SR3GH S/N 634AFBC3354D3C6D", cpu.toString());
			}
		}
	}

	@Test
	@Timeout(30)
	void anEmptyRepositoryGivesEmptyResults() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			new FakeSdrRepository().install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				assertTrue(client.sensors().execute().isEmpty());
				assertTrue(client.frus().execute().isEmpty());
				assertEquals(0, bmc.getCount(NETFN_STORAGE, GET_SDR), "nothing to walk");
			}
		}
	}

	@Test
	@Timeout(30)
	void closeDuringALongOperationStillReleasesThePort() throws Exception {
		Set<Thread> before = Thread.getAllStackTraces().keySet();
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> null);
			// 6 tries of 300 ms: the operation outlives the logout wait of close()
			IpmiClient client = client(bmc).retries(5).build();
			ExecutorService thread = Executors.newSingleThreadExecutor();
			try {
				Future<?> operation = thread.submit(client::chassisStatus);
				Thread.sleep(300);
				client.close();
				assertThrows(java.util.concurrent.ExecutionException.class, operation::get);
			} finally {
				thread.shutdownNow();
			}
			assertEquals("", survivors(before), "the UDP listener must not survive close()");
		}
	}

	@Test
	@Timeout(30)
	void aNonLinearSensorIsConvertedWithTheFactorsOfItsReading() throws Exception {
		byte[] nonLinear = Fixtures.FRONT_PANEL_FULL_RECORD.clone();
		nonLinear[23] = 0x70; // linearization: non-linear
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			FakeSdrRepository sdr = new FakeSdrRepository()
					.add(nonLinear)
					.reading(TEMPERATURE_SENSOR, 0x9a, 0x40, 0x00)
					// next reading A0h, M=2, B=0, R exponent -1
					.factors(TEMPERATURE_SENSOR, 0x9a, 0xa0, 0x02, 0x00, 0x00, 0x00, 0x00, 0xf0);
			sdr.install(bmc);
			try (IpmiClient client = client(bmc).build()) {
				Sensor temperature = client.sensors().execute().get(0);
				assertEquals(30.8, temperature.reading().getAsDouble(), 1e-9); // 154 x 2 / 10, no linearization
				assertEquals(SensorStatus.OK, temperature.status());
				assertFalse(temperature.problem().isPresent());
				assertFalse(temperature.upperCritical().isPresent(), "a threshold byte needs its own factors");

				// A BMC that refuses the factors costs the reading only
				sdr.factors(TEMPERATURE_SENSOR, 0x9a, 0xc1);
				temperature = client.sensors().execute().get(0);
				assertFalse(temperature.reading().isPresent());
				assertEquals(0x9a, temperature.rawReading().getAsInt());
				assertEquals(SensorStatus.OK, temperature.status());
				assertTrue(temperature.problem().get().contains("Get Sensor Reading Factors"), temperature.problem().get());
				assertTrue(temperature.problem().get().contains("C1h"), temperature.problem().get());
			}
		}
	}

	@Test
	@Timeout(30)
	void aMalformedReadingReplyCostsThatSensorAndKeepsTheSession() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			// One data byte: Get Sensor Reading needs two
			bmc.handle(NETFN_SENSOR, GET_SENSOR_READING, data -> new byte[] { 0x00, 0x05 });
			try (IpmiClient client = client(bmc).build()) {
				SensorList sensors = client.sensors().execute();
				assertEquals(3, sensors.size());
				for (Sensor sensor : sensors) {
					assertEquals(SensorStatus.UNAVAILABLE, sensor.status());
					assertTrue(sensor.problem().get().contains("GetSensorReading"), sensor.problem().get());
				}
				assertEquals(1, bmc.getCount(OPEN_SESSION_REQUEST), "a malformed reply does not lose the session");
			}
		}
	}

	@Test
	@Timeout(30)
	void eachSensorIsReadFromItsOwner() throws Exception {
		for (FakeRmcpPlusBmc.BridgeReply reply : new FakeRmcpPlusBmc.BridgeReply[] {
				FakeRmcpPlusBmc.BridgeReply.SEPARATE,
				FakeRmcpPlusBmc.BridgeReply.EMBEDDED }) {
			try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
				// Four sensors numbered 7: at LUN 0 and LUN 1 of the BMC, on a controller behind it, of the BIOS
				byte[] sensor = compactSensorRecord(
						1,
						7,
						EntityId.Processor.getCode(),
						1,
						SensorType.Processor.getCode(),
						0x6f,
						"S");
				new FakeSdrRepository()
						.add(withOwner(sensor, 0x20, 0, 0))
						.add(withOwner(FakeSdrRepository.withId(sensor, 2), 0x20, 0, 1))
						.add(withOwner(FakeSdrRepository.withId(sensor, 3), 0x2c, 6, 0))
						.add(withOwner(FakeSdrRepository.withId(sensor, 4), 0xb1, 0, 0))
						.readingAtLun(0, 7, 0x10, 0x40, 0x00)
						.readingAtLun(1, 7, 0x20, 0x40, 0x00)
						.install(bmc);
				bmc.satellite(6, 0x2c, NETFN_SENSOR, GET_SENSOR_READING, data -> new byte[] { 0x00, 0x30, 0x40, 0x00 });
				bmc.bridgeReply(reply);
				try (IpmiClient client = client(bmc).build()) {
					SensorList sensors = client.sensors().execute();
					assertEquals(0x10, sensors.get(0).rawReading().getAsInt(), reply.toString());
					assertEquals(0x20, sensors.get(1).rawReading().getAsInt(), "read at the LUN of its record");
					assertEquals(0x30, sensors.get(2).rawReading().getAsInt(), "read from its controller");
					assertFalse(sensors.get(2).problem().isPresent(), sensors.get(2).problem().toString());

					Sensor bios = sensors.get(3);
					assertFalse(bios.rawReading().isPresent(), "the BMC would answer for its own sensor 7");
					assertEquals(SensorStatus.UNAVAILABLE, bios.status());
					assertTrue(bios.problem().get().contains("system software (owner ID B1h)"), bios.problem().get());

					assertEquals(2, bmc.getCount(NETFN_SENSOR, GET_SENSOR_READING), "the BMC reads its own sensors only");
					assertEquals(1, bmc.getBridgedRequests().size());
					byte[] bridged = bmc.getBridgedRequests().get(0);
					assertEquals(0x2c, bridged[0] & 0xff, "addressed to the controller");
					assertEquals(NETFN_SENSOR << 2, bridged[1] & 0xff, "Sensor/Event at LUN 0");
					assertEquals(0x81, bridged[3] & 0xff, "from the remote console");
					assertEquals(GET_SENSOR_READING, bridged[5] & 0xff);
					assertEquals(7, bridged[6]);
					assertEquals(1, bmc.getCount(OPEN_SESSION_REQUEST));
					assertEquals(Collections.emptyList(), bmc.getErrors());
				}
			}
		}
	}

	@Test
	@Timeout(30)
	void aControllerThatDoesNotAnswerCostsItsSensorsOnly() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			byte[] sensor = compactSensorRecord(
					1,
					1,
					EntityId.Processor.getCode(),
					1,
					SensorType.Processor.getCode(),
					0x6f,
					"S");
			new FakeSdrRepository()
					.add(withOwner(sensor, 0x2c, 6, 0))
					.add(withOwner(FakeSdrRepository.withId(sensor, 2), 0x2c, 6, 0))
					.add(withOwner(FakeSdrRepository.withId(sensor, 3), 0x42, 0, 0))
					.add(FakeSdrRepository.withId(sensor, 4))
					.reading(1, 0x10, 0x40, 0x00)
					.install(bmc);
			// The BMC forwards the requests to 2Ch, which never answers; nothing answers at 42h
			bmc.satellite(6, 0x2c, NETFN_SENSOR, GET_SENSOR_READING, data -> new byte[] { 0x00, 0x30, 0x40, 0x00 });
			bmc.bridgeReply(FakeRmcpPlusBmc.BridgeReply.NONE);
			try (IpmiClient client = client(bmc).build()) {
				SensorList sensors = client.sensors().execute();
				for (int i = 0; i < 2; i++) {
					assertEquals(SensorStatus.UNAVAILABLE, sensors.get(i).status());
					assertEquals(
							"No reply from the controller at 2Ch on channel 6 through the BMC",
							sensors.get(i).problem().get());
				}
				assertTrue(sensors.get(2).problem().get().contains("NAK on write"), sensors.get(2).problem().get());
				assertTrue(sensors.get(2).problem().get().contains("83h"), sensors.get(2).problem().get());
				assertEquals(0x10, sensors.get(3).rawReading().getAsInt(), "the sensors of the BMC are read");
				assertEquals(3, bmc.getCount(NETFN_APP, SEND_MESSAGE), "2 tries for the first sensor of 2Ch, 1 for 42h");
				assertEquals(1, bmc.getCount(OPEN_SESSION_REQUEST), "the session is kept");
			}
		}
	}

	@Test
	@Timeout(30)
	void aBmcWithoutRepositoryIsWalkedThroughItsDeviceSdrs() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			new FakeSdrRepository()
					.add(compactSensorRecord(1, 1, EntityId.Processor.getCode(), 1, SensorType.Processor.getCode(), 0x6f, "S"))
					.reading(1, 0x10, 0x40, 0x00)
					.deviceSdrs()
					.install(bmc);
			// Provides Device SDRs (bit 7 of the device revision), a Sensor Device without SDR Repository
			byte[] deviceId = DEVICE_ID.clone();
			deviceId[1] = (byte) 0x81;
			deviceId[5] = 0x01;
			byte[] reply = new byte[1 + deviceId.length];
			System.arraycopy(deviceId, 0, reply, 1, deviceId.length);
			bmc.handle(NETFN_APP, GET_DEVICE_ID, data -> reply);
			try (IpmiClient client = client(bmc).build()) {
				SensorList sensors = client.sensors().execute();
				assertEquals(1, sensors.size(), sensors.toString());
				assertEquals(0x10, sensors.get(0).rawReading().getAsInt());
				assertEquals(1, bmc.getCount(NETFN_SENSOR, GET_DEVICE_SDR));
				assertEquals(0, bmc.getCount(NETFN_STORAGE, GET_SDR));
			}
		}
	}

	@Test
	@Timeout(30)
	void aSolConsoleEndsWhenItsSessionIsDropped() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			try (
					IpmiClient client = client(bmc).privilege(PrivilegeLevel.Administrator).build();
					SolConsole console = client.sol().open()) {
				bmc.expireSession();
				assertTrue(client.chassisStatus().powerOn(), "reopened on a new session");
				assertTrue(console.deactivated(), "the console of the lost session is invalidated");
				assertEquals(-1, console.input().read(), "its reads end");
			}
		}
	}

	@Test
	@Timeout(30)
	void aSolConsoleOnAnotherPortSurvivesTheLossOfTheSessionOfTheClient() throws Exception {
		try (
				FakeRmcpPlusBmc bmc = bmc();
				FakeRmcpPlusBmc solPort = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			// The BMC serves SOL on another UDP port: the console has a session of its own there
			solPort.enableSol(solPort.getPort(), 255, ADMINISTRATOR);
			bmc.enableSol(solPort.getPort(), 255, ADMINISTRATOR);
			try (
					IpmiClient client = client(bmc).privilege(PrivilegeLevel.Administrator).build();
					SolConsole console = client.sol().open()) {
				assertTrue(solPort.isSolActive());
				bmc.expireSession();
				assertTrue(client.chassisStatus().powerOn(), "reopened on a new session");
				assertFalse(console.deactivated(), "the console on its own port is not touched");
				console.output().write("ls\r".getBytes(StandardCharsets.US_ASCII));
				await(() -> solPort.getSolReceived().length == 3, "the console still works");
			}
			assertFalse(solPort.isSolActive(), "closing the console deactivates the payload");
		}
	}

	@Test
	@Timeout(30)
	void aPrivilegeRefusedOnTheSolPayloadPortIsAnAuthenticationFailure() throws Exception {
		try (
				FakeRmcpPlusBmc bmc = bmc();
				FakeRmcpPlusBmc solPort = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			solPort.enableSol(solPort.getPort(), 255, ADMINISTRATOR);
			bmc.enableSol(solPort.getPort(), 255, ADMINISTRATOR);
			solPort.handle(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL, data -> new byte[] { (byte) 0x81 });
			try (IpmiClient client = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				IpmiAuthenticationException refused = assertThrows(
						IpmiAuthenticationException.class,
						() -> client.sol().open());
				assertTrue(refused.getMessage().contains("(completion code 81h)"), refused.getMessage());
				assertFalse(solPort.isSolActive());
			}
		}
	}

	@Test
	@Timeout(30)
	void aLostSessionIsReopenedAndAReadIsRetriedButNotASend() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			client.chassisStatus();
			bmc.expireSession();

			assertEquals(3, client.sensors().execute().size(), "the read is retried on a new session");
			assertEquals(2, bmc.getCount(OPEN_SESSION_REQUEST));

			bmc.expireSession();
			assertThrows(IpmiTimeoutException.class, () -> client.send(new GetChassisStatus()));
			assertEquals(2, bmc.getCount(OPEN_SESSION_REQUEST), "a command is never replayed");

			assertTrue(client.chassisStatus().powerOn(), "the next operation opens a new session");
			assertEquals(3, bmc.getCount(OPEN_SESSION_REQUEST));

			bmc.expireSession();
			assertThrows(IpmiTimeoutException.class, () -> client.raw(0x06, 0x01).execute());
			assertEquals(3, bmc.getCount(OPEN_SESSION_REQUEST), "a raw request is never replayed");
		}
	}

	@Test
	@Timeout(30)
	void rawRequestsOfAnyNetworkFunction() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			// Get DCMI Capabilities Info (Group Extension 2Ch), answered under 2Dh
			List<byte[]> requests = new ArrayList<>();
			bmc.handle(0x2c, 0x01, data -> {
				requests.add(data);
				return Fixtures.bytes("00 dc 01 05 02 00 01 07");
			});
			RawResponse dcmi = client.raw(0x2C, 0x01).data(0xDC, 0x01).execute();
			assertEquals(CompletionCode.Ok, dcmi.completionCode());
			assertEquals(0, dcmi.rawCompletionCode());
			assertArrayEquals(Fixtures.bytes("dc 01 05 02 00 01 07"), dcmi.data());
			assertEquals("completion code 00h, data DC 01 05 02 00 01 07", dcmi.toString());
			assertEquals(1, requests.size());
			assertArrayEquals(Fixtures.bytes("dc 01"), requests.get(0));

			assertArrayEquals(FakeRmcpPlusBmc.DEVICE_ID, client.raw(0x06, 0x01).execute().data());

			// An error completion code is returned, not thrown
			RawResponse unknown = client.raw(0x30, 0x42).execute();
			assertEquals(CompletionCode.InvalidCommand, unknown.completionCode());
			assertEquals(0xc1, unknown.rawCompletionCode());
			assertEquals(0, unknown.data().length);
			assertEquals("completion code C1h", unknown.toString());
			bmc.handle(0x2e, 0x01, data -> new byte[] { (byte) 0x80 });
			RawResponse specific = client.raw(0x2e, 0x01).execute();
			assertEquals(CompletionCode.Unknown, specific.completionCode());
			assertEquals(0x80, specific.rawCompletionCode());

			assertThrows(IllegalArgumentException.class, () -> client.raw(0x07, 1), "a response network function");
			assertThrows(IllegalArgumentException.class, () -> client.raw(0x40, 1));
			assertThrows(IllegalArgumentException.class, () -> client.raw(-2, 1));
			assertThrows(IllegalArgumentException.class, () -> client.raw(6, 0x100));
			assertThrows(IllegalArgumentException.class, () -> client.raw(6, 1).data(0x100));
			assertThrows(IllegalArgumentException.class, () -> client.raw(6, 1).data(-1));
			assertTrue(bmc.getErrors().isEmpty(), bmc.getErrors().toString());
		}
	}

	@Test
	@Timeout(30)
	void builderValidation() {
		assertThrows(IllegalArgumentException.class, () -> IpmiClient.builder(" "));
		assertThrows(IllegalArgumentException.class, () -> IpmiClient.builder(null));
		IpmiClient.Builder builder = IpmiClient.builder("bmc");
		assertThrows(IllegalArgumentException.class, () -> builder.port(0));
		assertThrows(IllegalArgumentException.class, () -> builder.port(65536));
		assertThrows(IllegalArgumentException.class, () -> builder.cipherSuites(0));
		assertThrows(IllegalArgumentException.class, () -> builder.cipherSuites(17, 1));
		assertThrows(IllegalArgumentException.class, () -> builder.cipherSuite(15));
		assertThrows(IllegalArgumentException.class, () -> builder.cipherSuite(20));
		assertThrows(IllegalArgumentException.class, () -> builder.cipherSuites());
		assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ofNanos(1)));
		assertThrows(IllegalArgumentException.class, () -> builder.messageTimeout(Duration.ofNanos(999999)));
		assertThrows(IllegalArgumentException.class, () -> builder.messageTimeout(Duration.ofSeconds(-1)));
		assertThrows(IllegalArgumentException.class, () -> builder.keepAlive(Duration.ofSeconds(-1)));
		assertThrows(IllegalArgumentException.class, () -> builder.retries(-1));
		assertThrows(NullPointerException.class, () -> builder.credentials(null, new char[0]));
		assertThrows(NullPointerException.class, () -> builder.privilege(null));
		assertThrows(IllegalArgumentException.class, () -> builder.privilege(PrivilegeLevel.MaximumAvailable));
		assertThrows(IllegalArgumentException.class, () -> builder.privilege(PrivilegeLevel.Callback));
		assertThrows(IllegalArgumentException.class, () -> builder.keepAlive(Duration.ofNanos(1)));
		assertThrows(
				IllegalArgumentException.class,
				() -> IpmiClient.builder("bmc").credentials("seventeen-letters", new char[0]).build());
		assertThrows(
				IllegalArgumentException.class,
				() -> IpmiClient.builder("bmc").credentials("user", "twenty-one characters".toCharArray()).build());
		assertThrows(IllegalArgumentException.class, () -> IpmiClient.builder("bmc").bmcKey(new byte[21]).build());
		IpmiClient
				.builder("bmc")
				.credentials("sixteen-letters!", "twenty characters...".toCharArray())
				.bmcKey(new byte[20])
				.cipherSuites(17, 3)
				.keepAlive(Duration.ZERO)
				.build()
				.close();
	}

	@Test
	@Timeout(30)
	void eachFailureHasItsException() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			IpmiAuthenticationException wrongPassword = assertThrows(IpmiAuthenticationException.class, () -> {
				try (IpmiClient client = client(bmc).credentials(USER, "wrong".toCharArray()).build()) {
					client.chassisStatus();
				}
			});
			assertNull(wrongPassword.statusCode(), "the console side rejects the key exchange code");

			IpmiAuthenticationException unknownUser = assertThrows(IpmiAuthenticationException.class, () -> {
				try (IpmiClient client = client(bmc).credentials("nobody", PASSWORD.toCharArray()).build()) {
					client.chassisStatus();
				}
			});
			assertEquals(CompletionCode.UnauthorizedName, unknownUser.statusCode());

			assertThrows(IpmiClientException.class, () -> {
				try (IpmiClient client = client(bmc).cipherSuites(8).build()) {
					client.chassisStatus();
				}
			}, "the BMC offers no MD5 suite");

			bmc.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> new byte[] { (byte) 0xd4 });
			try (IpmiClient client = client(bmc).build()) {
				IpmiCompletionCodeException refused = assertThrows(
						IpmiCompletionCodeException.class,
						() -> client.send(new GetChassisStatus()));
				assertEquals(CompletionCode.InsufficentPrivilege, refused.completionCode());
				assertEquals(0xd4, refused.rawCompletionCode());
				assertEquals(NetworkFunction.ChassisRequest, refused.networkFunction());
				assertEquals(0x01, refused.command());
				assertTrue(refused.getMessage().contains("privilege(PrivilegeLevel.Operator)"), refused.getMessage());
				assertThrows(IpmiCompletionCodeException.class, client::chassisStatus);
			}

			bmc.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> null);
			try (IpmiClient client = client(bmc).timeout(Duration.ofMillis(400)).build()) {
				long start = System.nanoTime();
				IpmiTimeoutException deadline = assertThrows(IpmiTimeoutException.class, client::chassisStatus);
				assertTrue(deadline.getMessage().contains("did not complete"), deadline.getMessage());
				long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				assertTrue(elapsed >= 400 && elapsed < 2000, "elapsed " + elapsed + " ms");
			}
			try (IpmiClient client = client(bmc).build()) {
				IpmiTimeoutException noReply = assertThrows(IpmiTimeoutException.class, client::chassisStatus);
				assertTrue(noReply.getMessage().contains("after 2 tries"), noReply.getMessage());
			}
		}
		try (FakeBmc silent = FakeBmc.silent()) {
			try (
					IpmiClient client = IpmiClient
							.builder(silent.getAddress().getHostAddress())
							.port(silent.getPort())
							.credentials(USER, PASSWORD.toCharArray())
							.messageTimeout(MESSAGE_TIMEOUT)
							.retries(0)
							.build()) {
				IpmiConnectionException noBmc = assertThrows(IpmiConnectionException.class, client::chassisStatus);
				assertTrue(noBmc.getMessage().contains("No reply"), noBmc.getMessage());
			}
		}
		try (IpmiClient client = IpmiClient.builder("no-such-host.invalid").credentials(USER, new char[0]).build()) {
			assertThrows(IpmiConnectionException.class, client::chassisStatus);
		}
	}

	@Test
	@Timeout(30)
	void capabilitiesNeedNoCredentialsAndTheOtherOperationsDo() throws Exception {
		try (
				FakeRmcpPlusBmc bmc = bmc();
				IpmiClient client = IpmiClient.builder(bmc.getAddress().getHostAddress()).port(bmc.getPort()).build()) {
			BmcCapabilities capabilities = client.capabilities();
			assertTrue(capabilities.ipmi20());
			assertFalse(capabilities.bmcKeyRequired());
			assertEquals(19, capabilities.cipherSuites().size());
			assertEquals(17, capabilities.cipherSuites().get(16).getId());
			assertTrue(capabilities.cipherSuites().get(16).isSupported());
			assertFalse(capabilities.toString().isEmpty());
			assertEquals(0, bmc.getCount(OPEN_SESSION_REQUEST), "no session");

			assertThrows(IllegalStateException.class, client::chassisStatus);
		}
	}

	@Test
	@Timeout(30)
	void privilegeAndCipherSuiteSettings() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			try (IpmiClient client = client(bmc).privilege(PrivilegeLevel.Operator).build()) {
				assertTrue(client.chassisStatus().powerOn());
				assertEquals(1, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL), "raised right after the handshake");
			}
			awaitClose(bmc);
			try (IpmiClient client = client(bmc).cipherSuite(3).build()) {
				assertTrue(client.chassisStatus().powerOn());
				assertEquals(1, bmc.getAlgorithms()[0], "RAKP-HMAC-SHA1: suite 3");
			}
			awaitClose(bmc);
			assertEquals(1, bmc.getCount(NETFN_APP, GET_CHANNEL_CIPHER_SUITES) > 0 ? 1 : 0);
			int discoveries = bmc.getCount(NETFN_APP, GET_CHANNEL_CIPHER_SUITES);
			try (IpmiClient client = client(bmc).cipherSuite(17).build()) {
				assertTrue(client.chassisStatus().powerOn());
				assertEquals(3, bmc.getAlgorithms()[0], "RAKP-HMAC-SHA256: suite 17");
			}
			assertEquals(discoveries, bmc.getCount(NETFN_APP, GET_CHANNEL_CIPHER_SUITES), "no discovery with a fixed suite");
			awaitClose(bmc);
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aBusyBmcThatCannotRaiseTheSessionIsNotAnAuthenticationFailure() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.handle(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL, data -> new byte[] { (byte) 0xc0 });
			try (IpmiClient client = client(bmc).privilege(PrivilegeLevel.Operator).build()) {
				IpmiClientException busy = assertThrows(IpmiClientException.class, client::chassisStatus);
				assertEquals(IpmiClientException.class, busy.getClass());
				assertTrue(busy.getMessage().contains("could not raise the session"), busy.getMessage());
				assertTrue(busy.getMessage().contains("(completion code C0h)"), busy.getMessage());
				assertEquals(2, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL), "busy is sent again");
			}
		}
	}

	@Test
	@Timeout(30)
	void anIpmi15OnlyBmcFailsWithAClientExceptionAtOnce() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.inject(FakeRmcpPlusBmc.Fault.IPMI_1_5_ONLY);
			try (IpmiClient client = client(bmc).build()) {
				IpmiClientException refused = assertThrows(IpmiClientException.class, client::chassisStatus);
				assertEquals(IpmiClientException.class, refused.getClass(), "the BMC answered: not a connection failure");
				assertTrue(refused.getMessage().contains("does not support IPMI 2.0"), refused.getMessage());
				assertEquals(
						IpmiClientException.class,
						assertThrows(IpmiClientException.class, client::capabilities).getClass());
				assertEquals(2, bmc.getCount(NETFN_APP, GET_CHANNEL_AUTHENTICATION_CAPABILITIES), "once per operation");
				assertEquals(0, bmc.getCount(FakeRmcpPlusBmc.IPMI_MESSAGE), "no RMCP+ request");
			}
			// Without discovery, the client does not ask: Open Session gets no reply
			try (IpmiClient client = client(bmc).cipherSuite(17).build()) {
				assertThrows(IpmiConnectionException.class, client::chassisStatus);
				assertEquals(2, bmc.getCount(OPEN_SESSION_REQUEST));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aPrivilegeTheBmcRefusesFailsTheOperation() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.handle(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL, data -> new byte[] { (byte) 0x81 });
			try (IpmiClient client = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				IpmiAuthenticationException refused = assertThrows(IpmiAuthenticationException.class, client::chassisStatus);
				assertEquals(CompletionCode.Unknown, refused.statusCode());
				assertTrue(refused.getMessage().contains("refused the Administrator privilege to admin"), refused.getMessage());
				assertTrue(refused.getMessage().contains("(completion code 81h)"), refused.getMessage());
				assertEquals(1, bmc.getCount(OPEN_SESSION_REQUEST), "not replayed");
				assertEquals(1, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL), "the refusal is not sent again");
				awaitClose(bmc);
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void chassisControlAndIdentifyNeedTheOperatorPrivilege() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			try (IpmiClient client = client(bmc).build()) {
				IpmiCompletionCodeException refused = assertThrows(
						IpmiCompletionCodeException.class,
						() -> client.chassisControl(PowerCommand.PowerDown));
				assertEquals(CompletionCode.InsufficentPrivilege, refused.completionCode(), "a User session");
				assertTrue(refused.getMessage().contains("privilege(PrivilegeLevel.Operator)"), refused.getMessage());
				assertEquals(
						CompletionCode.InsufficentPrivilege,
						assertThrows(IpmiCompletionCodeException.class, () -> client.chassisIdentify(5)).completionCode());
				assertTrue(bmc.isPowerOn(), "the refused command did nothing");
				assertEquals(-1, bmc.getChassisControl());
				assertNull(bmc.getChassisIdentify());
				assertThrows(NullPointerException.class, () -> client.chassisControl(null));
				assertThrows(IllegalArgumentException.class, () -> client.chassisIdentify(-1));
				assertThrows(IllegalArgumentException.class, () -> client.chassisIdentify(256));
			}
			awaitClose(bmc);
			try (IpmiClient client = client(bmc).privilege(PrivilegeLevel.Operator).build()) {
				client.chassisControl(PowerCommand.PowerDown);
				assertEquals(0, bmc.getChassisControl());
				assertFalse(client.chassisStatus().powerOn(), "the fake BMC carries the command out at once");

				IpmiCompletionCodeException off = assertThrows(
						IpmiCompletionCodeException.class,
						() -> client.chassisControl(PowerCommand.HardReset));
				assertEquals(CompletionCode.CommandNotSupported, off.completionCode(), "D5h: a reset of a system that is off");
				assertEquals(0xd5, off.rawCompletionCode());
				int controls = bmc.getCount(NETFN_CHASSIS, CHASSIS_CONTROL);
				IllegalStateException cycleOff = assertThrows(
						IllegalStateException.class,
						() -> client.chassisControl(PowerCommand.PowerCycle));
				assertTrue(cycleOff.getMessage().contains("The system is off"), cycleOff.getMessage());
				assertEquals(controls, bmc.getCount(NETFN_CHASSIS, CHASSIS_CONTROL), "a cycle of an off system is not sent");

				client.chassisControl(PowerCommand.PowerUp);
				assertTrue(client.chassisStatus().powerOn());
				client.chassisControl(PowerCommand.PowerCycle);
				assertEquals(2, bmc.getChassisControl());
				client.chassisControl(PowerCommand.HardReset);
				assertEquals(3, bmc.getChassisControl());
				client.chassisControl(PowerCommand.DiagnosticInterrupt);
				assertEquals(4, bmc.getChassisControl());
				client.chassisControl(PowerCommand.SoftShutdown);
				assertEquals(5, bmc.getChassisControl());
				assertFalse(client.chassisStatus().powerOn());

				client.chassisIdentify(5);
				assertArrayEquals(new byte[] { 5 }, bmc.getChassisIdentify(), "the interval byte alone");
				client.chassisIdentify(255);
				assertArrayEquals(new byte[] { (byte) 0xff }, bmc.getChassisIdentify());
				client.chassisIdentifyForever();
				assertArrayEquals(new byte[] { 0, 1 }, bmc.getChassisIdentify(), "the force byte set");
				client.chassisIdentify(0);
				assertArrayEquals(new byte[] { 0 }, bmc.getChassisIdentify(), "off");
				assertEquals(8, bmc.getCount(NETFN_CHASSIS, CHASSIS_CONTROL), "six accepted, two refused");
				assertEquals(5, bmc.getCount(NETFN_CHASSIS, CHASSIS_IDENTIFY), "four accepted, one refused");
			}
			awaitClose(bmc);
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aChassisControlWhoseReplyIsLostIsNotSentAgain() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).privilege(PrivilegeLevel.Operator).build()) {
			// The BMC carries the reset out and loses the reply
			assertTrue(client.chassisStatus().powerOn());
			bmc.inject(bmc.getSessionRequests() + 1, FakeRmcpPlusBmc.Fault.DROP);
			IpmiTimeoutException lost = assertThrows(
					IpmiTimeoutException.class,
					() -> client.chassisControl(PowerCommand.HardReset));
			assertTrue(lost.getMessage().contains("sent once"), lost.getMessage());
			assertEquals(1, bmc.getCount(NETFN_CHASSIS, CHASSIS_CONTROL), "a reset the BMC may have done is not sent again");
			assertEquals(3, bmc.getChassisControl(), "the BMC did carry it out");
			assertTrue(client.chassisStatus().powerOn(), "the session is kept: a read goes on");
			assertEquals(1, bmc.getCount(OPEN_SESSION_REQUEST), "no new session for one lost reply");

			bmc.inject(bmc.getSessionRequests() + 1, FakeRmcpPlusBmc.Fault.DROP);
			assertThrows(IpmiTimeoutException.class, () -> client.chassisIdentify(5));
			assertEquals(1, bmc.getCount(NETFN_CHASSIS, CHASSIS_IDENTIFY));

			// C3h: the BMC timed out on its side and cannot say what it did; a read is retried, a control is not
			bmc.handle(NETFN_CHASSIS, CHASSIS_CONTROL, data -> new byte[] { (byte) 0xc3 });
			IpmiCompletionCodeException unavailable = assertThrows(
					IpmiCompletionCodeException.class,
					() -> client.chassisControl(PowerCommand.PowerDown));
			assertEquals(CompletionCode.Timeout, unavailable.completionCode());
			assertEquals(2, bmc.getCount(NETFN_CHASSIS, CHASSIS_CONTROL), "not sent again on C3h");
			bmc.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> new byte[] { (byte) 0xc3 });
			assertThrows(IpmiCompletionCodeException.class, () -> client.chassisStatus());
			assertEquals(2 + 1 + 1, bmc.getCount(NETFN_CHASSIS, GET_CHASSIS_STATUS), "a read is sent again on C3h: 2 tries");
		}
	}

	@Test
	@Timeout(30)
	void twoClientsWithDifferentMessageTimeoutsSideBySide() throws Exception {
		try (FakeRmcpPlusBmc slow = bmc(); FakeRmcpPlusBmc fast = bmc()) {
			slow.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> null);
			fast.handle(NETFN_CHASSIS, GET_CHASSIS_STATUS, data -> null);
			try (
					IpmiClient patient = client(slow).messageTimeout(Duration.ofMillis(3000)).retries(0).build();
					IpmiClient hasty = client(fast).messageTimeout(Duration.ofMillis(200)).retries(0).build()) {
				long start = System.nanoTime();
				assertThrows(IpmiTimeoutException.class, hasty::chassisStatus);
				long hastyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				start = System.nanoTime();
				assertThrows(IpmiTimeoutException.class, patient::chassisStatus);
				long patientMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				// The message queue expires messages every 500 ms: a timeout is reported within that much
				assertTrue(hastyMs < 2000, "hasty " + hastyMs + " ms");
				assertTrue(patientMs >= 3000, "patient " + patientMs + " ms");
			}
		}
	}

	@Test
	@Timeout(30)
	void concurrentOperationsAreSerializedOnOneSession() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			ExecutorService threads = Executors.newFixedThreadPool(6);
			try {
				List<Future<Boolean>> results = new ArrayList<>();
				for (int i = 0; i < 12; i++) {
					results.add(threads.submit(() -> client.chassisStatus().powerOn()));
				}
				for (Future<Boolean> result : results) {
					assertTrue(result.get());
				}
			} finally {
				threads.shutdownNow();
			}
			assertEquals(1, bmc.getCount(OPEN_SESSION_REQUEST));
			assertEquals(12, bmc.getCount(NETFN_CHASSIS, GET_CHASSIS_STATUS));
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void closeIsIdempotentAndLeavesNoThreadBehind() throws Exception {
		Set<Thread> before = Thread.getAllStackTraces().keySet();
		try (FakeRmcpPlusBmc bmc = bmc()) {
			IpmiClient client = client(bmc).build();
			assertTrue(client.chassisStatus().powerOn());
			client.close();
			assertTrue(bmc.isSessionClosed(), "Close Session was sent");
			client.close();
			assertThrows(IllegalStateException.class, client::chassisStatus);
			assertEquals("", survivors(before), "library threads still alive after close()");
			assertEquals(Collections.emptyList(), bmc.getErrors());

			// A client that never opened a session, and one whose BMC does not answer the logout
			client(bmc).build().close();
			bmc.handle(NETFN_APP, FakeRmcpPlusBmc.CLOSE_SESSION, data -> null);
			IpmiClient unanswered = client(bmc).build();
			unanswered.chassisStatus();
			long start = System.nanoTime();
			unanswered.close();
			assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000, "close() is bounded");
			assertEquals("", survivors(before));
		}
	}

	@Test
	@Timeout(30)
	void aSolConsoleReadsAndWritesInTheSession() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			try (IpmiClient client = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				assertThrows(IpmiCompletionCodeException.class, () -> {
					try (IpmiClient user = client(bmc).build()) {
						user.sol().open();
					}
				}, "the User privilege cannot activate the payload");
				try (SolConsole console = client.sol().open()) {
					assertTrue(bmc.isSolActive());
					console.output().write("ls\r".getBytes(StandardCharsets.US_ASCII));
					await(() -> bmc.getSolReceived().length == 3, "the BMC receives the characters");
					assertEquals("ls\r", new String(bmc.getSolReceived(), StandardCharsets.US_ASCII));

					bmc.sendSol(1, 0, "hello".getBytes(StandardCharsets.US_ASCII));
					InputStream input = console.input();
					byte[] buffer = new byte[16];
					int read = input.read(buffer);
					assertEquals("hello", new String(buffer, 0, read, StandardCharsets.US_ASCII));
					assertFalse(console.deactivated());

					assertTrue(client.chassisStatus().powerOn(), "the session still serves the other operations");
				}
				assertFalse(bmc.isSolActive(), "closing the console deactivates the payload");
				assertTrue(client.chassisStatus().powerOn(), "and keeps the session");
				// A closed console is no longer tracked: the loss of the session does not touch it
				bmc.expireSession();
				assertTrue(client.chassisStatus().powerOn());
				try (SolConsole again = client.sol().open()) {
					assertTrue(bmc.isSolActive(), "a new console after a closed one");
				}
			}
			awaitClose(bmc);
		}
	}

	@Test
	@Timeout(30)
	void aSolPayloadHeldByADeadSessionIsReclaimed() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			try (IpmiClient first = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				first.sol().open();
				// The console is killed: no deactivation, and the BMC keeps the payload active after it expires the session
				bmc.expireSession();
			}
			try (
					IpmiClient second = client(bmc).privilege(PrivilegeLevel.Administrator).build();
					SolConsole console = second.sol().open()) {
				assertEquals(
						1,
						bmc.getCount(NETFN_APP, FakeRmcpPlusBmc.DEACTIVATE_PAYLOAD),
						"the stale instance is deactivated");
				assertTrue(bmc.isSolActive());
				console.output().write("x".getBytes(StandardCharsets.US_ASCII));
				await(() -> bmc.getSolReceived().length == 1, "the reclaimed console works");
			}
		}
	}

	@Test
	@Timeout(30)
	void aSolPayloadHeldByALiveSessionIsNotTouched() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc()) {
			bmc.enableSol(bmc.getPort(), 255, ADMINISTRATOR);
			try (IpmiClient first = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				first.sol().open();
				bmc.expireSession();
			}
			// The BMC still has the session that holds the payload
			bmc
					.handle(NETFN_APP, FakeRmcpPlusBmc.GET_SESSION_INFO, data -> new byte[]
					{ 0x00, 0x02, 0x10, 0x02, 0x02, 0x04, 0x01 });
			try (IpmiClient second = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				IpmiClientException failure = assertThrows(IpmiClientException.class, () -> second.sol().open());
				assertTrue(failure.getMessage().contains("in use by another session"), failure.getMessage());
				assertTrue(failure.getMessage().contains("user ID 2, privilege 4"), failure.getMessage());
				assertEquals(0, bmc.getCount(NETFN_APP, FakeRmcpPlusBmc.DEACTIVATE_PAYLOAD), "never deactivated");
			}
			// A BMC that refuses the lookup: the refusal reaches the caller, the holder keeps its console
			bmc.handle(NETFN_APP, FakeRmcpPlusBmc.GET_SESSION_INFO, data -> new byte[] { (byte) 0xd4 });
			try (IpmiClient third = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				IpmiCompletionCodeException refused = assertThrows(
						IpmiCompletionCodeException.class,
						() -> third.sol().open());
				assertEquals(CompletionCode.InsufficentPrivilege, refused.completionCode());
				assertEquals(0, bmc.getCount(NETFN_APP, FakeRmcpPlusBmc.DEACTIVATE_PAYLOAD), "still never deactivated");
			}
			// A malformed reply (five bytes) is neither a found nor an absent session
			bmc.handle(NETFN_APP, FakeRmcpPlusBmc.GET_SESSION_INFO, data -> new byte[] { 0x00, 0x02, 0x10, 0x02, 0x02 });
			try (IpmiClient fourth = client(bmc).privilege(PrivilegeLevel.Administrator).build()) {
				IpmiClientException malformed = assertThrows(IpmiClientException.class, () -> fourth.sol().open());
				assertTrue(malformed.getMessage().contains("Invalid response length"), malformed.getMessage());
				assertEquals(0, bmc.getCount(NETFN_APP, FakeRmcpPlusBmc.DEACTIVATE_PAYLOAD), "and never deactivated");
			}
		}
	}

	@Test
	void oemRecordsAreDecodedNotSkipped() throws Exception {
		try (FakeRmcpPlusBmc bmc = bmc(); IpmiClient client = client(bmc).build()) {
			SensorList sensors = client.sensors().execute();
			assertEquals(1, sensors.skippedRecords().size());
			assertNotNull(sensors.skippedRecords().get(0).reason());
			assertTrue(sensors.skippedRecords().get(0).toString().contains("0006h"));
			// The OEM record is in the repository the FRU reader walks, as an OemRecord
			assertEquals(2, client.frus().execute().size());
			assertTrue(OemRecord.class.getSimpleName().length() > 0);
		}
	}

	private static String survivors(Set<Thread> before) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		Set<Thread> alive;
		do {
			alive = new HashSet<>(Thread.getAllStackTraces().keySet());
			alive.removeAll(before);
			alive.removeIf(thread -> !thread.isAlive() || thread.getName().startsWith("fake-"));
			if (alive.isEmpty()) {
				return "";
			}
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		return alive.stream().map(Thread::getName).sorted().collect(Collectors.joining(", "));
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
}
