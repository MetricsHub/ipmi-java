keywords: supported commands, ipmi commands, cipher suites, sdr record types, oem records, sel records, fru records, multirecord, completion codes
description: Reference of the IPMI commands, cipher suites, SDR, SEL and FRU records the library implements, and how it handles OEM and unknown records.

# Supported Commands

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The library implements **IPMI 2.0 over LAN** (RMCP+ sessions, IPMI 2.0 section 13) with the
commands below. Each command is a class of
[`org.metricshub.ipmi.core.coding.commands`](apidocs/org/metricshub/ipmi/core/coding/commands/package-summary.html),
sent with [`IpmiClient.send()`](low-level-api.html#sending-a-command-through-the-client) or the
[low-level API](low-level-api.html#sending-commands); the last column shows which operations of
`IpmiClient` use them. A command not listed here can be added by
[extending `IpmiCommandCoder`](low-level-api.html#writing-your-own-command) (any network
function, the [extension and OEM ones](low-level-api.html#extension-and-oem-network-functions)
included), or sent as bytes with [`raw()`](low-level-api.html#raw-requests).

## Commands

| Command | Class | NetFn / Cmd | Used by `IpmiClient` |
| --- | --- | --- | --- |
| **Session** | | | |
| Get Channel Authentication Capabilities | `GetChannelAuthenticationCapabilities` | App / `38h` | every session, `capabilities()` |
| Get Channel Cipher Suites | `GetChannelCipherSuites` | App / `54h` | every session (unless `cipherSuite(id)`), `capabilities()` |
| RMCP+ Open Session | `OpenSession` | (payload) | every session |
| RAKP Message 1 / 3 | `Rakp1`, `Rakp3` | (payload) | every session |
| Set Session Privilege Level | `SetSessionPrivilegeLevel` | App / `3Bh` | every session opened at `privilege(Operator)` or `privilege(Administrator)`, right after RAKP 4 (sent by `IpmiConnector.openSession()`) |
| Get Session Info | `GetSessionInfo` | App / `3Dh` | `sol()`: is the session that holds a SOL payload still there? |
| Close Session | `CloseSession` | App / `3Ch` | `close()` |
| **BMC** | | | |
| Get Device ID | `GetDeviceId` | App / `01h` | `deviceId()`, and the [keep-alive](configuration.html#keep-alive) of every session |
| **Chassis** | | | |
| Get Chassis Status | `GetChassisStatus` | Chassis / `01h` | `chassisStatus()` |
| Chassis Control | `ChassisControl` | Chassis / `02h` | `chassisControl()` |
| Chassis Identify | `ChassisIdentify` | Chassis / `04h` | `chassisIdentify()`, `chassisIdentifyForever()` |
| **SDR repository and sensors** | | | |
| Get SDR Repository Info | `GetSdrRepositoryInfo` | Storage / `20h` | `sensors()`, `frus()` ([SDR cache](configuration.html#sdr-cache)) |
| Reserve SDR Repository | `ReserveSdrRepository` | Storage / `22h` | `sensors()`, `frus()` |
| Get SDR | `GetSdr` | Storage / `23h` | `sensors()`, `frus()` |
| Get Sensor Reading | `GetSensorReading` | Sensor/Event / `2Dh` | `sensors()` |
| **FRU** | | | |
| Get FRU Inventory Area Info | `GetFruInventoryAreaInfo` | Storage / `10h` | `frus()` |
| Read FRU Data | `ReadFruData` | Storage / `11h` | `frus()` |
| **System Event Log** | | | |
| Get SEL Info | `GetSelInfo` | Storage / `40h` | `sel()` |
| Reserve SEL | `ReserveSel` | Storage / `42h` | `sel()`, when the BMC asks for a reservation |
| Get SEL Entry | `GetSelEntry` | Storage / `43h` | `sel()` |
| Get SEL Time UTC Offset | `GetSelTimeUtcOffset` | Storage / `5Ch` | `sel()`: the SEL time of a BMC that keeps it as local time (IPMI 2.0 revision 1.1; `C1h` from the others, `07FFh` when unspecified) |
| **Payloads (Serial over LAN)** | | | |
| Activate Payload (SOL) | `ActivateSolPayload` | App / `48h` | `sol()` |
| Deactivate Payload | `DeactivatePayload` | App / `49h` | `sol()` |
| Get Payload Activation Status | `GetPayloadActivationStatus` | App / `4Ah` | `sol()` |
| Get Payload Instance Info | `GetPayloadInstanceInfo` | App / `4Bh` | `sol()` |
| Get Channel Payload Support | `GetChannelPayloadSupport` | App / `4Eh` | |

The read operations of `IpmiClient` need nothing above the **User** privilege; Chassis Control
and Chassis Identify need **Operator**, the payload commands and Serial over LAN **Administrator**
([Privilege level](configuration.html#privilege-level)). The level is requested at login and set
right after it with Set Session Privilege Level, by `IpmiClient` and `IpmiConnector.openSession()`
alike (IPMI 2.0 section 22.18). Any command is sent in the session of the
client with [`send()`](low-level-api.html#sending-a-command-through-the-client), or as bytes with
[`raw()`](low-level-api.html#raw-requests).

## Cipher suites

| Algorithm | Implemented |
| --- | --- |
| Authentication | RAKP-none, RAKP-HMAC-SHA1, RAKP-HMAC-MD5, RAKP-HMAC-SHA256 |
| Integrity | none, HMAC-SHA1-96, HMAC-MD5-128, HMAC-SHA256-128 — **not** MD5-128 |
| Confidentiality | none, AES-CBC-128 — **not** xRC4-128 or xRC4-40 |

Hence the cipher suites 0 to 3, 6 to 8, and 15 to 17; see the
[full table](preparing-the-bmc.html#cipher-suites). MD5-128 and xRC4 are tracked in
[#106](https://github.com/metricshub/ipmi-java/issues/106).

## SDR records

[`SensorRecord.populateSensorRecord()`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorRecord.html)
decodes the records of the SDR repository (IPMI 2.0, section 43) into these classes:

| Type | Record | Class | Returned by `sensors()` |
| --- | --- | --- | --- |
| `01h` | Full Sensor | `FullSensorRecord` | yes, with its reading |
| `02h` | Compact Sensor | `CompactSensorRecord` | yes, with its reading |
| `03h` | Event-Only | `EventOnlyRecord` | no |
| `08h` | Entity Association | `EntityAssociationRecord` | no |
| `09h` | Device-relative Entity Association | `DeviceRelativeEntityAssiciationRecord` | no |
| `10h` | Generic Device Locator | `GenericDeviceLocatorRecord` | no |
| `11h` | FRU Device Locator | `FruDeviceLocatorRecord` | no (drives `frus()`) |
| `12h` | Management Controller Device Locator | `ManagementControllerDeviceLocatorRecord` | no |
| `13h` | Management Controller Confirmation | `ManagementControllerConfirmationRecord` | no |
| `C0h` – `FFh` | OEM | `OemRecord` | no |

Full, Compact and Event-Only records share
[`AbstractSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/AbstractSensorRecord.html)
(owner, entity, sensor type, event/reading type, units, name...).

### OEM and unknown records

IPMI 2.0 reserves the record types `C0h` to `FFh` for OEM use, and vendors do use them: a
GIGABYTE BMC tested with this library returns 19 records of type `D0h`, a Lenovo IMM one of type
`C0h`. They are all decoded as an
[`OemRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/OemRecord.html), which
keeps the whole vendor-defined payload as raw bytes. Only type `C0h` has a standard layout, with
a manufacturer ID: for the types `C1h` to `FFh`, `getManufacturerId()` returns `0` (unknown), not
the record's vendor.

A record that cannot be decoded — a reserved type such as the deprecated BMC Message Channel
Info record (`14h`), a record shorter than its header — is **skipped**: the walk goes on with the
next record, so one unexpected record does not cost the whole sensor list, and the record is
listed with its reason in `skippedRecords()` of the
[`SensorList`](apidocs/org/metricshub/ipmi/client/SensorList.html) or
[`FruList`](apidocs/org/metricshub/ipmi/client/FruList.html). When a BMC answers a whole-record Get SDR with fewer bytes than the record
declares, the client reads the record again in chunks.

## SEL records

[`SelRecord.populateSelRecord()`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/SelRecord.html)
decodes the 16-byte entries of the System Event Log (IPMI 2.0, section 32), which `sel()` returns
as [`SelEntry`](system-event-log.html#the-selentry-object)s:

| Type | Record | Decoded fields |
| --- | --- | --- |
| `02h` | System event | Timestamp, generator ID, sensor type and number, event/reading type, event offset (as a `ReadingType`), direction, the three event data bytes |
| `C0h` – `DFh` | OEM, timestamped | Timestamp, manufacturer ID, 6 OEM bytes |
| `E0h` – `FFh` | OEM, not timestamped | 13 OEM bytes |
| `03h` – `BFh` | Reserved | The record ID only |

An entry shorter than 16 bytes is refused with `IllegalArgumentException`, which ends the read
with an `IpmiClientException`: no BMC tested returns one.

## FRU records

[`ReadFruData.decodeFruData()`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/ReadFruData.html)
decodes the FRU information (Platform Management FRU Information Storage Definition v1.0):

| Area | Class |
| --- | --- |
| Internal Use | `InternalUseInfo`, the vendor-private bytes as they are |
| Chassis Info | `ChassisInfo` |
| Board Info | `BoardInfo` |
| Product Info | `ProductInfo` |
| MultiRecord: Power Supply Information | `PowerSupplyInfo` |
| MultiRecord: DC Output, DC Load | `DcOutputInfo`, `DcLoadInfo` |
| MultiRecord: Management Access | `ManagementAccessInfo` |
| MultiRecord: Base / Extended Compatibility | `BaseCompatibilityInfo`, `ExtendedCompatibilityInfo` |
| MultiRecord: OEM | `OemInfo` |
| MultiRecord: any other type (Extended DC Output, Extended DC Load, reserved) | `RawMultiRecordInfo`, with the type and the bytes of the record |

`frus()` returns every decoded area, the MultiRecord ones included
([`Fru.multiRecords()`](apidocs/org/metricshub/ipmi/client/Fru.html)). FRUs in another format,
such as the SPD data of memory modules, are not decoded
([#107](https://github.com/metricshub/ipmi-java/issues/107)): the FRU is returned without areas,
with the reason in `Fru.problems()`.

The decoder checks the common header checksum and rejects a FRU whose header is corrupt. An area
that the data read does not hold in full is skipped, the others are returned, and
`ReadFruData.decodeFruData(data, problems)` reports what was skipped and why. In the
MultiRecord area, a record of a type the library does not model (such as the Extended DC Output
and Extended DC Load records) is kept as a `RawMultiRecordInfo`, a record of an unknown format
version is skipped and reported, an area that ends without its end-of-list record is reported,
and the record that carries the end-of-list flag is decoded like the others.
