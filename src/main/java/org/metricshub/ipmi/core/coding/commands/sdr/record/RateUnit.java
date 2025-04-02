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

public enum RateUnit {
    Microseconds(RateUnit.US, "us"), Miliseconds(RateUnit.MS, "ms"), Seconds(
            RateUnit.S, "s"), Minutes(RateUnit.MIN, "min"), Hours(RateUnit.H,
            "h"), Days(RateUnit.D, "days"), None(RateUnit.NONE, "") ;

    private static final int NONE = 0x0;
    private static final int US = 0x1;
    private static final int MS = 0x2;
    private static final int S = 0x3;
    private static final int MIN = 0x4;
    private static final int H = 0x5;
    private static final int D = 0x6;

    private int code;
    private String text;

    RateUnit(int code, String value) {
        this.code = code;
        this.text = value;
    }

    public int getCode() {
        return code;
    }

    public String getUnit() {
        return text;
    }

    public static RateUnit parseInt(int value) {
        switch (value) {
        case NONE:
            return None;
        case US:
            return Microseconds;
        case MS:
            return Miliseconds;
        case S:
            return Seconds;
        case MIN:
            return Minutes;
        case H:
            return Hours;
        case D:
            return Days;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
