keywords: prerequisites, ipmi over lan, bmc, tested bmcs, udp 623, firewall, user, operator, privilege level, cipher suites, kg, two-key, capabilities, probe, cve-2013-4786, ipmitool, ipmiutil
description: Prerequisites on the BMC — the tested BMCs, enabling IPMI over LAN, the account and its privilege level, the cipher suites the client can use, the BMC key, the firewall, and how to check what the BMC offers and whether the account can log in.

# Preparing the BMC

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Three things must be true on the **Baseboard Management Controller** of the monitored server
before this client can talk to it:

1. **IPMI over LAN is enabled**, on UDP port 623, and reachable through the network,
2. the **account** is allowed to log in over the LAN channel with at least the **User**
   privilege, and
3. the BMC offers at least one **cipher suite** the client can use, one that signs its messages
   (see [Cipher suites](#cipher-suites)).

Nothing has to be installed on the server or in its operating system: the client talks to the BMC
only, and works whether the server is powered on or off, as long as the BMC has standby power.

## What this client needs

| Requirement | Detail |
| --- | --- |
| IPMI over LAN | Enabled on the LAN channel of the BMC (usually channel 1). Many recent BMCs ship with it **disabled**. |
| IPMI 2.0 (RMCP+) | The client only opens RMCP+ sessions. IPMI 1.5-only BMCs (RMCP with MD2/MD5 or straight password authentication) are not supported: the client refuses them at once with `IpmiClientException: The BMC ... does not support IPMI 2.0 / RMCP+` ([Troubleshooting](troubleshooting.html#the-bmc-does-not-support-ipmi-2-0)), or, with [`cipherSuite(id)`](configuration.html#cipher-suites), which skips that check, fails with `IpmiConnectionException` when Open Session gets no reply ([Troubleshooting](troubleshooting.html#ipmiconnectionexception)). |
| UDP port 623 | Open from the machine running the client to the BMC. The port can be changed with [`port(int)`](configuration.html#host-and-port) on the builder (`-P` with `ipmicli`). |
| An account | Enabled, with a password, allowed to log in over IPMI on the LAN channel. |
| Privilege level | **User**: the device ID, the chassis status, the SEL, and the sensors and FRUs of the BMC itself. **Operator**: the power and identify commands, and on most BMCs the [sensors](sensors.html#sensor-owners) and [FRUs](fru-inventory.html#frus-of-other-controllers) of the other controllers, which the BMC reaches with Send Message (User is enough on an HPE iLO 5, Administrator is needed on an AMI-based GIGABYTE BMC). **Administrator**: [Serial over LAN](serial-over-lan.html) on most BMCs (the level the BMC configures for SOL, `Privilege Level` in `ipmitool sol info 1`). A client built with `privilege(PrivilegeLevel.MaximumAvailable)` runs the session at User and raises it only when a command needs more, as far as the account allows ([Raising the session on demand](configuration.html#raising-the-session-on-demand)). |
| A supported cipher suite | Suite **3** or **17** in practice; see [Cipher suites](#cipher-suites). |

## Tested BMCs

The library is tested on these BMCs, whose answers and quirks the pages of this site cite: Dell
iDRAC 6, 7 and 8, HPE iLO 5, Lenovo IMM, Lenovo XClarity Controller (XCC), Supermicro, Cisco IMC,
Fujitsu iRMC S5, and an AMI-based GIGABYTE BMC. Other IPMI 2.0 BMCs are expected to work: report
the ones that do not on the [issue tracker](https://github.com/MetricsHub/ipmi-java/issues).

## Enabling IPMI over LAN

On most BMCs it is a single option of the web interface, usually in the network or security
settings: *IPMI Over LAN* (Dell iDRAC), *IPMI/DCMI over LAN* (HPE iLO), *IPMI over LAN* (Lenovo
XCC and Cisco IMC), *IPMI* in the network services of Supermicro and most ASPEED/AMI firmwares.

The same settings usually hold a privilege limit for the whole LAN channel (*Channel Privilege
Level Limit* on Dell iDRAC, *Privilege Level Limit* on Cisco IMC) and the BMC key (*Encryption
Key*, see [BMC key (Kg)](#bmc-key-kg)). No session goes above the lower of the channel's limit
and the account's own IPMI privilege on that channel: allow both at least the level the client
needs (see [What this client needs](#what-this-client-needs)).

From the server's operating system (Linux, as root, with the IPMI driver loaded:
`modprobe -a ipmi_si ipmi_devintf`), `ipmitool` does the same through the local KCS interface:

```bash
# Show the LAN channel configuration (IP address, enabled cipher suites, ...)
ipmitool lan print 1

# Enable IPMI messaging on LAN channel 1
ipmitool lan set 1 access on
```

## Creating the account

Use an account dedicated to monitoring, with the lowest privilege that allows what you collect.
The client requests the level set with [`privilege()`](configuration.html#privilege-level) on the
builder, **User** by default. User covers the read operations of
[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) on the BMC itself (Get Device
ID, Get Chassis Status, Get SDR, Get Sensor Reading, Read FRU Data, Get SEL Entry, ...). The
sensors and FRUs of the other controllers (power supplies, fans, RAID adapter...), which the BMC
reaches with Send Message, need **Operator** on most BMCs (Administrator on an AMI-based
GIGABYTE BMC), and Operator also allows the power and identify commands. To read these sensors
and FRUs, give the account that level and build the client with
`privilege(PrivilegeLevel.MaximumAvailable)`: the session runs at User and is raised only when the
BMC refuses a command at that level
([Raising the session on demand](configuration.html#raising-the-session-on-demand)). With
`ipmitool`, assuming user slot 3 is free:

```bash
ipmitool user list 1
ipmitool user set name 3 monitor
ipmitool user set password 3
ipmitool user enable 3
# Allow IPMI over LAN for this user, with the User privilege (2); privilege=3 for Operator
ipmitool channel setaccess 1 3 link=on ipmi=on callin=on privilege=2
```

> [!NOTE]
> User names are at most **16 bytes** and passwords at most **20 bytes** in UTF-8: `build()`
> refuses a longer one with `IllegalArgumentException`. Some older BMCs store only 16 bytes of
> password. The account must also be enabled *and* allowed on the LAN channel (`ipmi=on`): an
> account that can log in to the web interface is not necessarily allowed to log in over IPMI.

If the BMC sets a maximum privilege per cipher suite (`Cipher Suite Priv Max` in
`ipmitool lan print 1`), the suite the client uses must allow the level of the session: `USER`
for the read operations, `OPERATOR` for the power and identify commands and, on most BMCs, for
the sensors and FRUs of other controllers, `ADMINISTRATOR` for Serial over LAN on most BMCs. A
client built with `privilege(PrivilegeLevel.MaximumAvailable)` raises the session no higher than
that maximum.

For [Serial over LAN](serial-over-lan.html#prerequisites), SOL must also be enabled on the BMC
(`ipmitool sol set enabled true 1`), the SOL payload enabled for the account
(`ipmitool sol payload enable 1 3`), and the account allowed the level the BMC requires for SOL
(`Privilege Level` in `ipmitool sol info 1`): Administrator on most BMCs, `privilege=4` in
`ipmitool channel setaccess`.

> [!WARNING]
> IPMI 2.0 lets any host that reaches UDP port 623 obtain, for a valid user name, an HMAC of the
> password from the RAKP handshake (CVE-2013-4786) and guess the password offline; no cipher
> suite or BMC key prevents it. Keep the BMCs on a management network that only the monitoring
> hosts reach, give the monitoring account a long random password used nowhere else, and disable
> the accounts that do not need IPMI.

## Cipher suites

An RMCP+ session uses a **cipher suite**: one authentication algorithm (for the RAKP handshake),
one integrity algorithm (signing the messages) and one confidentiality algorithm (encrypting
them). The *Supported* column says which suites the library implements; `IpmiClient` refuses the
suites without integrity, which only the
[low-level API](low-level-api.html#choosing-the-cipher-suite) can use:

| Suite | Authentication | Integrity | Confidentiality | Supported |
| --- | --- | --- | --- | --- |
| 0 | none | none | none | low-level API only (avoid it) |
| 1 | RAKP-HMAC-SHA1 | none | none | low-level API only |
| 2 | RAKP-HMAC-SHA1 | HMAC-SHA1-96 | none | yes |
| **3** | RAKP-HMAC-SHA1 | HMAC-SHA1-96 | AES-CBC-128 | **yes** |
| 4, 5 | RAKP-HMAC-SHA1 | HMAC-SHA1-96 | xRC4-128, xRC4-40 | no |
| 6 | RAKP-HMAC-MD5 | none | none | low-level API only |
| 7 | RAKP-HMAC-MD5 | HMAC-MD5-128 | none | yes |
| 8 | RAKP-HMAC-MD5 | HMAC-MD5-128 | AES-CBC-128 | yes |
| 9, 10 | RAKP-HMAC-MD5 | HMAC-MD5-128 | xRC4-128, xRC4-40 | no |
| 11 | RAKP-HMAC-MD5 | MD5-128 | none | yes |
| 12 | RAKP-HMAC-MD5 | MD5-128 | AES-CBC-128 | yes |
| 13, 14 | RAKP-HMAC-MD5 | MD5-128 | xRC4-128, xRC4-40 | no |
| 15 | RAKP-HMAC-SHA256 | none | none | low-level API only |
| 16 | RAKP-HMAC-SHA256 | HMAC-SHA256-128 | none | yes |
| **17** | RAKP-HMAC-SHA256 | HMAC-SHA256-128 | AES-CBC-128 | **yes** |
| 18, 19 | RAKP-HMAC-SHA256 | HMAC-SHA256-128 | xRC4-128, xRC4-40 | no |

Suites **3** and **17** sign and encrypt every message, and every current BMC offers at least
one of them. Prefer **17** where available. The xRC4 suites are not implemented: IPMI 2.0 table
13-19 makes xRC4 optional for BMCs, and none of the tested BMCs requires it. MD5-128 (suites 11 and
12) is a plain MD5 of the password around each message rather than an HMAC of the session key: it
is implemented for the older BMCs that offer nothing better, and comes last in the default
preference.

### How `IpmiClient` chooses the suite

By default (`cipherSuites(17, 3, 8, 16, 2, 7, 12, 11)`), the client first asks the BMC for its
list of cipher suites (Get Channel Cipher Suites) when it opens its session, then picks the first of
these suites that the BMC offers:

1. **17**, then **3**, then 8: messages signed and encrypted;
2. 16, then 2, then 7: messages signed, not encrypted;
3. 12, then 11: messages signed with MD5-128, encrypted or not.

A Lenovo XCC that offers suites 1 to 19 gets suite 17; a Cisco IMC that offers 0 to 14 gets
suite 3. A BMC that offers none of the eight fails the first operation with:

```text
IpmiClientException: The BMC ... offers none of the cipher suites [17, 3, 8, 16, 2, 7, 12, 11] (offered: ...)
```

In addition:

* The client never chooses a suite without integrity (0, 1, 6, 15), even when the BMC offers
  nothing else, and `cipherSuites(...)` and `cipherSuite(id)` on the builder refuse them with
  `IllegalArgumentException`: the list of suites is not authenticated, so an attacker on the
  network could otherwise make the client fall back to a session whose replies can be forged.
* The algorithms of a suite are those of the table above, whatever the BMC's record of the suite
  says.
* The OEM suites a BMC may list (as Cisco IMC does) are ignored.
* The client opens the session with the suite it chose, and does not try another one when the
  BMC refuses it. Some BMCs list a suite they refuse at Open Session: a Lenovo XCC lists suite 12,
  a Cisco IMC suite 11. The login then fails with `IpmiClientException` (*refused the session: No
  Cipher Suite match with proposed security algorithms.*): list with `cipherSuites(...)` the
  suites the BMC accepts
  ([The BMC refuses the session](troubleshooting.html#the-bmc-refuses-the-session)).

To control the suite:

* **restrict the suites offered by the BMC** — `ipmitool lan print 1` lists them
  (`RMCP+ Cipher Suites`, with the maximum privilege of each in `Cipher Suite Priv Max`), and the
  web interface or `ipmitool lan set 1 cipher_privs` can disable the ones you do not want; or
* list the suites the client may use with **`cipherSuites(...)`** on the builder, by order of
  preference, or set **`cipherSuite(id)`**: the client then skips the discovery and always uses
  that suite, which fails on a BMC that does not offer it
  ([Configuration](configuration.html#cipher-suites)); or
* open the session yourself with the [low-level API](low-level-api.html#choosing-the-cipher-suite).

## BMC key (Kg)

Some BMCs can be configured with a **BMC key (Kg)** for *two-key* logins: the session keys are
then derived from this key instead of the user's password. `bmcKeyRequired()` of
[`capabilities()`](#checking-what-the-bmc-offers) (`BMC key (Kg)` in `ipmicli probe`, no login
needed) tells whether a BMC has one. Pass it as raw bytes with `bmcKey()` to the
[builder](configuration.html#bmc-key), at most 20 bytes: the bytes of the text given to
`ipmitool -k`, or the decoded hexadecimal of `ipmitool -y` and `ipmicli -k`. Most BMCs have no
key (all zeros, which the client treats as no key): leave `bmcKey()` unset then.

## Firewall

| From | To | Protocol / port |
| --- | --- | --- |
| The machine running the client (any local port) | The BMC | **UDP 623** (RMCP / RMCP+), or the port set with [`port(int)`](configuration.html#host-and-port) |
| The BMC (the same port) | The machine running the client (the same local port) | UDP replies |

A stateful firewall needs the outbound rule only. Each client binds one ephemeral local UDP port
when it first contacts the BMC (`capabilities()` included), keeps it for all its sessions (a new
session after a lost one, and the Serial over LAN one, included) and releases it in `close()`
([Host and port](configuration.html#host-and-port)), so a stateless firewall must accept UDP
replies from the BMC's port on the whole ephemeral range.

[Serial over LAN](serial-over-lan.html) may use another UDP port: the BMC returns the port of the
SOL payload when it is activated, usually the same one (623 on every lab BMC the console was
tested with), and the console then opens a second session on that port, with the same
credentials and BMC key. Allow it too if your BMC announces a different one.

## Checking what the BMC offers

`capabilities()` reads what the BMC offers without credentials and without opening a session
(Get Channel Authentication Capabilities and Get Channel Cipher Suites); it returns a
[`BmcCapabilities`](apidocs/org/metricshub/ipmi/client/BmcCapabilities.html).
[`ipmicli probe`](cli.html#probe) prints the same, and the suite a login would choose.

```java
try (IpmiClient bmc = IpmiClient.builder(host).build()) {
	BmcCapabilities offer = bmc.capabilities();
	for (CipherSuite suite : offer.cipherSuites()) {
		System.out.println(suite.getId() + (suite.isSupported() ? "" : " (not implemented)"));
	}
	System.out.println("BMC key required: " + offer.bmcKeyRequired());
}
```

| Method | Content |
| --- | --- |
| `cipherSuites()` | The standard suites the BMC offers, in its order, with their algorithms (the OEM suites are left out); [`CipherSuite.isSupported()`](apidocs/org/metricshub/ipmi/core/coding/security/CipherSuite.html) tells whether the library implements a suite. |
| `bmcKeyRequired()` | Whether the BMC key (Kg) is set: give it with [`bmcKey()`](configuration.html#bmc-key). |
| `anonymousLogin()`, `nullUserNames()`, `nonNullUserNames()` | The logins the BMC accepts: anonymous (null user name and null password), null user name with a password, and whether at least one user has a non-null user name. |
| `perMessageAuthentication()`, `userLevelAuthentication()` | Whether the BMC authenticates every message of a session rather than the login only, and the commands of the User level. |
| `ipmi20()` | Always `true`: the call fails with `IpmiClientException` for a BMC without IPMI 2.0 (RMCP+). |
| `data()` | The reply to Get Channel Authentication Capabilities, as the protocol core decodes it (`GetChannelAuthenticationCapabilitiesResponseData`). |

The call fails with `IpmiConnectionException` when the host name does not resolve or the BMC does
not answer.

## Checking access

Run the same request from the machine that will run the client, with the same account, privilege
level and cipher suite: if it fails there, the problem is on the BMC or in the network, not in
the client.

```bash
# ipmitool: RMCP+ (lanplus), User privilege, cipher suite 17
ipmitool -I lanplus -H bmc.example.com -U monitor -P 'the-password' -L USER -C 17 chassis status
ipmitool -I lanplus -H bmc.example.com -U monitor -P 'the-password' -L USER -C 17 sdr elist

# The sensors of other controllers (Send Message), with an account allowed Operator
ipmitool -I lanplus -H bmc.example.com -U monitor -P 'the-password' -L OPERATOR -C 17 sdr elist

# ipmiutil: IPMI LAN 2.0 (-F lan2), cipher suite 17 (-J 17), User privilege (-V 2)
ipmiutil health -N bmc.example.com -U monitor -P 'the-password' -F lan2 -J 17 -V 2
```

The [command-line client](cli.html) tests the login with the library itself:
`ipmicli -h bmc.example.com -u monitor -pf pw.txt -v chassis` sends a single command, and its exit
code and message name the cause of a failure
([Troubleshooting](troubleshooting.html#first-steps)).

See [Troubleshooting](troubleshooting.html) for the usual failures.
