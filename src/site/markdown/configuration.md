keywords: configuration, builder, credentials, password, bmc key, kg, privilege level, cipher suite, timeout, message timeout, retries, keep-alive, sdr cache, port
description: Every option of the IpmiClient builder — host and port, credentials, BMC key, privilege level, cipher suites, the deadline, the message timeout, the retries, the keep-alive period and the SDR cache — with their defaults and exact semantics.

# Configuration

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

An [`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) is configured by its
[builder](apidocs/org/metricshub/ipmi/client/IpmiClient.Builder.html): the BMC, the credentials
and the session settings. `build()` validates them and touches no network; the first operation
that needs a session opens it.

```java
try (IpmiClient bmc = IpmiClient.builder("bmc.example.com")
		.credentials("monitor", password)        // char[], not copied: wipe it after close()
		.bmcKey(kg)                              // optional: two-key login (Kg), raw bytes
		.port(623)                               // default
		.privilege(PrivilegeLevel.Operator)      // maximum privilege of the session; default User
		.cipherSuites(17, 3)                     // preference among the suites the BMC offers; default 17, 3, 8, 16, 2, 7
		.timeout(Duration.ofMinutes(2))          // overall deadline of each operation; default 2 min
		.messageTimeout(Duration.ofSeconds(5))   // per-message timeout; default 5 s
		.retries(3)                              // resends of a message without reply; default 3
		.keepAlive(Duration.ofSeconds(30))       // default 30 s; Duration.ZERO disables it
		.sdrCache(true)                          // default: one SDR walk per client
		.build()) {
	// ...
}
```

Every setting is a property of this client: two clients in the same JVM can use different
message timeouts, retries or keep-alive periods. Nothing is configured globally.

## Options

| Option | Default | Details |
| --- | --- | --- |
| `builder(host)` | required | [Host and port](#host-and-port) |
| `port(int)` | `623` | [Host and port](#host-and-port) |
| `credentials(user, password)` | required by every operation but `capabilities()` | [Credentials](#credentials) |
| `bmcKey(byte[])` | none | [BMC key](#bmc-key) |
| `privilege(PrivilegeLevel)` | `User` | [Privilege level](#privilege-level) |
| `cipherSuites(int...)`, `cipherSuite(int)` | `17, 3, 8, 16, 2, 7` | [Cipher suites](#cipher-suites) |
| `timeout(Duration)` | 2 minutes | [Timeout](#timeout) |
| `messageTimeout(Duration)` | 5 seconds | [Message timeout and retries](#message-timeout-and-retries) |
| `retries(int)` | `3` | [Message timeout and retries](#message-timeout-and-retries) |
| `keepAlive(Duration)` | 30 seconds | [Keep-alive](#keep-alive) |
| `sdrCache(boolean)` | `true` | [SDR cache](#sdr-cache) |

### Host and port

The host is the host name or the IP address (IPv4 or IPv6) of the **BMC**, not of the server's
operating system. It is resolved with `InetAddress.getByName()` each time a session is opened; a
name that does not resolve fails the operation with `IpmiConnectionException`.

`port()` is the UDP port of the BMC, **623** by default. Change it only for a BMC behind a NAT
or a proxy that forwards another port to 623. The local UDP port is always an ephemeral one,
chosen by the operating system when the client opens its session, and released by `close()`.

### Credentials

`credentials(user, password)` is the IPMI account of the BMC: see
[Preparing the BMC](preparing-the-bmc.html#creating-the-account). Both are sent to the BMC
encoded in UTF-8, whatever the platform charset. The BMC stores at most 16 bytes of user name and
20 bytes of password: `build()` refuses a longer one with `IllegalArgumentException` (an empty
password is valid). The password is a `char[]` that the builder does **not** copy: the library
encodes it to bytes each time it opens a session, so keep the array intact until `close()` and
wipe it afterwards if you want no copy of the password left in memory.

Every operation needs the credentials, except
[`capabilities()`](apidocs/org/metricshub/ipmi/client/IpmiClient.html#capabilities()), which
reads what the BMC offers before any login; an operation that needs a session on a client built
without credentials throws `IllegalStateException`.

### BMC key

`bmcKey(byte[])` is the **BMC key (Kg)** of the BMC, as raw bytes (at most 20), for BMCs
configured with *two-key* logins. Leave it unset (the default on virtually every BMC): the session
keys are then derived from the password. A key of zero bytes only is the default value of a BMC
and is treated as no key. See [Preparing the BMC](preparing-the-bmc.html#bmc-key-kg).

### Privilege level

`privilege(PrivilegeLevel)` is the maximum privilege of the session, requested at Open Session
and RAKP Message 1. Every session starts at the User level whatever was requested (IPMI 2.0,
section 22.18), so the client raises it with **Set Session Privilege Level** right after the
handshake for `Operator` and `Administrator`; a BMC that refuses the level fails the first
operation with `IpmiAuthenticationException`.

| Level | Enough for |
| --- | --- |
| `User` (default) | Every read operation: `chassisStatus()`, `sensors()`, `frus()`, `sel()`, but for the sensors of other controllers than the BMC on most BMCs |
| `Operator` | The control operations: `chassisControl()`, `chassisIdentify()`, `chassisIdentifyForever()`; the sensors of other controllers than the BMC, which the BMC reaches with Send Message ([Sensor owners](sensors.html#sensor-owners)), Administrator on some BMCs |
| `Administrator` | Serial over LAN on most BMCs, the configuration commands |

The account must be allowed that level on the LAN channel
([Preparing the BMC](preparing-the-bmc.html#creating-the-account)).

### Cipher suites

`cipherSuites(int...)` lists the suites the client may use, by order of preference: before opening
the session, the client asks the BMC which suites it offers (Get Channel Cipher Suites) and takes
the first of the list that the BMC offers. The default, `17, 3, 8, 16, 2, 7`, prefers the suites
that sign and encrypt every message (17: RAKP-HMAC-SHA256, HMAC-SHA256-128, AES-CBC-128; 3:
RAKP-HMAC-SHA1, HMAC-SHA1-96, AES-CBC-128; 8: RAKP-HMAC-MD5, HMAC-MD5-128, AES-CBC-128), then
the same without encryption. A BMC that offers none of the listed suites fails the first operation
with `IpmiClientException: The BMC ... offers none of the cipher suites ...`.

`cipherSuite(int)` uses exactly that suite without asking the BMC, which saves the Get Channel
Authentication Capabilities and Get Channel Cipher Suites round trips with a BMC known to support
it; an IPMI 1.5-only BMC then fails with `IpmiConnectionException` (no reply to Open Session)
instead of `IpmiClientException` (does not support IPMI 2.0 / RMCP+).

The suites without integrity (0, 1, 6 and 15) are refused by the builder: the list of suites the
BMC sends is not authenticated, so falling back to them would let an attacker downgrade the
session to one whose replies can be forged. See
[Cipher suites](preparing-the-bmc.html#cipher-suites) for the full table and which algorithms
the library implements.

### Timeout

`timeout(Duration)` is the **overall deadline of each operation**, session setup included: 2
minutes by default. When it expires, the operation is abandoned and throws
[`IpmiTimeoutException`](apidocs/org/metricshub/ipmi/client/IpmiTimeoutException.html) with
nothing collected, and the next operation opens a new session. Walking a large SDR repository or
reading many FRUs can take tens of seconds on a slow BMC. Each operation builder (`sensors()`,
`frus()`, `sol()`) has its own `timeout(Duration)` that overrides the client's deadline for that
operation.

### Message timeout and retries

Below the deadline, each message has its own timeout: `messageTimeout(Duration)`, 5 seconds by
default. A message without reply when it expires is sent again at once, `retries(int)` times
(3 by default); a message that got no reply after all the tries fails the operation with
`IpmiTimeoutException`, and the session is considered lost. The timeout also applies to each step
of the session handshake, where the failure is an `IpmiConnectionException`. See
[Timeouts and Errors](timeouts-and-errors.html).

### Keep-alive

While the session is open, the client sends a no-op message (Get Device ID, as `ipmitool` does)
every `keepAlive(Duration)`, 30 seconds by default, so that the BMC does not close the session for
inactivity between two operations (BMCs expire an idle session after 60 seconds, typically).
`Duration.ZERO` disables the keep-alive: the BMC then expires the session when the client stays
idle, and the next operation opens a new one.

### SDR cache

The client caches the SDR repository it reads, so that `sensors()` and `frus()` on the same client
walk it **once**. Before using the cache, each operation sends Get SDR Repository Info and walks
the repository again only when its record count or the timestamps of its most recent addition and
erase changed (IPMI 2.0, section 33.9); a BMC that does not answer the command is walked every
time. `sdrCache(false)` disables the cache for BMCs whose timestamps cannot be trusted.

## Thread safety

A client can be shared between threads: its operations are **serialized**, one at a time on its
session, in the order the threads submit them, each under its own deadline. Parallel sessions
against the **same BMC** are not reliable: BMCs accept a limited number of sessions and drop
replies under load, and parallel sessions from one JVM lose far more replies than the same
sessions from separate processes ([#97](https://github.com/metricshub/ipmi-java/issues/97)), so
the client never opens two. Clients of different BMCs run in parallel, each on its own thread and
UDP port.

An open client holds one of the BMC's few session slots (typically 4 to 8) with its keep-alive
messages for as long as the JVM runs: close it when you are done with the BMC.
