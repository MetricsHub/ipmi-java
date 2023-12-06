package org.sentrysoftware.ipmi.core.coding.commands.chassis;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, Sentry Software
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

import org.sentrysoftware.ipmi.core.coding.commands.ResponseData;
import org.sentrysoftware.ipmi.core.common.TypeConverter;

/**
 * Wrapper for Get Chassis Status response.
 */
public class GetChassisStatusResponseData implements ResponseData {

    public static final String FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE = "Front Panel Button Capabilities not set";

    private byte currentPowerState;

    private byte lastPowerEvent;

    private byte miscChassisState;

    private boolean isFrontPanelButtonCapabilitiesSet;

    private byte frontPanelButtonCapabilities;

    public GetChassisStatusResponseData() {
        setFrontPanelButtonCapabilitiesSet(false);
    }

    public void setCurrentPowerState(byte currentPowerState) {
        this.currentPowerState = currentPowerState;
    }

    public byte getCurrentPowerState() {
        return currentPowerState;
    }

    public PowerRestorePolicy getPowerRestorePolicy() {
        switch ((currentPowerState & TypeConverter.intToByte(0x60)) >> 5) {
        case 0:
            return PowerRestorePolicy.PoweredOff;
        case 1:
            return PowerRestorePolicy.PowerRestored;
        case 2:
            return PowerRestorePolicy.PoweredUp;
        default:
            throw new IllegalArgumentException("Invalid Power Restore Policy");
        }
    }

    /**
     * @return True when controller attempted to turn system power on or off,
     *         but system did not enter desired state
     */
    public boolean isPowerControlFault() {
        return ((currentPowerState & TypeConverter.intToByte(0x10)) != 0);
    }

    /**
     * @return True when fault was detected in main power subsystem.
     */
    public boolean isPowerFault() {
        return ((currentPowerState & TypeConverter.intToByte(0x8)) != 0);
    }

    /**
     * @return True when interlock was detected (chassis is presently shut down
     *         because a chassis panel interlock switch is active)
     */
    public boolean isInterlock() {
        return ((currentPowerState & TypeConverter.intToByte(0x4)) != 0);
    }

    /**
     * @return True when system was shut down because of power overload
     *         condition.
     */
    public boolean isPowerOverload() {
        return ((currentPowerState & TypeConverter.intToByte(0x2)) != 0);
    }

    /**
     * @return True when system power is on.
     */
    public boolean isPowerOn() {
        return ((currentPowerState & TypeConverter.intToByte(0x1)) != 0);
    }

    public void setLastPowerEvent(byte lastPowerEvent) {
        this.lastPowerEvent = lastPowerEvent;
    }

    public byte getLastPowerEvent() {
        return lastPowerEvent;
    }

    /**
     * @return True when last 'Power is on' state was entered via IPMI command.
     */
    public boolean wasIpmiPowerOn() {
        return ((lastPowerEvent & TypeConverter.intToByte(0x10)) != 0);
    }

    /**
     * @return True if last power down caused by power fault.
     */
    public boolean wasPowerFault() {
        return ((lastPowerEvent & TypeConverter.intToByte(0x8)) != 0);
    }

    /**
     * @return True if last power down caused by a power interlock being
     *         activated.
     */
    public boolean wasInterlock() {
        return ((lastPowerEvent & TypeConverter.intToByte(0x4)) != 0);
    }

    /**
     * @return True if last power down caused by a Power overload.
     */
    public boolean wasPowerOverload() {
        return ((lastPowerEvent & TypeConverter.intToByte(0x2)) != 0);
    }

    /**
     * @return True if AC failed.
     */
    public boolean acFailed() {
        return ((lastPowerEvent & TypeConverter.intToByte(0x1)) != 0);

    }

    public void setMiscChassisState(byte miscChassisState) {
        this.miscChassisState = miscChassisState;
    }

    public byte getMiscChassisState() {
        return miscChassisState;
    }

    /**
     * @return True if Chassis Identify command and state info supported.
     */
    public boolean isChassisIdentifyCommandSupported() {
        return ((miscChassisState & TypeConverter.intToByte(0x40)) != 0);
    }

    public ChassisIdentifyState getChassisIdentifyState() {
        if (!isChassisIdentifyCommandSupported()) {
            throw new IllegalAccessError(
                    "Chassis Idetify command and state not supported");
        }

        return ChassisIdentifyState.parseInt((miscChassisState & TypeConverter
                .intToByte(0x30)) >> 4);
    }

    /**
     * @return True if cooling or fan fault was detected.
     */
    public boolean coolingFaultDetected() {
        return ((miscChassisState & TypeConverter.intToByte(0x8)) != 0);
    }

    /**
     * @return True if drive fault was detected.
     */
    public boolean driveFaultDetected() {
        return ((miscChassisState & TypeConverter.intToByte(0x4)) != 0);
    }

    /**
     * @return True if Front Panel Lockout active (power off and reset via
     *         chassis push-buttons disabled.).
     */
    public boolean isFrontPanelLockoutActive() {
        return ((miscChassisState & TypeConverter.intToByte(0x2)) != 0);
    }

    /**
     * @return True if Chassis intrusion active is active.
     */
    public boolean isChassisIntrusionActive() {
        return ((miscChassisState & TypeConverter.intToByte(0x1)) != 0);
    }

    public void setFrontPanelButtonCapabilities(
            byte frontPanelButtonCapabilities) {
        this.frontPanelButtonCapabilities = frontPanelButtonCapabilities;
        setFrontPanelButtonCapabilitiesSet(true);
    }

    public byte getFrontPanelButtonCapabilities() {
        return frontPanelButtonCapabilities;
    }

    /**
     * @return Standby (sleep) button disable is allowed.
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isStandbyButtonDisableAllowed()
            throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x80)) != 0);
    }

    /**
     * @return Diagnostic Interrupt button disable is allowed.
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isDiagnosticInterruptButtonDisableAllowed()
            throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x40)) != 0);
    }

    /**
     * @return Reset button disable is allowed.
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isResetButtonDisableAllowed() throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x20)) != 0);
    }

    /**
     * @return Power off button disable allowed (in the case there is a single
     *         combined power/standby (sleep) button, disabling power off also
     *         disables sleep requests via that button.)
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isPowerOffButtonDisableAllowed()
            throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x10)) != 0);
    }

    /**
     * @return Standby (sleep) button disabled.
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isStandbyButtonDisabled() throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x8)) != 0);
    }

    /**
     * @return Diagnostic Interrupt button disabled.
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isDiagnosticInterruptButtonDisabled()
            throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x4)) != 0);
    }

    /**
     * @return Reset button disabled.
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isResetButtonDisabled() throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x2)) != 0);
    }

    /**
     * @return Power off button disabled (in the case there is a single combined
     *         power/standby (sleep) button, disabling power off also disables
     *         sleep requests via that button are also disabled.)
     * @throws IllegalAccessException
     *             when Front Panel Button Capabilities wasn't set.
     */
    public boolean isPowerOffButtonDisabled() throws IllegalAccessException {
        if (!isFrontPanelButtonCapabilitiesSet()) {
            throw new IllegalAccessException(
                    FRONT_PANEL_BUTTON_CAPABILITIES_NOT_SET_MESSAGE);
        }
        return ((frontPanelButtonCapabilities & TypeConverter.intToByte(0x1)) != 0);
    }

    private void setFrontPanelButtonCapabilitiesSet(
            boolean isFrontPanelButtonCapabilitiesSet) {
        this.isFrontPanelButtonCapabilitiesSet = isFrontPanelButtonCapabilitiesSet;
    }

    public boolean isFrontPanelButtonCapabilitiesSet() {
        return isFrontPanelButtonCapabilitiesSet;
    }

}
