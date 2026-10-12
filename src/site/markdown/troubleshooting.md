keywords: troubleshooting, timeout, no reply, login, authentication, unauthorized name, insufficient privilege, 0xd4, serial over lan, ipmitool, ipmiutil
description: Diagnose the usual failures of the IPMI Java Client — no reply, authentication errors, insufficient privilege, missing sensors or FRUs — and compare with ipmitool and ipmiutil.

# Troubleshooting

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## First steps

1. **Check the BMC from the same machine with another tool**, with the same account, privilege
   level and cipher suite (see [below](#equivalent-ipmitool-and-ipmiutil-commands)). If
   `ipmitool` or `ipmiutil` fails too, the problem is in the BMC configuration or the network:
   see [Preparing the BMC](preparing-the-bmc.html).
2. **Start with `capabilities()`**, which needs no credentials and no session, then
   **`chassisStatus()`**: a single command, so it tests the network, the credentials and the
   cipher suite in a few hundred milliseconds.
3. **Act on the exception type**: the library never logs; every failure is an
   [`IpmiClientException`](timeouts-and-errors.html#exceptions) whose subclass and message say
   what went wrong, and every problem that did not fail the operation is in its result
   ([Problems that do not fail the operation](timeouts-and-errors.html#problems-that-do-not-fail-the-operation)).

## `IpmiConnectionException: No reply from ...`

The BMC did not answer the session setup after all the tries (4 tries of 5 s by default, about
20 s).

| Cause | Check |
| --- | --- |
| Wrong address: the server's operating system instead of its BMC | The BMC has its own IP address (`ipmitool lan print 1` on the server). |
| IPMI over LAN disabled on the BMC | [Enabling IPMI over LAN](preparing-the-bmc.html#enabling-ipmi-over-lan) |
| UDP port 623 filtered | [Firewall](preparing-the-bmc.html#firewall) |
| An IPMI 1.5-only BMC | Such BMCs never answer the RMCP+ requests of the client, starting with Get Channel Cipher Suites ([#154](https://github.com/metricshub/ipmi-java/issues/154)). |
| Several sessions to the same BMC at the same time | BMCs drop replies under concurrent sessions: use one client per BMC, which [serializes its operations](configuration.html#thread-safety). |

## `IpmiTimeoutException`

Either the **deadline** of the operation expired (`... did not complete within ...`): a large
SDR repository or many FRUs on a slow BMC, a network that loses many replies, or a deadline
shorter than the handshake tries; raise the [timeout](configuration.html#timeout), 2 minutes by
default. Or a **message of the session got no reply** after all its tries (`No reply from ... to
... after 4 tries: the session is lost`): the BMC rebooted, expired or revoked the session, or
drops replies under load; the next operation opens a new session, and a read operation is
[retried once](timeouts-and-errors.html#session-recovery) on it by itself.

A lost UDP reply is retried after the
[message timeout](timeouts-and-errors.html#message-timeout-and-retries); an operation only fails
when every try in a row gets no reply.

## `IpmiAuthenticationException`

The RAKP handshake (the login) failed and the session could not be opened. The credentials are
sent once; `statusCode()` says why:

| `statusCode()` | Meaning |
| --- | --- |
| `null` (`... wrong password or BMC key (Authentication check failed)`) | The BMC's proof does not match the password: **wrong password** (or wrong [BMC key](configuration.html#bmc-key)). |
| `UnauthorizedName` | **Unknown user**, or a user not allowed to log in over the LAN channel. |
| `UnauthorizedRole`, `InvalidRole`, `InsufficentPrivilege` | The account is not allowed the requested [privilege level](configuration.html#privilege-level) on the LAN channel, or the BMC refused to raise the session to it. |
| `InvalidIntegrityCheckValue` | The BMC rejected the integrity check value of RAKP Message 3: wrong password or BMC key on a BMC that checks it first. |

An `IllegalArgumentException` from `build()` (`Password is too long. ...`, `Username is too
long. ...`, `BMC key is too long. ...`) means the password or the BMC key is longer than the 20
bytes a BMC stores, or the user name longer than 16 bytes: the BMC cannot have it, and nothing is
sent to it. An `IpmiClientException: ... does not match the request` (`Open Session Response`,
`RAKP Message 2`, `RAKP Message 4`) means a handshake reply of the BMC is for another session, or
confirms other algorithms than the requested ones; it is not sent again.

Check the account with `ipmitool -I lanplus ... -L USER chassis status`: `ipmitool` reports
`RAKP 2 HMAC is invalid` for a wrong password and `unauthorized name` for an unknown user.

## `IpmiCompletionCodeException` with `InsufficentPrivilege` (`D4h`)

The BMC refused a command at the session's privilege level. The default **User** level is enough
for everything the client reads; `send()` of a control command (Chassis Control, chassis
identify) needs `privilege(PrivilegeLevel.Operator)` on the builder, and Serial over LAN
`Administrator` on most BMCs. If a BMC refuses a read command at the User level, check:

* the privilege of the account on the LAN channel (`ipmitool channel getaccess 1 <user id>`),
* the maximum privilege of the cipher suite in use (`Cipher Suite Priv Max` in
  `ipmitool lan print 1`).

When commands that worked earlier in the same session start failing with `D4h`, the session was
revoked, and sending the command again in the same session does not help. On HP iLO 5, this is
what happens when another client deletes the sessions, for example a management tool logged in
as an administrator; the revocation shows at a whole minute of the session's age (60 s, 120 s,
...). Close the client and open a new one, or wait for the next operation: a session whose
messages get no reply is reopened, but one that answers `D4h` is not.

## `IpmiClientException: The BMC ... offers none of the cipher suites ...`

The client only opens sessions with a cipher suite whose messages are signed
([How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)), and
the message lists the suites the BMC offers. Enable suite 17 or 3 on the BMC (in its web
interface, or with `ipmitool lan set 1 cipher_privs`), or list the suites the BMC offers with
`cipherSuites()` on the builder, among those the library implements.

With the [low-level API](low-level-api.html#choosing-the-cipher-suite), a suite that uses an
algorithm the client does not implement fails with
`IllegalArgumentException: Confidentiality algorithm XRC4-128 is not yet implemented.` (or
MD5-128 integrity): choose a suite whose `isSupported()` is `true`.

## Sensors or FRUs are missing

| Symptom | Cause |
| --- | --- |
| `skippedRecords()` lists a record | A record that cannot be decoded is skipped with its reason; the other sensors are still returned. [SDR records](supported-commands.html#sdr-records) lists what is decoded. |
| `Sensor.problem()` says `Get Sensor Reading failed: ...` | The BMC refused the Get Sensor Reading of that sensor with an error completion code: the sensor is returned without reading or states, and the other sensors are still returned. With `D4h`, the BMC may have revoked the session (see the `D4h` section above). |
| `Fru.problems()` says `FRU <id> is truncated at offset <n>: ... Requested Sensor, data, or record not present` | The FRU is declared in the SDR repository but not present, for example an empty power supply bay. Usually harmless: the reading stops there and the areas read so far are decoded. |
| `Fru.problems()` says `Cannot read FRU <id>` or `Cannot decode FRU <id>` | The BMC refused Get FRU Inventory Area Info for that FRU, or its data is not in the IPMI FRU format (for example the SPD data of a memory module, [#107](https://github.com/metricshub/ipmi-java/issues/107)). The other FRUs are still returned. |
| `Fru.problems()` says `The <area> info area at offset <n> is truncated` | The read stopped before the end of that area (see above): the complete areas of the FRU are still returned. |
| A sensor known to `ipmitool` is not returned | Only Full and Compact sensor records of the BMC's own repository are read: sensors behind satellite controllers are not ([#84](https://github.com/metricshub/ipmi-java/issues/84)), and shared Compact records are not expanded ([#100](https://github.com/metricshub/ipmi-java/issues/100)). A sensor whose reading the BMC flags as unavailable or not scanned (`ipmitool` shows `na` or `disabled`) is returned without reading or states, with `problem()` set. |
| Negative processor temperatures (`CPU1 DTS = -44.0`) | Not an error: Intel *Digital Thermal Sensor* readings are the margin below the maximum junction temperature. |

## Collecting is slow

* **FRUs**: each FRU is read 16 bytes at a time, one round trip per chunk: a few seconds per FRU
  on some BMCs ([#102](https://github.com/metricshub/ipmi-java/issues/102)).
* **Lost replies** stall an operation until the message timeout: shorten it
  ([Timeouts and Errors](timeouts-and-errors.html#message-timeout-and-retries)).
* **Unselected sensors** still cost a round trip each unless they are excluded with
  [`types()` or `names()`](sensors.html#selecting-sensors).

## Closing a Serial over LAN console fails with `Message timed out`

`SerialOverLan.close()` throws `IOException` caused by `ConnectionException: Message timed out`
when the BMC does not answer Deactivate Payload within the tries of the
[message timeout](timeouts-and-errors.html#message-timeout-and-retries); the
[`SolConsole`](serial-over-lan.html#with-ipmiclient) of the client swallows it and reports the
console deactivated. The console still closes the sessions it opened.

We saw it on a Dell iDRAC whose server did not read its serial port: after characters were written
to the console, the BMC answered nothing, from any session, for about 30 s. Open a new console or
session after that delay.

## Equivalent `ipmitool` and `ipmiutil` commands

With `ipmitool`, add `-I lanplus -H <bmc> -U <user> -P <password> -L USER -C <suite>`; with
`ipmiutil`, add `-N <bmc> -U <user> -P <password> -F lan2 -J <suite> -V 2`.

| Library | `ipmitool` | `ipmiutil` |
| --- | --- | --- |
| `capabilities()` | `channel getciphers ipmi` | |
| `chassisStatus()` | `chassis status` | `health` |
| `frus().execute()` | `fru print` | `fru` |
| `sensors().execute()` | `sdr elist` | `sensor` |
| `send(new GetSelInfo())`, `send(new GetSelEntry(...))` | `sel info`, `sel elist` | `sel` |
| `sol().open()` | `sol activate` | `sol -a` |

The state descriptions of [`Sensor.states()`](sensors.html#states) follow the wording of
`ipmiutil`.

> [!NOTE]
> `ipmiutil sensor` reads the sensors with Get Device SDR, which some BMCs refuse (`D4h`) at any
> privilege level, while this library reads the SDR repository with Get SDR. A failure of
> `ipmiutil sensor` alone does not mean the library will fail.
