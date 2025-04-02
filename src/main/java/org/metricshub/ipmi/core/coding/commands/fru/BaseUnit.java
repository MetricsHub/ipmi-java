package org.metricshub.ipmi.core.coding.commands.fru;

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
 * Represents unit which is used to access FRU
 */
public enum BaseUnit {
    Bytes(BaseUnit.BYTES), Words(BaseUnit.WORDS), ;
    private static final int BYTES = 0;
    private static final int WORDS = 1;
    private static final int BYTESIZE = 1;
    private static final int WORDSIZE = 16;

    private int code;

    BaseUnit(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BaseUnit parseInt(int value) {
        switch (value) {
        case BYTES:
            return Bytes;
        case WORDS:
            return Words;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }

    /**
     * Returns size of the unit in bytes.
     */
    public int getSize() {
        switch (this) {
        case Bytes:
            return BYTESIZE;
        case Words:
            return WORDSIZE;
        default:
            throw new IllegalArgumentException("Invalid value: " + this);
        }
    }
}
