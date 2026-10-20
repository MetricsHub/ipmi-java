keywords: device id, get device id, bmc identification, manufacturer id, product id, firmware revision, ipmi version, iana enterprise number, device functions, mc info, bmc info, capabilities, cipher suites, authentication capabilities
description: Identify the BMC of a server through IPMI — manufacturer and product IDs, firmware revision, IPMI version and the functions it provides — with the Get Device ID command, and read what it supports before any login.

# Device ID

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The IPMI **Get Device ID** command (IPMI 2.0 section 20.1) tells what the BMC is: who makes it,
which product it is, which firmware it runs, which version of IPMI it implements and which
functions it provides. It is a single request that every BMC answers at the User level, and the
first thing a monitoring product reads to tell the platforms apart, before choosing the OEM
records or commands a vendor needs. It is also the message the client sends to keep its session
alive ([Keep-alive](configuration.html#keep-alive)). Before any login, `capabilities()` reads
what the BMC supports ([below](#what-the-bmc-supports-before-the-login)).

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

[`DeviceId`](apidocs/org/metricshub/ipmi/client/DeviceId.html) decodes the response (IPMI 2.0
table 20-2) and never throws:

| Method | Meaning |
| --- | --- |
| `manufacturerId()` | The [IANA enterprise number](https://www.iana.org/assignments/enterprise-numbers/) of the vendor of the BMC (674 for Dell, 5771 for Cisco, 10368 for Fujitsu, 10876 for Supermicro, 15370 for GIGABYTE, 19046 for Lenovo, 47196 for Hewlett Packard Enterprise, among others); 0 when unspecified |
| `productId()` | The product, as the manufacturer numbers its products |
| `firmwareRevision()` | The firmware revision as `ipmitool` prints it: the major revision, a dot and the two digits of the minor revision (`5.38`, `1.05`) |
| `auxiliaryFirmwareRevision()` | Four bytes whose meaning the manufacturer defines (a build number, a boot block version), empty when the BMC does not send them |
| `ipmiVersion()` | The IPMI version the BMC implements, from the BCD byte of the reply: `2.0` in practice, since the client logs in with RMCP+ only and a BMC that implements IPMI 1.5 alone fails the login ([Troubleshooting](troubleshooting.html#the-bmc-does-not-support-ipmi-2-0)) |
| `id()`, `revision()` | The device ID (32 on most BMCs) and its revision |
| `available()` | The BMC is in normal operation; `false` while it updates its firmware or its SDR repository, or initializes itself |
| `providesDeviceSdrs()` | The BMC provides Device SDRs, as a satellite controller does |
| `functions()` | The functions the BMC flags in the Additional Device Support byte, as a set of [`DeviceFunction`](apidocs/org/metricshub/ipmi/client/DeviceFunction.html), empty when it flags none (table below) |
| `data()` | The raw [`GetDeviceIdResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/app/GetDeviceIdResponseData.html), for the bytes as the BMC sent them |

The manufacturer and product IDs identify the platform. `frus()` gives the manufacturer name and
the model of the system board as text, but the manufacturer ID tells the vendor of the BMC, and so
which OEM SDR records, SEL entries and commands to expect. The functions are what the BMC claims
to implement: on a BMC without `SEL_DEVICE`, `sel().execute()` typically fails with an
`IpmiCompletionCodeException` (`C1h`, invalid command).

| `DeviceFunction` | Bit | The BMC flags that it |
| --- | --- | --- |
| `SENSOR_DEVICE` | 0 | has sensors |
| `SDR_REPOSITORY_DEVICE` | 1 | holds the SDR repository |
| `SEL_DEVICE` | 2 | holds the System Event Log |
| `FRU_INVENTORY_DEVICE` | 3 | holds a FRU inventory |
| `IPMB_EVENT_RECEIVER` | 4 | accepts event messages from the IPMB |
| `IPMB_EVENT_GENERATOR` | 5 | sends event messages onto the IPMB |
| `BRIDGE` | 6 | answers the commands of the Bridge network function (ICMB bridging); it says nothing about Send Message |
| `CHASSIS_DEVICE` | 7 | implements the Chassis commands, as the chassis device of the ICMB specification |

`DeviceFunction.isSetIn(byte)` tests one function in the Additional Device Support byte of a
Device ID read with `send()`, such as the reply of a controller behind the BMC
([Commands for another controller](low-level-api.html#commands-for-another-controller)):
`DeviceFunction.SEL_DEVICE.isSetIn(me.getAdditionalDeviceSupport())`, where `me` is the
`GetDeviceIdResponseData` of the example there.

What the BMCs of a few servers answer ([`ipmicli ... info`](cli.html#info)):

| BMC | `manufacturerId()` | `productId()` | `firmwareRevision()` | `ipmiVersion()` |
| --- | ---: | ---: | --- | --- |
| Lenovo ThinkServer System Manager (TSM) | 19046 | 502 | 5.38 | 2.0 |
| Supermicro | 10876 | 2366 | 7.02 | 2.0 |
| Dell iDRAC | 674 | 256 | 2.92 | 2.0 |
| HPE iLO 5 | 47196 | 8192 | 3.18 | 2.0 |
| Cisco IMC | 5771 | 21 | 4.01 | 2.0 |
| Fujitsu iRMC | 10368 | 1300 | 1.00 | 2.0 |
| GIGABYTE | 15370 | 358 | 13.05 | 2.0 |

## What the BMC supports before the login

`capabilities()` reads what the BMC supports, with Get Channel Authentication Capabilities and
Get Channel Cipher Suites. It needs no credentials and opens no session, and returns a
[`BmcCapabilities`](apidocs/org/metricshub/ipmi/client/BmcCapabilities.html): the standard cipher
suites the BMC offers with their algorithms (the OEM suites are left out), whether it requires the
[BMC key](configuration.html#bmc-key) (Kg), the logins it accepts (anonymous login, null and
non-null user names), and whether it authenticates every message and the commands of the User
level. [Checking what the BMC offers](preparing-the-bmc.html#checking-what-the-bmc-offers)
describes each method with an example, and [`ipmicli probe`](cli.html#probe) prints the result.
`capabilities()` fails with `IpmiConnectionException` when the host is unknown or the BMC does not
answer, and with `IpmiClientException` on a BMC without IPMI 2.0 (RMCP+), so `ipmi20()` is always
`true`.

## Errors

`deviceId()` opens the session when needed and fails with an
[`IpmiClientException`](timeouts-and-errors.html#exceptions): `IpmiAuthenticationException` when
the login is refused, `IpmiConnectionException` when the BMC does not answer the session setup,
`IpmiTimeoutException` when the deadline expires. Like the other read operations, it is retried
once on a new session when the session was lost while it ran
([Session recovery](timeouts-and-errors.html#session-recovery)): a BMC that stopped answering
then fails it with `IpmiConnectionException`, as it does not answer the setup of the new session
either, and with `IpmiTimeoutException` when the new session opens but Get Device ID again gets
no reply. The command itself is mandatory in every IPMI implementation and needs only the User
privilege, the lowest level a session of the client runs at: a BMC that refuses it is not an IPMI
BMC.

## With the command-line client

`ipmicli ... info` prints the same identity, one field per line: device ID and revision, firmware
revision, IPMI version, manufacturer and product IDs, the device-available and device-SDR flags,
the `DeviceFunction` names and the auxiliary firmware revision; `info --json` gives the fields of
`DeviceId`. `ipmicli probe` prints what the BMC supports before the login. See
[`info`](cli.html#info) and [`probe`](cli.html#probe).
