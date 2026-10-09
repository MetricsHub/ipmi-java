keywords: troubleshooting, timeout, command timed out, login, authentication check failed, unauthorized name, insufficient privilege, 0xd4, ipmitool, ipmiutil, debug
description: Diagnose the usual failures of the IPMI Java Client — timeouts, authentication errors, insufficient privilege, missing sensors or FRUs — and compare with ipmitool and ipmiutil.

# Troubleshooting

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## First steps

1. **Check the BMC from the same machine with another tool**, with the same account, privilege
   level and cipher suite (see [below](#equivalent-ipmitool-and-ipmiutil-commands)). If
   `ipmitool` or `ipmiutil` fails too, the problem is in the BMC configuration or the network:
   see [Preparing the BMC](preparing-the-bmc.html).
2. **Set the `org.metricshub.ipmi` logger to `DEBUG`** ([Logging](installation.html#logging)):
   the messages sent, the retries, the records skipped and the handshake errors are logged.
3. **Start with `getChassisStatus()`**: it is a single command, so it tests the network, the
   credentials and the cipher suite in a few hundred milliseconds.

## `Command timed out` or `TimeoutException`, nothing collected

The BMC did not answer in time. A BMC that never answers fails the session handshake after its
4 tries of 5 s: the `ExecutionException` wraps `ConnectionException: Command timed out`. A
`TimeoutException` means the whole call outlived the overall timeout
([Timeouts and Errors](timeouts-and-errors.html)).

| Cause | Check |
| --- | --- |
| Wrong address: the server's operating system instead of its BMC | The BMC has its own IP address (`ipmitool lan print 1` on the server). |
| IPMI over LAN disabled on the BMC | [Enabling IPMI over LAN](preparing-the-bmc.html#enabling-ipmi-over-lan) |
| UDP port 623 filtered | [Firewall](preparing-the-bmc.html#firewall) |
| An IPMI 1.5-only BMC | Such BMCs never answer the RMCP+ requests of the client, starting with Get Channel Cipher Suites. |
| A lost UDP reply | Retried after the [per-message timeout](timeouts-and-errors.html#per-message-timeout-and-retries); the call only fails when 4 tries in a row get no reply. |
| Several sessions to the same BMC at the same time | BMCs drop replies under concurrent sessions: query each BMC [from one thread at a time](configuration.html#thread-safety). |
| A large SDR repository or many FRUs on a slow BMC | Raise the [timeout](configuration.html#timeout): 120 s is a safe value. |

The stack trace of the `Command timed out` shows the step that was waiting. A failure in
`getAvailableCipherSuites` means the BMC never answered the very first request: the address, the
port or the firewall is wrong, or IPMI over LAN is disabled.

## The login fails

The RAKP handshake (the login) failed and the session could not be opened. The credentials are
sent once; the `ExecutionException` wraps the reason:

| Cause | Meaning |
| --- | --- |
| `IllegalArgumentException: Authentication check failed` | The BMC's proof does not match the password: **wrong password** (or wrong [BMC key](configuration.html#bmc-key)). |
| `IPMIException: Unauthorized name.` | **Unknown user**, or a user not allowed to log in over the LAN channel. |
| Another `IPMIException` (`Invalid role.`, ...) | The account is not allowed the User privilege level, or the BMC refused the cipher suite. |
| `IllegalArgumentException: Password is too long. ...` (or `Username is too long. ...`, `BMC key is too long. ...`) | The password or the BMC key is longer than the 20 bytes a BMC stores, or the user name longer than 16 bytes: the BMC cannot have it. Nothing is sent to the BMC. |
| `IllegalArgumentException: Open Session Response does not match the request` (or `RAKP Message 2`, `RAKP Message 4`) | A handshake reply of the BMC is for another session, or confirms other algorithms than the requested ones. It is not sent again. |

Check the account with `ipmitool -I lanplus ... -L USER chassis status`: `ipmitool` reports
`RAKP 2 HMAC is invalid` for a wrong password and `unauthorized name` for an unknown user.

## `IPMIException: Cannot execute command due to insufficient privilege level ...` (`0xD4`)

The BMC refused a command at the session's privilege level. `IpmiClient` always uses the
**User** level, which the specification allows for every command it sends; if a BMC refuses
one, check:

* the privilege of the account on the LAN channel (`ipmitool channel getaccess 1 <user id>`),
* the maximum privilege of the cipher suite in use (`Cipher Suite Priv Max` in
  `ipmitool lan print 1`).

With the [low-level API](low-level-api.html#privilege-level), open the session with the level the
command needs (Operator for Chassis Control, Administrator for configuration commands).

When commands that worked earlier in the same session start failing with `0xD4`, the session was
revoked, and sending the command again in the same session does not help. On HP iLO 5, this is
what happens when another client deletes the sessions, for example a management tool logged in
as an administrator; the revocation shows at a whole minute of the session's age (60 s, 120 s,
...). Each `IpmiClient` call opens its own session, so the next call works again.

## `The BMC offers none of the cipher suites 17, 3, 8, 16, 2 and 7`

`IpmiClient` only opens sessions with a cipher suite whose messages are signed
([How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)), and
the message lists the suites the BMC offers. Enable suite 17 or 3 on the BMC (in its web
interface, or with `ipmitool lan set 1 cipher_privs`).

With the [low-level API](low-level-api.html#choosing-the-cipher-suite), a suite that uses an
algorithm the client does not implement fails with
`IllegalArgumentException: Confidentiality algorithm XRC4-128 is not yet implemented.` (or
MD5-128 integrity): choose a suite whose `isSupported()` is `true`.

## Sensors or FRUs are missing

| Symptom | Cause |
| --- | --- |
| `WARN Skipping SDR record ...` | A record that cannot be decoded is skipped; the other sensors are still returned. [SDR records](supported-commands.html#sdr-records) lists what is decoded. |
| `WARN Failed to read sensor <n> (<name>) on <host>: ...` | The BMC refused the Get Sensor Reading of that sensor with an error completion code: the sensor is returned without reading or states, and the other sensors are still returned. With the `0xD4` message, the BMC may have revoked the session (see the `0xD4` section above). |
| `WARN Failed to read FRU <id> at offset <n>, the FRU data is truncated there: Requested Sensor, data, or record not present` | The FRU is declared in the SDR repository but not present, for example an empty power supply bay. Usually harmless: the reading stops there and the areas read so far are decoded. |
| `WARN Failed to read FRU <id>` | The BMC did not answer Get FRU Inventory Area Info for that FRU, or its data is not in the IPMI FRU format (for example the SPD data of a memory module, [#107](https://github.com/metricshub/ipmi-java/issues/107)). The other FRUs are still returned. |
| `WARN The <area> info area at offset <n> is truncated: skipped` | The read stopped before the end of that area (see above): the complete areas of the FRU are still returned. |
| A sensor known to `ipmitool` is not returned | Only Full and Compact sensor records of the BMC's own repository are read: sensors behind satellite controllers are not ([#84](https://github.com/metricshub/ipmi-java/issues/84)), and shared Compact records are not expanded ([#100](https://github.com/metricshub/ipmi-java/issues/100)). A sensor whose reading the BMC flags as unavailable or not scanned (`ipmitool` shows `na` or `disabled`) is returned without reading or states. |
| Negative processor temperatures (`CPU1 DTS = -44.0`) | Not an error: Intel *Digital Thermal Sensor* readings are the margin below the maximum junction temperature. |

## Collecting is slow

* **FRUs**: each FRU is read 16 bytes at a time, one round trip per chunk: a few seconds per FRU
  on some BMCs ([#102](https://github.com/metricshub/ipmi-java/issues/102)).
* **`getFrusAndSensorsAsStringResult()`** opens two sessions and walks the SDR repository twice
  ([#102](https://github.com/metricshub/ipmi-java/issues/102)).
* **Lost replies** stall a call until the per-message timeout: shorten it
  ([Timeouts and Errors](timeouts-and-errors.html#library-wide-defaults)).

## Equivalent `ipmitool` and `ipmiutil` commands

With `ipmitool`, add `-I lanplus -H <bmc> -U <user> -P <password> -L USER -C <suite>`; with
`ipmiutil`, add `-N <bmc> -U <user> -P <password> -F lan2 -J <suite> -V 2`.

| Library | `ipmitool` | `ipmiutil` |
| --- | --- | --- |
| `IpmiClient.getChassisStatus()` | `chassis status` | `health` |
| `IpmiClient.getFrus()` | `fru print` | `fru` |
| `IpmiClient.getSensors()` | `sdr elist` | `sensor` |
| Cipher suites offered by the BMC | `channel getciphers ipmi` | |
| Get SEL Info / Get SEL Entry | `sel info`, `sel elist` | `sel` |

The device types and state descriptions of the [text output](sensors.html#text-output-format)
follow the wording of `ipmiutil`.

> [!NOTE]
> `ipmiutil sensor` reads the sensors with Get Device SDR, which some BMCs refuse (`0xD4`) at any
> privilege level, while this library reads the SDR repository with Get SDR. A failure of
> `ipmiutil sensor` alone does not mean the library will fail.
