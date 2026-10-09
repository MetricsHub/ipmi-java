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
| [Per-message timeout](#per-message-timeout-and-retries) | `IpmiConnector.setTimeout(handle, ms)`, or the `timeout` of [`connection.properties`](#library-wide-defaults) | 5 000 ms, capped by the overall timeout | Each request, including each step of the session handshake |

## Overall timeout

Each `IpmiClient` method runs its whole exchange — open the session, send the commands, close
the session — in a worker thread, and waits for it at most `timeout` seconds. When the deadline
expires, the worker is interrupted and the method throws `java.util.concurrent.TimeoutException`,
with nothing collected: there are no partial results.

The interrupted worker stops at its current wait, closes the session and releases the UDP port,
and the method waits up to one second for that cleanup before throwing. A worker stuck in a call
that cannot be interrupted (name resolution, for example) closes the connection when that call
returns; a `WARN` says so. The library's receiving and timer threads are daemon threads: they
never keep the JVM alive.

## Per-message timeout and retries

Below the overall timeout, each message has its own timeout and is retried:

1. The request is sent, and the client waits for the reply up to the **per-message timeout**.
2. Without a reply, the request is sent again at once (the BMC already had the whole timeout), up
   to `retries` (3) times.
3. When every try failed, the call fails with a `ConnectionException`: `Command timed out` during
   the session handshake, `Message timed out` in the session.

BMC replies with a *transient* completion code are retried too. In the session, the request is
sent again after a random pause of up to `idleTime` (4 000 ms) when the BMC says it is busy (node
busy, out of resources, initialization in progress), and at once after a timeout on the BMC side
(`C3h`), where the BMC already waited for the device it could not reach; the steps of the session
handshake are sent again at once. Any other error completion code fails at once.

The per-message timeout is **5 s** by default, and `IpmiClient` caps it by the overall timeout.
With the defaults, a lost reply costs the per-message timeout, then the request is sent again; a
BMC that never answers fails after 4 tries, about 20 s into the call.

`IpmiClientConfiguration` does not expose the per-message timeout
([#101](https://github.com/metricshub/ipmi-java/issues/101)). To change it:

* with the [low-level API](low-level-api.html#timeouts), call `setTimeout(handle, ms)` on the
  connector right after `createConnection()`;
* with `IpmiClient`, change the [library-wide default](#library-wide-defaults) before the first
  call.

## Library-wide defaults

The defaults come from two properties files packaged in the jar, read through the
`org.metricshub.ipmi.core.common.PropertiesManager` singleton:

| Property | Default | Meaning | Read |
| --- | --- | --- | --- |
| `timeout` | `5000` | Per-message timeout, in ms | When each connection is created |
| `retries` | `3` | How many times a failed message is sent again | When each `IpmiConnector` is created |
| `idleTime` | `4000` | Upper bound of the random pause before resending a request the BMC answered as busy, in ms | When each `IpmiConnector` is created |
| `pingPeriod` | `30000` | Keep-alive period, in ms, when the configuration's `pingPeriod` is `-1` | When each `IpmiConnector` is created |

Override them at application startup, from a single thread, before the first IPMI call: the
values then apply to every connection created afterwards, in the whole JVM.

```java
import org.metricshub.ipmi.core.common.PropertiesManager;

PropertiesManager properties = PropertiesManager.getInstance();
properties.setProperty("timeout", "2000"); // per-message timeout: 2 s instead of 5 s
properties.setProperty("retries", "3");
```

`PropertiesManager` logs every lookup at the `DEBUG` level. Its initialization is thread-safe and
`setProperty()` may be called at any time, but a value changed while connections are being created
applies to some of them and not to others, hence "from a single thread, at startup".

## Exceptions

The `IpmiClient` methods declare three checked exceptions:

| Exception | When |
| --- | --- |
| `TimeoutException` | The [overall timeout](#overall-timeout) expired: a large SDR repository or many FRUs on a slow BMC, or an overall timeout shorter than the handshake tries (about 20 s). |
| `ExecutionException` | The exchange failed. `getCause()` holds the actual exception (see below). |
| `InterruptedException` | The calling thread was interrupted while waiting. |

Common causes wrapped in the `ExecutionException`:

| Cause | Meaning |
| --- | --- |
| `IllegalArgumentException: Authentication check failed` | The RAKP handshake failed: the BMC's proof does not match the password or the [BMC key](configuration.html#bmc-key). The credentials are sent once. |
| `IPMIException: Unauthorized name.`, `Invalid role.`, ... | The BMC refused the session: unknown user, user not allowed over the LAN channel or at the User level, cipher suite refused ([Troubleshooting](troubleshooting.html#the-login-fails)). |
| `ConnectionException: Command timed out` / `Message timed out` | No reply after all the [tries](#per-message-timeout-and-retries) of a message: `Command timed out` during the session handshake, the usual symptom of a wrong host, a closed UDP port or IPMI over LAN disabled; `Message timed out` in the session. |
| `IPMIException` | The BMC answered with an error completion code. `getCompletionCode()` returns it, for example `InsufficentPrivilege` (sic, `0xD4`). |
| `IllegalArgumentException: ... is not yet implemented.` | The chosen cipher suite uses an algorithm the client does not implement (xRC4, MD5-128). See [cipher suites](preparing-the-bmc.html#cipher-suites). |
| `Exception: Cannot get the available cipher suites.` | The BMC returned an empty cipher suite list. |
| `UnknownHostException` | The host name cannot be resolved. |

[Troubleshooting](troubleshooting.html) maps these symptoms to their usual fixes.

## Errors that do not fail the call

Some problems are logged at the `WARN` level and the call goes on with what it could collect:

* an **SDR record** that cannot be decoded — a reserved record type, a record shorter than its
  header — is skipped and the repository walk continues with the next record
  ([Supported Commands](supported-commands.html#oem-and-unknown-records));
* a **FRU** that cannot be read (for example a FRU device that is not present) is reported
  truncated or not at all ([FRU Inventory](fru-inventory.html#how-the-frus-are-read));
* a **sensor** whose reading is not available (completion code `DataNotPresent`, not logged) or
  refused with another completion code is returned without reading data.

Values a record may carry but the library does not model (an OEM or chassis-specific entity ID, a
reserved device or sensor type, a state the reading type does not define) are logged at the
`DEBUG` level as `Invalid value: ...` and replaced with a default (`Other` for an entity ID); the
record is still decoded.
