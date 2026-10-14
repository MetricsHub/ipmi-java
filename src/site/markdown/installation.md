keywords: install, maven, gradle, dependency, jdk, java 8, no dependency, standalone jar, command line, ipmicli, installer, curl, powershell
description: Add the IPMI Java Client to your build, the supported JDKs and requirements, and the one-command installation of the ipmicli command-line client; the library has no dependency and does not log.

# Installation

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## Add it to your build

The library is published on
[Maven Central](https://central.sonatype.com/artifact/${project.groupId}/${project.artifactId}):

> [!TABS]
> * Maven
>   ```xml
>   <dependency>
>     <groupId>${project.groupId}</groupId>
>     <artifactId>${project.artifactId}</artifactId>
>     <version>${project.version}</version>
>   </dependency>
>   ```
> * Gradle (Groovy)
>   ```groovy
>   implementation '${project.groupId}:${project.artifactId}:${project.version}'
>   ```
> * Gradle (Kotlin)
>   ```kotlin
>   implementation("${project.groupId}:${project.artifactId}:${project.version}")
>   ```

Version 2.0.00 replaces the API of the 1.x versions: see
[Upgrading from 1.x](upgrading.html#upgrading-from-1-x-to-2-0-00). Versions up to 1.2.00 were
published as `org.sentrysoftware:ipmi`, with the `org.sentrysoftware.ipmi` packages; see
[Upgrading](upgrading.html#upgrading-from-1-2-00-and-earlier).

## Requirements

| Requirement | Detail |
| --- | --- |
| Java | **Java 8** or later. The library is compiled for Java 8 and built and tested on JDK 17. |
| Cryptography | The standard JCE algorithms only: `HmacSHA1`, `HmacSHA256`, `HmacMD5`, `AES/CBC/NoPadding`. No extra security provider is needed. |
| Network | UDP from the machine running the client to port **623** of the BMC (or the port set with [`port()`](configuration.html#host-and-port)). Each client also binds an ephemeral local UDP port while it is open. |
| A BMC | With IPMI 2.0 over LAN enabled and an account allowed to log in: see [Preparing the BMC](preparing-the-bmc.html). IPMI 1.5-only BMCs are not supported. |

## Dependencies

The library has **no runtime dependency**: the RMCP+ protocol, the RAKP handshake and the
encryption are implemented with the JDK alone.

It does not log either. Every failure reaches the caller as an exception, and every problem that
does not fail an operation is part of its result ([Timeouts and Errors](timeouts-and-errors.html)):
log what you need from there, with the logging framework of your application.

## Command-line client

Install the **`ipmicli`** command ([Command-Line Client](cli.html)) with one command:

> [!TABS]
> * Linux / macOS
>   ```shell-session
>   $ curl -fsSL https://metricshub.org/ipmi-java/get | sh
>   ```
> * Windows (PowerShell)
>   ```powershell
>   irm https://metricshub.org/ipmi-java/get.ps1 | iex
>   ```

The installer downloads the standalone jar of the latest release from GitHub, verifies its
SHA-256 checksum, and installs an `ipmicli` launcher, with no `sudo` or administrator rights:

| | Linux / macOS | Windows |
| --- | --- | --- |
| Jar | `${XDG_DATA_HOME:-~/.local/share}/ipmicli/ipmi-java-standalone.jar` | `%LOCALAPPDATA%\ipmicli\ipmi-java-standalone.jar` |
| Launcher | `~/.local/bin/ipmicli` (the installer prints the line to add to your profile when the directory is not on the `PATH`) | `%LOCALAPPDATA%\ipmicli\bin\ipmicli.cmd` (the installer adds the directory to the user `PATH`: open a new terminal) |

The launcher finds a Java runtime, 8 or later, each time it runs: `IPMICLI_JAVA_HOME`, then
`JAVA_HOME`, then `java` on the `PATH` (on Linux and macOS, then `/usr/libexec/java_home`, sdkman
and `/usr/lib/jvm`). Installing or upgrading Java needs no reinstallation. Without a suitable
runtime, it exits with code 127 and points to [Adoptium](https://adoptium.net).

```shell-session
$ ipmicli --version
ipmi-java ${project.version}
```

Running the installer again upgrades in place. A checksum mismatch aborts the installation
before anything is replaced. These environment variables change what it does:

| Variable | Effect |
| --- | --- |
| `IPMICLI_VERSION` | Install this release (`2.0.00` or later) instead of the latest. |
| `IPMICLI_INSTALL_DIR` | Linux and macOS: the directory of the launcher (default `~/.local/bin`). Windows: the installation directory (default `%LOCALAPPDATA%\ipmicli`). |

```shell-session
$ curl -fsSL https://metricshub.org/ipmi-java/get | IPMICLI_VERSION=${project.version} sh
```

> [!TIP]
> Prefer to inspect the script before running it? Download it first:
>
> ```shell-session
> $ curl -fsSLO https://metricshub.org/ipmi-java/get
> $ less get
> $ sh get
> ```

### Manual download

Every release also publishes `${project.artifactId}-${project.version}-standalone.jar` on the
[releases page](https://github.com/metricshub/ipmi-java/releases/latest), with its `.sha256`
checksum: the library with the command-line client as its entry point, which runs on Java 8 or
later with no other file.

```bash
java -jar ${project.artifactId}-${project.version}-standalone.jar -h bmc.example.com -u monitor -pf pw.txt chassis
```

The same jar is published on Maven Central with the `standalone` classifier; a build uses the
regular artifact above, which has the same classes without the entry point.

## Building from source

```bash
git clone https://github.com/metricshub/ipmi-java.git
cd ipmi-java
mvn verify
```

`mvn verify site` also generates this documentation and the reports (Javadoc, tests, Checkstyle,
PMD, SpotBugs) in `target/site`. `mvn package` produces both jars in `target`: the library jar and
the `standalone` one.
