keywords: fru, field replaceable unit, inventory, serial number, part number, manufacturer, product, board, chassis, read fru data, dimm, memory module, spd, serial presence detect, ddr4, ddr5, jedec
description: Read the FRU inventory of a server through its BMC — which FRUs are read, and the Fru object with the Board, Chassis, Product and MultiRecord areas, and the SPD data of memory modules.

# FRU Inventory

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

**Field Replaceable Units (FRUs)** — the chassis, the system board, power supplies, risers,
backplanes, sometimes the memory modules — carry a small EEPROM with their manufacturer, product
name, part number and serial number. The BMC exposes them through the FRU commands, and
describes which ones exist in its SDR repository.

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	for (Fru fru : bmc.frus().execute()) {
		System.out.println(fru.name() + ": " + fru.manufacturer() + " " + fru.model() + " S/N " + fru.serialNumber());
	}
}
```

## How the FRUs are read

[`frus().execute()`](apidocs/org/metricshub/ipmi/client/FrusRequest.html):

1. reads **FRU 0**, the built-in FRU of the BMC (usually the system board), with Get FRU
   Inventory Area Info and Read FRU Data;
2. walks the **SDR repository** (once per client, see the [SDR cache](configuration.html#sdr-cache))
   and, for each **FRU Device Locator** record of a *logical* FRU device (one accessed with the FRU
   commands of the BMC), reads that FRU the same way;
3. attaches FRU 0 to the first **Compact Sensor** record of the system board entity, under the
   name `<board product name> <entity instance>`, when no locator record pointed at it before.

`ids(int...)` restricts the read to the given FRU device IDs, and `timeout(Duration)` overrides
the deadline of the client for this operation:

```java
FruList frus = bmc.frus().ids(0, 1).timeout(Duration.ofSeconds(30)).execute();
```

The FRU data is read in chunks of 16 bytes, which keeps the requests small but makes large FRUs
slow to read: a few seconds per FRU on some BMCs
([#102](https://github.com/metricshub/ipmi-java/issues/102)).

A FRU whose data cannot be read entirely — not present, a read refused at some offset, an area
that cannot be decoded — is returned with what could be decoded, `complete()` false and the
reasons in `problems()`; a BMC that stops answering fails the operation. Physical FRU devices
(EEPROMs on a private I²C bus, read with Master Write-Read) are not read.

## The `Fru` object

Each [`Fru`](apidocs/org/metricshub/ipmi/client/Fru.html) is immutable and holds:

| Method | Content |
| --- | --- |
| `id()` | The FRU device ID, 0 for the system board |
| `name()` | The name of the FRU, from its locator record (`PSU 1`, `Riser 1x16`, ...) |
| `entity()`, `entityInstance()` | What the FRU is (`EntityId.PowerSupply`, `EntityId.SystemBoard`, ...) and which one |
| `manufacturer()`, `model()`, `serialNumber()`, `partNumber()` | The summary, from the Product area, or from the Board area for a field the Product area does not give; empty when neither gives it |
| `board()`, `product()`, `chassis()` | The decoded [`BoardInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/BoardInfo.html), [`ProductInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/ProductInfo.html) and [`ChassisInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/ChassisInfo.html) areas, each `Optional` |
| `multiRecords()` | The decoded records of the MultiRecord area ([`MultiRecordInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/MultiRecordInfo.html): power supply, DC output, management access...) |
| `spd()` | The decoded [`SpdInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/SpdInfo.html) of a memory module (see [Memory modules](#memory-modules)), `Optional` |
| `records()`, `locator()` | Every decoded area, and the [`FruDeviceLocatorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FruDeviceLocatorRecord.html) that located the FRU |
| `complete()`, `problems()` | Whether the inventory was read and decoded entirely, and what went wrong otherwise |

| Area | Fields |
| --- | --- |
| `BoardInfo` | `getBoardManufacturer()`, `getBoardProductName()`, `getBoardSerialNumber()`, `getBoardPartNumber()`, `getMfgDate()`, `getFruFileId()`, `getCustomBoardInfo()` |
| `ProductInfo` | `getManufacturerName()`, `getProductName()`, `getProductModelNumber()`, `getProductVersion()`, `getProductSerialNumber()`, `getAssetTag()`, `getFruFileId()`, `getCustomProductInfo()` |
| `ChassisInfo` | `getChassisType()`, `getChassisPartNumber()`, `getChassisSerialNumber()`, `getCustomChassisInfo()` |

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

## Memory modules

Some BMCs (a Lenovo XCC, for one) locate a FRU device per DIMM slot. Such a device returns the
**Serial Presence Detect (SPD)** data of the module, the JEDEC EEPROM contents that describe it,
rather than a FRU inventory with a common header and areas. The library recognizes SPD data by
its JEDEC DRAM device type and decodes the DDR3, DDR4 and DDR5 layouts into an
[`SpdInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/SpdInfo.html), the only
record of the FRU:

| Method | Content |
| --- | --- |
| `getMemoryType()`, `getModuleType()` | `DDR4`; `RDIMM`, `UDIMM`, `SO-DIMM`, `LRDIMM`... |
| `getCapacityMegabytes()`, `isEcc()` | The capacity of the module, computed from the SDRAM density, the device and bus widths and the ranks; whether the bus carries ECC bits |
| `getManufacturer()`, `getDramManufacturer()` | The makers of the module and of its DRAM chips, from their JEDEC JEP106 codes (`Samsung`, `Micron Technology`, `SK Hynix`, `Kingston`...); `JEDEC bank <n> code <xx>h` for a code the library does not name |
| `getPartNumber()`, `getSerialNumber()` | The part number (`M393A2K40BB2-CTD`) and the serial number, 8 hexadecimal digits |
| `getManufacturingYear()`, `getManufacturingWeek()` | When the module was made (`2017`, `45`), 0 when not programmed |

The summary of the `Fru` takes its values from the SPD data: `manufacturer()` is the module
manufacturer, `model()` and `partNumber()` the part number, `serialNumber()` the serial number.

```java
for (Fru fru : bmc.frus().execute()) {
	fru.spd().ifPresent(spd -> System.out.println(fru.name() + ": " + spd.getCapacityMegabytes() + " MB "
			+ spd.getMemoryType() + " " + spd.getModuleType() + " " + spd.getManufacturer() + " " + spd.getPartNumber()));
}
```

```
DIMM_2: 16384 MB DDR4 RDIMM Samsung M393A2K40BB2-CTD
DIMM_3: 16384 MB DDR4 RDIMM Samsung M393A2K40BB2-CTD
```

The SPD data of a DDR2 or LPDDR module is recognized but not decoded: the FRU is returned
without records and `problems()` says so. An empty DIMM slot is a FRU the BMC refuses to read
(completion code `CBh`), like any absent FRU.

