keywords: sensors, sdr, sensor data record, sensor reading, thresholds, temperature, voltage, fan, power, states, text output format, ipmiresultconverter
description: Read the sensors of a server through its BMC — how the SDR repository is walked, the Sensor object, readings and thresholds, discrete states, and the text output format of getFrusAndSensorsAsStringResult.

# Sensors

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The BMC describes its sensors in the **Sensor Data Record (SDR) repository**: one record per
sensor, with its name, what it measures (the *entity*: a processor, a power supply, the system
board, ...), its unit, the formula that converts its raw reading, and its thresholds. The client
walks this repository and reads the sensors that have a reading.

```java
List<Sensor> sensors = IpmiClient.getSensors(config);
```

## How the sensors are read

[`IpmiClient.getSensors()`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) opens a session
and:

1. walks the SDR repository from the first record to the last with **Get SDR**, reading large
   records in chunks when the BMC cannot return them at once, and reserving the repository
   (**Reserve SDR Repository**) when the BMC requires it or cancels the reservation;
2. sends **Get Sensor Reading** for every **Full Sensor** and **Compact Sensor** record.

Event-Only records (sensors without a reading), the locator and association records, and the
OEM records are part of the walk but are not returned. A record the library cannot decode is
logged and skipped; the walk goes on
([Supported Commands](supported-commands.html#sdr-records)).

The walk reads only the sensors of the **BMC's** SDR repository, through the BMC itself: sensors
owned by satellite controllers that the BMC does not bridge, and the Device SDRs of other
controllers, are not read ([#84](https://github.com/metricshub/ipmi-java/issues/84),
[#105](https://github.com/metricshub/ipmi-java/issues/105)).

## The `Sensor` object

Each [`Sensor`](apidocs/org/metricshub/ipmi/client/model/Sensor.html) holds:

| Method | Content |
| --- | --- |
| `getName()` | The sensor name of the SDR record (`CPU1 Temp`, `PSU2 Present`, ...) |
| `getEntityId()`, `getDeviceId()` | The entity the sensor belongs to: its type (`EntityId.Processor`, `EntityId.PowerSupply`, ...) and instance number |
| `isFull()`, `isCompact()` | Whether the record is a Full Sensor record (an analog sensor with a conversion formula and thresholds) or a Compact one (usually a discrete sensor) |
| `getRecord()` | The decoded record: a [`FullSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FullSensorRecord.html) or a [`CompactSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/CompactSensorRecord.html), both [`AbstractSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/AbstractSensorRecord.html) |
| `getData()` | The [`GetSensorReadingResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/GetSensorReadingResponseData.html), or `null` when the BMC has no reading for the sensor (completion code `DataNotPresent`) |
| `getStates()` | The asserted states, as `sensorName=state|sensorName=state...`, or an empty string |

### Readings

For a Full Sensor record, the reading is converted to the sensor's unit with the record's
linear formula (`M`, `B` and the exponents of IPMI 2.0, section 36.3):

```java
for (Sensor sensor : IpmiClient.getSensors(config)) {
	if (sensor.isFull() && sensor.getData() != null) {
		FullSensorRecord record = (FullSensorRecord) sensor.getRecord();
		double value = sensor.getData().getSensorReading(record);
		System.out.println(sensor.getName() + " = " + value + " " + record.getSensorBaseUnit()
				+ " (upper critical: " + record.getUpperCriticalThreshold() + ")");
	}
}
```

```text
Ambient Temp = 17.0 DegreesC (upper critical: 39.0)
System Power = 92.0 Watts (upper critical: 0.0)
Fan 1 = 6600.0 Rpm (upper critical: 0.0)
System 3.3V = 3.38 Volts (upper critical: 3.56)
```

`getSensorBaseUnit()` returns a
[`SensorUnit`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorUnit.html);
the six thresholds (`getLowerNonCriticalThreshold()` to `getUpperNonRecoverableThreshold()`)
are converted with the same formula. A threshold the BMC does not define, or does not make
readable, is left at `0.0`, the same value as a threshold that really is 0; on BMCs that do not
set the *init sensor type* bit of the record, every threshold is left at `0.0`
([#83](https://github.com/metricshub/ipmi-java/issues/83)).

> [!WARNING]
> Known limitations of the decoding, by the IPMI 2.0 specification
> (not all of them reproduced on real hardware):
>
> * a sensor whose reading is flagged *unavailable* or whose scanning is disabled is reported
>   with a reading of `0.0` ([#110](https://github.com/metricshub/ipmi-java/issues/110));
> * the non-linear conversions and the readability of each threshold are not fully handled
>   ([#83](https://github.com/metricshub/ipmi-java/issues/83));
> * the threshold status bits of the reading are mis-mapped
>   ([#82](https://github.com/metricshub/ipmi-java/issues/82));
> * a Compact record that describes several shared sensors is reported as a single sensor
>   ([#100](https://github.com/metricshub/ipmi-java/issues/100)).

### States

Discrete sensors (presence, redundancy, power supply status, processor status, ...) report a set
of asserted **states** instead of a value. `getStates()` returns each asserted state with a
description, in the wording of `ipmiutil`, as `sensorName=state`, separated with `|`:

```text
PSU Redundancy=Fully Redundant
PSU1 Present=Presence detected
CPU0_Status=Presence detected
```

The raw states are available as
`getData().getStatesAsserted(record.getSensorType(), record.getEventReadingType())`, a list of
[`ReadingType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/ReadingType.html).
For OEM sensors (event/reading type `0x7F`), whose states the specification does not define,
the state is the raw reading: `sensorName=0xHHLL`.

## Text output format

[`IpmiClient.getFrusAndSensorsAsStringResult()`](apidocs/org/metricshub/ipmi/client/IpmiClient.html)
reads the FRUs, then the sensors (two sessions, see
[#102](https://github.com/metricshub/ipmi-java/issues/102)), and converts them with
[`IpmiResultConverter`](apidocs/org/metricshub/ipmi/client/IpmiResultConverter.html) into
semicolon-separated lines, the format that MetricsHub's IPMI connectors parse. The lines come in
three groups, in this order:

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
| `$deviceId` | The entity instance number |
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
* Thresholds are rounded to integers, in the same unit as `$value`. A threshold is empty when
  its decoded value is `0.0`: when the BMC does not define it or does not make it readable, but
  also when it really is 0 (a lower fan threshold of 0 RPM, for example), and on BMCs affected by
  [#83](https://github.com/metricshub/ipmi-java/issues/83).

The chassis status has its own text form:
[`getChassisStatusAsStringResult()`](chassis-status.html#as-text).
