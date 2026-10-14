keywords: chassis status, power state, power on, power off, power cycle, reset, chassis control, chassis identify, identify led, power restore policy, intrusion, fault, get chassis status
description: Read the chassis status of a server through its BMC — power state, last power event, power restore policy, faults, intrusion and identify LED — and control its power and identify LED.

# Chassis Status

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The IPMI **Get Chassis Status** command reports whether the server is powered on, why it last
changed power state, what it does when mains power returns, and a few fault and front panel
flags. It is a single request: reading it is fast, and it is a good way to check that the
credentials work.

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
(IPMI 2.0, section 28.2) and never throws:

| Method | Meaning |
| --- | --- |
| `powerOn()` | System power is on |
| `powerRestorePolicy()` | What happens when mains power returns: `PoweredOff`, `PowerRestored` (back to the previous state), `PoweredUp`, or `Unknown` when the BMC reports it as such |
| `lastPowerEvents()` | Why the system last changed power state, as a set of [`PowerEvent`](apidocs/org/metricshub/ipmi/client/PowerEvent.html): `AC_FAILED`, `POWER_OVERLOAD`, `INTERLOCK`, `POWER_FAULT`, `IPMI_COMMAND`; several can be flagged at once |
| `powerFault()` | A fault was detected in the main power subsystem |
| `powerOverload()` | The system was shut down because of a power overload |
| `interlock()` | A power interlock (a switch that cuts power when the chassis is open) is active |
| `powerControlFault()` | The power controller tried to change the power state and failed |
| `coolingFault()` | A cooling or fan fault was detected |
| `driveFault()` | A drive fault was detected |
| `intrusion()` | The chassis intrusion sensor is active (the case is or was open) |
| `frontPanelLockout()` | The power off and reset buttons of the front panel are disabled |
| `identifyState()` | The state of the identify LED (`Off`, `TemporaryOn`, `IndefiniteOn`), or empty when the BMC does not report it |
| `data()` | The raw [`GetChassisStatusResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/GetChassisStatusResponseData.html), for the front panel button flags and the raw bytes |

Not every BMC fills every flag: the intrusion, drive and cooling bits in particular are optional
in the specification, and a BMC that does not implement them reports `false`.

## Controlling the power

`chassisControl(PowerCommand)` sends the **Chassis Control** command (IPMI 2.0, section 28.3).
It needs a client built with the **Operator** or **Administrator**
[privilege](configuration.html#privilege-level), which the client requests at login and sets
right after it (Set Session Privilege Level): at the default User level the BMC refuses the
command with `D4h`, and an account not allowed Operator fails the first operation with
`IpmiAuthenticationException`.

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password)
		.privilege(PrivilegeLevel.Operator).build()) {
	if (!bmc.chassisStatus().powerOn()) {
		bmc.chassisControl(PowerCommand.PowerUp);
	}
}
```

| `PowerCommand` | Effect |
| --- | --- |
| `PowerUp` | Power the system on |
| `PowerDown` | Power it off at once (soft off, S4/S5), without shutting the operating system down |
| `PowerCycle` | Power it off for at least a second, then on. Optional: a BMC should refuse it with `D5h` when the system is off, but some power the system on instead, so check `powerOn()` first |
| `HardReset` | Pulse the reset signal. A BMC that can tell it would do nothing (the system is off) should refuse it with `D5h` |
| `SoftShutdown` | Ask the operating system to shut down through ACPI, by emulating a fatal overtemperature (optional) |
| `DiagnosticInterrupt` | Pulse a diagnostic interrupt, an NMI on x86, so that the operating system writes a diagnostic dump (optional) |

The BMC answers as soon as it **accepts** the command, and carries it out afterwards: a power
off takes a few seconds, a soft shutdown as long as the operating system needs. Poll
`chassisStatus().powerOn()` to see the state change. A refused command fails with
[`IpmiCompletionCodeException`](timeouts-and-errors.html#exceptions): `D4h` at a session below
Operator, `D5h` for a command the system's present state does not allow, `CCh` for an optional
command the BMC does not implement.

Unlike the read operations, a control command is **sent once**: not again when its reply is
lost or is a `C3h` (a timeout on the BMC side, which says the response is unavailable, not that
nothing was done), since the BMC may have carried it out and a second reset would reset the
system twice, and not on a new session. A lost reply fails with `IpmiTimeoutException`, a `C3h`
with `IpmiCompletionCodeException`, and `chassisStatus()` then tells what the BMC did; a busy
BMC (`C0h`), which did nothing, is retried as for any command. `PowerCycle` is refused with `IllegalStateException`, before anything is
sent, when the system is off: the specification recommends that check, as some BMCs power the
system up instead of doing nothing.

## The identify LED

`chassisIdentify(int seconds)` sends the **Chassis Identify** command (IPMI 2.0, section 28.5):
the chassis identifies itself, typically by blinking a LED, for the given number of seconds (1 to
255; the BMC may stretch it by 20%). `chassisIdentify(0)` turns the identify off, and
`chassisIdentifyForever()` keeps it on until then, with the optional "force identify on" byte of
the command, which not every BMC implements (`CCh` from those that do not). Both need the
Operator privilege, are sent once like the power commands, and `identifyState()` of the
[chassis status](#the-chassisstatus-object) reports the result on the BMCs that support it:

```java
bmc.chassisIdentify(30);
System.out.println(bmc.chassisStatus().identifyState()); // Optional[TemporaryOn]
bmc.chassisIdentify(0);
```
