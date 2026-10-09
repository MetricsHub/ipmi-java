# IPMI Java Client
![GitHub release (with filter)](https://img.shields.io/github/v/release/metricshub/ipmi-java)
![Build](https://img.shields.io/github/actions/workflow/status/metricshub/ipmi-java/deploy.yml)
![GitHub top language](https://img.shields.io/github/languages/top/metricshub/ipmi-java)
![License](https://img.shields.io/github/license/metricshub/ipmi-java)

This project is a fork of the excellent [IPMI Library for Java by Verax Systems](https://veraxsystems.com/ipmi-library-for-java/) ([see also](https://en.wikipedia.org/wiki/Verax_IPMI)). It is however not related to [another fork by rbuckland](https://github.com/rbuckland/ipmilib).

See **[Project Documentation](https://metricshub.org/ipmi-java)** and the [Javadoc](https://metricshub.org/ipmi-java/apidocs) for more information on how to use this library in your code.

The IPMI Java Client talks to the Baseboard Management Controller (BMC) of a server over IPMI 2.0 over LAN (RMCP+): it reads the chassis status, the Field Replaceable Units (FRUs) and the sensors of the SDR repository, as Java objects or as the text output that MetricsHub parses, and its low-level API sends any IPMI command (System Event Log, chassis control, Serial over LAN). It requires Java 8 or later.

```java
IpmiClientConfiguration config = new IpmiClientConfiguration("bmc.example.com", "monitor", password, null, false, 120);
System.out.println(IpmiClient.getChassisStatusAsStringResult(config));
System.out.println(IpmiClient.getFrusAndSensorsAsStringResult(config));
```

The BMC must have IPMI over LAN enabled and an account with the User privilege: see [Preparing the BMC](https://metricshub.org/ipmi-java/preparing-the-bmc.html).

## Upgrading

Version 1.2.03 makes the `protected` fields of the protocol classes (`AbstractIpmiRunner`, `MessageHandler`, `IpmiLanMessage`, `ConfidentialityAlgorithm`, `IntegrityAlgorithm`) `private`. Subclasses must use the new `protected` accessors instead; see [Upgrading from 1.2.02](https://metricshub.org/ipmi-java/upgrading.html#upgrading-from-1-2-02) for the list. The `IpmiClient` API is unchanged. The Full, Compact and Event-Only sensor records now share the `AbstractSensorRecord` superclass, and commands can check responses with `IpmiCommandCoder.validateResponse()`; both are described on the same page. The user name and password are now encoded in UTF-8 whatever the platform charset, and the BMC key is used as raw bytes; as a result, `AuthenticationAlgorithm.getKeyExchangeAuthenticationCode()` and `checkKeyExchangeAuthenticationCode()` take the key and password as `byte[]` instead of `String`. `UdpMessenger.getSentPackets()` is removed, and the `CONST1`/`CONST2` constants of `IntegrityAlgorithm` and `ConfidentialityAesCbc128` are now `private`. `IpmiConnector.closeConnection()` releases the connection, whose handle then throws `IllegalStateException`; the keep-alive is actually sent with the default configuration, as a one-way Get Device ID whose reply and timeout are not reported to the listeners; a one-way IPMI message keeps its tag reserved until its reply or its timeout; and `Constants.TIMEOUT`, which nothing reads, is deprecated.

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
