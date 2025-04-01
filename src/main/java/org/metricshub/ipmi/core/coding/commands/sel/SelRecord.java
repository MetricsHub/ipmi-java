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

public class SelRecord {

    private int recordId;

    private SelRecordType recordType;

    private Date timestamp;

    /**
     * {@link SensorType} code for sensor that generated the event
     */
    private SensorType sensorType;

    /**
     * Number of sensor that generated the event
     */
    private int sensorNumber;

    private EventDirection eventDirection;

    private ReadingType event;

    /**
     * Reading that triggered event. Provided in raw value (need to do
     * {@link FullSensorRecord#calcFormula(int)}). Only for threshold sensors.
     */
    private byte reading;

    public static SelRecord populateSelRecord(byte[] data) {
        SelRecord record = new SelRecord();

        byte[] buffer = new byte[4];

        buffer[0] = data[0];
        buffer[1] = data[1];
        buffer[2] = 0;
        buffer[3] = 0;

        record.setRecordId(TypeConverter.littleEndianByteArrayToInt(buffer));

        record.setRecordType(SelRecordType.parseInt(TypeConverter.byteToInt(data[2])));

        System.arraycopy(data, 3, buffer, 0, 4);

        record.setTimestamp(TypeConverter.decodeDate(TypeConverter.littleEndianByteArrayToInt(buffer)));

        record.setSensorType(SensorType.parseInt(TypeConverter.byteToInt(data[10])));

        record.setSensorNumber(TypeConverter.byteToInt(data[11]));

        record.setEventDirection(EventDirection.parseInt((TypeConverter.byteToInt(data[12]) & 0x80) >> 7));

        int eventType = TypeConverter.byteToInt(data[12]) & 0x7f;

        int eventOffset = TypeConverter.byteToInt(data[13]) & 0xf;

        record.setEvent(ReadingType.parseInt(record.getSensorType(), eventType, eventOffset));

        record.setReading(data[14]);

        return record;
    }

    public void setRecordId(int recordId) {
        this.recordId = recordId;
    }

    public int getRecordId() {
        return recordId;
    }

    public SelRecordType getRecordType() {
        return recordType;
    }

    public void setRecordType(SelRecordType recordType) {
        this.recordType = recordType;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public void setSensorType(SensorType sensorType) {
        this.sensorType = sensorType;
    }

    public int getSensorNumber() {
        return sensorNumber;
    }

    public void setSensorNumber(int sensorNumber) {
        this.sensorNumber = sensorNumber;
    }

    public EventDirection getEventDirection() {
        return eventDirection;
    }

    public void setEventDirection(EventDirection eventDirection) {
        this.eventDirection = eventDirection;
    }

    public ReadingType getEvent() {
        return event;
    }

    public void setEvent(ReadingType event) {
        this.event = event;
    }

    /**
     * Reading that triggered event. Provided in raw value (need to do
     * {@link FullSensorRecord#calcFormula(int)}). Only for threshold sensors.
     */
    public byte getReading() {
        return reading;
    }

    public void setReading(byte reading) {
        this.reading = reading;
    }
}
