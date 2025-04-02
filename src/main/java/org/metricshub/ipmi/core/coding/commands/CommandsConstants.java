package org.metricshub.ipmi.core.coding.commands;

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

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Set of constants. Byte constants are encoded as pseudo unsigned bytes.
 * IpmiLanConstants doesn't use {@link TypeConverter} because fields need to be
 * runtime constants.
 * 
 * @see TypeConverter#byteToInt(byte)
 * @see TypeConverter#intToByte(int)
 */
public final class CommandsConstants {

    /**
     * Highest available authentication level
     */
    public static final byte AL_HIGHEST_AVAILABLE = 0x00;

    /**
     * Authentication level = Callback
     */
    public static final byte AL_CALLBACK = 0x01;

    /**
     * Authentication level = User
     */
    public static final byte AL_USER = 0x02;

    /**
     * Authentication level = Operator
     */
    public static final byte AL_OPERATOR = 0x03;

    /**
     * Authentication level = Administrator
     */
    public static final byte AL_ADMINISTRATOR = 0x04;

    /**
     * OEM-defined authentication level
     */
    public static final byte AL_OEM = 0x05;

    private CommandsConstants() {
    }
}
