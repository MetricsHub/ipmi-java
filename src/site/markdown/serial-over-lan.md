keywords: serial over lan, sol, console, serial port, solconsole, serialoverlan, payload, privilege, administrator, maximumavailable, encryption, break, bmc key, kg, ipmicli
description: Open a Serial over LAN (SOL) console to a server's serial port through its BMC — as streams in the session of IpmiClient, or with the low-level SerialOverLan class: privilege, encryption, payload in use, reading and writing, serial port operations, events, and closing.

# Serial over LAN

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

**Serial over LAN (SOL)** redirects the server's serial port — the BIOS setup, the boot loader,
a Linux serial console, the Special Administration Console (SAC) of Windows Emergency Management
Services — to an IPMI session. [`IpmiClient`](#with-ipmiclient) opens it as a pair of streams in
its session; the [`SerialOverLan`](#opening-a-console-with-the-low-level-api) class of
[`org.metricshub.ipmi.core.api.sol`](apidocs/org/metricshub/ipmi/core/api/sol/package-summary.html)
is the layer below, on top of the [low-level API](low-level-api.html). For an interactive console
in a terminal, with `~.` to close it and `~B` to send a break, use
[`ipmicli sol`](cli.html#interactive-sol-console).

## Prerequisites

* SOL is **enabled** on the BMC (`ipmitool sol info 1`, `ipmitool sol set enabled true 1`; `1` is
  the LAN channel on most BMCs), and the server's firmware or operating system writes to the
  serial port that the BMC redirects, at the bit rate configured for SOL.
* The account is allowed, on the LAN channel, the privilege that the BMC requires for SOL, shown as
  *Privilege Level* by `ipmitool sol info 1`: **Administrator** on most BMCs, User or Operator on
  some. The SOL payload is enabled for the account (`ipmitool sol payload enable 1 <user id>`). A
  client built with `privilege(PrivilegeLevel.MaximumAvailable)` raises its session to the level
  the BMC requires, as far as the account allows
  ([Raising the session on demand](configuration.html#raising-the-session-on-demand)).
* No other SOL session is active: a BMC usually serves a single SOL session at a time
  ([what `open()` does with a payload in use](#with-ipmiclient)).
* The BMC is prepared as described in [Preparing the BMC](preparing-the-bmc.html): IPMI over LAN
  enabled, a cipher suite whose privilege limit allows the level of the console, and the
  [firewall](preparing-the-bmc.html#firewall) open to the UDP port of the SOL payload when the BMC
  announces another one than 623.

When `open()` fails for want of one of them, see
[Opening a Serial over LAN console fails](troubleshooting.html#opening-a-serial-over-lan-console-fails).

> [!IMPORTANT]
> The console is encrypted only when the session uses a cipher suite with encryption (17, 3, 8 or
> 12). With suites 16, 2, 7 or 11, the console crosses the network in clear, the passwords typed
> on it included, and a BMC configured to require encryption
> (`ipmitool sol set force-encryption true 1`) refuses the activation with `84h`. Restrict the
> client to encrypting suites with [`cipherSuites(17, 3, 8, 12)`](configuration.html#cipher-suites);
> with `SerialOverLan`, make its `CipherSuiteSelectionHandler` pick one of them, or open the session
> you pass to `SerialOverLan(connector, session)` with one of them.

## With `IpmiClient`

```java
import java.nio.charset.StandardCharsets;

import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.SolConsole;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;

try (IpmiClient bmc = IpmiClient.builder("bmc.example.com")
		.credentials("admin", password) // password: a char[]
		.privilege(PrivilegeLevel.Administrator) // what most BMCs require for SOL, or MaximumAvailable
		.build();
		SolConsole console = bmc.sol().open()) {

	console.output().write("\r\n".getBytes(StandardCharsets.US_ASCII)); // wake the console up

	byte[] buffer = new byte[2048];
	int read = console.input().read(buffer); // blocks until characters arrive, -1 once the console ended
	if (read > 0) {
		System.out.print(new String(buffer, 0, read, StandardCharsets.US_ASCII));
	}
}
```

[`sol().open()`](apidocs/org/metricshub/ipmi/client/SolRequest.html) activates the SOL payload
in the session of the client (or, when the BMC serves SOL on another UDP port, in a session it
opens there with the same credentials and privilege) and returns a
[`SolConsole`](apidocs/org/metricshub/ipmi/client/SolConsole.html):

| Method | Behavior |
| --- | --- |
| `output()` | An `OutputStream` whose bytes reach the serial port of the server. A write returns once the BMC acknowledged the characters, and throws `IOException` when it does not, or once the console is closed. |
| `input()` | An `InputStream` of what the server writes on its serial port. A read blocks until characters arrive, and returns -1 once the console has ended (closed, or `deactivated()`) and the characters already received are read. On an interrupted thread, a read that has no characters to return throws `IOException`. |
| `deactivated()` | Whether the console ended other than by a successful `close()`: the BMC reported the payload deactivated (another session deactivated or took it, or the server was reset or powered on or off locally), the client was closed or lost the session the console runs in, or `close()` could not deactivate the payload. |
| `invokeOperations(SolOperation...)` | The [serial port operations](#serial-port-operations-and-events). |
| `registerEventListener(SolEventListener)`, `unregisterEventListener(SolEventListener)` | Add or remove a listener of the [status events](#serial-port-operations-and-events) of the console. |
| `close()` | Deactivates the payload and releases the console; the session of the client stays open for its other operations. It throws no exception: when the BMC does not confirm the deactivation, the console is released all the same and `deactivated()` returns `true` ([Closing](#closing)). Closing a closed console does nothing. |

`sol().timeout(Duration)` overrides the [deadline](configuration.html#timeout) of the client for
the activation, session setup included. The deadline bounds the activation only: once open, the
console stays open until it is closed or ends.

`open()` throws (all of them `IpmiClientException`s but `IllegalStateException`):

| Exception | When |
| --- | --- |
| `IpmiCompletionCodeException` | The BMC refused a command of the activation, with the code in `rawCompletionCode()`: `D4h` (`InsufficentPrivilege`) when the session runs below the privilege the BMC requires for SOL, or a code of Activate Payload (IPMI 2.0 table 24-2), such as `81h` (the payload type is disabled) or `84h` (the BMC requires encryption). |
| `IpmiAuthenticationException` | The BMC refused the login or the privilege of the client when the client opened its session, or, for a BMC that serves SOL on another UDP port, the privilege of the client in the session there ([Exceptions](timeouts-and-errors.html#exceptions)). |
| `IpmiConnectionException` | The session of the client, or the one on the SOL port, could not be established. |
| `IpmiTimeoutException` | The activation did not complete within the deadline, or a message of the session got no reply after all the tries. |
| `IpmiClientException` | Every SOL payload instance is held by another live session (below), or the activation failed otherwise (the BMC reports no free payload instance, or a payload size that leaves no room for characters): `getCause()` is the `SOLException`. |
| `IllegalStateException` | The client is closed, or was built without credentials. |

A client built with `privilege(PrivilegeLevel.MaximumAvailable)` needs no fixed level: when the
BMC refuses the activation for insufficient privilege (`D4h`), the session is raised to Operator,
then to Administrator, as far as the account allows, and the activation is sent again after each
raise ([Raising the session on demand](configuration.html#raising-the-session-on-demand)). A level
the BMC refuses is the ceiling of the session, and `open()` then fails with
`IpmiCompletionCodeException`, as at a fixed level.

When every SOL payload instance is taken, `open()` asks the BMC which session holds each one (Get
Payload Instance Info, then Get Session Info):

* A session the BMC still has keeps its console: `open()` fails with an `IpmiClientException`
  that names the holder, `Cannot open the SOL console of <host>:` followed by
  `The SOL payload is in use by another session: instance 1 by session ... (user ID 2, privilege 4)`.
* A session the BMC no longer has (a console killed without being closed) loses it: `open()`
  deactivates the payload and activates it again. Some BMCs (Supermicro) keep such a payload
  active for good; once they forget the dead session, within a minute, the next `open()` reclaims
  it, and until then fails as for a live session.
* When the BMC refuses Get Session Info with another code than `CBh` or `CCh` (which say the
  session is gone), for example `D4h` or `C1h`, `open()` fails with that
  `IpmiCompletionCodeException`, and the holder keeps its console.

The `SerialOverLan` constructors reclaim nothing: they fail with
`SOLException: Cannot activate SOL payload, as there are no available payload instances.`

A console lives in a session of the client. When the client is closed, or drops its session
(lost, or after an operation exceeded its deadline:
[Session recovery](timeouts-and-errors.html#session-recovery)), the console ends: its `input()`
returns -1 once the characters already received are read, and `deactivated()` returns `true`;
open a new console. A console that the BMC serves on another UDP port has a session of its own
there: it outlives the loss of the client's session, but not the closing of the client. An idle
console stays open as long as the [keep-alive](configuration.html#keep-alive) keeps its session
alive; with `keepAlive(Duration.ZERO)`, the BMC expires the session of an idle console. Close the
console before the client (try-with-resources closes them in that order, as in the example above):
closing the client sends no Deactivate Payload, and leaves the session of a console on another
port open until the BMC expires it.

The other operations of the client (`chassisStatus()`, `sensors()`, ...) keep working while a
console is open.

## Opening a console with the low-level API

```java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.metricshub.ipmi.core.api.sol.CipherSuiteSelectionHandler;
import org.metricshub.ipmi.core.api.sol.SerialOverLan;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;

// Pick the first suite with encryption of the client's default preference that the BMC offers
List<Integer> preference = Arrays.asList(17, 3, 8, 12);
CipherSuiteSelectionHandler selector = suites -> suites
		.stream()
		.filter(suite -> preference.contains((int) suite.getId()))
		.min(Comparator.comparingInt(suite -> preference.indexOf((int) suite.getId())))
		.orElseThrow(() -> new IllegalStateException("The BMC offers no cipher suite with encryption"));

IpmiConnector connector = new IpmiConnector(0);
try (SerialOverLan sol = new SerialOverLan(connector, "bmc.example.com", "admin", "the-password", selector)) {
	sol.writeString("\r\n", StandardCharsets.US_ASCII); // wake the console up
	System.out.print(sol.readString(StandardCharsets.US_ASCII, 2048, 2000));
}
```

This constructor opens a dedicated session, activates the SOL payload, and owns the session and
the connector: **closing the `SerialOverLan`, or failing to open it, closes the session and tears
down the connector** passed to it. Use a new connector for each console opened this way.

The session is opened with the **Administrator** privilege, raised to it right after the login.
An account not allowed Administrator fails with `SessionException`, even on a BMC that would
activate the payload at a lower level: open the session yourself at that level and pass it to
`SerialOverLan(connector, session)`, as the example after the table of constructors shows.

The cipher suite is chosen by a
[`CipherSuiteSelectionHandler`](apidocs/org/metricshub/ipmi/core/api/sol/CipherSuiteSelectionHandler.html),
which receives the suites the BMC offers and returns the one to use: the selector above picks the
first of 17, 3, 8 and 12 (the suites with encryption, in the order of the client's default
preference) that the BMC offers, and fails if it offers none of them. A selector returns a suite
or throws: a `null` suite fails the session setup with `SessionException`, before anything is
sent, and is never taken for suite 0.
[`SpecificCipherSuiteSelector`](apidocs/org/metricshub/ipmi/core/api/sol/SpecificCipherSuiteSelector.html)
always returns the suite it was built with, whether the BMC offers it or not:
`new SpecificCipherSuiteSelector(CipherSuite.forId(17))`.

| Constructor | Use |
| --- | --- |
| `SerialOverLan(connector, host, user, password, selector)` | New session on UDP port 623 |
| `SerialOverLan(connector, host, port, user, password, selector)` | New session on another port |
| `SerialOverLan(connector, host, port, user, password, bmcKey, selector)` | New session on a BMC configured for two-key logins: the [BMC key](configuration.html#bmc-key) as raw bytes |
| `SerialOverLan(connector, session)` | Reuse a session opened with the low-level API, for one console at a time: closing the console leaves the session, and the connector, open |

To activate the payload at another privilege than Administrator, open the session yourself
([A complete session with `IpmiConnector`](low-level-api.html#a-complete-session-with-ipmiconnector))
and pass it to the console:

```java
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sol.SerialOverLan;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.connection.Session;

IpmiConnector connector = new IpmiConnector(0);
try {
	ConnectionHandle handle = connector
			.createConnection(
					InetAddress.getByName("bmc.example.com"),
					623,
					CipherSuite.forId(17),
					PrivilegeLevel.Operator);
	Session session = connector.openSession(handle, "operator", "the-password", null);
	try (SerialOverLan sol = new SerialOverLan(connector, session)) {
		sol.writeString("\r\n", StandardCharsets.US_ASCII); // wake the console up
		System.out.print(sol.readString(StandardCharsets.US_ASCII, 2048, 2000));
	} finally {
		connector.closeSession(handle);
	}
} finally {
	connector.tearDown();
}
```

If the BMC serves SOL on another UDP port (Activate Payload returns it), the console activates the
payload in a session on that port, as the IPMI specification requires: it uses the session the
same user already has open there on the same connector, or opens one with the same user name,
password, BMC key, cipher suite and privilege level. Closing the console closes the session it
opened there, but not a session that was already open on that port. `getConnectionHandle()`
returns the connection the console exchanges its packets on (that of the session on the payload
port when there is one), for example to change its message timeout with
`connector.setTimeout(handle, ms)`.

A session runs at the privilege it was opened with:
[`openSession()`](low-level-api.html#privilege-level) raises an Operator or Administrator session
right after the login. A session opened with `MaximumAvailable` runs at User instead: when the BMC
refuses to activate the payload at that level (`D4h`), the console raises the session with Set
Session Privilege Level, to Operator first and then to Administrator, and tries again after each
step. `getRaisedPrivilegeLevel()` returns the level the console reached, or `null` when it raised
nothing; the session keeps that level after the console is closed.

The constructors close what they opened, then throw:

| Exception | When |
| --- | --- |
| `SOLException` | The payload cannot be activated: SOL disabled, no free payload instance, a privilege the BMC refuses, a payload size that leaves no room for characters. `getCause()` is the `IPMIException` when the BMC answered with an error completion code. |
| `SessionException` | A session cannot be opened, or the account is not allowed the privilege requested for it. |

## Reading and writing

This section describes the methods of `SerialOverLan`. `SolConsole` works the same way underneath:
its `output()` sends the same packets and throws `IOException` where `writeBytes()` returns
`false`, and its `input()` reads from the same 64 KiB buffer.

Writes block until the BMC acknowledges the data, and return `false` when the BMC rejects it or
does not answer within the message timeout and retries of the connector; the server may then have
received the first characters already. The data is sent in packets of the size the BMC announces
when the payload is activated (42 to 248 characters on the
[tested BMCs](preparing-the-bmc.html#tested-bmcs), at most 255), one packet at a time: writes from
several threads are serialized. A packet that the BMC cannot take yet (its buffer is full, or the
server is off) is sent again after a pause of 100 ms, then 200 ms, 300 ms and so on, as many times
as the connector retries a message (`IpmiConnector.setRetries()`, 3 by default); the write returns
`false` when the BMC still refuses it. When the BMC accepts only part of a packet, the rest goes in
a new packet. A packet whose acknowledgement is lost is sent again with the same sequence number,
so the BMC does not pass it to the server twice.

| Method | Writes |
| --- | --- |
| `writeBytes(byte[])`, `writeByte(byte)` | Raw bytes |
| `writeString(String, Charset)` | A string, encoded with the given charset (`writeString(String)` uses the platform charset) |
| `writeIntArray(int[])`, `writeInt(int)` | Values from 0 to 255, as bytes |

Received characters are buffered as they arrive, up to **64 KiB**: when the buffer is full, the
BMC is told to hold its data (Suspend NACK) until a read makes room. A packet that the BMC sends
again (when an acknowledgement is lost) is buffered once.

> [!WARNING]
> Read the console often enough not to fill the 64 KiB buffer (the `input()` of a `SolConsole`, or
> the read methods below): a Supermicro BMC takes the Suspend NACK for an acknowledgement and drops
> the characters it was asked to hold.

Reads take from this buffer:

| Method | Behavior |
| --- | --- |
| `readBytes()`, `readString(Charset)` | Everything available now, possibly nothing |
| `readBytes(int count)`, `readString(Charset, int count)` | At most `count` bytes available now |
| `readBytes(int count, int timeoutMs)`, `readString(Charset, int count, int timeoutMs)` | Wait until `count` bytes are available, the buffer is full, or the timeout expires, then return what is available |
| `readIntArray(...)` | The same, as values from 0 to 255 |

Without a `Charset`, `readString(...)` uses the platform charset.

## Serial port operations and events

`invokeOperations(SolOperation...)` acts on the remote serial port:

| [`SolOperation`](apidocs/org/metricshub/ipmi/core/coding/payload/sol/SolOperation.html) | Effect |
| --- | --- |
| `Break` | Send a serial break (for example the Linux magic SysRq) |
| `FlushInbound` | Drop the characters sent from the console that the BMC has not passed to the server yet |
| `FlushOutbound` | Drop the characters from the server that the BMC has not sent to the console yet |
| `CTS` | Deassert CTS (Clear To Send) to the server's serial controller |
| `DCD_DSR` | Deassert DCD and DSR to the server's serial controller |
| `RingWOR` | Assert the Ring Indicator (wake on ring) |

`Break`, `FlushInbound` and `FlushOutbound` act once. `CTS`, `DCD_DSR` and `RingWOR` are line
states: the console holds them in every packet it sends, data and acknowledgements, until the
next `invokeOperations()` call, which sets them again; call `invokeOperations()` without them to
release them. The operations go in a packet without characters. The call returns `true` as soon
as the packet is sent, or `false` when it cannot be sent: it does not wait for an
acknowledgement, which many BMCs never send for such a packet (Supermicro, Cisco IMC and Fujitsu
iRMC BMCs among them).

A [`SolEventListener`](apidocs/org/metricshub/ipmi/core/api/sol/SolEventListener.html)
registered with `registerEventListener()` receives the
[`SolStatus`](apidocs/org/metricshub/ipmi/core/coding/payload/sol/SolStatus.html) values the BMC
reports, once per packet:

| `SolStatus` | Meaning |
| --- | --- |
| `CharacterTransferUnavailable` | The server is powered down or asleep: the BMC cannot pass characters |
| `SolDeactivated` | SOL is deactivated, or being deactivated |
| `TransmitOverrun` | Characters from the server were dropped since the previous packet (hardware flow control ignored) |
| `Break` | The server sent a serial break |
| `RtsAsserted`, `DtrAsserted` | The server asserted RTS or DTR (test mode only) |

The listener receives them through two methods:

| Method | Called for | Thread |
| --- | --- | --- |
| `processRequestEvent(statuses)` | The statuses the BMC sends by itself, and those in the acknowledgement of `invokeOperations()`, which the call does not wait for | The receiving thread of the connector |
| `processResponseEvent(statuses, data, operations)` | The statuses in the acknowledgements of a packet you wrote, with its characters and the line states it held | The thread that writes |

`processRequestEvent` is called on the receiving thread of the connector, which no other reply
reaches meanwhile: return quickly, and do not write, close or open a console from it. Hand that
work over to another thread.

`SolDeactivated` means the BMC closed the console, for example because another user activated
SOL. A `SolConsole` then returns `true` from `deactivated()`, and its `input()` returns -1 once
the characters already received are read. To continue, close the console and open a new one:
`sol().open()` with `IpmiClient`, or a new `SerialOverLan` with the low-level API, from another
thread when the listener reacts.

## Closing

`SolConsole.close()` deactivates the SOL payload, closes the session the console opened on another
UDP port (if any), and leaves the session of the client open. It never throws: when the BMC does
not confirm the deactivation, the console is released all the same and reported deactivated.

`SerialOverLan.close()` deactivates the SOL payload, closes the sessions the console opened, and
tears down the connector when the console owns it. When the BMC does not confirm the deactivation,
it throws `IOException`, after releasing the sessions and the connector all the same
([Closing a Serial over LAN console fails with `Message timed out`](troubleshooting.html#closing-a-serial-over-lan-console-fails-with-message-timed-out)).

Closing an already closed console does nothing.
