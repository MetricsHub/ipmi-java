keywords: cli, command line, ipmicli, standalone, jar, probe, chassis, power, sensors, fru, sol, serial over lan, json, exit codes, ipmitool
description: Manual of the ipmicli command-line client of the IPMI Java Client - subcommands, connection options, passwords and BMC key, text and JSON output, the interactive Serial over LAN console, exit codes, and the ipmitool equivalents.

# Command-Line Client

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Every release ships a self-contained executable jar that bundles the library and a command-line
client: download `${project.artifactId}-${project.version}-standalone.jar` from the
[latest release](https://github.com/metricshub/ipmi-java/releases/latest) and run it with Java 8
or later. This page is its manual.

```text
java -jar ${project.artifactId}-${project.version}-standalone.jar [options] <subcommand> [subcommand options]
```

The command is called **`ipmicli`** in this page and in its own usage. Give it that name with a
shell alias (`alias ipmicli='java -jar /path/to/${project.artifactId}-${project.version}-standalone.jar'`,
or a `doskey ipmicli=java -jar ... $*` macro on Windows) until the one-command installer
([#163](https://github.com/metricshub/ipmi-java/issues/163)) puts a launcher on the `PATH`. The
jar needs no other file: the library has no dependency.

The CLI is a thin layer over the [`IpmiClient`](index.html#quick-start) API: every subcommand is
one operation of the client, with the same defaults, the same
[session handling](timeouts-and-errors.html#session-recovery) and the same failures, reported
as exit codes. It is meant to feel familiar to `ipmitool` users: see the
[ipmitool equivalents](#ipmitool-equivalents).

## Synopsis

```text
ipmicli -h <host> probe [--json]
ipmicli -h <host> -u <user> [options] chassis [--json]
ipmicli -h <host> -u <user> [options] power status
ipmicli -h <host> -u <user> [options] sensors [--type <type>,...] [--name <glob>,...] [--long] [--json]
ipmicli -h <host> -u <user> [options] fru [<id>...] [--json]
ipmicli -h <host> -u <user> [options] sol [--escape <char> | --no-escape]
ipmicli --help | --version
```

The options before the subcommand are the [connection options](#connection-options); the options
after it belong to the subcommand, in any order with its arguments. `--help` prints the usage
summary, `--version` the version of the build.

## Subcommands

| Subcommand | Library operation | Description |
| --- | --- | --- |
| `probe` | `capabilities()` | What the BMC offers **before any login**: IPMI 2.0 support, whether a BMC key (Kg) is required, the anonymous and null-user logins, per-message and user-level authentication, and the cipher suites with their algorithms, whether the library implements them, and the one the CLI would choose. Needs no credentials: the first thing to run when a login fails. |
| `chassis` | `chassisStatus()` | The power state, the power restore policy, the last power event, the faults (power, cooling, drive), interlock, overload, intrusion, front panel lockout, and the identify state. |
| `power status` | `chassisStatus()` | Prints `on` or `off`. |
| `sensors` | `sensors()` | One line per sensor with its reading, unit and status. `--type` and `--name` select the sensors **before** they are read (fewer round trips on a slow BMC); `--long` adds the sensor number, the entity, the six thresholds and the problem of a sensor without reading. |
| `fru` | `frus()` | The FRU inventory, one block per FRU with the fields of its chassis, board and product areas; `<id>...` reads only these FRU IDs. |
| `sol` | `sol().open()` | An interactive [Serial over LAN console](#interactive-sol-console) on the serial port of the server. |

`sensors` lists the sensor types to pass to `--type` as they are named in
[`SensorType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorType.html)
(`Temperature`, `Voltage`, `Fan`, `PowerSupply`, `Processor`, `Memory`...), case-insensitively;
`--name` takes globs where `*` matches any characters and `?` one, case-insensitively, and
either option can be repeated or given a comma-separated list.

The chassis power control, chassis identify, System Event Log, Get Device ID and raw command
subcommands follow with the corresponding operations of the library
([#104](https://github.com/metricshub/ipmi-java/issues/104),
[#103](https://github.com/metricshub/ipmi-java/issues/103),
[#111](https://github.com/metricshub/ipmi-java/issues/111),
[#131](https://github.com/metricshub/ipmi-java/issues/131)).

## Connection options

| Option | Description |
| --- | --- |
| `-h, --hostname <host>` | BMC host name or IP address (IPv4 or IPv6). Required. |
| `-u, --username <user>` | User name. Required, except for `probe`. An empty string (`-u ""`) is the null user (anonymous login). |
| `-p, --password <password>` | Password. Command-line arguments may be visible to other local processes: avoid in automation. |
| `-pf, --password-file <file>` | Read the password from a UTF-8 file (preferred for automation, see [below](#passwords-and-bmc-key)). |
| `-k, --bmc-key <hex>` | BMC key (Kg) of a two-key login, in hexadecimal (`0x` optional), at most 20 bytes. |
| `-kf, --bmc-key-file <file>` | Read the hexadecimal BMC key from a file. |
| `-P, --port <port>` | UDP port of the BMC. Default: 623. |
| `-L, --privilege <level>` | `user`, `operator` or `administrator` (case-insensitive). Default: the lowest level the subcommand needs (see [below](#default-privilege-per-subcommand)). |
| `-C, --cipher-suite <id>[,<id>...]` | One ID: use exactly this cipher suite, without discovery. Several: the preference order among the suites the BMC offers. Default: `17,3,8,16,2,7`. Suites without integrity (0, 1, 6, 15) are refused: their replies could be forged. |
| `-t, --timeout <ms>` | Deadline of the subcommand, in milliseconds. Default: 120000. For `sol`, it bounds the session setup and the payload activation only. |
| `--message-timeout <ms>` | Per-message timeout. Default: 5000. |
| `--retries <n>` | Resends of a message that got no reply. Default: 3. |
| `-v, --verbose` | Print to stderr the address, the user and privilege, the cipher suite preference, and the time of each operation. Never prints a secret. |
| `--help` | Print the usage summary. |
| `--version` | Print the version of the build. |

The two password options are mutually exclusive, and so are the two BMC key options. Every option
also takes the `--option=value` form. `-p` is the password and `-P` the port, as in the other
MetricsHub command-line clients; `ipmitool` uses the reverse (see the
[equivalents](#ipmitool-equivalents)).

### Default privilege per subcommand

A session asks for its maximum privilege level at login, and a BMC refuses the login when the
account is not allowed that level. So the CLI asks for the **least** privilege that works (unlike
`ipmitool`, which asks for Administrator and fails with read-only accounts unless given
`-L USER`):

| Subcommands | Default `-L` |
| --- | --- |
| `chassis`, `power status`, `sensors`, `fru` | `user` |
| `sol` | `administrator` |

A BMC that refuses a command at the session's level answers `D4h`: the CLI exits with 77 and says
to use `-L operator` or `-L administrator`. Serial over LAN is the usual case: most BMCs
activate the SOL payload at the Administrator level only, some at the User level.

## Passwords and BMC key

If neither `-p` nor `-pf` is given (and the subcommand is not `probe`), the CLI asks the password
on the interactive console, without echo. There is no console when the standard input or output
is redirected (`| jq`, `> out.txt`): such runs must use `--password-file` (or, less securely,
`--password`).

A password file is decoded as UTF-8 and **exactly one** final LF, CRLF or CR is removed; every
other byte, spaces and earlier line endings included, is part of the password. The same rule
applies to the BMC key file, which holds the key in hexadecimal. A password or key longer than
the 20 bytes a BMC stores is refused before anything is sent.

Secrets are never printed: not in the usage errors, not with `--verbose`, not in the failure
messages. The CLI blanks the password argument it was given and wipes its copies of the password
and of the key when it ends.

## Output

Diagnostics go **only to stderr**, so stdout can always be piped or parsed. The text output is
meant for humans and is stable enough for `grep`; `--json` prints UTF-8
[JSON Lines](https://jsonlines.org/): one object per sensor or FRU, one object for `probe` and
`chassis`. Absent values (a reading the sensor does not have, a threshold the record does not
set, an empty FRU field) are omitted from the JSON objects, never printed as `0`.

The output below was captured on a Lenovo RD350 (serial numbers masked), with
`ipmicli -h bmc.example.com -u monitor -pf pw.txt ...`.

### `probe`

```text
$ ipmicli -h bmc.example.com probe
IPMI 2.0                    supported
BMC key (Kg)                not required
Anonymous login             disabled
Null user names             disabled
Non-null user names         enabled
Per-message authentication  enabled
User-level authentication   enabled
Cipher suites
  ID  Authentication    Integrity        Confidentiality  Library
  3   RAKP-HMAC-SHA1    HMAC-SHA1-96     AES-CBC-128      supported
  17  RAKP-HMAC-SHA256  HMAC-SHA256-128  AES-CBC-128      supported
Chosen cipher suite         17
```

The last line is the suite a login with the same `-C` option would use: the first suite of the
preference that the BMC offers, or the single suite of a one-ID `-C`, which is used without
discovery. When that suite is one the library does not implement, or one the BMC does not offer,
the line says so and that the login would fail; `none` means a login would fail with
*offers none of the cipher suites* ([Troubleshooting](troubleshooting.html#ipmiclientexception-the-bmc-offers-none-of-the-cipher-suites)).
`--json` prints one object with the same fields and the suites as an array:

```json
{"ipmi20":true,"bmcKeyRequired":false,"anonymousLogin":false,"nullUserNames":false,"nonNullUserNames":true,"perMessageAuthentication":true,"userLevelAuthentication":true,"cipherSuites":[{"id":3,"authentication":"RAKP-HMAC-SHA1","integrity":"HMAC-SHA1-96","confidentiality":"AES-CBC-128","supported":true},{"id":17,"authentication":"RAKP-HMAC-SHA256","integrity":"HMAC-SHA256-128","confidentiality":"AES-CBC-128","supported":true}],"chosenCipherSuite":17}
```

### `chassis` and `power status`

```text
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt chassis
Power                       on
Power restore policy        PowerRestored
Last power events           AC_FAILED
Power fault                 no
Power overload              no
Interlock                   no
Power control fault         no
Cooling fault               no
Drive fault                 no
Intrusion                   no
Front panel lockout         no
Identify                    Off
```

The values are those of [`ChassisStatus`](chassis-status.html): the power restore policy is
`PoweredOff`, `PowerRestored`, `PoweredUp` or `Unknown`, the last power events are `AC_FAILED`,
`POWER_OVERLOAD`, `INTERLOCK`, `POWER_FAULT` and `IPMI_COMMAND` (`none` when the BMC reports
none), and the `Identify` line is only printed by a BMC that reports the identify state.

```json
{"powerOn":true,"powerRestorePolicy":"PowerRestored","lastPowerEvents":["AC_FAILED"],"powerFault":false,"powerOverload":false,"interlock":false,"powerControlFault":false,"coolingFault":false,"driveFault":false,"intrusion":false,"frontPanelLockout":false,"identifyState":"Off"}
```

`power status` prints just `on` or `off`.

### `sensors`

```text
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors
Name            Type                   Reading  Unit      Status
Ambient Temp    Temperature            19       DegreesC  ok
CPU1 DTS        Temperature            -42      DegreesC  ok
Exhaust Temp    Temperature            22       DegreesC  ok
Host Power      PowerUnit                                 ok
System Power    Current                88       Watts     ok
Fan 1           Fan                    6700     Rpm       ok
HDD             DriveBay                                  unavailable
PSU Redundancy  PowerSupply                               Fully Redundant
System 3.3V     Voltage                3.38     Volts     ok
PSU1 Present    PowerSupply                               Presence detected
```

(An excerpt: the Lenovo card has 49 sensors.) The columns are aligned on their widest value, two
spaces apart. The `Reading` is the converted reading of a threshold sensor, rounded to three
decimals, in the `Unit` of its record; a discrete sensor has no reading. `Status` is the
[`status()`](sensors.html#the-sensor-object) of a threshold sensor against its thresholds (`ok`,
`non-critical`, `critical`, `non-recoverable`), the asserted states of a discrete sensor
(`Presence detected`, `Fully Redundant`...), or `unavailable` when the BMC reports no valid
reading (a sensor that is not scanned, not present, or whose reading the BMC refused).

`--long` adds the sensor number, the entity, the six thresholds (lower and upper non-recoverable,
critical and non-critical) and, for a sensor without reading, the reason:

```text
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors --type temperature --long
Name          Number  Type         Entity           Reading  Unit      Status  LNR  LC  LNC  UNC  UC  UNR  Problem
Ambient Temp  1       Temperature  AirInlet 1       19       DegreesC  ok           3   8    37   39
CPU1 DTS      2       Temperature  Processor 1      -42      DegreesC  ok
CPU2 DTS      3       Temperature  Processor 2      -46      DegreesC  ok
Exhaust Temp  9       Temperature  SystemChassis 1  22       DegreesC  ok
```

With `--json`, one object per sensor, with the fields of [`Sensor`](sensors.html#the-sensor-object)
(`reading` and `rawReading` only when the reading is valid, the thresholds only when the record
sets them, `problem` only when there is one):

```text
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors --type Fan --name 'Fan 1' --json
{"name":"Fan 1","number":122,"type":"Fan","entity":"Fan","entityInstance":1,"unit":"Rpm","reading":6600.0,"rawReading":66,"status":"OK","states":[],"lowerCritical":1600.0}
```

An SDR record the library cannot decode is reported on stderr (`ipmicli: SDR record 0006h (type
0Bh): ...`) and the other sensors are printed: see
[Sensors or FRUs are missing](troubleshooting.html#sensors-or-frus-are-missing).

### `fru`

```text
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt fru
FRU 0: MotherBoard FRU (SystemBoard 1)
  Chassis type               RackMountChassis
  Board manufacturer         LENOVO
  Board product              RD350-RD450
  Board serial number        8SSB20A05892R1SH4AY0000
  Board part number          SB20A05892
  Board mfg date             2014-10-31T04:59:00Z
  Board custom 5             00:8C:FA:00:00:00
  Product manufacturer       LENOVO
  Product name               RD350
  Product part/model number  00000000000001
  Product version            70D60001EA
  Product serial number      S4M00000

FRU 3: MidPlane FRU (OtherSystemBoard 1)

FRU 17: PSU1 FRU (SystemInternalExpansionBoard 2)
  Product manufacturer       LITEON
  Product name               PS-2451-6L-LF
  Product serial number      003W
ipmicli: FRU 3: Cannot read FRU 3: ReadFruData failed: Requested Sensor, data, or record not present. (completion code CBh)
```

(An excerpt.) One block per FRU, headed by its ID, its name and its entity from the SDR locator
record, then the fields of its chassis, board and product areas that are not empty (the custom
fields of an area numbered as the area lists them, a binary one in hexadecimal). The board
manufacturing date is UTC, ISO-8601. A FRU the BMC cannot read completely is reported on stderr
with the fields read so far, and `fru` exits with 1 once every FRU is printed; a FRU that is
declared but absent (an empty power supply bay, FRU 3 above) is the usual case
([Troubleshooting](troubleshooting.html#sensors-or-frus-are-missing)).

`fru 0 17` reads only these FRUs; an ID that no locator record declares exits with 66
(`ipmicli: no such FRU 9`). `--json` prints one object per FRU, with the summary fields of
[`Fru`](fru-inventory.html) (`manufacturer`, `model`, `serialNumber`, `partNumber`) and one
nested object per area:

```json
{"id":17,"name":"PSU1 FRU","entity":"SystemInternalExpansionBoard","entityInstance":2,"manufacturer":"LITEON","model":"PS-2451-6L-LF","serialNumber":"003W","product":{"manufacturerName":"LITEON","productName":"PS-2451-6L-LF","serialNumber":"003W"},"complete":true}
```

An incomplete FRU has `"complete":false` and a `problems` array.

## Interactive SOL console

```bash
ipmicli -h bmc.example.com -u admin -pf pw.txt sol
```

`sol` activates the Serial over LAN payload and bridges it with the terminal until the console is
closed. The bytes of the BMC go to stdout unmodified, so the local terminal interprets the escape
sequences of the remote console; the typed bytes go to the BMC.

* **Escape sequences**, recognized at the start of a line, as with `ssh` and
  `ipmitool sol activate`: `~.` closes the console, `~B` sends a serial break, `~?` lists the
  sequences, `~~` sends a `~`. `--escape <char>` changes the escape character (one printable ASCII character), `--no-escape`
  disables the sequences (for binary transfers); the end of the input then closes the console.
* **Every keystroke goes to the BMC**, Ctrl+C included, without local echo (the remote console
  echoes, or hides a password), so arrow keys, function keys and BIOS setup screens work; `~.`
  is the way out. On Linux and macOS, the terminal is switched with `stty raw -echo` on
  `/dev/tty` and restored when the console closes, on an error, and from a shutdown hook (a
  kill). On Windows, where Java cannot switch the console to raw mode, a PowerShell child reads
  the keys of the console (`[Console]::ReadKey`) and hands them over, the arrow, editing and
  function keys as their ANSI sequences; use Windows Terminal (the default since Windows 11) for
  the escape sequences of the remote console to be interpreted. Ctrl+C is a key for the BMC
  there too, and the Windows console stops treating it as a signal while the console is open:
  `~.` is the way out, and Ctrl+Break ends the program through the shutdown hook. When neither works, the console
  falls back to line mode: the input is sent when Enter is pressed, without local echo.
* When stdin is not a terminal, its bytes are forwarded as they come and the end of the input
  closes the console, so `printf 'root\r' | ipmicli ... sol` works in scripts. Each read of the
  input is one SOL packet: a keystroke in raw mode, a line or a block from a pipe.
* When the BMC deactivates the console (another user activated SOL, or the session was lost),
  the CLI says so on stderr and exits with 70. When SOL is already active in another session,
  the activation fails with the completion code of the BMC (80h, *payload already active*); the
  CLI never deactivates the console of another session. A console ended by a kill is deactivated
  by the shutdown hook; one ended by a hard kill is not, and some BMCs (Supermicro) keep the
  payload active after its session is gone until it is deactivated (`ipmitool sol deactivate`).

The default privilege of `sol` is Administrator, which most BMCs require to activate the SOL
payload; `-L user` or `-L operator` is enough on the BMCs that allow it. The SOL console shares
the session of the client, so the `--timeout` bounds its setup only: an idle console never times
out. See [Serial over LAN](serial-over-lan.html) for what the BMCs do.

## Exit codes

The sysexits values of the MetricsHub command-line clients:

| Code | Meaning |
| ---: | --- |
| `0` | Success (including `sol` closed with `~.` or by the end of the input). |
| `1` | Partial result: `fru` could not read some FRUs completely (reported on stderr); the rest was printed. |
| `64` | Invalid usage, including an unreadable password or key file, and a setting the client refuses (a cipher suite without integrity, a password longer than 20 bytes). |
| `66` | `fru <id>`: no such FRU. |
| `69` | BMC unreachable: unknown host, socket error, no reply to the session setup (wrong host, UDP port 623 filtered, IPMI over LAN disabled). |
| `70` | IPMI failure: an error completion code, no common cipher suite, a malformed reply, the SOL console deactivated by the BMC. |
| `74` | Local I/O failure (stdout closed). |
| `77` | Authentication or authorization failure: unknown user, wrong password or BMC key, privilege level refused at login, insufficient privilege (`D4h`, with the `-L` hint). |
| `124` | Timeout: the deadline of the subcommand expired, or a message of the session got no reply after all the tries. |

The mapping follows the [exceptions](timeouts-and-errors.html#exceptions) of the client:
`IpmiConnectionException` 69, `IpmiAuthenticationException` 77, `IpmiTimeoutException` 124,
`IpmiCompletionCodeException` 70 (77 for `D4h`), any other `IpmiClientException` 70.

```text
$ ipmicli -h bmc.example.com -u monitor -p wrong chassis; echo $?
ipmicli: The BMC bmc.example.com refused the login of monitor: wrong password or BMC key (Authentication check failed)
77
$ ipmicli -h 192.0.2.1 -u monitor -pf pw.txt chassis; echo $?
ipmicli: No reply from 192.0.2.1:623 to the session setup after 4 tries: wrong host, UDP port 623 filtered, or IPMI over LAN disabled (Command timed out)
69
```

## Examples

What a BMC offers, before any login:

```bash
ipmicli -h bmc.example.com probe
```

The power state, reading the password from a file:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt power status
```

The temperatures and fans of a BMC that requires a BMC key, with the time of each step:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt -kf kg.txt -v sensors --type Temperature,Fan
```

Every sensor as JSON Lines, filtered with `jq`:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors --json | jq -r 'select(.status != "OK") | .name'
```

The system board FRU of a BMC that only offers cipher suite 3, on another port:

```bash
ipmicli -h bmc.example.com -P 6230 -u monitor -pf pw.txt -C 3 fru 0
```

A console on the serial port, closed with `~.`:

```bash
ipmicli -h bmc.example.com -u admin -pf pw.txt sol
```

## ipmitool equivalents

| ipmitool | ipmicli |
| --- | --- |
| `ipmitool -I lanplus -H h -U u -P p chassis status` | `ipmicli -h h -u u -p p chassis` |
| `ipmitool -I lanplus ... chassis power status` | `ipmicli ... power status` |
| `ipmitool -I lanplus ... sdr list` or `sensor list` | `ipmicli ... sensors` (`--long` for the thresholds) |
| `ipmitool -I lanplus ... sdr type Temperature` | `ipmicli ... sensors --type Temperature` |
| `ipmitool -I lanplus ... fru print` | `ipmicli ... fru` |
| `ipmitool -I lanplus ... sol activate` | `ipmicli ... sol` |
| `ipmitool -I lanplus ... lan print` (the cipher suites) | `ipmicli -h h probe` (no login needed) |
| `-I lanplus` | Implied: the CLI speaks RMCP+ (IPMI 2.0) only. |
| `-H host` | `-h host` |
| `-U user` | `-u user` |
| `-P password` | `-p password` |
| `-f file` (password file) | `-pf file` |
| `-p port` | `-P port` |
| `-k kg` (BMC key as text), `-y hexkg` | `-k hexkg` (hexadecimal only) |
| `-L USER` | The default of every read subcommand; `-L operator` or `-L administrator` to raise it. |
| `-C 3` | `-C 3` |
| `-N seconds`, `-R retries` | `--message-timeout <ms>`, `--retries <n>` |
| `-v` | `-v` (the connection details and the time of each step, on stderr) |
| `~.` to close `sol activate` | `~.` |

`ipmitool` prints a sensor reading as `na` when the BMC reports it unavailable: `ipmicli` prints
`unavailable` in the `Status` column and leaves `Reading` empty.
