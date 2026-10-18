keywords: troubleshooting, timeout, no reply, login, authentication, unauthorized name, insufficient privilege, 0xd4, set session privilege level, ipmi 1.5, rmcp+, serial over lan, ipmitool, ipmiutil, ipmicli, probe
description: Diagnose the usual failures of the IPMI Java Client — no reply, authentication errors, insufficient privilege, missing sensors or FRUs — and compare with ipmitool and ipmiutil.

# Troubleshooting

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## First steps

1. **Run `ipmicli probe`** from the same machine, with the standalone jar
   ([Command-Line Client](cli.html)): it needs no credentials and no session, and says whether
   the BMC answers, whether it requires a BMC key, and which cipher suites it offers that the
   library implements. Then **`ipmicli -v chassis`** with the account: a single command, so it
   tests the credentials, the privilege level and the cipher suite in a few hundred milliseconds,
   and its exit code (77 authentication, 69 unreachable, 124 timeout) and message name the cause.
   In code, the same two steps are `capabilities()` and `chassisStatus()`.
2. **Check the BMC from the same machine with another tool**, with the same account, privilege
   level and cipher suite (see [below](#equivalent-ipmitool-and-ipmiutil-commands)). If
   `ipmitool` or `ipmiutil` fails too, the problem is in the BMC configuration or the network:
   see [Preparing the BMC](preparing-the-bmc.html).
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
| An IPMI 1.5-only BMC, with `cipherSuite(id)` (`-C` with one ID) | Without the cipher suite discovery, the client does not ask the BMC which IPMI versions it supports, and an IPMI 1.5-only BMC never answers the RMCP+ Open Session. With the discovery, it fails at once ([below](#ipmiclientexception-3a-the-bmc-does-not-support-ipmi-2-0-2f-rmcp-2b)). |
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

The RAKP handshake (the login) failed, or the BMC refused to raise the session to the requested
privilege right after it (`80h`, `81h`: a BMC that is busy then is an `IpmiClientException`), and
the session could not be opened. The credentials are sent once;
`statusCode()` says why:

| `statusCode()` | Meaning |
| --- | --- |
| `null` (`... wrong password or BMC key (Authentication check failed)`) | The BMC's proof does not match the password: **wrong password** (or wrong [BMC key](configuration.html#bmc-key)). |
| `UnauthorizedName` | **Unknown user**, or a user not allowed to log in over the LAN channel. |
| `UnauthorizedRole`, `InvalidRole`, `InsufficentPrivilege` | The account is not allowed the requested [privilege level](configuration.html#privilege-level) on the LAN channel. |
| `Unknown` (`... refused the Operator privilege to <user>: the level exceeds the privilege limit of the channel or of the user (completion code 81h)`, or `80h`, the level is not available for this user) | The login worked, but the account, the LAN channel or the cipher suite (`Cipher Suite Priv Max` in `ipmitool lan print 1`) is limited to a lower level, and the BMC refused to raise the session (Set Session Privilege Level). Request a lower [privilege](configuration.html#privilege-level) (`-L` with `ipmicli`), or allow the level to the account. |
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

The BMC refused a command at the session's privilege level, which the client requests at login
and sets right after it: the command needs a higher level than the requested one. The default
**User** level is enough for everything the client reads; `send()` of a control command (Chassis Control, chassis
identify) needs `privilege(PrivilegeLevel.Operator)` on the builder, and Serial over LAN
`Administrator` on most BMCs; `privilege(PrivilegeLevel.MaximumAvailable)` raises the session
on that `D4h`, as far as the account allows
([Raising the session on demand](configuration.html#raising-the-session-on-demand)). If a BMC
refuses a read command at the User level, check:

* the privilege of the account on the LAN channel (`ipmitool channel getaccess 1 <user id>`),
* the maximum privilege of the cipher suite in use (`Cipher Suite Priv Max` in
  `ipmitool lan print 1`).

When commands that worked earlier in the same session start failing with `D4h`, the session was
revoked, and sending the command again in the same session does not help. On HP iLO 5, this is
what happens when another client deletes the sessions, for example a management tool logged in
as an administrator; the revocation shows at a whole minute of the session's age (60 s, 120 s,
...). Close the client and open a new one, or wait for the next operation: a session whose
messages get no reply is reopened, but one that answers `D4h` is not.

## `IpmiClientException: The BMC ... does not support IPMI 2.0 / RMCP+ ...`

Before any RMCP+ request, the client sends Get Channel Authentication Capabilities in the IPMI
v1.5 format, which every BMC answers (IPMI 2.0, section 13.13), and its reply reports no IPMI v2.0
connections (table 22-15): the BMC is IPMI 1.5-only (MD2, MD5 or straight password
authentication), or IPMI 2.0 is disabled on its LAN channel. The client only opens RMCP+
sessions ([What this client needs](preparing-the-bmc.html#what-this-client-needs)), like
`ipmitool -I lanplus`, which refuses such a BMC too ("This BMC does not support IPMI v2 /
RMCP+"). `ipmicli probe` exits with 70 the same way.

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
| `Fru.problems()` says `Cannot read FRU <id>` or `Cannot decode FRU <id>` | The BMC refused Get FRU Inventory Area Info for that FRU, or its data is neither in the IPMI FRU format nor the SPD data of a DDR3, DDR4 or DDR5 memory module nor the processor information ROM of an Intel Xeon processor (the SPD data of a DDR2 or LPDDR module, and a processor information ROM of another data format revision than 7, are reported as not decoded). The other FRUs are still returned. |
| `Fru.problems()` says `The <area> info area at offset <n> is truncated` | The read stopped before the end of that area (see above): the complete areas of the FRU are still returned. |
| `Sensor.problem()` says `The sensor belongs to system software` | The record names system software, the BIOS for instance, as the owner of the sensor: no controller holds its reading ([Sensor owners](sensors.html#sensor-owners)). `ipmiutil` shows it as not available. |
| `Sensor.problem()` says `... through the BMC to the controller at ... failed` or `No reply from the controller at ...` | The sensor belongs to another controller than the BMC, which the BMC forwards the request to with Send Message. `D4h`: the session needs a higher privilege for Send Message, Operator or, on some BMCs, Administrator (`privilege(PrivilegeLevel.MaximumAvailable)` raises it there when the account has the level); `83h` (NAK on write): no controller answered at that address; no reply: the controller is absent or does not answer ([Sensor owners](sensors.html#sensor-owners)). |
| A sensor known to `ipmitool` is not returned | Only Full and Compact sensor records are read, and shared Compact records are not expanded ([#100](https://github.com/metricshub/ipmi-java/issues/100)). A sensor whose reading the BMC flags as unavailable or not scanned (`ipmitool` shows `na` or `disabled`) is returned without reading or states, with `problem()` set. |
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
| `sel().execute()` | `sel info`, `sel elist` | `sel` |
| `sol().open()` | `sol activate` | `sol -a` |

The state descriptions of [`Sensor.states()`](sensors.html#states) follow the wording of
`ipmiutil`.

> [!NOTE]
> `ipmiutil sensor` reads the sensors with Get Device SDR, which some BMCs refuse (`D4h`) at any
> privilege level, while this library reads the SDR repository with Get SDR. A failure of
> `ipmiutil sensor` alone does not mean the library will fail.
