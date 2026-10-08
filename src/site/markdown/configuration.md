keywords: configuration, ipmiclientconfiguration, credentials, password, bmc key, kg, skipauth, timeout, pingperiod, keep-alive, port
description: Every option of IpmiClientConfiguration — host and port, credentials, BMC key, skipAuth, the overall timeout and the keep-alive period — with their defaults and exact semantics.

# Configuration

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Every call of [`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) takes an
[`IpmiClientConfiguration`](apidocs/org/metricshub/ipmi/client/IpmiClientConfiguration.html),
which carries the target, the credentials and the timing options. It is a plain mutable object:
build it once per BMC and reuse it for every call; the client never modifies it.

## Constructors

```java
// host, user, password, BMC key, skipAuth, timeout (s)
new IpmiClientConfiguration("bmc.example.com", "monitor", password, null, false, 120);

// ... with a UDP port other than 623
new IpmiClientConfiguration("bmc.example.com", 6230, "monitor", password, null, false, 120);

// ... with a keep-alive period (ms), 0 to disable the keep-alive messages
new IpmiClientConfiguration("bmc.example.com", "monitor", password, null, false, 120, 0);
```

Every option also has a setter (`setPort(int)`, `setPingPeriod(long)`, ...), so the options of
one constructor can be combined with those of another.

## Options

| Option | Default | Details |
| --- | --- | --- |
| `hostname` | required | [Host and port](#host-and-port) |
| `port` | `623` | [Host and port](#host-and-port) |
| `username`, `password` | required | [Credentials](#credentials) |
| `bmcKey` | `null` | [BMC key](#bmc-key) |
| `skipAuth` | required | [skipAuth](#skipauth) |
| `timeout` | required, in **seconds** | [Timeout](#timeout) |
| `pingPeriod` | `-1`: 30 000 ms | [Keep-alive](#keep-alive) |

### Host and port

`hostname` is the host name or the IP address (IPv4 or IPv6) of the **BMC**, not of the server's
operating system. It is resolved with `InetAddress.getByName()` at each call.

`port` is the UDP port of the BMC, **623** by default. Change it only for a BMC behind a NAT
or a proxy that forwards another port to 623. The local UDP port is always an ephemeral one,
chosen by the operating system for each session.

### Credentials

`username` and `password` are the IPMI account of the BMC: see
[Preparing the BMC](preparing-the-bmc.html#creating-the-account). The password is a `char[]`;
the library converts it to a `String` internally to open the session and does not clear the
array, so clear it yourself once you no longer need the configuration.

The client opens every session with the **User** privilege level, which is enough for every
`IpmiClient` method.

### BMC key

`bmcKey` is the **BMC key (Kg)** of the BMC, as raw bytes, for BMCs configured with *two-key*
logins. Leave it `null` (the default on virtually every BMC): the session keys are then derived
from the password. See [Preparing the BMC](preparing-the-bmc.html#bmc-key-kg).

### skipAuth

Despite its name, `skipAuth` does **not** skip authentication: the session is always
authenticated with the user name and password (RAKP handshake). It chooses how the cipher suite is
picked:

| `skipAuth` | Before opening the session | Cipher suite | Privilege |
| --- | --- | --- | --- |
| `false` (recommended) | Get Channel Cipher Suites, then Get Channel Authentication Capabilities | Picked from the BMC's list, [by position](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite) | User |
| `true` | Nothing: the session is opened directly | Always **3** (RAKP-HMAC-SHA1, HMAC-SHA1-96, AES-CBC-128) | User |

Use `true` to force suite 3 on a BMC whose suite list would make the position rule pick a suite
the client does not implement, or to save two round trips per call.

### Timeout

`timeout` is the **overall deadline of each `IpmiClient` call, in seconds**: opening the session,
every command, and closing the session. When it expires, the call is cancelled and throws
`java.util.concurrent.TimeoutException`. Walking a large SDR repository or reading many FRUs can
take tens of seconds on a slow BMC: 120 s is a safe value.

`getFrusAndSensorsAsStringResult()` makes two calls (FRUs, then sensors), each with this
deadline, so it can take up to twice the timeout.

The timeout of each **message** is a different setting, 5 minutes by default; see
[Timeouts and Errors](timeouts-and-errors.html).

### Keep-alive

While a session is open, the client sends a no-op message (Get Channel Authentication
Capabilities) every `pingPeriod` **milliseconds**, so that the BMC does not close the session for
inactivity during a long collection.

| `pingPeriod` | Behavior |
| --- | --- |
| `-1` (default) | The `pingPeriod` of [`connection.properties`](timeouts-and-errors.html#library-wide-defaults): 30 000 ms |
| `> 0` | One keep-alive message every `pingPeriod` ms |
| `0` (or any other negative value) | No keep-alive messages |

Each `IpmiClient` call opens its own session and closes it when it is done, so the keep-alive
only matters for calls that last longer than the BMC's session inactivity timeout (typically
60 s). Disable it (`0`) to keep the traffic to the strict minimum.

## Thread safety

`IpmiClient` methods are static and keep no state between calls: each call creates its own
connector, local UDP port, session and worker thread. Calls can run in parallel, for different
BMCs. Parallel sessions against the **same BMC** are not reliable: BMCs accept a limited number of
sessions and drop replies under load, and parallel sessions from one JVM lose far more replies
than the same sessions from separate processes
([#97](https://github.com/metricshub/ipmi-java/issues/97)). Query a given BMC from one thread at a
time.
