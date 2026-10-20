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

import java.nio.charset.StandardCharsets;

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
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 * @param length
	 *        - length of the record
	 */
	public ManagementAccessInfo(byte[] fruData, int offset, int length) {
		super();

		recordType = ManagementAccessRecordType
				.parseInt(
						TypeConverter
								.byteToInt(fruData[offset]));

		byte[] buffer = new byte[length - 1];

		System.arraycopy(fruData, offset + 1, buffer, 0, length - 1);

		accessInfo = new String(buffer, StandardCharsets.ISO_8859_1);
	}

	/**
	 * Returns the kind of management access information the record holds.
	 *
	 * @return the sub-record type, {@link ManagementAccessRecordType#Unspecified} for a code the enumeration does not
	 *         list
	 */
	public ManagementAccessRecordType getRecordType() {
		return recordType;
	}

	/**
	 * Sets the kind of management access information the record holds.
	 *
	 * @param recordType the sub-record type
	 */
	public void setRecordType(ManagementAccessRecordType recordType) {
		this.recordType = recordType;
	}

	/**
	 * Returns the management access information: a URL, a name or a ping address of the system or of a component,
	 * or the 16-byte unique ID of the system.
	 *
	 * @return the bytes that follow the sub-record type, decoded as Latin 1
	 */
	public String getAccessInfo() {
		return accessInfo;
	}

	/**
	 * Sets the management access information.
	 *
	 * @param accessInfo the management access information
	 */
	public void setAccessInfo(String accessInfo) {
		this.accessInfo = accessInfo;
	}

}
