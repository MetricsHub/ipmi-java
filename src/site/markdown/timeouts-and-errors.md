keywords: timeout, per-message timeout, retries, lost udp reply, timeoutexception, executionexception, connectionexception, ipmiexception, completion code, connection.properties
description: The overall and per-message timeouts, what happens when the BMC drops a UDP reply, the retries, and the exceptions thrown by IpmiClient.

# Timeouts and Errors

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

IPMI over LAN runs over **UDP**: a request or a reply can be lost, and nothing but a timeout
tells the client. BMCs do drop replies, especially when several sessions query them at the same
time. Two timeouts apply:

| Timeout | Set with | Default | Scope |
| --- | --- | --- | --- |
| [Overall timeout](#overall-timeout) | `IpmiClientConfiguration.timeout` (seconds) | none, required | One `IpmiClient` call, from the first packet to the closed session |
| [Per-message timeout](#per-message-timeout-and-retries) | `IpmiConnector.setTimeout(handle, ms)`, or the `timeout` of [`connection.properties`](#library-wide-defaults) | 300 000 ms | Each request, including each step of the session handshake |

## Overall timeout

Each `IpmiClient` method runs its whole exchange — open the session, send the commands, close
the session — in a worker thread, and waits for it at most `timeout` seconds. When the deadline
expires, the worker is interrupted and the method throws `java.util.concurrent.TimeoutException`,
with nothing collected: there are no partial results.

> [!WARNING]
> Interrupting the worker does not always stop it
> ([#79](https://github.com/metricshub/ipmi-java/issues/79)): a worker waiting for a reply may
> keep waiting, and the library's receiving and timer threads are not daemon threads. The calling
> thread gets its `TimeoutException` on time, but these threads can keep a short-lived JVM alive:
> end command-line programs with `System.exit(0)`.

## Per-message timeout and retries

Below the overall timeout, each message has its own timeout and is retried:

1. The request is sent, and the client waits for the reply up to the **per-message timeout**.
2. Without a reply, the request is sent again, after a random pause of up to `idleTime`
   (4 000 ms), up to `retries` (3) times.
3. When every try failed, the call fails with a `ConnectionException`: `Command timed out` during
   the session handshake, `Message timed out` in the session.

BMC replies with a *transient* completion code — node busy, out of resources, initialization in
progress, timeout — are retried the same way. Any other error completion code fails at once.

> [!IMPORTANT]
> The per-message timeout is **5 minutes** by default, longer than any reasonable overall
> timeout, and `IpmiClientConfiguration` does not expose it
> ([#77](https://github.com/metricshub/ipmi-java/issues/77),
> [#101](https://github.com/metricshub/ipmi-java/issues/101)). With the defaults, **a single lost
> reply makes the whole call wait for the overall timeout** and throw `TimeoutException`.

To recover from lost replies within the overall timeout, lower the per-message timeout to a few
seconds:

* with the [low-level API](low-level-api.html#timeouts), call `setTimeout(handle, ms)` on the
  connector right after `createConnection()`;
* with `IpmiClient`, change the [library-wide default](#library-wide-defaults) before the first
  call.

Two known defects limit what the retries achieve: a retried in-session message does not wait
for the reply to the resent request
([#78](https://github.com/metricshub/ipmi-java/issues/78)), and each handshake step waits longer
than its timeout because it counts its 1 ms sleeps rather than the elapsed time
([#79](https://github.com/metricshub/ipmi-java/issues/79)). A short per-message timeout still
turns a lost reply into a retry (or a fast failure) instead of a stall.

## Library-wide defaults

The defaults come from two properties files packaged in the jar, read through the
`org.metricshub.ipmi.core.common.PropertiesManager` singleton:

| Property | Default | Meaning | Read |
| --- | --- | --- | --- |
| `timeout` | `300000` | Per-message timeout, in ms | When each connection is created |
| `retries` | `3` | How many times a failed message is sent again | When each `IpmiConnector` is created |
| `idleTime` | `4000` | Upper bound of the random pause before a retry, in ms | When each `IpmiConnector` is created |
| `pingPeriod` | `30000` | Keep-alive period, in ms, of the connectors created with `IpmiConnector(int)` or `IpmiConnector(int, InetAddress)`; not applied to `IpmiClient` (see [Keep-alive](configuration.html#keep-alive)) | When each `IpmiConnector` is created |

Override them at application startup, from a single thread, before the first IPMI call: the
values then apply to every connection created afterwards, in the whole JVM.

```java
import org.metricshub.ipmi.core.common.PropertiesManager;

PropertiesManager properties = PropertiesManager.getInstance();
properties.setProperty("timeout", "5000"); // per-message timeout: 5 s instead of 5 min
properties.setProperty("retries", "3");
```

`PropertiesManager` logs every lookup at the `INFO` level and its lazy initialization is not
synchronized ([#98](https://github.com/metricshub/ipmi-java/issues/98)), hence "from a single
thread, at startup".

## Exceptions

The `IpmiClient` methods declare three checked exceptions:

| Exception | When |
| --- | --- |
| `TimeoutException` | The [overall timeout](#overall-timeout) expired. Also the usual symptom of a wrong host, a closed UDP port, IPMI over LAN disabled, or a lost reply with the default per-message timeout. |
| `ExecutionException` | The exchange failed. `getCause()` holds the actual exception (see below). |
| `InterruptedException` | The calling thread was interrupted while waiting. |

Common causes wrapped in the `ExecutionException`:

| Cause | Meaning |
| --- | --- |
| `ConnectionException: Illegal connection state: Rakp1Waiting` | The RAKP handshake failed: wrong user name or password, account not allowed over LAN or at the User level. The `ERROR` log shows the actual reason (`Authentication check failed`, ...), see [#109](https://github.com/metricshub/ipmi-java/issues/109). |
| `ConnectionException: Command timed out` / `Message timed out` | No reply after all the tries of a message (with a [shortened](#per-message-timeout-and-retries) per-message timeout). |
| `IPMIException` | The BMC answered with an error completion code. `getCompletionCode()` returns it, for example `InsufficientPrivilege` (`0xD4`). |
| `IllegalArgumentException: ... is not yet implemented.` | The chosen cipher suite uses an algorithm the client does not implement (xRC4, MD5-128). See [cipher suites](preparing-the-bmc.html#cipher-suites). |
| `Exception: Cannot get the available cipher suites.` | The BMC returned an empty cipher suite list. |
| `UnknownHostException` | The host name cannot be resolved. |

[Troubleshooting](troubleshooting.html) maps these symptoms to their usual fixes.

## Errors that do not fail the call

Some problems are logged at the `WARN` level and the call goes on with what it could collect:

* an **SDR record** that cannot be decoded — a reserved record type, a record shorter than its
  header — is skipped and the repository walk continues with the next record (a Get SDR reply
  without any record byte fails the call instead, see
  [Supported Commands](supported-commands.html#oem-and-unknown-records));
* a **FRU** that cannot be read (for example a FRU device that is not present) is reported
  truncated or not at all — except when Get FRU Inventory Area Info fails for FRU 0, or gets no
  reply for any FRU ([FRU Inventory](fru-inventory.html#how-the-frus-are-read));
* a **sensor** whose reading is not available (completion code `DataNotPresent`) is returned
  without reading data.

Unknown values in a record (an entity ID, a sensor type or a unit the library does not know) are
logged at the `ERROR` level as `Invalid value: ...` and replaced with a default (`Other` for an
entity ID); the record is still decoded.
