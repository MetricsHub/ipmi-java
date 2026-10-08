keywords: upgrade, migration, release notes, breaking changes, protected fields, accessors, abstractsensorrecord, validateresponse, utf-8, bmc key, org.sentrysoftware
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
  reply is sent again.

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
| `MessageHandler` | `lastReceivedSequenceNumber` | `getLastReceivedSequenceNumber()`, `setLastReceivedSequenceNumber(int)` |
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
