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

	/**
	 * Size of the common SDR record header (record ID, SDR version, record type, record length).
	 */
	private static final int HEADER_LENGTH = 5;

	/**
	 * Size of the manufacturer ID field in a C0h OEM record.
	 */
	private static final int MANUFACTURER_ID_LENGTH = 3;

	private int manufacturerId;

	private byte[] oemData;

	@Override
	protected void populateTypeSpecficValues(
			byte[] recordData,
			SensorRecord record) {

		// Only the C0h OEM record has a defined layout (3-byte manufacturer ID
		// followed by OEM data). Vendor-defined record types C1h-FFh carry an
		// unknown layout, so keep their whole type-specific payload and leave
		// the manufacturer ID at 0 (unknown).
		int dataOffset = HEADER_LENGTH;

		if (recordData[3] == RecordTypes.OEM_RECORD && recordData.length >= HEADER_LENGTH + MANUFACTURER_ID_LENGTH) {
			byte[] buffer = new byte[4];
			System.arraycopy(recordData, HEADER_LENGTH, buffer, 0, MANUFACTURER_ID_LENGTH);
			setManufacturerId(TypeConverter.littleEndianByteArrayToInt(buffer));
			dataOffset += MANUFACTURER_ID_LENGTH;
		}

		byte[] data = new byte[Math.max(0, recordData.length - dataOffset)];

		if (data.length > 0) {
			System.arraycopy(recordData, dataOffset, data, 0, data.length);
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
