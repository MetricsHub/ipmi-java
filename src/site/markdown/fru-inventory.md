keywords: fru, field replaceable unit, inventory, serial number, part number, manufacturer, product, board, chassis, read fru data
description: Read the FRU inventory of a server through its BMC — which FRUs are read, the Board, Chassis and Product areas, and the FRU lines of the text output.

# FRU Inventory

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

**Field Replaceable Units (FRUs)** — the chassis, the system board, power supplies, risers,
backplanes, sometimes the memory modules — carry a small EEPROM with their manufacturer, product
name, part number and serial number. The BMC exposes them through the FRU commands, and
describes which ones exist in its SDR repository.

```java
List<Fru> frus = IpmiClient.getFrus(config);
```

## How the FRUs are read

[`IpmiClient.getFrus()`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) opens a session and:

1. reads **FRU 0**, the built-in FRU of the BMC (usually the system board), with Get FRU
   Inventory Area Info and Read FRU Data;
2. walks the **SDR repository** and, for each **FRU Device Locator** record of a *logical* FRU
   device (one accessed with the FRU commands of the BMC), reads that FRU the same way;
3. attaches FRU 0 to the first **Compact Sensor** record of the system board entity, under the
   name `<board product name> <entity instance>`.

The FRU data is read in chunks of 16 bytes, which keeps every request small enough for any BMC
but makes large FRUs slow to read: a few seconds per FRU on some BMCs
([#102](https://github.com/metricshub/ipmi-java/issues/102)).

A FRU that cannot be read — not present, or answering with an error at some offset — is logged at
the `WARN` level and reported truncated, or not at all; it never fails the whole call. Physical
FRU devices (EEPROMs on a private I²C bus, read with Master Write-Read) are not read.

## The `Fru` object

Each [`Fru`](apidocs/org/metricshub/ipmi/client/model/Fru.html) holds:

* `getFruLocator()` — the
  [`FruDeviceLocatorRecord`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/FruDeviceLocatorRecord.html)
  that describes the FRU: `getName()`, `getFruEntityId()` and `getFruEntityInstance()` (what the
  FRU is: a power supply, a processor board, ...), `getDeviceId()` (the FRU ID);
* `getFruRecords()` — the decoded information areas of the FRU, among:

| Record | Fields |
| --- | --- |
| [`BoardInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/BoardInfo.html) | `getBoardManufacturer()`, `getBoardProductName()`, `getBoardSerialNumber()`, `getBoardPartNumber()`, `getMfgDate()`, `getFruFileId()`, `getCustomBoardInfo()` |
| [`ProductInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/ProductInfo.html) | `getManufacturerName()`, `getProductName()`, `getProductModelNumber()`, `getProductVersion()`, `getProductSerialNumber()`, `getAssetTag()`, `getFruFileId()`, `getCustomProductInfo()` |
| [`ChassisInfo`](apidocs/org/metricshub/ipmi/core/coding/commands/fru/record/ChassisInfo.html) | `getChassisType()`, `getChassisPartNumber()`, `getChassisSerialNumber()`, `getCustomChassisInfo()` |

The MultiRecord area (power supply, DC output, management access records) is decoded by the
library but not returned by `getFrus()`; read it with the [low-level API](low-level-api.html) and
`ReadFruData.decodeFruData()` if you need it.

```java
for (Fru fru : IpmiClient.getFrus(config)) {
	for (FruRecord record : fru.getFruRecords()) {
		if (record instanceof ProductInfo) {
			ProductInfo product = (ProductInfo) record;
			System.out.println(product.getManufacturerName() + " " + product.getProductName()
					+ " S/N " + product.getProductSerialNumber());
		}
	}
}
```

## FRU lines of the text output

[`getFrusAndSensorsAsStringResult()`](sensors.html#text-output-format) starts with one line per
FRU:

```text
FRU;$vendor;$model;$serialNumber
```

For example (serial numbers masked):

```text
FRU;LENOVO;RD350;S4M00000 - 00000000000001
FRU;LITEON;PS-2451-6L-LF;0000
FRU;LENOVO;Riser 1x16;8SSC50A00000V1SH4AX0000 - SC50A00000
```

The fields come from the **Product** area when it has a manufacturer name, and from the **Board**
area otherwise:

| Field | Product area | Board area |
| --- | --- | --- |
| `$vendor` | Manufacturer name | Board manufacturer |
| `$model` | Product name | Board product name |
| `$serialNumber` | Product serial number, followed by ` - ` and the product part/model number when both exist | Board serial number, followed by ` - ` and the board part number when both exist |

A FRU with neither a vendor nor a model is not listed. The lines are sorted by how complete and
how central they are:

1. the system chassis and system board, with a model and a serial number (from the Product
   area);
2. the front and back panel boards, then the other FRUs, with a model and a serial number (from
   the Product area);
3. every other FRU (incomplete Product area, or Board area only).

The same vendor, model and serial number are repeated on the
[device lines](sensors.html#device-state-lines) of the sensors that belong to the same entity.
