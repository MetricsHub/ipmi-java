keywords: upgrade, migration, release notes, breaking changes, protected fields, accessors, abstractsensorrecord, validateresponse, utf-8, bmc key, cipher suite, integrity, org.sentrysoftware
description: What changes when upgrading the IPMI Java Client — from 1.2.02, from 1.2.01, and from the org.sentrysoftware:ipmi artifact of 1.2.00 and earlier.

# Upgrading

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

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
  ([Troubleshooting](troubleshooting.html#the-login-fails)); only a handshake step that got no
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
* `IpmiConnector.closeConnection()` releases the connection: its handle then throws
  `IllegalStateException` instead of addressing a disconnected connection, and a session that
  fails to be established by `SerialOverLan` closes its own connection instead of tearing down the
  whole connector;
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
| `IpmiLanMessage` | `networkFunction` | `getNetworkFunctionCode()`, `setNetworkFunctionCode(byte)` |
| `ConfidentialityAlgorithm` | `sik` | `getSik()` |
| `IntegrityAlgorithm` | `sik` | `getSik()`, `setSik(byte[])` |

`IpmiClient`, `IpmiResultConverter`, `Utils`, `DeviceDescription`, `ReadingTypeDescription` and
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
| `SessionManager` | New `establishSession(...)` overload with the password as a `byte[]` |
| `Rakp1` | The constructor takes the password as a `byte[]` of at most 20 bytes; `getPassword()` is removed; the new `checkCredentials(String, byte[], byte[])` checks the lengths, and the user name message now reads `Username is too long. Its length cannot exceed 16 bytes` |
| `OpenSessionAck`, `Connection.startSession()`, `ConnectionManager.startSession()` | The password is a `byte[]` |

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
