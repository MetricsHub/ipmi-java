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

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * The reply to Get Chassis Status (IPMI 2.0 table 28-3): the current power state, the last power event, the
 * miscellaneous chassis state and, when the BMC sends it, the front panel button capabilities. The byte numbers below
 * are those of the table, where byte 1 is the completion code.
 */
public class GetChassisStatusResponseData implements ResponseData {

	/**
	 * The message of the {@link IllegalAccessException} that the front panel button getters throw when the reply has
	 * no front panel button capabilities byte.
	 */
	public static final String FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE = "Front Panel Button Capabilities not set";

	private byte currentPowerState;

	private byte lastPowerEvent;

	private byte miscChassisState;

	private boolean isFrontPanelButtonCapabilitiesSet;

	private byte frontPanelButtonCapabilities;

	/**
	 * Creates an empty reply, without front panel button capabilities.
	 */
	public GetChassisStatusResponseData() {
		setFrontPanelButtonCapabilitiesSet(false);
	}

	/**
	 * Sets the current power state byte.
	 *
	 * @param currentPowerState the current power state byte (byte 2)
	 */
	public void setCurrentPowerState(byte currentPowerState) {
		this.currentPowerState = currentPowerState;
	}

	/**
	 * Returns the current power state byte as the BMC sent it (byte 2), which the power getters below decode.
	 *
	 * @return the current power state byte
	 */
	public byte getCurrentPowerState() {
		return currentPowerState;
	}

	/**
	 * Returns the power restore policy: what the chassis does when AC power returns (byte 2 bits 6:5).
	 *
	 * @return the power restore policy, {@link PowerRestorePolicy#Unknown} for 11b
	 */
	public PowerRestorePolicy getPowerRestorePolicy() {
		switch ((currentPowerState & TypeConverter.intToByte(0x60)) >> 5) {
		case 0:
			return PowerRestorePolicy.PoweredOff;
		case 1:
			return PowerRestorePolicy.PowerRestored;
		case 2:
			return PowerRestorePolicy.PoweredUp;
		default:
			// 11b is "unknown" in IPMI 2.0 Table 28-3
			return PowerRestorePolicy.Unknown;
		}
	}

	/**
	 * Tells whether the controller attempted to turn the system power on or off and the system did not enter the
	 * desired state (byte 2 bit 4).
	 *
	 * @return True when controller attempted to turn system power on or off,
	 *         but system did not enter desired state
	 */
	public boolean isPowerControlFault() {
		return (currentPowerState & TypeConverter.intToByte(0x10)) != 0;
	}

	/**
	 * Tells whether a fault was detected in the main power subsystem (byte 2 bit 3).
	 *
	 * @return True when fault was detected in main power subsystem.
	 */
	public boolean isPowerFault() {
		return (currentPowerState & TypeConverter.intToByte(0x8)) != 0;
	}

	/**
	 * Tells whether the chassis is shut down because a chassis panel interlock switch is active (byte 2 bit 2).
	 *
	 * @return True when interlock was detected (chassis is presently shut down
	 *         because a chassis panel interlock switch is active)
	 */
	public boolean isInterlock() {
		return (currentPowerState & TypeConverter.intToByte(0x4)) != 0;
	}

	/**
	 * Tells whether the system was shut down because of a power overload (byte 2 bit 1).
	 *
	 * @return True when system was shut down because of power overload
	 *         condition.
	 */
	public boolean isPowerOverload() {
		return (currentPowerState & TypeConverter.intToByte(0x2)) != 0;
	}

	/**
	 * Tells whether the system power is on (byte 2 bit 0).
	 *
	 * @return True when system power is on.
	 */
	public boolean isPowerOn() {
		return (currentPowerState & TypeConverter.intToByte(0x1)) != 0;
	}

	/**
	 * Sets the last power event byte.
	 *
	 * @param lastPowerEvent the last power event byte (byte 3)
	 */
	public void setLastPowerEvent(byte lastPowerEvent) {
		this.lastPowerEvent = lastPowerEvent;
	}

	/**
	 * Returns the last power event byte as the BMC sent it (byte 3).
	 *
	 * @return the last power event byte
	 */
	public byte getLastPowerEvent() {
		return lastPowerEvent;
	}

	/**
	 * Tells whether the last 'Power is on' state was entered with an IPMI command (byte 3 bit 4).
	 *
	 * @return True when last 'Power is on' state was entered via IPMI command.
	 */
	public boolean wasIpmiPowerOn() {
		return (lastPowerEvent & TypeConverter.intToByte(0x10)) != 0;
	}

	/**
	 * Tells whether the last power down was caused by a power fault (byte 3 bit 3).
	 *
	 * @return True if last power down caused by power fault.
	 */
	public boolean wasPowerFault() {
		return (lastPowerEvent & TypeConverter.intToByte(0x8)) != 0;
	}

	/**
	 * Tells whether the last power down was caused by a power interlock being activated (byte 3 bit 2).
	 *
	 * @return True if last power down caused by a power interlock being
	 *         activated.
	 */
	public boolean wasInterlock() {
		return (lastPowerEvent & TypeConverter.intToByte(0x4)) != 0;
	}

	/**
	 * Tells whether the last power down was caused by a power overload (byte 3 bit 1).
	 *
	 * @return True if last power down caused by a Power overload.
	 */
	public boolean wasPowerOverload() {
		return (lastPowerEvent & TypeConverter.intToByte(0x2)) != 0;
	}

	/**
	 * Tells whether the AC failed (byte 3 bit 0).
	 *
	 * @return True if AC failed.
	 */
	public boolean acFailed() {
		return (lastPowerEvent & TypeConverter.intToByte(0x1)) != 0;

	}

	/**
	 * Sets the miscellaneous chassis state byte.
	 *
	 * @param miscChassisState the miscellaneous chassis state byte (byte 4)
	 */
	public void setMiscChassisState(byte miscChassisState) {
		this.miscChassisState = miscChassisState;
	}

	/**
	 * Returns the miscellaneous chassis state byte as the BMC sent it (byte 4).
	 *
	 * @return the miscellaneous chassis state byte
	 */
	public byte getMiscChassisState() {
		return miscChassisState;
	}

	/**
	 * Tells whether the BMC supports the Chassis Identify command and reports its state (byte 4 bit 6).
	 *
	 * @return True if Chassis Identify command and state info supported.
	 */
	public boolean isChassisIdentifyCommandSupported() {
		return (miscChassisState & TypeConverter.intToByte(0x40)) != 0;
	}

	/**
	 * Returns the state of the chassis identify (byte 4 bits 5:4), which the BMC reports when it supports the Chassis
	 * Identify command and its state ({@link #isChassisIdentifyCommandSupported()}).
	 *
	 * @return the chassis identify state
	 */
	public ChassisIdentifyState getChassisIdentifyState() {
		if (!isChassisIdentifyCommandSupported()) {
			throw new IllegalAccessError(
					"Chassis Idetify command and state not supported");
		}

		return ChassisIdentifyState
				.parseInt(
						(miscChassisState
								& TypeConverter
										.intToByte(0x30)) >> 4);
	}

	/**
	 * Tells whether a cooling or fan fault was detected (byte 4 bit 3).
	 *
	 * @return True if cooling or fan fault was detected.
	 */
	public boolean coolingFaultDetected() {
		return (miscChassisState & TypeConverter.intToByte(0x8)) != 0;
	}

	/**
	 * Tells whether a drive fault was detected (byte 4 bit 2).
	 *
	 * @return True if drive fault was detected.
	 */
	public boolean driveFaultDetected() {
		return (miscChassisState & TypeConverter.intToByte(0x4)) != 0;
	}

	/**
	 * Tells whether the front panel lockout is active: power off and reset with the chassis push-buttons are disabled
	 * (byte 4 bit 1).
	 *
	 * @return True if Front Panel Lockout active (power off and reset via
	 *         chassis push-buttons disabled.).
	 */
	public boolean isFrontPanelLockoutActive() {
		return (miscChassisState & TypeConverter.intToByte(0x2)) != 0;
	}

	/**
	 * Tells whether a chassis intrusion is active (byte 4 bit 0).
	 *
	 * @return True if Chassis intrusion is active.
	 */
	public boolean isChassisIntrusionActive() {
		return (miscChassisState & TypeConverter.intToByte(0x1)) != 0;
	}

	/**
	 * Sets the front panel button capabilities byte, which makes the front panel button getters available.
	 *
	 * @param frontPanelButtonCapabilities the front panel button capabilities byte (byte 5)
	 */
	public void setFrontPanelButtonCapabilities(
			byte frontPanelButtonCapabilities) {
		this.frontPanelButtonCapabilities = frontPanelButtonCapabilities;
		setFrontPanelButtonCapabilitiesSet(true);
	}

	/**
	 * Returns the front panel button capabilities byte as the BMC sent it (byte 5, optional).
	 *
	 * @return the front panel button capabilities byte, 0 when the reply has none
	 *         ({@link #isFrontPanelButtonCapabilitiesSet()})
	 */
	public byte getFrontPanelButtonCapabilities() {
		return frontPanelButtonCapabilities;
	}

	/**
	 * Tells whether the standby (sleep) button can be disabled (byte 5 bit 7).
	 *
	 * @return Standby (sleep) button disable is allowed.
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isStandbyButtonDisableAllowed()
			throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x80)) != 0;
	}

	/**
	 * Tells whether the diagnostic interrupt button can be disabled (byte 5 bit 6).
	 *
	 * @return Diagnostic Interrupt button disable is allowed.
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isDiagnosticInterruptButtonDisableAllowed()
			throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x40)) != 0;
	}

	/**
	 * Tells whether the reset button can be disabled (byte 5 bit 5).
	 *
	 * @return Reset button disable is allowed.
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isResetButtonDisableAllowed() throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x20)) != 0;
	}

	/**
	 * Tells whether the power off button can be disabled (byte 5 bit 4).
	 *
	 * @return Power off button disable allowed (in the case there is a single
	 *         combined power/standby (sleep) button, disabling power off also
	 *         disables sleep requests via that button.)
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isPowerOffButtonDisableAllowed()
			throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x10)) != 0;
	}

	/**
	 * Tells whether the standby (sleep) button is disabled (byte 5 bit 3).
	 *
	 * @return Standby (sleep) button disabled.
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isStandbyButtonDisabled() throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x8)) != 0;
	}

	/**
	 * Tells whether the diagnostic interrupt button is disabled (byte 5 bit 2).
	 *
	 * @return Diagnostic Interrupt button disabled.
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isDiagnosticInterruptButtonDisabled()
			throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x4)) != 0;
	}

	/**
	 * Tells whether the reset button is disabled (byte 5 bit 1).
	 *
	 * @return Reset button disabled.
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isResetButtonDisabled() throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x2)) != 0;
	}

	/**
	 * Tells whether the power off button is disabled (byte 5 bit 0).
	 *
	 * @return Power off button disabled (in the case there is a single combined
	 *         power/standby (sleep) button, the sleep requests of that button are
	 *         also disabled.)
	 * @throws IllegalAccessException
	 *         when Front Panel Button Capabilities wasn't set.
	 */
	public boolean isPowerOffButtonDisabled() throws IllegalAccessException {
		if (!isFrontPanelButtonCapabilitiesSet()) {
			throw new IllegalAccessException(
					FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
		}
		return (frontPanelButtonCapabilities & TypeConverter.intToByte(0x1)) != 0;
	}

	private void setFrontPanelButtonCapabilitiesSet(
			boolean frontPanelButtonCapabilitiesSet) {
		this.isFrontPanelButtonCapabilitiesSet = frontPanelButtonCapabilitiesSet;
	}

	/**
	 * Tells whether the reply has the optional front panel button capabilities byte (byte 5): the front panel button
	 * getters throw {@link IllegalAccessException} when it does not.
	 *
	 * @return whether the reply has the front panel button capabilities byte
	 */
	public boolean isFrontPanelButtonCapabilitiesSet() {
		return isFrontPanelButtonCapabilitiesSet;
	}

}
