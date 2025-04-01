package org.metricshub.ipmi.core.coding.commands.fru.record;

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
 * OEM record from FRU Multi Record Area
 */
public class OemInfo extends MultiRecordInfo {

    private int manufacturerId;

    private byte[] oemData;

    /**
     * Creates and populates record
     *
     * @param fruData
     *            - raw data containing record
     * @param offset
     *            - offset to the record in the data
     * @param length
     *            - length of the record
     */
    public OemInfo(byte[] fruData, int offset, int length) {
        super();
        // TODO: Test when server containing such records will be available

        byte[] buffer = new byte[4];

        System.arraycopy(fruData, offset, buffer, 0, 3);
        buffer[3] = 0;

        manufacturerId = TypeConverter.littleEndianByteArrayToInt(buffer);

        oemData = new byte[length - 3];

        System.arraycopy(fruData, offset+3, oemData, 0, length-3);

    }

    public int getManufacturerId() {
        return manufacturerId;
    }

    public void setManufacturerId(int manufacturerId) {
        this.manufacturerId = manufacturerId;
    }

    public byte[] getOemData() {
        return oemData;
    }

    public void setOemData(byte[] oemData) {
        this.oemData = oemData;
    }

}
