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
 * OEM specific record.
 */
public class OemRecord extends SensorRecord {

    private int manufacturerId;

    private byte[] oemData;

    @Override
    protected void populateTypeSpecficValues(byte[] recordData,
            SensorRecord record) {

        // Vendor-specific record types (C1h-FFh) don't necessarily follow the
        // C0h layout (3-byte manufacturer ID + data), so guard every offset.
        byte[] buffer = new byte[4];

        if (recordData.length >= 8) {
            System.arraycopy(recordData, 5, buffer, 0, 3);
        }

        buffer[3] = 0;

        setManufacturerId(TypeConverter.littleEndianByteArrayToInt(buffer));

        byte[] data = new byte[Math.max(0, recordData.length - 8)];

        if (data.length > 0) {
            System.arraycopy(recordData, 8, data, 0, data.length);
        }

        setOemData(data);
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
