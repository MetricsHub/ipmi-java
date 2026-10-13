package org.metricshub.ipmi.core.coding.commands.chassis;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

/**
 * Specifies types of commands that can be sent to BMC via
 * {@link ChassisControl} command.
 */
public enum PowerCommand {
	/**
	 * Force system into soft off (S4/S45) state. This is for 'emergency'
	 * management power down actions. The command does not initiate a clean
	 * shut-down of the operating system prior to powering down the system.
	 */
	PowerDown(PowerCommand.POWERDOWN),
	PowerUp(PowerCommand.POWERUP),
	/**
	 * Power cycle (optional): a power off interval of at least 1 second, then power up. When the system is off,
	 * the BMC is recommended to do nothing and answer D5h; some power the system up instead. Check the power state
	 * first and only cycle a system that is on.
	 */
	PowerCycle(PowerCommand.POWERCYCLE),
	/**
	 * Hard reset. In some implementations, the BMC may not know whether a reset
	 * will cause any particular effect and will pulse the system reset signal
	 * regardless of power state. If the implementation can tell that no action
	 * will occur if a reset is delivered in a given power state, then it is
	 * recommended (but still optional) that a D5h 'Request parameter(s) not
	 * supported in present state.' error completion code be returned.
	 */
	HardReset(PowerCommand.HARDRESET),
	/**
	 * Pulse a diagnostic interrupt (optional) that goes directly to the processors, typically to make the
	 * operating system write a diagnostic dump: an NMI on IA-32 systems.
	 */
	DiagnosticInterrupt(PowerCommand.DIAGNOSTICINTERRUPT),
	/**
	 * Initiate a soft shutdown of the operating system via ACPI (optional), by emulating a fatal overtemperature.
	 */
	SoftShutdown(PowerCommand.SOFTSHUTDOWN),;

	private static final int POWERDOWN = 0;
	private static final int POWERUP = 1;
	private static final int POWERCYCLE = 2;
	private static final int HARDRESET = 3;
	private static final int DIAGNOSTICINTERRUPT = 4;
	private static final int SOFTSHUTDOWN = 5;

	private int code;

	PowerCommand(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static PowerCommand parseInt(int value) {
		switch (value) {
		case POWERDOWN:
			return PowerDown;
		case POWERUP:
			return PowerUp;
		case POWERCYCLE:
			return PowerCycle;
		case HARDRESET:
			return HardReset;
		case DIAGNOSTICINTERRUPT:
			return DiagnosticInterrupt;
		case SOFTSHUTDOWN:
			return SoftShutdown;
		default:
			throw new IllegalArgumentException("Invalid value: " + value);
		}
	}
}
