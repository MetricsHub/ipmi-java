# IPMI Java Client
![GitHub release (with filter)](https://img.shields.io/github/v/release/metricshub/ipmi-java)
![Maven Central](https://img.shields.io/maven-central/v/org.metricshub/ipmi-java)
![Build](https://img.shields.io/github/actions/workflow/status/metricshub/ipmi-java/deploy.yml)
![GitHub top language](https://img.shields.io/github/languages/top/metricshub/ipmi-java)
![License](https://img.shields.io/badge/license-LGPL--3.0-blue)

This project is a fork of the excellent [IPMI Library for Java by Verax Systems](https://web.archive.org/web/20250104201815/https://veraxsystems.com/ipmi-library-for-java/). It is however not related to [another fork by rbuckland](https://github.com/rbuckland/ipmilib).

See **[Project Documentation](https://metricshub.org/ipmi-java)** and the [Javadoc](https://metricshub.org/ipmi-java/apidocs) for more information on how to use this library in your code.

The IPMI Java Client talks to the Baseboard Management Controller (BMC) of a server over IPMI 2.0 over LAN (RMCP+). It requires Java 8 or later and has no dependency. It:

* identifies the BMC (manufacturer, product, firmware revision),
* reads what the BMC offers before any login, without credentials (its cipher suites and login options),
* reads the chassis status, and controls the power and the identify LED,
* reads the Field Replaceable Units (FRUs), including the SPD data of the memory modules and the Processor Information ROM (PIROM) of Intel processors,
* reads the sensors of the SDR repository with their readings, thresholds and states, including those of the controllers that the BMC reaches with Send Message (power supplies, fans, RAID adapters),
* reads the System Event Log (SEL),
* opens Serial over LAN consoles,
* sends any IPMI command in the same session.

The results are typed Java objects. Add the library to your build (Gradle and the requirements are on the [installation page](https://metricshub.org/ipmi-java/installation.html)):

```xml
<dependency>
  <groupId>org.metricshub</groupId>
  <artifactId>ipmi-java</artifactId>
  <version>2.0.00</version>
</dependency>
```

```java
import org.metricshub.ipmi.client.*;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;

char[] password = System.console().readPassword("Password: ");

try (IpmiClient bmc = IpmiClient.builder("bmc.example.com").credentials("monitor", password).build()) {
    ChassisStatus chassis = bmc.chassisStatus();
    System.out.println("Power is " + (chassis.powerOn() ? "on" : "off"));

    for (Sensor sensor : bmc.sensors().types(SensorType.Temperature, SensorType.Fan).execute()) {
        sensor.reading().ifPresent(value -> System.out.println(sensor.name() + " = " + value + " " + sensor.unit()));
    }

    for (Fru fru : bmc.frus().execute()) {
        System.out.println(fru.name() + ": " + fru.manufacturer() + " " + fru.model() + " S/N " + fru.serialNumber());
    }

    for (SelEntry entry : bmc.sel().execute()) {
        System.out.println(entry); // SEL entry 5 at 2024-05-15T08:23:27Z: PowerUnit sensor 100, Power off/down (assertion)
    }
}
```

A client owns one RMCP+ session, opened by the first operation and closed by `close()`: always use it in a try-with-resources block. The BMC must have IPMI over LAN enabled, and the account needs:

* **User** to identify the BMC and to read the chassis status, the sensors, the FRUs and the SEL,
* **Operator** to control the power and the identify LED, and, on most BMCs, to read the sensors and FRUs of the controllers other than the BMC (power supplies, fans, RAID adapters), which the BMC reaches with Send Message (Administrator on an AMI-based GIGABYTE BMC),
* **Administrator** for Serial over LAN on most BMCs.

See [Preparing the BMC](https://metricshub.org/ipmi-java/preparing-the-bmc.html). The session runs at the level that `privilege()` sets on the builder, User by default; with `privilege(PrivilegeLevel.MaximumAvailable)`, it runs at User and is raised only when a command needs more, as far as the account allows ([Raising the session on demand](https://metricshub.org/ipmi-java/configuration.html#raising-the-session-on-demand)).

## Command-line client

The library comes with a command-line client, `ipmicli`, that exposes the same operations. Install it with one command (Java 8 or later required):

```bash
curl -fsSL https://metricshub.org/ipmi-java/get | sh
```

On Windows (PowerShell):

```powershell
irm https://metricshub.org/ipmi-java/get.ps1 | iex
```

See the [installation page](https://metricshub.org/ipmi-java/installation.html#command-line-client) for details. The client is a self-contained executable jar (`target/ipmi-java-<version>-standalone.jar`, also on the [releases page](https://github.com/metricshub/ipmi-java/releases/latest)), which `java -jar` runs as well.

What the BMC offers, before any login:

```bash
ipmicli -h bmc.example.com probe
```

The sensors, with the password read from a file:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors
```

The FRU inventory as JSON Lines:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt fru --json
```

Subcommands: `probe`, `info` (the identity of the BMC), `chassis`, `power status|on|off|cycle|reset|soft|diag`, `identify`, `sensors`, `fru`, `sel` (the System Event Log, `--since <id>` for the new entries), `sol` (an interactive Serial over LAN console) and `raw` (any request, like `ipmitool raw`: `raw 0x2c 0x01 0xdc 0x01`). Diagnostics go to stderr, the exit code says what failed (77 authentication, 69 unreachable, 124 timeout...). See the [Command-Line Client](https://metricshub.org/ipmi-java/cli.html) page for the options, the output formats and the `ipmitool` equivalents.

## Upgrading

See [Upgrading](https://metricshub.org/ipmi-java/upgrading.html) for the changes of each version.

### From 2.0.00

Code written for 2.0.00 links unchanged with 2.1.00 and compiles unchanged unless it uses a renamed enum constant as a `case` label; which reads more of the server and fixes several decodings, so some results change; see [Upgrading from 2.0.00 to 2.1.00](https://metricshub.org/ipmi-java/upgrading.html#upgrading-from-2-0-00-to-2-1-00) for the details.

* Each sensor and each FRU is read from the controller its SDR record names, at the LUN of the record. The sensors and FRUs of the other controllers (power supplies, fans, RAID and network adapters) are read through the BMC with Send Message: the power supply, fan, network and drive sensors of an HPE iLO 5 now have their readings. Send Message needs the Operator privilege on most BMCs; below it, these sensors and FRUs are returned without reading or inventory, with the `D4h` completion code in their `problem()` or `problems()`. The sensors owned by system software (the BIOS sensors of Dell iDRACs) are `UNAVAILABLE`, where 2.0.00 returned the states of an unrelated BMC sensor. A BMC without SDR repository that holds the SDRs of its own sensors is walked with Get Device SDR.
* A Compact record shared by several sensors gives one `Sensor` per sensor: every drive slot and DIMM slot of a Dell iDRAC, where 2.0.00 listed the first one only.
* The FRUs of memory modules (`Fru.spd()`: DDR3, DDR4 and DDR5 SPD data) and of Intel processors (`Fru.pirom()`) are decoded. FRUs are read 255 bytes at a time instead of 16, with a smaller count when the controller refuses it.
* `privilege(PrivilegeLevel.MaximumAvailable)` (`ipmicli -L auto`) is accepted: the session runs at User and is raised only when a command needs more.
* Cipher suites 11 and 12 (MD5-128 integrity) open sessions, and the default preference becomes 17, 3, 8, 16, 2, 7, 12, 11.
* The `states()` of a threshold sensor list the thresholds its reading is at or beyond (`Upper Critical Going High`), a discrete sensor described by a Full record has no `reading()`, the threshold and generic states of OEM sensor types are decoded, the reserved sensor type codes are `SensorType.Reserved` instead of `Oem`, and `Sensor.typeCode()`, `SelEntry.sensorTypeCode()` and `Sensor.percentage()` are new.
* The jar declares the automatic module name `org.metricshub.ipmi`: a modular application writes `requires org.metricshub.ipmi;` instead of `requires ipmi.java;`.
* Misspelled names are fixed, and the old ones stay as deprecated aliases until 3.0: `SensorType.SystemFirmwareProgress` (printed so by `ipmicli`), `CompletionCode.InsufficientPrivilege` and `DeviceRelativeEntityAssociationRecord`. `CommandCodes.GET_DEVICE_SDR_INFO` is `20h` (it was `21h`, now `GET_DEVICE_SDR`): recompile code that uses it.
* Non-linear sensors have a `reading()`, and `Sensor.name()` is never `null`. `FullSensorRecord.getSensorResolution()` returns `|M x 10^R|`, and `NaN` for a linearizable or non-linear sensor.

### From 1.x to 2.0.00

Version 2.0.00 replaces the client API of 1.x and removes the SLF4J dependency; see [Upgrading from 1.x](https://metricshub.org/ipmi-java/upgrading.html#upgrading-from-1-x-to-2-0-00) for the migration table.

* `IpmiClientConfiguration` and the static methods of `IpmiClient` are replaced by the builder above: `IpmiClient.builder(host).credentials(user, password).build()` returns a reusable, thread-safe, `AutoCloseable` client that keeps one session open across its operations (`deviceId()`, `chassisStatus()`, `chassisControl(PowerCommand)`, `chassisIdentify(seconds)`, `chassisIdentifyForever()`, `sensors()`, `frus()`, `sel()`, `send(command)`, `raw(netFn, command)`, `sol().open()`, `capabilities()`), walks the SDR repository once for `frus()` and `sensors()`, and reopens the session when the BMC loses it. The cipher suite preference, the privilege level, the message timeout, the retries and the keep-alive period are settings of the builder; `PropertiesManager` and the `connection.properties` and `vxipmi.properties` files are removed.
* The results are typed and immutable (`ChassisStatus`, `Sensor` with its converted reading, thresholds, status and states, `Fru` with its decoded areas, `SelEntry` with its sensor, event, direction and timestamp, `SensorList` and `FruList` with the SDR records the walk skipped), and replace the `org.metricshub.ipmi.client.model` classes. The objects of the protocol core that they also return (the FRU areas and `Fru.locator()`, `Sensor.record()` and `readingData()`, `SelEntry.record()`, `ChassisStatus.data()`) are shared by reference: treat them as read-only. The semicolon-separated text output (`IpmiResultConverter`, `DeviceDescription`) is MetricsHub's format and moves to the MetricsHub IPMI extension, where it stays byte for byte the same for the same BMC data, except for the sensor and FRU names that 1.x padded with NUL characters and the OS Boot states that 1.x did not name.
* The checked `ExecutionException`, `TimeoutException` and `InterruptedException` are replaced by the unchecked `IpmiClientException` hierarchy: `IpmiAuthenticationException`, `IpmiConnectionException`, `IpmiTimeoutException`, `IpmiCompletionCodeException`.
* The library no longer logs: the problems that do not fail an operation are reported in its result (`Sensor.problem()`, `Fru.problems()`, `skippedRecords()`, `ReadFruData.decodeFruData(data, problems)`), and `org.slf4j:slf4j-api` is no longer a dependency.
* In the low-level API, the commands no longer take `IpmiVersion`, `CipherSuite` and `AuthenticationType` in their constructors (the session sets them when it sends the command), `IpmiCommandCoder<R>` declares the response type so that `IpmiConnector.sendMessage()` returns it without a cast, `IpmiConnector.setTimeout(int)` and `setRetries(int)` replace the properties files, `CipherSuite.forId(id)` builds a standard suite, `IpmiCommandCoder.getNetworkFunctionCode()` and `IpmiLanRequest(int networkFunctionCode, ...)` write commands of the extension and OEM network functions (DCMI), and a wrong password fails the handshake with `AuthenticationException`.

## Build instructions

This is a simple Maven project. The library is compiled for Java 8, but building it requires **JDK 17** or later and Maven 3.8.9 or later. Build with:

```bash
mvn verify
```

`mvn verify site` also builds the documentation in `target/site` (sources in [src/site](src/site)).

Each push to `main` publishes the current `-SNAPSHOT` version (the library and its `standalone` jar) to the Central snapshots repository, https://central.sonatype.com/repository/maven-snapshots/, so that a fix can be tried before it is released (declare that repository, with snapshots enabled, in your build).

## Code format and quality checks

The code is formatted with the MetricsHub Eclipse formatter profile ([metricshub-eclipse-formatter.xml](metricshub-eclipse-formatter.xml), shared with the other MetricsHub Java projects), and the build fails on unformatted code. Simply run the below command before committing:

```bash
mvn formatter:format
```

The build also fails on [Checkstyle](checkstyle.xml) violations. A justified violation can be suppressed with `// CHECKSTYLE.OFF: <RuleName>` and `// CHECKSTYLE.ON: <RuleName>` comments.

[PMD](pmd.xml) violations fail the build too, and so does any block of 100 tokens or more duplicated elsewhere in the code (CPD): extract the shared code instead of copying it.

The build fails on any SpotBugs bug as well. An intentional one is suppressed with `@SuppressFBWarnings` and a `justification`.

To ignore the whole-tree reformat commit in `git blame`, run once:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

## Release instructions

The artifact is published to [Maven Central](https://central.sonatype.com/) through the
[Central Portal](https://central.sonatype.com/publishing) with the `central-publishing-maven-plugin` (server Id `central`,
authenticated with a Portal user token). Manual deployments require these credentials in your Maven `settings.xml`.

It is strongly recommended to only use [GitHub Actions "Release to Maven Central"](https://github.com/metricshub/ipmi-java/actions/workflows/release.yml) to perform a release:

* Set the version of the `<dependency>` at the top of this README to the version being released (the workflow does
  not update it)
* Manually trigger the "Release to Maven Central" workflow
* Specify the version being released and the next version number (SNAPSHOT)
* The workflow deploys the release to Maven Central, where the Central Portal publishes it automatically
  (`autoRelease: true`), publishes the documentation to https://metricshub.org/ipmi-java (GitHub Pages; only a
  release updates it), creates the GitHub release, then adds the `ipmi-java-standalone.jar` alias and the `.sha256`
  checksums that the `ipmicli` installers download (if that last job fails, re-run it: the installers fail until it
  succeeds)
* Check on the [Central Portal](https://central.sonatype.com/publishing/deployments) that the deployment reached the
  *Published* state
* Edit the notes of the GitHub release: above the generated list of pull requests, summarize the changes users will
  notice and link to the matching section of [Upgrading](https://metricshub.org/ipmi-java/upgrading.html), for example
  https://metricshub.org/ipmi-java/upgrading.html#upgrading-from-2-0-00-to-2-1-00 (the heading in lower case, with its
  spaces and dots replaced by dashes)
* Merge the PR that has been created to prepare the next version

## Security

Report a vulnerability privately with GitHub's [Report a vulnerability](https://github.com/metricshub/ipmi-java/security/advisories/new) form, not in a public issue.

## License

The license is the GNU Lesser General Public License (LGPL) version 3.0. The IPMI Library for Java by Verax Systems is published under the GNU GPL v3; this fork uses it under a commercial (non-GPL) license granted by Verax Systems to Sentry Software in 2021. The source files derived from it name Verax Systems and MetricsHub as copyright holders.

Each Java source file under `src/main/java` includes the LGPL-3 header (the build fails otherwise).
To add the header to new source files, simply execute the below command (existing headers are never modified):

```bash
mvn license:update-file-header
```
