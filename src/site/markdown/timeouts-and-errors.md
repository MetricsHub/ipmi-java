keywords: timeout, deadline, per-message timeout, retries, lost udp reply, session recovery, ipmiclientexception, ipmitimeoutexception, ipmiconnectionexception, ipmiauthenticationexception, ipmicompletioncodeexception, completion code
description: The deadline and per-message timeouts of IpmiClient, what happens when the BMC drops a UDP reply or loses the session, the retries, and the exceptions an operation throws.

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

Each operation runs on the worker thread of the client, which waits for it at most the deadline.
When it expires, the operation is abandoned and throws
[`IpmiTimeoutException`](apidocs/org/metricshub/ipmi/client/IpmiTimeoutException.html) with
nothing collected: there are no partial results. The state of the session is then unknown, so the
client drops it: the next operation opens a new one.

The deadline covers the queueing of the operation behind those of other threads, the session
setup when the operation triggers it, and the exchange itself. The library's threads (one worker
per client, and the receiving and timer threads of its connection) are daemon threads: they never
keep the JVM alive, and `close()` ends them.

## Message timeout and retries

Below the deadline, each message has its own timeout and is retried:

1. The request is sent, and the client waits for the reply up to the **message timeout**.
2. Without a reply, the request is sent again at once (the BMC already had the whole timeout), up
   to `retries` times.
3. When every try failed, the operation fails: with
   [`IpmiConnectionException`](apidocs/org/metricshub/ipmi/client/IpmiConnectionException.html)
   during the session handshake (the usual symptom of a wrong host, a filtered UDP port or IPMI
   over LAN disabled), with `IpmiTimeoutException` in the session, where the session is then
   considered [lost](#session-recovery).

The control commands are the exception: `chassisControl()` and `chassisIdentify()` send their
command **once**, since a BMC that carried out a reset and lost the reply would carry it out again;
a lost reply fails them with `IpmiTimeoutException` without losing the session, a `C3h` reply
(which says the response is unavailable, not that nothing was done) with
`IpmiCompletionCodeException` at once, and
`chassisStatus()` tells what the BMC did ([Controlling the power](chassis-status.html#controlling-the-power)).

BMC replies with a *transient* completion code are retried too: the request is sent again after
a random pause of up to 4 seconds when the BMC says it is busy (node busy, out of resources,
initialization in progress), and at once after a timeout on the BMC side (`C3h`), where the BMC
already waited for the device it could not reach. Any other error completion code fails at once.
`raw()` retries none of them: it returns every completion code as the BMC sent it.

With the defaults, a lost reply costs 5 seconds, then the request is sent again; a BMC that never
answers fails the handshake after 4 tries, about 20 seconds into the first operation. A
collection over a network that loses replies goes faster with a shorter message timeout:

```java
IpmiClient.builder(host).credentials(user, password)
		.messageTimeout(Duration.ofSeconds(2))
		.retries(3)
		.build();
```

The message queue of a connection checks for timeouts every 500 ms: a timeout is reported within
that much of its expiry.

## Session recovery

The client keeps its session open between operations with its
[keep-alive](configuration.html#keep-alive) messages. When the session is lost all the same (the
BMC rebooted, expired it, or another client deleted it), the next operation opens a new one. A
**read** operation (`chassisStatus()`, `sensors()`, `frus()`, `sel()`) that fails because the session was
lost while it ran is retried **once** on a new session; `send()`, `raw()` and the control
operations are never replayed, as a chassis control sent twice is not the same as sent once.

A session is considered lost when a message of the session got no reply after all its tries, when
the connection was closed, or when the deadline of an operation expired.

## Exceptions

Every failure of an operation is an unchecked
[`IpmiClientException`](apidocs/org/metricshub/ipmi/client/IpmiClientException.html). Act on
the subclass, not on the message:

| Exception | When |
| --- | --- |
| [`IpmiAuthenticationException`](apidocs/org/metricshub/ipmi/client/IpmiAuthenticationException.html) | The BMC refused the login: unknown user, wrong password or [BMC key](configuration.html#bmc-key), role or privilege refused, also when it refused to raise the session to the requested privilege right after the login. `statusCode()` carries the RAKP status code when the BMC sent one (`UnauthorizedName`, `UnauthorizedRole`, ...), or the completion code of Set Session Privilege Level (`Unknown` for its `80h` and `81h`), and is `null` when the console side rejected the key exchange code of the BMC, the symptom of a wrong password or BMC key. The credentials are sent once. |
| [`IpmiConnectionException`](apidocs/org/metricshub/ipmi/client/IpmiConnectionException.html) | The session could not be established for a network reason: unknown host, socket error, or no reply to the session setup after all the tries (wrong host, UDP port 623 filtered, IPMI over LAN disabled, an IPMI 1.5-only BMC). |
| [`IpmiTimeoutException`](apidocs/org/metricshub/ipmi/client/IpmiTimeoutException.html) | The [deadline](#deadline) of the operation expired, or a message of the session got no reply after all the tries. |
| [`IpmiCompletionCodeException`](apidocs/org/metricshub/ipmi/client/IpmiCompletionCodeException.html) | The BMC answered a command with an error completion code: `completionCode()` (`CompletionCode`, `Unknown` for a code the library does not list), `rawCompletionCode()`, `networkFunction()` and `command()`. `InsufficentPrivilege` (sic, `D4h`) means the command needs a higher [privilege level](configuration.html#privilege-level) than the session has. |
| `IpmiClientException` itself | The rest: no cipher suite in common ([how the suite is chosen](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)), a handshake reply that does not match the request (for another session, or confirming other algorithms than the requested ones), the SOL payload that cannot be activated, an interrupted operation (the interrupt status is restored). |

The checked exception of the protocol core that caused the failure (`IPMIException`,
`ConnectionException`, `SOLException`, ...) is the `getCause()`.

A client built without credentials throws `IllegalStateException` from the operations that need a
session, a closed client from every operation, and the builder throws
`IllegalArgumentException` for an invalid setting (a password longer than 20 bytes, a user name
longer than 16, a cipher suite without integrity, a negative duration).

[Troubleshooting](troubleshooting.html) maps these symptoms to their usual fixes.

## Problems that do not fail the operation

The library never logs: a problem that does not fail an operation is reported in its result.

* An **SDR record** that cannot be used — the BMC returned it truncated, or the library cannot
  decode it (a reserved record type, a record shorter than its header) — is skipped, the
  repository walk continues with the next record, and
  [`SensorList.skippedRecords()`](apidocs/org/metricshub/ipmi/client/SensorList.html) and
  [`FruList.skippedRecords()`](apidocs/org/metricshub/ipmi/client/FruList.html) list it with
  its record ID, its type and the reason
  ([Supported Commands](supported-commands.html#oem-and-unknown-records)).
* A **FRU** that cannot be read entirely — not present, a read refused at some offset, an area
  that cannot be decoded — is returned with `complete()` false and the reasons in
  [`problems()`](apidocs/org/metricshub/ipmi/client/Fru.html)
  ([FRU Inventory](fru-inventory.html#how-the-frus-are-read)).
* A **sensor** whose reading the BMC does not give — refused with a completion code
  (`DataNotPresent`, `InsufficentPrivilege`, ...), not valid yet, or not scanned — is returned
  without reading and without states, with the reason in
  [`problem()`](apidocs/org/metricshub/ipmi/client/Sensor.html) and the status `UNAVAILABLE`.
* A **reply** that fails the integrity check of the session, that is not signed in a session with
  integrity, or that cannot be decoded, is discarded as the IPMI specification requires, and the
  request is sent again when it times out; a reply received twice (a duplicate or a replay) is
  discarded too.

Values a record may carry but the library does not model (an OEM or chassis-specific entity ID, a
reserved device or sensor type, a state the reading type does not define) are replaced with a
default (`Other` for an entity ID, `Unknown` for a state); the record is still decoded.
