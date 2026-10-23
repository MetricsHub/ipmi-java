keywords: configuration, builder, credentials, password, bmc key, kg, privilege level, maximumavailable, raise privilege, set session privilege level, insufficient privilege, cipher suite, timeout, message timeout, retries, keep-alive, sdr cache, port, thread safety, close, unit test, periodic collection, null user, anonymous login, mockito
description: Every option of the IpmiClient builder — host and port, credentials, BMC key, privilege level (fixed, or raised on demand), cipher suites, the deadline, the message timeout, the retries, the keep-alive period and the SDR cache — with their defaults, valid values and exact semantics, and how to share a client, close it, keep it between collections and unit-test the code that uses it.

# Configuration

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

An [`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) is configured by its
[builder](apidocs/org/metricshub/ipmi/client/IpmiClient.Builder.html): the BMC, the credentials
and the session settings. `build()` validates them and touches no network; the first operation
that needs a session opens it.

```java
import java.time.Duration;

import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;

try (IpmiClient bmc = IpmiClient.builder("bmc.example.com")
		.credentials("monitor", password)        // char[], not copied: wipe it after close()
		.bmcKey(kg)                              // optional: two-key login (Kg), raw bytes
		.port(623)                               // default
		.privilege(PrivilegeLevel.Operator)      // maximum privilege of the session; default User
		.cipherSuites(17, 3)                     // by preference; default 17, 3, 8, 16, 2, 7, 12, 11
		.timeout(Duration.ofMinutes(2))          // overall deadline of each operation; default 2 min
		.messageTimeout(Duration.ofSeconds(5))   // per-message timeout; default 5 s
		.retries(3)                              // resends of a message without reply; default 3
		.keepAlive(Duration.ofSeconds(30))       // default 30 s; Duration.ZERO disables it
		.sdrCache(true)                          // default: walk the SDRs again only on a change
		.build()) {
	// ...
}
```

Every setting is a property of this client: two clients in the same JVM can use different
message timeouts, retries or keep-alive periods. Nothing is configured globally.

## Options

| Option | Default | Valid values | Details |
| --- | --- | --- | --- |
| `builder(host)` | required | a host name or IP address, not blank | [Host and port](#host-and-port) |
| `port(int)` | `623` | 1 to 65535 | [Host and port](#host-and-port) |
| `credentials(user, password)` | none: required by every operation but `capabilities()` | a user name of at most 16 bytes and a password of at most 20 bytes, in UTF-8 | [Credentials](#credentials) |
| `bmcKey(byte[])` | none | at most 20 bytes, or `null` for no key | [BMC key](#bmc-key) |
| `privilege(PrivilegeLevel)` | `User` | `User`, `Operator`, `Administrator`, `MaximumAvailable` | [Privilege level](#privilege-level) |
| `cipherSuites(int...)`, `cipherSuite(int)` | `17, 3, 8, 16, 2, 7, 12, 11` | at least one suite, 0 to 19, but not 0, 1, 6 or 15 (no integrity) nor 4, 5, 9, 10, 13, 14, 18 or 19 (xRC4) | [Cipher suites](#cipher-suites) |
| `timeout(Duration)` | 2 minutes | 1 ms to `Integer.MAX_VALUE` ms (a little under 25 days) | [Timeout](#timeout) |
| `messageTimeout(Duration)` | 5 seconds | 1 ms to `Integer.MAX_VALUE` ms | [Message timeout and retries](#message-timeout-and-retries) |
| `retries(int)` | `3` | 0 or more | [Message timeout and retries](#message-timeout-and-retries) |
| `keepAlive(Duration)` | 30 seconds | `Duration.ZERO` (disabled), or 1 ms or more | [Keep-alive](#keep-alive) |
| `sdrCache(boolean)` | `true` | `true` or `false` | [SDR cache](#sdr-cache) |

A setter refuses an invalid value at once with `IllegalArgumentException`, and `build()` checks the
lengths of the user name, the password and the BMC key
([Invalid settings and calls](timeouts-and-errors.html#invalid-settings-and-calls)). A `null`
argument throws `NullPointerException`, except for `builder(null)`, which throws
`IllegalArgumentException`, and `bmcKey(null)`, which means no key. The defaults of the deadline,
the message timeout, the retries and the keep-alive are public constants of `IpmiClient`:
`DEFAULT_TIMEOUT`, `DEFAULT_MESSAGE_TIMEOUT`, `DEFAULT_RETRIES` and `DEFAULT_KEEP_ALIVE`.

### Host and port

The host is the host name or the IP address (IPv4 or IPv6) of the **BMC**, not of the server's
operating system. It is resolved with `InetAddress.getByName()` each time the client opens a
session or reads `capabilities()`; a name that does not resolve fails the operation with
`IpmiConnectionException`. `host()` returns the host a client was built for.

`port()` is the UDP port of the BMC, **623** by default. Change it only for a BMC behind a NAT
or a proxy that forwards another port to 623. The local UDP port is an ephemeral one, chosen by
the operating system when the client first contacts the BMC (`capabilities()` included), kept
when the client opens a new session, and released by `close()`.

### Credentials

`credentials(user, password)` is the IPMI account of the BMC: see
[Preparing the BMC](preparing-the-bmc.html#creating-the-account). Both are sent to the BMC
encoded in UTF-8, whatever the platform charset. The BMC stores at most 16 bytes of user name and
20 bytes of password: `build()` refuses a longer one with `IllegalArgumentException` (an empty
password is valid). An empty user name is the null user: use it on a BMC that accepts the
anonymous login (empty user name and password) or null user names, as `capabilities()` reports
(`anonymousLogin()`, `nullUserNames()`). The password is a `char[]` that the builder does **not**
copy: the library encodes it to bytes each time it opens a session, so keep the array intact until
`close()` and wipe it afterwards if you want no copy of the password left in memory. The same goes
for the `byte[]` of `bmcKey()`. The library wipes its own copies of both: the ones of the handshake
as soon as the session is open, and the ones the session keeps (for Serial over LAN and MD5-128
integrity) when it closes.

Every operation needs the credentials, except
[`capabilities()`](apidocs/org/metricshub/ipmi/client/IpmiClient.html), which reads what the BMC
offers before any login
([Checking what the BMC offers](preparing-the-bmc.html#checking-what-the-bmc-offers)); an
operation that needs a session on a client built without credentials throws
`IllegalStateException`.

### BMC key

`bmcKey(byte[])` is the **BMC key (Kg)** of the BMC, as raw bytes (at most 20), for BMCs
configured with *two-key* logins. Leave it unset (the default on virtually every BMC): the session
keys are then derived from the password. A key of zero bytes only is the default value of a BMC
and is treated as no key. See [Preparing the BMC](preparing-the-bmc.html#bmc-key-kg).

Like the password, the array is not copied: keep it intact until `close()`, as each new session
uses it. `build()` refuses a key longer than 20 bytes with `IllegalArgumentException`. A key given
to `ipmitool -k` is the bytes of that text; one given to `ipmitool -y` is written in hexadecimal:
decode it to bytes.

### Privilege level

`privilege(PrivilegeLevel)` is the maximum privilege of the session, requested at Open Session
and RAKP Message 1
([`PrivilegeLevel`](apidocs/org/metricshub/ipmi/core/coding/commands/PrivilegeLevel.html) is in
the package `org.metricshub.ipmi.core.coding.commands`). The specification starts every session
at the User level whatever was requested (IPMI 2.0 section 22.18), so the client raises it with
**Set Session Privilege Level** right after the handshake for `Operator` and `Administrator`. A
BMC that refuses the level fails the operation that opens the session with
`IpmiAuthenticationException`; another failure of that command (a busy BMC) fails it with
`IpmiClientException`.

| Level | Enough for |
| --- | --- |
| `User` (default) | Every read operation (`deviceId()`, `chassisStatus()`, `sensors()`, `frus()`, `sel()`), except, on most BMCs, the sensors and FRUs of controllers other than the BMC |
| `Operator` | The control operations: `chassisControl()`, `chassisIdentify()`, `chassisIdentifyForever()`; the sensors and FRUs of controllers other than the BMC, which the BMC reaches with Send Message ([Sensor owners](sensors.html#sensor-owners), [FRUs of other controllers](fru-inventory.html#frus-of-other-controllers)), or Administrator for them on some BMCs |
| `Administrator` | Serial over LAN on most BMCs, the configuration commands |
| `MaximumAvailable` | Everything the account is allowed: the session runs at User and is raised on demand ([below](#raising-the-session-on-demand)) |

The account must be allowed that level on the LAN channel
([Preparing the BMC](preparing-the-bmc.html#creating-the-account)). `Callback`, at which no
operation works, is refused by `privilege()` with `IllegalArgumentException`.

### Raising the session on demand

`privilege(PrivilegeLevel.MaximumAvailable)` runs the session at **User** and raises it only when
a command needs more:

1. **Login**: the client requests Administrator at Open Session and RAKP Message 1, then Operator,
   then User when the BMC refuses the role to the account. It does not use the role `0h` of the
   specification ("highest level matching the proposed algorithms"): HPE iLO 5, Dell iDRAC, Cisco
   IMC and GIGABYTE BMCs refuse it, and Lenovo ThinkServer System Manager (TSM), Supermicro and Fujitsu iRMC BMCs refuse to set
   a session opened with it to any level.
2. **Start at User**: right after the handshake, the client sets the session to User, as some
   BMCs start it at the requested maximum.
3. **Raise on `D4h`**: when the BMC refuses a command for insufficient privilege (`D4h`), the
   client raises the session to Operator with Set Session Privilege Level and sends the command
   again, since the BMC did not carry it out; at the next `D4h`, to Administrator. `sol().open()`
   raises the session the same way when the BMC refuses to activate the payload at its level.
4. **Ceiling**: a level the BMC refuses (`80h`: not available for this user; `81h`: above the
   limit of the channel or of the user) is the ceiling of the session, remembered for its
   lifetime, and the `D4h` reaches the caller as it does at a fixed level
   (`IpmiCompletionCodeException`, or the `problem()` of a sensor). A raise that fails for another
   reason (a busy BMC) fails the command with that completion code, and is tried again at the next
   `D4h`.

This is the mode for a monitoring account on a fleet of BMCs that differ: `sensors()` reads the
sensors of controllers other than the BMC ([Sensor owners](sensors.html#sensor-owners)) at the
User level on an HPE iLO 5, after one raise to Operator on most BMCs, and after a second raise to
Administrator on an AMI-based GIGABYTE BMC, each with the same client and without running the
whole session at the highest level of the account.

A session that the BMC revoked (on HPE iLO 5, a session that another client deleted) answers
`D4h` to every command, Set Session Privilege Level included: the client asks for the raise once,
takes the refusal as the ceiling, and the `D4h` reaches the caller. Such a session is not reopened
by itself: close the client and build a new one
([Troubleshooting](troubleshooting.html#ipmicompletioncodeexception-with-insufficientprivilege-28d4h-29)).

### Cipher suites

`cipherSuites(int...)` lists the suites the client may use, by order of preference: before opening
the session, the client checks that the BMC supports IPMI 2.0 (Get Channel Authentication
Capabilities), asks it which suites it offers (Get Channel Cipher Suites), and takes the first
suite of the list that the BMC offers. The default, `17, 3, 8, 16, 2, 7, 12, 11`, prefers the
suites that sign and encrypt every message (17: RAKP-HMAC-SHA256, HMAC-SHA256-128, AES-CBC-128; 3:
RAKP-HMAC-SHA1, HMAC-SHA1-96, AES-CBC-128; 8: RAKP-HMAC-MD5, HMAC-MD5-128, AES-CBC-128), then
the same without encryption, and last the MD5-128 suites (12 with AES-CBC-128, 11 without), whose
signature is a plain MD5 of the password around the message. A BMC that offers none of the listed
suites fails the operation that opens the session with
`IpmiClientException: The BMC ... offers none of the cipher suites ...`.

`cipherSuite(int)` uses exactly that suite without asking the BMC, which saves the Get Channel
Authentication Capabilities and Get Channel Cipher Suites round trips with a BMC known to support
it; an IPMI 1.5-only BMC then fails with `IpmiConnectionException` (no reply to Open Session)
instead of `IpmiClientException` (does not support IPMI 2.0 / RMCP+). A BMC that does not offer
that suite refuses the Open Session request
(`IpmiClientException: The BMC ... refused the session: ...`) or does not answer it
(`IpmiConnectionException`).

The suites without integrity (0, 1, 6 and 15) are refused by the builder: the list of suites the
BMC sends is not authenticated, so falling back to them would let an attacker downgrade the
session to one whose replies can be forged. So are the xRC4 suites (4, 5, 9, 10, 13, 14, 18 and
19), which the library does not implement: a login with them would always fail. See
[Cipher suites](preparing-the-bmc.html#cipher-suites) for the full table and which algorithms
the library implements.

### Timeout

`timeout(Duration)` is the **overall deadline of each operation**, session setup included: 2
minutes by default. When it expires, the operation is abandoned and throws
[`IpmiTimeoutException`](apidocs/org/metricshub/ipmi/client/IpmiTimeoutException.html) with
nothing collected. When the operation was running, the client drops the session and the next
operation opens a new one; an operation still queued behind those of other threads is never
sent, and the session is kept ([Deadline](timeouts-and-errors.html#deadline)). Walking a large
SDR repository or reading many FRUs can take tens of seconds on a slow BMC. Each operation
builder (`sensors()`, `frus()`, `sel()`, `raw()`, `sol()`) has its own `timeout(Duration)` that
overrides the client's deadline for that operation; for `sol()`, it covers the activation of the
console. `deviceId()`, `chassisStatus()`, the control operations, `send()` and `capabilities()`
run under the client's deadline.

### Message timeout and retries

Below the deadline, each message has its own timeout: `messageTimeout(Duration)`, 5 seconds by
default. A message without reply when it expires is sent again at once, `retries(int)` times
(3 by default); a message that got no reply after all the tries loses the session and fails the
operation with `IpmiTimeoutException`, except a read operation, which is first run once more on a
new session, and fails with `IpmiConnectionException` when the BMC does not answer the setup of
that session either. Two timeouts keep the session: that of a control operation
(`chassisControl()`, `chassisIdentify()`, `chassisIdentifyForever()`), whose command is sent once
and never again, and that of a request forwarded with Send Message that the BMC acknowledged but
the controller behind it did not answer
([Session recovery](timeouts-and-errors.html#session-recovery)). The timeout also applies to each
step of the session handshake, where the failure is an `IpmiConnectionException`.

The deadline bounds the tries too: keep the message timeout times the number of tries
(`retries` + 1) well below it. Otherwise the deadline expires first, and the operation fails with
`IpmiTimeoutException` (`... did not complete within ...`) instead of the
`IpmiConnectionException` of a handshake that got no reply, and a read operation is not run again
on a new session. See [Timeouts and Errors](timeouts-and-errors.html).

### Keep-alive

While the session is open, the client sends a no-op message (Get Device ID, as `ipmitool` does)
every `keepAlive(Duration)`, 30 seconds by default, so that the BMC does not close the session for
inactivity between two operations (BMCs expire an idle session after 60 seconds, typically). Keep
the period well below the inactivity timeout of the BMC. The keep-alive does not wait for its
reply: a keep-alive that gets no reply is not reported and does not mark the session lost; a lost
session shows at the next operation.

`Duration.ZERO` disables the keep-alive: the BMC then expires the session when the client stays
idle, and the client finds out only when a request of the next operation gets no reply after all
its tries (about 20 seconds with the defaults). A read operation is then run again on a new
session; any other operation fails with `IpmiTimeoutException`
([Session recovery](timeouts-and-errors.html#session-recovery)).

### SDR cache

The client caches the SDR repository it reads, so that `sensors()` and `frus()` on the same client
walk it **once**. Before using the cache, each operation sends Get SDR Repository Info and walks
the repository again only when its record count or the timestamps of its most recent addition and
erase changed (IPMI 2.0 section 33.9); a BMC that refuses the command (an error completion code)
is walked every time. `sdrCache(false)` disables the cache for BMCs whose timestamps cannot be
trusted.

## Thread safety

A client can be shared between threads: its operations are **serialized**, one at a time on its
session, in the order the threads submit them, each under its own deadline. Parallel sessions
against the **same BMC** are not reliable: BMCs accept a limited number of sessions and drop
replies under load, and a Supermicro BMC accepts about two sessions opened at the same time and
evicts the others ([#97](https://github.com/metricshub/ipmi-java/issues/97)). The client therefore
uses a single session, plus a second one only for a [Serial over LAN](serial-over-lan.html)
console that the BMC serves on another UDP port. Clients of different BMCs run in parallel, each
on its own thread and UDP port.

## Closing the client

An open client holds one of the BMC's few session slots (typically 4 to 8) with its keep-alive
messages for as long as the JVM runs: close it when you are done with the BMC, with
try-with-resources.

`close()` queues Close Session behind the operations already submitted, sends it once, without
retries, and waits for it at most the message timeout plus one second. When an operation is still
running at the end of that wait, Close Session is not sent: the operation is interrupted and fails
with `IpmiClientException`, and the BMC expires the session by itself. `close()` then releases the
local UDP port and the threads of the client, which are daemon threads and never keep the JVM
alive. A BMC that does not answer the logout does not fail the caller: `close()` throws nothing.
The [SOL consoles](serial-over-lan.html#with-ipmiclient) of the client end: their `input()`
returns -1 once the characters already received are read, and `deactivated()` returns `true`.
Close the consoles first (try-with-resources does): closing the client sends no Deactivate Payload
for them, and leaves the session of a console that the BMC serves on another UDP port for the BMC
to expire. Closing a closed client does nothing, and every operation of a closed client throws
`IllegalStateException`.

## Periodic collection

A monitoring application that collects the same BMCs periodically either keeps one client per BMC
open between collections, or builds one client per collection:

| | One client per BMC, kept open | One client per collection |
| --- | --- | --- |
| Login | Once, then again only when the session is lost | At every collection |
| SDR repository | Walked once; each later `sensors()` or `frus()` sends only Get SDR Repository Info unless the repository changed (a BMC that refuses the command is walked every time) | Walked at every collection |
| BMC session slot | Held as long as the client is open | Free between collections |
| Local resources | One UDP port and two daemon threads (the worker of the client and the UDP receiver) from the first operation until `close()`, plus three while a session is open (the keep-alive timer and two message-queue timers; two with `keepAlive(Duration.ZERO)`) | The same, during the collection only |

Keep the client open between collections, unless other tools need its session slot: this saves
the login and the SDR walk of each collection
([Collecting is slow](troubleshooting.html#collecting-is-slow)). Catch the exceptions in the
collection task, so that a failed collection does not cancel the next ones:

```java
IpmiClient bmc = IpmiClient.builder(host)
		.credentials(user, password)
		.privilege(PrivilegeLevel.MaximumAvailable) // raised on demand for the sensors of other controllers
		.build();
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleWithFixedDelay(() -> {
	try {
		for (Sensor sensor : bmc.sensors().execute()) {
			report(sensor);
		}
	} catch (IpmiClientException e) {
		warn(e); // a lost session is reopened by the next collection
	}
}, 0, 1, TimeUnit.MINUTES);
// At shutdown: scheduler.shutdown(), then bmc.close()
```

## Testing code that uses the client

`IpmiClient`, its operation builders (`SensorsRequest`, `FrusRequest`, `SelRequest`, `RawRequest`,
`SolRequest`) and its results (`BmcCapabilities`, `DeviceId`, `ChassisStatus`, `SensorList`,
`Sensor`, `FruList`, `Fru`, `SkippedRecord`, `SelEntryList`, `SelEntry`, `RawResponse`,
`SolConsole`) are final classes that only the client creates. In unit tests, put the calls of your
code behind an interface of your own and fake it, or mock the final classes with the inline mock
maker of Mockito (the default since Mockito 5, which needs Java 11 or later to run the tests).
