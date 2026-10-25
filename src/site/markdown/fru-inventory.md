keywords: fru, field replaceable unit, inventory, serial number, part number, manufacturer, product, board, chassis, read fru data, asset tag, multirecord, power supply, dimm, memory module, spd, serial presence detect, ddr4, ddr5, jedec, processor, pirom, s-spec, ppin
description: Read the FRU inventory of a server through its BMC — which FRUs are read, and the Fru object with the Board, Chassis, Product and MultiRecord areas, the SPD data of memory modules and the Processor Information ROM of processors.

# FRU Inventory

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

**Field Replaceable Units (FRUs)** — the chassis, the system board, power supplies, risers,
backplanes, sometimes the memory modules and the processors — carry a small EEPROM with their
manufacturer, product name, part number and serial number. The BMC exposes them through the FRU
commands, and describes which ones exist in its SDR repository.

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	for (Fru fru : bmc.frus().execute()) {
		System.out.println(fru.name() + ": " + fru.manufacturer() + " " + fru.model() + " S/N " + fru.serialNumber());
	}
}
```

The model and serial number of the server itself are those of its system FRU, FRU 0 on most
BMCs: see [Identifying the server](#identifying-the-server).

## How the FRUs are read

[`frus().execute()`](apidocs/org/metricshub/ipmi/client/FrusRequest.html):

1. walks the **SDR repository**, or takes it from the [SDR cache](configuration.html#sdr-cache)
   it shares with `sensors()`;
2. reads **FRU 0**, the built-in FRU of the BMC (usually the system board), with Get FRU
   Inventory Area Info and Read FRU Data, unless `ids()` leaves it out;
3. reads the FRU of each **FRU Device Locator** record of a *logical* FRU device, one that a
   management controller serves with these two commands, from the controller and at the logical
   unit number (LUN, 0 to 3) the record names (IPMI 2.0 table 43-7). The BMC serves its own FRUs.
   The FRUs of another controller are read through the BMC, which forwards the two commands with
   **Send Message**, as it does for sensor readings ([Sensor owners](sensors.html#sensor-owners));
4. returns FRU 0 at the position of the first **Compact Sensor** record of the System Board
   entity, when no locator record of FRU 0 of the BMC comes before that record in the repository.
   Its `entity()` is `SystemBoard`, its `entityInstance()` the one of that sensor, and its
   `name()` the board product name of FRU 0 (else its product name, else its chassis part number,
   else `System Board`) followed by that entity instance. A FRU 0 that could not be read is
   returned the same way, with its `problems()`;
5. otherwise returns FRU 0 after the other FRUs of the repository, when the BMC returned at least
   one of its areas, with the `SystemBoard` entity, instance 0, and the same name without an
   instance: the HPE iLO 5, whose System Board sensors are Full records, returns its FRU 0 this
   way. The FRU Inventory Device bit of the BMC's own Management Controller Device Locator record
   does not matter: Supermicro BMCs and the Fujitsu iRMC S5 clear it, yet serve FRU 0;
6. reads **FRU 0** at LUN 0 of each other controller whose **Management Controller Device
   Locator** record sets FRU Inventory Device (IPMI 2.0 table 43-8), through the BMC, unless a FRU
   Device Locator record already located it, and returns it with the entity, instance and ID
   string of that record. The primary FRU of a controller is never declared by a FRU Device
   Locator record (table 43-7, byte 7): `ipmitool fru print` reads it the same way.

Physical FRU devices (EEPROMs on a private I²C bus, read with Master Write-Read) are not read.

### Selecting FRUs

`ids(int...)` restricts the read to the given FRU device IDs, and `timeout(Duration)` overrides,
for this operation, the [deadline](timeouts-and-errors.html#deadline) of the client (2 minutes by
default), which covers the session setup, the SDR walk and every FRU read:

```java
FruList frus = bmc.frus().ids(0, 1).timeout(Duration.ofSeconds(30)).execute();
```

An ID selects the FRUs of that ID on every controller and LUN: `ids(1)` returns every FRU 1 that a
locator record declares, on the BMC and on any other controller. An ID the repository does not
locate is left out of the result, without an exception. The SDR repository is walked, or its cache
checked, all the same: the selection saves FRU reads only. Calling `ids()` again replaces the
IDs, and `ids()` without arguments reads no FRU.

When the deadline expires, `execute()` throws
[`IpmiTimeoutException`](apidocs/org/metricshub/ipmi/client/IpmiTimeoutException.html) and returns
none of the FRUs read so far. A session lost during the operation is reopened and the operation
run again once, within the same deadline
([Session recovery](timeouts-and-errors.html#session-recovery)).

### Read size

The FRU data is read in chunks of as many bytes as the controller returns at once. The first Read
FRU Data asks for 255 bytes, and each refusal (`C7h` to `CAh`) drops the count to the power of two
below the count sent (128, 64, 32), down to the 16 bytes every BMC answers; a refusal of 16 bytes
or fewer ends the read of that FRU, which is returned with the areas decoded from the bytes read
before that offset ([Incomplete FRUs](#incomplete-frus)). The count a controller accepts at a LUN
serves its other FRUs during the same operation; the next `frus()` starts again at 255. Most BMCs
answer 255 bytes; a Dell iDRAC 8 or a Cisco IMC answers 16, which makes a large FRU take a few
seconds there.

A FRU device that Get FRU Inventory Area Info declares word-addressed takes its offsets in words:
it is read an even number of bytes at a time (254 instead of 255), except for its last read.

### Incomplete FRUs

A FRU whose data cannot be read entirely — not present, a read refused at some offset, an area
that cannot be decoded — is returned with what could be decoded, `complete()` false and the
reasons in `problems()`. A BMC that stops answering fails the whole operation.

| `problems()` says | Meaning |
| --- | --- |
| `Cannot read FRU <id>: ... (completion code CBh)` | The FRU is declared but absent, an empty power supply bay or DIMM slot for instance: nothing is read. With another code, the controller refused the FRU before any data was read |
| `FRU <id> is truncated at offset <n>: ...` | The Read FRU Data at that offset was refused ([Read size](#read-size)), got no reply through the BMC, or returned no data (`No data returned`): the areas held in the first `<n>` bytes are decoded |
| `... from the controller at <xx>h on channel <c>: No reply through the BMC` | The controller behind the BMC did not answer ([FRUs of other controllers](#frus-of-other-controllers)) |
| `... from the controller at <xx>h on channel <c>: NAK on write: no controller acknowledged the address (completion code 83h)` | The BMC found no controller at that address. `Lost arbitration` (`81h`) and `Bus error` (`82h`) report a problem on the bus of the controller |
| `... from the controller at <xx>h on channel <c>: SendMessage failed: ... (completion code D4h). The command requires a higher privilege than the session has: ...` | The session lacks the privilege the BMC requires for Send Message ([FRUs of other controllers](#frus-of-other-controllers)) |
| `Cannot decode FRU <id>: ...` | The data is not a FRU inventory (`Invalid format version`, `Invalid common header checksum`), is SPD data of a DRAM type the library does not decode or that is truncated, or is a Processor Information ROM of another revision, truncated or corrupt (invalid static checksum, section pointer outside the ROM): no record is returned |
| `The <area> info area at offset <n> is truncated (<x> of <y> bytes read)`, `... is beyond the <m> byte(s) read`, `Cannot decode the <area> info area at offset <n>: ...`, `The internal use area ...` | That area is missing; the other areas are returned |
| `The multirecord ...` | The MultiRecord area is cut short or has a corrupt record header: the records before it are returned |
| `Cannot decode the multirecord at offset <n>: ...` | That record is missing; the records around it are returned |

For a FRU of another controller, the `Cannot read FRU`, `FRU <id> is truncated` and
`Cannot decode FRU` messages add `from the controller at <xx>h on channel <c>` before the colon,
with `, LUN <l>` after the channel for a LUN other than 0.

See also [Sensors or FRUs are missing](troubleshooting.html#sensors-or-frus-are-missing).

### FRUs of other controllers

A FRU device ID is unique per controller and LUN only: a FRU of another controller may have the
ID of a FRU of the BMC, and both are returned, each with its `locator()`.

A controller behind the BMC that does not answer costs its own FRUs only, whose `problems()` say
`Cannot read FRU 5 from the controller at 44h on channel 0: No reply through the BMC`. The session
is kept, and once a controller has not answered Get FRU Inventory Area Info, its other FRUs at the
same LUN are not requested again during the same operation. A controller that answered it but
not a Read FRU Data costs that FRU only.

Send Message to another controller needs the **Operator** privilege on most BMCs
(IPMI 2.0 table G-1). The User level is enough on an HPE iLO 5, and an AMI-based GIGABYTE BMC
requires Administrator ([Sensor owners](sensors.html#sensor-owners)). Below that level, the FRUs
of other controllers come back with a `D4h` in their `problems()`. A client built with
`privilege(PrivilegeLevel.MaximumAvailable)` raises the session on that first `D4h`, as far as
the account allows, and reads these FRUs
([Raising the session on demand](configuration.html#raising-the-session-on-demand)).

## The `Fru` object

Each [`Fru`](apidocs/org/metricshub/ipmi/client/Fru.html) is immutable, but the area objects and
the locator record it returns are the objects of the protocol core: they have setters and are
shared with the `Fru` (`locator().setName(...)` changes `name()`), so treat them as read-only. A
`Fru` holds:

| Method | Content |
| --- | --- |
| `id()` | The FRU device ID, 0 for the system board |
| `name()` | The name of the FRU, from its locator record (`PSU 1`, `Riser 1x16`, ...) |
| `entity()`, `entityInstance()` | What the FRU is (`EntityId.PowerSupply`, `EntityId.SystemBoard`, ...) and which one |
| `manufacturer()` | The manufacturer of the Product area (`getManufacturerName()`), else of the Board area (`getBoardManufacturer()`); the module manufacturer of a memory module; `Intel` for a processor |
| `model()` | The product name (`getProductName()`), else the board product name (`getBoardProductName()`); the part number of a memory module; the S-spec of a processor |
| `serialNumber()` | The product serial number (`getProductSerialNumber()`), else the board serial number (`getBoardSerialNumber()`); the serial number of a memory module; the PPIN of a processor |
| `partNumber()` | The product part/model number (`getProductModelNumber()`), else the board part number (`getBoardPartNumber()`); the part number of a memory module or of a processor |
| `board()`, `product()`, `chassis()` | The decoded [`BoardInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/BoardInfo.html), [`ProductInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/ProductInfo.html) and [`ChassisInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/ChassisInfo.html) areas, each `Optional` |
| `multiRecords()` | The decoded records of the MultiRecord area ([`MultiRecordInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/MultiRecordInfo.html): power supply, DC output, management access..., see [MultiRecord area](#multirecord-area)) |
| `spd()` | The decoded [`SpdInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/SpdInfo.html) of a memory module (see [Memory modules](#memory-modules)), `Optional` |
| `pirom()` | The decoded [`PiromInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/PiromInfo.html) of a processor (see [Processors](#processors)), `Optional` |
| `records()`, `locator()` | Every decoded record: the internal use area ([`InternalUseInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/InternalUseInfo.html), the vendor-private bytes, which no other method returns), the Chassis, Board and Product areas and the multirecords, or the single `SpdInfo` or `PiromInfo`; and the [`FruDeviceLocatorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FruDeviceLocatorRecord.html) that located the FRU |
| `complete()`, `problems()` | Whether the inventory was read and decoded entirely, and what went wrong otherwise ([Incomplete FRUs](#incomplete-frus)). The checksums of the common header and of the multirecord headers are checked; those of the areas and of the multirecord data are not, as real firmware gets them wrong on genuine data |

Each summary value (`manufacturer()`, `model()`, `serialNumber()`, `partNumber()`) is trimmed. A
blank field counts as absent, and the value is empty, never null, when no area gives it.

The `BoardInfo`, `ProductInfo` and `ChassisInfo` areas give these fields, as strings unless listed
below the table:

| Area | Fields |
| --- | --- |
| `BoardInfo` | `getBoardManufacturer()`, `getBoardProductName()`, `getBoardSerialNumber()`, `getBoardPartNumber()`, `getMfgDate()`, `getFruFileId()`, `getCustomBoardInfo()` |
| `ProductInfo` | `getManufacturerName()`, `getProductName()`, `getProductModelNumber()`, `getProductVersion()`, `getProductSerialNumber()`, `getAssetTag()`, `getFruFileId()`, `getCustomProductInfo()` |
| `ChassisInfo` | `getChassisType()`, `getChassisPartNumber()`, `getChassisSerialNumber()`, `getCustomChassisInfo()` |

* `getMfgDate()` is a `java.util.Date`, null when the board gives no manufacturing date.
* `getFruFileId()` returns the raw bytes.
* `getChassisType()` returns a
  [`ChassisType`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/ChassisType.html).
* `getCustomBoardInfo()`, `getCustomProductInfo()` and `getCustomChassisInfo()` return a
  `String[]`, one element per custom field.
* A field the area does not hold is empty, and a field encoded as binary is given in hexadecimal
  digits.

```java
for (Fru fru : bmc.frus().execute()) {
	fru.product().ifPresent(product -> System.out.println(product.getManufacturerName() + " "
			+ product.getProductName() + " S/N " + product.getProductSerialNumber()));
	if (!fru.complete()) {
		System.out.println("  incomplete: " + fru.problems());
	}
}
```

[`FruList`](apidocs/org/metricshub/ipmi/client/FruList.html), the result of `execute()`, is an
unmodifiable `List<Fru>` that also gives the SDR records the walk skipped
(`skippedRecords()`).

### MultiRecord area

`multiRecords()` holds one object per record of the MultiRecord area, of the class of its type
([FRU records](supported-commands.html#fru-records)). Each record is a `MultiRecordInfo`: cast it
to the class of its type to read its fields.

| Record type | Class and fields |
| --- | --- |
| `00h` Power Supply Information | `PowerSupplyInfo`: `getCapacity()` in W, `getPeakVa()` in VA (-1 when not given), `getMaximumInrush()` in A, `getLowEndInputVoltage1()`, `getHighEndInputVoltage1()`, `getLowEndInputVoltage2()`, `getHighEndInputVoltage2()` in units of 10 mV, `getLowEndInputFrequencyRange()`, `getHighEndInputFrequencyRange()` in Hz |
| `01h` DC Output | `DcOutputInfo`: `getOutputNumber()`, `getNominalVoltage()`, `getMaximumNegativeDeviation()`, `getMaximumPositiveDeviation()` in units of 10 mV (1200 for 12 V) |
| `02h` DC Load | `DcLoadInfo`: `getOutputNumber()`, `getNominalVoltage()`, `getMinimumVoltage()`, `getMaximumVoltage()` in units of 10 mV, `getMinimumCurrentLoad()`, `getMaximumCurrentLoad()` in mA |
| `03h` Management Access | `ManagementAccessInfo`: `getRecordType()`, the kind of access information, and `getAccessInfo()`, its text |
| `04h`, `05h` Base and Extended Compatibility | `BaseCompatibilityInfo`, `ExtendedCompatibilityInfo`: `getManufacturerId()`, `getEntityId()`, `getCompatibilityBase()`, `getCodeStart()`, `getCodeRangeMasks()` |
| `C0h` to `FFh` OEM | `OemInfo`: `getManufacturerId()`, `getOemData()` |
| Any other type (ASF, Extended DC Output, Extended DC Load, NVM Express, reserved) | `RawMultiRecordInfo`: `getRecordType()`, `getData()` |

### Identifying the server

The model and serial number of the server itself are in the Product Info area of its system FRU,
FRU 0 on most BMCs: `model()`, `partNumber()` and `serialNumber()` of that `Fru` give its product
name, model number and serial number (`System x3650 M2`, `794722G` and `KD9098C` on an IBM
System x3650 M2), and `ProductInfo.getAssetTag()` its asset tag. The Board Info area describes the
system board only: its serial number (`YK159097Z09Y` on the same server) is not the server's. The
Chassis Info area (`chassis()`) holds the chassis part and serial numbers; on that server the
chassis serial number is the product serial number. Vendors fill these areas differently: when
the Product area does not give a field, the summary falls back to the Board area.

## Memory modules

Some BMCs, a Lenovo XClarity Controller (XCC) for one, locate a FRU device per DIMM slot. Such a
device returns the **Serial Presence Detect (SPD)** data of the module, the JEDEC EEPROM contents
that describe it, rather than a FRU inventory with a common header and areas. The library
recognizes SPD data by its JEDEC DRAM device type and decodes the DDR3, DDR4 and DDR5 layouts
into an [`SpdInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/SpdInfo.html), the
only record of the FRU:

| Method | Content |
| --- | --- |
| `getMemoryType()`, `getModuleType()` | The DRAM type, `DDR3`, `DDR4` or `DDR5`; the module type, `RDIMM`, `UDIMM`, `SO-DIMM`, `LRDIMM`... (`Module type <x>h` for a code JEDEC does not define) |
| `getCapacityMegabytes()`, `isEcc()` | The capacity of the module in megabytes (MiB), computed from the SDRAM density, the device and bus widths and the ranks, 0 when the SPD data does not give it; whether the bus carries ECC bits |
| `getManufacturer()`, `getDramManufacturer()` | The makers of the module and of its DRAM chips, from their JEDEC JEP106 codes (`Samsung`, `Micron Technology`, `SK Hynix`, `Kingston`...); `JEDEC bank <n> code <xx>h` for a code the library does not name; empty when not programmed (the DRAM manufacturer also when the SPD data ends before it) |
| `getPartNumber()`, `getSerialNumber()` | The part number (`M393A2K40BB2-CTD`) and the serial number, 8 hexadecimal digits; each empty when not programmed |
| `getManufacturingYear()`, `getManufacturingWeek()` | When the module was made (`2017`, `45`), 0 when not programmed |

The summary of the `Fru` takes its values from the SPD data: `manufacturer()` is the module
manufacturer, `model()` and `partNumber()` the part number, `serialNumber()` the serial number.

```java
for (Fru fru : bmc.frus().execute()) {
	fru.spd().ifPresent(spd -> System.out.println(fru.name() + ": " + spd.getCapacityMegabytes() + " MB "
			+ spd.getMemoryType() + " " + spd.getModuleType() + " " + spd.getManufacturer() + " " + spd.getPartNumber()));
}
```

```text
DIMM_2: 16384 MB DDR4 RDIMM Samsung M393A2K40BB2-CTD
DIMM_3: 16384 MB DDR4 RDIMM Samsung M393A2K40BB2-CTD
```

The SPD data of another DRAM type (DDR, DDR2 and its FB-DIMM variants, DDR4E, LPDDR3 to LPDDR5X,
DDR5 NVDIMM-P) is recognized but not decoded, and SPD data that ends before the end of the part
number is reported as truncated. Either way the FRU is returned without records, and `problems()`
says why (`Cannot decode FRU <id>: The SPD data of a DDR2 module is not decoded`). An empty DIMM
slot is a FRU the BMC refuses to read (completion code `CBh`), like any absent FRU.

## Processors

The same BMCs may locate a FRU device per processor, declared as a *System Processor Cartridge
FRU / PIROM* (device type `10h`, modifier `03h`). Such a device returns the **Processor
Information ROM** (PIROM) of the CPU: the 128-byte EEPROM on the package of an Intel Xeon
processor, described in chapter 5 of the Intel Xeon Scalable Processors Datasheet, Volume One.

The library recognizes a PIROM by its header, which holds a data format revision and the size
`0080h`. A ROM of data format revision 7 with a valid static checksum is decoded into a
[`PiromInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/PiromInfo.html), the
only record of the FRU:

| Method | Content |
| --- | --- |
| `getSSpec()` | The S-spec of the processor, which identifies its exact model and stepping (`SR3GH`, an Intel Xeon Silver 4110) |
| `getPartNumber()` | The part number marked on the processor: the Intel family number and SKU number |
| `getPpin()` | The Protected Processor Inventory Number, the unique 64-bit identifier of the processor, as 16 hexadecimal digits |
| `getCpuid()` | The low 16 bits of the CPUID signature as an `int`, which holds the type, family, model and stepping: `0x0654` for a Skylake-SP of stepping 4 |
| `getCores()`, `isHyperThreading()` | The number of cores, and whether the processor supports Hyper-Threading, two threads per core (the ROM does not say whether the BIOS enables it) |
| `getBaseFrequencyMhz()`, `getMaxFrequencyMhz()`, `getSystemClockMhz()` | The P1 (base) and P0 (turbo) core frequencies, and the system clock |
| `getL2KilobytesPerCore()`, `getL3Kilobytes()`, `getNvmDimmMaxCapacityGigabytes()` | The caches, and the maximum capacity of non-volatile memory DIMMs the processor supports |
| `getTdpWatts()`, `getTcaseMax()`, `getDtsMax()` | The thermal design power in watts, and the maximum case temperature and maximum digital thermal sensor (DTS) temperature in °C |

A field the ROM does not program, or whose section is absent, is 0, `false` or an empty string.

The summary of the `Fru` takes its values from the ROM: `manufacturer()` is `Intel`, `model()`
the S-spec, `partNumber()` the part number and `serialNumber()` the PPIN.

```java
for (Fru fru : bmc.frus().execute()) {
	fru.pirom().ifPresent(cpu -> System.out.println(fru.name() + ": " + cpu.getSSpec() + ", " + cpu.getCores()
			+ " cores at " + cpu.getBaseFrequencyMhz() + " MHz, " + cpu.getTdpWatts() + " W, PPIN " + cpu.getPpin()));
}
```

```text
PIROM 1: SR3GH, 8 cores at 2100 MHz, 85 W, PPIN 634AFBC3354D3C6D
PIROM 2: SR3GH, 8 cores at 2100 MHz, 85 W, PPIN 666DB709E2AB8496
```

A ROM of another revision, a truncated ROM, an invalid static checksum or a section pointer
outside the ROM leaves the FRU without records: `complete()` is false and `problems()` gives the
reason.

## With the command-line client

`ipmicli fru` prints the same FRUs, one block per FRU with the fields of its chassis, board and
product areas, or of its memory module or processor. `fru <id>...` reads only these FRUs, like
`ids()`, but exits with 66 for an ID the repository does not locate, and `--json` gives the
summary fields of `Fru` and one object per area. The MultiRecord and internal use areas are not
printed. A FRU read incompletely is printed with what was read, its problem goes to stderr, and
`fru` exits with 1 (66 when an ID is also missing). See [`fru`](cli.html#fru) in the manual of
the command-line client.
