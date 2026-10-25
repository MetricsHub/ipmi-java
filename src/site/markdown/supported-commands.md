keywords: supported commands, ipmi commands, privilege levels, cipher suites, sdr record types, oem records, sel records, fru records, multirecord, spd, pirom, ipmi specification
description: Reference of the IPMI commands, cipher suites, SDR, SEL and FRU records the library implements, the privilege each operation needs, how it handles OEM and unknown records, and the specifications it follows.

# Supported Commands

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The library implements **IPMI 2.0 over LAN** (RMCP+ sessions, IPMI 2.0 section 13) with the
commands below. It does not open IPMI 1.5 sessions, so a BMC that supports only IPMI 1.5 cannot
be used ([What this client needs](preparing-the-bmc.html#what-this-client-needs)). It reaches the
BMC over the network only, in RMCP+ sessions over UDP (port 623 by default): it cannot use the
in-band interface of the server's operating system (KCS or SSIF through the IPMI driver,
`ipmitool -I open`), and on the server itself it logs in to the BMC at its IP address with an
account, as from any other host.

Each command is a class of a subpackage of
[`org.metricshub.ipmi.core.coding.commands`](apidocs/org/metricshub/ipmi/core/coding/commands/package-summary.html),
named in the group rows of the table, sent with
[`IpmiClient.send()`](low-level-api.html#sending-a-command-through-the-client) or the
[low-level API](low-level-api.html#sending-commands); the last column shows which operations of
`IpmiClient` use them. Open Session and the RAKP messages are not commands but RMCP+ payloads of
their own types: the login sends them, not `send()`. A command not listed here can be added by
[extending `IpmiCommandCoder`](low-level-api.html#writing-your-own-command) (any network
function, the [extension and OEM ones](low-level-api.html#extension-and-oem-network-functions)
included), or sent as bytes with [`raw()`](low-level-api.html#raw-requests). Among the commands
not implemented are Clear SEL, the commands that read or change the sensor thresholds (`sensors()`
gives the thresholds of the SDR records), the boot options, the users, the LAN and channel
configuration, PEF and the watchdog, and the DCMI power management commands.

## Commands

| Command | Class | NetFn / Cmd | Used by `IpmiClient` |
| --- | --- | --- | --- |
| **Session** | `commands.session` | | |
| Get Channel Authentication Capabilities | `GetChannelAuthenticationCapabilities` | App / `38h` | every session, as its first message and in the IPMI v1.5 format, unless the builder fixes the suite with `cipherSuite(id)`; `capabilities()` |
| Get Channel Cipher Suites | `GetChannelCipherSuites` | App / `54h` | every session, unless the builder fixes the suite with `cipherSuite(id)`; `capabilities()` |
| RMCP+ Open Session | `OpenSession` | RMCP+ payload `10h` | every session |
| RAKP Message 1 / 3 | `Rakp1`, `Rakp3` | RMCP+ payloads `12h`, `14h` | every session |
| Set Session Privilege Level | `SetSessionPrivilegeLevel` | App / `3Bh` | every session opened at `privilege(Operator)`, `privilege(Administrator)` or `privilege(MaximumAvailable)`, right after RAKP 4 (sent by `IpmiConnector.openSession()`): to the requested level, or to User for `MaximumAvailable`; with `MaximumAvailable`, again each time the BMC refuses a command for insufficient privilege (`D4h`), `sol()` included, one level at a time up to Administrator ([Raising the session on demand](configuration.html#raising-the-session-on-demand)) |
| Get Session Info | `GetSessionInfo` | App / `3Dh` | `sol()`: is the session that holds a SOL payload still there? |
| Close Session | `CloseSession` | App / `3Ch` | `close()`; the login, when Set Session Privilege Level fails; `SolConsole.close()`, and `sol()` when the console fails to open, for the session the console opened when the BMC serves SOL on another port |
| **BMC** | `commands.app` | | |
| Get Device ID | `GetDeviceId` | App / `01h` | `deviceId()`, and the [keep-alive](configuration.html#keep-alive) of every session; `sensors()`, `frus()`: does a BMC that refuses Get SDR Repository Info hold Device SDRs? |
| Send Message | `SendMessage` | App / `34h` | `sensors()`, `frus()`: the sensors and FRUs of controllers other than the BMC ([Sensor owners](sensors.html#sensor-owners), [FRUs of other controllers](fru-inventory.html#frus-of-other-controllers)) |
| **Chassis** | `commands.chassis` | | |
| Get Chassis Status | `GetChassisStatus` | Chassis / `01h` | `chassisStatus()` |
| Chassis Control | `ChassisControl` | Chassis / `02h` | `chassisControl()` |
| Chassis Identify | `ChassisIdentify` | Chassis / `04h` | `chassisIdentify()`, `chassisIdentifyForever()` |
| **SDR repository and sensors** | `commands.sdr` | | |
| Get SDR Repository Info | `GetSdrRepositoryInfo` | Storage / `20h` | `sensors()`, `frus()` ([SDR cache](configuration.html#sdr-cache)) |
| Reserve SDR Repository | `ReserveSdrRepository` | Storage / `22h` | `sensors()`, `frus()`, when the BMC asks for a reservation |
| Get SDR | `GetSdr` | Storage / `23h` | `sensors()`, `frus()`: each record whole, or in chunks (8 bytes, then 16 at a time) when the BMC refuses the whole record (`CAh`, `FFh`) or returns fewer bytes than the record declares |
| Get Device SDR Info | `GetDeviceSdrInfo` | Sensor/Event / `20h` | `sensors()`, `frus()`: a BMC without SDR repository that holds Device SDRs |
| Get Device SDR | `GetDeviceSdr` | Sensor/Event / `21h` | `sensors()`, `frus()`: a BMC without SDR repository that holds Device SDRs, read like Get SDR |
| Reserve Device SDR Repository | `ReserveDeviceSdrRepository` | Sensor/Event / `22h` | `sensors()`, `frus()`: a BMC without SDR repository, when it asks for a reservation |
| Get Sensor Reading | `GetSensorReading` | Sensor/Event / `2Dh` | `sensors()` |
| Get Sensor Reading Factors | `GetSensorReadingFactors` | Sensor/Event / `23h` | `sensors()`: the reading of a non-linear sensor (linearization `70h`-`7Fh`) |
| **FRU** | `commands.fru` | | |
| Get FRU Inventory Area Info | `GetFruInventoryAreaInfo` | Storage / `10h` | `frus()`: FRU 0 of the BMC, then the FRU of each logical FRU Device Locator record, at the access LUN of the record, through Send Message for a FRU of another controller, and FRU 0 of each other controller whose Management Controller Device Locator record sets FRU Inventory Device ([How the FRUs are read](fru-inventory.html#how-the-frus-are-read)) |
| Read FRU Data | `ReadFruData` | Storage / `11h` | `frus()`, like Get FRU Inventory Area Info |
| **System Event Log** | `commands.sel` | | |
| Get SEL Info | `GetSelInfo` | Storage / `40h` | `sel()` |
| Reserve SEL | `ReserveSel` | Storage / `42h` | `sel()`, when the BMC asks for a reservation |
| Get SEL Entry | `GetSelEntry` | Storage / `43h` | `sel()` |
| Get SEL Time UTC Offset | `GetSelTimeUtcOffset` | Storage / `5Ch` | `sel()`, at the start of every read: the offset of the SEL time from UTC, subtracted from the timestamps; a BMC without the command, which IPMI 2.0 revision 1.1 added (`C1h` or `D5h`), or with an unspecified offset (`07FFh`) has its SEL time taken as UTC |
| **Payloads (Serial over LAN)** | `commands.payload` | | |
| Activate Payload (SOL) | `ActivateSolPayload` | App / `48h` | `sol()` |
| Deactivate Payload | `DeactivatePayload` | App / `49h` | `SolConsole.close()`; `sol()`, when the console fails to open after the activation, or to free an instance held by a session the BMC no longer has |
| Get Payload Activation Status | `GetPayloadActivationStatus` | App / `4Ah` | `sol()` |
| Get Payload Instance Info | `GetPayloadInstanceInfo` | App / `4Bh` | `sol()` |
| Get Channel Payload Support | `GetChannelPayloadSupport` | App / `4Eh` | none |

The privilege that each operation of `IpmiClient` needs
([Privilege level](configuration.html#privilege-level)):

| Operations | Privilege |
| --- | --- |
| `deviceId()`, `chassisStatus()`, `sel()`, the sensors and FRUs of the BMC | User |
| The sensors and FRUs of controllers other than the BMC, which Send Message reaches | Operator (IPMI 2.0 table G-1); User is enough on an HPE iLO 5, Administrator is needed on an AMI-based GIGABYTE BMC |
| `chassisControl()`, `chassisIdentify()`, `chassisIdentifyForever()` | Operator |
| `sol()`: Activate and Deactivate Payload | The level that the SOL configuration of the BMC sets (IPMI 2.0 table G-1), Administrator on most BMCs; the other payload commands and Get Session Info need User |

The level is requested at Open Session and RAKP Message 1. The specification starts every session
at User whatever was requested (IPMI 2.0 section 22.18), so `IpmiClient` and
`IpmiConnector.openSession()` alike set it with Set Session Privilege Level right after the login
when the requested level is Operator or Administrator. With `privilege(MaximumAvailable)`, the
session is set to User, as some BMCs start it at the requested maximum, and `IpmiClient` raises it
only when the BMC refuses a command for insufficient privilege
([Raising the session on demand](configuration.html#raising-the-session-on-demand)).

## Cipher suites

| Algorithm | Implemented | Classes |
| --- | --- | --- |
| Authentication | RAKP-none, RAKP-HMAC-SHA1, RAKP-HMAC-MD5, RAKP-HMAC-SHA256 | `AuthenticationRakpNone`, `AuthenticationRakpHmacSha1`, `AuthenticationRakpHmacMd5`, `AuthenticationRakpHmacSha256` |
| Integrity | none, HMAC-SHA1-96, HMAC-MD5-128, MD5-128, HMAC-SHA256-128 | `IntegrityNone`, `IntegrityHmacSha1_96`, `IntegrityHmacMd5_128`, `IntegrityMd5_128`, `IntegrityHmacSha256_128` |
| Confidentiality | none, AES-CBC-128 — **not** xRC4-128 or xRC4-40, which IPMI 2.0 table 13-19 makes optional for BMCs and none of the [tested BMCs](preparing-the-bmc.html#tested-bmcs) requires | `ConfidentialityNone`, `ConfidentialityAesCbc128` |

The classes are in
[`org.metricshub.ipmi.core.coding.security`](apidocs/org/metricshub/ipmi/core/coding/security/package-summary.html).
These algorithms make up the cipher suites 0 to 3, 6 to 8, 11, 12, and 15 to 17
([full table](preparing-the-bmc.html#cipher-suites)). `IpmiClient` refuses the suites without
integrity (0, 1, 6 and 15), and by default takes the first of 17, 3, 8, 16, 2, 7, 12, 11 that the
BMC offers; [How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)
also tells how the list of suites that a BMC sends is read. The
[low-level API](low-level-api.html#choosing-the-cipher-suite) takes any of the twelve.

## SDR records

[`SensorRecord.populateSensorRecord()`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorRecord.html)
decodes the records of the SDR repository (IPMI 2.0 section 43) into these classes:

| Type | Record | Class | Returned by `sensors()` |
| --- | --- | --- | --- |
| `01h` | Full Sensor | `FullSensorRecord` | yes, with its reading (none for a [sensor of system software](sensors.html#sensor-owners)) |
| `02h` | Compact Sensor | `CompactSensorRecord` | yes, with its reading (none for a sensor of system software), one sensor per instance of a [shared record](sensors.html#shared-records) |
| `03h` | Event-Only | `EventOnlyRecord` | no |
| `08h` | Entity Association | `EntityAssociationRecord` | no |
| `09h` | Device-relative Entity Association | `DeviceRelativeEntityAssociationRecord` | no |
| `10h` | Generic Device Locator | `GenericDeviceLocatorRecord` | no |
| `11h` | FRU Device Locator | `FruDeviceLocatorRecord` | no (`frus()` reads the logical FRUs it locates) |
| `12h` | Management Controller Device Locator | `ManagementControllerDeviceLocatorRecord` | no |
| `13h` | Management Controller Confirmation | `ManagementControllerConfirmationRecord` | no |
| `14h` | BMC Message Channel Info (deprecated in IPMI 1.5) | none: skipped | no |
| `00h`, `04h`-`07h`, `0Ah`-`0Fh`, `15h`-`BFh` | Reserved | none: skipped | no |
| `C0h`-`FFh` | OEM | `OemRecord` | no |

Full, Compact and Event-Only records share
[`AbstractSensorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/AbstractSensorRecord.html)
(owner, entity, sensor type, event/reading type, units, name...).

The name of a sensor or locator record is its ID string, decoded as its type/length byte says
(IPMI 2.0 section 43.15). Bits 4:0 give the length in characters, and bits 7:6 the encoding:

| Bits 7:6 | Encoding | Decoded as |
| --- | --- | --- |
| `00b` | Unicode, whose encoding the specification does not name | UTF-8, the length counting bytes |
| `01b` | BCD plus | 2 characters per byte |
| `10b` | 6-bit packed ASCII | 4 characters per 3 bytes |
| `11b` | 8-bit ASCII + Latin 1 | ISO 8859-1, one character per byte |

The bytes after the string, which BMCs with fixed-length records fill with padding, are ignored,
and the name ends at its first NUL character.

### OEM and unknown records

IPMI 2.0 reserves the record types `C0h` to `FFh` for OEM use, and vendors do use them: a
GIGABYTE BMC tested with this library returns 19 records of type `D0h`, a Lenovo ThinkServer System Manager (TSM) one of type
`C0h`. They are all decoded as an
[`OemRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/OemRecord.html), which
keeps the whole vendor-defined payload as raw bytes. Only type `C0h` has a standard layout, with
a manufacturer ID: for the types `C1h` to `FFh`, `getManufacturerId()` returns `0` (unknown), not
the record's vendor.

A record that cannot be decoded is **skipped**: a record of the deprecated type `14h` or of a
reserved type, a record too short for its 5-byte header or for the fields of its type, or a
record the BMC does not return in full. The walk goes on with the next record, so one unexpected
record does not cost the whole sensor list, and the record is listed with its reason in
`skippedRecords()` of the [`SensorList`](apidocs/org/metricshub/ipmi/client/SensorList.html) or
[`FruList`](apidocs/org/metricshub/ipmi/client/FruList.html).

## SEL records

[`SelRecord.populateSelRecord()`](apidocs/org/metricshub/ipmi/core/coding/commands/sel/SelRecord.html)
decodes the 16-byte entries of the System Event Log (IPMI 2.0 section 32), which `sel()` returns
as [`SelEntry`](system-event-log.html#the-selentry-object) objects:

| Type | Record | Decoded fields |
| --- | --- | --- |
| `02h` | System event | Timestamp, generator ID, sensor type and number, event/reading type, event offset (as a `ReadingType`), direction, the three event data bytes |
| `C0h`-`DFh` | OEM, timestamped | Timestamp, manufacturer ID, 6 OEM bytes |
| `E0h`-`FFh` | OEM, not timestamped | 13 OEM bytes |
| `00h`, `01h`, `03h`-`BFh` | Reserved | The record ID only |

An entry shorter than 16 bytes is refused with `IllegalArgumentException`, which ends the read
with an `IpmiClientException`. No tested BMC returns such an entry.

## FRU records

[`ReadFruData.decodeFruData()`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/ReadFruData.html)
decodes the FRU information (Platform Management FRU Information Storage Definition v1.0, see
[References](#references)):

| Area, record or format | Type | Class |
| --- | --- | --- |
| Internal Use area | — | `InternalUseInfo`, the vendor-private bytes as they are |
| Chassis Info area | — | `ChassisInfo` |
| Board Info area | — | `BoardInfo` |
| Product Info area | — | `ProductInfo` |
| MultiRecord: Power Supply Information | `00h` | `PowerSupplyInfo` |
| MultiRecord: DC Output, DC Load | `01h`, `02h` | `DcOutputInfo`, `DcLoadInfo` |
| MultiRecord: Management Access | `03h` | `ManagementAccessInfo` |
| MultiRecord: Base / Extended Compatibility | `04h`, `05h` | `BaseCompatibilityInfo`, `ExtendedCompatibilityInfo` |
| MultiRecord: OEM | `C0h`-`FFh` | `OemInfo` |
| MultiRecord: any other type: ASF (`06h`-`08h`), Extended DC Output (`09h`), Extended DC Load (`0Ah`), NVM Express (`0Bh`-`0Fh`), reserved (`10h`-`BFh`) | `06h`-`BFh` | `RawMultiRecordInfo`, with the type (`getRecordType()`) and the bytes of the record |
| JEDEC SPD data of a memory module (DDR3, DDR4, DDR5), in place of the FRU inventory | — | `SpdInfo`: memory and module type, capacity, ECC, module and DRAM manufacturers (JEDEC JEP106), part number, serial number, manufacturing date |
| Intel Processor Information ROM of a processor (data format revision 7), in place of the FRU inventory | — | `PiromInfo`: S-spec, part number, PPIN, CPUID, cores, frequencies, caches, NVM DIMM capacity, TDP and temperatures |

`frus()` returns every decoded area, the MultiRecord ones included
([`Fru.multiRecords()`](apidocs/org/metricshub/ipmi/client/Fru.html)). The FRU device of a DIMM
returns the SPD data of the module rather than a FRU inventory: the data is recognized by its
JEDEC DRAM device type and decoded into a single `SpdInfo`
([`Fru.spd()`](apidocs/org/metricshub/ipmi/client/Fru.html)); the FRU device of a processor
returns its Processor Information ROM, decoded into a single `PiromInfo`
([`Fru.pirom()`](apidocs/org/metricshub/ipmi/client/Fru.html)). The SPD data of a module of
another memory type (DDR, DDR2, DDR2 FB-DIMM, DDR4E, LPDDR3 to LPDDR5X, DDR5 NVDIMM-P) and a
Processor Information ROM of another revision are recognized but not decoded. SPD data that ends
before the end of the part number of the module is rejected, and so is a Processor Information
ROM shorter than its 128 bytes, with a wrong static checksum or with a section pointer outside the
ROM. A FRU that is recognized but not decoded, or rejected, is returned without areas, with the
reason in `Fru.problems()`.

The decoder checks the checksums that frame the data and ignores the others, which real firmware
gets wrong on genuine data:

* A FRU whose data is shorter than its 8-byte common header, whose format version is not 1, or
  whose common header fails its checksum is rejected: `frus()` returns it without areas, with the
  reason in `Fru.problems()`.
* The checksum of a chassis, board or product area is not checked: the area is decoded anyway. An
  area that the data read does not hold in full, or that cannot be decoded, is skipped and
  reported, and the other areas are returned.
* In the MultiRecord area, a record header that fails its checksum, or a record longer than the
  data read, ends the area: the records before it are returned and the problem is reported. The
  checksum of the record data is not checked (a Dell iDRAC 8 writes it off by one on its power
  supply records). A record of a type the library does not model (such as the Extended DC Output
  and Extended DC Load records, `09h` and `0Ah`) is kept as a `RawMultiRecordInfo`, a record of
  an unknown format version, or that cannot be decoded, is skipped and reported, an area that
  ends without its end-of-list record is reported, and the record that carries the end-of-list
  flag is decoded like the others.

`ReadFruData.decodeFruData(data, problems)` reports what was skipped and why; `frus()` returns
these messages in `Fru.problems()`.

## References

The section and table numbers cited on this site are those of these specifications:

* *Intelligent Platform Management Interface Specification, Second Generation, v2.0*, document
  revision 1.1 of October 1, 2013, with the E7 markup of April 21, 2015. Revision 1.0 (2004)
  numbers the cited sections the same way, but not all the tables: its table 44-11 is table G-1
  of revision 1.1, for example.
* [*Platform Management FRU Information Storage Definition* v1.0](https://www.intel.com/content/dam/www/public/us/en/documents/specification-updates/ipmi-platform-mgt-fru-info-storage-def-v1-0-rev-1-3-spec-update.pdf),
  document revision 1.3 of March 24, 2015, for the FRU areas and MultiRecords.
* JEDEC Standard 21-C, annexes K (DDR3) and L (DDR4), and JESD400-5 (DDR5), for the SPD data of
  memory modules, and JEDEC JEP106 for their manufacturer IDs.
* *Intel Xeon Scalable Processors Datasheet, Volume One*, chapter 5, for the Processor
  Information ROM.
* [*Data Center Manageability Interface (DCMI) Specification* v1.5](https://www.intel.com/content/dam/www/public/us/en/documents/technical-specifications/dcmi-v1-5-rev-spec.pdf),
  revision 1.0 of August 23, 2011, for the DCMI commands of the
  [raw request example](low-level-api.html#raw-requests).
