keywords: troubleshooting, timeout, illegal connection state, rakp1waiting, authentication check failed, insufficient privilege, 0xd4, ipmitool, ipmiutil, debug
description: Diagnose the usual failures of the IPMI Java Client — timeouts, authentication errors, insufficient privilege, missing sensors or FRUs, a JVM that does not exit — and compare with ipmitool and ipmiutil.

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

## `TimeoutException`, nothing collected

The BMC did not answer in time. With the default settings, this is the symptom of every network
or configuration problem, because the 5-minute per-message timeout is longer than the overall
timeout ([Timeouts and Errors](timeouts-and-errors.html)).

| Cause | Check |
| --- | --- |
| Wrong address: the server's operating system instead of its BMC | The BMC has its own IP address (`ipmitool lan print 1` on the server). |
| IPMI over LAN disabled on the BMC | [Enabling IPMI over LAN](preparing-the-bmc.html#enabling-ipmi-over-lan) |
| UDP port 623 filtered | [Firewall](preparing-the-bmc.html#firewall) |
| An IPMI 1.5-only BMC | Such BMCs never answer the RMCP+ Open Session request ([#91](https://github.com/metricshub/ipmi-java/issues/91)). |
| A lost UDP reply | Run the call again. With a [shorter per-message timeout](timeouts-and-errors.html#library-wide-defaults), lost replies are retried instead. |
| Several sessions to the same BMC at the same time | BMCs drop replies under concurrent sessions: query each BMC [from one thread at a time](configuration.html#thread-safety). |
| A large SDR repository or many FRUs on a slow BMC | Raise the [timeout](configuration.html#timeout): 120 s is a safe value. |

When the timeout expires, the interrupted session logs an `ERROR` with an `InterruptedException`
(`sleep interrupted`): its stack trace shows the step that was waiting. A wait in
`getAvailableCipherSuites` means the BMC never answered the very first request: the address, the
port or the firewall is wrong, or IPMI over LAN is disabled.

## `Illegal connection state: Rakp1Waiting`

The `ExecutionException` wraps
`ConnectionException: Illegal connection state: Rakp1Waiting`: the RAKP handshake (the login)
failed, and the session could not be opened. The actual reason is logged just before, at the
`ERROR` level ([#109](https://github.com/metricshub/ipmi-java/issues/109)):

| Logged | Cause |
| --- | --- |
| `IllegalArgumentException: Authentication check failed` | The BMC's proof does not match the password: **wrong password** (or wrong [BMC key](configuration.html#bmc-key)). |
| `IPMIException: Unauthorized name.` | **Unknown user**, or a user not allowed to log in over the LAN channel. |
| Another `IPMIException` (`Invalid role.`, ...) | The account is not allowed the User privilege level, or the BMC refused the cipher suite. |

Check the account with `ipmitool -I lanplus ... -L USER chassis status`: `ipmitool` reports
`RAKP 2 HMAC is invalid` for a wrong password and `unauthorized name` for an unknown user.

## `IPMIException: Insufficient privilege level` (`0xD4`)

The BMC refused a command at the session's privilege level. `IpmiClient` always uses the
**User** level, which the specification allows for every command it sends; if a BMC refuses
one, check:

* the privilege of the account on the LAN channel (`ipmitool channel getaccess 1 <user id>`),
* the maximum privilege of the cipher suite in use (`Cipher Suite Priv Max` in
  `ipmitool lan print 1`).

With the [low-level API](low-level-api.html#privilege-level), open the session with the level the
command needs (Operator for Chassis Control, Administrator for configuration commands).

## `... is not yet implemented.`

`IllegalArgumentException: Confidentiality algorithm XRC4-128 is not yet implemented.` (or
MD5-128 integrity): the cipher suite chosen for the session uses an algorithm the client does not
implement. See [How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)
to make it use suite 3 or 17.

## Sensors or FRUs are missing

| Symptom | Cause |
| --- | --- |
| `WARN Skipping SDR record ...` | A record that cannot be decoded is skipped; the other sensors are still returned. [SDR records](supported-commands.html#sdr-records) lists what is decoded. |
| `WARN Failed to read FRU <id> at offset <n> ... Requested Sensor, data, or record not present` | The FRU is declared in the SDR repository but not present, for example an empty power supply bay. Usually harmless. |
| `WARN Failed to decode FRU <id>` | The FRU data is not in the IPMI FRU format (for example the SPD data of a memory module, [#107](https://github.com/metricshub/ipmi-java/issues/107)). |
| A sensor known to `ipmitool` is not returned | Only Full and Compact sensor records of the BMC's own repository are read: sensors behind satellite controllers are not ([#84](https://github.com/metricshub/ipmi-java/issues/84)), and shared Compact records are not expanded ([#100](https://github.com/metricshub/ipmi-java/issues/100)). |
| A sensor reads `0.0` | The BMC flags the reading as unavailable, which is not checked yet ([#110](https://github.com/metricshub/ipmi-java/issues/110)). |
| Negative processor temperatures (`CPU1 DTS = -44.0`) | Not an error: Intel *Digital Thermal Sensor* readings are the margin below the maximum junction temperature. |

## The JVM does not exit

After a `TimeoutException`, some threads of the library may still run, and they are not daemon
threads ([#79](https://github.com/metricshub/ipmi-java/issues/79)). End command-line programs and
test harnesses with `System.exit()`.

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
