keywords: device id, get device id, bmc identification, manufacturer id, product id, firmware revision, ipmi version, iana enterprise number, mc info, bmc info
description: Identify the BMC of a server through IPMI — manufacturer and product IDs, firmware revision, IPMI version and the functions it provides — with the Get Device ID command.

# Device ID

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The IPMI **Get Device ID** command (IPMI 2.0, section 20.1) tells what the BMC is: who makes it,
which product it is, which firmware it runs, which version of IPMI it implements and which
functions it provides. It is a single request that every BMC answers at the User level, and the
first thing a monitoring product reads to tell the platforms apart, before choosing the OEM
records or commands a vendor needs. It is also the message the client sends to keep its session
alive ([Keep-alive](configuration.html#keep-alive)).

## The `DeviceId` object

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	DeviceId device = bmc.deviceId();

	System.out.println("Manufacturer " + device.manufacturerId() + ", product " + device.productId());
	System.out.println("Firmware " + device.firmwareRevision() + ", IPMI " + device.ipmiVersion());
	if (device.functions().contains(DeviceFunction.SEL_DEVICE)) {
		System.out.println(bmc.sel().execute().size() + " events in the SEL");
	}
}
```

[`DeviceId`](apidocs/org/metricshub/ipmi/client/DeviceId.html) decodes the response (IPMI 2.0,
table 20-2) and never throws:

| Method | Meaning |
| --- | --- |
| `manufacturerId()` | The [IANA enterprise number](https://www.iana.org/assignments/enterprise-numbers/) of the vendor of the BMC: 674 for Dell, 5771 for Cisco, 10368 for Fujitsu, 10876 for Supermicro, 15370 for GIGABYTE, 19046 for Lenovo, 47196 for Hewlett Packard Enterprise... 0 when unspecified |
| `productId()` | The product, as the manufacturer numbers its products |
| `firmwareRevision()` | The firmware revision as `ipmitool` prints it: the major revision, a dot and the two digits of the minor revision (`5.38`, `1.05`) |
| `auxiliaryFirmwareRevision()` | Four bytes whose meaning the manufacturer defines (a build number, a boot block version), empty when the BMC does not send them |
| `ipmiVersion()` | The IPMI version the BMC implements: `2.0`, or `1.5` for a BMC without RMCP+ |
| `id()`, `revision()` | The device ID (32 on most BMCs) and its revision |
| `available()` | The BMC is in normal operation; `false` while it updates its firmware or its SDR repository |
| `providesDeviceSdrs()` | The BMC provides Device SDRs, as a satellite controller does |
| `functions()` | The functions it provides, as a set of [`DeviceFunction`](apidocs/org/metricshub/ipmi/client/DeviceFunction.html): `SENSOR_DEVICE`, `SDR_REPOSITORY_DEVICE`, `SEL_DEVICE`, `FRU_INVENTORY_DEVICE`, `IPMB_EVENT_RECEIVER`, `IPMB_EVENT_GENERATOR`, `BRIDGE`, `CHASSIS_DEVICE` |
| `data()` | The raw [`GetDeviceIdResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/app/GetDeviceIdResponseData.html), for the bytes as the BMC sent them |

The manufacturer and product IDs are what identifies the platform: `frus()` reads the
manufacturer name and the model of the system board as text, but the vendor of the BMC (whose
OEM SDR records, SEL entries and commands are to be expected) is this number. The functions are
what the BMC claims to implement: a BMC without `SEL_DEVICE` answers `C1h` to `sel()`.

What the BMCs of a few servers answer (`ipmicli ... info`, [Command-Line Client](cli.html)):

| BMC | `manufacturerId()` | `productId()` | `firmwareRevision()` | `ipmiVersion()` |
| --- | ---: | ---: | --- | --- |
| Lenovo IMM | 19046 | 502 | 5.38 | 2.0 |
| Supermicro | 10876 | 2366 | 7.02 | 2.0 |
| Dell iDRAC | 674 | 256 | 2.92 | 2.0 |
| HPE iLO 5 | 47196 | 8192 | 3.18 | 2.0 |
| Cisco IMC | 5771 | 21 | 4.01 | 2.0 |
| Fujitsu iRMC | 10368 | 1300 | 1.00 | 2.0 |
| GIGABYTE | 15370 | 358 | 13.05 | 2.0 |

## Errors

`deviceId()` opens the session when needed and fails with an
[`IpmiClientException`](timeouts-and-errors.html) when the login is refused or the BMC does not
answer. The command itself is mandatory in every IPMI implementation and needs no privilege: a
BMC that refuses it is not an IPMI BMC.
