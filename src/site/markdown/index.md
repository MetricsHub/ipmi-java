keywords: ipmi java client, ipmi 2.0, rmcp+, bmc, hardware monitoring, device id, chassis, power control, sensors, fru, sel, serial over lan, ipmicli, command line, overview
description: A Java client and command-line tool for IPMI 2.0 over LAN (RMCP+): identify a server's BMC, read its chassis status, FRU inventory, sensors and System Event Log, control the power, open a Serial over LAN console, or send any IPMI command.

# Java Client for IPMI 2.0 over LAN

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## Overview

The **IPMI Java Client** talks to the **Baseboard Management Controller (BMC)** of a server
(Dell iDRAC, HPE iLO, Lenovo IMM and XClarity Controller (XCC), Cisco IMC, Fujitsu iRMC,
Supermicro, and the BMC firmwares of most other boards; see
[Tested BMCs](preparing-the-bmc.html#tested-bmcs)) over **IPMI 2.0** (Intelligent Platform
Management Interface) **over LAN**, in the authenticated **RMCP+** (Remote Management Control
Protocol) sessions of IPMI 2.0, on UDP port 623. It lets a Java application:

* identify the **BMC**: manufacturer and product IDs, firmware revision, IPMI version and the
  functions it provides ([Device ID](device-id.html)),
* probe the BMC before any login, without credentials: the cipher suites it offers and its login
  options ([What the BMC supports before the login](device-id.html#what-the-bmc-supports-before-the-login),
  [`BmcCapabilities`](apidocs/org/metricshub/ipmi/client/BmcCapabilities.html)),
* read the **chassis status**: power on or off, last power event, power restore policy, faults,
  intrusion, and **control the power** (on, off, cycle, reset, soft shutdown, diagnostic
  interrupt) and the identify LED ([Chassis Status](chassis-status.html)),
* read the **FRU inventory**: manufacturer, product name, part and serial numbers of the chassis,
  boards, power supplies and other Field Replaceable Units, the Serial Presence Detect (SPD) data
  of memory modules and the Processor Information ROM of Intel Xeon processors
  ([FRU Inventory](fru-inventory.html)),
* read the **sensors** that the SDR records of the BMC describe (Full and Compact sensor
  records), including those of the satellite controllers the BMC reaches with Send Message:
  temperatures, voltages, fan speeds, currents, power and energy readings with their thresholds,
  and the discrete states (presence, redundancy, failure, ...);
  [Sensors](sensors.html#what-is-not-read) lists what is not read,
* read the **System Event Log**: the hardware events the BMC logged (thresholds crossed, power
  supply failures, memory errors, intrusion), whole or since the last entry read
  ([System Event Log](system-event-log.html)),
* open a **Serial over LAN** console on the server's serial port ([Serial over LAN](serial-over-lan.html)),
  and
* send **any IPMI command** in the same session, as a command class or as raw bytes (DCMI and
  OEM network functions included), or through the low-level connector
  ([Low-Level API](low-level-api.html)).

`deviceId()`, `capabilities()`, `chassisStatus()`, `frus()`, `sensors()` and `sel()` only read:
besides the session commands, they send Get commands, the Reserve commands that some BMCs require
before the SDR repository or the SEL is read, and Send Message to reach the other controllers
([Supported Commands](supported-commands.html#commands)); they change nothing on the server. The
power control, the identify LED and the Serial over LAN console act on the server or the BMC,
and so can the commands sent with `send()` or `raw()`.

The same operations are available from the command line with the **`ipmicli`** client
([Command-Line Client](cli.html)). Install it with one command
([Installation](installation.html#command-line-client) has the details):

> [!TABS]
> * Linux / macOS
>   ```bash
>   curl -fsSL https://metricshub.org/ipmi-java/get | sh
>   ```
> * Windows (PowerShell)
>   ```powershell
>   irm https://metricshub.org/ipmi-java/get.ps1 | iex
>   ```

Then, for example, list the sensors of a BMC:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors
```

The library is the IPMI engine of [MetricsHub](https://metricshub.com) hardware monitoring: its
[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) returns the identity of the
BMC, the chassis status, the inventory, the sensors and the System Event Log as typed Java
objects.

It is a fork of the [IPMI Library for Java by Verax Systems](https://web.archive.org/web/20250104201815/https://veraxsystems.com/ipmi-library-for-java/),
with the RAKP-HMAC-SHA256 and RAKP-HMAC-MD5 authentication and the HMAC-SHA256-128, HMAC-MD5-128
and MD5-128 integrity algorithms added (cipher suites 6 to 8, 11, 12 and 15 to 17), tolerance for
the OEM records that vendors put in their SDR repository, and many fixes. Moving from the Verax
library takes a package rename and a few code changes
([Migrating from Verax](migrating-from-verax.html)).

The library is free software, published under the
[GNU Lesser General Public License v3.0](https://github.com/metricshub/ipmi-java/blob/main/LICENSE):
applications under any license can use it, and a modified version of the library that you
distribute stays under the LGPL.

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
> allowed to log in over LAN with the **User** privilege at least. User is enough for every read
> operation, but for the sensors and FRUs of other controllers than the BMC, which most BMCs serve
> at the **Operator** level (some at **Administrator** only); controlling the power and the
> identify LED needs **Operator**, and Serial over LAN **Administrator** on most BMCs. A client
> built with `privilege(PrivilegeLevel.MaximumAvailable)` raises the session only when a command
> needs more ([Raising the session on demand](configuration.html#raising-the-session-on-demand)).
> Several vendors ship with IPMI over LAN disabled. See [Preparing the BMC](preparing-the-bmc.html).

Everything starts with `IpmiClient.builder(host)`, which returns an
[`IpmiClient.Builder`](apidocs/org/metricshub/ipmi/client/IpmiClient.Builder.html): the client it
builds owns **one session** with the BMC, opened by the first operation and closed by `close()`,
so always use it in a try-with-resources block.

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

Which prints, on a Lenovo RD350 (an excerpt: the server has 49 sensors and more FRUs; serial
numbers masked):

```text
Power is on
MotherBoard FRU: LENOVO RD350 S/N S4M00000
PSU1 FRU: LITEON PS-2451-6L-LF S/N 0000
Ambient Temp = 19.0 DegreesC (OK)
System Power = 88.0 Watts (OK)
Fan 1 = 6600.0 Rpm (OK)
System 3.3V = 3.38 Volts (OK)
```

The client runs its operations one at a time on its session, walks the SDR repository once for
both `frus()` and `sensors()` while the repository does not change
([SDR cache](configuration.html#sdr-cache)), and reopens the session when the BMC loses it.

Every failure of an operation, but for a call the client refuses (below), is an unchecked
[`IpmiClientException`](apidocs/org/metricshub/ipmi/client/IpmiClientException.html), whose
subclass says what to act on: the BMC refused the login (`IpmiAuthenticationException`), the
session could not be established for a network reason (`IpmiConnectionException`), the BMC
answered an error completion code (`IpmiCompletionCodeException`), or a message got no reply or
the operation outlived its deadline (`IpmiTimeoutException`). The other failures, such as a BMC
that offers none of the cipher suites, are an `IpmiClientException` itself. An invalid builder
setting throws `IllegalArgumentException`, and a call the client refuses (any operation of a
closed client, an operation that needs a session on a client built without credentials, a power
cycle of a system that is off) throws `IllegalStateException`
([Timeouts and Errors](timeouts-and-errors.html#exceptions)).

## Where to go next

* [Installation](installation.html) — coordinates, supported JDKs and requirements, the
  one-command installation of `ipmicli`
* [Command-Line Client](cli.html) — `ipmicli`: the identity of the BMC, the chassis status, the
  power control, the sensors, the FRUs, the System Event Log and a Serial over LAN console from
  the command line
* [Preparing the BMC](preparing-the-bmc.html) — the tested BMCs, enabling IPMI over LAN, the
  account, privilege level and cipher suites, the firewall
* [Device ID](device-id.html) — manufacturer, product and firmware revision of the BMC, the
  functions it provides, and what it supports before any login: cipher suites and login options
  (`capabilities()`)
* [Chassis Status](chassis-status.html) — power state and chassis flags, power control, identify
  LED
* [FRU Inventory](fru-inventory.html) — how FRUs are read, which fields are reported, memory
  modules and processors
* [Sensors](sensors.html) — readings, thresholds, status and states, sensor owners and shared
  records
* [System Event Log](system-event-log.html) — the logged events, OEM entries, polling for new
  entries
* [Serial over LAN](serial-over-lan.html) — a console on the server's serial port
* [Configuration](configuration.html) — every option of the client builder
* [Timeouts and Errors](timeouts-and-errors.html) — deadlines, message timeouts, retries, session
  recovery and the exceptions
* [Low-Level API](low-level-api.html) — `IpmiConnector`: sessions, cipher suites, any IPMI command
* [Supported Commands](supported-commands.html) — commands, cipher suites, SDR, SEL and FRU
  records, and the specifications whose sections and tables the site cites
  ([References](supported-commands.html#references))
* [Troubleshooting](troubleshooting.html) — common failures and how to diagnose them with
  `ipmitool` or `ipmiutil`
* [Upgrading](upgrading.html) — changes between versions, and the migration from 1.x
* [Migrating from Verax](migrating-from-verax.html) — moving from the Verax IPMI Library for Java
* [Javadoc](apidocs/index.html) — the API reference
