keywords: low-level api, ipmiconnector, ipmiasyncconnector, session, cipher suite, privilege level, sendmessage, send, ipmi command, sel, system event log, chassis control, custom command
description: Send any IPMI command in the session of IpmiClient with send(), or open RMCP+ sessions yourself with IpmiConnector — choosing the cipher suite and privilege level, timeouts, reading the System Event Log, controlling the power, the asynchronous connector, and writing your own commands.

# Low-Level API

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) wraps what monitoring needs.
Every other command goes through `send()`, in the session of the client, or through the protocol
layer of the `org.metricshub.ipmi.core` packages, which `IpmiClient` itself uses:

| Class | Role |
| --- | --- |
| [`IpmiConnector`](apidocs/org/metricshub/ipmi/core/api/sync/IpmiConnector.html) | Synchronous API: open sessions and send a command, waiting for its response. |
| [`IpmiAsyncConnector`](apidocs/org/metricshub/ipmi/core/api/async/IpmiAsyncConnector.html) | Asynchronous API: send commands and receive the responses through a listener. |
| [`ConnectionHandle`](apidocs/org/metricshub/ipmi/core/api/async/ConnectionHandle.html) | Identifies one connection (one BMC) of a connector, with its cipher suite and privilege level. |
| The commands, in [`org.metricshub.ipmi.core.coding.commands`](apidocs/org/metricshub/ipmi/core/coding/commands/package-summary.html) | One class per IPMI request (`GetChassisStatus`, `GetSdr`, `GetSelEntry`, ...), and one `...ResponseData` class per response. See [Supported Commands](supported-commands.html). |
| [`SerialOverLan`](apidocs/org/metricshub/ipmi/core/api/sol/SerialOverLan.html) | A Serial over LAN console. See [Serial over LAN](serial-over-lan.html). |

## Sending a command through the client

Every command is an
[`IpmiCommandCoder<R>`](apidocs/org/metricshub/ipmi/core/coding/commands/IpmiCommandCoder.html)
that declares the type `R` of its response, and takes only its own parameters: the session sets
the IPMI version, the cipher suite and the authentication type when it sends it. `send()` returns
the typed response, without a cast:

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	GetSelInfoResponseData info = bmc.send(new GetSelInfo());
	System.out.println("SEL entries: " + info.getEntriesCount());
}
```

`send()` throws [`IpmiCompletionCodeException`](timeouts-and-errors.html#exceptions) when the
BMC answers with an error completion code, and `IpmiTimeoutException` when it does not answer. A
command is **never replayed** on a new session when the session is lost while it is sent: the
next operation opens a new session.

### Reading the System Event Log

[`sel()`](system-event-log.html) reads the log; the raw commands behind it are Get SEL Info and
one Get SEL Entry per entry, following the next record IDs:

```java
GetSelInfoResponseData info = bmc.send(new GetSelInfo());
System.out.println("SEL entries: " + info.getEntriesCount());

if (info.getEntriesCount() > 0) { // Get SEL Entry fails on an empty SEL
	// A whole entry needs no reservation (IPMI 2.0, section 31.5): reservation ID 0
	int recordId = 0; // 0: the first entry
	while (recordId != 0xFFFF) { // 0xFFFF: no more entries
		GetSelEntryResponseData entry = bmc.send(new GetSelEntry(0, recordId));
		SelRecord record = entry.getSelRecord();
		System.out.println(record.getRecordId() + " " + record.getRecordType() + " " + record.getTimestamp() + " "
				+ record.getSensorType() + " " + record.getEvent() + " " + record.getEventDirection());
		recordId = entry.getNextRecordId();
	}
}
```

```text
SEL entries: 643
1 System Wed May 15 11:15:25 CEST 2024 EventLoggingDisabled LogAreaReset Assertion
2 OemTimestamped Wed May 15 11:16:11 CEST 2024 null null null
5 System Wed May 15 11:23:27 CEST 2024 PowerUnit PowerOffOrDown Assertion
6 System Wed May 15 11:23:34 CEST 2024 PowerUnit PowerOffOrDown Deassertion
```

`SelRecord` keeps the timestamp as the BMC sent it (a `Date` of the unsigned 32-bit value, so
`FFFFFFFFh` and the pre-init values are dates of 2106 and of 1970 to 1987); `SelEntry` of the
client tells them apart ([Timestamps](system-event-log.html#timestamps)).

### Controlling the power

[`chassisControl()`](chassis-status.html#controlling-the-power) wraps the Chassis Control
command, and `chassisIdentify()` the Chassis Identify command. Sending them yourself, on a client
built with the `Operator` or `Administrator` [privilege](configuration.html#privilege-level),
is the same:

```java
bmc.send(new ChassisControl(PowerCommand.PowerUp));
bmc.send(new ChassisIdentify(15, false)); // 15 seconds; (0, false) turns it off, (0, true) forces it on
```

`PowerCommand` has the six controls of the specification: `PowerUp`, `PowerDown` (immediate,
without shutting down the operating system), `PowerCycle`, `HardReset`, `SoftShutdown` and
`DiagnosticInterrupt`.

## A complete session with `IpmiConnector`

The connector is the layer below the client: several connections and sessions on one UDP port,
no deadline, no session recovery, checked exceptions.

```java
import java.net.InetAddress;
import java.util.Comparator;
import java.util.List;

import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

public class LowLevelExample {

	public static void main(String[] args) throws Exception {
		// Bind a local UDP port: 0 lets the operating system choose a free one
		IpmiConnector connector = new IpmiConnector(0);
		try {
			// Wait at most 2 s for each reply instead of the default 5 s, on every connection created from now on
			connector.setTimeout(2000);

			// Register a connection to the BMC, on UDP port 623
			ConnectionHandle handle = connector.createConnection(InetAddress.getByName("bmc.example.com"));

			// Pick a cipher suite among those the BMC offers: 17 if available, else 3
			List<CipherSuite> suites = connector.getAvailableCipherSuites(handle);
			CipherSuite cipherSuite = suites
					.stream()
					.filter(suite -> suite.getId() == 17 || suite.getId() == 3)
					.max(Comparator.comparingInt(CipherSuite::getId))
					.orElseThrow(() -> new IllegalStateException("The BMC offers neither cipher suite 17 nor 3"));

			// Declare the cipher suite and the privilege level, then log in (RAKP handshake)
			connector.getChannelAuthenticationCapabilities(handle, cipherSuite, PrivilegeLevel.User);
			connector.openSession(handle, "monitor", "the-password", null);
			try {
				// Send any command: the response has the type the command declares
				GetChassisStatusResponseData status = connector.sendMessage(handle, new GetChassisStatus());
				System.out.println("Power is " + (status.isPowerOn() ? "on" : "off"));
			} finally {
				// Log out, even when a command failed: BMCs only have a few session slots
				connector.closeSession(handle);
			}
		} finally {
			// Close every connection and release the local UDP port
			connector.tearDown();
		}
	}
}
```

The four steps before the first command are mandatory and must come in this order:
`createConnection()`, `getAvailableCipherSuites()`, `getChannelAuthenticationCapabilities()`,
`openSession()`. Calling them out of order fails with
`ConnectionException: Illegal connection state: ...`. Close the session in a `finally` block:
`tearDown()` only releases the local resources and does not log out, so a session left open
holds one of the BMC's few session slots until the BMC expires it.

`openSession()` takes the password as a `String`, sent in UTF-8, or as a `byte[]`, sent as is
(a literal `null` password needs a cast to choose between the two). The connection keeps its own
copy of the password and of the BMC key, to open a [Serial over LAN](serial-over-lan.html)
session, so you can clear your arrays once the session is open. The BMC stores at most 20 bytes of
password and of BMC key, and 16 of user name: a longer one fails with `IllegalArgumentException`
before anything is sent. A wrong password or BMC key fails the handshake with
[`AuthenticationException`](apidocs/org/metricshub/ipmi/core/coding/commands/session/AuthenticationException.html)
(a subclass of `IllegalArgumentException`), and the handshake checks that each reply of the BMC is
for this session and confirms the requested algorithms; otherwise it fails with
`IllegalArgumentException: ... does not match the request`.

## Connections and connectors

An `IpmiConnector` owns one local UDP port and the threads that send and receive on it. It can
hold connections to several BMCs at once, each identified by its `ConnectionHandle`, and each
with its own session. Two connectors cannot share a local port: create one connector per local
port (or always pass `0`), and call `tearDown()` when you are done with it.

| Method | Purpose |
| --- | --- |
| `IpmiConnector(int port)`, `IpmiConnector(int port, InetAddress address)` | Bind the given local port (`0`: any free port), on all interfaces or on one, with a keep-alive period of 30 000 ms. |
| `IpmiConnector(int port, long pingPeriod)` | The same, with a [keep-alive period](configuration.html#keep-alive) in ms (`0`: none). |
| `setTimeout(int ms)`, `setTimeout(handle, int ms)` | The [message timeout](#timeouts) of the connections created from now on (5 000 ms by default), or of one connection. |
| `setRetries(int)` | How many times a message or a handshake step without reply is sent again (3 by default). |
| `createConnection(InetAddress address[, int port])` | Register a connection to a BMC (port 623 by default). |
| `createConnection(InetAddress address, [int port,] CipherSuite cipherSuite, PrivilegeLevel level)` | The same, skipping the cipher suite and capabilities steps: call `openSession()` next. |
| `closeSession(handle)` | Log out (Close Session). |
| `closeConnection(handle)` | Close and release the connection; the handle is no longer usable. |
| `tearDown()` | Close every connection and release the local port. |

The settings are those of the connector: two connectors in one JVM can use different timeouts
and retries.

The keep-alive is a Get Device ID that the connection sends one-way every `pingPeriod` ms while a
session is open. A one-way IPMI message (the keep-alive, or a request sent with
`sendOneWayMessage()`) is queued like any request: its tag stays reserved until its reply arrives
or it times out, so that a late reply cannot be taken for the reply of a later request, and it
takes one of the 8 slots of the message window until then. Its reply is discarded instead of
being delivered to the listeners, and its timeout is not reported.

### Choosing the cipher suite

`getAvailableCipherSuites()` returns the standard suites the BMC offers, in the BMC's order, with
the algorithms of the [table](preparing-the-bmc.html#cipher-suites) (the OEM suites are left
out), including suites the library does not implement. Choose one it implements, with integrity:
**3** or **17** in practice; `CipherSuite.isSupported()` tells whether the library implements the
algorithms of a suite, and a suite with an xRC4 or MD5-128 algorithm fails when the session is
opened. Do not fall back to a suite without integrity (0, 1, 6, 15) just because the BMC lists
nothing else: the list is not authenticated. To skip the discovery when you already know the
suite, build it with `CipherSuite.forId()` and pass it to `createConnection()`:

```java
// Suite 17: RAKP-HMAC-SHA256, HMAC-SHA256-128, AES-CBC-128
ConnectionHandle handle = connector.createConnection(address, 623, CipherSuite.forId(17), PrivilegeLevel.User);
connector.openSession(handle, "monitor", "the-password", null);
```

### Privilege level

The [`PrivilegeLevel`](apidocs/org/metricshub/ipmi/core/coding/commands/PrivilegeLevel.html)
requested in `getChannelAuthenticationCapabilities()` (or `createConnection()`) is the maximum
level of the session: `User` for reading, `Operator` for most control commands (chassis control,
setting the boot device), `Administrator` for configuration commands and Serial over LAN. The
account must be allowed that level on the LAN channel, or the handshake fails. Every session
starts at the User level (IPMI 2.0, section 22.18): send
[`SetSessionPrivilegeLevel`](apidocs/org/metricshub/ipmi/core/coding/commands/session/SetSessionPrivilegeLevel.html)
after `openSession()` to raise it, as `IpmiClient` does.

### Timeouts

`setTimeout(ms)` sets the [message timeout](timeouts-and-errors.html#message-timeout-and-retries)
of the connections created afterwards, `setTimeout(handle, ms)` that of one connection. Call them
before `createConnection()` or right after: the timeout then also applies to the handshake.
`sendMessage()` retries a message `getRetries()` times (3 by default) before throwing. Unlike
`IpmiClient`, the connector has no overall deadline: bound it yourself if you need one.

## Sending commands

Every command class takes its own parameters only; the session sets the IPMI version, the cipher
suite and the authentication type when it encodes the request. `sendMessage()` returns the
response type the command declares, or throws:

* `IPMIException` when the BMC answers with an error completion code (`getCompletionCode()`,
  `getRawCode()`),
* `ConnectionException` when no reply came after all the tries, or the connection is not in a
  state that allows sending,
* `IllegalArgumentException` when the response does not match the request.

```java
GetSelInfoResponseData info = connector.sendMessage(handle, new GetSelInfo());
connector.sendMessage(handle, new ChassisControl(PowerCommand.PowerUp));
```

### Writing your own command

A command the library does not implement is a subclass of
[`IpmiCommandCoder<R>`](apidocs/org/metricshub/ipmi/core/coding/commands/IpmiCommandCoder.html),
where `R` is the type of its response: return its network function and command code, build the
request data in `preparePayload()`, and decode the response in `getResponseData()`, where
`validateResponse()` checks the response and the completion code and returns the response data
bytes. A minimal Get Device ID:

```java
public class GetDeviceId extends IpmiCommandCoder<GetDeviceIdResponseData> {

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ApplicationRequest;
	}

	@Override
	public byte getCommandCode() {
		return 0x01; // Get Device ID (IPMI 2.0, section 20.1)
	}

	@Override
	protected IpmiLanMessage preparePayload(int sequenceNumber) {
		// No request data
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null,
				TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public GetDeviceIdResponseData getResponseData(IpmiMessage message) throws IPMIException {
		byte[] data = validateResponse(message); // throws IPMIException on an error completion code
		return new GetDeviceIdResponseData(data); // your own ResponseData implementation
	}
}
```

```java
GetDeviceIdResponseData id = bmc.send(new GetDeviceId());
```

## Asynchronous API

[`IpmiAsyncConnector`](apidocs/org/metricshub/ipmi/core/api/async/IpmiAsyncConnector.html) has
the same session methods, but its `sendMessage(handle, request, isOneWay)` returns at once with
the **tag** of the message. Responses are delivered to the
[`IpmiResponseListener`](apidocs/org/metricshub/ipmi/core/api/async/IpmiResponseListener.html)s
registered with `registerListener()`, as an `IpmiResponseData` (with the `ResponseData`) or an
`IpmiError` (with the exception), carrying the tag and the connection handle of the request:

```java
asyncConnector.registerListener(response -> {
	if (response instanceof IpmiResponseData) {
		ResponseData data = ((IpmiResponseData) response).getResponseData();
		// handle the response to the message tagged response.getTag()
	} else {
		Exception error = ((IpmiError) response).getException();
	}
});
int tag = asyncConnector.sendMessage(handle, new GetChassisStatus(), false);
```

The listener is called from the library's own threads: return quickly. The synchronous
`IpmiConnector` is built on this API.
