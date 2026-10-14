keywords: serial over lan, sol, console, serial port, solconsole, serialoverlan, payload, administrator, break, bmc key, kg
description: Open a Serial over LAN (SOL) console to a server's serial port through its BMC — as streams in the session of IpmiClient, or with the low-level SerialOverLan class: reading and writing, serial port operations, events, and closing.

# Serial over LAN

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

**Serial over LAN (SOL)** redirects the server's serial port — the BIOS setup, the boot loader,
a Linux or Windows EMS serial console — to an IPMI session. [`IpmiClient`](#with-ipmiclient)
opens it as a pair of streams in its session; the
[`SerialOverLan`](#opening-a-console-with-the-low-level-api) class of
[`org.metricshub.ipmi.core.api.sol`](apidocs/org/metricshub/ipmi/core/api/sol/package-summary.html)
is the layer below, on top of the [low-level API](low-level-api.html).

## Prerequisites

* SOL is **enabled** on the BMC (`ipmitool sol info 1`, `ipmitool sol set enabled true 1`), and the
  server's firmware or operating system writes to the serial port that the BMC redirects, at the
  bit rate configured for SOL.
* The account has the **Administrator** privilege on the LAN channel, and the SOL payload is
  enabled for it (`ipmitool sol payload enable 1 <user id>`).
* No other SOL session is active: a BMC usually supports a single SOL session at a time. The
  console never deactivates a payload that a session the BMC still has activated: activating it then
fails with `The SOL payload is in use by another session: instance 1 by session ...` (the user ID
and privilege of that session). A payload held by a session the BMC does not have any more, that
of a console that was killed without closing, is deactivated and activated again: some BMCs
(Supermicro) keep such a payload active for good, and forget the dead session within a minute.

## With `IpmiClient`

```java
try (IpmiClient bmc = IpmiClient.builder("bmc.example.com")
		.credentials("admin", password)
		.privilege(PrivilegeLevel.Administrator) // the SOL payload needs it on most BMCs
		.build();
		SolConsole console = bmc.sol().open()) {

	console.output().write("\r\n".getBytes(StandardCharsets.US_ASCII)); // wake the console up

	byte[] buffer = new byte[2048];
	int read = console.input().read(buffer); // blocks until characters arrive
	System.out.print(new String(buffer, 0, read, StandardCharsets.US_ASCII));
}
```

[`sol().open()`](apidocs/org/metricshub/ipmi/client/SolRequest.html) activates the SOL payload
in the session of the client (or, when the BMC serves SOL on another UDP port, in a session it
opens there with the same credentials and privilege) and returns a
[`SolConsole`](apidocs/org/metricshub/ipmi/client/SolConsole.html):

| Method | Behavior |
| --- | --- |
| `output()` | An `OutputStream` whose bytes reach the serial port of the server. A write returns once the BMC acknowledged the characters, and throws `IOException` when it does not. |
| `input()` | An `InputStream` of what the server writes on its serial port. A read blocks until characters arrive, and returns -1 once the console is closed or the BMC deactivated it. |
| `deactivated()` | Whether the BMC reported the payload deactivated: the session ended, another console took the payload, or the server reset its serial port. |
| `invokeOperations(SolOperation...)` | The [serial port operations](#serial-port-operations-and-events). |
| `registerEventListener(SolEventListener)` | The [status events](#serial-port-operations-and-events) of the console. |
| `close()` | Deactivates the payload and releases the console; the session of the client stays open for its other operations. Closing a closed console does nothing. |

`open()` throws `IpmiCompletionCodeException` when the BMC refuses the activation
(`InsufficentPrivilege` without the Administrator privilege), and `IpmiClientException` when
the BMC offers no SOL payload instance or the activation fails. The other operations of the
client (`chassisStatus()`, `sensors()`, ...) keep working while a console is open.

## Opening a console with the low-level API

```java
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

import org.metricshub.ipmi.core.api.sol.CipherSuiteSelectionHandler;
import org.metricshub.ipmi.core.api.sol.SerialOverLan;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

// Use cipher suite 17 if the BMC offers it, else 3
CipherSuiteSelectionHandler selector = suites -> suites
		.stream()
		.filter(suite -> suite.getId() == 17 || suite.getId() == 3)
		.max(Comparator.comparingInt(CipherSuite::getId))
		.orElseThrow(() -> new IllegalStateException("The BMC offers neither cipher suite 17 nor 3"));

IpmiConnector connector = new IpmiConnector(0);
try (SerialOverLan sol = new SerialOverLan(connector, "bmc.example.com", "admin", "the-password", selector)) {
	sol.writeString("\r\n", StandardCharsets.US_ASCII); // wake the console up
	System.out.print(sol.readString(StandardCharsets.US_ASCII, 2048, 2000));
}
```

This constructor opens a dedicated session with the **Administrator** privilege (raised to it
right after the login: an account not allowed Administrator fails with `SessionException`, even
on a BMC that would activate the payload at a lower level; open the session yourself at that level
and use `SerialOverLan(connector, session)` then), activates the SOL payload, and owns the session
and the connector: **closing the `SerialOverLan`, or failing to
open it, closes the session and tears down the connector** passed to it. Use a new connector for
each console opened this way.

The cipher suite is chosen by a
[`CipherSuiteSelectionHandler`](apidocs/org/metricshub/ipmi/core/api/sol/CipherSuiteSelectionHandler.html),
which receives the suites the BMC offers and returns the one to use: the selector above picks 17,
else 3, and fails if the BMC offers neither.
[`SpecificCipherSuiteSelector`](apidocs/org/metricshub/ipmi/core/api/sol/SpecificCipherSuiteSelector.html)
always returns the suite it was built with, whether the BMC offers it or not.

| Constructor | Use |
| --- | --- |
| `SerialOverLan(connector, host, user, password, selector)` | New session on UDP port 623 |
| `SerialOverLan(connector, host, port, user, password, selector)` | New session on another port |
| `SerialOverLan(connector, host, port, user, password, bmcKey, selector)` | New session on a BMC configured for two-key logins: the [BMC key](configuration.html#bmc-key) as raw bytes |
| `SerialOverLan(connector, session)` | Reuse a session opened with the low-level API, for one console at a time: closing the console leaves the session, and the connector, open |

If the BMC serves SOL on another UDP port (Activate Payload returns it), the console activates the
payload in a session on that port, as the IPMI specification requires: it uses the session
already open there on the same connector, or opens one with the same user name, password, BMC
key and privilege level, which closing the console closes. The other sessions and the connector are left as they are.

A session runs at the level it was opened for, which `openSession()` sets right after the login.
Only a session opened with `MaximumAvailable` still runs at User: when the payload cannot be
activated at that level, the console raises it to Administrator (Set Session Privilege Level) and
tries again. The constructors throw `SOLException` when the
payload cannot be activated (SOL disabled, no free payload instance, privilege refused, a payload
size that leaves no room for characters), and `SessionException` when a session cannot be opened;
they close what they opened before throwing.

## Reading and writing

Writes block until the BMC acknowledges the data, and return `false` when it is rejected; the
server may then have received the first characters already. The data is sent in packets of the
size the BMC announces when the payload is activated (42 to 248 characters on the BMCs we tested,
at most 255), one packet at a time: writes from several threads are serialized. A packet that the BMC cannot take yet (its buffer is full, or the server
is off) is sent again after a growing pause; a packet whose acknowledgement is lost is sent again
with the same sequence number, so the BMC does not pass it to the server twice.

| Method | Writes |
| --- | --- |
| `writeBytes(byte[])`, `writeByte(byte)` | Raw bytes |
| `writeString(String, Charset)` | A string, encoded with the given charset (`writeString(String)` uses the platform charset) |
| `writeIntArray(int[])`, `writeInt(int)` | Values from 0 to 255, as bytes |

Received characters are buffered as they arrive, up to **64 KiB**: when the buffer is full, the
BMC is told to hold its data (Suspend NACK) until a read makes room. Read often enough not to
fill it: a Supermicro BMC takes the Suspend NACK for an acknowledgement and drops the characters
it was asked to hold. A packet that the BMC sends again (when an acknowledgement is lost) is
buffered once. Reads take from this buffer:

| Method | Behavior |
| --- | --- |
| `readBytes()`, `readString(Charset)` | Everything available now, possibly nothing |
| `readBytes(int count)`, `readString(Charset, int count)` | At most `count` bytes available now |
| `readBytes(int count, int timeoutMs)`, `readString(Charset, int count, int timeoutMs)` | Wait until `count` bytes are available, the buffer is full, or the timeout expires, then return what is available |
| `readIntArray(...)` | The same, as values from 0 to 255 |

Without a `Charset`, `readString(...)` uses the platform charset.

## Serial port operations and events

`invokeOperations(SolOperation...)` acts on the remote serial port:

| `SolOperation` | Effect |
| --- | --- |
| `Break` | Send a serial break (for example the Linux magic SysRq) |
| `FlushInbound`, `FlushOutbound` | Flush the BMC's buffers in either direction |
| `CTS` | De-assert CTS (Clear To Send) to the server's serial controller |
| `DCD_DSR` | De-assert DCD and DSR to the server's serial controller |
| `RingWOR` | Assert the Ring Indicator (wake on ring) |

`Break`, `FlushInbound` and `FlushOutbound` act once. `CTS`, `DCD_DSR` and `RingWOR` are line
states: the console holds them in every packet it sends, data and acknowledgements, until the
next `invokeOperations()` call, which sets them again; call `invokeOperations()` without them to
release them. The operations go in a packet without characters, and the call returns once the
packet is sent: many BMCs never acknowledge such a packet (Supermicro, Cisco IMC and Fujitsu iRMC
in our tests).

A [`SolEventListener`](apidocs/org/metricshub/ipmi/core/api/sol/SolEventListener.html)
registered with `registerEventListener()` receives the status the BMC reports
([`SolStatus`](apidocs/org/metricshub/ipmi/core/coding/payload/sol/SolStatus.html):
`CharacterTransferUnavailable`, `SolDeactivated`, `TransmitOverrun`, `Break`, `RtsAsserted`,
`DtrAsserted`), either spontaneously (`processRequestEvent`) or in the acknowledgements of a
packet you wrote (`processResponseEvent`), once per packet. The statuses in the
acknowledgement of operations, which `invokeOperations()` does not wait for, go to
`processRequestEvent`.

`processRequestEvent` is called on the receiving thread of the connector, which no other reply
reaches meanwhile: return quickly, and do not write, close or open a console from it. Hand that
work over to another thread.

`SolDeactivated` means the BMC closed the console, for example because another user activated
SOL: open a new `SerialOverLan` (from another thread) to continue.

## Closing

`close()` deactivates the SOL payload, closes the sessions the console opened, and tears down the
connector when the console owns it. When the BMC does not confirm the deactivation, `close()`
throws `IOException`, after releasing the sessions and the connector all the same. Closing an
already closed console does nothing.
