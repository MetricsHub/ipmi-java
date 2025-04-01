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

import java.util.ArrayList;

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * FRU record containing Chassis info.<br>
 * This area is used to hold Serial Number, Part Number, and other information
 * about the system chassis.
 */
public class ChassisInfo extends FruRecord {

    private ChassisType chassisType;

    private String chassisPartNumber = "";

    private String chassisSerialNumber = "";

    private String[] customChassisInfo = new String[0];

    /**
     * Creates and populates record
     *
     * @param fruData
     *            - raw data containing record
     * @param offset
     *            - offset to the record in the data
     */
    public ChassisInfo(final byte[] fruData, final int offset) {
        super();

        if (fruData[offset] != 0x1) {
            throw new IllegalArgumentException("Invalid format version");
        }

        chassisType = ChassisType.parseInt(TypeConverter
                .byteToInt(fruData[offset + 2]));

        int partNumber = TypeConverter.byteToInt(fruData[offset + 3]);

        int currentOffset = offset + 4;

        int index = 0;

        ArrayList<String> customInfo = new ArrayList<String>();

        while (partNumber != 0xc1 && currentOffset < fruData.length) {

            int partType = (partNumber & 0xc0) >> 6;

            int partDataLength = (partNumber & 0x3f);

            if (partDataLength > 0 && partDataLength + currentOffset < fruData.length) {

                byte[] partNumberData = new byte[partDataLength];

                System.arraycopy(fruData, currentOffset, partNumberData, 0,
                        partDataLength);

                currentOffset += partDataLength;

                switch (index) {
                case 0:
                    setChassisPartNumber(FruRecord.decodeString(partType,
                            partNumberData, true));
                    break;
                case 1:
                    setChassisSerialNumber(FruRecord.decodeString(partType,
                            partNumberData, true));
                    break;
                default:
                    if (partDataLength == 0) {
                        continue;
                    }
                    customInfo.add(FruRecord.decodeString(partType,
                            partNumberData, true));
                    break;
                }
            }

            partNumber = TypeConverter.byteToInt(fruData[currentOffset]);

            ++currentOffset;

            ++index;
        }

        customChassisInfo = new String[customInfo.size()];
        customChassisInfo = customInfo.toArray(customChassisInfo);
    }

    public ChassisType getChassisType() {
        return chassisType;
    }

    public void setChassisType(ChassisType chassisType) {
        this.chassisType = chassisType;
    }

    public String getChassisPartNumber() {
        return chassisPartNumber;
    }

    public void setChassisPartNumber(String chassisPartNumber) {
        this.chassisPartNumber = chassisPartNumber;
    }

    public String getChassisSerialNumber() {
        return chassisSerialNumber;
    }

    public void setChassisSerialNumber(String chassisSerialNumber) {
        this.chassisSerialNumber = chassisSerialNumber;
    }

    public String[] getCustomChassisInfo() {
        return customChassisInfo;
    }

}
