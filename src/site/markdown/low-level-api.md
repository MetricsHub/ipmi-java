keywords: low-level api, ipmiconnector, ipmiasyncconnector, session, cipher suite, privilege level, sendmessage, send, raw, ipmi command, sel, system event log, chassis control, custom command, dcmi, extension network function, oem network function, send message, bridging, satellite controller
description: Send any IPMI command in the session of IpmiClient with send(), to the BMC or through Send Message to a controller behind it, or as bytes with raw(), or open RMCP+ sessions yourself with IpmiConnector — choosing the cipher suite and privilege level, timeouts, reading the System Event Log, controlling the power, the asynchronous connector, and writing your own commands, DCMI and OEM ones included.

# Low-Level API

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) wraps what monitoring needs.
Every other command goes through `send()` or `raw()`, in the session of the client, or through
the protocol layer of the `org.metricshub.ipmi.core` packages, which `IpmiClient` itself uses:

| Class | Role |
| --- | --- |
| [`IpmiConnector`](apidocs/org/metricshub/ipmi/core/api/sync/IpmiConnector.html) | Synchronous API: open sessions and send a command, waiting for its response. |
| [`IpmiAsyncConnector`](apidocs/org/metricshub/ipmi/core/api/async/IpmiAsyncConnector.html) | Asynchronous API: send commands and receive the responses through a listener. |
| [`ConnectionHandle`](apidocs/org/metricshub/ipmi/core/api/async/ConnectionHandle.html) | Identifies one connection (one BMC) of a connector: `getRemoteAddress()` and `getRemotePort()` of the BMC, and `getCipherSuite()` and `getPrivilegeLevel()`, the suite and the maximum privilege requested for its session (`MaximumAvailable` stays `MaximumAvailable`, whatever level the session runs at). |
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
BMC answers with an error completion code, `IpmiTimeoutException` when it does not answer or the
deadline of the client expires, and the other exceptions of every operation otherwise: another
`IpmiClientException` when the session cannot be opened or the reply cannot be decoded,
`IllegalStateException` from a closed client or one built without credentials. A command is
**never replayed** on a new session when the session is lost while it is sent: the next operation
opens a new session.

### Commands for another controller

[`SendMessage`](apidocs/org/metricshub/ipmi/core/coding/commands/app/SendMessage.html) carries any
command to a controller behind the BMC (Send Message with response tracking, IPMI 2.0 section
6.13.3) and returns the typed response of that controller. It takes the channel of the controller
and its slave address in the 8-bit form, the 7-bit address shifted left (`44h` for the 7-bit
`22h`). The sensor records of the core give the 7-bit form: pass `record.getChannelNumber()` and
`(record.getSensorOwnerId() & 0x7F) << 1`, and build the carried command with
`record.getSensorOwnerLun()`. The Intel Management Engine of a server, at `2Ch` on channel 6:

```java
GetDeviceIdResponseData me = bmc.send(new SendMessage<>(6, 0x2C, new GetDeviceId()));
System.out.println("ME firmware " + me.getFirmwareRevision() + ", manufacturer " + me.getManufacturerId());
```

```text
ME firmware 4.44, manufacturer 343
```

The LUN is the one the command is built with: `new GetSensorReading(number, lun)`. Send Message
to another controller needs the Operator privilege (IPMI 2.0 table G-1). Some BMCs allow it at
User (HPE iLO 5), others only at Administrator (an AMI-based GIGABYTE BMC): below the level they
require, they answer `D4h`, on which a client built with `MaximumAvailable` raises the session
and sends the request again
([Raising the session on demand](configuration.html#raising-the-session-on-demand)). A reply
lost after the BMC acknowledged Send Message
([`isAcknowledged()`](apidocs/org/metricshub/ipmi/core/coding/commands/app/SendMessage.html))
fails with `IpmiTimeoutException` but keeps the session, as the controller may be absent; a request
the BMC does not acknowledge loses the session, like any other command. `sensors()` and `frus()`
send Send Message themselves ([Sensor owners](sensors.html#sensor-owners),
[How the FRUs are read](fru-inventory.html#how-the-frus-are-read)).

`SendMessage` throws `IllegalArgumentException` for a channel outside 0-15, and for an address
that is not an even number from `00h` to `FEh` (an odd one is a software ID such as `B1h`, which
no controller answers to). When the exchange fails with an error completion code, the
`IpmiCompletionCodeException` always names Send Message (`networkFunction()`
`ApplicationRequest`, `command()` `34h`), whether the BMC refused to forward the request or the
controller answered the carried command with an error: `isAcknowledged()` is `true` only in the
second case. `SendMessage.describeCompletionCode(rawCode)` describes the codes specific to Send
Message, `80h` to `83h` (`83h`: no controller acknowledged the address).

### Reading the System Event Log

[`sel()`](system-event-log.html) reads the log with Get SEL Time UTC Offset, Get SEL Info and one
Get SEL Entry per entry, following the next record IDs, and takes a reservation with Reserve SEL
when a BMC asks for one with `C5h`
([How the log is read](system-event-log.html#how-the-log-is-read)). The loop below is its core,
without the UTC offset, the reservation and the checks of a log that changes while it is read:

```java
GetSelInfoResponseData info = bmc.send(new GetSelInfo());
System.out.println("SEL entries: " + info.getEntriesCount());

if (info.getEntriesCount() > 0) { // Get SEL Entry fails on an empty SEL
	// A whole entry needs no reservation (IPMI 2.0 section 31.5): reservation ID 0. A BMC that answers C5h
	// wants one anyway: take it with bmc.send(new ReserveSel()).getReservationId() and read the entry again
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

`SelRecord.getTimestamp()` is the SEL time as the BMC sent it, converted to a `Date` without the
UTC offset that `sel()` applies. `FFFFFFFFh` (unspecified) comes out as a date in 2106, and a
pre-init time (up to `20000000h`, counted from the initialization of the SEL device) as a date
between 1970 and 1987. The `SelEntry` objects that `sel()` returns apply the offset and handle
both cases: `timestamp()` is empty in both, and `preInitSeconds()` gives the pre-init time
([Timestamps](system-event-log.html#timestamps)).

### Controlling the power

[`chassisControl()`](chassis-status.html#controlling-the-power) wraps the Chassis Control
command, and `chassisIdentify()` the Chassis Identify command; `send()` is for the commands the
client does not wrap.

> [!WARNING]
> `send()` has none of the safeguards of `chassisControl()`: it sends the command again after a
> lost reply or a `C3h`, with the configured
> [retries](timeouts-and-errors.html#message-timeout-and-retries), so a reset or a power cycle can
> reach the BMC twice, and it does not refuse a `PowerCycle` of a system that is off. Use the
> operations for the power commands.

The coders can still be sent with `send()`, on a client built with the `Operator`,
`Administrator` or `MaximumAvailable` [privilege](configuration.html#privilege-level):

```java
bmc.send(new ChassisControl(PowerCommand.PowerUp)); // chassisControl(PowerCommand.PowerUp) is safer
bmc.send(new ChassisIdentify(15, false)); // 15 seconds; (0, false) turns it off, (0, true) forces it on
```

`PowerCommand` has the six controls of the specification: `PowerUp`, `PowerDown` (immediate,
without shutting down the operating system), `PowerCycle`, `HardReset`, `SoftShutdown` and
`DiagnosticInterrupt`.

### Raw requests

[`raw()`](apidocs/org/metricshub/ipmi/client/RawRequest.html) sends any request as bytes, like
`ipmitool raw`, and returns its reply whatever its completion code: a
[`RawResponse`](apidocs/org/metricshub/ipmi/client/RawResponse.html) with `completionCode()`,
`rawCompletionCode()` and `data()`, the response data after the completion code. Get DCMI
Capabilities Info, under the Group Extension network function (`2Ch`), whose first data byte is
the defining body (`DCh` for DCMI), with the parameter `01h`:

```java
RawResponse dcmi = bmc.raw(0x2C, 0x01).data(0xDC, 0x01).execute();
System.out.println(dcmi);
if (dcmi.completionCode() == CompletionCode.Ok) {
	byte[] data = dcmi.data(); // DCh, then the DCMI version: 01h 05h is DCMI 1.5
}
```

```text
completion code 00h, data DC 01 05 02 00 01 07
```

Get Power Reading (DCMI command `02h`, mode `01h`) returns the power consumption of the system in
watts, also on servers whose SDR has no power sensor:

```java
RawResponse power = bmc.raw(0x2C, 0x02).data(0xDC, 0x01, 0x00, 0x00).execute();
byte[] data = power.data();
if (power.completionCode() == CompletionCode.Ok && data.length >= 18) {
	// DCh, then the current, minimum, maximum and average power (2 bytes each, LS byte first),
	// a timestamp, the statistics period and the reading state
	int watts = (data[1] & 0xFF) | (data[2] & 0xFF) << 8;
	boolean active = (data[17] & 0x40) != 0; // the power measurement is active
}
```

A raw request:

* takes any request network function, `00h` to `3Eh`: the standard ones, Transport (`0Ch`),
  Group Extension (`2Ch`), OEM/Group (`2Eh`, whose first three data bytes are an IANA enterprise
  number) and the controller-specific OEM ones (`30h` to `3Eh`). An odd code (that of a response,
  IPMI 2.0 section 5.1), or a command or data byte outside 0-255, fails with
  `IllegalArgumentException`;
* returns an error completion code instead of throwing it, and does not retry it, not even a busy
  one (`C0h`) or a `C3h`; only a `D4h` is retried, by a client built with `MaximumAvailable`
  (below);
* is sent again when its reply is lost, like `send()`, but never replayed on a new session, and
  has none of the safeguards of the power operations;
* runs at the privilege level of the session: a request that needs Operator gets `D4h` from a
  client built at User. A client built with `MaximumAvailable` raises the session on that `D4h`,
  as far as the account allows, and sends the request again, for `raw()` as for `send()`
  ([Raising the session on demand](configuration.html#raising-the-session-on-demand)): `raw()`
  returns the `D4h` once the session cannot go higher, and throws `IpmiCompletionCodeException`
  when the raise fails with another code (a busy BMC). Such a client also keeps track of a Set
  Session Privilege Level you send yourself, typed or raw;
* runs under the [deadline](timeouts-and-errors.html#deadline) of the client, or under its own,
  set with `timeout(Duration)`.

From the command line, [`ipmicli raw`](cli.html#raw) sends the same requests:
`ipmicli ... raw 0x2c 0x01 0xdc 0x01`.

## A complete session with `IpmiConnector`

The connector is the layer below the client: several connections and sessions on one UDP port,
no deadline, no session recovery, checked exceptions.

```java
import java.net.InetAddress;
import java.util.Arrays;
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

			// Pick the first suite of the client's default preference that the BMC offers
			List<Integer> preference = Arrays.asList(17, 3, 8, 16, 2, 7, 12, 11);
			List<CipherSuite> suites = connector.getAvailableCipherSuites(handle);
			CipherSuite cipherSuite = suites
					.stream()
					.filter(suite -> preference.contains((int) suite.getId()))
					.min(Comparator.comparingInt(suite -> preference.indexOf((int) suite.getId())))
					.orElseThrow(() -> new IllegalStateException("The BMC offers none of the preferred cipher suites"));

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

Before the first command, four calls must come in this order, unless `createConnection()` is
given the cipher suite and the privilege level
([Choosing the cipher suite](#choosing-the-cipher-suite)). A call out of order fails with
`ConnectionException: Illegal connection state: ...`.

1. `createConnection()` registers the BMC; nothing is sent.
2. `getAvailableCipherSuites()` sends Get Channel Authentication Capabilities, in the IPMI v1.5
   format at the User level, then the RMCP+ Get Channel Cipher Suites when the reply reports IPMI
   v2.0 connections (IPMI 2.0 section 13.13, table 22-15). An IPMI 1.5-only BMC fails at once with
   [`Ipmi20UnsupportedException`](apidocs/org/metricshub/ipmi/core/connection/Ipmi20UnsupportedException.html),
   a `ConnectionException` that is not sent again.
3. `getChannelAuthenticationCapabilities()` sends nothing: it sets the cipher suite and the
   privilege level of the session, and returns the Get Channel Authentication Capabilities reply
   that step 2 received.
4. `openSession()` logs in (Open Session, RAKP Messages 1 to 4) and sets the session to its
   [privilege level](#privilege-level).

Close the session in a `finally` block: `tearDown()` and `closeConnection()` only release local
resources and do not log out, so a session left open holds one of the BMC's few session slots
until the BMC expires it.

`openSession()` takes the password as a `String`, sent in UTF-8, or as a `byte[]`, sent as is
(a literal `null` password needs a cast to choose between the two). The connection keeps its own
copy of the password and of the BMC key, to open a [Serial over LAN](serial-over-lan.html)
session, so you can clear your arrays once the session is open. The BMC stores at most 20 bytes of
password and of BMC key, and 16 of user name: a longer one fails with `IllegalArgumentException`
before anything is sent. The handshake fails with:

* [`AuthenticationException`](apidocs/org/metricshub/ipmi/core/coding/commands/session/AuthenticationException.html),
  a subclass of `IllegalArgumentException`, for a wrong password or BMC key;
* `IPMIException` when the BMC refuses the login with an RMCP+ status code: `0Dh`
  (`UnauthorizedName`) for an unknown user name, `0Ah` (`UnauthorizedRole`) or `09h`
  (`InvalidRole`) for a privilege level the account is not allowed (with `MaximumAvailable`, only
  when User is refused too, as each refused level is followed by the next one down). On `01h`
  (`InsufficientResources`, no free session slot), the login is first started again from Open
  Session, like a login whose step got no reply;
* `IllegalArgumentException: ... does not match the request` when a reply is not for this
  session or does not confirm the requested algorithms;
* `SessionException` when the BMC refuses to set the session to its privilege level
  ([Privilege level](#privilege-level));
* `ConnectionException` when a step gets no reply after all the tries.

## Connections and connectors

An `IpmiConnector` owns one local UDP port and the threads that send and receive on it. It can
hold connections to several BMCs at once, each identified by its `ConnectionHandle`, and each
with its own session. Two connectors cannot share a local port: create one connector per local
port (or always pass `0`), and call `tearDown()` when you are done with it.

| Method | Purpose |
| --- | --- |
| `IpmiConnector(int port)`, `IpmiConnector(int port, InetAddress address)` | Bind the given local port (`0`: any free port), on all interfaces or on one, with a keep-alive period of 30 000 ms. |
| `IpmiConnector(int port, long pingPeriod)` | The same, with a [keep-alive period](configuration.html#keep-alive) in ms (`0`: none). |
| `setTimeout(int ms)`, `setTimeout(handle, int ms)` | The [message timeout](#timeouts) of the connections created from now on (5 000 ms by default), or of one connection; less than 1 ms fails with `IllegalArgumentException`. |
| `getTimeout(handle)` | The message timeout of one connection. |
| `setRetries(int)`, `getRetries()` | How many times a message without reply is sent again, or a login whose step got no reply is started again from Open Session (3 by default); a negative count fails with `IllegalArgumentException`. |
| `createConnection(InetAddress address[, int port])` | Register a connection to a BMC (port 623 by default). |
| `createConnection(InetAddress address, [int port,] CipherSuite cipherSuite, PrivilegeLevel level)` | The same, skipping the cipher suite and capabilities steps: call `openSession()` next. |
| `sendMessage(handle, command)` | Send a command and wait for its typed response ([Sending commands](#sending-commands)). |
| `sendMessage(handle, command, int resends)` | The same, sending the command again at most `resends` times instead of `getRetries()`: `0` for a command the BMC must not receive twice. |
| `sendOneWayMessage(handle, request)` | Send a request without waiting for its reply. |
| `getExistingSessionForCriteria(address, port, user)` | The session this connector holds to that BMC port for that user, or `null`. |
| `registerIncomingMessageListener(listener)`, `unregisterIncomingMessageListener(listener)` | Receive the payloads the BMC sends unprompted, such as SOL packets. |
| `closeSession(handle)` | Log out (Close Session). |
| `closeConnection(handle)` | Close and release the connection without logging out (call `closeSession()` first); the requests still waiting for a reply fail, and the handle is no longer usable. |
| `tearDown()` | Close every connection, without logging out, and release the local port. |

The settings are those of the connector: two connectors in one JVM can use different timeouts
and retries.

The keep-alive is a Get Device ID that the connection sends one-way every `pingPeriod` ms while a
session is open; it is skipped for that period when the message window is full. A one-way
message (the keep-alive, or a request sent with `sendOneWayMessage()`):

* is queued like any request, and keeps its tag and one of the 8 slots of the message window
  until its reply arrives or it times out, so that a late reply is not taken for the reply of a
  later request;
* has its reply discarded instead of delivered to the listeners, and its timeout is not reported.

### Choosing the cipher suite

`getAvailableCipherSuites()` returns the standard suites the BMC offers, in the BMC's order, with
the algorithms of the [table](preparing-the-bmc.html#cipher-suites) (the OEM suites are left
out), including suites the library does not implement. Choose one it implements, with integrity,
as `IpmiClient` does in the order 17, 3, 8, 16, 2, 7, 12, 11
([Cipher suites](configuration.html#cipher-suites)); `CipherSuite.isSupported()` tells whether
the library implements the algorithms of a suite, and a suite with an xRC4 algorithm fails when
the session is opened. Do not fall back to a suite without integrity (0, 1, 6, 15) just because
the BMC lists nothing else: the list is not authenticated. To skip the discovery when you already
know the suite, build it with `CipherSuite.forId()` and pass it to `createConnection()` (Get
Channel Authentication Capabilities is not sent either: an IPMI 1.5-only BMC then just does not
answer).

A `CipherSuite` holds the keys of the session it opens: give each connection its own instance.
Take it from that connection's `getAvailableCipherSuites()`, from a new `CipherSuite.forId()`
call, or from `copy()`, and never pass the same object to two connections:

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
specification starts every session at the User level whatever that maximum (IPMI 2.0 section
22.18), so for `Operator` and `Administrator`, `openSession()` raises it with
[`SetSessionPrivilegeLevel`](apidocs/org/metricshub/ipmi/core/coding/commands/session/SetSessionPrivilegeLevel.html)
right after the handshake, as `IpmiClient` and `ipmitool` do: the session it returns runs at the
requested level.

An account (or a cipher suite) limited to a lower level can be refused at login: `openSession()`
then throws the `IPMIException` of the RMCP+ status, `0Ah` (`UnauthorizedRole`) or `09h`
(`InvalidRole`) for the account, `11h`, `04h` or `05h` for the cipher suite
([The BMC refuses the session](troubleshooting.html#the-bmc-refuses-the-session)), and no session
is open. It can also log in, the BMC capping the session at its limit, and then be refused the
raise: `80h` (the level is not available for this user), `81h` (it exceeds the privilege limit of
the channel or of the user), or another completion code. `openSession()` then closes the session
and throws `SessionException`, whose cause is the `IPMIException` of the completion code. Either
way, request the level the commands need.

A `User` session is not raised. `MaximumAvailable` asks for the highest level the account is
allowed: `openSession()` requests Administrator at Open Session and RAKP Message 1, then Operator
and User when the BMC refuses the role to the account (RMCP+ status `09h` or `0Ah`), and sets the
session to User with `SetSessionPrivilegeLevel` right after the handshake, as some BMCs start it
at the requested maximum. A refusal of that request closes the session and throws
`SessionException`, as for a raise. When a command answers `D4h`, raise the session yourself
with `SetSessionPrivilegeLevel`, as `IpmiClient` does
([Raising the session on demand](configuration.html#raising-the-session-on-demand)).
`SerialOverLan` raises such a session by itself when the BMC refuses to activate the payload with
`D4h` ([Serial over LAN](serial-over-lan.html#opening-a-console-with-the-low-level-api)).

### Timeouts

`setTimeout(ms)` sets the [message timeout](timeouts-and-errors.html#message-timeout-and-retries)
of the connections created afterwards, `setTimeout(handle, ms)` that of one connection. Call them
before `createConnection()` or right after: the timeout then also applies to the handshake.
`sendMessage()` retries a message `getRetries()` times (3 by default) before throwing. Unlike
`IpmiClient`, the connector has no overall deadline: bound it yourself if you need one.

## Sending commands

`sendMessage()` sends a command in the session of a connection and returns the response type the
command declares, or throws:

* `IPMIException` when the BMC answers with an error completion code (`getCompletionCode()`,
  `getRawCode()`). When the BMC says it is busy (node busy `C0h`, initialization in progress
  `D2h`), the request is first sent again up to `getRetries()` times, after a random pause of up
  to 4 seconds each time; a `C3h` (timeout on the BMC side) is sent again at once, like a request
  whose reply was lost. Any other code is thrown at once.
* `ConnectionException` when no reply came after all the tries, or the connection is not in a
  state that allows sending.
* `IllegalStateException` when the connection was closed with `closeConnection()`: its handle is
  no longer usable.
* `IllegalArgumentException` when the response is malformed.

A reply is matched to its request by its tag, its command code and its network function, which
is that of the request plus one (IPMI 2.0 section 5.1). Any other reply is ignored, and a request
whose reply never comes fails with `ConnectionException`.

`sendMessage(handle, command, resends)` sends the command again at most `resends` times when no
reply or a `C3h` comes, instead of `getRetries()` times; a busy reply, which says the command was
not carried out, is still retried `getRetries()` times. Pass `0` for a command the BMC must not
receive twice (a reset, a power cycle), as its reply can be lost after the BMC carried it out: a
lost reply then fails with `ConnectionException`, a `C3h` with `IPMIException`, and Get Chassis
Status tells what the BMC did. A negative count fails with `IllegalArgumentException`.

```java
GetSelInfoResponseData info = connector.sendMessage(handle, new GetSelInfo());
// Sent once, in a session opened at Operator
connector.sendMessage(handle, new ChassisControl(PowerCommand.PowerUp), 0);
```

## Writing your own command

Check [Supported Commands](supported-commands.html) first: Get Device ID, for instance, is
implemented as
[`GetDeviceId`](apidocs/org/metricshub/ipmi/core/coding/commands/app/GetDeviceId.html), the
command behind [`deviceId()`](device-id.html). A command the library does not implement is a
subclass of
[`IpmiCommandCoder<R>`](apidocs/org/metricshub/ipmi/core/coding/commands/IpmiCommandCoder.html),
where `R` is the type of its response: return its network function and command code, build the
request data in `preparePayload()`, and decode the response in `getResponseData()`, where
`validateResponse()` checks the response and the completion code and returns the response data
after the completion code, or `null` when the reply has none: check it before decoding. Such a
command is sent like any other: with `send()` in the session of `IpmiClient`, or with
`sendMessage()` of the connector. A minimal Get Self Test Results (IPMI 2.0 section 20.4), whose
two response bytes say whether the BMC passed its self test:

```java
public class GetSelfTestResults extends IpmiCommandCoder<GetSelfTestResultsResponseData> {

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ApplicationRequest;
	}

	@Override
	public byte getCommandCode() {
		return 0x04; // Get Self Test Results (IPMI 2.0 section 20.4)
	}

	@Override
	protected IpmiLanMessage preparePayload(int sequenceNumber) {
		// No request data
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null,
				TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public GetSelfTestResultsResponseData getResponseData(IpmiMessage message) throws IPMIException {
		byte[] data = validateResponse(message); // throws IPMIException on an error completion code
		if (data == null || data.length < 2) {
			throw new IllegalArgumentException("Invalid response length");
		}
		return new GetSelfTestResultsResponseData(data); // your own ResponseData implementation
	}
}
```

```java
GetSelfTestResultsResponseData results = bmc.send(new GetSelfTestResults());
```

Three more hooks serve the commands that need them:

* `decodeCommandSpecificCompletionCode(rawCode)` gives a meaning to a completion code specific to
  the command (`01h` to `7Eh`, `80h` to `BEh`, which are `Unknown` otherwise): it returns an
  existing `CompletionCode` constant, which `IPMIException.getCompletionCode()` then returns;
  `getRawCode()` always gives the byte. A transient constant (`NodeBusy`, `Timeout`) makes the
  connector send the request again.
* `answers(reply)` tells whether a reply received under the tag of the request answers it, for a
  command answered more than once (as `SendMessage` is). By default, a reply answers the request
  when it has its command code, under its response network function.
* `checkLun(lun)` checks a LUN argument (0 to 3). Pass the LUN to the
  `IpmiLanRequest(NetworkFunction, byte, byte[], byte, byte)` constructor, which takes a standard
  network function only.

### Extension and OEM network functions

[`NetworkFunction`](apidocs/org/metricshub/ipmi/core/coding/payload/lan/NetworkFunction.html)
lists the standard network functions, `00h` to `0Bh`. A command of another one, Transport
(`0Ch`), Group Extension (`2Ch`, DCMI), OEM/Group (`2Eh`) or a controller-specific OEM network
function (`30h` to `3Eh`), returns `null` from `getNetworkFunction()`, overrides
`getNetworkFunctionCode()`, and builds its request with the `IpmiLanRequest` constructor that
takes the code; its reply is the one under that code plus one. Get DCMI Capabilities Info:

```java
public class GetDcmiCapabilitiesInfo extends IpmiCommandCoder<DcmiCapabilitiesResponseData> {

	private final int parameter;

	public GetDcmiCapabilitiesInfo(int parameter) {
		this.parameter = parameter; // 01h: the supported DCMI capabilities
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return null; // not in the enumeration: getNetworkFunctionCode() is what is sent
	}

	@Override
	public int getNetworkFunctionCode() {
		return 0x2C; // Group Extension
	}

	@Override
	public byte getCommandCode() {
		return 0x01; // Get DCMI Capabilities Info
	}

	@Override
	protected IpmiLanMessage preparePayload(int sequenceNumber) {
		// The DCMI group extension identification (DCh), then the parameter selector
		byte[] data = { (byte) 0xDC, (byte) parameter };
		return new IpmiLanRequest(getNetworkFunctionCode(), getCommandCode(), data,
				TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public DcmiCapabilitiesResponseData getResponseData(IpmiMessage message) throws IPMIException {
		return new DcmiCapabilitiesResponseData(validateResponse(message)); // your own ResponseData
	}
}
```

When the BMC answers it with an error completion code, the `IpmiCompletionCodeException` of
`send()` has a `null` `networkFunction()`. To send such a request once, without writing a class,
use [`raw()`](#raw-requests).

## Asynchronous API

[`IpmiAsyncConnector`](apidocs/org/metricshub/ipmi/core/api/async/IpmiAsyncConnector.html) has
the same session methods, except that `createConnection()` always takes the remote port (623 for
a BMC). Its `sendMessage(handle, request, isOneWay)` returns the **tag** of the message as soon as
it is sent, `isOneWay` replacing `sendOneWayMessage()`. A connection has at most 8 requests
waiting for a reply: while all 8 slots are taken, `sendMessage()` waits for a free slot, for up to
one message timeout per try, and fails with `ConnectionException: Message queue is full` after
`getRetries()` more tries.

Responses are delivered to the
[`IpmiResponseListener`](apidocs/org/metricshub/ipmi/core/api/async/IpmiResponseListener.html)s
registered with `registerListener()`, as an `IpmiResponseData` (with the `ResponseData`) or an
`IpmiError` (with the exception), carrying the tag of the request and a new `ConnectionHandle`.
That handle holds the index, address and port of the connection, but not its cipher suite,
privilege level or user. Every listener receives the responses of every connection of the
connector, and tags are numbered per connection: match a response with
`response.getHandle().getHandle() == handle.getHandle()` and its tag, never with `==` or
`equals()` on the handles. `unregisterListener()` removes a listener:

```java
asyncConnector.registerListener(response -> {
	if (response.getHandle().getHandle() != handle.getHandle()) {
		return; // a response on another connection
	}
	if (response instanceof IpmiResponseData) {
		ResponseData data = ((IpmiResponseData) response).getResponseData();
		// handle the response to the message tagged response.getTag()
	} else {
		Exception error = ((IpmiError) response).getException();
	}
});
int tag = asyncConnector.sendMessage(handle, new GetChassisStatus(), false);
```

The asynchronous connector sends each message once. A reply that does not come within the
message timeout reaches the listeners as an `IpmiError` with
`ConnectionException: Message timed out`, and an error completion code, a busy `C0h` included, as
an `IpmiError` with its `IPMIException`: send the message again yourself if needed, as the
synchronous `IpmiConnector` does.

The listener is called from the library's own threads: return quickly. The synchronous
`IpmiConnector` is built on this API. `openSession()` blocks until the session is open and set to
its privilege level: a listener registered before it also receives the reply to the Set Session
Privilege Level it sends for an `Operator`, `Administrator` or `MaximumAvailable` session, under
a tag that `sendMessage()` did not return.
