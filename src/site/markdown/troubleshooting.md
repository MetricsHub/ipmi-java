keywords: troubleshooting, timeout, no reply, login, authentication, unauthorized name, insufficient privilege, d4h, 0xd4, set session privilege level, session slots, cipher suite, ipmi 1.5, rmcp+, serial over lan, ipmitool, ipmiutil, ipmicli, probe, bug report
description: Diagnose the usual failures of the IPMI Java Client — no reply, authentication errors, insufficient privilege, missing sensors or FRUs — compare with ipmitool and ipmiutil, and report a problem.

# Troubleshooting

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## First steps

1. **Run `ipmicli probe`** from the same machine ([Command-Line Client](cli.html)). It needs no
   credentials and opens no session. It says whether the BMC answers, whether it requires a BMC
   key, and which cipher suites it offers, marking those the library implements and the one it
   would choose. In code: `capabilities()`.
2. **Run `ipmicli -v chassis`** with the account. This one command tests the credentials, the
   privilege level and the cipher suite in a few hundred milliseconds. Its message and its
   [exit code](cli.html#exit-codes) name the cause: 77 authentication or privilege, 69
   unreachable, 124 timeout, 70 other IPMI failures. In code: `chassisStatus()`.
3. **Check the BMC from the same machine with another tool**, with the same account, privilege
   level and cipher suite (see [below](#equivalent-ipmitool-and-ipmiutil-commands)). If
   `ipmitool` or `ipmiutil` fails too, the problem is in the BMC configuration or the network:
   see [Preparing the BMC](preparing-the-bmc.html).
4. **Act on the exception type**: the library never logs. Every failure of an operation is an
   [`IpmiClientException`](timeouts-and-errors.html#exceptions) whose subclass and message say
   what went wrong, and every problem that did not fail the operation is in its result
   ([Problems that do not fail the operation](timeouts-and-errors.html#problems-that-do-not-fail-the-operation)).
   An `IllegalStateException` means that the client was built without `credentials()` (only
   `capabilities()` works then) or is closed, or, from a `PowerCycle` of `chassisControl()`, that
   the system is off.

## `IpmiConnectionException`

The session could not be established:

* `Unknown host <host>`: the host name does not resolve on this machine.
* `Cannot connect to <host>:<port>: ...`: the operating system could not send the session setup
  to the BMC, for example because no network route leads to it.
* `No reply from <host>:<port> to the session setup after 4 tries: ...`: the BMC did not answer
  the session setup after all the tries (4 tries of 5 s by default, about 20 s). The usual
  causes:

| Cause | Check |
| --- | --- |
| Wrong address: the server's operating system instead of its BMC | The BMC has its own IP address (`ipmitool lan print 1` on the server). |
| IPMI over LAN disabled on the BMC | [Enabling IPMI over LAN](preparing-the-bmc.html#enabling-ipmi-over-lan) |
| UDP port 623 filtered | [Firewall](preparing-the-bmc.html#firewall) |
| An IPMI 1.5-only BMC, with `cipherSuite(id)` (`-C` with one ID) | Without the cipher suite discovery, the client does not ask the BMC which IPMI versions it supports, and an IPMI 1.5-only BMC never answers the RMCP+ Open Session. With the discovery, it fails at once ([below](#the-bmc-does-not-support-ipmi-2-0)). |
| Several sessions to the same BMC at the same time | BMCs drop replies under concurrent sessions: use one client per BMC, which [serializes its operations](configuration.html#thread-safety). |

## `IpmiTimeoutException`

The message says which wait ran out:

| Message | Cause | What to do |
| --- | --- | --- |
| `The <operation> operation on <host> did not complete within PT2M` | The [deadline](configuration.html#timeout) of the operation expired: a large SDR repository or many FRUs on a slow BMC, a network that loses many replies, or a deadline shorter than the handshake tries. The client drops the session when the operation was already running. | Raise the timeout (2 minutes by default). |
| `No reply from <host> to <command> after 4 tries: the session is lost` | A message of the session got no reply: the BMC rebooted or expired the session, or drops replies under load. | The next operation opens a new session. A read operation has already run again once on a new session before failing ([Session recovery](timeouts-and-errors.html#session-recovery)); `send()` and `raw()` are not run again. |
| `No reply from <host> to ChassisControl, which is sent once and not again: ...` (or `ChassisIdentify`) | `chassisControl()`, `chassisIdentify()` or `chassisIdentifyForever()` got no reply. The command is not sent again, as the BMC may have carried it out, and the session is kept. | Read `chassisStatus()` to see whether the BMC carried it out ([Controlling the power](chassis-status.html#controlling-the-power)). |
| `No reply from the controller behind <host> to SendMessage after 4 tries: ...` | A request sent with `send(new SendMessage<>(...))` was forwarded by the BMC, but the controller behind it did not answer. The session is kept. | Check the channel and the address of the controller ([Commands for another controller](low-level-api.html#commands-for-another-controller)). |

A request whose reply is lost is sent again after the
[message timeout](timeouts-and-errors.html#message-timeout-and-retries), up to `retries` times; an
operation fails only when every try in a row gets no reply. The control commands of
`chassisControl()`, `chassisIdentify()` and `chassisIdentifyForever()` are the exception: they are
sent once (third row above).

## `IpmiAuthenticationException`

The BMC refused the login (the RAKP handshake), or refused to raise the new session to the
requested privilege right after it (Set Session Privilege Level answered `80h`, `81h` or `D4h`):
the session is not opened. A BMC that answers Set Session Privilege Level with another error, a
busy one for instance, fails with an `IpmiClientException` instead
(`The BMC ... could not set the session to the ... privilege: ...`). A login the BMC refuses is
not tried again, as sending the credentials again can lock the account out: only a login whose
step got no reply, or that the BMC answered with a transient completion code (node busy, out of
resources), is started again, from Open Session. With `MaximumAvailable`, a role the BMC refuses
(`UnauthorizedRole`, `InvalidRole`) is requested one level lower: Administrator, then Operator,
then User. `statusCode()` says why:

| `statusCode()` | Meaning |
| --- | --- |
| `null`, message ending `(Authentication check failed)` | The BMC's proof in RAKP Message 2 does not match the password: **wrong password**. |
| `null`, message ending `(Integrity check failed)` | The password matched, but the integrity check value of RAKP Message 4 does not: **wrong [BMC key](configuration.html#bmc-key)**, a BMC key missing for a BMC that requires one (`ipmicli probe` says whether it does), or one given to a BMC that has none. |
| `UnauthorizedName` | **Unknown user**, or a user not allowed to log in over the LAN channel. |
| `UnauthorizedRole`, `InvalidRole`, `InsufficientPrivilege` | The account is not allowed the requested [privilege level](configuration.html#privilege-level) on the LAN channel. |
| `InsufficientResourcesForRole` | The BMC has no free session for the requested privilege level: close the sessions that other tools or unclosed clients hold, or request a lower [privilege](configuration.html#privilege-level). |
| `InvalidNameLength` | The BMC does not accept a user name of that length: check the user name. |
| `UnauthorizedGuid` | A status the specification gives to the remote console, not to the BMC: the BMC does not follow the handshake. Compare with `ipmitool`. |
| `Unknown`, message ending `(completion code 81h)` | The login worked, but the account, the LAN channel or the cipher suite (`Cipher Suite Priv Max` in `ipmitool lan print 1`) is limited to a lower level, and the BMC refused to raise the session (`... refused the Operator privilege to <user>: the level exceeds the privilege limit of the channel or of the user`). Request a lower [privilege](configuration.html#privilege-level) (`-L` with `ipmicli`), or allow the level to the account. |
| `Unknown`, message ending `(completion code 80h)` | The same, for a level the BMC does not make available to this user (`the level is not available for this user`). |
| `InvalidIntegrityCheckValue` | The BMC rejected the integrity check value of RAKP Message 3: wrong password or BMC key on a BMC that checks it first. |

An `IllegalArgumentException` from `build()` (`Password is too long. ...`, `Username is too
long. ...`, `BMC key is too long. ...`) means that the password or the BMC key is longer than the
20 bytes a BMC stores, or the user name longer than 16 bytes: the BMC cannot have it, and nothing
is sent to it.

An `IpmiClientException` that says
`Cannot open the session with <host>: ... does not match the request` (`Open Session Response`,
`RAKP Message 2`, `RAKP Message 4`) means that a handshake reply of the BMC is for another
session, or confirms other algorithms than the requested ones. The client does not try the
handshake again, as the BMC would answer the same.

Check the account with `ipmitool -I lanplus ... -L USER -v chassis status`. Without `-v`,
`ipmitool` only prints `Error: Unable to establish IPMI v2 / RMCP+ session`. With it, `ipmitool`
also prints `> RAKP 2 HMAC is invalid` for a wrong password and
`RAKP 2 message indicates an error : unauthorized name` for an unknown user.

## The BMC refuses the session

The operation fails with `IpmiClientException: The BMC ... refused the session: ...`: the BMC
answered Open Session or a RAKP message with an error status that is not about the account
(IPMI 2.0 table 13-15).

| Message ending | Cause |
| --- | --- |
| `Insufficient resources to create a session.` (`01h`) | Every session slot of the BMC is in use, by other tools or by clients that were not closed. The login is started again from Open Session up to `retries` times before it fails. Close the clients you no longer need: BMCs typically expire an idle session after 60 seconds. |
| `No Cipher Suite match with proposed security algorithms.` (`11h`), `Invalid authentication algorithm.` (`04h`), `Invalid integrity algorithm.` (`05h`) | The BMC refuses the cipher suite, or does not allow it at the requested privilege (`Cipher Suite Priv Max` in `ipmitool lan print 1`): see below the table. |

When the BMC refuses the cipher suite:

* with `cipherSuite(id)` (`-C` with one ID), use `cipherSuites()` instead, which picks a suite the
  BMC offers;
* for a suite the BMC offers, request a lower [privilege](configuration.html#privilege-level), or
  raise the maximum privilege of that suite on the BMC;
* some BMCs list a suite they refuse at Open Session: a Lenovo XClarity Controller (XCC) lists
  suite 12 and a Cisco IMC suite 11, and `ipmiutil -J <id>` gets the same refusal. The client
  does not try another suite: enable that suite on the BMC, or list with `cipherSuites()` the
  suites the BMC accepts
  ([How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)).

## `IpmiCompletionCodeException` with `InsufficientPrivilege` (`D4h`)

`InsufficientPrivilege` is the `CompletionCode` constant of `D4h`. The BMC refused a command
at the session's privilege level, which the client requests at login and sets right after it: the
command needs a higher level. The levels the operations need
([Privilege level](configuration.html#privilege-level)):

* **User** (the default): the reads of the BMC itself: `deviceId()`, `chassisStatus()`, and the
  sensors, FRUs and SEL of the BMC.
* **Operator**: `chassisControl()`, `chassisIdentify()` and `chassisIdentifyForever()`, and a
  control command (Chassis Control, Chassis Identify) sent with `send()`; `raw()` returns the
  `D4h` in its reply instead of throwing it. Also the sensors and FRUs of other controllers, which
  the BMC reaches with Send Message: some BMCs need Administrator for it, others accept User. Their
  `D4h` is in `Sensor.problem()` or `Fru.problems()`, not thrown
  ([Sensor owners](sensors.html#sensor-owners)).
* **Administrator**: Serial over LAN, on most BMCs.

`privilege(PrivilegeLevel.MaximumAvailable)` (`-L auto` with `ipmicli`) raises the session on that
`D4h`, as far as the account allows
([Raising the session on demand](configuration.html#raising-the-session-on-demand)). If a BMC
refuses a read command at the User level, check:

* the privilege of the account on the LAN channel (`ipmitool channel getaccess 1 <user id>`),
* the maximum privilege of the cipher suite in use (`Cipher Suite Priv Max` in
  `ipmitool lan print 1`).

When commands that worked earlier in the same session start failing with `D4h`, the BMC revoked
the session. It then answers `D4h` to every command of the session, Set Session Privilege Level
included, and sending a command again does not help. On HPE iLO 5, this is what happens when
another client deletes the sessions, for example a management tool logged in as an administrator;
the revocation shows at a whole minute of the session's age (60 s, 120 s, ...). The client reopens
a session whose messages get no reply, but not one that answers `D4h`: close the client and build
a new one.

## The BMC does not support IPMI 2.0

The operation fails with `IpmiClientException: The BMC ... does not support IPMI 2.0 / RMCP+ ...`.
The BMC's reply to Get Channel Authentication Capabilities reports no IPMI v2.0 connections
(IPMI 2.0 table 22-15): the BMC is IPMI 1.5-only (MD2, MD5 or straight password authentication),
or IPMI 2.0 is disabled on its LAN channel. The client sends this request in the IPMI v1.5 format,
which every BMC answers (IPMI 2.0 section 13.13), before any RMCP+ request whenever it discovers
the cipher suites: by default, with `cipherSuites()`, and in `capabilities()`. With
`cipherSuite(id)`, it skips this check, and such a BMC fails with `IpmiConnectionException`
instead ([above](#ipmiconnectionexception)).

The client only opens RMCP+ sessions
([What this client needs](preparing-the-bmc.html#what-this-client-needs)), like
`ipmitool -I lanplus`, which refuses such a BMC too: it prints
`Error: Unable to establish IPMI v2 / RMCP+ session`, and with `-v` also
`This BMC does not support IPMI v2 / RMCP+`. `ipmicli probe` exits with 70 the same way.

## No cipher suite in common with the BMC

The operation fails with `IpmiClientException: The BMC ... offers none of the cipher suites ...`,
which lists the preference of the client and the suites the BMC offers. The client only opens
sessions with a cipher suite whose messages are signed
([How `IpmiClient` chooses the suite](preparing-the-bmc.html#how-ipmiclient-chooses-the-suite)).
The default preference, 17, 3, 8, 16, 2, 7, 12, 11, already holds every such suite the library
implements: enable suite 17 or 3 on the BMC (in its web interface, or with
`ipmitool lan set 1 cipher_privs`). When `cipherSuites()` on the builder (`-C` with several IDs in
`ipmicli`) narrowed the preference, add to it the suites of these eight that the BMC offers. A
BMC that lists the suite the client chose but refuses it at Open Session fails with
`refused the session: No Cipher Suite match ...` instead
([The BMC refuses the session](#the-bmc-refuses-the-session)).

The library does not implement xRC4, the confidentiality algorithm of suites 4, 5, 9, 10, 13, 14,
18 and 19: `ipmicli probe` marks them `not implemented`, and their `isSupported()` is `false`.
With the [low-level API](low-level-api.html#choosing-the-cipher-suite), such a suite fails when
the session is opened, with an `IllegalArgumentException`
(`Confidentiality algorithm XRC4-128 is not implemented.`, `XRC4-40` for suites 5, 10, 14 and
19): choose a suite whose `isSupported()` is `true`.

## Sensors or FRUs are missing

| Symptom | Cause |
| --- | --- |
| `skippedRecords()` lists a record | A record that cannot be decoded is skipped with its reason; the other sensors are still returned. [SDR records](supported-commands.html#sdr-records) lists what is decoded. |
| `Sensor.problem()` says `Get Sensor Reading failed: ...` | The BMC refused the Get Sensor Reading of that sensor with an error completion code: the sensor is returned without reading or states, and the other sensors are still returned. With `D4h`, the BMC may have revoked the session (see the `D4h` section above). |
| `Sensor.problem()` says `Get Sensor Reading Factors failed: ...` | The BMC refused the factors of the reading of a non-linear sensor: `rawReading()`, `status()` and `states()` are still returned, but `reading()` is empty ([Readings](sensors.html#readings)). |
| `Sensor.problem()` says `The reading is not valid yet: the sensor is being re-armed or initialized` or `Sensor scanning is disabled` | The BMC flags the reading as unavailable or the sensor as not scanned (`ipmitool` shows `na` or `disabled`): the sensor is returned with the status `UNAVAILABLE`, without reading or states. |
| `Fru.problems()` says `Cannot read FRU <id>: ...` or `FRU <id> is truncated at offset <n>: ...` with `Requested Sensor, data, or record not present` (`CBh`) | The FRU is declared in the SDR repository but not present, for example an empty power supply bay or DIMM slot. Usually harmless: the reading stops there, and the areas read before, if any, are decoded. |
| `Fru.problems()` says `Cannot read FRU <id>: No data returned` or `FRU <id> is truncated at offset <n>: No data returned` | The controller returned no data at an offset within the size it declared in Get FRU Inventory Area Info: the reading stops there and the areas read so far are decoded. |
| `Fru.problems()` says `Cannot read FRU <id>: ...` | The BMC refused Get FRU Inventory Area Info or the first Read FRU Data of that FRU, or malformed its reply: nothing of that FRU is decoded. The other FRUs are still returned. |
| `Fru.problems()` says `Cannot decode FRU <id>: ...` | The data read is not in a format the library decodes, or it is corrupt or truncated. The library decodes the IPMI FRU format (a common header with a valid checksum), the SPD data of a DDR3, DDR4 or DDR5 memory module, and the Processor Information ROM (data format revision 7) of an Intel Xeon processor. The SPD data of another DRAM type (DDR, DDR2 and its FB-DIMM variants, DDR4E, LPDDR3 to LPDDR5X, DDR5 NVDIMM-P) or that ends before the end of the part number, and a Processor Information ROM of another revision, are reported this way ([Memory modules](fru-inventory.html#memory-modules)). The other FRUs are still returned. |
| `Fru.problems()` says `The <area> info area at offset <n> is truncated` | The read stopped before the end of that area (see above): the complete areas of the FRU are still returned ([Incomplete FRUs](fru-inventory.html#incomplete-frus) lists every message). |
| `Fru.problems()` says `The multirecord ...` or `Cannot decode the multirecord at offset <n>: ...` | The MultiRecord area is cut short or has a corrupt record header, or one of its records cannot be decoded: the records before it, or around that record, are still returned. |
| `Sensor.problem()` says `The sensor belongs to system software` | The record names system software, the BIOS for instance, as the owner of the sensor: no controller holds its reading ([Sensor owners](sensors.html#sensor-owners)). `ipmiutil` shows it as not available. |
| `Sensor.problem()` says `Get Sensor Reading through the BMC to the controller at ... failed: ... (completion code D4h)`; `Fru.problems()` says `Cannot read FRU <id> from the controller at ...: ...` with `(completion code D4h)` | The sensor or FRU belongs to a controller other than the BMC, which the BMC forwards the request to with Send Message, and Send Message needs a higher privilege than the session has: Operator or, on some BMCs, Administrator. `privilege(PrivilegeLevel.MaximumAvailable)` raises the session there when the account has the level ([Sensor owners](sensors.html#sensor-owners)). |
| `Sensor.problem()` or `Fru.problems()` names `the controller at ...` and says `NAK on write: no controller acknowledged the address (completion code 83h)` | No controller answered at that address on the bus behind the BMC. |
| `Sensor.problem()` says `No reply from the controller at ... through the BMC`; `Fru.problems()` says `Cannot read FRU <id> from the controller at ...: No reply through the BMC` | The controller is absent or does not answer. The sensors and FRUs of the other controllers are still read ([Sensor owners](sensors.html#sensor-owners)). |
| A sensor known to `ipmitool` is not returned | Only the sensors of Full and Compact sensor records are returned: an Event-Only record has no reading ([How the sensors are read](sensors.html#how-the-sensors-are-read)). A Compact record shared by several sensors is returned as one sensor per instance, where `ipmitool` shows one line ([Shared records](sensors.html#shared-records)). |
| Negative processor temperatures (`CPU1 DTS = -44.0`) | Not an error: Intel *Digital Thermal Sensor* readings are the margin below the maximum junction temperature. |

## Collecting is slow

* **The SDR repository** is walked by the first `sensors()` or `frus()` of a client, with at least
  one Get SDR per record, then cached while the BMC reports it unchanged
  ([SDR cache](configuration.html#sdr-cache)): reuse the client between collections instead of
  building a new one each time.
* **FRUs**: a BMC that returns at most 16 bytes per Read FRU Data (Dell iDRAC 8, Cisco IMC) costs
  one round trip per 16 bytes of FRU, a few seconds per large FRU
  ([Read size](fru-inventory.html#read-size)).
* **A controller behind the BMC that does not answer** costs all the tries of one message (about
  20 s with the defaults) once per `sensors()` or `frus()` call; its other sensors and FRUs are
  then skipped at once ([Sensor owners](sensors.html#sensor-owners)).
* **Lost replies** stall an operation until the message timeout: shorten it
  ([Timeouts and Errors](timeouts-and-errors.html#message-timeout-and-retries)).
* **Sensors you do not need** each cost a Get Sensor Reading round trip unless you exclude them
  with [`types()` or `names()`](sensors.html#selecting-sensors).

## Opening a Serial over LAN console fails

| Failure | Cause |
| --- | --- |
| `IpmiClientException: Cannot open the SOL console of <host>: The SOL payload is in use by another session: instance 1 by session ...` | Another session that the BMC still has holds the payload; the message gives its user ID and privilege. Close that console first; for a console killed without being closed, try again once the BMC has expired its session (typically after 60 seconds). The client deactivates a payload held by a session the BMC no longer has, and activates it again without an error ([With `IpmiClient`](serial-over-lan.html#with-ipmiclient)). |
| `IpmiCompletionCodeException` with `D4h`: `ActivateSolPayload failed: ...` | The session needs the privilege the BMC requires for SOL (*Privilege Level* in `ipmitool sol info 1`), Administrator on most BMCs: `privilege(PrivilegeLevel.Administrator)` on the builder, or `MaximumAvailable`, which raises the session as far as the account allows ([Prerequisites](serial-over-lan.html#prerequisites)). |
| `IpmiCompletionCodeException` with `81h`: `ActivateSolPayload failed: Payload type is disabled ...` | The BMC does not allow the SOL payload to be activated: check that SOL is enabled (`ipmitool sol set enabled true 1`) and that the SOL payload is enabled for the account (`ipmitool sol payload enable 1 <user id>`) ([Prerequisites](serial-over-lan.html#prerequisites)). |
| `IpmiCompletionCodeException` with `84h`: `ActivateSolPayload failed: Cannot activate the payload without encryption ...` | The BMC requires encryption (`force-encryption`) and the session uses a cipher suite without it: restrict the client to `cipherSuites(17, 3, 8, 12)` ([Prerequisites](serial-over-lan.html#prerequisites)). |

## Closing a Serial over LAN console fails with `Message timed out`

`SerialOverLan.close()` throws `IOException` caused by `ConnectionException: Message timed out`
when the BMC does not answer Deactivate Payload within the tries of the
[message timeout](timeouts-and-errors.html#message-timeout-and-retries); the
[`SolConsole`](serial-over-lan.html#with-ipmiclient) of the client swallows it and reports the
console deactivated. The console still closes the sessions it opened.

On a Dell iDRAC whose server did not read its serial port, the BMC answered nothing, from any
session, for about 30 s after characters were written to the console. Open a new console or
session after that delay.

## Equivalent `ipmitool` and `ipmiutil` commands

With `ipmitool`, add `-I lanplus -H <bmc> -U <user> -P <password> -L USER -C <suite>`; with
`ipmiutil`, add `-N <bmc> -U <user> -P <password> -F lan2 -J <suite> -V 2`. Use `-L OPERATOR`
(`-V 3`) for `chassisControl()`, `chassisIdentify()` and the sensors and FRUs of controllers other
than the BMC ([Sensor owners](sensors.html#sensor-owners),
[FRUs of other controllers](fru-inventory.html#frus-of-other-controllers); `-L ADMINISTRATOR`
(`-V 4`) on an AMI-based GIGABYTE BMC), and `-L ADMINISTRATOR` (`-V 4`) for `sol()`. The
[Command-Line Client](cli.html#ipmitool-equivalents) lists the `ipmitool` equivalents of each
`ipmicli` subcommand and option.

| Library | `ipmitool` | `ipmiutil` |
| --- | --- | --- |
| `capabilities()` | `channel getciphers ipmi` | none |
| `deviceId()` | `mc info` | `health` |
| `chassisStatus()` | `chassis status` | `health` |
| `chassisControl(PowerCommand.PowerUp)` (`PowerDown`, `PowerCycle`, `HardReset`, `SoftShutdown`, `DiagnosticInterrupt`) | `chassis power on` (`off`, `cycle`, `reset`, `soft`, `diag`) | `reset -u` (`-d`, `-c`, `-r`, `-D`, `-n`) |
| `chassisIdentify(15)`, `chassisIdentifyForever()` | `chassis identify 15`, `chassis identify force` | `alarms -i15`, `alarms -i255` |
| `frus().execute()` | `fru print` | `fru` |
| `sensors().execute()` | `sdr elist` | `sensor` |
| `sel().execute()` | `sel info`, `sel elist` | `sel` |
| `sol().open()` | `sol activate` | `sol -a` |
| `raw(0x06, 0x01).execute()` | `raw 0x06 0x01` | `cmd 00 20 18 01` (bus, slave address, then the network function and LUN in one byte) |

The state descriptions of [`Sensor.states()`](sensors.html#states) follow the wording of
`ipmiutil`, and of `ipmitool` for the few states `ipmiutil` does not name.

> [!NOTE]
> `ipmiutil sensor` reads the sensors with Get Device SDR, which some BMCs refuse (`D4h`) at any
> privilege level, while this library reads the SDR repository with Get SDR, and uses Get Device
> SDR only on a BMC without SDR repository
> ([How the sensors are read](sensors.html#how-the-sensors-are-read)). A failure of
> `ipmiutil sensor` alone does not mean the library will fail.

## Reporting a problem

Report a bug on the [issue tracker](https://github.com/metricshub/ipmi-java/issues). The library
does not log, so the report needs what you collect:

* the library version (`ipmicli --version`, or the version of the dependency) and the Java version,
* the BMC: its vendor and model, and the output of `ipmicli -h <bmc> -u <user> info`,
* the output of `ipmicli -h <bmc> probe`,
* the failing command run with `-v`, with its output and exit code, or, from code, the exception
  with its stack trace and its causes,
* for a value decoded wrongly, the bytes the BMC sent: `SelEntry.record().getRawData()` for a SEL
  entry, the reply of `ipmicli ... raw` for a command, `ipmitool fru read <id> <file>` for a FRU,
  `ipmitool sdr dump <file>` for the SDR repository.

`-v` prints the host name and the user name, never the password or the BMC key: mask the host
names, user names and serial numbers you do not want to publish.
