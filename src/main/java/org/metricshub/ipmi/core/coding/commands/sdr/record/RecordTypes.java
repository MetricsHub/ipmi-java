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

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Class holding codes for SDR types. Byte constants are encoded as pseudo
 * unsigned bytes. IpmiLanConstants doesn't use {@link TypeConverter} because
 * fields need to be runtime constants.
 * 
 * @see TypeConverter#byteToInt(byte)
 * @see TypeConverter#intToByte(int)
 * 
 */
public final class RecordTypes {

    public static final byte FULL_SENSOR_RECORD = 0x01;

    public static final byte COMPACT_SENSOR_RECORD = 0x02;

    public static final byte EVENT_ONLY_RECORD = 0x03;

    public static final byte ENTITY_ASSOCIATION_RECORD = 0x08;

    public static final byte DEVICE_RELATIVE_ENTITY_ASSOCIATION_RECORD = 0x09;

    public static final byte GENERIC_DEVICE_LOCATOR_RECORD = 0x10;

    public static final byte FRU_DEVICE_LOCATOR_RECORD = 0x11;

    public static final byte MANAGEMENT_CONTROLLER_DEVICE_LOCATOR_RECORD = 0x12;

    public static final byte MANAGEMENT_CONTROLLER_CONFIRMATION_RECORD = 0x13;

    public static final byte BMC_MESSAGE_CHANNEL_INFO_RECORD = 0x14;

    public static final byte OEM_RECORD = (byte) (0xc0 - 256);

    private RecordTypes() {
    }
}
