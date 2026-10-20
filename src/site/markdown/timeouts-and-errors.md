keywords: timeout, deadline, per-message timeout, retries, lost udp reply, session recovery, ipmiclientexception, ipmitimeoutexception, ipmiconnectionexception, ipmiauthenticationexception, ipmicompletioncodeexception, completion code, illegalargumentexception, illegalstateexception, sensor problem
description: The deadline and per-message timeouts of IpmiClient, the retries, what happens when the BMC drops a UDP reply or loses the session, the exceptions an operation throws, and the problems it reports in its result instead.

# Timeouts and Errors

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

IPMI over LAN runs over **UDP**: a request or a reply can be lost, and nothing but a timeout
tells the client. BMCs do drop replies, especially when several sessions query them at the same
time. Two timeouts apply, both settings of the [client builder](configuration.html):

| Timeout | Set with | Default | Scope |
| --- | --- | --- | --- |
| [Deadline](#deadline) | `timeout(Duration)`, on the builder or on an operation builder | 2 minutes | One operation, session setup included |
| [Message timeout](#message-timeout-and-retries) | `messageTimeout(Duration)`, with `retries(int)` | 5 seconds, 3 retries | Each request, including each step of the session handshake |

## Deadline

Each operation runs on the worker thread of the client, and the calling thread waits for it at
most the deadline. When it expires, the operation is abandoned and throws
[`IpmiTimeoutException`](apidocs/org/metricshub/ipmi/client/IpmiTimeoutException.html) with
nothing collected: there are no partial results. When the operation was running, the state of the
session is unknown, so the client drops it: the next operation opens a new one. An operation still
queued behind those of other threads is never sent, and the session is kept.

The deadline covers the queueing of the operation behind those of other threads, the session
setup when the operation triggers it, the exchange itself, and its replay on a new session when
the session was [lost](#session-recovery) while it ran.

An operation abandoned at its deadline leaves no thread that keeps the JVM alive: the threads of
the library are daemon threads ([Closing the client](configuration.html#closing-the-client)).

## Message timeout and retries

Below the deadline, each message has its own timeout and is retried:

1. The request is sent, and the client waits for the reply up to the **message timeout**.
2. Without a reply, the request is sent again at once (the BMC already had the whole timeout), up
   to `retries` times.
3. When every try failed during the session handshake, the operation fails with
   [`IpmiConnectionException`](apidocs/org/metricshub/ipmi/client/IpmiConnectionException.html)
   (the usual symptom of a wrong host, a filtered UDP port or IPMI over LAN disabled). After the
   handshake, the session is considered [lost](#session-recovery) and the operation fails with
   `IpmiTimeoutException`, except a read operation, which is first run once more on a new
   session.

The control commands are an exception too: `chassisControl()`, `chassisIdentify()` and
`chassisIdentifyForever()` send their command **once**, since a BMC that carried out a reset and
lost the reply would carry it out again; a lost reply fails them with `IpmiTimeoutException`
and leaves the session in doubt (the next operation checks it first, see
[Session recovery](#session-recovery)), a `C3h` reply (which says the response is unavailable, not that
nothing was done) with `IpmiCompletionCodeException` at once, and `chassisStatus()` tells what
the BMC did ([Controlling the power](chassis-status.html#controlling-the-power)). A busy reply
(`C0h` or `D2h`, below) is still retried, as it says that the BMC did not carry the command out.

BMC replies with a *transient* completion code are retried too, up to `retries` times: after a
random pause of up to 4 seconds when the BMC says it is busy (`C0h`, node busy, or `D2h`,
initialization in progress), and at once after a timeout on the BMC side (`C3h`), where the BMC
already waited for the device it could not reach. In the session handshake, one of these
replies, or the RAKP status *insufficient resources to create a session*, is retried at once, up to
`retries` times as well (a lost reply or one of these replies to Open Session or a RAKP message
starts the exchange over from Open Session). Any other error completion code fails at once.

`raw()` retries no completion code, transient or not: it returns every one as the BMC sent it,
except a `D4h` on a client built with `MaximumAvailable`, which raises the session and sends the
request again, as far as the BMC allows
([Raising the session on demand](configuration.html#raising-the-session-on-demand)).

With the defaults, a lost reply costs 5 seconds, then the request is sent again; a BMC that never
answers fails the handshake after 4 tries, about 20 seconds into the first operation. A
collection over a network that loses replies goes faster with a shorter message timeout:

```java
try (IpmiClient bmc = IpmiClient.builder(host)
		.credentials(user, password)
		.messageTimeout(Duration.ofSeconds(2)) // a lost reply costs 2 s instead of 5
		.build()) {
	// ...
}
```

Keep the message timeout above the slowest replies of the BMC: a reply that comes after its
timeout is dropped, and the request is sent again.

The message queue of a connection checks for timeouts every 500 ms: a timeout is reported within
that much of its expiry.

## Session recovery

The client keeps its session open between operations with its
[keep-alive](configuration.html#keep-alive) messages, whose replies it does not check. When the
BMC loses the session all the same (it rebooted, or expired it), the client finds out at the
first message of the session that gets no reply after all its tries, about 20 seconds with the
defaults: the session is then lost, and the next operation opens a new one. A **read** operation
(`deviceId()`, `chassisStatus()`, `sensors()`, `frus()`, `sel()`) that fails because the session
was lost while it ran is retried **once** on a new session, within its deadline: when the BMC
does not answer the setup of that session either (it is still rebooting, or is off the network),
the read fails with the `IpmiConnectionException` of the setup, and when the request again gets
no reply, with `IpmiTimeoutException`. `send()`, `raw()`, `sol().open()` and the control
operations are never replayed, as a chassis control sent twice is not the same as sent once: they
fail with the `IpmiTimeoutException` of the message without reply. `capabilities()` opens no
session.

A session is considered lost when a message of the session got no reply after all its tries, when
the connection was closed, or when the deadline of a running operation expired. A control
operation sends its command once, so its lost reply says nothing about the session: the next
operation first sends a Get Device ID, with the retries, and the session is lost if that gets no
reply either. Two timeouts keep
the session: that of a control command, sent once ([above](#message-timeout-and-retries)), and
that of a request forwarded with Send Message that the BMC acknowledged but the controller behind
it did not answer
([Commands for another controller](low-level-api.html#commands-for-another-controller)).

A session the BMC revoked is not lost, as it still answers: on HPE iLO 5, a session that another
client deleted answers `D4h` (`InsufficentPrivilege`) to every command. Close the client and
build a new one
([Troubleshooting](troubleshooting.html#ipmicompletioncodeexception-with-insufficentprivilege-28d4h-29)).

## Exceptions

Every failure of an operation is an unchecked
[`IpmiClientException`](apidocs/org/metricshub/ipmi/client/IpmiClientException.html), except a
call the API does not allow, which throws a standard Java exception
([Invalid settings and calls](#invalid-settings-and-calls)). Act on the subclass, not on the
message:

| Exception | When |
| --- | --- |
| [`IpmiAuthenticationException`](apidocs/org/metricshub/ipmi/client/IpmiAuthenticationException.html) | The BMC refused the login: unknown user, wrong password or [BMC key](configuration.html#bmc-key), refused role, or the requested privilege refused right after the login. `statusCode()` tells which (below). |
| [`IpmiConnectionException`](apidocs/org/metricshub/ipmi/client/IpmiConnectionException.html) | The session could not be established for a network reason: unknown host, socket error, or no reply to the session setup after all the tries (wrong host, UDP port 623 filtered, IPMI over LAN disabled, an IPMI 1.5-only BMC with `cipherSuite(id)`). Also a connection closed while an operation ran, which loses the session, and the session of a [Serial over LAN](serial-over-lan.html#with-ipmiclient) console that the BMC serves on another UDP port, when it cannot be opened. |
| [`IpmiTimeoutException`](apidocs/org/metricshub/ipmi/client/IpmiTimeoutException.html) | The [deadline](#deadline) of the operation expired, or a message of the session got no reply after all the tries. |
| [`IpmiCompletionCodeException`](apidocs/org/metricshub/ipmi/client/IpmiCompletionCodeException.html) | The BMC answered a command with an error **completion code**, the first byte of every IPMI response (`00h` for success, IPMI 2.0 table 5-2), such as `C1h` (invalid command) or `CBh` (requested data not present): `completionCode()` (`CompletionCode`, `Unknown` for a code the library does not list), `rawCompletionCode()`, `networkFunction()` (`null` for a network function that `NetworkFunction` does not list: an [extension or OEM command](low-level-api.html#extension-and-oem-network-functions) sent with `send()`) and `command()`. `InsufficentPrivilege` (sic, `D4h`) means the command needs a higher [privilege level](configuration.html#privilege-level) than the session has; with `MaximumAvailable`, that the session could not be [raised](configuration.html#raising-the-session-on-demand) that far; when commands that worked earlier in the same session start failing with it, that the BMC [revoked the session](#session-recovery). |
| `IpmiClientException` itself | Any other failure, listed below the table. |

`IpmiClientException` itself reports:

* a BMC without IPMI 2.0 / RMCP+
  ([Troubleshooting](troubleshooting.html#the-bmc-does-not-support-ipmi-2-0)), or with no cipher
  suite in common
  ([how the suite is chosen](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite));
* a [session the BMC refuses](troubleshooting.html#the-bmc-refuses-the-session) for another
  reason than the login (insufficient resources to create a session), or a handshake reply that
  does not match the request (for another session, or confirming other algorithms than the
  requested ones);
* a Set Session Privilege Level after the login that fails with another code than a refusal of
  the level (a busy BMC);
* a reply whose data the library cannot decode;
* a SOL payload that cannot be activated for another reason than a completion code (every
  instance held by another session);
* a System Event Log that cannot be read consistently (changed twice during the read, next record
  IDs that loop, an entry other than the one asked for or with record ID `0000h` or `FFFFh`, a
  Clear SEL still in progress after 10 s:
  [When the read fails](system-event-log.html#when-the-read-fails));
* an interrupted operation (the interrupt status is restored).

`IpmiAuthenticationException.statusCode()` tells which refusal it was:

* the status code of the BMC in its Open Session or RAKP reply (`UnauthorizedName`,
  `UnauthorizedRole`, ...) when it refused the login;
* the completion code of Set Session Privilege Level when it refused the privilege right after the
  login (`Unknown` for its `80h` and `81h`, which the message names);
* `null` when the client rejected the key exchange code or the integrity check value of the BMC
  (RAKP Message 2 or 4): a wrong password or BMC key.

A login the BMC refuses is not tried again, as sending the credentials again can lock the account
out: the handshake is tried again only when a step got no reply or a transient completion code,
and with `MaximumAvailable` a role the BMC refuses is requested one level lower
([Raising the session on demand](configuration.html#raising-the-session-on-demand)).

The checked exception of the protocol core that caused the failure (`IPMIException`,
`ConnectionException`, `SOLException`, ...) is the `getCause()`.

[Troubleshooting](troubleshooting.html) maps these symptoms to their usual fixes.

### Invalid settings and calls

An invalid setting or argument fails at once with `IllegalArgumentException`, before anything is
sent to the BMC:

| Call | Refused |
| --- | --- |
| `IpmiClient.builder(host)` | A `null` or blank host |
| `port(int)` | A port outside 1-65535 |
| `privilege(PrivilegeLevel)` | `Callback` |
| `cipherSuites(int...)`, `cipherSuite(int)` | No suite, a suite outside 0-19, a suite without integrity (0, 1, 6, 15), an xRC4 suite (4, 5, 9, 10, 13, 14, 18, 19) |
| `timeout(Duration)` of the builder or of an operation builder, `messageTimeout(Duration)` | A duration under 1 millisecond (zero included) or above `Integer.MAX_VALUE` milliseconds |
| `retries(int)` | A negative count |
| `keepAlive(Duration)` | A negative period, or one under 1 millisecond other than `Duration.ZERO` |
| `build()` | A user name longer than 16 bytes, a password or BMC key longer than 20 bytes |
| `chassisIdentify(int)` | Seconds outside 0-255 |
| `raw(int, int)` | An odd network function (that of a response) or one outside `00h`-`3Eh`, a command outside 0-255 |
| `data(int...)` of `raw()` | A byte outside 0-255 |
| `sel().since(int)` | A record ID outside 1-65534 |

`IllegalStateException` comes from an operation that needs a session on a client built without
credentials (every operation but `capabilities()`), from every operation of a closed client, and
from `chassisControl(PowerCommand.PowerCycle)` when the system is off. A `null` argument throws
`NullPointerException`, except for `builder(null)` (`IllegalArgumentException`) and
`bmcKey(null)`, which means no BMC key.

## Problems that do not fail the operation

The library never logs: a problem that does not fail an operation is reported in its result.

* An **SDR record** that cannot be used — the BMC returned it truncated, or the library cannot
  decode it (a reserved record type, a record shorter than its header) — is skipped, the
  repository walk continues with the next record, and
  [`SensorList.skippedRecords()`](apidocs/org/metricshub/ipmi/client/SensorList.html) and
  [`FruList.skippedRecords()`](apidocs/org/metricshub/ipmi/client/FruList.html) list it with
  its record ID, its type and the reason
  ([Supported Commands](supported-commands.html#oem-and-unknown-records)).
* A **FRU** that cannot be read entirely — not present, on a controller behind the BMC that does
  not answer, a read refused or a reply malformed at some offset (the reading stops there), an
  area that cannot be decoded — is returned with `complete()` false and the reasons in
  [`problems()`](apidocs/org/metricshub/ipmi/client/Fru.html)
  ([Incomplete FRUs](fru-inventory.html#incomplete-frus)).
* A **sensor** without a usable reading — refused with a completion code (`DataNotPresent`,
  `InsufficentPrivilege`, ...), not valid yet or not scanned, owned by system software, owned by
  a controller behind the BMC that does not answer, or whose reply cannot be decoded — is
  returned without reading and without states, with the reason in
  [`problem()`](apidocs/org/metricshub/ipmi/client/Sensor.html) and the status `UNAVAILABLE`
  ([Sensor problems](sensors.html#problems)). A controller behind the BMC that does not answer
  costs its own sensors only: the session is kept. A non-linear sensor whose reading factors the
  BMC refused or did not return keeps its raw reading, its states and its status, without
  `reading()`, with the reason in `problem()`.
* A **reply** that fails the integrity check of the session, that is not signed in a session with
  integrity, or whose RMCP+ packet cannot be decoded, is discarded as the IPMI specification
  requires, and the request is sent again when it times out; a reply received twice (a duplicate
  or a replay) is discarded too. A reply whose command data is malformed is not discarded: it
  fails that command with `IpmiClientException`. A malformed reply to a sensor reading costs that
  sensor, and one to a FRU read stops the reading of that FRU, as above; any other malformed
  reply, during the walk of the SDR repository for instance, fails the operation.

Values a record may carry but the library does not model (an OEM or chassis-specific entity ID, a
reserved device or sensor type, a state the reading type does not define) are replaced with a
default (`Other` for an entity ID, `Unknown` for a state); the record is still decoded.
