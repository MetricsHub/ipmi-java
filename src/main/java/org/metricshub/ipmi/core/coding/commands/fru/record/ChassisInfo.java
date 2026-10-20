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
 * <p>
 * Each string field is decoded according to its type/length byte: text without its leading and trailing spaces, BCD
 * plus, 6-bit ASCII, or binary data as lowercase hexadecimal digits. A field the area does not give is empty, never
 * null.
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
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 */
	public ChassisInfo(final byte[] fruData, final int offset) {
		super();

		if (fruData[offset] != 0x1) {
			throw new IllegalArgumentException("Invalid format version");
		}

		chassisType = ChassisType
				.parseInt(
						TypeConverter
								.byteToInt(fruData[offset + 2]));

		int partNumber = TypeConverter.byteToInt(fruData[offset + 3]);

		int currentOffset = offset + 4;

		int index = 0;

		ArrayList<String> customInfo = new ArrayList<String>();

		while (partNumber != 0xc1 && currentOffset < fruData.length) {

			int partType = (partNumber & 0xc0) >> 6;

			int partDataLength = partNumber & 0x3f;

			if (partDataLength > 0 && partDataLength + currentOffset < fruData.length) {

				byte[] partNumberData = new byte[partDataLength];

				System
						.arraycopy(
								fruData,
								currentOffset,
								partNumberData,
								0,
								partDataLength);

				currentOffset += partDataLength;

				switch (index) {
				case 0:
					setChassisPartNumber(
							decodeString(
									partType,
									partNumberData,
									true));
					break;
				case 1:
					setChassisSerialNumber(
							decodeString(
									partType,
									partNumberData,
									true));
					break;
				default:
					customInfo
							.add(
									decodeString(
											partType,
											partNumberData,
											true));
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

	/**
	 * Returns the type of the chassis, coded as the SMBIOS chassis types.
	 *
	 * @return the chassis type, {@link ChassisType#Other} for a code that {@link ChassisType} does not list
	 */
	public ChassisType getChassisType() {
		return chassisType;
	}

	/**
	 * Sets the type of the chassis.
	 *
	 * @param chassisType the chassis type
	 */
	public void setChassisType(ChassisType chassisType) {
		this.chassisType = chassisType;
	}

	/**
	 * Returns the chassis part number.
	 *
	 * @return the chassis part number, empty when the area does not give it
	 */
	public String getChassisPartNumber() {
		return chassisPartNumber;
	}

	/**
	 * Sets the chassis part number.
	 *
	 * @param chassisPartNumber the chassis part number
	 */
	public void setChassisPartNumber(String chassisPartNumber) {
		this.chassisPartNumber = chassisPartNumber;
	}

	/**
	 * Returns the chassis serial number.
	 *
	 * @return the chassis serial number, empty when the area does not give it
	 */
	public String getChassisSerialNumber() {
		return chassisSerialNumber;
	}

	/**
	 * Sets the chassis serial number.
	 *
	 * @param chassisSerialNumber the chassis serial number
	 */
	public void setChassisSerialNumber(String chassisSerialNumber) {
		this.chassisSerialNumber = chassisSerialNumber;
	}

	/**
	 * Returns the custom fields of the chassis area, those that follow its predefined fields.
	 *
	 * @return the custom fields in order, empty when there are none
	 */
	public String[] getCustomChassisInfo() {
		return customChassisInfo;
	}

}
