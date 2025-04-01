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
 * Record from FRU Multi Record Area
 */
public abstract class MultiRecordInfo extends FruRecord {

    public MultiRecordInfo() {
        super();
    }

    /**
     * Creates and populates record
     *
     * @param fruData
     *            - raw data containing record
     * @param offset
     *            - offset to the record in the data
     */
    public static MultiRecordInfo populateMultiRecord(final byte[] fruData, final int offset) {
        MultiRecordInfo recordInfo = null;

        // TODO: Test when server containing such records will be available

        if ((TypeConverter.byteToInt(fruData[offset + 1]) & 0xf) != 0x2) {
            throw new IllegalArgumentException("Invalid FRU record version");
        }

        FruMultiRecordType recordType = FruMultiRecordType
                .parseInt(TypeConverter.byteToInt(fruData[offset]));

        int length = TypeConverter.byteToInt(fruData[offset + 2]);

        int currentOffset = offset + 5;

        switch (recordType) {
        case PowerSupplyInformation:
            recordInfo = new PowerSupplyInfo(fruData, currentOffset);
            break;
        case DcOutput:
            recordInfo = new DcOutputInfo(fruData, currentOffset);
            break;
        case DcLoad:
            recordInfo = new DcLoadInfo(fruData, currentOffset);
            break;
        case ManagementAccessRecord:
            recordInfo = new ManagementAccessInfo(fruData, currentOffset, length);
            break;
        case BaseCompatibilityRecord:
            recordInfo = new BaseCompatibilityInfo(fruData, currentOffset, length);
            break;
        case ExtendedCompatibilityRecord:
            recordInfo = new ExtendedCompatibilityInfo(fruData, currentOffset, length);
            break;
        case OemRecord:
            recordInfo = new OemInfo(fruData, currentOffset, length);
            break;
        default:
            throw new IllegalArgumentException("Unsupported record type");
        }

        return recordInfo;
    }
}
