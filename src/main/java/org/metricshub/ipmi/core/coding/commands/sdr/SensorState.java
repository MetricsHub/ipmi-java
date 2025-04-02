package org.metricshub.ipmi.core.coding.commands.sdr;

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
 * Represents state of threshold-based sensor.
 */
public enum SensorState {
    BelowLowerNonRecoverable(SensorState.BELOWLOWERNONRECOVERABLE), AboveUpperNonCritical(
            SensorState.ABOVEUPPERNONCRITICAL), AboveUpperNonRecoverable(
            SensorState.ABOVEUPPERNONRECOVERABLE), BelowLowerNonCritical(
            SensorState.BELOWLOWERNONCRITICAL), BelowLowerCritical(
            SensorState.BELOWLOWERCRITICAL), AboveUpperCritical(
            SensorState.ABOVEUPPERCRITICAL), Ok(SensorState.OK), Invalid(
            SensorState.INVALID);
    private static final int BELOWLOWERNONRECOVERABLE = 4;
    private static final int ABOVEUPPERNONCRITICAL = 8;
    private static final int ABOVEUPPERNONRECOVERABLE = 32;
    private static final int BELOWLOWERNONCRITICAL = 1;
    private static final int BELOWLOWERCRITICAL = 2;
    private static final int ABOVEUPPERCRITICAL = 16;
    private static final int OK = 0;
    private static final int INVALID = -1;

    private int code;

    SensorState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SensorState parseInt(int value) {
        if ((value & BELOWLOWERNONRECOVERABLE) != 0) {
            return BelowLowerNonRecoverable;
        }
        if ((value & BELOWLOWERCRITICAL) != 0) {
            return BelowLowerCritical;
        }
        if ((value & ABOVEUPPERNONCRITICAL) != 0) {
            return BelowLowerNonCritical;
        }
        if ((value & ABOVEUPPERNONRECOVERABLE) != 0) {
            return AboveUpperNonRecoverable;
        }
        if ((value & ABOVEUPPERCRITICAL) != 0) {
            return AboveUpperCritical;
        }
        if ((value & ABOVEUPPERNONCRITICAL) != 0) {
            return AboveUpperNonCritical;
        }
        if (value == OK) {
            return Ok;
        }
        return Invalid;
    }
}
