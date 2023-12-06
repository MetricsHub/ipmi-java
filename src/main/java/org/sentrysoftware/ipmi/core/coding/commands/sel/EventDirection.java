package org.sentrysoftware.ipmi.core.coding.commands.sel;

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

public enum EventDirection {

    Assertion(EventDirection.ASSERTION),
    Deassertion(EventDirection.DEASSERTION), ;

    private static final int ASSERTION = 0;
    private static final int DEASSERTION = 1;

    private int code;

    EventDirection(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static EventDirection parseInt(int value) {
        switch (value) {
        case ASSERTION:
            return Assertion;
        case DEASSERTION:
            return Deassertion;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
