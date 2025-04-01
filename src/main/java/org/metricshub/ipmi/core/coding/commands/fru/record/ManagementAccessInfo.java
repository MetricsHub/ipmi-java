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
 * Management Access Information record from FRU Multi Record Area
 */
public class ManagementAccessInfo extends MultiRecordInfo {

    private ManagementAccessRecordType recordType;

    private String accessInfo;

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
    public ManagementAccessInfo(byte[] fruData, int offset, int length) {
        super();
        // TODO: Test when server containing such records will be available

        recordType = ManagementAccessRecordType.parseInt(TypeConverter
                .byteToInt(fruData[offset]));

        byte[] buffer = new byte[length - 1];

        System.arraycopy(fruData, offset + 1, buffer, 0, length - 1);

        accessInfo = new String(buffer);
    }

    public ManagementAccessRecordType getRecordType() {
        return recordType;
    }

    public void setRecordType(ManagementAccessRecordType recordType) {
        this.recordType = recordType;
    }

    public String getAccessInfo() {
        return accessInfo;
    }

    public void setAccessInfo(String accessInfo) {
        this.accessInfo = accessInfo;
    }

}
