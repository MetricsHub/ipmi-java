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
 * Contains command codes for IPMI commands Byte constants are encoded as pseudo
 * unsigned bytes. IpmiLanConstants doesn't use {@link TypeConverter} because
 * fields need to be runtime constants.
 * 
 * @see TypeConverter#byteToInt(byte)
 * @see TypeConverter#intToByte(int)
 * 
 */
public final class CommandCodes {

    /**
     * An IPMI code for Get Chassis Status command
     */
    public static final byte GET_CHASSIS_STATUS = 0x01;

    /**
     * An IPMI code for Chassis Control command
     */
    public static final byte CHASSIS_CONTROL = 0x02;

    /**
     * An IPMI code for Get FRU Inventory Area Info command
     */
    public static final byte GET_FRU_INVENTORY_AREA_INFO = 0x10;

    /**
     * An IPMI code for Read FRU Data command
     */
    public static final byte READ_FRU_DATA = 0x11;

    /**
     * An IPMI code for Get Device SDR Info command
     */
    public static final byte GET_SDR_REPOSITORY_INFO = 0x20;

    /**
     * An IPMI code for Get Device SDR Info command
     */
    public static final byte GET_DEVICE_SDR_INFO = 0x21;

    /**
     * An IPMI code for Reserve SDR Repository command
     */
    public static final byte RESERVE_SDR_REPOSITORY = 0x22;

    /**
     * An IPMI code for Get SDR command
     */
    public static final byte GET_SDR = 0x23;

    /**
     * An IPMI code for Get Channel Authentication Capabilities command
     */
    public static final byte GET_CHANNEL_AUTHENTICATION_CAPABILITIES = 0x38;

    /**
     * An IPMI code for Set Session Privilege Level command
     */
    public static final byte SET_SESSION_PRIVILEGE_LEVEL = 0x3B;

    /**
     * An IPMI code for Get SEL Info command
     */
    public static final byte GET_SEL_INFO = 0x40;

    /**
     * An IPMI code for Reserve SEL command
     */
    public static final byte RESERVE_SEL = 0x42;

    /**
     * An IPMI code for Get SEL Entry command
     */
    public static final byte GET_SEL_ENTRY = 0x43;

    /**
     * An IPMI code for Get Channel Payload Support command
     */
    public static final byte GET_CHANNEL_PAYLOAD_SUPPORT = 0x4E;

    /**
     * An IPMI code for Activate Payload command
     */
    public static final byte ACTIVATE_PAYLOAD = 0x48;

    /**
     * An IPMI code for Deactivate Payload command
     */
    public static final byte DEACTIVATE_PAYLOAD = 0x49;

    /**
     * An IPMI code for Get Payload Activation Status command
     */
    public static final byte GET_PAYLOAD_ACTIVATION_STATUS = 0x4A;

    /**
     * An IPMI code for Get Channel Cipher Suites command
     */
    public static final byte GET_CHANNEL_CIPHER_SUITES = 0x54;

    private CommandCodes() {
    }
}
