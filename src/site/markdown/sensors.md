keywords: sensors, sdr, sensor data record, sensor reading, thresholds, temperature, voltage, fan, power, states, non-linear, get sensor reading factors, sensor owner, lun, satellite controller, send message, bridging, get device sdr
description: Read the sensors of a server through its BMC — how the SDR repository is walked, selecting sensors, and the Sensor object with its reading, thresholds, status and states.

# Sensors

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The BMC describes its sensors in the **Sensor Data Record (SDR) repository**: one record per
sensor, with its name, what it measures (the *entity*: a processor, a power supply, the system
board, ...), its unit, the formula that converts its raw reading, and its thresholds. The client
walks this repository and reads the sensors that have a reading.

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	for (Sensor sensor : bmc.sensors().execute()) {
		System.out.println(sensor);
	}
}
```

## How the sensors are read

[`sensors().execute()`](apidocs/org/metricshub/ipmi/client/SensorsRequest.html):

1. walks the SDR repository from the first record to the last with **Get SDR**, reading large
   records in chunks when the BMC cannot return them at once, and reserving the repository
   (**Reserve SDR Repository**) when the BMC requires it or cancels the reservation; the walk is
   done once per client and shared with `frus()` ([SDR cache](configuration.html#sdr-cache));
2. sends **Get Sensor Reading** for every selected **Full Sensor** and **Compact Sensor** record,
   to the owner the record names ([Sensor owners](#sensor-owners)).

Event-Only records (sensors without a reading), the locator and association records, and the
OEM records are part of the walk but are not returned. A record the library cannot decode is
skipped and listed in `skippedRecords()`; the walk goes on
([Supported Commands](supported-commands.html#sdr-records)).

A BMC without SDR repository that holds the SDRs of its own sensors (Get Device ID: a Sensor
Device that provides Device SDRs, without SDR Repository Device) is walked with
**Get Device SDR** instead, as `ipmitool` does, when it refuses Get SDR Repository Info. These
SDRs are read again by each operation: they have no timestamp that tells whether a copy is
current.

### Sensor owners

Each sensor record names the owner of the sensor (IPMI 2.0, section 5.4): a controller, by its
slave address, channel and LUN, or system software, by its software ID. The client asks the
owner for the reading:

| Owner in the record | How the reading is requested |
| --- | --- |
| The BMC (`20h` on channel 0) | Get Sensor Reading to the BMC, at the LUN of the record |
| Another controller: a satellite controller of the power supplies and fans, of a RAID adapter, of a network adapter, the Intel Management Engine | Get Sensor Reading forwarded by the BMC with **Send Message** (IPMI 2.0, section 6.13.3) |
| System software, the BIOS for instance (bit 0 of the owner ID set: `B1h` on Dell iDRACs) | None: no controller holds the reading, and the BMC would answer for its own sensor of the same number. The sensor is returned without reading or states, `UNAVAILABLE`, with its `problem()` |

The BMC returns the response of the other controller as a message of its own, after its response
to Send Message, or within that response: the client accepts both. A controller that does not
answer costs its own sensors only, whose `problem()` says
`No reply from the controller at 44h on channel 0 through the BMC`: the session is kept, and the
other sensors of that controller are not requested again during the same operation.

Send Message to another controller needs the **Operator** privilege (IPMI 2.0, table G-1). Some
BMCs allow it at the User level (HPE iLO 5), others only at the Administrator level (an AMI-based
GIGABYTE BMC). A client below the level the BMC requires gets the sensors of the BMC as usual,
and those of the other controllers without reading, their `problem()` saying
`Get Sensor Reading through the BMC to the controller at 2Ch on channel 6 failed: ... (completion code D4h)`
([Privilege level](configuration.html#privilege-level)). A client built with
`privilege(PrivilegeLevel.MaximumAvailable)` raises the session to the level the BMC requires on
that first `D4h`, as far as the account allows, and reads these sensors with the same code on
every BMC ([Raising the session on demand](configuration.html#raising-the-session-on-demand)).

### Selecting sensors

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

## The `Sensor` object

Each [`Sensor`](apidocs/org/metricshub/ipmi/client/Sensor.html) is immutable and holds:

| Method | Content |
| --- | --- |
| `name()` | The sensor name of the SDR record (`CPU1 Temp`, `PSU2 Present`, ...) |
| `number()` | The sensor number |
| `type()` | What it measures or monitors: [`SensorType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorType.html) (`Temperature`, `Voltage`, `Fan`, `PowerSupply`, ...) |
| `entity()`, `entityInstance()` | The entity the sensor belongs to: its type (`EntityId.Processor`, `EntityId.PowerSupply`, ...) and instance number |
| `unit()` | The [`SensorUnit`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorUnit.html) of the reading and thresholds |
| `reading()` | The converted reading of a threshold sensor (`OptionalDouble`), empty for a discrete sensor or when the reading is unavailable |
| `rawReading()` | The reading byte as the BMC sent it, before conversion |
| `lowerNonCritical()` ... `upperNonRecoverable()` | The six thresholds, converted like the reading; empty when the record does not define them, so that a threshold of `0` is a threshold |
| `status()` | [`SensorStatus`](apidocs/org/metricshub/ipmi/client/SensorStatus.html): `OK`, `NON_CRITICAL`, `CRITICAL` or `NON_RECOVERABLE` for a threshold sensor, against its thresholds; `OK` for a discrete sensor that answers; `UNAVAILABLE` when the reading is unavailable |
| `states()` | The asserted states of a discrete sensor, in the wording of `ipmiutil` (of `ipmitool` for the few states `ipmiutil` does not name), each once |
| `rawStates()` | The same, as [`ReadingType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/ReadingType.html)s |
| `problem()` | Why the sensor has no reading and no states: the BMC refused the reading (with its completion code), the reading is not valid yet, scanning is disabled, the sensor belongs to system software, or the controller that owns it did not answer through the BMC ([Sensor owners](#sensor-owners)); why a non-linear sensor has no reading (but has its states and status) when the BMC refused its reading factors; empty otherwise |
| `record()`, `readingData()` | The decoded record ([`FullSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FullSensorRecord.html) or [`CompactSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/CompactSensorRecord.html)) and the [`GetSensorReadingResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/GetSensorReadingResponseData.html), for everything else |

[`SensorList`](apidocs/org/metricshub/ipmi/client/SensorList.html), the result of `execute()`,
is an unmodifiable `List<Sensor>` that also gives the SDR records the walk skipped
(`skippedRecords()`).

### Readings

For a Full Sensor record, the reading is converted to the sensor's unit with the record's
linear formula (`M`, `B` and the exponents of IPMI 2.0, section 36.3), linearization included:

```java
for (Sensor sensor : bmc.sensors().execute()) {
	sensor.reading().ifPresent(value -> System.out.println(sensor.name() + " = " + value + " " + sensor.unit()
			+ " (upper critical: " + sensor.upperCritical() + ", " + sensor.status() + ")"));
}
```

```text
Ambient Temp = 17.0 DegreesC (upper critical: OptionalDouble[39.0], OK)
System Power = 92.0 Watts (upper critical: OptionalDouble.empty, OK)
Fan 1 = 6600.0 Rpm (upper critical: OptionalDouble.empty, OK)
System 3.3V = 3.38 Volts (upper critical: OptionalDouble[3.56], OK)
```

A non-linear sensor (linearization `70h`-`7Fh`) has factors that vary with the reading: the
client asks the BMC for the factors of each reading with Get Sensor Reading Factors (IPMI 2.0,
section 35.5) and applies them without linearization function (section 36.2). Its thresholds
are empty, as each threshold byte would need factors of its own; `status()` still tells which
threshold the BMC finds the reading at or beyond.

`reading()` is empty when the BMC flags the reading it does not vouch for (unavailable, scanning
disabled), when the record has no analog reading, or when it refused the reading or the factors
of a non-linear sensor: `problem()` says which.

> [!NOTE]
> A Compact record that describes several shared sensors is reported as a single sensor
> ([#100](https://github.com/metricshub/ipmi-java/issues/100)).

### States

Discrete sensors (presence, redundancy, power supply status, processor status, ...) report a set
of asserted **states** instead of a value. `states()` returns the asserted states that have a
description in the wording of `ipmiutil`:

```text
PSU Redundancy: [Fully Redundant]
PSU1 Present: [Presence detected]
CPU0_Status: [Presence detected]
```

The states of a threshold sensor are the thresholds its reading is at or beyond. For OEM
sensors (event/reading types `0x70` to `0x7F`), whose states the specification does not define,
the single state is the raw value of the state bytes: `0xHHLL` (`0xLL` when the BMC returns a
single state byte). An OEM sensor with no state asserted reports no state, like any discrete
sensor.

