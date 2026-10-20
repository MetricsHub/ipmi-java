keywords: cli, command line, ipmicli, standalone, jar, probe, info, device id, mc info, chassis, power, power on, power off, power cycle, reset, identify, sensors, fru, sel, system event log, sol, serial over lan, raw, dcmi, json, exit codes, ipmitool
description: Manual of the ipmicli command-line client of the IPMI Java Client — subcommands, connection options, passwords and BMC key, text and JSON output, the System Event Log, the interactive Serial over LAN console, raw requests, exit codes, and the ipmitool equivalents.

# Command-Line Client

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Every release ships a self-contained executable jar that bundles the library and a command-line
client, **`ipmicli`**. This page is its manual. Install it with one command (Java 8 or later is
required):

> [!TABS]
> * Linux / macOS
>   ```bash
>   curl -fsSL https://metricshub.org/ipmi-java/get | sh
>   ```
> * Windows (PowerShell)
>   ```powershell
>   irm https://metricshub.org/ipmi-java/get.ps1 | iex
>   ```

The installer verifies the checksum of the jar and installs an `ipmicli` launcher. On Windows, it
adds the directory of the launcher to the user `PATH`; on Linux and macOS, it prints the line to
add to your shell profile when that directory (`~/.local/bin` by default) is not on the `PATH`.
See [Installation](installation.html#command-line-client) to inspect the script first, pin a
version, change the directories or [uninstall](installation.html#uninstalling) the CLI.

Without the installer, download `${project.artifactId}-${project.version}-standalone.jar` from the
[latest release](https://github.com/metricshub/ipmi-java/releases/latest) and run it with
`java -jar ${project.artifactId}-${project.version}-standalone.jar` wherever this page says
`ipmicli`. The jar needs no other file: the library has no dependency.

The CLI is a thin layer over the [`IpmiClient`](index.html#quick-start) API: every subcommand is
one operation of the client, with the same defaults (except the privilege level, which the CLI
sets per subcommand: see [Default privilege per subcommand](#default-privilege-per-subcommand)),
the same [session handling](timeouts-and-errors.html#session-recovery) and the same failures,
reported as exit codes. It is meant to feel familiar to `ipmitool` users: see the
[ipmitool equivalents](#ipmitool-equivalents).

## Synopsis

The command line has the form `ipmicli [options] <subcommand> [subcommand options]`:

```text
ipmicli -h <host> [options] probe [--json]
ipmicli -h <host> -u <user> [options] <subcommand> [arguments]
ipmicli --help | --version

<subcommand> [arguments]:
  info [--json]
  chassis [--json]
  power status | on | off | cycle | reset | soft | diag
  identify [<seconds> | force | off]
  sensors [--type <type>,...] [--name <glob>,...] [--long] [--json]
  fru [<id>...] [--json]
  sel [--since <id>] [--json]
  sol [--escape <char> | --no-escape]
  raw <netfn> <command> [<data byte>...] [--json]
```

The options before the subcommand are the [connection options](#connection-options); the options
after it belong to the subcommand, in any order with its arguments. `--help` prints the usage
summary, also after a subcommand (`ipmicli sensors --help`); `--version` prints the version of
the build.

## Subcommands

| Subcommand | Library operation | Description |
| --- | --- | --- |
| `probe` | `capabilities()` | What the BMC offers **before any login**: IPMI 2.0 support, whether a BMC key (Kg) is required, the anonymous and null-user logins, per-message and user-level authentication, and the cipher suites with their algorithms, whether the library implements them, and the one the CLI would choose. Needs no credentials: the first thing to run when a login fails. |
| `info` | `deviceId()` | The identity of the BMC: manufacturer and product IDs, firmware revision, IPMI version, the functions it provides ([Device ID](device-id.html)). |
| `chassis` | `chassisStatus()` | The power state, the power restore policy, the last power event, the faults (power, cooling, drive), interlock, overload, intrusion, front panel lockout, and the identify state. |
| `power status` | `chassisStatus()` | Prints `on` or `off`. |
| `power on`, `off`, `cycle`, `reset`, `soft`, `diag` | `chassisControl()` | Powers the system on; off at once; off then on; resets it; asks the operating system to shut down (ACPI); pulses a diagnostic interrupt (NMI). Prints nothing: the exit code says whether the BMC accepted the command, which it carries out afterwards. |
| `identify`, `identify <seconds>`, `identify force`, `identify off` | `chassisIdentify()`, `chassisIdentifyForever()` | `identify` blinks the identify LED of the chassis for 15 seconds, `identify <seconds>` for 1 to 255 seconds, `identify force` until it is turned off (not every BMC supports `force`); `identify off` turns it off. Prints nothing. |
| `sensors` | `sensors()` | One line per sensor with its reading, unit and status. `--type` and `--name` select the sensors **before** they are read (fewer round trips on a slow BMC); `--long` adds the sensor number, the entity, the six thresholds and the problem of a sensor without reading. |
| `fru` | `frus()` | The FRU inventory, one block per FRU with the fields of its chassis, board and product areas, or the SPD data of a memory module, or the Processor Information ROM of a processor; `<id>...` reads only these FRU IDs. |
| `sel` | `sel()` | The System Event Log, one line per entry with its time, the sensor that reported it, the event and its direction; `--since <id>` reads only the entries after the given record ID. |
| `sol` | `sol().open()` | An interactive [Serial over LAN console](#interactive-sol-console) on the serial port of the server. |
| `raw` | `raw(netFn, command).data(...).execute()` | Sends any request, like `ipmitool raw`: the network function, the command and the data bytes in hexadecimal, `0x` optional (`06`, `0x06` and `6` are the same byte). Prints the response data in hexadecimal, 16 bytes per line. Extension and OEM network functions are accepted (`raw 0x2c 0x01 0xdc 0x01`, Get DCMI Capabilities Info). |

`--type` takes the sensor types as the `Type` column of `sensors` prints them, the names of
[`SensorType`](apidocs/org/metricshub/ipmi/core/coding/commands/sdr/record/SensorType.html)
(`Temperature`, `Voltage`, `Fan`, `PowerSupply`, `Processor`, `Memory`...), case-insensitively.
`--name` takes globs, case-insensitively: `*` matches any characters and `?` one. Each option can
be repeated or given a comma-separated list. A glob matches the whole name: `--name 'Fan*'`
selects `Fan 1`, `--name Fan` does not (quote the globs so that the shell does not expand them).
A sensor is selected when its type is one of the `--type` values and its name matches one of the
`--name` globs.

## Connection options

| Option | Description |
| --- | --- |
| `-h, --hostname <host>` | BMC host name or IP address (IPv4 or IPv6). Required. |
| `-u, --username <user>` | User name. Required, except for `probe`. An empty string (`-u ""`) is the null user name: with an empty password (`-p ""`) it is the anonymous login, with a password a login by null user name; `probe` says whether the BMC accepts each. |
| `-p, --password <password>` | Password. Command-line arguments may be visible to other local processes: avoid in automation. |
| `-pf, --password-file <file>` | Read the password from a UTF-8 file (preferred for automation, see [below](#passwords-and-bmc-key)). |
| `-k, --bmc-key <hex>` | The [BMC key (Kg)](preparing-the-bmc.html#bmc-key-kg) of a BMC configured for two-key logins (`probe` says whether it requires one): an even number of hexadecimal digits (`0x` optional), at most 20 bytes. |
| `-kf, --bmc-key-file <file>` | Read the BMC key from a file, in the same hexadecimal form. |
| `-P, --port <port>` | UDP port of the BMC. Default: 623. |
| `-L, --privilege <level>` | `user`, `operator`, `administrator` or `auto` (case-insensitive). Default: the lowest level the subcommand needs (see [below](#default-privilege-per-subcommand)). |
| `-C, --cipher-suite <id>[,<id>...]` | One ID: use exactly this cipher suite, without discovery. Several: the preference order among the suites the BMC offers. Default: `17,3,8,16,2,7,12,11`. Suites without integrity (0, 1, 6, 15) are refused: their replies could be forged. |
| `-t, --timeout <ms>` | Deadline of the subcommand, in milliseconds. Default: 120000. For `sol`, it bounds the session setup and the payload activation only. |
| `--message-timeout <ms>` | Per-message timeout. Default: 5000. |
| `--retries <n>` | Resends of a message that got no reply. Default: 3. |
| `-v, --verbose` | Print to stderr the address, the user and privilege, the cipher suite preference, the message timeout and retries, and the time of each operation, failed ones included; `sel` and `sol` add a summary (see [`sel`](#sel) and the [SOL console](#interactive-sol-console)). Never prints a secret. |
| `--help` | Print the usage summary. |
| `--version` | Print the version of the build. |

`-P`, `-L`, `-C`, `-k`/`-kf`, `-t`, `--message-timeout` and `--retries` set `port()`,
`privilege()` (`auto` is `MaximumAvailable`), `cipherSuites()` (`cipherSuite()` for a single ID),
`bmcKey()`, `timeout()`, `messageTimeout()` and `retries()` of the client builder: see
[Configuration](configuration.html#options) for what each one does.

The two password options are mutually exclusive, and so are the two BMC key options. Every option
that takes a value also takes the `option=value` form (`--port=6230`, `-P=6230`); a flag (`-v`,
`--json`, `--long`, `--no-escape`, `--help`, `--version`) takes no value: `--json=true` is
refused. `-p` is the password and `-P` the port, as in the other MetricsHub command-line clients;
`ipmitool` uses the reverse (see the [equivalents](#ipmitool-equivalents)).

### Default privilege per subcommand

A session asks for its maximum privilege level at login and is raised to it right after, and a
BMC refuses the login, or the raise, when the account is not allowed that level. So the CLI asks
for the **least** privilege that works (unlike `ipmitool`, which asks for Administrator and fails
with read-only accounts unless given `-L USER`):

| Subcommands | Default `-L` |
| --- | --- |
| `info`, `chassis`, `power status`, `sensors`, `fru`, `sel` | `user` |
| `power on`, `off`, `cycle`, `reset`, `soft`, `diag`, `identify` | `operator` |
| `sol`, `raw` | `administrator` |

`-L auto` requests the highest level the account is allowed, runs the session at `user` and
raises it only when the BMC refuses a command for insufficient privilege (`D4h`), to `operator`
then `administrator`, as far as the account allows
([Raising the session on demand](configuration.html#raising-the-session-on-demand)): the same
command line works on BMCs that need different levels for the same command, and a read-only
account still logs in.

Most BMCs accept Send Message, which forwards a request to another controller (power supplies,
fans, a RAID adapter), from the Operator level; some from the User level (HPE iLO 5), others only
from the Administrator level (an AMI-based GIGABYTE BMC). Below that level, `sensors` lists the
sensors of these controllers without reading, with the `D4h` problem in `--long` and `--json`:
give `-L auto` (or `-L operator`, `-L administrator`) to read them
([Sensor owners](sensors.html#sensor-owners)). The same holds for `fru`: the FRUs of another
controller than the BMC are read with Send Message too. Below the level the BMC requires, such a
FRU is printed without inventory, its `D4h` goes to stderr, and `fru` exits with 1: give `-L auto`
([FRUs of other controllers](fru-inventory.html#frus-of-other-controllers)).

A BMC that refuses a command at the session's level answers `D4h`: the CLI exits with 77 and says
which higher `-L` to use (at the Administrator level, that the BMC restricts the command; with
`-L auto`, that the account does not have the level or the BMC restricts the command). Serial over
LAN is the usual case: most BMCs activate the SOL payload at the Administrator level only, some
at the Operator or User level. A raw request can be anything, so `raw` asks for Administrator, like
`ipmitool`: give `-L user` to send a read with a read-only account.

## Passwords and BMC key

If neither `-p` nor `-pf` is given (and the subcommand is not `probe`), the CLI asks the password
on the interactive console, without echo. There is no console when the standard input or output
is redirected (`| jq`, `> out.txt`): such runs must use `--password-file` (or, less securely,
`--password`).

A password file is decoded as UTF-8 and **exactly one** final LF, CRLF or CR is removed; every
other byte, spaces and earlier line endings included, is part of the password. The same rule
applies to the BMC key file, which holds the key in hexadecimal. A user name longer than 16
bytes, or a password or key longer than 20 bytes, the most a BMC stores, is refused before
anything is sent, with exit code 64 (the user name and the password are counted in UTF-8).

On Windows, write the password file in UTF-8 without byte order mark, for instance with
`[IO.File]::WriteAllText("$PWD\pw.txt", 'secret')`. Windows PowerShell 5.1 writes UTF-16 with
`>`, which the CLI refuses (*is not valid UTF-8*), and with `-Encoding utf8` it writes a byte
order mark, which would become part of the password; PowerShell 7 writes UTF-8 without byte order
mark by default. Java receives its command-line arguments in the ANSI code page of the system: a
character of a `-p` or `-u` value that this code page lacks (Cyrillic or CJK on a Western
European system, for instance) reaches the CLI as `?`, and the BMC refuses the login. Put such a
password in a UTF-8 file and pass it with `-pf`.

Secrets are never printed: not in the usage errors, not with `--verbose`, not in the failure
messages. The CLI wipes its copies of the password and of the key when it ends, but a `-p` or
`-k` value stays in memory as text that the CLI cannot wipe (the JVM keeps its command line in the
`sun.java.command` system property), and the command line that other processes see cannot be
changed: use `-pf` and `-kf` when other users share the machine.

## Output

Diagnostics go **only to stderr**, so stdout can always be piped or parsed. The text output is
meant for humans and is stable enough for `grep`; `--json` prints UTF-8
[JSON Lines](https://jsonlines.org/): one object per sensor, FRU or SEL entry, one object for
`probe`, `info`, `chassis` and `raw`. Absent values (a reading the sensor does not have, a
threshold the record does not set, an empty FRU field) are omitted from the JSON objects, never
printed as `0`. In a `custom` array, an empty field stays as `""`, so that each field keeps its
position in the area.

In the text output, a control character in a string from the BMC (a sensor or FRU name, a FRU
field, a SEL description) is printed as `?`, so that it cannot drive the terminal. `--json` keeps
these characters, with JSON escapes for those below U+0020.

The output below was captured on a Lenovo RD350, whose BMC is a Lenovo ThinkServer System Manager (TSM) (serial
numbers masked), with
`ipmicli -h bmc.example.com -u monitor -pf pw.txt ...`, except the memory module and processor
FRUs of the `fru` example, which come from a Lenovo XClarity Controller (XCC).

### `probe`

```shell-session
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

Every line but the last shows what `capabilities()` returns
([Checking what the BMC offers](preparing-the-bmc.html#checking-what-the-bmc-offers)). The last
line is the suite a login with the same `-C` option would use: the first suite of the preference
that the BMC offers, or the single suite of a one-ID `-C`, which is used without discovery. When
that suite is one the library does not implement, or one the BMC does not offer, the line says so
and that the login would fail; `none` means a login would fail with *offers none of the cipher
suites* ([Troubleshooting](troubleshooting.html#no-cipher-suite-in-common-with-the-bmc)).
`--json` prints one object with the same fields and the suites as an array (`supported` is false
for a suite the library does not implement). `chosenCipherSuite` is omitted when no suite would be
chosen (`none` in the text output); when a login with the chosen suite would fail,
`chosenCipherSuiteProblem` says why: `not offered by the BMC: the login would fail` or
`not implemented by the library: the login would fail`:

```json
{"ipmi20":true,"bmcKeyRequired":false,"anonymousLogin":false,"nullUserNames":false,"nonNullUserNames":true,"perMessageAuthentication":true,"userLevelAuthentication":true,"cipherSuites":[{"id":3,"authentication":"RAKP-HMAC-SHA1","integrity":"HMAC-SHA1-96","confidentiality":"AES-CBC-128","supported":true},{"id":17,"authentication":"RAKP-HMAC-SHA256","integrity":"HMAC-SHA256-128","confidentiality":"AES-CBC-128","supported":true}],"chosenCipherSuite":17}
```

### `info`

```shell-session
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt info
Device ID                   32
Device revision             1
Firmware revision           5.38
IPMI version                2.0
Manufacturer ID             19046
Product ID                  502
Device available            yes
Provides device SDRs        no
Functions                   SENSOR_DEVICE, SDR_REPOSITORY_DEVICE, SEL_DEVICE, FRU_INVENTORY_DEVICE, IPMB_EVENT_RECEIVER, IPMB_EVENT_GENERATOR, CHASSIS_DEVICE
Aux firmware revision       b2010000
```

The values are those of [`DeviceId`](device-id.html): the manufacturer ID is the IANA enterprise
number of the vendor (19046 is Lenovo), the firmware revision is printed as `ipmitool` prints it,
the functions are the [`DeviceFunction`](apidocs/org/metricshub/ipmi/client/DeviceFunction.html)
names (`none` when the BMC flags none), and the `Aux firmware revision` line (four bytes in
hexadecimal, vendor-defined) is only printed by a BMC that sends it.

```json
{"id":32,"revision":1,"firmwareRevision":"5.38","ipmiVersion":"2.0","manufacturerId":19046,"productId":502,"available":true,"providesDeviceSdrs":false,"functions":["SENSOR_DEVICE","SDR_REPOSITORY_DEVICE","SEL_DEVICE","FRU_INVENTORY_DEVICE","IPMB_EVENT_RECEIVER","IPMB_EVENT_GENERATOR","CHASSIS_DEVICE"],"auxiliaryFirmwareRevision":"b2010000"}
```

### `chassis` and `power status`

```shell-session
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
none), and the `Identify` line (`Off`, `TemporaryOn` or `IndefiniteOn`) is only printed by a BMC
that reports the identify state.

```json
{"powerOn":true,"powerRestorePolicy":"PowerRestored","lastPowerEvents":["AC_FAILED"],"powerFault":false,"powerOverload":false,"interlock":false,"powerControlFault":false,"coolingFault":false,"driveFault":false,"intrusion":false,"frontPanelLockout":false,"identifyState":"Off"}
```

`power status` prints just `on` or `off`.

### `power` actions and `identify`

```shell-session
$ ipmicli -h bmc.example.com -u operator -pf pw.txt power off; echo $?
0
$ ipmicli -h bmc.example.com -u operator -pf pw.txt power status
off
```

A power action and `identify` print nothing: the exit code is 0 once the BMC has accepted the
command. The BMC carries it out afterwards (a power off takes a few seconds, a `soft` shutdown
as long as the operating system needs): `power status` shows the result. The actions map to the
[`PowerCommand`](chassis-status.html#controlling-the-power) of the library:

| Action | `PowerCommand` | Notes |
| --- | --- | --- |
| `on` | `PowerUp` | Powers the system on. |
| `off` | `PowerDown` | Immediate, no operating system shutdown. |
| `cycle` | `PowerCycle` | Off for at least a second, then on. The client reads the power state first and refuses to cycle a system that is off, without sending the power command (exit code 70): some BMCs would power it up. Optional in the specification: `CCh` on a BMC that does not implement it. |
| `reset` | `HardReset` | Most BMCs refuse it when the system is off (`D5h`, exit code 70). |
| `soft` | `SoftShutdown` | Asks the operating system to shut down (ACPI). Optional (`CCh`). |
| `diag` | `DiagnosticInterrupt` | Pulses a diagnostic interrupt (an NMI on x86 systems). Optional (`CCh`). |

An action is sent once, never again when its reply is lost or is a `C3h`, as the BMC may have
carried it out. A lost reply exits with 124, a `C3h` with 70, and `power status` tells what the
BMC did.

`identify` blinks the identify LED for 15 seconds, `identify 60` for a minute (1 to 255), and
`identify force` until `identify off`, on the BMCs that implement the "force identify on" option
(the others answer `CCh`). `chassis` shows the identify state on the BMCs that report it.

### `sensors`

```shell-session
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

(An excerpt: the BMC of this RD350 has 49 sensors.) The columns are aligned on their widest
value, two spaces apart. The `Reading` is the converted reading of a threshold sensor, rounded to
three decimals, in the `Unit` of its record; a discrete sensor has no reading. `Status` is the
[`status()`](sensors.html#the-sensor-object) of a threshold sensor, as the BMC compares its
reading with its thresholds (`ok`, `non-critical`, `critical`, `non-recoverable`), the asserted
states of a discrete sensor (`Presence detected`, `Fully Redundant`...), `ok` for a discrete
sensor that answers with no asserted state the library describes (`Host Power` above), or
`unavailable` when the BMC reports no valid reading (a sensor that is not scanned, not present,
or whose reading the BMC refused).

`--long` adds the sensor number, the entity, the six thresholds (lower and upper non-recoverable,
critical and non-critical) and, for a sensor without reading, the reason:

```shell-session
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors --type temperature --long
Name          Number  Type         Entity           Reading  Unit      Status  LNR  LC  LNC  UNC  UC  UNR  Problem
Ambient Temp  1       Temperature  AirInlet 1       19       DegreesC  ok           3   8    37   39
CPU1 DTS      2       Temperature  Processor 1      -42      DegreesC  ok
CPU2 DTS      3       Temperature  Processor 2      -46      DegreesC  ok
Exhaust Temp  9       Temperature  SystemChassis 1  22       DegreesC  ok
```

With `--json`, one object per sensor, with the fields of [`Sensor`](sensors.html#the-sensor-object).
`status` is the `SensorStatus` name (`OK`, `NON_CRITICAL`, `CRITICAL`, `NON_RECOVERABLE`,
`UNAVAILABLE`), and `states` is always present, empty when there is none. `unit` is present only
when the record names one (not `Unspecified`, which leaves the `Unit` column of the text output
empty), `reading` only when the sensor has a valid converted reading, `rawReading` whenever its
reading is valid, discrete sensors included, the thresholds only when the record sets them, and
`problem` only when there is one:

```shell-session
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors --type Fan --name 'Fan 1' --json
{"name":"Fan 1","number":122,"type":"Fan","entity":"Fan","entityInstance":1,"unit":"Rpm","reading":6600.0,"rawReading":66,"status":"OK","states":[],"lowerCritical":1600.0}
```

An SDR record the library cannot decode is reported on stderr (`ipmicli: SDR record 0006h (type
0Bh): ...`) and the other sensors are printed: see
[Sensors or FRUs are missing](troubleshooting.html#sensors-or-frus-are-missing).

### `fru`

```shell-session
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
ipmicli: FRU 3: Cannot read FRU 3: ReadFruData failed: Requested Sensor, data, or record not present. (completion code CBh)

FRU 17: PSU1 FRU (SystemInternalExpansionBoard 2)
  Product manufacturer       LITEON
  Product name               PS-2451-6L-LF
  Product serial number      0000

FRU 97: DIMM_2 (MemoryDevice 2)
  Memory type                DDR4 RDIMM ECC
  Memory capacity            16384 MB
  Module manufacturer        Samsung
  DRAM manufacturer          Samsung
  Module part number         M393A2K40BB2-CTD
  Module serial number       3768424A
  Module mfg date            2017-W45

FRU 85: PIROM 1 (Processor 1)
  Processor S-spec           SR3GH
  Processor part number      37608DC00416530
  Processor PPIN             634AFBC3354D3C6D
  Processor CPUID            0654h
  Processor cores            8 with Hyper-Threading
  Processor frequency        2100-3000 MHz
  Processor L2 cache         1024 KB per core
  Processor L3 cache         11264 KB
  Processor NVM DIMM max     768 GB
  Processor TDP              85 W
  Processor Tcase max        77 C
```

(An excerpt; FRUs 97 and 85 come from a Lenovo XCC.) One block per FRU, headed by its ID, its
name and its entity from the SDR locator record, then the fields of its chassis, board and
product areas that are not empty (the custom fields of an area numbered as the area lists them, a
binary one in hexadecimal). The board manufacturing date is UTC, ISO-8601. A memory module (FRU
97 above, whose FRU device returns the SPD data of the DIMM) is printed with its type, capacity,
manufacturers, part and serial numbers and manufacturing week
([Memory modules](fru-inventory.html#memory-modules)), and a processor (FRU 85, whose FRU device
returns the Processor Information ROM of the CPU) with its S-spec, part number, PPIN, cores,
frequencies, caches and thermal design power ([Processors](fru-inventory.html#processors)). The
MultiRecord area (power supply, DC output and load, management access records...) and the
internal use area are not printed, in text or JSON: read them in code with `Fru.multiRecords()`
and `Fru.records()` ([The `Fru` object](fru-inventory.html#the-fru-object)).

A FRU the BMC cannot read completely is printed with the fields read so far, its problem goes to
stderr (`ipmicli: FRU <id>: ...`), and `fru` exits with 1 once every FRU is printed; a FRU that
is declared but absent (FRU 3 above, or an empty power supply bay) is the usual case
([Troubleshooting](troubleshooting.html#sensors-or-frus-are-missing)).

`fru 0 17` reads only these FRUs; an ID that no locator record declares exits with 66
(`ipmicli: no such FRU 9`), also when another FRU is incomplete. `--json` prints one object per
FRU, with the summary fields of [`Fru`](fru-inventory.html) (`manufacturer`, `model`,
`serialNumber`, `partNumber`) and one nested object per area, its empty fields omitted:

| Object | Keys |
| --- | --- |
| `chassis` | `type`, `partNumber`, `serialNumber`, `custom` |
| `board` | `manufacturer`, `productName`, `serialNumber`, `partNumber`, `mfgDate` (ISO-8601, UTC), `custom` |
| `product` | `manufacturerName`, `productName`, `modelNumber`, `version`, `serialNumber`, `assetTag`, `custom` |
| `spd` (memory module) | `memoryType`, `moduleType`, `capacityMegabytes`, `ecc`, `manufacturer`, `dramManufacturer`, `partNumber`, `serialNumber`, `mfgDate` (ISO-8601 week, `2017-W45`) |
| `pirom` (processor) | `sSpec`, `partNumber`, `ppin`, `cpuid` (hexadecimal, without `h`), `cores`, `hyperThreading`, `baseFrequencyMhz`, `maxFrequencyMhz`, `l2KilobytesPerCore`, `l3Kilobytes`, `nvmDimmMaxCapacityGigabytes`, `tdpWatts`, `tcaseMax`, `dtsMax` (not in the text output) |

FRU 17 of the example above:

```json
{"id":17,"name":"PSU1 FRU","entity":"SystemInternalExpansionBoard","entityInstance":2,"manufacturer":"LITEON","model":"PS-2451-6L-LF","serialNumber":"0000","product":{"manufacturerName":"LITEON","productName":"PS-2451-6L-LF","serialNumber":"0000"},"complete":true}
```

An incomplete FRU has `"complete":false` and a `problems` array.

### `sel`

```shell-session
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sel
ID   Time                  Type                  Sensor  Event                                          Direction
1    2024-05-15T08:15:25Z  EventLoggingDisabled  254     Log area reset/cleared                         assertion
2    2024-05-15T08:16:11Z  OemTimestamped                OEM data 02 01 05 00 00 00
3    2024-05-15T08:16:11Z  OemTimestamped                OEM data 02 08 05 00 00 00
5    2024-05-15T08:23:27Z  PowerUnit             100     Power off/down                                 assertion
6    2024-05-15T08:23:34Z  PowerUnit             100     Power off/down                                 deassertion
7    2024-05-15T08:24:15Z  VersionChange         0       Firmware or software incompatibility detected  assertion
640  pre-init 315532807s   PowerSupply           219     Presence detected                              assertion
643  pre-init 315532815s   PowerSupply           156     Fully Redundant                                assertion
```

(An excerpt: the log of this RD350 has 643 entries, 358 of them OEM records.) One line per entry
of the [System Event Log](system-event-log.html), in the order of the log: the record ID, the
time (UTC, ISO-8601; `pre-init <seconds>s` for an event logged before the BMC had the time, like
the `Pre-Init` of `ipmitool`; empty for an OEM entry without timestamp), the sensor type of a
system event or the record type of an OEM one, the sensor number, the event in the wording of
`ipmiutil` (of `ipmitool` for the few events `ipmiutil` does not name) or the OEM bytes, and
`assertion` or `deassertion`. The times are UTC whatever the BMC keeps (it reports the offset of
a local SEL time with Get SEL Time UTC Offset). `-v` adds to stderr what Get SEL Info says of the
log: its number of entries, the time of the last addition and erase, and that offset.

`--since <id>` prints only the entries after the given record ID, normally the ID of the last
entry of the previous run; the header alone means no new entry. When the BMC no longer holds that
entry (the log was cleared), the whole log is printed. A log cleared and filled up to that ID
again goes unnoticed: compare the `last erase` time that `-v` prints between runs.

`--json` prints one object per entry with the fields of
[`SelEntry`](system-event-log.html#the-selentry-object): `id`, `type` (`System`,
`OemTimestamped`, `OemNonTimestamped` or `Reserved`) and `description` always, `timestamp` only
when it is a date, `preInitSeconds` only for a pre-init one, the system event fields
(`sensorType`, `sensorNumber`, `generatorId`, `eventType`, `event`, `direction`, `eventData` in
hexadecimal) only for a system event, `oemData` only for an OEM entry, and `manufacturerId` only
for a timestamped one:

```shell-session
$ ipmicli -h bmc.example.com -u monitor -pf pw.txt sel --since 5 --json
{"id":6,"type":"System","timestamp":"2024-05-15T08:23:34Z","sensorType":"PowerUnit","sensorNumber":100,"generatorId":32,"eventType":111,"event":"PowerOffOrDown","description":"Power off/down","direction":"Deassertion","eventData":"00ffff"}
{"id":7,"type":"System","timestamp":"2024-05-15T08:24:15Z","sensorType":"VersionChange","sensorNumber":0,"generatorId":1,"eventType":111,"event":"FirmwareOrSoftwareIncompatibilityDetected","description":"Firmware or software incompatibility detected","direction":"Assertion","eventData":"830800"}
{"id":8,"type":"OemTimestamped","timestamp":"2024-05-15T08:29:39Z","description":"OEM data 02 01 05 05 00 00","manufacturerId":19046,"oemData":"020105050000"}
```

(An excerpt: the first three entries.) The sensor names are not resolved: that takes a walk of
the SDR repository, as long as `sensors` on a slow BMC. A sensor number is unique only within its
owner (the BMC, a satellite controller, the BIOS), which `generatorId` identifies: in code,
[`SelEntry.isFrom(Sensor)`](system-event-log.html#the-selentry-object) matches the owner and LUN
as well as the number, as `ipmitool sel elist` does.

### `raw`

```shell-session
$ ipmicli -h bmc.example.com -u admin -pf pw.txt raw 0x2c 0x01 0xdc 0x01
dc 01 05 02 00 01 07
$ ipmicli -h bmc.example.com -u admin -pf pw.txt raw 0x06 0x01
20 01 05 38 02 bf 66 4a 00 f6 01 b2 01 00 00
```

The response data, after the completion code, in hexadecimal: 16 bytes per line, nothing for a
reply without data. The first request is Get DCMI Capabilities Info (Group Extension network
function `2Ch`, whose first data byte is the defining body, `DCh` for DCMI): the reply says DCMI
1.5. The second is Get Device ID, the command behind `info`.

Every byte is hexadecimal, `0x` or not: `10` is `10h`, where `ipmitool` reads `10` as decimal and
`010` as octal. The network function must be that of a request: an even byte from `00h` to `3Eh`
(odd network functions are those of the responses, IPMI 2.0 section 5.1). This includes the
standard network functions, Transport (`0Ch`), Group Extension (`2Ch`), OEM/Group (`2Eh`) and the
controller-specific OEM network functions (`30h` to `3Eh`). An error completion code is printed
on stderr with the exit code 70 (77 with the `-L` hint for `D4h`), after the data the BMC may
have sent with it; it is not retried, a busy `C0h` or a `C3h` included. Like any message, the
request is sent again when its reply is lost (`--retries 0` sends it once): a power command is
better sent with `power`, which has the safeguards. `--json` prints one object, with the
completion code as a number and the data in hexadecimal (omitted when empty), also for an error
completion code:

```json
{"completionCode":0,"data":"dc010502000107"}
```

## Interactive SOL console

```bash
ipmicli -h bmc.example.com -u admin -pf pw.txt sol
```

`sol` activates the Serial over LAN payload and bridges it with the terminal until the console is
closed. The bytes of the BMC go to stdout unmodified, so the local terminal interprets the escape
sequences of the remote console; the typed bytes go to the BMC.

* **Escape sequences**, recognized at the start of a line, as with `ssh` and
  `ipmitool sol activate`: `~.` closes the console, `~B` sends a serial break, `~?` lists the
  sequences, `~~` sends a `~`. `--escape <char>` changes the escape character (one printable
  ASCII character other than space); `--no-escape` disables the sequences, for binary transfers
  through a pipe, whose end closes the console.
* **Every keystroke goes to the BMC**, Ctrl+C included, without local echo (the remote console
  echoes, or hides a password), so arrow keys, function keys and BIOS setup screens work; `~.` at
  the start of a line is the way out.
  * Linux and macOS: the terminal is switched with `stty raw -echo` on `/dev/tty`, and restored
    when the console closes, on an error, and from a shutdown hook (a kill).
  * Windows, where Java cannot switch the console to raw mode: a PowerShell child reads the keys
    of the console (`[Console]::ReadKey`) and hands them over, the arrow, editing and function
    keys as their ANSI sequences. Ctrl+C is a key for the BMC there too: the Windows console
    stops treating it as a signal while the SOL console is open. Use Windows Terminal (the
    default terminal since Windows 11 22H2) for the escape sequences of the remote console to be
    interpreted.
  * When the terminal cannot be switched to raw mode (`stty` fails, or the PowerShell key reader
    does not start), the console falls back to line mode: a line is sent when Enter is pressed,
    without local echo, and Ctrl+C ends the program through the shutdown hook.
* When stdin is not a terminal, its bytes are forwarded as they come and the end of the input
  closes the console at once: what the server prints afterwards is lost. In a script, keep the
  input open as long as the output is needed, for instance
  `{ printf 'root\r'; sleep 5; } | ipmicli ... sol`. Each read of the input is one write to the
  BMC: a keystroke in raw mode, a line or a block from a pipe, sent as one SOL packet or several
  when it exceeds the payload size of the BMC. With `-v`, the CLI sums the console up when it
  closes: writes and bytes sent, the mean acknowledgement time per write, bytes received.
* When the BMC deactivates the console (another user activated SOL, or the session was lost),
  the CLI says so on stderr and exits with 70. When SOL is active in another session the BMC
  still has, the activation fails with exit code 70 and the message *Cannot open the SOL console
  of bmc.example.com: The SOL payload is in use by another session*, followed by the instance,
  the session ID, and the user ID and privilege level of that session (4 is Administrator): the
  CLI never deactivates the console of a live session. A console ended by a kill is deactivated
  by the shutdown hook; one ended by a hard kill is not, and some BMCs (Supermicro) keep its
  payload active for good: once the BMC has forgotten that dead session, within a minute, the
  next `sol` deactivates the stale payload itself and goes on.

The default privilege of `sol` is Administrator, which most BMCs require to activate the SOL
payload; `-L user` or `-L operator` is enough on the BMCs that allow it. The `--timeout` bounds
the setup of the console only (session and payload activation): an idle console never times
out. See [Serial over LAN](serial-over-lan.html) for what the BMCs do.

## Exit codes

The exit codes of the MetricsHub command-line clients: the sysexits values (64 to 77), 1 for a
partial result, 124 for a timeout (as GNU `timeout`) and 127 when the launcher cannot start the
CLI:

| Code | Meaning |
| ---: | --- |
| `0` | Success (including `sol` closed with `~.` or by the end of the input). |
| `1` | Partial result: `fru` could not read some FRUs completely (reported on stderr); the rest was printed. |
| `64` | Invalid usage, including an unreadable password or key file, no password option when there is no console to ask the password on (input or output redirected), no password entered at the prompt, and a setting the client refuses (a cipher suite without integrity, a user name longer than 16 bytes, a password or BMC key longer than 20 bytes). |
| `66` | `fru <id>`: no such FRU. |
| `69` | BMC unreachable: unknown host, socket error, no reply to the session setup (wrong host, UDP port 623 filtered, IPMI over LAN disabled, a BMC without IPMI 2.0 with a one-ID `-C`). |
| `70` | IPMI failure: an error completion code (also in the reply to `raw`), no common cipher suite, a BMC without IPMI 2.0 (RMCP+), a malformed reply, a `power cycle` of a system that is off, the SOL payload in use by another session, SOL input the BMC does not accept, the SOL console deactivated by the BMC. |
| `74` | Local I/O failure: stdout closed, or the password prompt or the input of `sol` could not be read. |
| `77` | Authentication or authorization failure: unknown user, wrong password or BMC key, privilege level refused at login, insufficient privilege (`D4h`, with the `-L` hint). |
| `124` | Timeout: the deadline of the subcommand expired, or a message of the session got no reply after all the tries. |
| `127` | The launcher of the [installer](installation.html#command-line-client) found no Java 8 or later (install one from [Adoptium](https://adoptium.net), or set `IPMICLI_JAVA_HOME`), or no jar (run the installer again). |
| `130`, `143` | Interrupted by Ctrl+C (`SIGINT`) or terminated (`SIGTERM`), as any Java program: 128 + the signal number. A run that `timeout(1)` stops exits with the 124 of `timeout` instead. In the raw mode of `sol`, Ctrl+C is a key for the BMC, not a signal. |

The mapping follows the [exceptions](timeouts-and-errors.html#exceptions) of the client:
`IpmiConnectionException` 69, `IpmiAuthenticationException` 77, `IpmiTimeoutException` 124,
`IpmiCompletionCodeException` 70 (77 for `D4h`), any other `IpmiClientException` 70; an
`IllegalArgumentException` of the builder (a setting it refuses) 64, an `IllegalStateException`
(a `PowerCycle` of a system that is off) 70.

```shell-session
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

Which BMC this is (manufacturer, product, firmware), as JSON for a script:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt info --json
```

Power a server on, then make it blink its identify LED for a minute, with an account allowed
the Operator level:

```bash
ipmicli -h bmc.example.com -u operator -pf pw.txt power on
ipmicli -h bmc.example.com -u operator -pf pw.txt identify 60
```

The temperatures and fans of a BMC that requires a BMC key, with the time of each step:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt -kf kg.txt -v sensors --type Temperature,Fan
```

The names of the sensors whose status is not `OK` (`UNAVAILABLE` ones included), from the JSON
Lines output filtered with `jq`. A discrete sensor that answers is `OK` whatever its `states`, a
power supply at `Failure detected` included: check the `states` of the discrete sensors to monitor
([The `Sensor` object](sensors.html#the-sensor-object)):

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt sensors --json | jq -r 'select(.status != "OK") | .name'
```

The system board FRU of a BMC that only offers cipher suite 3, on another port:

```bash
ipmicli -h bmc.example.com -P 6230 -u monitor -pf pw.txt -C 3 fru 0
```

The events logged since entry 643, the last one of the previous run:

```bash
ipmicli -h bmc.example.com -u monitor -pf pw.txt sel --since 643
```

A console on the serial port, closed with `~.`:

```bash
ipmicli -h bmc.example.com -u admin -pf pw.txt sol
```

The DCMI capabilities of a BMC, with a raw request:

```bash
ipmicli -h bmc.example.com -u admin -pf pw.txt raw 0x2c 0x01 0xdc 0x01
```

## ipmitool equivalents

| ipmitool | ipmicli |
| --- | --- |
| `ipmitool -I lanplus -H h -U u -P p mc info` (or `bmc info`) | `ipmicli -h h -u u -p p info` (the manufacturer as its IANA number, not a name) |
| `ipmitool -I lanplus ... chassis status` | `ipmicli ... chassis` |
| `ipmitool -I lanplus ... chassis power status` | `ipmicli ... power status` |
| `ipmitool -I lanplus ... chassis power on` (and `off`, `cycle`, `reset`, `soft`, `diag`) | `ipmicli ... power on` (same actions; the session is opened at the Operator level, not Administrator) |
| `ipmitool -I lanplus ... chassis identify [<seconds>]`, `chassis identify force` | `ipmicli ... identify [<seconds>]`, `identify force`, `identify off` (`chassis identify 0` in ipmitool) |
| `ipmitool -I lanplus ... sdr list` or `sensor list` | `ipmicli ... sensors` (`--long` for the thresholds) |
| `ipmitool -I lanplus ... sdr type Temperature` | `ipmicli ... sensors --type Temperature` |
| `ipmitool -I lanplus ... sensor get 'Fan 1'` | `ipmicli ... sensors --name 'Fan 1' --long` |
| `ipmitool -I lanplus ... fru print` | `ipmicli ... fru` |
| `ipmitool -I lanplus ... fru print 17` | `ipmicli ... fru 17` |
| `ipmitool -I lanplus ... sel list` or `sel elist` | `ipmicli ... sel` (the sensor numbers, not the names) |
| `ipmitool -I lanplus ... sel info` | `ipmicli ... -v sel --since <last id>` (the `SEL:` line of Get SEL Info goes to stderr; `sel` also reads the entries, and `--since` limits them to the new ones) |
| `ipmitool -I lanplus ... sol activate` | `ipmicli ... sol` |
| `ipmitool -I lanplus ... sol deactivate` | None: `sol` itself deactivates a console left active by a session the BMC no longer has, and never the console of a live session |
| `ipmitool -I lanplus ... dcmi ...`, `chassis bootdev`, `mc reset` | No subcommand: `raw` with the bytes of the command (see the DCMI [example](#examples)) |
| `ipmitool -I lanplus ... raw 0x06 0x01` | `ipmicli ... raw 0x06 0x01` (every byte hexadecimal, `0x` or not, where `ipmitool` reads `10` as decimal and `010` as octal; no leading space before the bytes; an error completion code exits with 70, 77 for `D4h`, and the message on stderr; odd network functions are refused) |
| `ipmitool -I lanplus ... lan print` (the cipher suites) | `ipmicli -h h probe` (no login needed) |
| `-I lanplus` | Implied: the CLI speaks RMCP+ (IPMI 2.0) only. |
| `-H host` | `-h host` |
| `-U user` | `-u user` |
| `-P password` | `-p password` |
| `-f file` (password file) | `-pf file` |
| `-E` (password from `IPMI_PASSWORD`) | None: no environment variable; `-pf file` |
| `-a` (prompt for the password) | The default when neither `-p` nor `-pf` is given |
| `-K` (Kg from `IPMI_KGKEY`), `-Y` (prompt for Kg) | `-kf file` (the key in hexadecimal) |
| `-p port` | `-P port` |
| `-k kg` (BMC key as text), `-y hexkg` | `-k hexkg` (hexadecimal only) |
| `-L USER` | The default of every read subcommand; `-L operator` or `-L administrator` to raise it, `-L auto` to raise it only when the BMC refuses a command (`D4h`). |
| `-C 3` | `-C 3` |
| `-N seconds`, `-R retries` | `--message-timeout <ms>`, `--retries <n>` |
| `-v` | `-v` (the connection details and the time of each step, on stderr) |
| `~.` to close `sol activate` | `~.` |

`ipmitool` prints a sensor reading as `na` when the BMC reports it unavailable: `ipmicli` prints
`unavailable` in the `Status` column and leaves `Reading` empty.
