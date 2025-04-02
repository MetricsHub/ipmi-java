package org.metricshub.ipmi.core.coding.commands.sdr;

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
import java.util.List;

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EventOnlyRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Wrapper for Get Sensor Reading response.
 */
public class GetSensorReadingResponseData implements ResponseData {
    private byte sensorReading;

    /**
     * This bit is set to indicate that a 're-arm' or 'Set Event Receiver'
     * command has been used to request an update of the sensor status, and that
     * update has not occurred yet. Software should use this bit to avoid
     * getting an incorrect status while the first sensor update is in progress.
     */
    private boolean sensorStateValid;

    /**
     * Contains state of the sensor if it is threshold-based.
     */
    private SensorState sensorState;

    /**
     * Contains state of the sensor if it is discrete.
     */
    private boolean[] statesAsserted;

    /**
     * Contains raw IPMI command data.
     */
    private byte[] raw;
 
    public double getSensorReading(FullSensorRecord sensorRecord) {
        return sensorRecord.calcFormula(TypeConverter.byteToInt(sensorReading));
    }

    public double getPlainSensorReading() {
        return TypeConverter.byteToInt(sensorReading);
    }

    public void setSensorReading(byte sensorReading) {
        this.sensorReading = sensorReading;
    }

    public boolean isSensorStateValid() {
        return sensorStateValid;
    }

    public void setSensorStateValid(boolean sensorStateValid) {
        this.sensorStateValid = sensorStateValid;
    }

    /**
     * Contains state of the sensor if it is threshold-based.
     */
    public SensorState getSensorState() {
        return sensorState;
    }

    public void setSensorState(SensorState sensorState) {
        this.sensorState = sensorState;
    }

    /**
     * Contains raw IPMI command data.
     */
   public byte[] getRaw() {
		return raw;
	}

	public void setRaw(byte[] raw) {
		this.raw = raw;
	}

    /**
     * Contains state of the sensor if it is discrete.
     *
     * @param sensorEventReadingType
     *            - value received via
     *            {@link FullSensorRecord#getEventReadingType()},
     *            {@link CompactSensorRecord#getEventReadingType()} or
     *            {@link EventOnlyRecord#getEventReadingType()}
     */
    public List<ReadingType> getStatesAsserted(SensorType sensorType,
            int sensorEventReadingType) {
        ArrayList<ReadingType> list = new ArrayList<ReadingType>();
        for (int i = 0; i < statesAsserted.length; ++i) {
            if (statesAsserted[i]) {
                list.add(ReadingType.parseInt(sensorType,
                        sensorEventReadingType, i));
            }
        }
        return list;
    }

    public void setStatesAsserted(boolean[] statesAsserted) {
        this.statesAsserted = statesAsserted;
    }
}
