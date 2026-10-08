keywords: low-level api, ipmiconnector, ipmiasyncconnector, session, cipher suite, privilege level, sendmessage, ipmi command, sel, system event log, chassis control, custom command
description: Open RMCP+ sessions and send any IPMI command with IpmiConnector — choosing the cipher suite and privilege level, timeouts, reading the System Event Log, controlling the power, the asynchronous connector, and writing your own commands.

# Low-Level API

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) covers what monitoring needs.
Everything else goes through the protocol layer of the `org.metricshub.ipmi.core` packages,
which `IpmiClient` itself uses:

| Class | Role |
| --- | --- |
| [`IpmiConnector`](apidocs/org/metricshub/ipmi/core/api/sync/IpmiConnector.html) | Synchronous API: open sessions and send a command, waiting for its response. Start here. |
| [`IpmiAsyncConnector`](apidocs/org/metricshub/ipmi/core/api/async/IpmiAsyncConnector.html) | Asynchronous API: send commands and receive the responses through a listener. |
| [`ConnectionHandle`](apidocs/org/metricshub/ipmi/core/api/async/ConnectionHandle.html) | Identifies one connection (one BMC) of a connector, with its cipher suite and privilege level. |
| The commands, in [`org.metricshub.ipmi.core.coding.commands`](apidocs/org/metricshub/ipmi/core/coding/commands/package-summary.html) | One class per IPMI request (`GetChassisStatus`, `GetSdr`, `GetSelEntry`, ...), and one `...ResponseData` class per response. See [Supported Commands](supported-commands.html). |
| [`SerialOverLan`](apidocs/org/metricshub/ipmi/core/api/sol/SerialOverLan.html) | A Serial over LAN console. See [Serial over LAN](serial-over-lan.html). |

## A complete session

```java
import java.net.InetAddress;
import java.util.Comparator;
import java.util.List;

import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

public class LowLevelExample {

	public static void main(String[] args) throws Exception {
		// Bind a local UDP port: 0 lets the operating system choose a free one
		IpmiConnector connector = new IpmiConnector(0);
		try {
			// Register a connection to the BMC, on UDP port 623
			ConnectionHandle handle = connector.createConnection(InetAddress.getByName("bmc.example.com"));

			// Wait at most 2 s for each reply instead of the default 5 s
			connector.setTimeout(handle, 2000);

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
				// Send any command, and cast the response to the matching ResponseData class
				GetChassisStatusResponseData status = (GetChassisStatusResponseData) connector
						.sendMessage(handle, new GetChassisStatus(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus));
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

## Connections and connectors

An `IpmiConnector` owns one local UDP port and the threads that send and receive on it. It can
hold connections to several BMCs at once, each identified by its `ConnectionHandle`, and each
with its own session. Two connectors cannot share a local port: create one connector per local
port (or always pass `0`), and call `tearDown()` when you are done with it.

| Method | Purpose |
| --- | --- |
| `IpmiConnector(int port)`, `IpmiConnector(int port, InetAddress address)` | Bind the given local port (`0`: any free port), on all interfaces or on one, with the keep-alive period of [`connection.properties`](timeouts-and-errors.html#library-wide-defaults) (30 000 ms). |
| `IpmiConnector(int port, long pingPeriod)` | The same, with a [keep-alive period](configuration.html#keep-alive) in ms (`0`: none). |
| `createConnection(InetAddress address[, int port])` | Register a connection to a BMC (port 623 by default). |
| `createConnection(InetAddress address, [int port,] CipherSuite cipherSuite, PrivilegeLevel level)` | The same, skipping the cipher suite and capabilities steps: call `openSession()` next. |
| `closeSession(handle)` | Log out (Close Session). |
| `closeConnection(handle)` | Forget the connection. |
| `tearDown()` | Close every connection and release the local port. |

### Choosing the cipher suite

`getAvailableCipherSuites()` returns the suites the BMC offers, in the BMC's order, including
suites the library does not implement. Choose one it implements: **3** or **17** in practice (see
the [table](preparing-the-bmc.html#cipher-suites)); a suite with an xRC4 or MD5-128 algorithm
fails when the session is opened. To skip the discovery when you already know the
suite, build it and pass it to `createConnection()`:

```java
// Suite 17: RAKP-HMAC-SHA256, HMAC-SHA256-128, AES-CBC-128
CipherSuite suite17 = new CipherSuite((byte) 17,
		new AuthenticationRakpHmacSha256().getCode(),
		new ConfidentialityAesCbc128().getCode(),
		new IntegrityHmacSha256_128().getCode());
ConnectionHandle handle = connector.createConnection(address, 623, suite17, PrivilegeLevel.User);
connector.openSession(handle, "monitor", "the-password", null);
```

`Connection.getDefaultCipherSuite()` returns suite 3 built the same way.

### Privilege level

The [`PrivilegeLevel`](apidocs/org/metricshub/ipmi/core/coding/commands/PrivilegeLevel.html)
requested in `getChannelAuthenticationCapabilities()` (or `createConnection()`) is the level of
the session: `User` for reading, `Operator` for most control commands (chassis control, setting
the boot device), `Administrator` for configuration commands and Serial over LAN. The account
must be allowed that level on the LAN channel, or the handshake fails.

### Timeouts

`setTimeout(handle, ms)` sets the [per-message timeout](timeouts-and-errors.html#per-message-timeout-and-retries)
of one connection. Call it right after `createConnection()`: it then also applies to the
handshake. `sendMessage()` retries a message `getRetries()` times (3 by default) before throwing.
Unlike `IpmiClient`, the low-level API has no overall timeout: bound it yourself if you need one.

## Sending commands

Every command class takes the IPMI version (`IpmiVersion.V20`), the session's cipher suite
(`handle.getCipherSuite()` or the one you chose), `AuthenticationType.RMCPPlus`, and its own
parameters. `sendMessage()` returns the matching `...ResponseData`, or throws:

* `IPMIException` when the BMC answers with an error completion code (`getCompletionCode()`),
* `ConnectionException` when no reply came after all the tries, or the connection is not in a
  state that allows sending,
* `IllegalArgumentException` when the response does not match the request.

### Reading the System Event Log

```java
GetSelInfoResponseData info = (GetSelInfoResponseData) connector
		.sendMessage(handle, new GetSelInfo(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus));
System.out.println("SEL entries: " + info.getEntriesCount());

if (info.getEntriesCount() > 0) { // Get SEL Entry fails on an empty SEL
	// GetSelEntry reads whole entries, which need no reservation (IPMI 2.0, section 31.5):
	// 0 works on every BMC, including those that do not implement Reserve SEL
	int reservationId = 0;

	int recordId = 0; // 0: the first entry
	while (recordId != 0xFFFF) { // 0xFFFF: no more entries
		GetSelEntryResponseData entry = (GetSelEntryResponseData) connector
				.sendMessage(handle, new GetSelEntry(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus,
						reservationId, recordId));
		SelRecord record = entry.getSelRecord();
		if (record.getRecordType() == SelRecordType.System) {
			System.out.println(record.getTimestamp() + " " + record.getSensorType() + " " + record.getEvent() + " "
					+ record.getEventDirection());
		} else {
			System.out.println("OEM entry " + record.getRecordId()); // vendor-defined content
		}
		recordId = entry.getNextRecordId();
	}
}
```

```text
SEL entries: 643
Wed May 15 11:15:25 CEST 2024 EventLoggingDisabled LogAreaReset Assertion
OEM entry 2
OEM entry 3
OEM entry 4
Wed May 15 11:23:27 CEST 2024 PowerUnit PowerOffOrDown Assertion
Wed May 15 11:23:34 CEST 2024 PowerUnit PowerOffOrDown Deassertion
```

Exposing the SEL in `IpmiClient` is tracked in
[#103](https://github.com/metricshub/ipmi-java/issues/103).

### Controlling the power

In a session opened with the `Operator` or `Administrator` privilege:

```java
connector.sendMessage(handle,
		new ChassisControl(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus, PowerCommand.PowerUp));
```

`PowerCommand` supports `PowerUp`, `PowerDown` (immediate, without shutting down the operating
system) and `HardReset`.

### Writing your own command

A command the library does not implement is a subclass of
[`IpmiCommandCoder`](apidocs/org/metricshub/ipmi/core/coding/commands/IpmiCommandCoder.html):
return its network function and command code, build the request data in `preparePayload()`, and
decode the response in `getResponseData()`, where `validateResponse()` checks the response and
the completion code and returns the response data bytes. A minimal Get Device ID:

```java
public class GetDeviceId extends IpmiCommandCoder {

	public GetDeviceId(CipherSuite cipherSuite) {
		super(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus);
	}

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
	public ResponseData getResponseData(IpmiMessage message) throws IPMIException {
		byte[] data = validateResponse(message); // throws IPMIException on an error completion code
		return new GetDeviceIdResponseData(data); // your own ResponseData implementation
	}
}
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
int tag = asyncConnector.sendMessage(handle, new GetChassisStatus(IpmiVersion.V20, cipherSuite,
		AuthenticationType.RMCPPlus), false);
```

The listener is called from the library's own threads: return quickly. The synchronous
`IpmiConnector` is built on this API.
