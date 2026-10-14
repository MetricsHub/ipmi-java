keywords: upgrade, migration, release notes, breaking changes, 2.0, fluent client, builder, device id, raw, dcmi, network function, ipmiclientexception, slf4j, ipmicli, standalone jar, sel, system event log, chassis control, chassis identify, protected fields, accessors, abstractsensorrecord, validateresponse, utf-8, bmc key, cipher suite, integrity, org.sentrysoftware
description: What changes when upgrading the IPMI Java Client — from 1.x to 2.0.00 (the fluent client, the System Event Log), from 1.2.02, from 1.2.01, and from the org.sentrysoftware:ipmi artifact of 1.2.00 and earlier.

# Upgrading

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## Upgrading from 1.x to 2.0.00

Version 2.0.00 replaces the client API of 1.x with a fluent client, removes the SLF4J
dependency, and simplifies the low-level commands. The protocol core and the decoders are the
same. It also adds a command-line client, `ipmicli`, in a standalone jar
([Command-Line Client](cli.html)); `CipherSuite` gains `getAuthenticationAlgorithmCode()`,
`getIntegrityAlgorithmCode()` and `getConfidentialityAlgorithmCode()` for it. The semicolon-separated text output of 1.x (`IpmiResultConverter`) is MetricsHub's format,
and moves to the [MetricsHub IPMI extension](https://github.com/MetricsHub/metricshub-community/issues/1391),
where it stays byte-identical to 1.2.x for the same BMC data, except for the sensor and FRU names
that 1.x padded with NUL characters (see [The low-level API](#the-low-level-api)).

### The client

[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) is built with
[`IpmiClient.builder(host)`](apidocs/org/metricshub/ipmi/client/IpmiClient.Builder.html) and
owns **one session**, opened by the first operation and kept alive until `close()`; the static
methods and `IpmiClientConfiguration` are gone.

| 1.x | 2.0.00 |
| --- | --- |
| `new IpmiClientConfiguration(host, user, password, bmcKey, skipAuth, timeout)` | `IpmiClient.builder(host).credentials(user, password).bmcKey(bmcKey).timeout(Duration.ofSeconds(timeout)).build()`, in a try-with-resources block ([Configuration](configuration.html)) |
| `new IpmiClientConfiguration(host, port, ...)` | `.port(port)` |
| `new IpmiClientConfiguration(..., pingPeriod)` | `.keepAlive(Duration.ofMillis(pingPeriod))`; `Duration.ZERO` disables the keep-alive |
| `skipAuth = true` | `.cipherSuite(3)`: the suite is fixed without discovery; `.cipherSuites(17, 3)` lists a preference |
| `connection.properties`, `vxipmi.properties`, `PropertiesManager` | `.messageTimeout(Duration)`, `.retries(int)` and `.keepAlive(Duration)` on the builder; `IpmiConnector.setTimeout(int)`, `setRetries(int)` and the `pingPeriod` constructor argument on the low-level API. The properties files and `PropertiesManager` are removed. |
| `IpmiClient.getChassisStatus(config)` | `bmc.chassisStatus()`, a [`ChassisStatus`](chassis-status.html) (`data()` gives the `GetChassisStatusResponseData`) |
| `IpmiClient.getSensors(config)` | `bmc.sensors().execute()`, a [`SensorList`](sensors.html) of typed [`Sensor`](apidocs/org/metricshub/ipmi/client/Sensor.html)s; `types()` and `names()` select sensors before their readings are requested |
| `IpmiClient.getFrus(config)` | `bmc.frus().execute()`, a [`FruList`](fru-inventory.html) of typed [`Fru`](apidocs/org/metricshub/ipmi/client/Fru.html)s; `ids()` selects FRUs |
| `GetSelInfo`, `GetSelEntry` sent by hand | `bmc.sel().execute()`, a [`SelEntryList`](system-event-log.html) of typed [`SelEntry`](apidocs/org/metricshub/ipmi/client/SelEntry.html)s; `since(entry)` reads the entries added after one already read |
| `ChassisControl` sent by hand | `bmc.chassisControl(PowerCommand)` on a client built with `privilege(PrivilegeLevel.Operator)` ([Controlling the power](chassis-status.html#controlling-the-power)); `chassisIdentify(seconds)` and `chassisIdentifyForever()` drive the identify LED |
| `IpmiClient.getChassisStatusAsStringResult(config)` | `bmc.chassisStatus().powerOn()`; the `System power state is up` text is produced by the MetricsHub IPMI extension |
| `IpmiClient.getFrusAndSensorsAsStringResult(config)` | `bmc.frus().execute()` and `bmc.sensors().execute()`, one session and one SDR walk instead of two; the text lines are produced by the MetricsHub IPMI extension, which took `IpmiResultConverter` over |
| A new session per call | One session per client, serialized operations, [reopened](timeouts-and-errors.html#session-recovery) when the BMC loses it |
| `Sensor.getRecord()`, `getData()`, `getStates()`, `getEntityId()`, `getDeviceId()` | `record()`, `readingData()`, `states()` (a list), `entity()`, `entityInstance()`, plus `reading()`, `rawReading()`, the thresholds, `status()`, `problem()` |
| `Fru.getFruLocator()`, `getFruRecords()` | `locator()`, `records()`, plus `board()`, `product()`, `chassis()`, `multiRecords()`, the summary (`manufacturer()`, `model()`, `serialNumber()`, `partNumber()`), `complete()` and `problems()` |
| `org.metricshub.ipmi.client.model`, `IpmiResultConverter`, `DeviceDescription` | Removed: the typed results replace the model classes, and the text converter with its `ipmiutil` entity names moves to the MetricsHub IPMI extension; `ReadingTypeDescription` moves to `org.metricshub.ipmi.client` |
| `org.metricshub.ipmi.client.runner` | Removed (implementation details of the client) |
| `ExecutionException` wrapping the cause, `TimeoutException`, `InterruptedException` | The unchecked [`IpmiClientException`](timeouts-and-errors.html#exceptions) hierarchy: `IpmiAuthenticationException`, `IpmiConnectionException`, `IpmiTimeoutException`, `IpmiCompletionCodeException`; the core exception is the `getCause()` |
| The `WARN` and `DEBUG` logs | Nothing is logged: `SensorList.skippedRecords()`, `FruList.skippedRecords()`, `Sensor.problem()` and `Fru.problems()` carry what the library logged ([Problems that do not fail the operation](timeouts-and-errors.html#problems-that-do-not-fail-the-operation)) |

The migration of a 1.x collection is a few lines:

```java
// 1.x
List<Fru> frus = IpmiClient.getFrus(
		new IpmiClientConfiguration(host, user, password, bmcKey, skipAuth, timeoutSeconds));
List<Sensor> sensors = IpmiClient.getSensors(
		new IpmiClientConfiguration(host, user, password, bmcKey, skipAuth, timeoutSeconds));

// 2.0.00
try (IpmiClient bmc = IpmiClient.builder(host)
		.credentials(user, password)
		.bmcKey(bmcKey)
		.timeout(Duration.ofSeconds(timeoutSeconds))
		.build()) {
	FruList frus = bmc.frus().execute();
	SensorList sensors = bmc.sensors().execute();
}
```

New in the client: [`capabilities()`](apidocs/org/metricshub/ipmi/client/BmcCapabilities.html)
reads the cipher suites and authentication options of a BMC without credentials,
[`deviceId()`](device-id.html) identifies the BMC (manufacturer and product IDs, firmware
revision, IPMI version, functions),
[`sel()`](system-event-log.html) reads the System Event Log as typed entries (the sensor, the
event in the wording of `ipmiutil`, the direction, the timestamp, the OEM bytes), whole or since
the last entry read,
[`chassisControl(PowerCommand)`](chassis-status.html#controlling-the-power) powers the system
on or off, cycles or resets it, and `chassisIdentify(seconds)` blinks the identify LED,
[`send(command)`](low-level-api.html#sending-a-command-through-the-client) sends any command of
the core in the session, [`raw(netFn, command)`](low-level-api.html#raw-requests) sends any
request as bytes, the extension and OEM network functions included (DCMI `2Ch`, OEM `30h`-`3Eh`),
and returns its completion code and data without throwing (`ipmicli raw` is its subcommand),
[`sol().open()`](serial-over-lan.html#with-ipmiclient) opens a Serial
over LAN console as streams, `privilege(PrivilegeLevel)` opens the session at the Operator or
Administrator level (raised with Set Session Privilege Level after the handshake, as every session
starts at User), and the [SDR cache](configuration.html#sdr-cache) makes `frus()` and
`sensors()` share one repository walk.

### Logging

The library no longer depends on `org.slf4j:slf4j-api` and never logs. The failures reach the
caller as exceptions; the problems that do not fail an operation are reported in its result (see
the table above); the failures nobody can act on (a lost keep-alive, a Close Session without
reply) are ignored.

### The low-level API

* The command constructors no longer take `IpmiVersion`, `CipherSuite` and `AuthenticationType`:
  `new GetChassisStatus()`, `new GetSdr(reservationId, recordId)`, `new ChassisControl(PowerCommand.PowerUp)`,
  `new SolCoder(message)` are complete commands, and the session sets the parameters when it
  encodes the request (`PayloadCoder.setSessionParameters()` still exists for code that encodes
  a command itself). The payload command constructors deprecated in 1.2.03 for building requests
  for cipher suite 0 are removed, and the remaining ones (`new DeactivatePayload(PayloadType, int)`,
  `new GetPayloadActivationStatus(PayloadType)`, `new GetChannelPayloadSupport(byte)`) are now
  the way to build them.
* `IpmiCommandCoder<R extends ResponseData>` is generic: `getResponseData()` returns `R`, and
  `IpmiConnector.sendMessage(handle, command)` returns `R` without a cast. A command of your own
  declares its response type: `class GetDeviceId extends IpmiCommandCoder<GetDeviceIdResponseData>`.
  `ActivatePayload<R extends ActivatePayloadResponseData>` is generic too.
* `IpmiConnector.setTimeout(int)` sets the message timeout of the connections created
  afterwards (5 000 ms by default), `setRetries(int)` the retries (3), and the `pingPeriod`
  constructor argument the keep-alive period (30 000 ms); `IpmiAsyncConnector` has the same
  setters, and `ConnectionManager.setTimeout(int)` too. The busy pause (`idleTime`) is a
  constant of 4 000 ms.
* `CipherSuite.forId(int)` builds one of the standard suites 0 to 19 with the algorithms of the
  specification, for `createConnection()` without discovery.
* A reply is matched to its request by its command code **and** its network function, the request
  one plus one (IPMI 2.0 section 5.1), where 1.2.x used the tag and the command code only.
  `IpmiCommandCoder.getNetworkFunctionCode()` gives the code of the request, by default that of
  `getNetworkFunction()`. A command of a network function `NetworkFunction` does not list
  (Transport `0Ch`, DCMI `2Ch`, OEM `2Eh`, `30h`-`3Eh`) returns `null` from
  `getNetworkFunction()`, overrides `getNetworkFunctionCode()`, and builds its request with the
  new `IpmiLanRequest(int networkFunctionCode, byte command, byte[] data, byte sequenceNumber)`
  ([Extension and OEM network functions](low-level-api.html#extension-and-oem-network-functions)).
  An `IpmiLanRequest` subclass that set the protected `networkFunction` field of 1.2.x to such a
  code must be replaced with this constructor and override, or its replies are ignored and its
  requests time out.
* `IpmiConnector.openSession()` and `IpmiAsyncConnector.openSession()` raise the session to the
  requested privilege with Set Session Privilege Level right after the handshake when it is
  `Operator` or `Administrator` (IPMI 2.0 section 22.18). In 1.x the session stayed at User on the
  BMCs that follow the specification (iDRAC 7 and 8, iLO 5, XCC, Cisco IMC), where its Operator
  and Administrator commands failed with `D4h`. A BMC that refuses the level now fails
  `openSession()` with `SessionException` (whose cause is the `IPMIException` of the completion
  code) after closing the session, where 1.x returned a session running at a lower level: request
  the level the commands need. A `SetSessionPrivilegeLevel` sent after `openSession()` is now
  redundant and harmless. The `SerialOverLan` constructors that open their own session request
  Administrator, and now fail with `SessionException` for an account not allowed Administrator:
  open the session at the level the payload needs and pass it to `SerialOverLan(connector,
  session)`; the console raises a session itself only when it was opened with
  `MaximumAvailable`.
* `getAvailableCipherSuites()` first sends Get Channel Authentication Capabilities in the IPMI
  v1.5 format (IPMI 2.0 section 13.13), and sends the RMCP+ Get Channel Cipher Suites only when the
  reply reports IPMI v2.0 connections (table 22-15). An IPMI 1.5-only BMC fails at once with the
  new `Ipmi20UnsupportedException`, a `ConnectionException` that is not sent again
  (`IpmiClientException` from `IpmiClient`, exit code 70 from `ipmicli`), where 1.x sent Get
  Channel Cipher Suites until all the tries timed out (`ConnectionException: Command timed out`).
  `getChannelAuthenticationCapabilities()` no longer sends anything: it returns that reply,
  requested at the User level, and declares the cipher suite and the privilege of the session.
  The handshake sends as many messages as before.
* `IpmiConnector.sendMessage(handle, command, resends)` sends a command with its own number of
  resends when no reply comes or the BMC answers `C3h`, 0 for a command the BMC must not receive
  twice.
* The new `GetDeviceId` command (`org.metricshub.ipmi.core.coding.commands.app`, IPMI 2.0
  section 20.1) decodes the identity of the BMC into `GetDeviceIdResponseData`; it is also the
  keep-alive the connection sends.
* `PowerCommand` has the three chassis controls 1.x left out: `PowerCycle` (`2h`),
  `DiagnosticInterrupt` (`4h`) and `SoftShutdown` (`5h`); `parseInt()` accepts their codes. The new
  `ChassisIdentify` command (IPMI 2.0 section 28.5) takes the interval in seconds and the "force
  identify on" flag.
* A wrong password or BMC key fails the handshake with
  `AuthenticationException` (`org.metricshub.ipmi.core.coding.commands.session`), a subclass of
  the `IllegalArgumentException` that 1.2.x threw with the same messages.
* `ReadFruData.decodeFruData(data, problems)` reports the areas and multirecords it skipped; the
  one-argument form keeps decoding what it can, silently.
* The decoders no longer log the values they do not model: they return the same defaults as
  before (`Other`, `Unknown`, `Unspecified`, `Oem`).
* The ID string of the SDR records (`getName()` of the sensor and locator records, so
  `Sensor.name()`, `Fru.name()` and the `names()` globs of `sensors()`) is decoded as long as its
  type/length byte says (IPMI 2.0 section 43.15) and ends at its first NUL character. 1.x decoded
  the rest of the record as well: on Fujitsu iRMC S5, HPE iLO 5 and Supermicro BMCs, the names
  ended with NUL characters (`Fan 1\0\0...`), which `names("Fan 1")` did not match.
* `SelRecord` decodes the generator ID, the event/reading type and the three event data bytes of
  a system event (`getGeneratorId()`, `getEventType()`, `getEventData()`), keeps the 16 bytes of
  the record (`getRawData()`), and refuses a record shorter than 16 bytes with
  `IllegalArgumentException` instead of `ArrayIndexOutOfBoundsException`. The new
  `GetSelTimeUtcOffset` command reads the UTC offset of a local SEL time (IPMI 2.0 revision 1.1). `TypeConverter.decodeDate()` decodes the IPMI timestamps as
  unsigned (IPMI 2.0 section 37.1): the dates from 2038 on, and the `FFFFFFFFh` marker of an
  unspecified timestamp, were negative dates in 1.x. It is used by the SEL and SDR repository
  timestamps.

## Upgrading from 1.2.02

The `IpmiClient` API is unchanged, and the client is more tolerant of real-world BMCs:

* the SDR repository walk no longer aborts on a record type the library does not model: OEM
  records (`C0h` – `FFh`) are decoded as `OemRecord`, and any record that cannot be decoded is
  logged and skipped, where 1.2.02 returned no sensors and no FRUs at all
  ([OEM and unknown records](supported-commands.html#oem-and-unknown-records));
* a BMC that answers a whole-record Get SDR with a truncated record is read again in chunks;
* the user name and password are encoded in UTF-8 whatever the platform charset (1.2.02 used the
  platform charset, UTF-8 by default only since Java 18), and the
  [BMC key](configuration.html#bmc-key) is used as raw bytes: a key with bytes `80h` or above no
  longer gets corrupted into a wrong session key;
* the user name is limited to 16 bytes once encoded, as the IPMI specification requires, instead of
  16 characters;
* a lost UDP reply is recovered in seconds instead of stalling the call: the per-message timeout
  is 5 s by default (it was 5 minutes) and capped by the overall timeout, a retried message waits
  for the reply of the resent request, and the handshake steps wait for the elapsed time rather
  than a number of sleeps ([Timeouts and Errors](timeouts-and-errors.html));
* a request is sent again at once after a lost reply or a timeout on the BMC side (`C3h`), where
  1.2.02 paused for a random time of up to `idleTime` (4 s) before each resend; with the 5 s
  message timeout, these pauses made a collection on a Dell iDRAC 8, which answers `C3h` for its
  absent FRUs and loses a few replies, take over two minutes; the pause now only follows the
  completion codes that say the BMC is busy;
* a BMC that never answers now fails the session handshake with
  `ExecutionException` wrapping `ConnectionException: Command timed out`, about 20 s into the
  call, where 1.2.02 threw `TimeoutException` at the overall timeout;
* the overall timeout cancels the worker for good: the interrupted session stops at its current
  wait and closes the session and the port, normally within one second of the deadline (a worker
  stuck in a call that cannot be interrupted, such as name resolution, does so when that call
  returns), and the receiving and timer threads are daemon threads, so a program no longer needs
  `System.exit()` to end;
* a failed login fails at once with its actual cause (`IllegalArgumentException: Authentication
  check failed`, `IPMIException: Unauthorized name.`), where 1.2.02 sent the credentials four
  times and threw `ConnectionException: Illegal connection state: Rakp1Waiting`
  ([Troubleshooting](troubleshooting.html#ipmiauthenticationexception)); only a handshake step that got no
  reply is sent again;
* the [keep-alive](configuration.html#keep-alive) is actually sent: with the default `pingPeriod`
  (`-1`) 1.2.02 sent no keep-alive at all, so a session could expire during a long collection; the
  keep-alive is now a Get Device ID every 30 s by default, as `ipmitool` sends, whose reply is
  discarded, and a Get Channel Authentication Capabilities command sent by the application in a
  session gets its reply (1.2.02 dropped it, as it did the keep-alive replies);
* a one-way IPMI message (`IpmiConnector.sendOneWayMessage()`, `IpmiAsyncConnector.sendMessage()`
  with `isOneWay`) is queued like any request: its tag stays reserved, and it takes a slot of the
  8-message window, until its reply arrives or it times out, where 1.2.02 did not reserve the tag,
  so it could be handed out again while the reply was still on its way and a late reply taken for
  the reply of a later request; its reply and its timeout are
  still not reported (the Serial over LAN acknowledgements, which the BMC never answers, are not
  queued);
* a sensor whose Get Sensor Reading fails with an error completion code is returned without
  reading, and a `WARN` names it, where 1.2.02 failed the whole call (it tolerated
  `DataNotPresent` only);
* `IpmiConnector.closeConnection()` releases the connection and forgets its session: its handle
  then throws `IllegalStateException` instead of addressing a disconnected connection, and
  `SessionManager.establishSession()` closes the connection of a session it fails to open instead
  of tearing down the whole connector; closing a connection or tearing down the connector fails the
  requests still waiting for a reply on it with `ConnectionException`, where they waited forever;
* the `PropertiesManager` lookups are logged at `DEBUG` instead of `INFO`, the unused
  `cleaningFrequency` property is gone from `connection.properties`, and `Constants.TIMEOUT`,
  which nothing reads, is deprecated;
* the sending, receiving and keep-alive threads of a connection no longer race: the state
  machine serializes transitions and received messages, the HMAC and AES objects of a cipher suite
  are used by one thread at a time, and the listener lists can be changed while they are being
  notified (a listener may unregister itself from its own callback).

The RMCP+ sessions follow the security rules of the IPMI 2.0 specification:

* `IpmiClient` chooses the cipher suite by its ID: the first of 17, 3, 8, 16, 2 and 7 that the BMC
  offers ([How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)),
  where 1.2.02 took the 4th suite of the BMC's list (or the 3rd, 2nd, 1st): suite 4 (xRC4) on a
  Lenovo XCC, which the client does not implement; a BMC that offers none of the six, only suites
  without integrity, fails with `ConnectionException: The BMC offers none of the cipher suites
  ...` instead of opening a session whose replies could be forged;
* `getAvailableCipherSuites()` gives the standard suites the algorithms of the specification,
  whatever the BMC's records say, leaves out the OEM suites (1.2.02 listed those of a Cisco IMC as
  suites -128 and -79) and skips malformed records instead of throwing; the new
  `CipherSuite.isSupported()` tells the suites the library implements;
* a reply that fails the integrity check of the session is discarded, where 1.2.02 logged
  `Integrity check failed` and accepted it, and so is a reply that is not signed in a session with
  integrity (1.2.02 accepted a forged reply in clear text); the integrity is checked before
  decrypting, the pad of AES-CBC-128 is checked, a reply received twice is discarded, and the
  sequence-number window no longer locks the session out after 16 lost replies;
* the handshake fails with `IllegalArgumentException: ... does not match the request` when a reply of
  the BMC is for another session or confirms other algorithms than the requested ones, and the RAKP
  Message 4 of RAKP-HMAC-MD5 (suites 6 to 8) is checked on its 16 bytes instead of 12;
* the console random number of the handshake comes from a `SecureRandom` (1.2.02 used a
  `java.util.Random` seeded with the time), and the console session IDs start at a random value
  instead of 100 (they still go up by one per session), and every new session numbers its messages
  from 1 again, where 1.2.02 dropped the replies of a session reopened on the same connection
  after more than 17 messages;
* a password or a BMC key longer than 20 bytes fails with `IllegalArgumentException` before
  anything is sent (1.2.02 failed the handshake as for a wrong password), an empty password is valid, a BMC key of zeros only is
  treated as no key, and the `char[]` password of `IpmiClientConfiguration` is no longer copied into
  a `String`;
* a truncated or malformed datagram is dropped instead of throwing an
  `ArrayIndexOutOfBoundsException` in the receiving thread;
* a request carries the LUN of the client (0) in its rqSeq/rqLUN byte, where 1.2.02 put the LUN of
  the target there;
* `GetChannelAuthenticationCapabilitiesResponseData.isIpmiv20Support()` reads the "IPMI v2.0
  connections" bit of the extended capabilities, where 1.2.02 returned the bit that says the
  extended capabilities are present.

The decoders follow the IPMI 2.0 and FRU specifications more closely; the visible changes are:

* a Full Sensor record reports `Double.NaN` for a threshold it does not define, where 1.2.02
  reported `0.0`; the text result now reports a threshold of `0` instead of leaving it empty;
* the thresholds are linearized like the reading, are read whenever byte 12 of the record says
  the sensor has thresholds (1.2.02 looked at the wrong byte), and `getAccuracy()` and
  `getTolerance()` are decoded as the specification says; `hasAnalogReading()` tells when the
  reading byte is not a reading, which includes the non-linear sensors (linearization `70h`-`7Fh`),
  whose conversion needs the Get Sensor Reading Factors command the library does not implement:
  their reading and thresholds are `NaN` where 1.2.02 dropped the sensor;
* a sensor whose reading the BMC flags as unavailable or not scanned is returned without reading
  and without states (1.2.02 reported `0.0`); `GetSensorReadingResponseData.isScanningEnabled()`
  exposes the flag;
* the threshold status of a reading (`getSensorState()`) and the states of a threshold sensor
  (`getStatesAsserted()`) name the threshold actually crossed (1.2.02 reported an upper
  threshold crossing as "below lower non-critical");
* a completion code the library does not list no longer aborts the decoding of the response:
  the command fails with an `IPMIException` whose `getCompletionCode()` is the new
  `CompletionCode.Unknown` and whose `getRawCode()` holds the code, and the RMCP+ status codes are
  no longer applied to IPMI commands;
* `FruDeviceLocatorRecord.getId()` returns the SDR record ID, as for every other record (1.2.02
  returned the FRU device ID, which `getDeviceId()` returns);
* SEL entries of the OEM record types are decoded with their own layout (`getManufacturerId()`,
  `getOemData()`), the record types `C0h` and `E0h` are accepted, and a reserved record type gives
  a `SelRecordType.Reserved` entry instead of an exception;
* `BoardInfo.getMfgDate()` is computed in UTC and is `null` when the FRU leaves the date
  unspecified (1.2.02 used the JVM time zone and reported 1996-01-01); FRU strings in a
  non-English language are decoded as UTF-16LE; a word-addressed FRU is read with 2-byte words;
* `IpmiClient.getFrus()` returns the FRUs it could read even when FRU 0 or Get FRU Inventory Area
  Info fails, returns FRU 0 once, and stops reading a FRU at its first unreadable chunk instead of
  shifting the following chunks into the gap;
* reserved values of the rate unit, modifier unit usage and power restore policy decode to
  `None` or `Unknown` instead of throwing;
* the sensors of the OEM event/reading types `70h` to `7Eh` (Cisco IMC, Dell iDRAC and Fujitsu iRMC
  use them for presence and module sensors) report the raw value of their state bytes
  (`sensorName=0xHHLL`) like those of `7Fh`, where 1.2.02 reported `sensorName=Unknown`; an OEM
  sensor (`70h` to `7Fh`) with no state asserted reports no state, where 1.2.02 reported the
  state bytes of a `7Fh` one; `ReadingType.parseInt()`, and so `getStatesAsserted()` and the event
  of a SEL record, returns `UnknownOEMEvent` for the states of `70h` to `7Fh`, where 1.2.02
  returned `Unknown` unless the sensor type was OEM; the new `ReadingType.isOem()` tells these
  types apart;
* a value the decoders do not model (an OEM or chassis-specific entity ID, a reserved device or
  sensor type, a state the reading type does not define) is logged at `DEBUG` instead of `ERROR`
  or `WARN`.

[Serial over LAN](serial-over-lan.html) consoles follow the IPMI specification and release what
they open:

* `writeBytes()` sends all the data: from the third packet on, 1.2.02 lost the data or threw
  `IllegalArgumentException` (on a Supermicro BMC, a 600-character write failed after 20 s with only
  its first packet delivered); a packet carries at most 255 characters;
* the packets of the console are numbered 1, 2, 3..., from 1 again at each activation of the
  payload, as `ipmitool` does: its acknowledgements no longer take a
  number, which made 1.2.02 reuse the number of the previous packet after 14 packets of the BMC
  (a BMC drops such a packet as a retry), and a packet whose acknowledgement is lost is sent again
  with its number, where 1.2.02 sent it as a new packet, which the BMC passed to the server again;
* a partial NACK of more than 127 characters sends the rest (1.2.02 read the count as a negative
  byte and gave up), and a packet that the BMC NACKs is sent again after a pause of 100, 200,
  then 300 ms instead of at once;
* `invokeOperations()` no longer types a NUL character with the operations, returns once the
  packet is sent instead of waiting for an acknowledgement that many BMCs never send, and holds the
  line states (`CTS`, `DCD_DSR`, `RingWOR`) until the next call, where 1.2.02 released them with
  the next packet;
* a packet that the BMC sends again is read once (1.2.02 repeated a piece of console output at
  each lost acknowledgement), every packet with a sequence number is acknowledged, also one
  without characters, a full buffer suspends the BMC until a read resumes it (the acknowledgement
  that resumes it is sent again, every 100 ms at most, until a newer packet shows that the BMC
  resumed), a status is reported once per packet, also when the BMC NACKs a packet you write, and a
  `SolDeactivated` status sent in a packet without sequence number reaches the event listeners;
* `readXxx(count, timeout)` waits on the buffer instead of polling it every millisecond, and
  returns when the buffer is full; the buffer holds 64 KiB instead of 2 KiB, since a Supermicro BMC
  drops the characters that a full buffer asks it to hold;
* a console receives the SOL packets of its own connection only, where 1.2.02 gave every console
  of a connector the packets of all of them, and `close()` unregisters it;
* the constructors that open their own session own the connector: they tear it down when the
  console cannot be opened, as `close()` does; `close()` closes every session the console opened,
  including the one on the SOL port when the BMC serves SOL on another UDP port, and releases them
  even when Deactivate Payload fails, where 1.2.02 threw before closing the session;
* `SerialOverLan(connector, session)` never closes the caller's session or connector (1.2.02 tore
  down the connector when the BMC served SOL on another port), and the session it opens on the SOL
  port has the BMC key of the caller's session and its own cipher suite instance (1.2.02 shared the
  caller's, which rekeyed the caller's session);
* a new constructor takes the [BMC key](configuration.html#bmc-key), and the privilege of a session
  is raised to the level it was opened for, not always to Administrator;
* a lost reply to Activate Payload is recovered: when the payload turns out to be active in the
  console's own session (Get Payload Instance Info), it is deactivated and activated again, where
  1.2.02 failed with `80h`; a payload active in another session is never deactivated;
* `GetChannelPayloadSupport` reads the session setup and OEM payloads at their offsets: 1.2.02
  reported the session setup payloads of a Dell iDRAC or a Cisco IMC as the OEM payloads 0 to 5.

Code that **uses the low-level API** to read FRUs: the `offset` and `countToRead` arguments of
the `ReadFruData` constructors are now **in bytes** whatever the access unit of the device, and
the offset alone is sent in words when Get FRU Inventory Area Info reports a word-addressed FRU
(1.2.02 multiplied both by a "word size" of 16, which read word-addressed FRUs 16 times too far).
A loop that walked a FRU in units of the device now walks it in bytes, with an even offset for
a word-addressed device:

```java
int size = info.getFruInventoryAreaSize(); // in bytes, whatever the access unit
for (int offset = 0; offset < size; offset += 16) {
	connector.sendMessage(handle, new ReadFruData(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus,
			fruId, info.getFruUnit(), offset, Math.min(16, size - offset)));
}
```

Code that **extends** the library's protocol classes needs the changes below. `QueueElement`
lost its `isTimedOut()`, `makeTimedOut()` and `refreshTimestamp()` methods: a timed-out message
now leaves the queue at once.

### Protected fields

The `protected` fields of the protocol classes are now `private`. Replace direct access to them
with the new `protected` accessors:

| Class | Former field | Accessor |
| --- | --- | --- |
| `AbstractIpmiRunner` | `ipmiConfiguration` | `getIpmiConfiguration()` |
| `AbstractIpmiRunner` | `connector` | `getConnector()` |
| `AbstractIpmiRunner` | `handle` | `getHandle()` |
| `AbstractIpmiRunner` | `nextRecId` | `getNextRecId()`, `setNextRecId(int)` |
| `MessageHandler` | `messageQueue` | `getMessageQueue()` |
| `MessageHandler` | `connection` | `getConnection()` |
| `MessageHandler` | `lastReceivedSequenceNumber` | None: the sequence-number window is private |
| `IpmiLanMessage` | `networkFunction` | `getNetworkFunctionCode()` (public), `setNetworkFunctionCode(byte)` |
| `ConfidentialityAlgorithm` | `sik` | `getSik()` |
| `IntegrityAlgorithm` | `sik` | `getSik()`, `setSik(byte[])` |

`IpmiClient`, `IpmiResultConverter`, `Utils`, `DeviceDescription`, `ReadingTypeDescription` (the last four removed or moved in 2.0.00) and
`MessageComposer` are now `final` (they only had private constructors, so they could not be
subclassed anyway).

### Credentials and transport

| Class | Change |
| --- | --- |
| `AuthenticationAlgorithm` | `getKeyExchangeAuthenticationCode(byte[] data, byte[] key)` and `checkKeyExchangeAuthenticationCode(byte[] data, byte[] key, byte[] password)` take the key and the password as bytes instead of a `String` |
| `IntegrityAlgorithm`, `ConfidentialityAesCbc128` | The `protected` constants `CONST1` and `CONST2` are now `private` |
| `UdpMessenger` | `getSentPackets()`, a debug counter, is removed |
| `ProtocolDecoder` | `decodePayload(...)` throws `IllegalArgumentException` on an empty payload instead of a `NullPointerException` |
| `Protocolv20Decoder` | `decode()` throws `IllegalArgumentException` for a message that fails the integrity check, is not signed in a session with integrity, or is truncated |
| `IpmiConnector`, `IpmiAsyncConnector` | New `openSession(ConnectionHandle, String username, byte[] password, byte[] bmcKey)`; a call with a literal `null` password now needs a cast: `(String) null` |
| `ConnectionHandle` | The password is kept as bytes: new `getPasswordBytes()` and `setPasswordBytes(byte[])`, which keeps a copy |
| `SessionManager` | New `establishSession(...)` overload with the password as a `byte[]` and the BMC key |
| `Rakp1` | The constructor takes the password as a `byte[]` of at most 20 bytes; `getPassword()` is removed; the new `checkCredentials(String, byte[], byte[])` checks the lengths, and the user name message now reads `Username is too long. Its length cannot exceed 16 bytes` |
| `OpenSessionAck`, `Connection.startSession()`, `ConnectionManager.startSession()` | The password is a `byte[]` |

### Serial over LAN and payloads

| Class | Change |
| --- | --- |
| `SerialOverLan` | New constructor with a `bmcKey`; `invokeOperations()` returns `true` once the packet is sent |
| `ConnectionListener` | New `processRequest(IpmiPayload, int handle)`, with the handle of the connection that received the payload, which the connection calls; `processRequest(IpmiPayload)`, which it calls by default, is deprecated, and both have a default implementation (`IpmiAsyncConnector.processRequest(IpmiPayload)` still notifies the inbound listeners, of any connection; a subclass of `IpmiAsyncConnector` that overrides it must override the new method instead, which the connections call) |
| `InboundMessageListener` | New default method `isPayloadSupported(IpmiPayload, int handle)`, which the connector calls: override it to take the payloads of a single connection |
| `IpmiConnector` | New `unregisterIncomingMessageListener(InboundMessageListener)` |
| `InboundSolMessageListener` | New `readBytes(int, int timeout)`, `getLineOperations()` and `setLineOperations(Set)` |
| `ConnectionHandle` | New `getBmcKey()` and `setBmcKey(byte[])`, which keeps a copy |
| `Session`, `SessionManager`, `Connection` | New `getManagedSystemSessionId()`, and a `Session` constructor and a `registerSession()` overload that take it |
| `CipherSuite` | New `copy()`: an uninitialized instance of the same suite, since opening a session keys its suite |
| `SolMessage`, `SolResponseData` | `getAcceptedCharacterCount()` and `getAcceptedCharactersNumber()` return an `int` from 0 to 255 instead of a signed `byte`; the `SolResponseData` constructor takes the count as an `int` (the one with a `byte` is deprecated) |
| `SolCoder` | New ACK-only constructor with the line states, and `isAcknowledgeOnly()`; the operations-only constructor sends no character |
| `GetPayloadInstanceInfo` | New command (IPMI 2.0 section 24.5) |
| `GetChannelPayloadSupport(byte)`, `DeactivatePayload(PayloadType, int)`, `GetPayloadActivationStatus(PayloadType)` | Deprecated: they build requests for cipher suite 0, which a session of another suite discards |
| `MessageQueue` | New `add(PayloadCoder, boolean, int tag)`, `remove(QueueElement)`, `resetSequenceNumber()`, and `retry(int tag)`, which restarts the timeout of a request sent again; `tearDown()` reports the requests still waiting as failed |
| `IpmiConnector` | A request whose connection is closed meanwhile fails at once with `IllegalStateException` instead of being sent again |
| `IpmiMessageHandler` | The tags of the IPMI requests go from 16 to 63, above the SOL packet sequence numbers, so that a reply is never taken for one of the other payload type |

### Sensor records

`FullSensorRecord`, `CompactSensorRecord` and `EventOnlyRecord` now extend the new
[`AbstractSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/AbstractSensorRecord.html)
(itself a `SensorRecord`), which holds the fields the three record types share: sensor owner and
number, entity, sensor type, event/reading type, direction, name (ID string), capabilities, units
and record sharing. Their getters and setters keep the same signatures, so existing code compiles
unchanged, and code that handles several record types can use `AbstractSensorRecord` instead of
testing each type:

```java
if (record instanceof AbstractSensorRecord) {
	AbstractSensorRecord sensor = (AbstractSensorRecord) record;
	System.out.println(sensor.getName() + ": " + sensor.getSensorType());
}
```

A record type now also inherits the getters of fields it does not define, which return defaults:

| Record | Field | Value |
| --- | --- | --- |
| `EventOnlyRecord` (no reading) | `getRateUnit()`, `getModifierUnitUsage()`, `getSensorBaseUnit()`, `getSensorModifierUnit()` | `null` |
| `EventOnlyRecord` (no reading) | `isHysteresisReadable()`, `isThresholdsReadable()` | `false` |
| `FullSensorRecord` (a single sensor) | `getShareCount()`, `getIdInstanceModifierOffset()` | `0` |
| `FullSensorRecord` (a single sensor) | `getIdInstanceModifierType()` | `null` |
| `FullSensorRecord` (a single sensor) | `isEntityInstanceIncrements()` | `false` |

### Command responses

Commands that extend
[`IpmiCommandCoder`](apidocs/org/metricshub/ipmi/core/coding/commands/IpmiCommandCoder.html) can
call the new `protected` method `validateResponse(IpmiMessage)`, which checks that a message is a
successful response to the command and returns its data: it throws `IllegalArgumentException`
for a response to another command or a payload that is not an IPMI LAN response, and
`IPMIException` for a completion code other than `Ok`. The message of the
`IllegalArgumentException` now names the command class (three commands used to name the wrong
command). See [Writing your own command](low-level-api.html#writing-your-own-command).

## Upgrading from 1.2.01

Version 1.2.02 added the UDP port of the BMC to `IpmiClientConfiguration` (a constructor with a
`port` argument, and `setPort(int)`), 623 by default. No change is needed.

## Upgrading from 1.2.00 and earlier

Version 1.2.01 moved the project from Sentry Software to MetricsHub. The API is the same, under
new Maven coordinates and package names:

| | 1.2.00 and earlier | 1.2.01 and later |
| --- | --- | --- |
| Maven coordinates | `org.sentrysoftware:ipmi` | `${project.groupId}:${project.artifactId}` |
| Client packages | `org.sentrysoftware.ipmi.client` | `org.metricshub.ipmi.client` |
| Protocol packages | `org.sentrysoftware.ipmi.core` | `org.metricshub.ipmi.core` |

Replace the dependency, then the package prefix in the imports (`org.sentrysoftware.ipmi.` →
`org.metricshub.ipmi.`).

Version 1.2.00 had added the RAKP-HMAC-SHA256 and RAKP-HMAC-MD5 authentication algorithms and
their integrity algorithms (cipher suites 6 to 8 and 15 to 17); 1.1.00 fixed a busy loop of the
connection thread.
