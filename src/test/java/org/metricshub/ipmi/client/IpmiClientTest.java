package org.metricshub.ipmi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.client.Fixtures.BOARD_SENSOR;
import static org.metricshub.ipmi.client.Fixtures.FAN_SENSOR;
import static org.metricshub.ipmi.client.Fixtures.PASSWORD;
import static org.metricshub.ipmi.client.Fixtures.PSU_FRU;
import static org.metricshub.ipmi.client.Fixtures.SUITES;
import static org.metricshub.ipmi.client.Fixtures.TEMPERATURE_SENSOR;
import static org.metricshub.ipmi.client.Fixtures.USER;
import static org.metricshub.ipmi.client.Fixtures.bmc;
import static org.metricshub.ipmi.client.Fixtures.utf8;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.ADMINISTRATOR;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHANNEL_CIPHER_SUITES;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHASSIS_STATUS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_APP;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_CHASSIS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.OPEN_SESSION_REQUEST;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.SET_SESSION_PRIVILEGE_LEVEL;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SDR;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SDR_REPOSITORY_INFO;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.GET_SENSOR_READING;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.NETFN_SENSOR;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.NETFN_STORAGE;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.compactSensorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.fruDeviceLocatorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.record;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.OemRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorUnit;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.transport.FakeBmc;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;
import org.metricshub.ipmi.core.transport.FakeSdrRepository;

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

			assertTrue(client.sensors().execute().stream().allMatch(sensor -> sensor.record() != null));
			assertFalse(sensors.toString().isEmpty());
			assertFalse(frus.toString().isEmpty());
			assertFalse(client.chassisStatus().toString().isEmpty());
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
