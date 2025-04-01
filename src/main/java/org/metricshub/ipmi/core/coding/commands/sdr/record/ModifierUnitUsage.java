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

public enum ModifierUnitUsage {

    None(ModifierUnitUsage.NONE),
    /**
     * Unit = Basic Unit / Modifier Unit
     */
    Divide(ModifierUnitUsage.DIVIDE),
    /**
     * Unit = Basic Unit * Modifier Unit
     */
    Mulitply(ModifierUnitUsage.MULITPLY), ;

    private static final int NONE = 0;
    private static final int DIVIDE = 1;
    private static final int MULITPLY = 2;

    private int code;

    ModifierUnitUsage(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ModifierUnitUsage parseInt(int value) {
        switch (value) {
        case NONE:
            return None;
        case DIVIDE:
            return Divide;
        case MULITPLY:
            return Mulitply;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
