# IPMI Java Client
![GitHub release (with filter)](https://img.shields.io/github/v/release/metricshub/ipmi-java)
![Build](https://img.shields.io/github/actions/workflow/status/metricshub/ipmi-java/deploy.yml)
![GitHub top language](https://img.shields.io/github/languages/top/metricshub/ipmi-java)
![License](https://img.shields.io/github/license/metricshub/ipmi-java)

This project is a fork of the excellent [IPMI Library for Java by Verax Systems](https://veraxsystems.com/ipmi-library-for-java/) ([see also](https://en.wikipedia.org/wiki/Verax_IPMI)). It is however not related to [another fork by rbuckland](https://github.com/rbuckland/ipmilib).

See **[Project Documentation](https://metricshub.org/ipmi-java)** and the [Javadoc](https://metricshub.org/ipmi-java/apidocs) for more information on how to use this library in your code.

The IPMI Java Client talks to the Baseboard Management Controller (BMC) of a server over IPMI 2.0 over LAN (RMCP+): it reads the chassis status, the Field Replaceable Units (FRUs), the sensors of the SDR repository and the System Event Log (SEL) as typed Java objects, controls the power and the identify LED, opens Serial over LAN consoles, and sends any IPMI command in the same session. It requires Java 8 or later and has no dependency.

```java
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
        System.out.println(entry.timestamp() + " " + entry.sensorType() + " " + entry.description() + " " + entry.direction());
    }
}
```

A client owns one RMCP+ session, opened by the first operation and closed by `close()`: always use it in a try-with-resources block. The BMC must have IPMI over LAN enabled and an account with the User privilege: see [Preparing the BMC](https://metricshub.org/ipmi-java/preparing-the-bmc.html).

## Command-line client

Every build also produces a self-contained executable jar with a command-line client, `ipmicli`, that exposes the same operations ([download the latest](https://github.com/metricshub/ipmi-java/releases/latest), Java 8 or later, no other file needed):

```text
target/ipmi-java-<version>.jar
target/ipmi-java-<version>-standalone.jar
```

What the BMC offers, before any login:

```bash
java -jar ipmi-java-<version>-standalone.jar -h bmc.example.com probe
```

The sensors, with the password read from a file:

```bash
java -jar ipmi-java-<version>-standalone.jar -h bmc.example.com -u monitor -pf password.txt sensors
```

The FRU inventory as JSON Lines:

```bash
java -jar ipmi-java-<version>-standalone.jar -h bmc.example.com -u monitor -pf password.txt fru --json
```

Subcommands: `probe`, `chassis`, `power status|on|off|cycle|reset|soft|diag`, `identify`, `sensors`, `fru`, `sel` (the System Event Log, `--since <id>` for the new entries) and `sol` (an interactive Serial over LAN console). Diagnostics go to stderr, the exit code says what failed (77 authentication, 69 unreachable, 124 timeout...). See the [Command-Line Client](https://metricshub.org/ipmi-java/cli.html) page for the options, the output formats and the `ipmitool` equivalents.

## Upgrading

Version 2.0.00 replaces the client API of 1.x and removes the SLF4J dependency; see [Upgrading from 1.x](https://metricshub.org/ipmi-java/upgrading.html#upgrading-from-1-x-to-2-0-00) for the migration table.

* `IpmiClientConfiguration` and the static methods of `IpmiClient` are replaced by the builder above: `IpmiClient.builder(host).credentials(user, password).build()` returns a reusable, thread-safe, `AutoCloseable` client that keeps one session open across its operations (`chassisStatus()`, `chassisControl(PowerCommand)`, `chassisIdentify(seconds)`, `sensors()`, `frus()`, `sel()`, `send(command)`, `sol().open()`, `capabilities()`), walks the SDR repository once for `frus()` and `sensors()`, and reopens the session when the BMC loses it. The cipher suite preference, the privilege level, the message timeout, the retries and the keep-alive period are settings of the builder; `PropertiesManager` and the `connection.properties` and `vxipmi.properties` files are removed.
* The results are typed and immutable (`ChassisStatus`, `Sensor` with its converted reading, thresholds, status and states, `Fru` with its decoded areas, `SelEntry` with its sensor, event, direction and timestamp, `SensorList` and `FruList` with the SDR records the walk skipped), and replace the `org.metricshub.ipmi.client.model` classes. The semicolon-separated text output (`IpmiResultConverter`, `DeviceDescription`) is MetricsHub's format and moves to the MetricsHub IPMI extension, where it stays byte for byte the same.
* The checked `ExecutionException`, `TimeoutException` and `InterruptedException` are replaced by the unchecked `IpmiClientException` hierarchy: `IpmiAuthenticationException`, `IpmiConnectionException`, `IpmiTimeoutException`, `IpmiCompletionCodeException`.
* The library no longer logs: the problems that do not fail an operation are reported in its result (`Sensor.problem()`, `Fru.problems()`, `skippedRecords()`, `ReadFruData.decodeFruData(data, problems)`), and `org.slf4j:slf4j-api` is no longer a dependency.
* In the low-level API, the commands no longer take `IpmiVersion`, `CipherSuite` and `AuthenticationType` in their constructors (the session sets them when it sends the command), `IpmiCommandCoder<R>` declares the response type so that `IpmiConnector.sendMessage()` returns it without a cast, `IpmiConnector.setTimeout(int)` and `setRetries(int)` replace the properties files, `CipherSuite.forId(id)` builds a standard suite, and a wrong password fails the handshake with `AuthenticationException`.

## Build instructions

This is a simple Maven project. Build with:

```bash
mvn verify
```

`mvn verify site` also builds the documentation in `target/site` (sources in [src/site](src/site)).

## Code format

The code is formatted with the MetricsHub Eclipse formatter profile ([metricshub-eclipse-formatter.xml](metricshub-eclipse-formatter.xml), shared with the other MetricsHub Java projects), and the build fails on unformatted code. Simply run the below command before committing:

```bash
mvn formatter:format
```

The build also fails on [Checkstyle](checkstyle.xml) violations. A justified violation can be suppressed with `// CHECKSTYLE.OFF: <RuleName>` and `// CHECKSTYLE.ON: <RuleName>` comments.

The build fails on any SpotBugs bug as well. An intentional one is suppressed with `@SuppressFBWarnings` and a `justification`.

To ignore the whole-tree reformat commit in `git blame`, run once:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

## Release instructions

The artifact is published to [Maven Central](https://central.sonatype.com/) through the
[Central Portal](https://central.sonatype.com/publishing) with the `central-publishing-maven-plugin` (server Id `central`,
authenticated with a Portal user token). Manual deployments require these credentials in your Maven `settings.xml`.

It is strongly recommended to only use [GitHub Actions "Release to Maven Central"](actions/workflows/release.yml) to perform a release:

* Manually trigger the "Release to Maven Central" workflow
* Specify the version being released and the next version number (SNAPSHOT)
* Publish the pending deployment on the [Central Portal](https://central.sonatype.com/publishing/deployments)
  (the workflow uploads and validates it, but does not publish it automatically)
* Merge the PR that has been created to prepare the next version

## License

License is GNU General Lesser Public License (LGPL) version 3.0. The IPMI Library for Java by Verax Systems is published under the GNU GPL v3; this fork uses it under a commercial (non-GPL) license granted by Verax Systems to Sentry Software in 2021. The source files derived from it keep Verax Systems as copyright holder.

Each source file includes the LGPL-3 header (build will fail otherwise).
To add the header to new source files, simply execute the below command (existing headers are never modified):

```bash
mvn license:update-file-header
```
