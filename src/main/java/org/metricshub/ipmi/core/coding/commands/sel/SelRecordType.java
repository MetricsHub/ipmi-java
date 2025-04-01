package org.metricshub.ipmi.core.coding.commands.sel;

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

public enum SelRecordType {
    OemTimestamped(SelRecordType.OEMTIMESTAMPED), System(SelRecordType.SYSTEM), OemNonTimestamped(
            SelRecordType.OEMNONTIMESTAMPED), ;
    /**
     * Represents OEM timestamped record type (C0h-DFh)
     */
    private static final int OEMTIMESTAMPED = 192;
    private static final int SYSTEM = 2;
    /**
     * Represents OEM timestamped record type (E0h-FFh)
     */
    private static final int OEMNONTIMESTAMPED = 224;

    private int code;

    SelRecordType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SelRecordType parseInt(int value) {
        if (value == SYSTEM) {
            return System;
        }
        if (value > OEMNONTIMESTAMPED) {
            return OemNonTimestamped;
        }
        if (value > OEMTIMESTAMPED) {
            return OemTimestamped;
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }
}
