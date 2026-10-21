package org.metricshub.ipmi.core.coding.commands.sel;

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

import java.util.Date;

import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.util.Arrays;

/**
 * A System Event Log record as the BMC sent it (IPMI 2.0 section 32), decoded: its type, its timestamp and, for a
 * system event, the sensor and the event. {@link org.metricshub.ipmi.client.SelEntry} wraps it with the timestamps
 * interpreted.
 */
public class SelRecord {

	/** The size of every SEL record, whatever its type (IPMI 2.0 section 32): 16 bytes. */
	public static final int RECORD_SIZE = 16;

	private int recordId;

	private SelRecordType recordType;

	private Date timestamp;

	/**
	 * {@link SensorType} code for sensor that generated the event
	 */
	private SensorType sensorType;

	/**
	 * Sensor type code of the sensor that generated the event, which {@link #sensorType} does not keep for an OEM or
	 * reserved code
	 */
	private int sensorTypeCode;

	/**
	 * Number of sensor that generated the event
	 */
	private int sensorNumber;

	/**
	 * Generator ID (IPMI 2.0 section 32.1): the slave address or system software ID of the controller or software that
	 * logged the event in the LS byte, its channel number and LUN in the MS byte
	 */
	private int generatorId;

	private EventDirection eventDirection;

	/**
	 * Event/reading type code (IPMI 2.0 table 42-1): 01h threshold, 02h-0Ch generic discrete, 6Fh sensor-specific,
	 * 70h-7Fh OEM
	 */
	private int eventType;

	private ReadingType event;

	/**
	 * The three event data bytes as the BMC sent them (IPMI 2.0 table 32-1): the event offset and the usage of the
	 * other two bytes in the first one, then the reading and the threshold of a threshold event or what the sensor
	 * type defines
	 */
	private byte[] eventData;

	/**
	 * Reading that triggered event. Provided in raw value (need to do
	 * {@link FullSensorRecord#calcFormula(int)}). Only for threshold sensors.
	 */
	private byte reading;

	private Integer manufacturerId;

	private byte[] oemData;

	/** The 16 bytes of the record as the BMC sent them */
	private byte[] rawData;

	/**
	 * Returns the 16 bytes of the record as the BMC sent them.
	 *
	 * @return the 16 bytes of the record as the BMC sent them, null for a record built by hand
	 */
	public byte[] getRawData() {
		return rawData;
	}

	/**
	 * Sets the bytes of the record as the BMC sent them.
	 *
	 * @param rawData the 16 bytes of the record as the BMC sent them
	 */
	public void setRawData(byte[] rawData) {
		this.rawData = rawData;
	}

	/**
	 * Decodes a SEL record (IPMI 2.0 section 32).
	 *
	 * @param data the 16 bytes of the record
	 * @return the record: a system event, an OEM record with its OEM bytes, or a record of a reserved type with its ID
	 *         only
	 * @throws IllegalArgumentException when the data is shorter than a SEL record
	 */
	public static SelRecord populateSelRecord(byte[] data) {
		if (data == null || data.length < RECORD_SIZE) {
			throw new IllegalArgumentException(
					"A SEL record is " + RECORD_SIZE + " bytes, not " + (data == null ? 0 : data.length));
		}

		SelRecord record = new SelRecord();

		record.setRawData(Arrays.copyOf(data, RECORD_SIZE));

		byte[] buffer = new byte[4];

		buffer[0] = data[0];
		buffer[1] = data[1];
		buffer[2] = 0;
		buffer[3] = 0;

		record.setRecordId(TypeConverter.littleEndianByteArrayToInt(buffer));

		record.setRecordType(SelRecordType.parseInt(TypeConverter.byteToInt(data[2])));

		switch (record.getRecordType()) {
		case System:
			populateSystemEvent(record, data);
			break;
		case OemTimestamped:
			// IPMI 2.0 section 32.2: timestamp, 3-byte manufacturer ID, 6 OEM-defined bytes
			System.arraycopy(data, 3, buffer, 0, 4);
			record.setTimestamp(TypeConverter.decodeDate(TypeConverter.littleEndianByteArrayToInt(buffer)));
			buffer[0] = data[7];
			buffer[1] = data[8];
			buffer[2] = data[9];
			buffer[3] = 0;
			record.setManufacturerId(TypeConverter.littleEndianByteArrayToInt(buffer));
			record.setOemData(Arrays.copyOfRange(data, 10, 16));
			break;
		case OemNonTimestamped:
			// IPMI 2.0 section 32.3: 13 OEM-defined bytes
			record.setOemData(Arrays.copyOfRange(data, 3, 16));
			break;
		default:
			// Reserved: nothing but the record ID is defined
			break;
		}

		return record;
	}

	/**
	 * Decodes the body of a system event record (IPMI 2.0 section 32.1).
	 */
	private static void populateSystemEvent(SelRecord record, byte[] data) {
		byte[] buffer = new byte[4];

		System.arraycopy(data, 3, buffer, 0, 4);

		record.setTimestamp(TypeConverter.decodeDate(TypeConverter.littleEndianByteArrayToInt(buffer)));

		buffer[0] = data[7];
		buffer[1] = data[8];
		buffer[2] = 0;
		buffer[3] = 0;

		record.setGeneratorId(TypeConverter.littleEndianByteArrayToInt(buffer));

		record.setSensorTypeCode(TypeConverter.byteToInt(data[10]));
		record.setSensorType(SensorType.parseInt(record.getSensorTypeCode()));

		record.setSensorNumber(TypeConverter.byteToInt(data[11]));

		record.setEventDirection(EventDirection.parseInt((TypeConverter.byteToInt(data[12]) & 0x80) >> 7));

		int eventType = TypeConverter.byteToInt(data[12]) & 0x7f;

		record.setEventType(eventType);

		int eventOffset = TypeConverter.byteToInt(data[13]) & 0xf;

		record.setEvent(ReadingType.parseInt(record.getSensorType(), eventType, eventOffset));

		record.setEventData(Arrays.copyOfRange(data, 13, RECORD_SIZE));

		record.setReading(data[14]);
	}

	/**
	 * Returns the generator ID of a system event (bytes 8 and 9 of the record).
	 *
	 * @return the generator ID of a system event (IPMI 2.0 section 32.1): the slave address or system software ID
	 *         of the controller or software that logged the event in the low byte, its channel number and LUN in the
	 *         high byte; 0 for the other record types
	 */
	public int getGeneratorId() {
		return generatorId;
	}

	/**
	 * Sets the generator ID of a system event.
	 *
	 * @param generatorId the generator ID of a system event
	 */
	public void setGeneratorId(int generatorId) {
		this.generatorId = generatorId;
	}

	/**
	 * Returns the event/reading type code of a system event (byte 13 bits 6:0 of the record).
	 *
	 * @return the event/reading type code of a system event (IPMI 2.0 table 42-1: 01h threshold, 02h to 0Ch generic
	 *         discrete, 6Fh sensor-specific, 70h to 7Fh OEM); 0 for the other record types
	 */
	public int getEventType() {
		return eventType;
	}

	/**
	 * Sets the event/reading type code of a system event.
	 *
	 * @param eventType the event/reading type code of a system event
	 */
	public void setEventType(int eventType) {
		this.eventType = eventType;
	}

	/**
	 * Returns the three event data bytes of a system event (bytes 14 to 16 of the record).
	 *
	 * @return the three event data bytes of a system event as the BMC sent them (IPMI 2.0 table 32-1): the event
	 *         offset and the usage of the next two bytes in the first one, then the reading and the threshold of a
	 *         threshold event, or what the sensor type defines; null for the other record types
	 */
	public byte[] getEventData() {
		return eventData;
	}

	/**
	 * Sets the three event data bytes of a system event.
	 *
	 * @param eventData the three event data bytes of a system event, null for the other record types
	 */
	public void setEventData(byte[] eventData) {
		this.eventData = eventData;
	}

	/**
	 * Returns the manufacturer ID of a timestamped OEM record (bytes 8 to 10 of the record).
	 *
	 * @return the manufacturer ID (IANA enterprise number) of an OEM timestamped record, null for the other record
	 *         types
	 */
	public Integer getManufacturerId() {
		return manufacturerId;
	}

	/**
	 * Sets the manufacturer ID of a timestamped OEM record.
	 *
	 * @param manufacturerId the manufacturer ID of an OEM timestamped record, null for the other record types
	 */
	public void setManufacturerId(Integer manufacturerId) {
		this.manufacturerId = manufacturerId;
	}

	/**
	 * Returns the OEM-defined bytes of an OEM record.
	 *
	 * @return the OEM-defined bytes of an OEM record (6 for a timestamped one, 13 otherwise), null for the other
	 *         record types
	 */
	public byte[] getOemData() {
		return oemData;
	}

	/**
	 * Sets the OEM-defined bytes of an OEM record.
	 *
	 * @param oemData the OEM-defined bytes of an OEM record, null for the other record types
	 */
	public void setOemData(byte[] oemData) {
		this.oemData = oemData;
	}

	/**
	 * Sets the record ID.
	 *
	 * @param recordId the record ID
	 */
	public void setRecordId(int recordId) {
		this.recordId = recordId;
	}

	/**
	 * Returns the record ID of the entry (bytes 1 and 2 of the record).
	 *
	 * @return the record ID
	 */
	public int getRecordId() {
		return recordId;
	}

	/**
	 * Returns the type of the record (byte 3 of the record, IPMI 2.0 section 31.6.1): a system event, an OEM record
	 * with or without a timestamp, or a reserved type. The record type byte itself is {@code getRawData()[2]}.
	 *
	 * @return the record type
	 */
	public SelRecordType getRecordType() {
		return recordType;
	}

	/**
	 * Sets the type of the record.
	 *
	 * @param recordType the record type
	 */
	public void setRecordType(SelRecordType recordType) {
		this.recordType = recordType;
	}

	/**
	 * Returns the timestamp of the record as the BMC sent it (bytes 4 to 7 of the record): a {@link Date} of the
	 * unsigned 32-bit number of seconds, with no UTC offset applied. FFFFFFFFh (unspecified) and the values up to
	 * 20000000h (seconds since the initialization of the SEL device, IPMI 2.0 section 37.1) are not dates:
	 * {@link org.metricshub.ipmi.client.SelEntry#timestamp()} tells them apart.
	 *
	 * @return the timestamp, null for a non-timestamped OEM record or a record of a reserved type
	 */
	public Date getTimestamp() {
		return timestamp;
	}

	/**
	 * Sets the timestamp of the record.
	 *
	 * @param timestamp the timestamp as the BMC sent it, null for a record without one
	 */
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Returns the type of the sensor that generated a system event (byte 11 of the record).
	 *
	 * @return the sensor type, null for the other record types
	 */
	public SensorType getSensorType() {
		return sensorType;
	}

	/**
	 * Sets the type of the sensor that generated a system event.
	 *
	 * @param sensorType the sensor type
	 */
	public void setSensorType(SensorType sensorType) {
		this.sensorType = sensorType;
	}

	/**
	 * Returns the sensor type code of a system event (byte 11 of the record, IPMI 2.0 table 42-3), which
	 * {@link #getSensorType()} does not keep for an OEM (C0h to FFh) or reserved code.
	 *
	 * @return the sensor type code, 0 to 255, 0 for the other record types
	 */
	public int getSensorTypeCode() {
		return sensorTypeCode;
	}

	/**
	 * Sets the sensor type code of a system event.
	 *
	 * @param sensorTypeCode the sensor type code, 0 to 255
	 */
	public void setSensorTypeCode(int sensorTypeCode) {
		this.sensorTypeCode = sensorTypeCode;
	}

	/**
	 * Returns the number of the sensor that generated a system event (byte 12 of the record): with the generator ID
	 * ({@link #getGeneratorId()}), it identifies the sensor.
	 *
	 * @return the sensor number, 0 for the other record types
	 */
	public int getSensorNumber() {
		return sensorNumber;
	}

	/**
	 * Sets the number of the sensor that generated a system event.
	 *
	 * @param sensorNumber the sensor number, 0 to 255
	 */
	public void setSensorNumber(int sensorNumber) {
		this.sensorNumber = sensorNumber;
	}

	/**
	 * Tells whether a system event is an assertion or a deassertion (byte 13 bit 7 of the record).
	 *
	 * @return the event direction, null for the other record types
	 */
	public EventDirection getEventDirection() {
		return eventDirection;
	}

	/**
	 * Sets whether a system event is an assertion or a deassertion.
	 *
	 * @param eventDirection the event direction
	 */
	public void setEventDirection(EventDirection eventDirection) {
		this.eventDirection = eventDirection;
	}

	/**
	 * Returns the event of a system event: the state that the event offset (the low 4 bits of byte 14 of the record)
	 * designates for the event/reading type and the sensor type.
	 *
	 * @return the event, null for the other record types
	 */
	public ReadingType getEvent() {
		return event;
	}

	/**
	 * Sets the event of a system event.
	 *
	 * @param event the event
	 */
	public void setEvent(ReadingType event) {
		this.event = event;
	}

	/**
	 * Returns the reading that triggered a threshold event (event data 2, byte 15 of the record), as the raw byte that
	 * {@link FullSensorRecord#calcFormula(int)} converts. Only meaningful for a threshold sensor whose event data 1
	 * says that event data 2 holds the trigger reading.
	 *
	 * @return the raw reading byte, 0 for the other record types
	 */
	public byte getReading() {
		return reading;
	}

	/**
	 * Sets the raw reading that triggered a threshold event.
	 *
	 * @param reading the raw reading byte
	 */
	public void setReading(byte reading) {
		this.reading = reading;
	}
}
