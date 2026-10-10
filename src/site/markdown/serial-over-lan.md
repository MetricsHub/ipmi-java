keywords: serial over lan, sol, console, serial port, serialoverlan, payload, administrator, break, bmc key, kg
description: Open a Serial over LAN (SOL) console to a server's serial port through its BMC — sessions, cipher suite selection, reading and writing, serial port operations, events, and closing.

# Serial over LAN

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

**Serial over LAN (SOL)** redirects the server's serial port — the BIOS setup, the boot loader,
a Linux or Windows EMS serial console — to an IPMI session. The library implements it in
[`org.metricshub.ipmi.core.api.sol`](apidocs/org/metricshub/ipmi/core/api/sol/package-summary.html),
on top of the [low-level API](low-level-api.html).

## Prerequisites

* SOL is **enabled** on the BMC (`ipmitool sol info 1`, `ipmitool sol set enabled true 1`), and the
  server's firmware or operating system writes to the serial port that the BMC redirects, at the
  bit rate configured for SOL.
* The account has the **Administrator** privilege on the LAN channel, and the SOL payload is
  enabled for it (`ipmitool sol payload enable 1 <user id>`).
* No other SOL session is active: a BMC usually supports a single SOL session at a time. The
  console never deactivates a payload that another session activated: activating it then fails.

## Opening a console

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

This constructor opens a dedicated session with the **Administrator** privilege, activates the
SOL payload, and owns the session and the connector: **closing the `SerialOverLan`, or failing to
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
already open there on the same connector, or opens one with the same user name, password and BMC
key, which closing the console closes. The other sessions and the connector are left as they are.

If the session's privilege is too low to activate the payload, the client raises it (Set Session
Privilege Level) to the privilege the session was opened for, Administrator for the constructors
that open their own session, and tries again. The constructors throw `SOLException` when the
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
`DtrAsserted`), either spontaneously (`processRequestEvent`), once per packet, or in the
acknowledgement of a packet you wrote (`processResponseEvent`). The statuses in the
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
