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
 * Chassis Identify State.
 */
public enum ChassisIdentifyState {
    Off(ChassisIdentifyState.OFF),
    TemporaryOn(ChassisIdentifyState.TEMPORARYON),
    IndefiniteOn(ChassisIdentifyState.INDEFINITEON), ;

    private static final int OFF = 0;
    private static final int TEMPORARYON = 1;
    private static final int INDEFINITEON = 2;

    private int code;

    ChassisIdentifyState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ChassisIdentifyState parseInt(int value) {
        switch (value) {
        case OFF:
            return Off;
        case TEMPORARYON:
            return TemporaryOn;
        case INDEFINITEON:
            return IndefiniteOn;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
