keywords: prerequisites, ipmi over lan, bmc, udp 623, firewall, user, privilege level, cipher suites, kg, two-key, ipmitool, ipmiutil
description: Prerequisites on the BMC — enabling IPMI over LAN, the account and its privilege level, the cipher suites the client can use, the BMC key, and the firewall.

# Preparing the BMC

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Three things must be true on the **Baseboard Management Controller** of the monitored server
before this client can talk to it:

1. **IPMI over LAN is enabled**, on UDP port 623, and reachable through the network,
2. the **account** is allowed to log in over the LAN channel with at least the **User**
   privilege, and
3. the BMC offers at least one **cipher suite** the client implements.

Nothing has to be installed on the server or in its operating system: the client talks to the BMC
only, and works whether the server is powered on or off, as long as the BMC has standby power.

## What this client needs

| Requirement | Detail |
| --- | --- |
| IPMI over LAN | Enabled on the LAN channel of the BMC (usually channel 1). Many recent BMCs ship with it **disabled**. |
| IPMI 2.0 (RMCP+) | The client only opens RMCP+ sessions. IPMI 1.5-only BMCs (RMCP with MD2/MD5 or straight password authentication) are not supported. |
| UDP port 623 | Open from the machine running the client to the BMC. The port can be changed with [`setPort()`](configuration.html#host-and-port). |
| An account | Enabled, with a password, allowed to log in over IPMI on the LAN channel. |
| Privilege level | **User** for everything `IpmiClient` does; **Administrator** for [Serial over LAN](serial-over-lan.html). |
| A supported cipher suite | Suite **3** or **17** in practice; see [Cipher suites](#cipher-suites). |

## Enabling IPMI over LAN

On most BMCs it is a single option of the web interface, usually in the network or security
settings: *IPMI Over LAN* (Dell iDRAC), *IPMI/DCMI over LAN* (HPE iLO), *IPMI over LAN* (Lenovo
XClarity Controller), *IPMI* in the network services of Supermicro and most ASPEED/AMI firmwares.

From the server's operating system, with `ipmitool` and the local KCS interface, the same is done
with:

```bash
# Show the LAN channel configuration (IP address, enabled cipher suites, ...)
ipmitool lan print 1

# Enable IPMI messaging on LAN channel 1
ipmitool lan set 1 access on
```

## Creating the account

Use an account dedicated to monitoring, with the **User** privilege only: everything
[`IpmiClient`](apidocs/org/metricshub/ipmi/client/IpmiClient.html) sends (Get Chassis Status,
Get SDR, Get Sensor Reading, Read FRU Data, ...) is allowed at the User level, and the client
always requests that level for its sessions. With `ipmitool`, assuming user slot 3 is free:

```bash
ipmitool user list 1
ipmitool user set name 3 monitor
ipmitool user set password 3
ipmitool user enable 3
# Allow IPMI over LAN for this user, with the User privilege (2)
ipmitool channel setaccess 1 3 link=on ipmi=on callin=on privilege=2
```

> [!NOTE]
> IPMI passwords are at most **20 bytes** (16 on older BMCs), and the account must be enabled
> *and* allowed on the LAN channel (`ipmi=on`): an account that can log in to the web interface
> is not necessarily allowed to log in over IPMI.

If the BMC enforces a maximum privilege per cipher suite (`Cipher Suite Priv Max` in
`ipmitool lan print 1`), make sure the suite the client uses allows at least `USER`.

## Cipher suites

An RMCP+ session uses a **cipher suite**: one authentication algorithm (for the RAKP handshake),
one integrity algorithm (signing the messages) and one confidentiality algorithm (encrypting
them). The client implements:

| Suite | Authentication | Integrity | Confidentiality | Supported |
| --- | --- | --- | --- | --- |
| 0 | none | none | none | yes (avoid it) |
| 1 | RAKP-HMAC-SHA1 | none | none | yes |
| 2 | RAKP-HMAC-SHA1 | HMAC-SHA1-96 | none | yes |
| **3** | RAKP-HMAC-SHA1 | HMAC-SHA1-96 | AES-CBC-128 | **yes** |
| 4, 5 | RAKP-HMAC-SHA1 | HMAC-SHA1-96 | xRC4-128, xRC4-40 | no |
| 6 | RAKP-HMAC-MD5 | none | none | yes |
| 7 | RAKP-HMAC-MD5 | HMAC-MD5-128 | none | yes |
| 8 | RAKP-HMAC-MD5 | HMAC-MD5-128 | AES-CBC-128 | yes |
| 9, 10 | RAKP-HMAC-MD5 | HMAC-MD5-128 | xRC4-128, xRC4-40 | no |
| 11 – 14 | RAKP-HMAC-MD5 | MD5-128 | none, AES-CBC-128, xRC4 | no |
| 15 | RAKP-HMAC-SHA256 | none | none | yes |
| 16 | RAKP-HMAC-SHA256 | HMAC-SHA256-128 | none | yes |
| **17** | RAKP-HMAC-SHA256 | HMAC-SHA256-128 | AES-CBC-128 | **yes** |
| 18, 19 | RAKP-HMAC-SHA256 | HMAC-SHA256-128 | xRC4-128, xRC4-40 | no |

Suites **3** and **17** sign and encrypt every message, and every current BMC offers at least
one of them. Prefer **17** where available.

### How `IpmiClient` chooses the suite

By default (`skipAuth` set to `false`), each call first asks the BMC for its list of cipher
suites (Get Channel Cipher Suites), then picks one **by its position in that list**: the 4th
suite if the BMC offers at least 4, otherwise the 3rd, the 2nd, or the only one. A BMC that
offers `3, 17` gets suite 17; a BMC that offers `0, 1, 2, 3, 17` gets suite 3.

The suite is not checked against the table above. If the position rule lands on a suite the
client does not implement (an xRC4 or MD5-128 suite), the session cannot be opened. To control the
suite:

* **restrict the suites offered by the BMC** — `ipmitool lan print 1` lists them
  (`RMCP+ Cipher Suites`), and the web interface or `ipmitool lan set 1 cipher_privs` can disable
  the ones you do not want; or
* set **`skipAuth` to `true`**: the client then skips the discovery and always uses
  **suite 3** with the User privilege, which fails on a BMC that does not offer suite 3
  ([Configuration](configuration.html#skipauth)); or
* open the session yourself with the [low-level API](low-level-api.html#choosing-the-cipher-suite).

## BMC key (Kg)

Some BMCs can be configured with a **BMC key (Kg)** for *two-key* logins: the session keys are
then derived from this key instead of the user's password. When it is set, pass it as the
`bmcKey` of the [configuration](configuration.html#bmc-key) (the same key as `ipmitool -k` or
`-y`). Leave `bmcKey` to `null` otherwise: this is the default on virtually every BMC.

## Firewall

| From | To | Protocol / port |
| --- | --- | --- |
| The machine running the client (any local port) | The BMC | **UDP 623** (RMCP / RMCP+) |
| The BMC (port 623) | The machine running the client (the same local port) | UDP replies |

A stateful firewall needs the outbound rule only. Each session binds its own ephemeral local UDP
port, so a stateless firewall must accept UDP replies from port 623 on the whole ephemeral range.

## Checking access with `ipmitool` or `ipmiutil`

Run the same request from the machine that will run the client, with the same account, privilege
level and cipher suite: if it fails there, the problem is on the BMC or in the network, not in
the client.

```bash
# ipmitool: RMCP+ (lanplus), User privilege, cipher suite 17
ipmitool -I lanplus -H bmc.example.com -U monitor -P 'the-password' -L USER -C 17 chassis status
ipmitool -I lanplus -H bmc.example.com -U monitor -P 'the-password' -L USER -C 17 sdr elist

# ipmiutil: IPMI LAN 2.0 (-F lan2), cipher suite 17 (-J 17), User privilege (-V 2)
ipmiutil health -N bmc.example.com -U monitor -P 'the-password' -F lan2 -J 17 -V 2
```

See [Troubleshooting](troubleshooting.html) for the usual failures.
