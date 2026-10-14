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

import java.nio.charset.StandardCharsets;

/**
 * Wrapper for SDR entry.
 */
public abstract class SensorRecord {

	private int id;
	private int sdrVersion;
	private byte recordType;
	private int recordLength;

	/**
	 * Parses sensor record raw data
	 *
	 * @param recordData
	 * @return {@link SensorRecord} populated with data
	 * @throws IllegalArgumentException
	 */
	public static SensorRecord populateSensorRecord(byte[] recordData) {

		if (recordData.length < 5) {
			throw new IllegalArgumentException("Record data is too short");
		}

		SensorRecord sensorRecord = null;

		byte recType = recordData[3];

		switch (recType) {
		case RecordTypes.FULL_SENSOR_RECORD://
			sensorRecord = new FullSensorRecord();
			break;
		case RecordTypes.BMC_MESSAGE_CHANNEL_INFO_RECORD:
			// Deprecated
			throw new IllegalArgumentException("This format is deprecated");
		case RecordTypes.COMPACT_SENSOR_RECORD://
			sensorRecord = new CompactSensorRecord();
			break;
		case RecordTypes.DEVICE_RELATIVE_ENTITY_ASSOCIATION_RECORD:
			// TODO: Test if server with such records will be available
			sensorRecord = new DeviceRelativeEntityAssiciationRecord();
			break;
		case RecordTypes.ENTITY_ASSOCIATION_RECORD://
			sensorRecord = new EntityAssociationRecord();
			break;
		case RecordTypes.EVENT_ONLY_RECORD:
			// TODO: Test if server with such records will be available
			sensorRecord = new EventOnlyRecord();
			break;
		case RecordTypes.FRU_DEVICE_LOCATOR_RECORD://
			sensorRecord = new FruDeviceLocatorRecord();
			break;
		case RecordTypes.GENERIC_DEVICE_LOCATOR_RECORD:
			// TODO: Test if server with such records will be available
			sensorRecord = new GenericDeviceLocatorRecord();
			break;
		case RecordTypes.MANAGEMENT_CONTROLLER_CONFIRMATION_RECORD:
			// TODO: Test if server with such records will be available
			sensorRecord = new ManagementControllerConfirmationRecord();
			break;
		case RecordTypes.MANAGEMENT_CONTROLLER_DEVICE_LOCATOR_RECORD://
			sensorRecord = new ManagementControllerDeviceLocatorRecord();
			break;
		case RecordTypes.OEM_RECORD://
			sensorRecord = new OemRecord();
			break;
		default:
			// IPMI 2.0 Table 43-1 reserves C0h-FFh for OEM records; vendors use
			// values above C0h (e.g. NVIDIA/GIGABYTE emit D0h). Never abort the
			// SDR repository walk on a record type we don't model.
			if (TypeConverter.byteToInt(recType) >= TypeConverter.byteToInt(RecordTypes.OEM_RECORD)) {
				sensorRecord = new OemRecord();
				break;
			}
			throw new IllegalArgumentException(
					"Invalid record type: "
							+ recType);
		}

		byte[] buffer = new byte[4];

		buffer[0] = recordData[0];
		buffer[1] = recordData[1];
		buffer[2] = 0;
		buffer[3] = 0;

		sensorRecord.setId(TypeConverter.littleEndianByteArrayToInt(buffer));

		sensorRecord
				.setSdrVersion(
						TypeConverter
								.littleEndianBcdByteToInt(recordData[2]));

		sensorRecord.setRecordType(recordData[3]);

		sensorRecord.setRecordLength(TypeConverter.byteToInt(recordData[4]));

		sensorRecord.populateTypeSpecficValues(recordData, sensorRecord);

		return sensorRecord;
	}

	/**
	 * Decodes record data which depends on record type
	 *
	 * @param recordData
	 *        - raw data containing whole record
	 * @param record
	 *        - {@link SensorRecord} being populated
	 */
	protected abstract void populateTypeSpecficValues(
			byte[] recordData,
			SensorRecord record);

	public void setId(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public void setSdrVersion(int sdrVersion) {
		this.sdrVersion = sdrVersion;
	}

	public int getSdrVersion() {
		return sdrVersion;
	}

	public void setRecordType(byte recordType) {
		this.recordType = recordType;
	}

	public byte getRecordType() {
		return recordType;
	}

	public void setRecordLength(int recordLength) {
		this.recordLength = recordLength;
	}

	public int getRecordLength() {
		return recordLength;
	}

	/**
	 * Decodes an ID string by its type/length byte (IPMI 2.0 section 43.15). Bits 7:6 give the encoding: Unicode (the
	 * specification names no encoding: it is decoded as UTF-8), BCD plus, 6-bit packed ASCII, or 8-bit ASCII + Latin
	 * 1; bits 4:0 give the length in characters, one per byte for 8-bit ASCII and Unicode. The bytes after the string
	 * are ignored, as BMCs with fixed-length records pad them ("Bytes beyond the ID string bytes are unspecified and
	 * should be ignored", table 43-1), a string longer than the record is cut at its end, and the string ends at its
	 * first NUL character, as ipmitool prints it.
	 *
	 * @param codingType
	 *        - the ID string type/length byte
	 * @param name
	 *        - the bytes after the type/length byte, to the end of the record
	 * @return the decoded name, empty when the type/length byte announces none
	 */
	protected String decodeName(byte codingType, byte[] name) {
		int typeLength = TypeConverter.byteToInt(codingType);
		int length = typeLength & 0x1f;
		String decoded;
		switch (typeLength >> 6) {
		case 0: // Unicode, of no specified encoding: UTF-8 also reads the ASCII that BMCs send with this type
			decoded = new String(name, 0, Math.min(length, name.length), StandardCharsets.UTF_8);
			break;
		case 1: // BCD plus, 2 characters per byte
			decoded = TypeConverter.decodeBcdPlus(name).substring(0, Math.min(length, name.length * 2));
			break;
		case 2: // 6-bit packed ASCII, 4 characters per 3 bytes: only the complete characters
			decoded = TypeConverter.decode6bitAscii(name).substring(0, Math.min(length, name.length * 8 / 6));
			break;
		default: // 8-bit ASCII + Latin 1
			decoded = new String(name, 0, Math.min(length, name.length), StandardCharsets.ISO_8859_1);
			break;
		}
		int nul = decoded.indexOf('\0');
		return nul < 0 ? decoded : decoded.substring(0, nul);
	}
}
