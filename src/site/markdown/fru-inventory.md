keywords: fru, field replaceable unit, inventory, serial number, part number, manufacturer, product, board, chassis, read fru data
description: Read the FRU inventory of a server through its BMC — which FRUs are read, and the Fru object with the Board, Chassis, Product and MultiRecord areas.

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

