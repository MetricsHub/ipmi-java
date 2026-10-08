keywords: supported commands, ipmi commands, cipher suites, sdr record types, oem records, fru records, multirecord, completion codes
description: Reference of the IPMI commands, cipher suites, SDR record types and FRU records the library implements, and how it handles OEM and unknown records.

# Supported Commands

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The library implements **IPMI 2.0 over LAN** (RMCP+ sessions, IPMI 2.0 section 13) with the
commands below. Each command is a class of
[`org.metricshub.ipmi.core.coding.commands`](apidocs/org/metricshub/ipmi/core/coding/commands/package-summary.html),
sent with the [low-level API](low-level-api.html#sending-commands); the last column shows which
ones `IpmiClient` uses. A command not listed here can be added by
[extending `IpmiCommandCoder`](low-level-api.html#writing-your-own-command).

## Commands

| Command | Class | NetFn / Cmd | Used by `IpmiClient` |
| --- | --- | --- | --- |
| **Session** | | | |
| Get Channel Authentication Capabilities | `GetChannelAuthenticationCapabilities` | App / `38h` | every call, and the keep-alive |
| Get Channel Cipher Suites | `GetChannelCipherSuites` | App / `54h` | every call (unless `skipAuth`) |
| RMCP+ Open Session | `OpenSession` | (payload) | every call |
| RAKP Message 1 / 3 | `Rakp1`, `Rakp3` | (payload) | every call |
| Set Session Privilege Level | `SetSessionPrivilegeLevel` | App / `3Bh` | |
| Close Session | `CloseSession` | App / `3Ch` | every call |
| **Chassis** | | | |
| Get Chassis Status | `GetChassisStatus` | Chassis / `01h` | `getChassisStatus()` |
| Chassis Control | `ChassisControl` | Chassis / `02h` | |
| **SDR repository and sensors** | | | |
| Get SDR Repository Info | `GetSdrRepositoryInfo` | Storage / `20h` | |
| Reserve SDR Repository | `ReserveSdrRepository` | Storage / `22h` | `getSensors()`, `getFrus()` |
| Get SDR | `GetSdr` | Storage / `23h` | `getSensors()`, `getFrus()` |
| Get Sensor Reading | `GetSensorReading` | Sensor/Event / `2Dh` | `getSensors()` |
| **FRU** | | | |
| Get FRU Inventory Area Info | `GetFruInventoryAreaInfo` | Storage / `10h` | `getFrus()` |
| Read FRU Data | `ReadFruData` | Storage / `11h` | `getFrus()` |
| **System Event Log** | | | |
| Get SEL Info | `GetSelInfo` | Storage / `40h` | |
| Reserve SEL | `ReserveSel` | Storage / `42h` | |
| Get SEL Entry | `GetSelEntry` | Storage / `43h` | |
| **Payloads (Serial over LAN)** | | | |
| Activate Payload (SOL) | `ActivateSolPayload` | App / `48h` | |
| Deactivate Payload | `DeactivatePayload` | App / `49h` | |
| Get Payload Activation Status | `GetPayloadActivationStatus` | App / `4Ah` | |
| Get Channel Payload Support | `GetChannelPayloadSupport` | App / `4Eh` | |

The `IpmiClient` calls need nothing above the **User** privilege; Chassis Control needs
**Operator**, the payload commands and Serial over LAN **Administrator**.

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

| Type | Record | Class | Returned by `getSensors()` |
| --- | --- | --- | --- |
| `01h` | Full Sensor | `FullSensorRecord` | yes, with its reading |
| `02h` | Compact Sensor | `CompactSensorRecord` | yes, with its reading |
| `03h` | Event-Only | `EventOnlyRecord` | no |
| `08h` | Entity Association | `EntityAssociationRecord` | no |
| `09h` | Device-relative Entity Association | `DeviceRelativeEntityAssiciationRecord` | no |
| `10h` | Generic Device Locator | `GenericDeviceLocatorRecord` | no |
| `11h` | FRU Device Locator | `FruDeviceLocatorRecord` | no (drives `getFrus()`) |
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
Info record (`14h`), a record shorter than its header — is **skipped**: the client logs it at
the `WARN` level and goes on with the next record, so one unexpected record does not cost the
whole sensor list. When a BMC answers a whole-record Get SDR with fewer bytes than the record
declares, the client reads the record again in chunks.

A Get SDR reply that carries the next record ID but **no record byte at all** is not handled:
the Get SDR decoder rejects it (`IllegalArgumentException: Invalid response payload length`),
which ends the walk and fails `getSensors()` or `getFrus()`.

## FRU records

[`ReadFruData.decodeFruData()`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/ReadFruData.html)
decodes the FRU information (Platform Management FRU Information Storage Definition v1.0):

| Area | Class |
| --- | --- |
| Chassis Info | `ChassisInfo` |
| Board Info | `BoardInfo` |
| Product Info | `ProductInfo` |
| MultiRecord: Power Supply Information | `PowerSupplyInfo` |
| MultiRecord: DC Output, DC Load | `DcOutputInfo`, `DcLoadInfo` |
| MultiRecord: Management Access | `ManagementAccessInfo` |
| MultiRecord: Base / Extended Compatibility | `BaseCompatibilityInfo`, `ExtendedCompatibilityInfo` |
| MultiRecord: OEM | `OemInfo` |

`IpmiClient.getFrus()` returns the Chassis, Board and Product areas only. FRUs in another format,
such as the SPD data of memory modules, are not decoded
([#107](https://github.com/metricshub/ipmi-java/issues/107)), and are logged and left out.
Known decoding issues of the FRU areas are listed in
[#85](https://github.com/metricshub/ipmi-java/issues/85).
