keywords: upgrade, migration, automatic-module-name, jpms, module name, deprecated aliases, release notes, breaking changes, behavior change, 2.1, 2.0, sensor owner, send message, get device sdr, lun, shared records, share count, non-linear, get sensor reading factors, sensor resolution, watchdog, read fru data, fru device locator, spd, dimm, pirom, processor, md5-128, maximumavailable, fluent client, builder, device id, raw, dcmi, network function, ipmiclientexception, slf4j, ipmicli, standalone jar, sel, system event log, chassis control, chassis identify, protected fields, accessors, abstractsensorrecord, validateresponse, utf-8, bmc key, cipher suite, integrity, org.sentrysoftware
description: What changes when upgrading the IPMI Java Client — from 2.0.00 to 2.1.00, from 1.x to 2.0.00 (the fluent client, the System Event Log, the fixes made since 1.2.02), from 1.2.01, and from the org.sentrysoftware:ipmi artifact of 1.2.00 and earlier.

# Upgrading

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The sections go from the latest release to the oldest: read them from the version you use up to
the top of the page. From 1.2.02, that is [Upgrading from 1.2.02](#upgrading-from-1-2-02) (the
fixes 2.0.00 brings to the protocol core, the decoders and Serial over LAN),
[Upgrading from 1.x to 2.0.00](#upgrading-from-1-x-to-2-0-00) (the new client API), then
[Upgrading from 2.0.00 to 2.1.00](#upgrading-from-2-0-00-to-2-1-00).

## Upgrading from 2.0.00 to 2.1.00

Version 2.1.00 only adds to the API of 2.0.00: no public or protected class, constructor, method,
field or enum constant is removed, so code built against 2.0.00 links unchanged, and compiles
unchanged except for the `case` labels of the two [renamed enum constants](#renamed-names). What changes is what the same calls return: the client reads each sensor and FRU from
the controller that holds it, returns every sensor of a shared record, converts the readings of
non-linear sensors, decodes the FRUs of memory modules and processors, reads the FRUs with fewer
requests, and opens sessions with the MD5-128 cipher suites and the `MaximumAvailable` privilege.
Most changes add results that 2.0.00 did not return; the ones that change the number, the
selection or the meaning of results existing code already gets are marked **Behavior change**.

### Java modules

The jar declares the automatic module name `org.metricshub.ipmi` in its manifest (it has no
`module-info`, and Java 8 ignores the entry). A modular application writes
`requires org.metricshub.ipmi;`. **Behavior change.** The `requires ipmi.java;` that 2.0.00
needed, a name the JDK derived from the jar's file name, no longer resolves.

### Renamed names

The misspelled or wrong public names of 2.0.00 are fixed. Each old name stays, deprecated, until
3.0:

| 2.0.00 | 2.1.00 | Compatibility |
| --- | --- | --- |
| `SensorType.SystemFirmwareProgess` | `SensorType.SystemFirmwareProgress` | The old name is a deprecated `static final` alias of the new constant: `==` comparisons and the `switch` statements of compiled code keep working. **Behavior change.** `name()`, `toString()` and `values()` only know the new spelling, `valueOf("SystemFirmwareProgess")` throws, and source code that uses the old name as a `case` label no longer compiles. |
| `CompletionCode.InsufficentPrivilege` | `CompletionCode.InsufficientPrivilege` | The same as above, for `D4h`. |
| `DeviceRelativeEntityAssiciationRecord` | `DeviceRelativeEntityAssociationRecord` | The SDR walk returns the new class for type `09h`. It extends the deprecated old one, so `instanceof` checks still match. |
| `CommandCodes.GET_DEVICE_SDR_INFO` = `21h` | `CommandCodes.GET_DEVICE_SDR_INFO` = `20h`, new `GET_DEVICE_SDR` = `21h` | **Behavior change.** `21h` is Get Device SDR; Get Device SDR Info is `20h` (IPMI 2.0 table G-1). The compiler copies the value of a constant into the code that uses it: recompile code that sends a request with `GET_DEVICE_SDR_INFO`, which sent Get Device SDR until then. |
| `CipherSuite.NOT_YET_IMPLEMENTED_MESSAGE` | none | Deprecated: no exception carries this message. |

### Sensors

* **Behavior change.** Each sensor is read from the owner its SDR record names
  ([Sensor owners](sensors.html#sensor-owners)), where 2.0.00 sent every Get Sensor Reading to
  LUN 0 of the BMC.
* A sensor of the BMC is read at the LUN of its record.
* A sensor of another controller is read through the BMC with Send Message: the power supply,
  fan, network and drive sensors of an HPE iLO 5 now have their readings. 2.0.00 asked the BMC
  for its own sensor of the same number, which answered with the reading of an unrelated sensor
  or with `CBh`.
* A sensor of system software is not read: it is `UNAVAILABLE`, without states, and its
  `problem()` says why. 2.0.00 returned the states of an unrelated BMC sensor for the BIOS
  sensors of Dell iDRACs (owner `B1h`).
* **Behavior change.** The sensors of other controllers need the privilege the BMC requires for
  Send Message, Operator on most BMCs: in a session below it, they are `UNAVAILABLE`, with the
  `D4h` completion code in their `problem()`. `privilege(PrivilegeLevel.MaximumAvailable)` raises
  the session on that `D4h` and reads them
  ([Sessions and cipher suites](#sessions-and-cipher-suites)).
* A controller behind the BMC that does not answer costs its own sensors only: their `problem()`
  says `No reply from the controller at 44h on channel 0 through the BMC`, the session is kept,
  and the other sensors of that controller and LUN are not requested again during the operation.
  Its first request still waits for all its tries (the retries plus one, each of one message
  timeout: 20 s with the defaults), once per operation and per controller and LUN, so several
  silent controllers can use up the deadline of the operation (2 minutes by default). 2.0.00
  never waited for another controller.
* **Behavior change.** A Compact record shared by several sensors (share count above 1, IPMI 2.0
  section 43.2) gives one `Sensor` per sensor, each read with its own Get Sensor Reading: a Dell
  iDRAC 8 now lists its drive slots `Drive 0` to `Drive 23` and its DIMM slots `A 1` to `A 12`
  and `B 1` to `B 12` ([Shared records](sensors.html#shared-records)). 2.0.00 returned the first
  sensor of each record only, under the bare ID string of the record. A BMC without shared
  records returns the same sensors as before.
* The `number()`, `name()` and `entityInstance()` of each shared sensor follow the record sharing
  fields, and its `record()` is the same object for all the sensors of the record.
* **Behavior change.** The `names()` globs of `sensors()` match the expanded names:
  `names("Drive 1?")` selects `Drive 10` to `Drive 19` on that iDRAC 8, where 2.0.00 matched the
  ID strings of its records (`Drive 0`, `Drive 15`) and selected `Drive 15` only.
* A non-linear sensor (linearization `70h`-`7Fh`) has a `reading()`, where 2.0.00 returned it
  without reading: the client sends Get Sensor Reading Factors with the raw reading and converts
  it with the factors of the reply, one more request per non-linear sensor and per operation. The
  thresholds of a non-linear sensor stay empty.
* **Behavior change.** A `problem()` no longer means that the sensor is `UNAVAILABLE`: when the
  BMC refuses Get Sensor Reading Factors, or the controller behind it does not answer it, the
  non-linear sensor has no `reading()` but keeps its `rawReading()`, `status()` and `states()`,
  and its `problem()` says why (`Get Sensor Reading Factors ... failed: ...`).
* `Sensor.name()` is never `null`: a Compact record that ends before its ID string gives the
  empty name, as an ID string of length 0 does. 2.0.00 returned `null`.
* The states of the Watchdog 1 sensor type (`11h`, deprecated since IPMI 1.5 in favor of
  Watchdog 2 but still emitted by older BMCs) are named: `Sensor.states()` and
  `SelEntry.description()` say `BIOS Reset`, `OS Reset`, `OS Shut Down`, `OS Power Down`,
  `OS Power Cycle`, `OS NMI/Diag Interrupt`, `OS Expired` and `OS pre-timeout Interrupt`, as
  `ipmitool` does, and `Sensor.rawStates()` and `SelEntry.event()` give the new `ReadingType`
  constants ([Protocol core](#protocol-core)). 2.0.00 reported these states as `Unknown`, and
  the SEL events as `Unknown event type 6Fh offset <n>h`.
* A BMC without SDR repository that holds the SDRs of its own sensors is walked with Get Device
  SDR, where 2.0.00 failed the operation. The client does so when the BMC refuses Get SDR
  Repository Info and its Get Device ID reports a Sensor Device that provides Device SDRs and no
  SDR Repository Device: it sends that Get Device ID in every operation on a BMC that refuses Get
  SDR Repository Info, and reads the Device SDRs again in every operation, as they have no
  timestamps that tell whether the [SDR cache](configuration.html#sdr-cache) is current.
* A BMC that refuses Get SDR Repository Info and answers `CBh` (record not present) to the first
  Get SDR has no records, where 2.0.00 failed `sensors()` and `frus()` with
  `IpmiCompletionCodeException`.
* A BMC whose next record IDs loop (the last record names `0000h` or a record already read
  instead of `FFFFh`) returns its sensors and FRUs, each record once, with the loop reported in
  `skippedRecords()`, where 2.0.00 read the repository again and again until the deadline of the
  operation failed it with `IpmiTimeoutException`.
* **Behavior change.** The `states()` of a threshold sensor list the thresholds its reading is at
  or beyond, in the wording of their SEL events: `[Upper Non Critical Going High, Upper Critical
  Going High]` for a temperature above its upper critical threshold. 2.0.00 always returned an
  empty list for a threshold sensor, although its documentation said otherwise; `status()` and
  `rawStates()` do not change. A caller that takes any state as a discrete state to report sees
  the threshold sensors beyond a threshold too.
* **Behavior change.** A discrete sensor (event/reading type other than `01h`) described by a
  Full record has no `reading()` and no thresholds, and the client no longer sends Get Sensor
  Reading Factors for it ([Readings](sensors.html#readings)). 2.0.00 converted its reading byte,
  which IPMI 2.0 table 35-15 says to ignore, into a meaningless value (`0.0` when the record left
  M and B at 0). `rawReading()` is unchanged. On the lab BMCs (HPE iLO 5, Dell iDRAC, Cisco IMC, Lenovo,
  Supermicro, Fujitsu iRMC, GIGABYTE), every discrete Full record declares no analog reading
  (data format `11b`), so none had a `reading()` in 2.0.00 either.
* **Behavior change.** The threshold and generic states (event/reading types `01h` to `0Ch`) of a
  sensor of an OEM sensor type (`C0h`-`FFh`) or of a reserved one are decoded as for any sensor
  type (IPMI 2.0 section 42.1): a presence sensor of type `C0h` has the state `Device Present`,
  where 2.0.00 returned `UnknownOEMEvent` in `rawStates()` and no state in `states()`. The
  sensor-specific states (`6Fh`) of an OEM sensor type stay `UnknownOEMEvent`.
* **Behavior change.** `Sensor.type()` is the new `SensorType.Reserved` for the codes IPMI 2.0
  table 42-3 reserves (`00h`, `2Dh`-`BFh`, `76h` included), where 2.0.00 returned `Oem`
  (`OemReserved` for `76h`): `types(SensorType.Oem)` no longer selects them, and
  `types(SensorType.Reserved)` does. The new `Sensor.typeCode()` gives the code of the record,
  which `Oem` and `Reserved` do not keep, and `toString()` shows it (`Oem C1h`).
* The new `Sensor.percentage()` tells a sensor whose reading and thresholds are a percentage
  (Sensor Units 1 bit 0), of `unit()` unless it is `Unspecified`; `toString()` shows its unit as
  `ipmitool` does (`percent`, `% Rpm`).

### System Event Log

* **Behavior change.** The threshold and generic events of an OEM or reserved sensor type are
  decoded: `event()` is `DevicePresent`, `UpperCriticalGoingHigh`... and `description()`
  `Device Present`, where 2.0.00 gave `UnknownOEMEvent` and `OEM event type 08h offset 1h`.
* **Behavior change.** `sensorType()` is `Reserved` for a reserved sensor type code, where 2.0.00
  returned `Oem` (`OemReserved` for `76h`). The new `sensorTypeCode()` gives the code of the
  record.
* **Behavior change.** The `description()` of an event the specification does not define, of an
  OEM or reserved sensor type, gives the sensor type code: `OEM sensor type C1h, event type 6Fh
  offset 2h` and `Reserved sensor type 2Dh, event type 6Fh offset 2h`, where 2.0.00 said
  `OEM event type 6Fh offset 2h` (`Unknown event type 6Fh offset 2h` for `76h`). `toString()`
  gives the code with the sensor type: `Oem C1h sensor 5`.

### Chassis status

* `ChassisStatus.identifyState()` returns the new `ChassisIdentifyState.Unknown` when the BMC
  reports the reserved identify state `11b`, where 2.0.00 threw `IllegalArgumentException` from
  `identifyState()` and `toString()`, and `ipmicli chassis` failed with a stack trace.

### FRU inventory

* Read FRU Data asks for 255 bytes at a time instead of 16
  ([Read size](fru-inventory.html#read-size)): the FRUs of most BMCs are read with up to 16 times
  fewer requests.
* A controller that refuses the count (`C7h` to `CAh`) is asked for the power of two below the
  count sent, down to 16, and the count it accepts serves its other FRUs during the operation: a
  controller that answers 16 bytes only (a Dell iDRAC 8 or a Cisco IMC) costs up to four refused
  requests per operation. A word-addressed FRU device is read an even number of bytes at a time
  (254 instead of 255), except for its last read.
* A reply with fewer bytes than asked is continued from where it stopped. 2.0.00 read on from 16
  bytes after the previous offset, which dropped the missing bytes and shifted the rest of the
  inventory.
* The `problems()` of a FRU truncated by a refused read or a malformed reply give the exact offset
  reached, where 2.0.00 gave a multiple of 16.
* A reply without data, at an offset within the size the controller declared, ends the read with
  `No data returned`, where 2.0.00 reported `Invalid response payload length`.
* **Behavior change.** Each FRU is read from the controller and at the LUN its FRU Device Locator
  record names (IPMI 2.0 table 43-7,
  [How the FRUs are read](fru-inventory.html#how-the-frus-are-read)), where 2.0.00 read every FRU
  from LUN 0 of the BMC. None of the [tested BMCs](preparing-the-bmc.html#tested-bmcs) has a FRU
  on another controller or LUN: this change follows the specification.
* A FRU of the BMC is read at the access LUN of its record.
* A FRU of another controller is read through the BMC with Send Message. 2.0.00 asked the BMC for
  its own FRU of the same ID, which came back absent or with the inventory of an unrelated device.
* **Behavior change.** Two FRUs with the same ID on different controllers or LUNs are both
  returned, where 2.0.00 returned the first one only
  ([FRUs of other controllers](fru-inventory.html#frus-of-other-controllers)).
* **Behavior change.** FRU 0 of the BMC is returned whenever the BMC serves it
  ([How the FRUs are read](fru-inventory.html#how-the-frus-are-read)). 2.0.00 read it on every
  `frus()` but returned it only when a FRU Device Locator record or a Compact Sensor record of the
  System Board entity anchored it, and dropped it otherwise: a BMC whose System Board sensors are
  all Full or Event-Only records, or that has none, now returns its system board FRU, after the
  other FRUs. Among the [tested BMCs](preparing-the-bmc.html#tested-bmcs), this is the HPE iLO 5:
  `frus().ids(0)` returned nothing with 2.0.00, and now returns the model and serial number of
  the server. The other tested BMCs return the same FRUs as before.
* **Behavior change.** FRU 0 of each other controller whose Management Controller Device Locator
  record sets FRU Inventory Device is read through the BMC and returned, which 2.0.00 never did.
  None of the [tested BMCs](preparing-the-bmc.html#tested-bmcs) sets this bit for another
  controller: this change follows the specification.
* The FRUs of other controllers need the privilege the BMC requires for Send Message, Operator on
  most BMCs: below it, they are returned without inventory, with the `D4h` in their `problems()`.
* A controller behind the BMC that does not answer costs its own FRUs only, after its first
  request waited for all its tries (20 s with the defaults), once per operation.
* The FRU of a memory module is decoded: its FRU device returns the JEDEC SPD data of the module,
  which 2.0.00 refused with `Cannot decode FRU <id>: Invalid format version: <n>` (`35` for most
  DDR4 modules). The new `Fru.spd()` gives an `SpdInfo` for a DDR3, DDR4 or DDR5 module: memory
  and module type, capacity, ECC, module and DRAM manufacturers, part number, serial number and
  manufacturing date ([Memory modules](fru-inventory.html#memory-modules)). The SPD data of
  another memory type (DDR2, LPDDR4...) is reported in `problems()`.
* The FRU of an Intel processor is decoded: its FRU device returns the Processor Information ROM
  (PIROM) of the CPU, which 2.0.00 refused with
  `Cannot decode FRU <id>: Invalid format version: 7`. The new `Fru.pirom()` gives a `PiromInfo`
  for data format revision 7: S-spec, part number, PPIN, CPUID, cores, frequencies, caches, NVM
  DIMM capacity, thermal design power and temperatures
  ([Processors](fru-inventory.html#processors)). A PIROM of another revision is reported in
  `problems()`.
* `manufacturer()`, `model()`, `serialNumber()` and `partNumber()` of a FRU whose product and
  board areas do not give them fall back to the module manufacturer, part number, serial number
  and part number of a memory module, and to `Intel`, the S-spec, the PPIN and the part number of
  a processor.

### Sessions and cipher suites

* `privilege(PrivilegeLevel.MaximumAvailable)`, which the builder refused in 2.0.00, runs the
  session at User and raises it on demand
  ([Raising the session on demand](configuration.html#raising-the-session-on-demand)).
* The login of such a session requests Administrator, then Operator, then User while the BMC
  refuses the role to the account; the session then runs at User.
* When the BMC refuses a command for insufficient privilege (`D4h`), the session is raised with
  Set Session Privilege Level, to Operator then to Administrator, and the command is sent again.
  `sol().open()` raises the session the same way when the BMC refuses to activate the payload.
* A level the BMC refuses is the ceiling of the session: the `D4h` then reaches the caller, as at
  a fixed level.
* Cipher suites 11 and 12 (MD5-128 integrity, IPMI 2.0 section 13.28.4: a plain MD5 of the
  password around each message) open sessions, where 2.0.00 failed the login with
  `Integrity algorithm MD5-128 is not yet implemented.`
  `CipherSuite.isSupported()` is `true` for them.
* **Behavior change.** The default preference of the builder and of `ipmicli -C` becomes
  `17, 3, 8, 16, 2, 7, 12, 11`: a BMC that offers one of the first six gets the same suite as
  before, and a BMC whose only suites with integrity are 11 and 12 now connects with one of them,
  where 2.0.00 failed with *offers none of the cipher suites*. `cipherSuites(17, 3, 8, 16, 2, 7)`
  (`ipmicli -C 17,3,8,16,2,7`) keeps the preference of 2.0.00, which never uses MD5-128.
* The xRC4 suites (4, 5, 9, 10, 13, 14, 18, 19) stay unsupported, by decision: xRC4 is optional
  for BMCs in IPMI 2.0 (table 13-19), and none of the
  [tested BMCs](preparing-the-bmc.html#tested-bmcs) requires it. Their exception says
  `is not implemented.` instead of `is not yet implemented.`
* The failures of Set Session Privilege Level when the session opens say *set* instead of
  *raise*: the `IpmiClientException` of a failure other than a refusal of the level (a busy BMC)
  says `could not set the session to the <level> privilege`, and the `SessionException` of
  `openSession()` says `The BMC refused to set the session to the <level> privilege`. With
  `MaximumAvailable`, the level named is `User`.
* **Behavior change.** `cipherSuites()` and `cipherSuite()` refuse the xRC4 suites (4, 5, 9, 10,
  13, 14, 18, 19) with `IllegalArgumentException`, as they refuse the suites without integrity.
  In 2.0.00, `cipherSuites(18, 17)` used suite 18 on a BMC that offers it, and failed the login
  instead of moving on to suite 17.
* The library wipes its copies of the password and of the BMC key, so that once the client is
  closed and the caller has wiped its `char[]` and `byte[]`, none is left in memory. 2.0.00 left
  up to 7 copies of the password and 3 of the key for each session, unwiped. The RAKP HMACs are
  computed with `MessageDigest` (RFC 2104) instead of a `Mac`, whose `SecretKeySpec` kept a copy
  of the key that cannot be wiped. **Behavior change** in the low-level API:
  `IpmiConnector.closeSession()`, `closeConnection()`, `tearDown()` and a failed `openSession()`
  wipe the password and the BMC key of the `ConnectionHandle` (the new
  `ConnectionHandle.wipeCredentials()`), whose `getPasswordBytes()`, `getPassword()` and
  `getBmcKey()` then return zeros, and the key of its integrity algorithm (the new
  `CipherSuite.wipeKeys()` and `IntegrityAlgorithm.wipeKey()`), which no longer generates the
  codes of the session.
* After a control operation (`chassisControl()`, `chassisIdentify()`,
  `chassisIdentifyForever()`) whose reply is lost, the next operation first checks the session
  with a Get Device ID, and opens a new session when that gets no reply either. In 2.0.00, a
  client that sent only control operations kept a session the BMC had dropped (after a reboot or
  an expiry) and every call timed out.

### Serial over LAN

* `SolConsole.input()` follows the `InputStream` contract: `available()` returns the number of
  characters a read returns without blocking, where 2.0.00 always returned 0 (so a
  `BufferedReader` over it is `ready()`), and a read returns what is buffered at once, where
  2.0.00 waited 200 ms for more characters unless as many as asked were there. `ipmicli sol`
  shows the output of the console without that delay.
* **Behavior change.** An error reply to a command of `sol().open()` is an
  `IpmiCompletionCodeException` that names that command (`networkFunction()`, `command()`, and
  the message `GetPayloadActivationStatus failed: ...`, `ActivateSolPayload failed: ...`,
  `SetSessionPrivilegeLevel failed: ...`), where 2.0.00 said `Activating the SOL payload failed`
  whatever the command, with a `null` network function and a command of -1. The message describes
  the codes of Activate Payload (`80h`-`84h`, IPMI 2.0 table 24-2), such as `Payload type is
  disabled` for `81h` and `Cannot activate the payload without encryption` for `84h`.
* **Behavior change.** A command of `sol().open()` that gets no reply after all the tries throws
  `IpmiTimeoutException` (`No reply from <host> to <command> after <n> tries`), where 2.0.00 threw
  a plain `IpmiClientException` (`Cannot activate SOL payload due to exception during activation
  process`). When the command went through the session of the client, the session is lost, and
  the next operation, `sol().open()` included, opens a new one: in 2.0.00, a client that only
  opened consoles kept a session the BMC had forgotten, and every `open()` failed.
* A second `sol().open()` while a console of the same client holds the only payload instance
  fails with `The SOL payload is held by a console this client already opened: close it first`,
  where 2.0.00 blamed `another session`.
* With `MaximumAvailable`, `sol().open()` raises the session of the client as every other
  operation does: a level the BMC refuses (`80h`, `81h` or `D4h` to Set Session Privilege Level)
  is the ceiling of the session, and `open()` fails with the `D4h` of Activate Payload, as at a
  fixed level. The level reached is recorded on failure too, so a later `open()` asks for no level
  the session already has or the BMC refused.

### Command-line client

* `-L auto` runs the session as `privilege(PrivilegeLevel.MaximumAvailable)` does; a command the
  BMC refuses at the highest level of the account exits with code 77, with a diagnostic that says
  so.
* `-C` defaults to `17,3,8,16,2,7,12,11`, and `probe` lists suites 11 and 12 as `supported`.
* **Behavior change.** The `Type` column of `sensors` and `sel`, and the `type` and `sensorType`
  fields of their `--json` output, spell sensor type `0Fh` `SystemFirmwareProgress`, where 2.0.00
  printed `SystemFirmwareProgess`. `sensors --type` accepts both spellings.
* **Behavior change.** `-C` with an xRC4 suite (4, 5, 9, 10, 13, 14, 18, 19) is a usage error
  (exit code 64), and `probe` no longer says `not implemented by the library` of the chosen suite.
* `chassis` prints `Unknown` as the identify state of a BMC that reports the reserved value.
* `fru` prints the SPD data of memory modules and the PIROM of processors; `fru --json` gives
  them as `spd` and `pirom` objects.
* **Behavior change.** `sensors --json` and `sel --json` give the sensor type code
  (`typeCode`, `sensorTypeCode`), the `Type` column of `sensors` and `sel` the code of an OEM or
  reserved sensor type (`Oem C1h`, `Reserved 2Dh`, where 2.0.00 printed `Oem`), and the `Unit`
  column of `sensors` a percentage as `ipmitool` does (`percent`, `% Rpm`), with
  `"percentage":true` in `--json`.
* **Behavior change.** `sensors --json` lists the thresholds a threshold sensor is at or beyond in
  `states`, which 2.0.00 left empty; the `Status` column still shows the `status()` of a threshold
  sensor (`critical`). A discrete sensor described by a Full record no longer has a `Reading` or
  a `reading` field.
* `sensors` lists the sensors of shared records and of other controllers as `sensors()` returns
  them, and no longer fails with a `NullPointerException` on a sensor without ID string, which
  2.0.00 printed as `"name":null` with `--json`.

### Messages

The messages built from a byte print it in hexadecimal, and a completion code is named once,
after a sentence without its final period. **Behavior change** for code that matches these
messages:

| 2.0.00 | 2.1.00 |
| --- | --- |
| `ReadFruData failed: Requested Sensor, data, or record not present. (completion code CBh)` | `ReadFruData failed: Requested Sensor, data, or record not present (completion code CBh)` |
| `ReadFruData failed: Command-specific completion code 0x81. (completion code 81h)` | `ReadFruData failed: Command-specific completion code 81h` |
| `Get Sensor Reading failed: Unknown completion code. (completion code 81h)` | `Get Sensor Reading failed: Command-specific completion code 81h` |
| `Invalid format version: -1` | `Invalid format version FFh` |
| `Invalid format version` (board, chassis and product info areas) | `Invalid format version 80h` |
| `Invalid record type: 11` | `Reserved record type 0Bh` |
| `This format is deprecated` | `The BMC Message Channel Info record (type 14h) is deprecated since IPMI 1.5` |

The same goes for the messages of `IpmiCompletionCodeException`, `Sensor.problem()`,
`Fru.problems()`, `SkippedRecord.reason()` and of a refused privilege raise.
`IPMIException.getMessage()` says `Command-specific completion code 81h.` instead of
`... 0x81.`, and the new `IPMIException.describe()` gives the message with the code.

### Protocol core

* **Behavior change.** `FullSensorRecord.getSensorResolution()` returns `|M x 10^R|`, the step
  between two successive raw readings of a linear sensor (IPMI 2.0 section 36.4.2), where 2.0.00
  returned half of it whatever the linearization. It returns `NaN` for a linearizable or
  non-linear sensor, whose resolution varies with the reading.
* **Behavior change.** `FullSensorRecord.hasAnalogReading()` is `false` for a discrete sensor
  (event/reading type other than `01h`). `calcFormula()` does not depend on it and still converts
  any reading byte.
* **Behavior change.** `SensorType.parseInt()` returns the new `SensorType.Reserved` for `00h` and
  `2Dh`-`BFh`, where it returned `Oem`, and for `76h`, where it returned `OemReserved`, now
  deprecated. The new `getSensorTypeCode()` of `AbstractSensorRecord` and `SelRecord` keeps the
  code that `Oem` and `Reserved` do not.
* **Behavior change.** `ReadingType.parseInt()` returns `UnknownOEMEvent` for an OEM sensor type
  with the sensor-specific event/reading type (`6Fh`) only, and `Unknown` for a reserved sensor
  type with `6Fh`; the threshold and generic states decode as for any sensor type. 2.0.00 returned
  `UnknownOEMEvent` for every state of an OEM or reserved sensor type.
* `AbstractSensorRecord.isPercentage()` decodes the Percentage bit of Sensor Units 1 of the Full
  and Compact records.
* **Behavior change.** `FullSensorRecord.hasAnalogReading()` is `true` for the non-linear
  sensors. `calcFormula(int)` still returns `NaN` for them: the new
  `calcFormula(int, SensorReadingFactors)` converts their reading byte with the factors of Get
  Sensor Reading Factors, and the new `isNonLinear()` tells them apart.
* **Behavior change.** `SessionManager.establishSession()`, and the `SerialOverLan` constructors
  that open their own session, fail with `SessionException` before sending Open Session when the
  `CipherSuiteSelectionHandler` returns `null`, where 2.0.00 opened the session with cipher suite 0
  (no authentication, integrity or confidentiality) without telling the caller. A selector
  returns a suite or throws; `new SpecificCipherSuiteSelector(CipherSuite.getEmpty())` still asks
  for suite 0 explicitly.
* **Behavior change.** `GetChassisStatusResponseData.getChassisIdentifyState()` throws
  `IllegalStateException` when the BMC does not report the identify state, instead of an
  `IllegalAccessError`, which a `catch (Exception e)` did not catch, and returns
  `ChassisIdentifyState.Unknown` for the reserved value `11b`.
* `CipherSuite.listIds(List)` formats the IDs of suites for messages.
* `getName()` of the Full, Compact and Event-Only sensor records is never `null`: `setName(null)`
  stores the empty string.
* `ReadingType` has the eight offsets of Watchdog 1: `BiosWatchdogReset`, `OsWatchdogReset`,
  `OsWatchdogShutDown`, `OsWatchdogPowerDown`, `OsWatchdogPowerCycle`,
  `OsWatchdogNmiDiagnosticInterrupt`, `OsWatchdogExpired` and `OsWatchdogPreTimeoutInterrupt`,
  for which `parseInt()` returned `Unknown` in 2.0.00.
* **Behavior change.** `openSession()` of a connection created with `MaximumAvailable` requests
  Administrator at Open Session and RAKP Message 1, then Operator, then User while the BMC refuses
  the role, and sets the session to User right after the handshake; an account limited to User
  costs up to three logins. 2.0.00 requested the role `0h` of the specification ("highest level
  matching the proposed algorithms"), which HPE iLO 5, Dell iDRAC, Cisco IMC and GIGABYTE BMCs
  refuse at login, and which leaves a session that Lenovo ThinkServer System Manager (TSM), Supermicro and Fujitsu iRMC BMCs
  will not set to any level. A `SetSessionPrivilegeLevel` sent after `openSession()` raises the
  session up to the role the login obtained.
* `SerialOverLan` raises a `MaximumAvailable` session when the BMC refuses to activate the payload
  at its level (`D4h`): to Operator, then to Administrator, as far as the account allows, where
  2.0.00 raised it to Administrator at once. The new `getRaisedPrivilegeLevel()` returns the level
  it reached, `null` when it raised none.
* **Behavior change.** A refusal of that raise (`80h`, `81h` or `D4h` to Set Session Privilege
  Level) ends the raises: `SerialOverLan` fails with the `SOLException` of the `D4h` of Activate
  Payload, with the refusal as a suppressed exception, where the refusal was the cause. The new
  `SerialOverLan(connector, session, level, ceiling)` raises a given session from `level` as far
  as `ceiling` only.
* `SOLException` gives the command that failed (`getCommand()`) and the connection it was sent on
  (`getConnectionHandle()`), and its message starts with the name of the command and ends with the
  message of its cause. 2.0.00 said `Cannot activate SOL payload due to exception during
  activation process`, without the cause.
* `SerialOverLan.available()` returns the number of bytes buffered, and `getSession()` the session
  of the payload.
* `IPMIException` takes the description of a command-specific completion code
  (`IPMIException(CompletionCode, int, String)`), which its `getMessage()` returns. A command
  describes its codes by overriding `IpmiCommandCoder.describeCommandSpecificCompletionCode(int)`:
  `ActivatePayload` describes `80h`-`84h`, and `DeactivatePayload` its `81h`.
* `CipherSuite.initializeAlgorithms(sik, password)` keys the MD5-128 integrity (the new
  `IntegrityMd5_128`) with the password. The one-argument `initializeAlgorithms(sik)` still works
  for every other suite, and throws `IllegalArgumentException` for the MD5-128 suites rather than
  sign with an empty password.
* `ReadFruData.decodeFruData()` returns a single `SpdInfo` for the SPD data of a memory module and
  a single `PiromInfo` for the PIROM of a processor, where it threw `IllegalArgumentException`
  (`Invalid format version: ...`). The `SpdInfo(byte[], int)` constructor, a stub that ignored its
  arguments in 2.0.00, decodes the data and throws `IllegalArgumentException` for data it does not
  decode.
* **Behavior change.** `ReadFruData` accepts a reply with a count of 0 and no data, which 2.0.00
  refused with `IllegalArgumentException` (`Invalid response payload length`): a loop that moves
  on by the bytes each reply returns must stop on an empty reply, as the loop of
  [Upgrading from 1.2.02](#upgrading-from-1-2-02) does. It refuses a reply shorter than its count
  with `IllegalArgumentException` instead of an `ArrayIndexOutOfBoundsException`.
* New commands: `SendMessage`, which carries any command to a controller behind the BMC
  ([Commands for another controller](low-level-api.html#commands-for-another-controller));
  `GetSensorReadingFactors`, whose `GetSensorReadingFactorsResponseData` gives a
  `SensorReadingFactors`; `GetDeviceSdrInfo` (with `GetDeviceSdrInfoResponseData`),
  `GetDeviceSdr` and `ReserveDeviceSdrRepository`.
* New constructors with a LUN: `GetSensorReading(sensorId, lun)`,
  `GetFruInventoryAreaInfo(fruId, lun)` and `ReadFruData(fruId, unit, offset, countToRead, lun)`.
  The existing constructors address LUN 0, as before. `GetSensorReadingFactors` and
  `GetDeviceSdrInfo` take a LUN too.
* `IpmiCommandCoder.answers(IpmiLanMessage)` tells whether a reply received under the tag of a
  request answers it. A command that gets more than one reply overrides it: `SendMessage` waits
  past a successful response to Send Message without data for the response of the controller.
* New `protected` helpers for the subclasses: `IpmiCommandCoder.checkLun(int)`, which checks the
  LUN of a command; `IpmiCommandCoder.encodeRequest(IpmiCommandCoder, int)`, for a command that
  carries another command in its data; `FruRecord.ascii(byte[], int, int)`, which decodes a
  fixed-length ASCII field.
* Also new: `PiromInfo` and `PiromInfo.isPirom()`, the getters of `SpdInfo` and `SpdInfo.isSpd()`,
  and `SetSessionPrivilegeLevel.getPrivilegeLevel()`, the level the command requests.

## Upgrading from 1.x to 2.0.00

Version 2.0.00, the release that follows 1.2.02 (no 1.2.03 was released), replaces the client
API of 1.x with a fluent client, removes the SLF4J dependency, and simplifies the low-level
commands. The protocol core keeps its packages and most of its API
([The low-level API](#the-low-level-api)); the fixes 2.0.00 brings to it, to the decoders and to
Serial over LAN are listed in [Upgrading from 1.2.02](#upgrading-from-1-2-02).
Version 2.0.00 also adds a command-line client, `ipmicli`, in a standalone jar
([Command-Line Client](cli.html)), with one-command installers for Linux, macOS and Windows
([Installation](installation.html#command-line-client)); `CipherSuite` gains
`getAuthenticationAlgorithmCode()`, `getIntegrityAlgorithmCode()` and
`getConfidentialityAlgorithmCode()` for it. The semicolon-separated text output of 1.x
(`IpmiResultConverter`) is MetricsHub's format, and moves to the
[MetricsHub IPMI extension](https://github.com/MetricsHub/metricshub-community/tree/main/metricshub-ipmi-extension),
where it stays byte-identical to 1.2.x for the same BMC data, except for the sensor and FRU names
that 1.x padded with NUL characters and the OS Boot states that `ReadingType` now names (see
[The low-level API](#the-low-level-api)).

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

// 2.0.00 and later
try (IpmiClient bmc = IpmiClient.builder(host)
		.credentials(user, password)
		.bmcKey(bmcKey)
		.timeout(Duration.ofSeconds(timeoutSeconds))
		.build()) {
	FruList frus = bmc.frus().execute();
	SensorList sensors = bmc.sensors().execute();
}
```

New in the client:

* [`capabilities()`](apidocs/org/metricshub/ipmi/client/BmcCapabilities.html) reads the cipher
  suites and authentication options of a BMC without credentials;
* [`deviceId()`](device-id.html) identifies the BMC: manufacturer and product IDs, firmware
  revision, IPMI version, functions;
* [`sel()`](system-event-log.html) reads the System Event Log as typed entries (the sensor, the
  event in the wording of `ipmiutil`, the direction, the timestamp, the OEM bytes), whole or since
  the last entry read;
* [`chassisControl(PowerCommand)`](chassis-status.html#controlling-the-power) powers the system
  on or off, cycles or resets it, and `chassisIdentify(seconds)` blinks the identify LED;
* [`send(command)`](low-level-api.html#sending-a-command-through-the-client) sends any command of
  the core in the session;
* [`raw(netFn, command)`](low-level-api.html#raw-requests) sends any request as bytes, the
  extension and OEM network functions included (DCMI `2Ch`, OEM `30h`-`3Eh`), and returns its
  completion code and data without throwing (`ipmicli raw` is its subcommand);
* [`sol().open()`](serial-over-lan.html#with-ipmiclient) opens a Serial over LAN console as
  streams;
* `privilege(PrivilegeLevel)` opens the session at the Operator or Administrator level: the
  specification starts every session at User, and the client raises it with Set Session Privilege
  Level after the handshake;
* the [SDR cache](configuration.html#sdr-cache) makes `frus()` and `sensors()` share one
  repository walk.

### Logging

The library no longer depends on `org.slf4j:slf4j-api` and never logs. The failures reach the
caller as exceptions; the problems that do not fail an operation are reported in its result (see
the table above); the failures nobody can act on (a lost keep-alive, a Close Session without
reply) are ignored.

### The low-level API

* The command constructors no longer take `IpmiVersion`, `CipherSuite` and `AuthenticationType`:
  `new GetChassisStatus()`, `new GetSdr(reservationId, recordId)`,
  `new ChassisControl(PowerCommand.PowerUp)`, `new SolCoder(message)` are complete commands, and
  the session sets the parameters when it encodes the request
  (`PayloadCoder.setSessionParameters()` still exists for code that encodes a command itself).
  The payload command constructors that took a `CipherSuite`
  (`DeactivatePayload(CipherSuite, PayloadType, int)`,
  `GetPayloadActivationStatus(CipherSuite, PayloadType)`,
  `GetChannelPayloadSupport(byte, CipherSuite, AuthenticationType)`) are removed:
  `new DeactivatePayload(PayloadType, int)`, `new GetPayloadActivationStatus(PayloadType)` and
  `new GetChannelPayloadSupport(byte)`, which built requests for cipher suite 0 in 1.x, now build
  them for the cipher suite of the session.
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
  new `IpmiLanRequest` constructor that takes the network function code as an `int`
  ([Extension and OEM network functions](low-level-api.html#extension-and-oem-network-functions)).
  An `IpmiLanRequest` subclass that set the protected `networkFunction` field of 1.2.x to such a
  code must be replaced with this constructor and override, or its replies are ignored and its
  requests time out.
* `IpmiConnector.openSession()` and `IpmiAsyncConnector.openSession()` raise the session to the
  requested privilege with Set Session Privilege Level right after the handshake when it is
  `Operator` or `Administrator` (IPMI 2.0 section 22.18). In 1.x the session stayed at User on the
  BMCs that follow the specification (Dell iDRAC 7 and 8, HPE iLO 5, Lenovo XClarity Controller
  (XCC), Cisco IMC), where its Operator and Administrator commands failed with `D4h`. A BMC that
  refuses the level now fails `openSession()` with `SessionException` (whose cause is the
  `IPMIException` of the completion code) after closing the session, where 1.x returned a session
  running at a lower level: request the level the commands need. A `SetSessionPrivilegeLevel`
  sent after `openSession()` is now redundant and harmless. The `SerialOverLan` constructors that
  open their own session request Administrator, and now fail with `SessionException` for an
  account not allowed Administrator: open the session at the level the payload needs and pass it
  to `SerialOverLan(connector, session)`; the console raises a session itself only when it was
  opened with `MaximumAvailable`.
* `getAvailableCipherSuites()` first sends Get Channel Authentication Capabilities in the IPMI
  v1.5 format (IPMI 2.0 section 13.13), and sends the RMCP+ Get Channel Cipher Suites only when the
  reply reports IPMI v2.0 connections (IPMI 2.0 table 22-15). An IPMI 1.5-only BMC fails at once
  with the new `Ipmi20UnsupportedException`, a `ConnectionException` that is not sent again
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
  `GetSelTimeUtcOffset` command reads the UTC offset of a local SEL time (IPMI 2.0 revision 1.1).
  `TypeConverter.decodeDate()` decodes the IPMI timestamps as unsigned (IPMI 2.0 section 37.1):
  the dates from 2038 on, and the `FFFFFFFFh` marker of an unspecified timestamp, were negative
  dates in 1.x. It is used by the SEL and SDR repository timestamps.
* `ReadingType` has the four offsets that IPMI 2.0 revision 1.1 adds to the OS Boot sensor type
  (`1Fh`, Base OS/Hypervisor installation, `07h` to `0Ah`): `BaseOsInstallationStarted`,
  `BaseOsInstallationCompleted`, `BaseOsInstallationAborted` and `BaseOsInstallationFailed`. In
  1.x, `parseInt()` returned `Unknown` for them, and so did the SEL entries and the sensor states;
  `Sensor.states()` and `SelEntry.description()` now say `Installation started`,
  `Installation completed`, `Installation aborted` and `Installation failed`, as `ipmitool` does.
* The unused leftovers of the protocol core are removed: the `UdpNotifier` and `SessionUpkeep`
  classes (the keep-alive is a Get Device ID sent by the connection),
  `StateMachine.isSessionChallenging()`, `ConnectionManager.getConnection(InetAddress, int)`,
  `UdpMessenger.getBufferSize()` and `setBufferSize(int)` (which the receiver ignored), the
  `MessageQueue` methods `removeAt(int)`, `containsId(int)` (use `getElement(tag) != null`),
  `getMessageFromQueue(int)`, `getMessageIndexFromQueue(int)`, `getMessageRetries(int)` and
  `getMessageSequenceNumber(int)`, and the `QueueElement(int, PayloadCoder)` constructor, `setId()`,
  `getRetries()`, `setRetries()`, `getResponse()` and `setResponse()`. `UdpMessenger` receives
  datagrams of any size, where 1.x truncated them at 512 bytes, and sends without pausing 1 ms
  after each datagram.

## Upgrading from 1.2.02

No 1.2.03 was released: the changes below were made after 1.2.02 and shipped in 2.0.00, together
with the new client API, the end of the logging and the unchecked exceptions described in
[Upgrading from 1.x to 2.0.00](#upgrading-from-1-x-to-2-0-00). They are written as 2.0.00
behaves; [Upgrading from 2.0.00 to 2.1.00](#upgrading-from-2-0-00-to-2-1-00) lists what changed
since.

The client is more tolerant of real-world BMCs:

* the SDR repository walk no longer aborts on a record type the library does not model: OEM
  records (`C0h`-`FFh`) are decoded as `OemRecord`, and any record that cannot be decoded is
  skipped and reported in `skippedRecords()` of the `SensorList` and the `FruList`, where 1.2.02
  returned no sensors and no FRUs at all
  ([OEM and unknown records](supported-commands.html#oem-and-unknown-records));
* a BMC that answers a whole-record Get SDR with a truncated record is read again in chunks;
* the user name and password are encoded in UTF-8 whatever the platform charset (1.2.02 used the
  platform charset, UTF-8 by default only since Java 18), and the
  [BMC key](configuration.html#bmc-key) is used as raw bytes: a key with bytes `80h` or above no
  longer gets corrupted into a wrong session key;
* the user name is limited to 16 bytes once encoded, as the IPMI specification requires, instead of
  16 characters;
* a lost UDP reply is recovered in seconds instead of stalling the call: the per-message timeout
  is 5 s by default (it was 5 minutes), a retried message waits for the reply of the resent
  request, and the handshake steps wait for the elapsed time rather than a number of sleeps
  ([Timeouts and Errors](timeouts-and-errors.html));
* a request is sent again at once after a lost reply or a timeout on the BMC side (`C3h`), where
  1.2.02 paused for a random time of up to `idleTime` (4 s) before each resend; with the 5 s
  message timeout, these pauses made a collection on a Dell iDRAC 8, which answers `C3h` for its
  absent FRUs and loses a few replies, take over two minutes; the pause now only follows the
  completion codes that say the BMC is busy;
* a BMC that never answers now fails the session handshake about 20 s into the call with
  `IpmiConnectionException` (*No reply from ... to the session setup*, whose cause is the
  `ConnectionException: Command timed out` of the low-level API), where 1.2.02 threw
  `TimeoutException` at the overall timeout;
* the deadline of an operation cancels it for good: the operation stops at its current wait, and
  the client drops its session, which the next operation opens again
  ([Deadline](timeouts-and-errors.html#deadline)); `close()` releases the UDP port even when an
  operation is stuck in a call that cannot be interrupted, such as name resolution; the receiving
  and timer threads are daemon threads, so a program no longer needs `System.exit()` to end;
* a failed login fails at once with `IpmiAuthenticationException`, whose cause is the actual one
  (`AuthenticationException: Authentication check failed` or `IPMIException: Unauthorized name.`
  in the low-level API), where 1.2.02 sent the credentials four times and threw
  `ConnectionException: Illegal connection state: Rakp1Waiting`
  ([Troubleshooting](troubleshooting.html#ipmiauthenticationexception)); only a login whose step
  got no reply is started again (from Open Session);
* the [keep-alive](configuration.html#keep-alive) is actually sent: with the default `pingPeriod`
  (`-1`) 1.2.02 sent no keep-alive at all, so a session could expire during a long collection; the
  keep-alive is now a Get Device ID every 30 s by default, as `ipmitool` sends, whose reply is
  discarded, and a Get Channel Authentication Capabilities command sent by the application in a
  session gets its reply (1.2.02 dropped it, as it did the keep-alive replies);
* a one-way IPMI message (`IpmiConnector.sendOneWayMessage()`, `IpmiAsyncConnector.sendMessage()`
  with `isOneWay`) is queued like any request: its tag stays reserved, and it takes a slot of the
  8-message window, until its reply arrives or it times out, where 1.2.02 did not reserve the tag,
  so it could be handed out again while the reply was still on its way and a late reply taken for
  the reply of a later request; its reply and its timeout are still not reported (the Serial over
  LAN acknowledgements, which the BMC never answers, are not queued);
* a sensor whose Get Sensor Reading fails with an error completion code is returned without
  reading, with the code in its `problem()`, where 1.2.02 failed the whole call (it tolerated
  `DataNotPresent` only);
* `IpmiConnector.closeConnection()` releases the connection and forgets its session: its handle
  then throws `IllegalStateException` instead of addressing a disconnected connection, and
  `SessionManager.establishSession()` closes the connection of a session it fails to open instead
  of tearing down the whole connector; closing a connection or tearing down the connector fails the
  requests still waiting for a reply on it with `ConnectionException`, where they waited forever;
* `Constants.TIMEOUT`, which nothing reads, is deprecated;
* the sending, receiving and keep-alive threads of a connection no longer race: the state
  machine serializes transitions and received messages, the HMAC and AES objects of a cipher suite
  are used by one thread at a time, and the listener lists can be changed while they are being
  notified (a listener may unregister itself from its own callback).

The RMCP+ sessions follow the security rules of the IPMI 2.0 specification:

* `IpmiClient` chooses the cipher suite by its ID: the first of 17, 3, 8, 16, 2 and 7 that the BMC
  offers, followed by 12 and 11 since 2.1.00
  ([How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)),
  where 1.2.02 took the 4th suite of the BMC's list (or the 3rd, 2nd, 1st): suite 4 (xRC4) on a
  Lenovo XCC, which the client does not implement; a BMC that offers none
  of them, only suites without integrity, fails with
  `IpmiClientException: The BMC ... offers none of the cipher suites ...` instead of opening a
  session whose replies could be forged;
* `getAvailableCipherSuites()` gives the standard suites the algorithms of the specification,
  whatever the BMC's records say, leaves out the OEM suites (1.2.02 listed those of a Cisco IMC as
  suites -128 and -79) and skips malformed records instead of throwing; the new
  `CipherSuite.isSupported()` tells the suites the library implements;
* a reply that fails the integrity check of the session is discarded, where 1.2.02 logged
  `Integrity check failed` and accepted it, and so is a reply that is not signed in a session with
  integrity (1.2.02 accepted a forged reply in clear text); the integrity is checked before
  decrypting, the pad of AES-CBC-128 is checked, a reply received twice is discarded, and the
  sequence-number window no longer locks the session out after 16 lost replies;
* the handshake fails with `IllegalArgumentException: ... does not match the request` when a reply
  of the BMC is for another session or confirms other algorithms than the requested ones, and the
  RAKP Message 4 of RAKP-HMAC-MD5 (suites 6 to 8) is checked on its 16 bytes instead of 12;
* the console random number of the handshake comes from a `SecureRandom` (1.2.02 used a
  `java.util.Random` seeded with the time), and the console session IDs start at a random value
  instead of 100 (they still go up by one per session), and every new session numbers its messages
  from 1 again, where 1.2.02 dropped the replies of a session reopened on the same connection
  after more than 17 messages;
* a password or a BMC key longer than 20 bytes fails with `IllegalArgumentException` before
  anything is sent (1.2.02 failed the handshake as for a wrong password), an empty password is
  valid, a BMC key of zeros only is treated as no key, and the `char[]` password given to
  `credentials()` is never copied into a `String`;
* a truncated or malformed datagram is dropped instead of throwing an
  `ArrayIndexOutOfBoundsException` in the receiving thread;
* a request carries the LUN of the client (0) in its rqSeq/rqLUN byte, where 1.2.02 put the LUN of
  the target there;
* `GetChannelAuthenticationCapabilitiesResponseData.isIpmiv20Support()` reads the "IPMI v2.0
  connections" bit of the extended capabilities, where 1.2.02 returned the bit that says the
  extended capabilities are present.

The decoders follow the IPMI 2.0 and FRU specifications more closely; the visible changes are:

* a Full Sensor record reports `Double.NaN` for a threshold it does not define, where 1.2.02
  reported `0.0`;
* the thresholds are linearized like the reading, are read whenever byte 12 of the record says
  the sensor has thresholds (1.2.02 looked at the wrong byte), and `getAccuracy()` and
  `getTolerance()` are decoded as the specification says; `hasAnalogReading()` tells when the
  reading byte is not a reading; in 2.0.00 it is `false` for the non-linear sensors
  (linearization `70h`-`7Fh`), whose conversion needs the Get Sensor Reading Factors command,
  which 2.0.00 does not implement: their reading and thresholds are `NaN`, where 1.2.02 dropped
  the sensor (2.1.00 converts their reading:
  [Upgrading from 2.0.00 to 2.1.00](#upgrading-from-2-0-00-to-2-1-00));
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
* `frus().execute()` returns the FRUs it could read even when FRU 0 or Get FRU Inventory Area
  Info fails, returns FRU 0 once, and stops reading a FRU at its first unreadable chunk instead of
  shifting the following chunks into the gap;
* reserved values of the rate unit, modifier unit usage and power restore policy decode to
  `None` or `Unknown` instead of throwing;
* the sensors of the OEM event/reading types `70h` to `7Eh` (Cisco IMC, Dell iDRAC and Fujitsu iRMC
  use them for presence and module sensors) report the raw value of their state bytes
  (`Sensor.states()` gives `0xHHLL`) like those of `7Fh`, where 1.2.02 reported `Unknown`; an OEM
  sensor (`70h` to `7Fh`) with no state asserted reports no state, where 1.2.02 reported the
  state bytes of a `7Fh` one; `ReadingType.parseInt()`, and so `getStatesAsserted()` and the event
  of a SEL record, returns `UnknownOEMEvent` for the states of `70h` to `7Fh`, where 1.2.02
  returned `Unknown` unless the sensor type was OEM; the new `ReadingType.isOem()` tells these
  types apart.

[Serial over LAN](serial-over-lan.html) consoles follow the IPMI specification and release what
they open:

* `writeBytes()` sends all the data: from the third packet on, 1.2.02 lost the data or threw
  `IllegalArgumentException` (on a Supermicro BMC, a 600-character write failed after 20 s with only
  its first packet delivered); a packet carries at most 255 characters;
* the packets of the console are numbered 1, 2, 3..., from 1 again at each activation of the
  payload, as `ipmitool` does: its acknowledgements no longer take a number, which made 1.2.02
  reuse the number of the previous packet after 14 packets of the BMC (a BMC drops such a packet
  as a retry), and a packet whose acknowledgement is lost is sent again with its number, where
  1.2.02 sent it as a new packet, which the BMC passed to the server again;
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
* a new constructor takes the [BMC key](configuration.html#bmc-key), and the console no longer
  raises every session to Administrator: `openSession()` sets the session to the level it was
  opened for ([The low-level API](#the-low-level-api));
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
a word-addressed device, and moves on by the bytes each reply returns:

```java
GetFruInventoryAreaInfoResponseData info = connector
		.sendMessage(handle, new GetFruInventoryAreaInfo(fruId));
int size = info.getFruInventoryAreaSize(); // in bytes, whatever the access unit
int offset = 0;
while (offset < size) {
	int count = Math.min(16, size - offset);
	ReadFruDataResponseData chunk = connector
			.sendMessage(handle, new ReadFruData(fruId, info.getFruUnit(), offset, count));
	if (chunk.getFruData().length == 0) {
		break; // no data within the size the device declared
	}
	offset += chunk.getFruData().length; // a device may return fewer bytes than asked
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
| `MessageHandler` | `messageQueue` | `getMessageQueue()` |
| `MessageHandler` | `connection` | `getConnection()` |
| `MessageHandler` | `lastReceivedSequenceNumber` | None: the sequence-number window is private |
| `IpmiLanMessage` | `networkFunction` | `getNetworkFunctionCode()` (public), `setNetworkFunctionCode(byte)` |
| `ConfidentialityAlgorithm` | `sik` | `getSik()` |
| `IntegrityAlgorithm` | `sik` | `getSik()`, `setSik(byte[])` |

`AbstractIpmiRunner`, whose protected fields 1.2.02 exposed, is removed in 2.0.00 with the rest of
`org.metricshub.ipmi.client.runner`.

`IpmiClient`, `ReadingTypeDescription` and `MessageComposer` are now `final`: in 1.2.02 they only
had private constructors, so they could not be subclassed anyway. `IpmiResultConverter` and
`DeviceDescription` are removed ([The client](#the-client)), and so is `Utils`, the string helpers
and timeout runner of the 1.x client.

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

| Identifier | 1.2.00 and earlier | 1.2.01 and later |
| --- | --- | --- |
| Maven coordinates | `org.sentrysoftware:ipmi` | `${project.groupId}:${project.artifactId}` |
| Client packages | `org.sentrysoftware.ipmi.client` | `org.metricshub.ipmi.client` |
| Protocol packages | `org.sentrysoftware.ipmi.core` | `org.metricshub.ipmi.core` |

Replace the dependency, then the package prefix in the imports (`org.sentrysoftware.ipmi.` →
`org.metricshub.ipmi.`).

Version 1.2.00 had added the RAKP-HMAC-SHA256 and RAKP-HMAC-MD5 authentication algorithms and
their integrity algorithms (cipher suites 6 to 8 and 15 to 17); 1.1.00 fixed a busy loop of the
connection thread.
