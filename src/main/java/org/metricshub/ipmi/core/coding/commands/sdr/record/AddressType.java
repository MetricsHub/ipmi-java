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
 * Type of address behind Sensor Record's 'Sensor Owner ID' field.
 */
public enum AddressType {
	/**
	 * IPMB Slave Address
	 */
    IpmbSlaveAddress(AddressType.IPMBSLAVEADDRESS),
    /**
     * System Software ID
     */
    SystemSoftwareId(AddressType.SYSTEMSOFTWAREID),
    ;
    private static final int IPMBSLAVEADDRESS = 0;
    private static final int SYSTEMSOFTWAREID = 1;

    private int code;

    /**
     * Creates a new {@link AddressType}
     * @param code address type code
     */
    AddressType(int code) {
        this.code = code;
    }

    /**
     * Get address code
     * @return int value
     */
    public int getCode() {
        return code;
    }

    /**
     * Parse the given int value and return the corresponding {@link AddressType}
     * 
     * @param value address type value as int
     * @return {@link AddressType} instance
     */
    public static AddressType parseInt(int value) {
        switch(value) {
        case IPMBSLAVEADDRESS:
            return IpmbSlaveAddress;
        case SYSTEMSOFTWAREID:
            return SystemSoftwareId;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
