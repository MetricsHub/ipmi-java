keywords: chassis status, power state, power on, power off, power restore policy, intrusion, fault, get chassis status
description: Read the chassis status of a server through its BMC — power state, last power event, power restore policy, faults, intrusion and identify LED.

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

## As text

```java
String text = IpmiResultConverter.convertResult(bmc.chassisStatus());
// "System power state is up" or "System power state is down"
```

The text reports the power state only.

## Controlling the power

`chassisStatus()` only reads. To power the server on or off or reset it, send the
[`ChassisControl`](apidocs/org/metricshub/ipmi/core/coding/commands/chassis/ChassisControl.html)
command with [`send()`](low-level-api.html#sending-a-command-through-the-client), on a client
built with the **Operator** or **Administrator** privilege. The supported power commands are
`PowerDown`, `PowerUp` and `HardReset`:

```java
try (IpmiClient bmc = IpmiClient.builder(host).credentials(user, password)
		.privilege(PrivilegeLevel.Operator).build()) {
	bmc.send(new ChassisControl(PowerCommand.PowerUp));
}
```

A dedicated `power()` operation is tracked in
[#104](https://github.com/metricshub/ipmi-java/issues/104).
