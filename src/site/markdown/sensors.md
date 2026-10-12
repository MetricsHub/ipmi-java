keywords: sensors, sdr, sensor data record, sensor reading, thresholds, temperature, voltage, fan, power, states, text output format, ipmiresultconverter
description: Read the sensors of a server through its BMC — how the SDR repository is walked, selecting sensors, the Sensor object with its reading, thresholds, status and states, and the text output format of IpmiResultConverter.

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
2. sends **Get Sensor Reading** for every selected **Full Sensor** and **Compact Sensor** record.

Event-Only records (sensors without a reading), the locator and association records, and the
OEM records are part of the walk but are not returned. A record the library cannot decode is
skipped and listed in `skippedRecords()`; the walk goes on
([Supported Commands](supported-commands.html#sdr-records)).

The walk reads only the sensors of the **BMC's** SDR repository, through the BMC itself: sensors
owned by satellite controllers that the BMC does not bridge, and the Device SDRs of other
controllers, are not read ([#84](https://github.com/metricshub/ipmi-java/issues/84),
[#105](https://github.com/metricshub/ipmi-java/issues/105)).

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
| `states()` | The asserted states of a discrete sensor, in the wording of `ipmiutil`, each once |
| `rawStates()` | The same, as [`ReadingType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/ReadingType.html)s |
| `problem()` | Why the sensor has no reading and no states: the BMC refused the reading (with its completion code), the reading is not valid yet, or scanning is disabled; empty otherwise |
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

`reading()` is empty when the BMC flags the reading it does not vouch for (unavailable, scanning
disabled), when the record has no analog reading, or when it refused the reading:
`problem()` says which.

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

## Text output format

[`IpmiResultConverter.convertResult(frus, sensors)`](apidocs/org/metricshub/ipmi/client/IpmiResultConverter.html)
converts the FRUs and the sensors of a client into semicolon-separated lines, the format that
MetricsHub's IPMI connectors parse:

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	String text = IpmiResultConverter.convertResult(bmc.frus().execute(), bmc.sensors().execute());
}
```

The lines come in three groups, in this order:

```text
FRU;LENOVO;RD350;S4M00000 - 00000000000001
FRU;LITEON;PS-2451-6L-LF;0000
Power Unit;3;Power Unit 3;;;;PSU Redundancy=Fully Redundant
Power Supply;1;Power Supply 1;;;;PSU1 Present=Presence detected
Power Supply;2;Power Supply 2;;;;PSU2 Present=Presence detected
Temperature;0008;Ambient Temp;Air Inlet 1;17.0;37;39
Temperature;0009;CPU1 DTS;Processor 1;-44.0;;
PowerConsumption;000d;System Power;Power Unit 2;92.0
Fan;0014;Fan 1;Fan Device 1;6600.0;1600;
Voltage;0022;System 3.3V;System Board 1;3380.0;3040;3560
```

### FRU lines

`FRU;$vendor;$model;$serialNumber` — one per FRU, see
[FRU Inventory](fru-inventory.html#fru-lines-of-the-text-output).

### Device state lines

```text
$deviceType;$deviceId;$deviceUniqueId;$vendor;$model;$serialNumber;$states
```

One line per **entity** (device) that has at least one sensor with an asserted state:

| Field | Content |
| --- | --- |
| `$deviceType` | The entity type, in the wording of `ipmiutil`: `System Board`, `Processor`, `Power Supply`, `Fan Device`, `Memory Device`, `Disk or Disk Bay`, ... |
| `$deviceId` | The entity instance number, as the signed byte of the record |
| `$deviceUniqueId` | `$deviceType $deviceId`, for example `Power Supply 1` |
| `$vendor`, `$model`, `$serialNumber` | From the FRU of the same entity type and instance, if any; empty otherwise |
| `$states` | The states of every sensor of the entity, `sensorName=state` separated with `|` |

Sensors that report `Device Absent` are left out.

### Reading lines

One line per Full Sensor record with a reading, for the units below; sensors in other units are
not reported, and neither are sensors with no reading (raw value `0xFF`).

```text
Temperature;$sensorId;$sensorName;$deviceUniqueId;$value;$threshold1;$threshold2
Voltage;$sensorId;$sensorName;$deviceUniqueId;$value;$threshold1;$threshold2
Fan;$sensorId;$sensorName;$deviceUniqueId;$value;$threshold1;$threshold2
Current;$sensorId;$sensorName;$deviceUniqueId;$value
PowerConsumption;$sensorId;$sensorName;$deviceUniqueId;$value
Energy;$sensorId;$sensorName;$deviceUniqueId;$value
```

| Line | Unit of `$value` | `$threshold1` | `$threshold2` |
| --- | --- | --- | --- |
| Temperature | °C (°F and K are converted) | Upper non-critical | Upper critical, else upper non-recoverable |
| Voltage | **mV** | Lower non-critical, else lower critical, else lower non-recoverable | Upper non-critical, else upper critical, else upper non-recoverable |
| Fan | RPM | Lower critical, else lower non-recoverable | Lower non-critical |
| Current | A | | |
| PowerConsumption | W | | |
| Energy | J | | |

* `$sensorId` is the SDR record ID, as 4 lowercase hexadecimal digits.
* `$deviceUniqueId` is the entity of the sensor, as in the device state lines.
* Thresholds are rounded to integers, in the same unit as `$value`, and empty when the BMC does
  not define them.

The chassis status has its own text form:
[`convertResult(ChassisStatus)`](chassis-status.html#as-text).
