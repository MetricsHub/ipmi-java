keywords: serial over lan, sol, console, serial port, serialoverlan, payload, administrator, break
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
* No other SOL session is active: a BMC usually supports a single SOL session at a time.

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
try {
	try (SerialOverLan sol = new SerialOverLan(connector, "bmc.example.com", "admin", "the-password", selector)) {
		sol.writeString("\r\n", StandardCharsets.US_ASCII); // wake the console up
		Thread.sleep(1000);
		System.out.print(sol.readString(StandardCharsets.US_ASCII, 4096, 2000));
	}
} finally {
	// Also releases the connector when the console cannot be opened
	connector.tearDown();
}
```

This constructor opens a dedicated session with the **Administrator** privilege, activates the
SOL payload, and owns the session: **closing the `SerialOverLan` closes the session and tears
down the connector** passed to it. Use a new connector for each console opened this way.

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
| `SerialOverLan(connector, session)` | Reuse a session opened with the low-level API; closing the console leaves it, and the connector, open. If the BMC serves SOL on another UDP port, the console uses an existing session on that port, or opens one (which closing the console closes, with the connector). |

If the session's privilege is too low to activate the payload, the client raises it to
Administrator (Set Session Privilege Level) and tries again. The constructors throw `SOLException` when
the payload cannot be activated (SOL disabled, no free payload instance, privilege refused).

## Reading and writing

Writes block until the BMC acknowledges the data, and return `false` when it is rejected.
Data longer than the BMC's SOL payload size (announced when the payload is activated) is sent in
several packets.

| Method | Writes |
| --- | --- |
| `writeBytes(byte[])`, `writeByte(byte)` | Raw bytes |
| `writeString(String, Charset)` | A string, encoded with the given charset (`writeString(String)` uses the platform charset) |
| `writeIntArray(int[])`, `writeInt(int)` | Values from 0 to 255, as bytes |

Received characters are buffered as they arrive. Reads take from this buffer:

| Method | Behavior |
| --- | --- |
| `readBytes()`, `readString(Charset)` | Everything available now, possibly nothing |
| `readBytes(int count)`, `readString(Charset, int count)` | At most `count` bytes available now |
| `readBytes(int count, int timeoutMs)`, `readString(Charset, int count, int timeoutMs)` | Wait until `count` bytes are available or the timeout expires, then return what is available |
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

A [`SolEventListener`](apidocs/org/metricshub/ipmi/core/api/sol/SolEventListener.html)
registered with `registerEventListener()` receives the status the BMC reports
([`SolStatus`](apidocs/org/metricshub/ipmi/core/coding/payload/sol/SolStatus.html):
`CharacterTransferUnavailable`, `SolDeactivated`, `TransmitOverrun`, `Break`, `RtsAsserted`,
`DtrAsserted`), either spontaneously (`processRequestEvent`) or in the acknowledgement of a
message you sent (`processResponseEvent`).

`SolDeactivated` means the BMC closed the console, for example because another user activated
SOL: open a new `SerialOverLan` to continue.
