keywords: verax, vxipmi, ipmi library for java, migration, package rename, com.veraxsystems.vxipmi, log4j, propertiesmanager, connection.properties, vxipmi.properties
description: Move from the IPMI Library for Java by Verax Systems (com.veraxsystems.vxipmi) to the IPMI Java Client — package rename, Maven dependency, logging, connector settings, behavior changes, and what changed in the fork.

# Migrating from Verax

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The IPMI Java Client is a fork of the
[IPMI Library for Java by Verax Systems](https://web.archive.org/web/20250104201815/https://veraxsystems.com/ipmi-library-for-java/)
(*vxipmi*). Its protocol layer, `org.metricshub.ipmi.core`, **is** the Verax library, under a new
package name and with the changes listed below; the high-level `org.metricshub.ipmi.client` API
is new. Code written for the Verax library needs the package rename and the changes of the steps
below. The compiler reports the removed `PropertiesManager`, the command constructors and the
protected fields (steps 4 to 6); the logging configuration, the properties files and the behavior
changes (steps 3, 4 and 7) do not show at compile time.

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
   | `com.veraxsystems.vxipmi.common.*`, `connection.*`, `sm.*`, `transport.*` | `org.metricshub.ipmi.core.common.*`, `connection.*`, `sm.*`, `transport.*` |

   A search and replace of `com.veraxsystems.vxipmi.` with `org.metricshub.ipmi.core.` does it.

3. Remove the log4j configuration of the Verax library (the `com.veraxsystems.vxipmi` loggers)
   and, unless your own code uses it, the log4j 1.x dependency (`log4j:log4j`): this library
   does not log. Every failure is an exception, and every other problem is part of the result
   ([Timeouts and Errors](timeouts-and-errors.html)).

4. If you changed the settings of the Verax library, in its `connection.properties` or
   `vxipmi.properties` file or with `PropertiesManager.getInstance().setProperty()`, set them on
   the connector instead: `PropertiesManager` and the two files are gone
   ([Connections and connectors](low-level-api.html#connections-and-connectors)).

   | Verax property | Connector setting |
   | --- | --- |
   | `timeout` | `setTimeout(ms)`, or `setTimeout(handle, ms)` for a connection already created (5 000 ms by default, where the Verax file set 10 000 ms) |
   | `retries` | `setRetries(n)` (3 by default) |
   | `pingPeriod` | The `pingPeriod` argument of `new IpmiConnector(port, pingPeriod)` (30 000 ms by default); `0` disables the keep-alive |
   | `idleTime` | None: the pause before resending a request the BMC answered as busy is random, up to 4 000 ms |
   | `cleaningFrequency` | None: the timeouts are checked every 500 ms |

5. Drop the `IpmiVersion`, `CipherSuite` and `AuthenticationType` arguments of the command
   constructors: the session sets them when it sends the command, and `sendMessage()` returns
   the response type the command declares ([Sending commands](low-level-api.html#sending-commands)).

6. If you subclassed protocol classes and accessed their `protected` fields, use the accessors
   that replaced them ([Upgrading from 1.2.02](upgrading.html#protected-fields)).

7. Check the behavior changes of the protocol core, which [Upgrading](upgrading.html) lists for
   the users of earlier releases:
   [Upgrading from 2.0.00 to 2.1.00](upgrading.html#upgrading-from-2-0-00-to-2-1-00),
   [The low-level API](upgrading.html#the-low-level-api) of 2.0.00 and
   [Upgrading from 1.2.02](upgrading.html#upgrading-from-1-2-02). The ones Verax code meets first:

   * `openSession()` raises the session to the requested Operator or Administrator level with Set
     Session Privilege Level, and fails with `SessionException` (after closing the session) when the
     BMC refuses the level, where with Verax the session stayed at User on the BMCs that follow the
     specification. The `SerialOverLan` constructors that open their own session request
     Administrator.
   * `getAvailableCipherSuites()` fails at once with `Ipmi20UnsupportedException` for an IPMI
     1.5-only BMC, where Verax timed out.
   * An OEM or command-specific completion code the library does not list is
     `CompletionCode.Unknown`, with the byte in `IPMIException.getRawCode()`, where Verax threw
     `IllegalArgumentException: Invalid value`.
   * A wrong password or BMC key throws `AuthenticationException`, a subclass of the
     `IllegalArgumentException` Verax threw, with the same messages.

## What changed in the fork

| Area | Change |
| --- | --- |
| Cipher suites | RAKP-HMAC-SHA256 and RAKP-HMAC-MD5 authentication, HMAC-SHA256-128, HMAC-MD5-128 and MD5-128 integrity: suites 6 to 8, 11, 12 and 15 to 17 work, in addition to 0 to 3. `IpmiClient` refuses the suites without integrity (0, 1, 6 and 15) ([How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)). |
| Keep-alive | `IpmiConnector(int port, long pingPeriod)` sets the keep-alive period of the connector (30 000 ms by default), or disables it (`0`). The keep-alive is a Get Device ID, as `ipmitool` sends, where Verax sent Get Channel Authentication Capabilities. |
| New commands | Get Device ID, Chassis Identify, Send Message (bridging to other controllers), Get Device SDR, Get Device SDR Info and Reserve Device SDR Repository, Get Sensor Reading Factors, Get Session Info, Get Payload Instance Info, Get SEL Time UTC Offset; `PowerCommand` has `PowerCycle`, `DiagnosticInterrupt` and `SoftShutdown`. |
| FRU decoding | The internal use area (`InternalUseInfo`), the multirecords of a type the library does not model (kept raw as `RawMultiRecordInfo`), the SPD data of DDR3, DDR4 and DDR5 modules (`SpdInfo`, a stub in Verax) and the Intel processor information ROM (`PiromInfo`); `ReadFruData.decodeFruData(data, problems)` reports what it skipped. |
| SDR repository | Every OEM record type (`C0h`-`FFh`, not only `C0h`) is decoded as `OemRecord`. `IpmiClient` skips a record it cannot decode instead of aborting the walk, and reads in chunks a record the BMC cannot return whole; with the low-level API, `SensorRecord.populateSensorRecord()` still throws `IllegalArgumentException` for a reserved or deprecated record type. |
| Sensor records | Full, Compact and Event-Only records share `AbstractSensorRecord` ([Upgrading](upgrading.html#sensor-records)). |
| Commands | `IpmiCommandCoder<R>` declares the response type of each command, the session sets the version, cipher suite and authentication type, and `validateResponse()` checks the response of every command ([Upgrading](upgrading.html#command-responses)). |
| Connection thread | The busy loop of `Connection.run()` that used a full CPU core is fixed. |
| High-level API | `IpmiClient` keeps one session open and reads the BMC capabilities, the device ID, the chassis status, the FRUs, the sensors and the System Event Log as typed results. It also controls the chassis power and the identify LED, opens Serial over LAN consoles, and sends raw requests or any command of the protocol core (`send()`) through its session ([Overview](index.html)). `ipmicli` offers the same operations from the command line, with `raw` for any other command ([Command-Line Client](cli.html)). |
| Logging | None: every failure is an exception, every other problem is in the result. |
| Java | Java 8 or later. |

## License

The Verax library is published under the GNU GPL v3. This fork is published under the
**GNU LGPL v3**: it uses the Verax code under a commercial (non-GPL) license granted by Verax
Systems to Sentry Software. The source files derived from the Verax library name Verax Systems
and MetricsHub as copyright holders.
