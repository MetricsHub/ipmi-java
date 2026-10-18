keywords: verax, vxipmi, ipmi library for java, migration, package rename, com.veraxsystems.vxipmi
description: Move from the IPMI Library for Java by Verax Systems (com.veraxsystems.vxipmi) to the IPMI Java Client — package rename, Maven dependency, logging, and what changed in the fork.

# Migrating from Verax

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The IPMI Java Client is a fork of the
[IPMI Library for Java by Verax Systems](https://en.wikipedia.org/wiki/Verax_IPMI) (*vxipmi*).
Its protocol layer, `org.metricshub.ipmi.core`, **is** the Verax library, under a new package
name and with the changes listed below; the high-level `org.metricshub.ipmi.client` API is new.
Code written for the Verax library keeps working after a package rename.

## Steps

1. Replace the Verax jar with the Maven dependency:

   ```xml
   <dependency>
     <groupId>${project.groupId}</groupId>
     <artifactId>${project.artifactId}</artifactId>
     <version>${project.version}</version>
   </dependency>
   ```

2. Rename the packages in the imports: `com.veraxsystems.vxipmi` becomes
   `org.metricshub.ipmi.core`, with the same sub-packages:

   | Verax | IPMI Java Client |
   | --- | --- |
   | `com.veraxsystems.vxipmi.api.sync.IpmiConnector` | `org.metricshub.ipmi.core.api.sync.IpmiConnector` |
   | `com.veraxsystems.vxipmi.api.async.*` | `org.metricshub.ipmi.core.api.async.*` |
   | `com.veraxsystems.vxipmi.api.sol.*` | `org.metricshub.ipmi.core.api.sol.*` |
   | `com.veraxsystems.vxipmi.coding.*` | `org.metricshub.ipmi.core.coding.*` |
   | `com.veraxsystems.vxipmi.common.*`, `connection.*`, `sm.*`, `transport.*` | `org.metricshub.ipmi.core.common.*`, ... |

   A search and replace of `com.veraxsystems.vxipmi.` with `org.metricshub.ipmi.core.` does it.

3. Remove the logging configuration of the Verax library: this one does not log. Every failure
   is an exception, and every other problem is part of the result
   ([Timeouts and Errors](timeouts-and-errors.html)).

4. If you edited the `connection.properties` or `vxipmi.properties` files of the Verax jar to
   change the timeouts, set the same values on the connector instead: `setTimeout(ms)`,
   `setRetries(n)` and the `pingPeriod` constructor argument
   ([Connections and connectors](low-level-api.html#connections-and-connectors)).

5. Drop the `IpmiVersion`, `CipherSuite` and `AuthenticationType` arguments of the command
   constructors: the session sets them when it sends the command, and `sendMessage()` returns
   the response type the command declares ([Sending commands](low-level-api.html#sending-commands)).

6. If you subclassed protocol classes and accessed their `protected` fields, use the accessors
   that replaced them ([Upgrading from 1.2.02](upgrading.html#protected-fields)).

## What changed in the fork

| Area | Change |
| --- | --- |
| Cipher suites | RAKP-HMAC-SHA256 and RAKP-HMAC-MD5 authentication, HMAC-SHA256-128, HMAC-MD5-128 and MD5-128 integrity: suites 6 to 8, 11, 12 and 15 to 17 work, in addition to 0 to 3. |
| Keep-alive | `IpmiConnector(int port, long pingPeriod)` sets the keep-alive period of the connector, or disables it (`0`). |
| SDR repository | Every OEM record type (`C0h` – `FFh`, not only `C0h`) is decoded as `OemRecord`, and a record that cannot be decoded is skipped instead of aborting the walk. Truncated Get SDR replies are read again in chunks. |
| Sensor records | Full, Compact and Event-Only records share `AbstractSensorRecord` ([Upgrading](upgrading.html#sensor-records)). |
| Commands | `IpmiCommandCoder<R>` declares the response type of each command, the session sets the version, cipher suite and authentication type, and `validateResponse()` checks the response of every command ([Upgrading](upgrading.html#command-responses)). |
| Connection thread | The busy loop of `Connection.run()` that used a full CPU core is fixed. |
| High-level API | `IpmiClient` keeps one session open and reads the chassis status, the FRUs and the sensors as typed results ([Overview](index.html)). |
| Logging | None: every failure is an exception, every other problem is in the result. |
| Java | Java 8 or later. |

## License

The Verax library is published under the GNU GPL v3. This fork is published under the
**GNU LGPL v3**: it uses the Verax code under a commercial (non-GPL) license granted by Verax
Systems to Sentry Software. The source files derived from the Verax library keep Verax Systems
as copyright holder.
