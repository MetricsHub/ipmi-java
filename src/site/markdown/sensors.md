keywords: sensors, sdr, sensor data record, sensor reading, thresholds, temperature, voltage, fan, power, states, non-linear, get sensor reading factors, sensor owner, lun, satellite controller, send message, bridging, get device sdr, shared record, share count, instance modifier, problem, skipped record, ipmicli sensors
description: Read the sensors of a server through its BMC — how the SDR repository (or the Device SDRs) is walked, the sensors of other controllers (Send Message), shared records, selecting sensors, and the Sensor object with its reading, thresholds, status and states.

# Sensors

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The BMC describes its sensors in the **Sensor Data Record (SDR) repository**: one record per
sensor (a Compact record can describe several, see [Shared records](#shared-records)), with its
name, its type (temperature, voltage, fan, presence, ...), the *entity* it belongs to (a
processor, a power supply, the system board, ...), its unit, the formula that converts its raw
reading, and its thresholds. `sensors()` walks this repository and returns a `Sensor` for each
sensor of its Full and Compact records, read from the controller that owns it.

A sensor is a **threshold** or a **discrete** sensor, as the *event/reading type* of its record
says (IPMI 2.0 table 42-1). A threshold sensor (event/reading type `01h`) has a numeric reading, a
temperature, a voltage or a fan speed for instance, which the BMC compares with its thresholds
([Readings](#readings)). A discrete sensor (the other event/reading types: generic `02h` to
`0Ch`, sensor-specific `6Fh`, OEM `70h` to `7Fh`) reports a set of asserted **states** instead,
such as a presence, a redundancy or a power supply failure ([States](#states)). `status()` grades
the reading of a threshold sensor only: check the `states()` of the discrete sensors.

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	for (Sensor sensor : bmc.sensors().execute()) {
		System.out.println(sensor);
	}
}
```

Each line (`Sensor.toString()`) gives the name of the sensor, its type, its entity and entity
instance, then its reading with its unit and its status (the status alone without reading), and
its states when it has any; a sensor with a `problem()` gives the problem after its entity
instead. On a Lenovo RD350 (an excerpt: its BMC has 49 sensors):

```text
Ambient Temp (Temperature, AirInlet 1): 19.0 DegreesC, OK
Host Power (PowerUnit, PowerUnit 1): OK
Fan 1 (Fan, Fan 1): 6600.0 Rpm, OK
HDD (DriveBay, Group 2): The reading is not valid yet: the sensor is being re-armed or initialized
PSU Redundancy (PowerSupply, PowerUnit 3): OK, states [Fully Redundant]
System 3.3V (Voltage, SystemBoard 1): 3.38 Volts, OK
```

## How the sensors are read

[`sensors().execute()`](apidocs/org/metricshub/ipmi/client/SensorsRequest.html):

1. walks the SDR repository from the first record to the last with **Get SDR**, reading large
   records in chunks when the BMC cannot return them at once, and reserving the repository
   (**Reserve SDR Repository**) when the BMC requires it or cancels the reservation; unless the
   [SDR cache](configuration.html#sdr-cache) is disabled, the client caches the records and
   shares them with `frus()` for as long as the BMC reports the repository unchanged;
2. sends **Get Sensor Reading** for every selected **Full Sensor** and **Compact Sensor** record,
   to the owner the record names ([Sensor owners](#sensor-owners)); a Compact record shared by
   several sensors is read once per sensor ([Shared records](#shared-records)).

Event-Only records (sensors without a reading), the locator and association records, and the
OEM records are part of the walk but are not returned. A record the BMC returns truncated, or
that the library cannot decode, is skipped and listed in `skippedRecords()`; the walk goes on
([Supported Commands](supported-commands.html#sdr-records)). A next record ID that names a record
already read ends the walk there, with the loop listed in `skippedRecords()`, so that a firmware
whose next record IDs loop does not make the walk read the repository forever. A refusal of the walk itself fails
the operation ([Errors](#errors)).

Some BMCs have no SDR repository: they hold the records of their own sensors themselves, as
**Device SDRs**. When the BMC refuses Get SDR Repository Info, and its Get Device ID says it is a
Sensor Device that provides Device SDRs but not an SDR Repository Device, the client reads its
records with **Get Device SDR** instead, as `ipmitool` does. Device SDRs are not cached: they have
no timestamp that tells whether a copy is current, so each operation reads them again.

### Sensor owners

Each sensor record names the owner of the sensor (IPMI 2.0 section 5.4): a controller, by its
slave address on the management bus of the server (IPMB, Intelligent Platform Management Bus),
its channel and its LUN (logical unit number, 0 to 3), or system software, by its software ID.
The client asks the owner for the reading:

| Owner in the record | How the reading is requested |
| --- | --- |
| The BMC (`20h` on channel 0) | Get Sensor Reading to the BMC, at the LUN of the record |
| Another controller (any other address, or `20h` on a channel other than 0): a satellite controller of the power supplies and fans, of a RAID adapter, of a network adapter, the Intel Management Engine | Get Sensor Reading forwarded by the BMC with **Send Message** (IPMI 2.0 section 6.13.3) |
| System software, the BIOS for instance (bit 0 of the owner ID set: `B1h` on Dell iDRACs) | None: no controller holds the reading, and the BMC would answer for its own sensor of the same number. The sensor is returned without reading or states, `UNAVAILABLE`, with its `problem()` |

Depending on the BMC, the response of the other controller comes in a message of its own after
the response to Send Message, or within that response: the client accepts both. A controller
that does not answer costs its own sensors only, whose `problem()` says:

```text
No reply from the controller at 44h on channel 0 through the BMC
```

The message names the LUN when it is not 0 (`..., LUN 1 through the BMC`). The session is kept,
and the client does not request the other sensors of that controller and LUN during the same
operation: they get the same `problem()`. A BMC that does not even acknowledge the Send Message
request is treated as for any other command: the session is lost, the read is replayed once on a
new session, and the operation fails when the replay fails too, with `IpmiTimeoutException` when
a request of the replay gets no reply and with `IpmiConnectionException` when the BMC does not
answer the setup of the new session
([Session recovery](timeouts-and-errors.html#session-recovery)).

Send Message to another controller needs the **Operator** privilege (IPMI 2.0 table G-1), but
BMCs differ: an HPE iLO 5 allows it at the User level, an AMI-based GIGABYTE BMC only at the
Administrator level. When the session is below the level the BMC requires, the sensors of the
BMC are read as usual, and those of the other controllers are returned without reading, with
this `problem()`:

```text
Get Sensor Reading through the BMC to the controller at 2Ch on channel 6 failed: ... (completion code D4h)
```

To read these sensors whatever level the BMC requires, build the client with
`privilege(PrivilegeLevel.MaximumAvailable)`: the client then raises the session one level at a
time (Operator, then Administrator) while the BMC answers `D4h`, as far as the account allows
([Privilege level](configuration.html#privilege-level),
[Raising the session on demand](configuration.html#raising-the-session-on-demand)).

## Selecting sensors

The selection is applied **before** the readings are requested, so that an unselected sensor
costs no round trip:

```java
// Only the temperature and fan sensors: one Get Sensor Reading per selected sensor
SensorList sensors = bmc.sensors().types(SensorType.Temperature, SensorType.Fan).execute();

// By name, case-insensitively: * matches any characters, ? one character
SensorList cpuTemperatures = bmc.sensors().names("CPU? Temp", "*DTS*").execute();

// With a deadline of its own
SensorList all = bmc.sensors().timeout(Duration.ofSeconds(30)).execute();
```

`types()` and `names()` combine: a sensor is read when its type is one of the types **and** its
name matches one of the patterns. A pattern matches the whole name: `CPU` does not select
`CPU1 Temp`, `CPU*` does. Calling `types()` or `names()` again replaces its previous selection,
and calling it without arguments selects nothing. The SDR repository is walked (or taken from
the [SDR cache](configuration.html#sdr-cache)) whatever the selection; `timeout()` overrides the
[deadline](timeouts-and-errors.html#deadline) of the client for this operation.

## The `Sensor` object

Each [`Sensor`](apidocs/org/metricshub/ipmi/client/Sensor.html) holds:

| Method | Content |
| --- | --- |
| `name()` | The sensor name of the SDR record (`CPU1 Temp`, `PSU2 Present`, ...), with its instance modifier for a sensor that shares its record (`Drive 3`, `Temp B`); never `null`: empty for a record without ID string |
| `number()` | The sensor number |
| `type()` | What it measures or monitors: [`SensorType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorType.html) (`Temperature`, `Voltage`, `Fan`, `PowerSupply`, ...); `Oem` for an OEM sensor type (`C0h`-`FFh`) and for a reserved code the library does not know, so `types(SensorType.Oem)` selects both |
| `entity()`, `entityInstance()` | The entity the sensor belongs to: its type (`EntityId.Processor`, `EntityId.PowerSupply`, ...) and instance number |
| `unit()` | The [`SensorUnit`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorUnit.html) of the reading and thresholds: the base unit of the record. A record with a rate unit (per second, per hour, ...) or a modifier unit (the base unit divided or multiplied by another one) gives them through `((AbstractSensorRecord) sensor.record())`: `getRateUnit()`, `getModifierUnitUsage()` and `getSensorModifierUnit()` |
| `reading()` | The converted reading of a threshold sensor (`OptionalDouble`); empty when the record gives no conversion (a discrete sensor, a Compact record, a Full record without analog reading) or when the reading, or the factors of a non-linear sensor, are unavailable ([Readings](#readings)) |
| `rawReading()` | The reading byte as the BMC sent it, before conversion (`OptionalInt`, 0 to 255), also given for a discrete sensor, whose reading byte the specification says to ignore (IPMI 2.0 table 35-15); empty when the BMC gave no valid reading (`problem()` says why), present when only the factors of a non-linear sensor are missing |
| `lowerNonCritical()` ... `upperNonRecoverable()` | The six thresholds of the SDR record, converted like the reading; empty when the record does not define them (so that a threshold of `0` is a threshold), for a Compact record, which has none, for a Full record without analog reading, and for a non-linear sensor. They are the values of the record: the client does not send Get Sensor Thresholds, so a threshold changed on the BMC at run time does not show here, while `status()` follows it |
| `status()` | [`SensorStatus`](apidocs/org/metricshub/ipmi/client/SensorStatus.html): for a threshold sensor, the most severe threshold the BMC reports the reading at or beyond in its Get Sensor Reading reply (`OK`, `NON_CRITICAL`, `CRITICAL` or `NON_RECOVERABLE`), even when the record does not give that threshold; `OK` for a discrete sensor that answers, whatever its states; `UNAVAILABLE` when the reading is unavailable (`problem()` says why) |
| `states()` | The asserted states of a discrete sensor, in the wording of `ipmiutil` (of `ipmitool` for the few states `ipmiutil` does not name), each once ([States](#states)) |
| `rawStates()` | The asserted states as a list of [`ReadingType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/ReadingType.html) values, one per asserted bit, including those `states()` has no wording for |
| `problem()` | Why the sensor has no reading or, for a non-linear sensor, no converted reading ([Problems](#problems)); empty otherwise |
| `record()`, `readingData()` | The decoded record, typed `SensorRecord`: cast it to [`FullSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FullSensorRecord.html) or [`CompactSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/CompactSensorRecord.html) (both extend [`AbstractSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/AbstractSensorRecord.html)) to read its fields; and the [`GetSensorReadingResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/GetSensorReadingResponseData.html) reply, an `Optional` that is empty when the reading was refused, unanswered or malformed, or the sensor belongs to system software (a reply the BMC flags as not valid or not scanned is there). Both give access to what `Sensor` does not expose |

A `Sensor` cannot be changed, but its `record()` and `readingData()` are the decoded objects of
the protocol core, which have setters. The record is also shared with the other sensors of a
shared record and, through the [SDR cache](configuration.html#sdr-cache), with the next
operations of the client: read these objects, do not modify them.

> [!IMPORTANT]
> `status()` does not interpret the states of a discrete sensor: a power supply whose `states()`
> is `[Failure detected]`, or a redundancy sensor at `[Redundancy Lost]`, is `OK`. Check the
> `states()` (or `rawStates()`) of the discrete sensors to monitor.

[`SensorList`](apidocs/org/metricshub/ipmi/client/SensorList.html), the result of `execute()`,
is an unmodifiable `List<Sensor>` in the order of the SDR repository. Its `skippedRecords()`
lists the SDR records the walk could not use (returned truncated by the BMC, or that the library
cannot decode), each a [`SkippedRecord`](apidocs/org/metricshub/ipmi/client/SkippedRecord.html)
with its `recordId()`, its `recordType()` (`-1` when its header could not be read) and the
`reason()`; its `toString()` reads `SDR record 0006h (type 0Bh): <reason>`.

### Readings

For a Full Sensor record, the reading is converted to the sensor's unit with the record's
linear formula (`M`, `B` and the exponents of IPMI 2.0 section 36.3), linearization included:

```java
for (Sensor sensor : bmc.sensors().execute()) {
	OptionalDouble upperCritical = sensor.upperCritical();
	String threshold = upperCritical.isPresent() ? String.valueOf(upperCritical.getAsDouble()) : "none";
	sensor.reading().ifPresent(value -> System.out.println(sensor.name() + " = " + value + " " + sensor.unit()
			+ " (upper critical: " + threshold + ", " + sensor.status() + ")"));
}
```

```text
Ambient Temp = 17.0 DegreesC (upper critical: 39.0, OK)
System Power = 92.0 Watts (upper critical: none, OK)
Fan 1 = 6600.0 Rpm (upper critical: none, OK)
System 3.3V = 3.38 Volts (upper critical: 3.56, OK)
```

A non-linear sensor (linearization `70h`-`7Fh`) has factors that vary with the reading: the
client asks the BMC for the factors of each reading with Get Sensor Reading Factors (IPMI 2.0
section 35.5) and applies them without linearization function (section 36.2). Its thresholds
are empty, as each threshold byte would need factors of its own; `status()` still tells which
threshold the BMC finds the reading at or beyond.

`reading()` is empty when the sensor has no reading at all (`status()` is `UNAVAILABLE` and
`problem()` says why), and when the factors of a non-linear sensor are missing (`problem()` says
why; `rawReading()`, `states()` and `status()` are still there). It is also empty, with an empty
`problem()`, for a discrete sensor, for a Compact record, which has no conversion formula, and
for a Full record without analog reading (data format `11b` or a reserved linearization).

### Problems

A sensor whose reading the client could not get is returned all the same, without states, with
the `status()` `UNAVAILABLE` and the reason in its `problem()`:

- the BMC refused Get Sensor Reading (`Get Sensor Reading failed: ...`, with the completion code:
  `CBh` for an empty drive slot on some BMCs);
- the BMC flags the reading as unavailable, for instance while the sensor is re-armed or
  initialized (`The reading is not valid yet: ...`), or the sensor as not scanned
  (`Sensor scanning is disabled`);
- the sensor belongs to system software (`The sensor belongs to system software ...`);
- the BMC refused or failed to forward the request to the controller that owns the sensor, or
  that controller refused it (`... through the BMC to the controller at ... failed: ...`, with the
  completion code: `D4h` when the session is below the level the BMC requires for Send Message,
  `83h` when no controller acknowledged the address), or the controller did not answer
  (`No reply from the controller at ...`) ([Sensor owners](#sensor-owners));
- the reply cannot be decoded (`GetSensorReading failed on <host>: ...`, or
  `SendMessage failed on <host>: ...` for a sensor of another controller).

A non-linear sensor whose reading factors are missing keeps its `rawReading()`, `states()` and
`status()`, without `reading()`: its `problem()` says that the BMC refused the factors
(`Get Sensor Reading Factors ... failed: ...`), that the controller did not answer
(`No reply from ... to Get Sensor Reading Factors`), or that the reply cannot be decoded.
[Sensors or FRUs are missing](troubleshooting.html#sensors-or-frus-are-missing) gives the usual
causes.

### Shared records

A Compact record with a **share count** of N describes N discrete sensors with consecutive
sensor numbers (IPMI 2.0 section 43.2): a Dell iDRAC 8 describes its 24 drive slots with a
`Drive 0` record shared by 15 sensors and a `Drive 15` record shared by 9, and its DIMM slots
with `A` and `B` records shared by 12 each. `sensors()` returns one `Sensor` per shared sensor,
each read with its own Get Sensor Reading, with the same `record()`:

- `number()` is the sensor number of the record plus the index of the sensor; a share count that
  would run past sensor number 255 is cut there, and the sensors beyond are not returned;
- `name()` is the ID string followed by the **instance modifier** of the sensor: the modifier
  offset plus the index, in digits for a numeric modifier, in letters counted like spreadsheet
  columns for an alpha one (`A` for 0, `Z` for 25, `AA` for 26). When the ID string of a numeric
  modifier already ends with a number, as on Dell iDRACs, that number is the one of the first
  sensor and is incremented instead, and the modifier offset is not used;
- `entityInstance()` increments with the index when the record says so, and is the instance of
  the record otherwise.

| Record: ID string, share count, modifier | Sensors |
| --- | --- |
| `Drive`, 2, numeric from 0 (Dell iDRAC 6 drive slots) | `Drive 0`, `Drive 1` |
| `Drive 0`, 15, numeric (Dell iDRAC 8 drive slots) | `Drive 0` to `Drive 14` |
| `Drive 15`, 9, numeric (Dell iDRAC 8 drive slots) | `Drive 15` to `Drive 23` |
| `A`, 12, numeric from 1 (Dell iDRAC 8 DIMM slots) | `A 1` to `A 12` |
| `Temp`, 2, alpha from 25 | `Temp Z`, `Temp AA` |

`names()` globs match the expanded names (`names("Drive 1?")` selects `Drive 10` to `Drive 19`),
and a sensor the BMC refuses (an empty drive slot answers `CBh` on some BMCs) costs that sensor
only: its `problem()` says so and the others are read.

### States

Discrete sensors (presence, redundancy, power supply status, processor status, ...) report a set
of asserted **states** instead of a value. `states()` returns the asserted states that have a
description, in the wording of `ipmiutil` (of `ipmitool` for the few states `ipmiutil` does not
name):

```java
for (Sensor sensor : bmc.sensors().execute()) {
	if (!sensor.states().isEmpty()) {
		System.out.println(sensor.name() + ": " + sensor.states());
	}
}
```

```text
PSU Redundancy: [Fully Redundant]
PSU1 Present: [Presence detected]
CPU0_Status: [Presence detected]
```

The states of a threshold sensor are the thresholds its reading is at or beyond. For a sensor of
an OEM event/reading type (`70h` to `7Fh`), whose states the specification does not define, the
single state is the state bytes in lowercase hexadecimal, the second byte first: `0x01c3`
(`0xc3` when the BMC returns a single state byte), while `rawStates()` holds an `UnknownOEMEvent`
for each asserted state bit. Such a sensor with no state asserted reports no state, like any
discrete sensor.

A state that its event/reading type does not define (an HPE iLO sets bits 6 and 7 of some
generic types) is reported as `Unknown` (`ReadingType.Unknown` in `rawStates()`).
The static `getReadingType(state)` of
[`ReadingTypeDescription`](apidocs/org/metricshub/ipmi/client/ReadingTypeDescription.html) gives
the wording that `states()` uses for a `ReadingType` of `rawStates()` or of `SelEntry.event()`,
and `null` for one the library does not describe.

## Errors

`execute()` throws only when the operation cannot go on: a problem that concerns one sensor is
reported in that sensor ([Problems](#problems)), and the other sensors are returned. It throws:

- the exceptions of any operation: the session cannot be set up, the connection is closed while
  the operation runs (the read is then replayed once on a new session), the deadline expires, or a
  reply of the walk cannot be decoded ([Exceptions](timeouts-and-errors.html#exceptions));
- `IpmiCompletionCodeException` when the BMC refuses Get SDR, Get Device SDR or the reservation
  of the repository with a code the walk does not work around: the walk takes a new reservation
  when the BMC cancels its reservation, reads in chunks a record too large for one reply, and
  ends when the BMC says the first record of a repository that may be empty is not present; a
  busy BMC is asked again first
  ([Message timeout and retries](timeouts-and-errors.html#message-timeout-and-retries));
- `IpmiTimeoutException` when the BMC does not answer a command of the walk or a Get Sensor
  Reading (or Get Sensor Reading Factors) of one of its own sensors, or does not acknowledge a
  Send Message: the session is then lost, the read is replayed once on a new session, and the
  operation fails when the replay fails too (with `IpmiConnectionException` when the BMC does
  not answer the setup of the new session)
  ([Session recovery](timeouts-and-errors.html#session-recovery)).

No sensor is returned when `execute()` throws. A failure of the walk fails `frus()` the same way,
as it walks the same repository.

## What is not read

- **Event-Only, locator, association and OEM records**: part of the walk, but no `Sensor` is
  returned for them ([How the sensors are read](#how-the-sensors-are-read)).
- **The current thresholds**: the threshold methods return the values of the Full Sensor record,
  and are empty for a Compact record, which has none. Get Sensor Thresholds is not sent, so a
  threshold changed on the BMC at run time does not show in `upperCritical()` and the other
  threshold methods; `status()` does show it, as the BMC compares the reading with its current
  thresholds.
- **The hysteresis**: Get Sensor Hysteresis is not sent, and the hysteresis bytes of the Full
  and Compact Sensor records are not decoded.
- **The event enables and the event status** (Get Sensor Event Enable, Get Sensor Event Status):
  not sent.
- **The nominal reading, the normal and sensor ranges, the tolerance and the accuracy**: only on
  the `FullSensorRecord` of `record()` (`getNominalReading()`, `getNormalMinimum()`,
  `getNormalMaximum()`, `getSensorMinmumReading()`, `getSensorMaximumReading()`,
  `getTolerance()`, `getAccuracy()`), not on `Sensor`.

## With the command-line client

`ipmicli sensors` prints the same sensors, one line each. `--type` and `--name` select them like
`types()` and `names()`, `--long` adds the number, the entity, the thresholds and the problem,
and `--json` gives the fields of `Sensor`: see [`sensors`](cli.html#sensors) in the manual of the
command-line client.
