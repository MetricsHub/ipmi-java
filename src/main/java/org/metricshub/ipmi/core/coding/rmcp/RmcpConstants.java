package org.metricshub.ipmi.core.coding.rmcp;

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
 * Set of constants.
 * Byte constants are encoded as pseudo unsigned bytes.
 * RMCPConstants doesn't use {@link TypeConverter} because 
 * fields need to be runtime constants.
 * @see TypeConverter#byteToInt(byte)
 * @see TypeConverter#intToByte(int)
 */
public final class RmcpConstants {

    /**
     * RMCP version 1.0
     */
    public static final byte RMCP_V1_0 = 0x06;

    /**
     * IANA Enterprise Number = ASF IANA
     */
    public static final int ASFIANA = 4542;

    /**
     * ASF Message type = Presence Ping
     */
    public static final byte PRESENCE_PING = (byte) (0x80 - 256);

    private RmcpConstants() {
    }
}
