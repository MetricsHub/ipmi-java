keywords: ipmi java client, ipmi 2.0, rmcp+, bmc, hardware monitoring, sensors, fru, sel, overview
description: A Java client for IPMI 2.0 over LAN (RMCP+): read the chassis power state, the FRU inventory, the sensors and the System Event Log of a server's BMC, open a Serial over LAN console, or send any IPMI command yourself.

# IPMI Java Client

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## Overview

The **IPMI Java Client** talks to the **Baseboard Management Controller (BMC)** of a server
(Dell iDRAC, HPE iLO, Lenovo XClarity Controller, OpenBMC, and the BMC firmwares of most other
boards) over **IPMI 2.0 over LAN (RMCP+)**, on UDP port 623. It lets a Java application:

* read the **chassis status**: power on or off, last power event, power restore policy, faults,
  intrusion, and **control the power** (on, off, cycle, reset) and the identify LED
  ([Chassis Status](chassis-status.html)),
* read the **FRU inventory**: manufacturer, product name, part and serial numbers of the chassis,
  boards, power supplies and other Field Replaceable Units ([FRU Inventory](fru-inventory.html)),
* read the **sensors** of the BMC's SDR repository (its Full and Compact sensor records):
  temperatures, voltages, fan speeds, currents, power and energy readings with their thresholds,
  and the discrete states (presence, redundancy, failure, ...); [Sensors](sensors.html) lists
  what is not read,
* read the **System Event Log**: the hardware events the BMC logged (thresholds crossed, power
  supply failures, memory errors, intrusion), whole or since the last entry read
  ([System Event Log](system-event-log.html)),
* open a **Serial over LAN** console on the server's serial port ([Serial over LAN](serial-over-lan.html)),
  and
* send **any IPMI command** in the same session, as a command class or as raw bytes (DCMI and
  OEM network functions included), or through the low-level connector
  ([Low-Level API](low-level-api.html)).

The same operations are available from the command line with the **`ipmicli`** client
([Command-Line Client](cli.html)), installed with `curl -fsSL https://metricshub.org/ipmi-java/get | sh`
(`irm https://metricshub.org/ipmi-java/get.ps1 | iex` on Windows, see [Installation](installation.html#command-line-client)):

```text
ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors
```

The library is the IPMI engine of [MetricsHub](https://metricshub.com) hardware monitoring: its
[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) returns the chassis status,
the inventory and the sensors as typed Java objects.

It is a fork of the [IPMI Library for Java by Verax Systems](https://en.wikipedia.org/wiki/Verax_IPMI),
with the RAKP-HMAC-SHA256 and RAKP-HMAC-MD5 authentication algorithms added, tolerance for the OEM
records that vendors put in their SDR repository, and many fixes. Moving from the Verax library is
mostly a package rename ([Migrating from Verax](migrating-from-verax.html)).

## Add the dependency

The library requires **Java 8** or later, has no dependency, and is published on
[Maven Central](https://central.sonatype.com/artifact/${project.groupId}/${project.artifactId}):

```xml
<dependency>
  <groupId>${project.groupId}</groupId>
  <artifactId>${project.artifactId}</artifactId>
  <version>${project.version}</version>
</dependency>
```

See [Installation](installation.html) for Gradle and the requirements.

## Quick start

> [!NOTE]
> **On the BMC**, IPMI over LAN must be enabled, UDP port 623 reachable, and the account must be
> allowed to log in over LAN with at least the **User** privilege. Several vendors ship with IPMI
> over LAN disabled. See [Preparing the BMC](preparing-the-bmc.html).

Everything starts with [`IpmiClient.builder()`](apidocs/org/metricshub/ipmi/client/IpmiClient.Builder.html):
the client it builds owns **one session** with the BMC, opened by the first operation and closed
by `close()`, so always use it in a try-with-resources block.

```java
import org.metricshub.ipmi.client.ChassisStatus;
import org.metricshub.ipmi.client.Fru;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.Sensor;

public class Example {

	public static void main(String[] args) {
		try (IpmiClient bmc = IpmiClient
				.builder("bmc.example.com")                 // host name or IP address of the BMC
				.credentials("monitor", "the-password".toCharArray())
				.build()) {

			ChassisStatus chassis = bmc.chassisStatus();
			System.out.println("Power is " + (chassis.powerOn() ? "on" : "off"));

			for (Fru fru : bmc.frus().execute()) {
				System.out.println(fru.name() + ": " + fru.manufacturer() + " " + fru.model() + " S/N " + fru.serialNumber());
			}

			for (Sensor sensor : bmc.sensors().execute()) {
				sensor.reading().ifPresent(value ->
						System.out.println(sensor.name() + " = " + value + " " + sensor.unit() + " (" + sensor.status() + ")"));
			}
		}
	}
}
```

Which prints (from a Lenovo server, serial number masked):

```text
Power is on
System Board 1: LENOVO RD350 S/N S4M00000
PSU 1: LITEON PS-2451-6L-LF S/N 0000
Ambient Temp = 17.0 DegreesC (OK)
System Power = 92.0 Watts (OK)
Fan 1 = 6600.0 Rpm (OK)
System 3.3V = 3.38 Volts (OK)
```

The client runs its operations one at a time on its session, walks the SDR repository once for
both `frus()` and `sensors()`, and reopens the session when the BMC loses it. Every failure is an
unchecked [`IpmiClientException`](apidocs/org/metricshub/ipmi/client/IpmiClientException.html)
whose subclass says what to act on: the BMC refused the login, did not answer, answered an error
completion code, or the operation outlived its deadline
([Timeouts and Errors](timeouts-and-errors.html)).

## Where to go next

* [Installation](installation.html) — coordinates, supported JDKs and requirements
* [Command-Line Client](cli.html) — `ipmicli`: the identity of the BMC, the chassis status, the
  power control, the sensors, the FRUs, the System Event Log and a Serial over LAN console from
  the command line, with the standalone jar
* [Preparing the BMC](preparing-the-bmc.html) — enabling IPMI over LAN, the account, privilege
  level and cipher suites, the firewall
* [Device ID](device-id.html) — manufacturer, product and firmware revision of the BMC, the
  functions it provides
* [Chassis Status](chassis-status.html) — power state and chassis flags, power control, identify
  LED
* [FRU Inventory](fru-inventory.html) — how FRUs are read and which fields are reported
* [Sensors](sensors.html) — readings, thresholds, status and states
* [System Event Log](system-event-log.html) — the logged events, OEM entries, polling for new
  entries
* [Configuration](configuration.html) — every option of the client builder
* [Timeouts and Errors](timeouts-and-errors.html) — deadlines, message timeouts, retries, session
  recovery and the exceptions
* [Low-Level API](low-level-api.html) — `IpmiConnector`: sessions, cipher suites, any IPMI command
* [Serial over LAN](serial-over-lan.html) — a console on the server's serial port
* [Supported Commands](supported-commands.html) — commands, cipher suites, SDR and FRU records
* [Troubleshooting](troubleshooting.html) — common failures and how to diagnose them with
  `ipmitool` or `ipmiutil`
* [Upgrading](upgrading.html) — changes between versions, and the migration from 1.x
* [Migrating from Verax](migrating-from-verax.html) — moving from the Verax IPMI Library for Java
