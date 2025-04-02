package org.metricshub.ipmi.core.coding.commands.sdr.record;

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
 * Indicates whether the sensor is monitoring an input or output relative to the
 * given Entity. E.g. if the sensor is monitoring a current, this can be used to
 * specify whether it is an input voltage or an output voltage.
 */
public enum SensorDirection {
    Unspecified(SensorDirection.UNSPECIFIED),
    Input(SensorDirection.INPUT),
    Output(SensorDirection.OUTPUT),
    Reserved(SensorDirection.RESERVED), ;

    private static final int UNSPECIFIED = 0;
    private static final int INPUT = 1;
    private static final int OUTPUT = 2;
    private static final int RESERVED = 3;

    private int code;

    SensorDirection(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SensorDirection parseInt(int value) {
        switch (value) {
        case UNSPECIFIED:
            return Unspecified;
        case INPUT:
            return Input;
        case OUTPUT:
            return Output;
        case RESERVED:
            return Reserved;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
