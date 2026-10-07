keywords: chassis status, power state, power on, power off, power restore policy, intrusion, fault, get chassis status
description: Read the chassis status of a server through its BMC — power state, last power event, power restore policy, faults, intrusion and front panel flags.

# Chassis Status

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

The IPMI **Get Chassis Status** command reports whether the server is powered on, why it last
changed power state, what it does when mains power returns, and a few fault and front panel
flags. It is a single request: reading it is fast, and it is a good way to check that the
credentials work.

## As text

```java
String status = IpmiClient.getChassisStatusAsStringResult(config);
// "System power state is up" or "System power state is down"
```

The text reports the power state only.

## As an object

```java
GetChassisStatusResponseData status = IpmiClient.getChassisStatus(config);

System.out.println("Power: " + (status.isPowerOn() ? "on" : "off"));
System.out.println("Restore policy: " + status.getPowerRestorePolicy());
System.out.println("Intrusion: " + status.isChassisIntrusionActive());
```

[`GetChassisStatusResponseData`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/GetChassisStatusResponseData.html)
decodes the response (IPMI 2.0, section 28.2):

| Method | Meaning |
| --- | --- |
| **Current power state** | |
| `isPowerOn()` | System power is on |
| `isPowerOverload()` | The system was shut down because of a power overload |
| `isInterlock()` | A power interlock (a switch that cuts power when the chassis is open) is active |
| `isPowerFault()` | A fault was detected in the main power subsystem |
| `isPowerControlFault()` | The power controller tried to change the power state and failed |
| `getPowerRestorePolicy()` | What happens when mains power returns: `PoweredOff`, `PowerRestored` (back to the previous state) or `PoweredUp` |
| **Last power event** | |
| `wasIpmiPowerOn()` | The last power-on was requested through IPMI |
| `wasPowerFault()` | The last power-down was caused by a power fault |
| `wasInterlock()` | The last power-down was caused by a power interlock |
| `wasPowerOverload()` | The last power-down was caused by a power overload |
| `acFailed()` | Mains (AC) power was lost |
| **Miscellaneous chassis state** | |
| `isChassisIntrusionActive()` | The chassis intrusion sensor is active (the case is or was open) |
| `isFrontPanelLockoutActive()` | The power off and reset buttons of the front panel are disabled |
| `driveFaultDetected()` | A drive fault was detected |
| `coolingFaultDetected()` | A cooling or fan fault was detected |
| `isChassisIdentifyCommandSupported()`, `getChassisIdentifyState()` | Whether the identify LED can be read, and its state: `Off`, `TemporaryOn`, `IndefiniteOn` |
| **Front panel buttons** (optional in the response) | |
| `isFrontPanelButtonCapabilitiesSet()` | The BMC returned the front panel button byte; the methods below throw `IllegalAccessException` otherwise |
| `isPowerOffButtonDisabled()`, `isResetButtonDisabled()`, ... | State of each button, and whether it can be disabled (`...DisableAllowed()`) |

> [!NOTE]
> `getChassisIdentifyState()` throws `IllegalAccessError` when
> `isChassisIdentifyCommandSupported()` is `false`: check it first. `getPowerRestorePolicy()`
> throws `IllegalArgumentException` when the BMC reports the policy as *unknown*
> ([#87](https://github.com/metricshub/ipmi-java/issues/87)).

Not every BMC fills every flag: the intrusion, drive and cooling bits in particular are optional
in the specification, and a BMC that does not implement them reports `false`.

## Controlling the power

`IpmiClient` only reads. To power the server on or off or reset it, send the
[`ChassisControl`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/ChassisControl.html)
command with the [low-level API](low-level-api.html#sending-commands), in a session opened with
the **Operator** or **Administrator** privilege. The supported power commands are `PowerDown`,
`PowerUp` and `HardReset`. Exposing power control in `IpmiClient` is tracked in
[#104](https://github.com/metricshub/ipmi-java/issues/104).
