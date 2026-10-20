keywords: events, alerts, sel entry, sel entry list, oem sel record, threshold event, utc offset, polling, since, clear sel, ipmitool sel elist
description: Read the System Event Log (SEL) of a server through its BMC — how the entries are read and when the read fails, the SelEntry object with its sensor, event, direction and timestamp, the SelEntryList result, OEM entries, and polling for new entries with since().

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
SEL entry 1 at 2024-05-15T08:15:25Z: EventLoggingDisabled sensor 254, Log area reset/cleared (assertion)
SEL entry 2 at 2024-05-15T08:16:11Z: OemTimestamped of manufacturer 19046, OEM data 02 01 05 00 00 00
SEL entry 5 at 2024-05-15T08:23:27Z: PowerUnit sensor 100, Power off/down (assertion)
SEL entry 6 at 2024-05-15T08:23:34Z: PowerUnit sensor 100, Power off/down (deassertion)
SEL entry 7 at 2024-05-15T08:24:15Z: VersionChange sensor 0, Firmware or software incompatibility detected (assertion)
SEL entry 640 315532807 s after initialization: PowerSupply sensor 219, Presence detected (assertion)
```

(An excerpt of the 643 entries of a Lenovo IMM.)

## How the log is read

[`sel().execute()`](apidocs/org/metricshub/ipmi/client/SelRequest.html):

1. sends **Get SEL Time UTC Offset**, for the offset of a BMC that keeps its SEL time as local
   time (IPMI 2.0 revision 1.1; the SEL time of a BMC without the command is taken as UTC), then
   **Get SEL Info**, for the number of entries and the times of the last addition and the last
   erase. An empty log is not walked, as Get SEL Entry fails on it;
2. sends **Get SEL Entry** for the first entry (record ID `0000h`), then for the *next record ID*
   each reply gives, until that ID is `FFFFh`. Each entry is read whole, so no reservation is
   needed (IPMI 2.0 section 31.5); when a BMC asks for one anyway, or cancels the one it gave
   (`C5h`), the client takes a reservation with **Reserve SEL** and reads the entry again;
3. reads the first entry of the walk again (with `since()`, the entry given to it, when the log
   still holds it), then sends Get SEL Info again, to check that the log did not change during
   the walk.

When the log changes during the read, or the BMC does not answer as expected:

* The log was **erased during the read**: the erase timestamp of Get SEL Info changed (after a
  Clear SEL, or a Delete SEL Entry, whose record IDs may be reused), the entry read again is gone
  or is another one (two erases within the same second leave the erase timestamp unchanged), or
  an entry named as the next one is not there any more (`CBh`). The read **starts over** once,
  whole, from the first entry of the log as it is now, even with `since()`, as the entry given
  to it may be another one by then.
* The log changes again during that second read: the read fails rather than return a list that
  mixes two logs ([below](#when-the-read-fails)).
* A Clear SEL is in progress (`81h`): the client waits for its end, up to 10 s per command.
* The BMC does not implement Get SEL Info (`C1h`, `D5h`): the log is read all the same, without
  the counts and timestamps. Any other refusal of Get SEL Info fails the read, as a clear could
  then go unnoticed.

Every record type is decoded: the system events, the OEM records with and without timestamp,
and the reserved types, of which only the record ID is defined
([Supported Commands](supported-commands.html#sel-records)). A log of 643 entries takes about
20 s on a Lenovo IMM (one round trip per entry); a Supermicro BMC answers its 18 entries in
half a second.

The read must end within the [deadline](timeouts-and-errors.html#deadline) of the client, 2
minutes by default, session setup included: when it expires, the read fails with
`IpmiTimeoutException` and returns none of the entries read. `timeout(Duration)` gives a large
log more time:

```java
SelEntryList entries = bmc.sel().timeout(Duration.ofMinutes(5)).execute();
```

The read needs the **User** privilege, like the other read operations, and is
[retried once](timeouts-and-errors.html#session-recovery) on a new session when the session is
lost while it runs.

### When the read fails

Like every operation, `execute()` fails with an
[`IpmiClientException`](timeouts-and-errors.html#exceptions) when the session cannot be opened.
The read itself fails with:

* `IpmiCompletionCodeException` when the BMC refuses a command: a refusal of Get SEL Entry other
  than `CBh`, `81h` and a first `C5h` (`C1h` from a BMC without a SEL, a second `C5h` for the
  same entry after a new reservation), `D4h` when a command needs more privilege than the session
  has (or, with `MaximumAvailable`, can be
  [raised](configuration.html#raising-the-session-on-demand) to), a refusal of Reserve SEL, or a
  refusal of Get SEL Time UTC Offset or Get SEL Info other than `C1h` and `D5h` (Get SEL Info
  waits out `81h`);
* `IpmiTimeoutException` when the [deadline](timeouts-and-errors.html#deadline) expires, or when
  a request gets no reply after all its tries;
* `IpmiClientException` itself when the log changed twice during the read, its next record IDs
  loop, the BMC answers another entry than the one asked for, a malformed entry or an entry whose
  record ID is `0000h` or `FFFFh`, or a Clear SEL is still in progress after 10 s.

## The `SelEntry` object

Each [`SelEntry`](apidocs/org/metricshub/ipmi/client/SelEntry.html) holds:

| Method | Content |
| --- | --- |
| `id()` | The record ID, 1 to 65534, unique in the log; after a Clear SEL or a Delete SEL Entry, the BMC may give it to another entry |
| `type()` | [`SelRecordType`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/SelRecordType.html): `System` for a system event, `OemTimestamped` (`C0h`–`DFh`), `OemNonTimestamped` (`E0h`–`FFh`) or `Reserved` (`00h`–`01h`, `03h`–`BFh`) |
| `timestamp()` | When the event was logged (`Optional<Instant>`), for a system event or a timestamped OEM entry whose timestamp is a date ([below](#timestamps)) |
| `preInitSeconds()` | For an event logged before the BMC had the time: the seconds since the initialization of the SEL device (`OptionalLong`) |
| `sensorType()` | The [`SensorType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorType.html) of the sensor that reported a system event (`Optional<SensorType>`): `Temperature`, `PowerSupply`, `Memory`, `PhysicalSecurity`, ... |
| `sensorNumber()` | Its sensor number (`OptionalInt`): the [`Sensor.number()`](sensors.html#the-sensor-object) of the sensor in the SDR repository, unique for a given `generatorId()` |
| `generatorId()` | What logged the event (`OptionalInt`). Low byte: the 7-bit slave address or system software ID in bits 7:1, bit 0 set for a software ID (`20h` for the BMC, `01h` for the BIOS). High byte: the channel in bits 7:4 and the LUN in bits 1:0 |
| `isFrom(sensor)` | Whether a [`Sensor`](sensors.html#the-sensor-object) of the SDR repository is the one that reported the event: same owner (address or software ID, LUN) and same number |
| `eventType()` | The event/reading type code (`OptionalInt`): `01h` for a threshold event, `02h`–`0Ch` generic discrete, `6Fh` sensor-specific, `70h`–`7Fh` OEM |
| `event()` | What happened, as the [`ReadingType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/ReadingType.html) of the event offset (`Optional<ReadingType>`): `UpperCriticalGoingHigh`, `PowerSupplyInputLost`, `LogAreaReset`, ... |
| `direction()` | [`EventDirection`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/EventDirection.html) (`Optional<EventDirection>`): `Assertion` (the threshold crossed, the failure detected) or `Deassertion` (back within the threshold, the failure gone) |
| `description()` | The entry in words. For a system event: the wording of `ipmiutil`, as in [`Sensor.states()`](sensors.html#states) (`Failure detected`, `Log area reset/cleared`, `Power Supply AC lost`); that of `ipmitool` for the few events `ipmiutil` does not name, such as the OS Boot installation events of IPMI 2.0 revision 1.1 and the Watchdog 1 events (`Installation started`, `BIOS Reset`); the name of the `ReadingType` spaced out when neither tool names it (`Upper Critical Going High`); the codes when the specification does not define the offset (`Unknown event type 08h offset 6h`) or the event is OEM (`OEM event type 71h offset 2h`). For an OEM entry, its OEM bytes (`OEM data 02 01 05 00 00 00`); for a reserved record type, `Reserved record type` |
| `eventData()` | The three event data bytes as the BMC sent them (`byte[]`, a copy): the offset in the low nibble of the first; for a threshold event, the raw reading that triggered it and the threshold in the next two ([below](#threshold-events)) |
| `manufacturerId()`, `oemData()` | The IANA enterprise number of a timestamped OEM entry (`OptionalInt`), and the OEM bytes of an OEM entry (`byte[]`, a copy): 6 with a timestamp, 13 without |
| `record()` | The decoded [`SelRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/SelRecord.html) of the protocol core, for everything else |

The accessors of a system event (`sensorType()`, `event()`, ...) are empty for the OEM and
reserved entries (an empty `Optional`, or a zero-length array for `eventData()`), and so are
`manufacturerId()` and `oemData()` for the system events: `type()` tells which. Two entries are
`equals()` when they are the same record (same ID, same 16 bytes). A `SelEntry` cannot be
changed, but its `record()` is the decoded object of the protocol core, not a copy, whose setters
change what the other methods return: read it, do not modify it.

### The `SelEntryList` result

[`SelEntryList`](apidocs/org/metricshub/ipmi/client/SelEntryList.html), the result of
`execute()`, is an unmodifiable `List<SelEntry>` in the order of the log. Its other methods give
what Get SEL Info, sent after the walk, says of the whole log, and the offset applied to the
timestamps:

| Method | Content |
| --- | --- |
| `totalEntries()` | The number of entries of the whole log (`OptionalInt`), more than `size()` after `since()`; empty when the BMC does not implement Get SEL Info (`C1h`, `D5h`) |
| `lastAddition()` | When an entry was last added (`Optional<Instant>`); empty when the BMC does not implement Get SEL Info, or reports no addition time (`FFFFFFFFh`) or a [pre-init](#timestamps) one |
| `lastErase()` | When entries were last deleted or the log cleared (`Optional<Instant>`); empty in the same cases (a BMC that never erased its log reports no erase time) |
| `utcOffsetMinutes()` | The offset from UTC subtracted from every timestamp ([below](#timestamps)): 0 when the BMC does not implement Get SEL Time UTC Offset or leaves the offset unspecified |
| `info()` | The Get SEL Info reply of the protocol core ([`GetSelInfoResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/GetSelInfoResponseData.html), in an `Optional`): the SEL version, the entry count, and the timestamps as the BMC sent them, before the UTC offset is applied; empty when the BMC does not implement Get SEL Info |

### Timestamps

A SEL timestamp is a number of seconds since 1970, but not always: the values up to `20000000h`
(January 1987) stamp the events logged **before the BMC had the time**, counted from the
initialization of the SEL device (IPMI 2.0 section 37.1), and `FFFFFFFFh` is an unspecified
timestamp. `timestamp()` is empty in both cases; `preInitSeconds()` gives the former, like the
`Pre-Init` of `ipmitool`. A Lenovo IMM logs the events of its first boot with its clock at 1980,
which falls in that range: they are reported as `preInitSeconds()` of about 315 million.

A BMC that keeps its SEL time as **local time** reports its offset from UTC with Get SEL Time UTC
Offset (IPMI 2.0 revision 1.1). The client subtracts that offset from every timestamp it
returns, so that `timestamp()`, `lastAddition()` and `lastErase()` are UTC instants, and
`utcOffsetMinutes()` gives the offset applied:

| Get SEL Time UTC Offset answers | `utcOffsetMinutes()` |
| --- | --- |
| An offset (-1440 to 1440 minutes) | That offset |
| `07FFh`, an unspecified offset (as after a Set SEL Time) | 0 |
| `C1h` or `D5h`: the BMC does not implement the command | 0 |
| Another refusal, such as `D4h` once the session cannot be [raised](configuration.html#raising-the-session-on-demand) any further | None: the read fails, as the timestamps would be off by an unknown offset |

The offset is the one in force when the log is read. The log records none per entry, so an
entry logged under another offset (before a daylight saving change, or a change of the time zone
of the BMC) comes out shifted by the difference, usually an hour.

The timestamps are only as good as the clock of the BMC. On the lab BMCs, a Supermicro BMC keeps
its SEL time in UTC and a Lenovo IMM reports an offset of 60 minutes (that of its time zone
setting), but a Dell iDRAC keeps its SEL clock on the local time of its time zone setting
*without* implementing the command: its timestamps come out ahead of UTC by the offset of that
time zone (two hours in summer, set to Central European time), and nothing in the protocol says
so. When the hour of an event matters, compare the SEL time of the BMC with the wall clock:
`ipmitool sel time get`, or Get SEL Time (Storage `48h`) sent with
[`raw()`](low-level-api.html#raw-requests), whose four data bytes are the SEL time in seconds
since 1970, LS byte first. Like the timestamps of the entries, it is local time on a BMC that
reports an offset: subtract the `utcOffsetMinutes()` of a `sel()` read (`entries` below) before
comparing it with UTC.

```java
RawResponse reply = bmc.raw(0x0A, 0x48).execute(); // Get SEL Time
if (reply.completionCode() == CompletionCode.Ok) {
	byte[] data = reply.data();
	long seconds = (data[0] & 0xFFL) | (data[1] & 0xFFL) << 8 | (data[2] & 0xFFL) << 16 | (data[3] & 0xFFL) << 24;
	Instant selTime = Instant.ofEpochSecond(seconds).minusSeconds(entries.utcOffsetMinutes() * 60L);
	System.out.println("SEL clock: " + selTime + ", this host: " + Instant.now());
}
```

### Threshold events

A threshold event (`eventType()` = `01h`) says which threshold was crossed in which direction
(`UpperCriticalGoingHigh`, `LowerNonCriticalGoingLow`, ...). Its `eventData()` usually carries
two raw bytes, which the high bits of the first byte announce:

* the reading that triggered the event, in the second byte, when bits 7:6 are `01b`;
* the threshold that was crossed, in the third byte, when bits 5:4 are `01b`.

Convert them with the `calcFormula()` of the
[`FullSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FullSensorRecord.html)
of the sensor. To find that sensor among `bmc.sensors().execute()`, use `isFrom(sensor)`: a
sensor number is unique only for its owner (the BMC, a satellite controller, the BIOS...), so
`isFrom()` compares the `generatorId()` of the event (the address or software ID and the LUN of
what logged it) with the owner of the SDR record, and the sensor number of the event with
`Sensor.number()`, as `ipmitool sel elist` does:

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

`calcFormula(int)` returns `NaN` for a non-linear sensor (linearization `70h`–`7Fh`,
`isNonLinear()`), whose factors vary with the reading. Ask the BMC for the factors of that
reading byte with Get Sensor Reading Factors and convert with them; the threshold in the third
byte converts the same way, with the factors of that byte:

```java
int raw = data[1] & 0xFF;
double value = record.calcFormula(raw);
if (record.isNonLinear()) {
	SensorReadingFactors factors = bmc
			.send(new GetSensorReadingFactors(sensor.number(), raw, record.getSensorOwnerLun()))
			.getFactors();
	value = record.calcFormula(raw, factors);
}
```

`bmc.send()` asks the BMC itself: for a sensor of another controller, wrap the command in
[`SendMessage`](low-level-api.html#commands-for-another-controller).

### OEM entries

Vendors log a lot in their own format: of the 643 entries of a Lenovo IMM, 358 are timestamped
OEM records, 342 of manufacturer 311 (Microsoft) and 16 of manufacturer 19046 (Lenovo). An OEM
entry comes with its OEM bytes, which only the vendor documents (`oemData()`, also in
`description()`: `OEM data 02 01 05 00 00 00`): six bytes, a timestamp and a `manufacturerId()`
for a timestamped record (`C0h`–`DFh`), 13 bytes and neither for a non-timestamped one
(`E0h`–`FFh`). `type()` gives only the range: the record type byte itself is
`record().getRawData()[2] & 0xFF`.

A system event of an OEM event/reading type (`70h`–`7Fh`), or of an OEM sensor type
(`C0h`–`FFh`) with a sensor-specific event/reading type (`6Fh`), is decoded like any other system
event, with `event()` = `UnknownOEMEvent` and a `description()` that gives the codes:
`OEM event type 71h offset 2h`.

## Polling for new entries

Reading the whole log at every poll costs one round trip per entry. `since(entry)` reads only the
entries added after the given one, the last entry of the previous read: that entry is read
again to find the next one and left out.

```java
SelEntry lastSeen = null;
while (true) {
	try {
		SelEntryList entries = lastSeen == null ? bmc.sel().execute() : bmc.sel().since(lastSeen).execute();
		for (SelEntry entry : entries) {
			alert(entry);
			lastSeen = entry;
		}
	} catch (IpmiClientException e) {
		// No reply, a refusal, a log that changed twice: report it; the next poll opens a new session if needed
		warn(e);
	}
	Thread.sleep(60_000);
}
```

When the log was cleared since, the record IDs started over: the BMC holds **another entry**
under the ID of the last one seen, or none, and the **whole log** is read (`lastErase()` is then
more recent than the previous read).

`since(int recordId)` does the same with the ID alone, for a cursor kept across restarts. It
takes a record ID of 1 to 65534 and throws `IllegalArgumentException` otherwise: for the first
read, call `sel().execute()`. Of `since(entry)` and `since(id)`, the last one called counts.
`since(int)` cannot tell a log cleared and filled up to that ID again from the one it read
before: keep the `lastErase()` of the previous read with the cursor, and when a read reports a
more recent one, read the whole log again with `sel().execute()`, as the entries up to that ID
are new too. When `lastErase()` is empty (a BMC without Get SEL Info, or one that reports no
erase time), only `since(entry)` notices a refilled log.

The log has a fixed capacity (hundreds to a few thousand entries); when it is full, most BMCs
stop logging (`SelFull`, `SelAlmostFull` events of the `EventLoggingDisabled` sensor type announce
it) and the log must be cleared with `ipmitool sel clear`, the web interface of the BMC, or the
Clear SEL command (Storage `47h`), for which the library has no coder.
[`raw()`](low-level-api.html#raw-requests) sends it with a reservation, from a client at the
Operator [privilege](configuration.html#privilege-level) or higher. The BMC may erase the log in the
background: a `sel()` read meanwhile waits for the end of the erase, up to 10 s.

```java
int reservation = bmc.send(new ReserveSel()).getReservationId();
RawResponse reply = bmc.raw(0x0A, 0x47).data(reservation & 0xFF, reservation >> 8, 'C', 'L', 'R', 0xAA).execute();
if (reply.completionCode() != CompletionCode.Ok) {
	// C5h: the reservation was canceled (an entry was added, or another client reserved the SEL):
	// read the new entries, then reserve and clear again
}
```

## With the command-line client

```shell-session
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

(The first entries of a Dell iDRAC.) See [`sel`](cli.html#sel) in the manual of the command-line
client: `--since <id>`, `--json`, and `-v` for a summary of the log (its entries, last addition
and erase, and the UTC offset of its SEL time).
