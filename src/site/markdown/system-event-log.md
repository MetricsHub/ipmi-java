keywords: sel, system event log, events, alerts, sel entry, oem sel record, threshold event, polling, since, ipmitool sel elist
description: Read the System Event Log (SEL) of a server through its BMC — how the entries are read, the SelEntry object with its sensor, event, direction and timestamp, OEM entries, and polling for new entries with since().

# System Event Log

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The BMC logs the hardware events it sees in its **System Event Log (SEL)**: a threshold crossed,
a power supply that lost its input, a memory error, a chassis intrusion, a power-off. It is the
main source of hardware alerts on IPMI: the sensors say what the state is now, the log says what
happened. The client reads it entry by entry.

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	for (SelEntry entry : bmc.sel().execute()) {
		System.out.println(entry);
	}
}
```

```text
SEL entry 1 at 2024-05-15T09:15:25Z: EventLoggingDisabled sensor 254, Log area reset/cleared (assertion)
SEL entry 2 at 2024-05-15T09:16:11Z: OemTimestamped of manufacturer 19046, OEM data 02 01 05 00 00 00
SEL entry 5 at 2024-05-15T09:23:27Z: PowerUnit sensor 100, Power off/down (assertion)
SEL entry 6 at 2024-05-15T09:23:34Z: PowerUnit sensor 100, Power off/down (deassertion)
SEL entry 7 at 2024-05-15T09:24:15Z: VersionChange sensor 0, Firmware or software incompatibility detected (assertion)
```

## How the log is read

[`sel().execute()`](apidocs/org/metricshub/ipmi/client/SelRequest.html):

1. sends **Get SEL Time UTC Offset**, for a BMC that keeps its SEL time as local time (IPMI 2.0
   revision 1.1; a BMC without the command keeps it in UTC), and **Get SEL Info**, for the number
   of entries and the time of the last addition and erase (an empty log is not read: Get SEL
   Entry fails on it);
2. sends **Get SEL Entry** for the first entry (record ID `0000h`), then for the *next record ID*
   each reply gives, until that ID is `FFFFh`. Each entry is read whole, so no reservation is
   needed (IPMI 2.0, section 31.5); when a BMC asks for one anyway, or cancels the one it gave
   (`C5h`), the client takes a reservation with **Reserve SEL** and reads the entry again.

The first entry read (or the entry given to `since()`, when nothing follows it) is read again
after the walk, and so is Get SEL Info: when the log was
**erased meanwhile** (its erase timestamp changed, after a Clear SEL or a Delete SEL Entry, whose
record IDs may be reused; or the first entry is another one, as two erases within a second
leave the timestamp unchanged), or when an entry named as the next one is not there any more
(`CBh`), the read **starts over**,
once, whole (the entry given to `since()` may be another one by then) and from what the log is
now; a log that changes twice during a read fails it with `IpmiClientException` rather than
return a list that mixes two logs, and so does a log whose next record IDs loop or that answers
another entry than the one asked for. While a Clear SEL is in progress (`81h`), the read waits
for its end, up to 10 s. A BMC without Get SEL Info (`C1h`, `D5h`) is read all the same, without
the counts and timestamps; any other refusal of it fails the read, as a clear could then go
unnoticed.

Every record type is decoded: the system events, the OEM records with and without timestamp,
and the reserved types, of which only the record ID is defined
([Supported Commands](supported-commands.html#sel-records)). A log of 643 entries takes about
20 s on a Lenovo IMM (one round trip per entry); a Supermicro BMC answers its 18 entries in
half a second.

The read needs the **User** privilege, like the other read operations, and is
[retried once](timeouts-and-errors.html#session-recovery) on a new session when the session is
lost while it runs.

## The `SelEntry` object

Each [`SelEntry`](apidocs/org/metricshub/ipmi/client/SelEntry.html) is immutable and holds:

| Method | Content |
| --- | --- |
| `id()` | The record ID, 1 to 65534, unique until the log is cleared |
| `type()` | [`SelRecordType`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/SelRecordType.html): `System` for a system event, `OemTimestamped` (`C0h`–`DFh`), `OemNonTimestamped` (`E0h`–`FFh`) or `Reserved` |
| `timestamp()` | When the event was logged (`Optional<Instant>`), for a system event or a timestamped OEM entry whose timestamp is a date ([below](#timestamps)) |
| `preInitSeconds()` | For an event logged before the BMC had the time: the seconds since the initialization of the SEL device (`OptionalLong`) |
| `sensorType()` | The [`SensorType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorType.html) of the sensor that reported a system event (`Temperature`, `PowerSupply`, `Memory`, `PhysicalSecurity`, ...) |
| `sensorNumber()` | Its sensor number: the [`Sensor.number()`](sensors.html#the-sensor-object) of the sensor in the SDR repository, unique for a given `generatorId()` |
| `generatorId()` | What logged the event: the slave address (`0x20` for the BMC) or system software ID in the low byte, its channel and LUN in the high byte |
| `isFrom(sensor)` | Whether a [`Sensor`](sensors.html#the-sensor-object) of the SDR repository is the one that reported the event: same owner (address or software ID, LUN) and same number |
| `eventType()` | The event/reading type code: `0x01` for a threshold event, `0x02`–`0x0C` generic discrete, `0x6F` sensor-specific, `0x70`–`0x7F` OEM |
| `event()` | What happened, as the [`ReadingType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/ReadingType.html) of the event offset: `UpperCriticalGoingHigh`, `PowerSupplyInputLost`, `LogAreaReset`, ... |
| `direction()` | [`EventDirection`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/EventDirection.html): `Assertion` (the threshold crossed, the failure detected) or `Deassertion` (back within the threshold, the failure gone) |
| `description()` | The event in the wording of `ipmiutil`, as [`Sensor.states()`](sensors.html#states) (`Failure detected`, `Log area reset/cleared`, `Power Supply AC lost`; that of `ipmitool` for the OS Boot installation events of IPMI 2.0 revision 1.1, which `ipmiutil` does not name: `Installation started`), the name of the `ReadingType` spaced out when neither names it (`Upper Critical Going High`), or the event/reading type and offset when the specification does not name it (`Unknown event type 08h offset 6h`) |
| `eventData()` | The three event data bytes as the BMC sent them: the offset in the low nibble of the first; for a threshold event, the raw reading that triggered it and the threshold in the next two ([below](#threshold-events)) |
| `manufacturerId()`, `oemData()` | The IANA enterprise number of a timestamped OEM entry, and the OEM bytes of an OEM entry (6 with a timestamp, 13 without) |
| `record()` | The decoded [`SelRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/SelRecord.html), for everything else |

The accessors of a system event (`sensorType()`, `event()`, ...) are empty for the OEM and reserved
entries, and `manufacturerId()` and `oemData()` for the system events: `type()` tells which.
Two entries are `equals()` when they are the same record (same ID, same 16 bytes).

[`SelEntryList`](apidocs/org/metricshub/ipmi/client/SelEntryList.html), the result of
`execute()`, is an unmodifiable `List<SelEntry>` that also gives what Get SEL Info says of the
whole log: `totalEntries()`, `lastAddition()` and `lastErase()` (`Optional`: a BMC that never
erased its log reports no erase time), and the `utcOffsetMinutes()` of the SEL time.

### Timestamps

A SEL timestamp is a number of seconds since 1970, but not always: the values up to `20000000h`
(January 1987) stamp the events logged **before the BMC had the time**, counted from the
initialization of the SEL device (IPMI 2.0, section 37.1), and `FFFFFFFFh` is an unspecified
timestamp. `timestamp()` is empty in both cases; `preInitSeconds()` gives the former, like the
`Pre-Init` of `ipmitool`. A Lenovo IMM logs the events of its first boot with its clock at 1980,
which falls in that range: they are reported as `preInitSeconds()` of about 315 million.

A BMC that keeps its SEL time as **local time** says so with the Get SEL Time UTC Offset command
of IPMI 2.0 revision 1.1, and the client subtracts that offset from every timestamp it returns,
so that `timestamp()`, `lastAddition()` and `lastErase()` are UTC instants; `utcOffsetMinutes()`
gives the offset applied: 0 when the BMC does not implement the command (`C1h`, `D5h`) or leaves
the offset unspecified (`07FFh`, as after a Set SEL Time), while any other refusal (`D4h`...)
fails the read, as the timestamps would be off by an unknown offset. The offset is the one in
force when the log is read: the log records none per entry, so an entry logged under another
offset (before a daylight saving change, or a change of the time zone of the BMC) comes out
shifted by the difference, an hour usually. The timestamps are
still only as good as the clock of the BMC: of the BMCs tested, a Supermicro keeps UTC, a Lenovo
IMM reports an offset of 60 minutes, and a Dell iDRAC runs its SEL clock on the local time of
its time zone setting *without* implementing the command, so its timestamps come out two hours
ahead of UTC in summer and nothing in the protocol says so. Compare the SEL time of the BMC
(`ipmitool sel time get`) with the wall clock when the hour of an event matters.

### Threshold events

A threshold event (`eventType()` = `0x01`) says which threshold was crossed in which direction
(`UpperCriticalGoingHigh`, `LowerNonCriticalGoingLow`, ...), and its `eventData()` usually carries
the raw reading that triggered it (second byte) and the threshold (third byte), as the high bits
of the first byte announce (`01` in bits 7:6 for the reading, `01` in bits 5:4 for the threshold).
They are raw bytes: convert them with the `calcFormula()` of the sensor's
[`FullSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FullSensorRecord.html),
found among `bmc.sensors().execute()` with `isFrom(sensor)`: a sensor number is unique for its
owner only (the BMC, a satellite controller, the BIOS...), so `isFrom()` matches the
`generatorId()` of the event (the address or software ID and the LUN of what logged it) with
the owner of the SDR record, and the sensor number, as `ipmitool sel elist` does:

```java
SensorList sensors = bmc.sensors().execute();
for (SelEntry entry : bmc.sel().execute()) {
	if (entry.eventType().orElse(0) == 0x01 && entry.direction().get() == EventDirection.Assertion) {
		byte[] data = entry.eventData();
		for (Sensor sensor : sensors) {
			if (entry.isFrom(sensor) && (data[0] & 0xC0) == 0x40 && sensor.record() instanceof FullSensorRecord) {
				FullSensorRecord record = (FullSensorRecord) sensor.record();
				System.out.println(entry.timestamp().orElse(null) + " " + sensor.name() + ": " + entry.description()
						+ " at " + record.calcFormula(data[1] & 0xFF) + " " + sensor.unit());
			}
		}
	}
}
```

### OEM entries

Vendors log a lot in their own format: of the 643 entries of a Lenovo IMM, 358 are timestamped
OEM records (manufacturer 19046, Lenovo). They are returned with their timestamp, manufacturer
ID and six bytes, which only the vendor documents; the system events among the OEM entries
are reported like the others. A system event of an OEM sensor type (`C0h`–`FFh`) or OEM
event/reading type (`70h`–`7Fh`) has `event()` = `UnknownOEMEvent` and a `description()` that
gives the codes.

## Polling for new entries

Reading the whole log at every poll costs one round trip per entry. `since(entry)` reads only the
entries added after the given one, the last entry of the previous read: that entry is read
again to find the next one and left out.

```java
SelEntry lastSeen = null;
while (true) {
	SelEntryList entries = lastSeen == null ? bmc.sel().execute() : bmc.sel().since(lastSeen).execute();
	for (SelEntry entry : entries) {
		alert(entry);
		lastSeen = entry;
	}
	Thread.sleep(60_000);
}
```

When the log was cleared since, the record IDs started over: the BMC holds **another entry**
under the ID of the last one seen, or none, and the **whole log** is read (`lastErase()` is then
more recent than the previous read). `since(int recordId)` does the same with the ID alone, for a
cursor kept across restarts, but cannot tell a log cleared and filled up to that ID again from
the one it read before: compare `lastErase()` with the previous read when using it.

The log has a fixed capacity (hundreds to a few thousand entries); when it is full, most BMCs
stop logging (`SelFull`, `SelAlmostFull` events of the `EventLoggingDisabled` sensor type announce
it) and the log must be cleared with `ipmitool sel clear`, the web interface of the BMC, or the
Clear SEL command, which the library does not implement.

## With the command-line client

```text
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sel
ID  Time                  Type                  Sensor  Event                      Direction
1   2023-03-15T17:06:39Z  EventLoggingDisabled  114     Log area reset/cleared     assertion
2   pre-init 39s          PowerSupply           101     Power Supply AC lost       assertion
3   pre-init 44s          PowerSupply           101     Power Supply AC lost       deassertion
4   2023-04-03T09:35:31Z  OsBoot                0       Installation started       assertion
5   2024-03-28T15:20:37Z  PowerSupply           100     Power Supply AC lost       assertion
6   2024-03-28T15:20:37Z  PowerSupply           116     Redundancy Lost            assertion
7   2024-03-28T15:20:42Z  PowerSupply           100     Power Supply AC lost       deassertion
```

(The first entries of a Dell iDRAC.) See [`sel`](cli.html#sel) in the manual of the command-line client:
`--since <id>` and `--json`.
