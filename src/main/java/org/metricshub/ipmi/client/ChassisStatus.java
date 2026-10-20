package org.metricshub.ipmi.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.metricshub.ipmi.core.coding.commands.chassis.ChassisIdentifyState;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerRestorePolicy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The state of the chassis: power, faults, intrusion, identify LED (Get Chassis Status, IPMI 2.0 table 28-3).
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The decoded reply of the protocol core is handed out "
		+ "by reference for the callers that need its every field")
public final class ChassisStatus {

	private final GetChassisStatusResponseData data;

	/**
	 * Creates the status from the reply of the BMC.
	 *
	 * @param data the reply to Get Chassis Status
	 */
	ChassisStatus(GetChassisStatusResponseData data) {
		this.data = data;
	}

	/**
	 * Tells whether the system is powered on.
	 *
	 * @return true when the system power is on
	 */
	public boolean powerOn() {
		return data.isPowerOn();
	}

	/**
	 * Returns what the system does when AC power returns after it was lost.
	 *
	 * @return the power restore policy
	 */
	public PowerRestorePolicy powerRestorePolicy() {
		return data.getPowerRestorePolicy();
	}

	/**
	 * Returns what caused the last power down or power on: several causes can be flagged at once.
	 *
	 * @return the causes, empty when the BMC flags none
	 */
	public Set<PowerEvent> lastPowerEvents() {
		Set<PowerEvent> events = EnumSet.noneOf(PowerEvent.class);
		if (data.acFailed()) {
			events.add(PowerEvent.AC_FAILED);
		}
		if (data.wasPowerOverload()) {
			events.add(PowerEvent.POWER_OVERLOAD);
		}
		if (data.wasInterlock()) {
			events.add(PowerEvent.INTERLOCK);
		}
		if (data.wasPowerFault()) {
			events.add(PowerEvent.POWER_FAULT);
		}
		if (data.wasIpmiPowerOn()) {
			events.add(PowerEvent.IPMI_COMMAND);
		}
		return Collections.unmodifiableSet(events);
	}

	/**
	 * Tells whether a power fault is active.
	 *
	 * @return true when the power fault is active
	 */
	public boolean powerFault() {
		return data.isPowerFault();
	}

	/**
	 * Tells whether the system was shut down because of a power overload.
	 *
	 * @return true when the system was shut down because of a power overload
	 */
	public boolean powerOverload() {
		return data.isPowerOverload();
	}

	/**
	 * Tells whether the chassis interlock is active: the system is powered down because a panel is open.
	 *
	 * @return true when the interlock is active
	 */
	public boolean interlock() {
		return data.isInterlock();
	}

	/**
	 * Tells whether the last power control command failed.
	 *
	 * @return true when the power control fault is active
	 */
	public boolean powerControlFault() {
		return data.isPowerControlFault();
	}

	/**
	 * Tells whether a cooling or fan fault was detected.
	 *
	 * @return true when a cooling fault was detected
	 */
	public boolean coolingFault() {
		return data.coolingFaultDetected();
	}

	/**
	 * Tells whether a drive fault was detected.
	 *
	 * @return true when a drive fault was detected
	 */
	public boolean driveFault() {
		return data.driveFaultDetected();
	}

	/**
	 * Tells whether a chassis intrusion is active.
	 *
	 * @return true when the intrusion is active
	 */
	public boolean intrusion() {
		return data.isChassisIntrusionActive();
	}

	/**
	 * Tells whether the front panel is locked out.
	 *
	 * @return true when the front panel lockout is active
	 */
	public boolean frontPanelLockout() {
		return data.isFrontPanelLockoutActive();
	}

	/**
	 * Returns the state of the chassis identify LED.
	 *
	 * @return the identify state ({@link ChassisIdentifyState#Unknown} when the BMC reports the reserved value 11b),
	 *         empty when the BMC does not report it
	 */
	public Optional<ChassisIdentifyState> identifyState() {
		return data.isChassisIdentifyCommandSupported() ? Optional.of(data.getChassisIdentifyState()) : Optional.empty();
	}

	/**
	 * Returns the reply of the BMC, as the protocol core decodes it.
	 *
	 * @return the reply to Get Chassis Status
	 */
	public GetChassisStatusResponseData data() {
		return data;
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder("Power ")
				.append(powerOn() ? "on" : "off")
				.append(", restore policy ")
				.append(powerRestorePolicy())
				.append(", last power event ")
				.append(lastPowerEvents());
		if (powerFault()) {
			text.append(", power fault");
		}
		if (powerOverload()) {
			text.append(", power overload");
		}
		if (interlock()) {
			text.append(", interlock");
		}
		if (powerControlFault()) {
			text.append(", power control fault");
		}
		if (coolingFault()) {
			text.append(", cooling fault");
		}
		if (driveFault()) {
			text.append(", drive fault");
		}
		if (intrusion()) {
			text.append(", intrusion");
		}
		if (frontPanelLockout()) {
			text.append(", front panel lockout");
		}
		identifyState().ifPresent(state -> text.append(", identify ").append(state));
		return text.toString();
	}
}
