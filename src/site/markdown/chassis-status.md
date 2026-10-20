keywords: chassis status, power state, power on, power off, power cycle, reset, chassis control, chassis identify, identify led, power restore policy, intrusion, fault, get chassis status
description: Read the chassis status of a server through its BMC — power state, last power event, power restore policy, faults, intrusion and identify LED — and control its power and identify LED.

# Chassis Status

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The IPMI **Get Chassis Status** command reports whether the server is powered on, why it last
changed power state, what it does when mains power returns, and a few fault and front panel
flags. It is a single request: reading it is fast, and it is a good way to check that the
credentials work. The page then covers the two commands that act on the chassis:
[Chassis Control](#controlling-the-power) (power on, power off, power cycle, hard reset, soft
shutdown, diagnostic interrupt) and [Chassis Identify](#the-identify-led) (the identify LED).

## The `ChassisStatus` object

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password).build()) {
	ChassisStatus status = bmc.chassisStatus();

	System.out.println("Power: " + (status.powerOn() ? "on" : "off"));
	System.out.println("Restore policy: " + status.powerRestorePolicy());
	System.out.println("Intrusion: " + status.intrusion());
}
```

[`ChassisStatus`](apidocs/org/metricshub/ipmi/client/ChassisStatus.html) decodes the response
(IPMI 2.0 section 28.2) and never throws:

| Method | Meaning |
| --- | --- |
| `powerOn()` | System power is on |
| `powerRestorePolicy()` | What happens when mains power returns, a [`PowerRestorePolicy`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/PowerRestorePolicy.html): `PoweredOff`, `PowerRestored` (back to the previous state), `PoweredUp`, or `Unknown` when the BMC reports it as such |
| `lastPowerEvents()` | The last power events, as a set of [`PowerEvent`](apidocs/org/metricshub/ipmi/client/PowerEvent.html): `AC_FAILED` (the AC supply was lost), `POWER_OVERLOAD`, `INTERLOCK` and `POWER_FAULT` (what caused the last power down), `IPMI_COMMAND` (the last power on was requested by an IPMI command). Several can be flagged at once, and the set is empty when the BMC flags none |
| `powerFault()` | A fault was detected in the main power subsystem |
| `powerOverload()` | The system was shut down because of a power overload |
| `interlock()` | A power interlock (a switch that cuts power when the chassis is open) is active |
| `powerControlFault()` | The power controller tried to change the power state and failed |
| `coolingFault()` | A cooling or fan fault was detected |
| `driveFault()` | A drive fault was detected |
| `intrusion()` | The chassis intrusion sensor is active (the case is or was open) |
| `frontPanelLockout()` | The power off and reset buttons of the front panel are disabled |
| `identifyState()` | The state of the identify LED, a [`ChassisIdentifyState`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/ChassisIdentifyState.html) (`Off`, `TemporaryOn`, `IndefiniteOn`), or empty when the BMC does not report it |
| `data()` | The raw [`GetChassisStatusResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/GetChassisStatusResponseData.html): the raw bytes (`getCurrentPowerState()`, `getLastPowerEvent()`, `getMiscChassisState()`) and the front panel button flags, such as `isPowerOffButtonDisabled()`. The button byte is optional: when the BMC did not send it, the button flags throw the checked `IllegalAccessException`, so check `isFrontPanelButtonCapabilitiesSet()` first |

Not every BMC fills every flag: a BMC that does not monitor the chassis intrusion, the drives or
the fans reports `false` for those bits, so `false` does not prove that there is no fault. The
specification marks only the identify state and the front panel button byte as optional
(IPMI 2.0 table 28-3).

`chassisStatus()` needs the User privilege, the default. It opens the session when needed, is
retried once on a new session when the session was lost while it ran, and fails with an
[`IpmiClientException`](timeouts-and-errors.html#exceptions) when the login is refused or the BMC
does not answer.

## Controlling the power

`chassisControl(PowerCommand)` sends the **Chassis Control** command (IPMI 2.0 section 28.3).
It needs the **Operator** privilege or higher
([Privilege level](configuration.html#privilege-level)):

* Built with `privilege(PrivilegeLevel.Operator)` or `Administrator`, the client requests that
  level at login and sets it right after the handshake with Set Session Privilege Level. When the
  account is not allowed that level, the first operation that opens the session fails with
  `IpmiAuthenticationException`, even a read.
* Built with `privilege(PrivilegeLevel.MaximumAvailable)`, the session runs at User and is raised
  to Operator when the BMC refuses the command with `D4h`. The BMC did not carry the refused
  command out, so the client then sends it again
  ([Raising the session on demand](configuration.html#raising-the-session-on-demand)). The `D4h`
  reaches the caller only when the BMC does not allow the account Operator.
* At the default User level, the BMC refuses the command with `D4h` (`IpmiCompletionCodeException`).

`PowerCommand` and
[`PrivilegeLevel`](apidocs/org/metricshub/ipmi/core/coding/commands/PrivilegeLevel.html) are
enums of the protocol core: `org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand` and
`org.metricshub.ipmi.core.coding.commands.PrivilegeLevel`.

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password)
		.privilege(PrivilegeLevel.Operator).build()) {
	if (!bmc.chassisStatus().powerOn()) {
		bmc.chassisControl(PowerCommand.PowerUp);
	}
}
```

| [`PowerCommand`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/PowerCommand.html) | Effect |
| --- | --- |
| `PowerUp` | Power the system on |
| `PowerDown` | Power it off at once (soft off, S4/S5), without shutting the operating system down |
| `PowerCycle` | Power it off for at least a second, then on. Optional: a BMC should refuse it with `D5h` when the system is off, but some power the system on instead, so `chassisControl()` reads the power state first and throws `IllegalStateException` when the system is off (use `PowerUp`) |
| `HardReset` | Pulse the reset signal. A BMC that can tell it would do nothing (the system is off) should refuse it with `D5h` |
| `SoftShutdown` | Ask the operating system to shut down through ACPI, by emulating a fatal overtemperature (optional) |
| `DiagnosticInterrupt` | Pulse a diagnostic interrupt, an NMI on x86, so that the operating system writes a diagnostic dump (optional) |

The BMC answers as soon as it **accepts** the command, and carries it out afterwards: a power
off takes a few seconds, a soft shutdown as long as the operating system needs. Poll
`chassisStatus().powerOn()` to see the state change. A refused command fails with
[`IpmiCompletionCodeException`](timeouts-and-errors.html#exceptions): `D4h` at a session below
Operator, `D5h` for a command the system's present state does not allow, `CCh` for an optional
command the BMC does not implement.

Unlike the read operations, a control command is **sent once**:

* After a lost reply, it is not sent again, since the BMC may have carried it out and a second
  reset would reset the system twice. The operation fails with `IpmiTimeoutException`, the
  session is kept, and `chassisStatus()` tells what the BMC did.
* A `C3h` reply (a timeout on the BMC side: the response is unavailable, which does not mean that
  nothing was done) fails with `IpmiCompletionCodeException` at once, for the same reason, and
  `chassisStatus()` then tells what the BMC did.
* A command that failed because the session was lost is not replayed on a new session.
* A busy BMC (`C0h`) did nothing: the command is sent again, as for any command.
* A command refused with `D4h` was not carried out either: a session raised on demand sends it
  again at the higher level (above).

`PowerCycle` reads the power state first: when Get Chassis Status reports the system off,
`chassisControl()` throws `IllegalStateException` without sending Chassis Control. The
specification recommends that a BMC do nothing in that state, and some BMCs power the system up
instead. Use `PowerUp` to power it on.

## The identify LED

`chassisIdentify(int seconds)` sends the **Chassis Identify** command (IPMI 2.0 section 28.5):
the chassis identifies itself, typically by blinking an LED, for the given number of seconds,
1 to 255 (the BMC may stretch it by 20%). To keep it on longer, send the command again before it
expires. `chassisIdentify(0)` turns the identify off. `chassisIdentifyForever()` keeps the
identify on until `chassisIdentify(0)` turns it off, with the optional "force identify on" byte
of the command.

* Both methods need the Operator privilege (`D4h` below it, and a session raised on demand is
  raised as for the power commands) and are sent once, like the power commands.
* `chassisIdentify(int)` throws `IllegalArgumentException`, before anything is sent, when
  `seconds` is not between 0 and 255.
* A BMC without Chassis Identify refuses both with `C1h`, and a BMC without the force byte refuses
  `chassisIdentifyForever()` with `CCh` (`IpmiCompletionCodeException`).

`identifyState()` of the [chassis status](#the-chassisstatus-object) reports the result on the
BMCs that support it:

```java
bmc.chassisIdentify(30);
System.out.println(bmc.chassisStatus().identifyState()); // Optional[TemporaryOn]
bmc.chassisIdentify(0);
```

## From the command line

`ipmicli ... chassis` prints the chassis status, and `power status` prints just `on` or `off`.
`power on|off|cycle|reset|soft|diag` sends `PowerUp`, `PowerDown`, `PowerCycle`, `HardReset`,
`SoftShutdown` or `DiagnosticInterrupt`, and `identify [<seconds>|force|off]` drives the identify
LED (15 seconds by default). `ipmicli` logs in at the Operator privilege by default for the power
actions and `identify`. See [`chassis` and `power status`](cli.html#chassis-and-power-status) and
[`power` actions and `identify`](cli.html#power-actions-and-identify).
