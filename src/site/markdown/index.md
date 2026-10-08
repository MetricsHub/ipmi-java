keywords: ipmi java client, ipmi 2.0, rmcp+, bmc, hardware monitoring, sensors, fru, overview
description: A Java client for IPMI 2.0 over LAN (RMCP+): read the chassis power state, the FRU inventory and the sensors of a server's BMC, or send any IPMI command yourself.

# IPMI Java Client

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## Overview

The **IPMI Java Client** talks to the **Baseboard Management Controller (BMC)** of a server
(Dell iDRAC, HPE iLO, Lenovo XClarity Controller, OpenBMC, and the BMC firmwares of most other
boards) over **IPMI 2.0 over LAN (RMCP+)**, on UDP port 623. It lets a Java application:

* read the **chassis status**: power on or off, last power event, power restore policy, faults,
  intrusion ([Chassis Status](chassis-status.html)),
* read the **FRU inventory**: manufacturer, product name, part and serial numbers of the chassis,
  boards, power supplies and other Field Replaceable Units ([FRU Inventory](fru-inventory.html)),
* read the **sensors** of the BMC's SDR repository (its Full and Compact sensor records):
  temperatures, voltages, fan speeds, currents, power and energy readings with their thresholds,
  and the discrete states (presence, redundancy, failure, ...); [Sensors](sensors.html) lists
  what is not read, and
* send **any IPMI command** through the low-level connector, including the System Event Log
  and chassis control commands, and open a **Serial over LAN** console
  ([Low-Level API](low-level-api.html), [Serial over LAN](serial-over-lan.html)).

The library is the IPMI engine of [MetricsHub](https://metricshub.com) hardware monitoring: its
high-level [`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) returns the
inventory and the sensors either as Java objects or as the semicolon-separated text that the
MetricsHub connectors parse ([text output format](sensors.html#text-output-format)).

It is a fork of the [IPMI Library for Java by Verax Systems](https://en.wikipedia.org/wiki/Verax_IPMI),
with the RAKP-HMAC-SHA256 and RAKP-HMAC-MD5 authentication algorithms added, tolerance for the OEM
records that vendors put in their SDR repository, and many fixes. Moving from the Verax library is
mostly a package rename ([Migrating from Verax](migrating-from-verax.html)).

## Add the dependency

The library requires **Java 8** or later and is published on
[Maven Central](https://central.sonatype.com/artifact/${project.groupId}/${project.artifactId}):

```xml
<dependency>
  <groupId>${project.groupId}</groupId>
  <artifactId>${project.artifactId}</artifactId>
  <version>${project.version}</version>
</dependency>
```

See [Installation](installation.html) for Gradle, the dependencies and logging.

## Quick start

> [!NOTE]
> **On the BMC**, IPMI over LAN must be enabled, UDP port 623 reachable, and the account must be
> allowed to log in over LAN with at least the **User** privilege. Several vendors ship with IPMI
> over LAN disabled. See [Preparing the BMC](preparing-the-bmc.html).

Everything starts with an
[`IpmiClientConfiguration`](apidocs/org/metricshub/ipmi/client/IpmiClientConfiguration.html) and
the static methods of [`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html):

```java
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientConfiguration;

public class Example {

	public static void main(String[] args) throws Exception {
		IpmiClientConfiguration config = new IpmiClientConfiguration(
				"bmc.example.com",            // host name or IP address of the BMC
				"monitor",                    // user name
				"the-password".toCharArray(), // password
				null,                         // BMC key (Kg), only with two-key authentication
				false,                        // skipAuth: discover the cipher suites first
				120);                         // overall timeout of each call, in seconds

		// "System power state is up"
		System.out.println(IpmiClient.getChassisStatusAsStringResult(config));

		// One line per FRU, per device with states, and per sensor reading
		System.out.println(IpmiClient.getFrusAndSensorsAsStringResult(config));
	}
}
```

Which prints (from a Lenovo server, serial numbers masked):

```text
System power state is up
FRU;LENOVO;RD350;S4M00000 - 00000000000001
FRU;LITEON;PS-2451-6L-LF;0000
Power Unit;3;Power Unit 3;;;;PSU Redundancy=Fully Redundant
Power Supply;1;Power Supply 1;;;;PSU1 Present=Presence detected
Temperature;0008;Ambient Temp;Air Inlet 1;17.0;37;39
PowerConsumption;000d;System Power;Power Unit 2;92.0
Fan;0014;Fan 1;Fan Device 1;6600.0;1600;
Voltage;0022;System 3.3V;System Board 1;3380.0;3040;3560
```

The same data is available as Java objects:

```java
GetChassisStatusResponseData status = IpmiClient.getChassisStatus(config);
List<Fru> frus = IpmiClient.getFrus(config);
List<Sensor> sensors = IpmiClient.getSensors(config);
```

Each call opens its own RMCP+ session, sends its commands, closes the session and releases its
UDP port; nothing has to be closed by the caller. Each call throws `TimeoutException` when it does
not complete within the configured timeout, and `ExecutionException` wrapping the cause when the
session cannot be opened or a command fails ([Timeouts and Errors](timeouts-and-errors.html)).

## Where to go next

* [Installation](installation.html) — coordinates, supported JDKs, dependencies and logging
* [Preparing the BMC](preparing-the-bmc.html) — enabling IPMI over LAN, the account, privilege
  level and cipher suites, the firewall
* [Chassis Status](chassis-status.html) — power state and chassis flags
* [FRU Inventory](fru-inventory.html) — how FRUs are read and which fields are reported
* [Sensors](sensors.html) — readings, thresholds, states, and the text output format
* [Configuration](configuration.html) — every option of `IpmiClientConfiguration`
* [Timeouts and Errors](timeouts-and-errors.html) — overall and per-message timeouts, retries,
  exceptions and logging
* [Low-Level API](low-level-api.html) — `IpmiConnector`: sessions, cipher suites, any IPMI command
* [Serial over LAN](serial-over-lan.html) — a console on the server's serial port
* [Supported Commands](supported-commands.html) — commands, cipher suites, SDR and FRU records
* [Troubleshooting](troubleshooting.html) — common failures and how to diagnose them with
  `ipmitool` or `ipmiutil`
* [Upgrading](upgrading.html) — changes between versions
* [Migrating from Verax](migrating-from-verax.html) — moving from the Verax IPMI Library for Java
