keywords: install, maven, gradle, dependency, jdk, java 8, slf4j, logging
description: Add the IPMI Java Client to your build, the supported JDKs, its single dependency (the SLF4J API), and how to configure its logging.

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

Versions up to 1.2.00 were published as `org.sentrysoftware:ipmi`, with the
`org.sentrysoftware.ipmi` packages; see [Upgrading](upgrading.html#upgrading-from-1-2-00-and-earlier).

## Requirements

| Requirement | Detail |
| --- | --- |
| Java | **Java 8** or later. The library is compiled for Java 8 and built and tested on JDK 17. |
| Cryptography | The standard JCE algorithms only: `HmacSHA1`, `HmacSHA256`, `HmacMD5`, `AES/CBC/NoPadding`. No extra security provider is needed. |
| Network | UDP from the machine running the client to port **623** of the BMC (or the port set with [`setPort()`](configuration.html#host-and-port)). Each session also binds an ephemeral local UDP port. |
| A BMC | With IPMI 2.0 over LAN enabled and an account allowed to log in: see [Preparing the BMC](preparing-the-bmc.html). IPMI 1.5-only BMCs are not supported. |

## Dependencies

The only runtime dependency is the **SLF4J 2 API** (`org.slf4j:slf4j-api`), through which the
library logs. Nothing else is pulled in: the RMCP+ protocol, the RAKP handshake and the
encryption are implemented with the JDK alone.

## Logging

The library logs through [SLF4J](https://www.slf4j.org/) under the `org.metricshub.ipmi` logger
hierarchy. Add the SLF4J provider of your logging framework (`logback-classic`, `slf4j-reload4j`,
`log4j-slf4j2-impl`, `slf4j-simple`, ...) to see the messages; without a provider, SLF4J prints a
one-time warning and discards them.

What is logged, and at which level:

| Level | Messages |
| --- | --- |
| `ERROR` | A value the decoders do not know (`Invalid value: ...` for an entity ID, sensor type or unit), a failed session handshake, exceptions in the receiving and keep-alive threads. |
| `WARN` | An SDR record that cannot be decoded and is skipped, a FRU that cannot be read or decoded (the FRU is then truncated or missing), a message that failed and is resent, a packet whose integrity check failed, a response listener that threw while a timeout was reported. |
| `DEBUG` | Each message sent, with its tag and attempt number; each message that timed out; a session that could not be closed cleanly; every lookup of a [`connection.properties`](timeouts-and-errors.html#library-wide-defaults) value. |

> [!TIP]
> Set the `org.metricshub.ipmi` logger to `WARN` in production, and to `DEBUG` when diagnosing a
> BMC ([Troubleshooting](troubleshooting.html)).

With `slf4j-simple`, for example:

```bash
java -Dorg.slf4j.simpleLogger.log.org.metricshub.ipmi=warn -cp ... MyApp
```

## Building from source

```bash
git clone https://github.com/metricshub/ipmi-java.git
cd ipmi-java
mvn verify
```

`mvn verify site` also generates this documentation and the reports (Javadoc, tests, Checkstyle,
PMD, SpotBugs) in `target/site`.
