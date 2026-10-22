keywords: install, maven, gradle, dependency, jdk, java 8, no dependency, standalone jar, command line, ipmicli, installer, curl, wget, powershell, checksum, uninstall, build from source
description: Add the IPMI Java Client to your build, the supported JDKs and requirements, the one-command installation and removal of the ipmicli command-line client, and building from source; the library has no dependency and does not log.

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
| Java | **Java 8** or later. The library is compiled for Java 8 and built and tested on JDK 17; [building it](#building-from-source) needs JDK 17 or later. |
| Cryptography | The standard JCA and JCE algorithms only: `HmacSHA1`, `HmacSHA256`, `HmacMD5`, `MD5` (the MD5-128 integrity of cipher suites 11 and 12), `AES/CBC/NoPadding` and the default `SecureRandom`. No extra security provider is needed. |
| Network | UDP from the machine running the client to port **623** of the BMC (or the port set with [`port()`](configuration.html#host-and-port)). Each client also binds an ephemeral local UDP port while it is open. |
| Java modules | The jar is an automatic module named `org.metricshub.ipmi` (it has no `module-info`): a modular application writes `requires org.metricshub.ipmi;`. |
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
>   ```bash
>   curl -fsSL https://metricshub.org/ipmi-java/get | sh
>   ```
> * Windows (PowerShell)
>   ```powershell
>   irm https://metricshub.org/ipmi-java/get.ps1 | iex
>   ```

The installer downloads the standalone jar of the latest release from GitHub, verifies its
SHA-256 checksum, and installs an `ipmicli` launcher, with no `sudo` or administrator rights:

| File | Linux / macOS | Windows |
| --- | --- | --- |
| Jar | `${XDG_DATA_HOME:-~/.local/share}/ipmicli/ipmi-java-standalone.jar` | `%LOCALAPPDATA%\ipmicli\ipmi-java-standalone.jar` |
| Launcher | `~/.local/bin/ipmicli` (the installer prints the line to add to your profile when the directory is not on the `PATH`) | `%LOCALAPPDATA%\ipmicli\bin\ipmicli.cmd` (the installer adds the directory to the user `PATH`: open a new terminal) |

The Linux and macOS installer needs `curl` or `wget` (without curl, run
`wget -qO- https://metricshub.org/ipmi-java/get | sh`), and `sha256sum` or `shasum`. The Windows
installer runs in Windows PowerShell 5.1 and PowerShell 7. Neither installer needs Java.

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
| `IPMICLI_INSTALL_DIR` | Linux and macOS: the directory of the launcher (default `~/.local/bin`); the jar does not move. Windows: the installation directory of the jar (default `%LOCALAPPDATA%\ipmicli`), with the launcher in its `bin` subdirectory. |
| `XDG_DATA_HOME` | Linux and macOS: the jar goes to `$XDG_DATA_HOME/ipmicli` (default `~/.local/share/ipmicli`). |

> [!TABS]
> * Linux / macOS
>   ```bash
>   curl -fsSL https://metricshub.org/ipmi-java/get | IPMICLI_VERSION=${project.version} sh
>   ```
> * Windows (PowerShell)
>   ```powershell
>   $env:IPMICLI_VERSION = '${project.version}'; irm https://metricshub.org/ipmi-java/get.ps1 | iex
>   ```

In PowerShell, the variable stays set until the window is closed, and a later installation from
that window installs the same release: `Remove-Item Env:IPMICLI_VERSION` clears it.

> [!TIP]
> Prefer to inspect the script before running it? Download it first:
>
> ```bash
> curl -fsSLO https://metricshub.org/ipmi-java/get
> less get
> sh get
> ```
>
> In PowerShell, run the downloaded script with `iex`, as the one-command installation does
> (`.\get.ps1` is blocked by the default execution policy of Windows client editions):
>
> ```powershell
> irm https://metricshub.org/ipmi-java/get.ps1 -OutFile get.ps1
> notepad get.ps1
> iex (Get-Content -Raw get.ps1)
> ```

### Uninstalling

The installers have no uninstall mode: delete the files they wrote.

> [!TABS]
> * Linux / macOS
>   ```bash
>   rm ~/.local/bin/ipmicli
>   rm -r "${XDG_DATA_HOME:-$HOME/.local/share}/ipmicli"
>   ```
> * Windows (PowerShell)
>   ```powershell
>   Remove-Item -Recurse "$env:LOCALAPPDATA\ipmicli"
>   ```

When `IPMICLI_INSTALL_DIR` was set, delete the launcher from that directory (Linux and macOS) or
that directory (Windows) instead. On Windows, also remove the `bin` subdirectory
(`%LOCALAPPDATA%\ipmicli\bin` by default) from the user `Path`: search for *Edit environment
variables for your account* in the Start menu.

### Manual download

Every release also publishes `${project.artifactId}-${project.version}-standalone.jar` on the
[releases page](https://github.com/metricshub/ipmi-java/releases/latest), with its `.sha256`
checksum: the library with the command-line client as its entry point, which runs on Java 8 or
later with no other file.

To check the download, run
`sha256sum -c ${project.artifactId}-${project.version}-standalone.jar.sha256` (`shasum -a 256 -c`
on macOS) in the directory that holds both files. On Windows, compare the hash that
`Get-FileHash ${project.artifactId}-${project.version}-standalone.jar` prints with the first field
of the `.sha256` file (the case differs). Each release also publishes the same jar as
`ipmi-java-standalone.jar`, with its own `.sha256`:
`https://github.com/metricshub/ipmi-java/releases/latest/download/ipmi-java-standalone.jar`
always downloads the latest release.

```bash
java -jar ${project.artifactId}-${project.version}-standalone.jar -h bmc.example.com -u monitor -pf pw.txt chassis
```

The same jar is published on Maven Central with the `standalone` classifier; a build uses the
regular artifact above, which has the same classes without the entry point.

## Building from source

Building needs a JDK 17 or later and Maven 3.8.9 or later (the jars still target Java 8):

```bash
git clone https://github.com/metricshub/ipmi-java.git
cd ipmi-java
mvn verify
```

`mvn verify site` also generates this documentation and the reports (Javadoc, tests, Checkstyle,
PMD, SpotBugs) in `target/site`. `mvn package` produces both jars in `target`: the library jar and
the `standalone` one.
