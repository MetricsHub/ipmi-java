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

	private static final int THRESHOLD_EVENT_READING_TYPE = 1;

	/**
	 * Event offsets (Table 42-2, event/reading type 01h) of the threshold comparison bits of Table 35-15: LNC going
	 * low, LC going low, LNR going low, UNC going high, UC going high, UNR going high.
	 */
	private static final int[] THRESHOLD_EVENT_OFFSETS = { 0, 2, 4, 7, 9, 11 };
	private byte sensorReading;

	/**
	 * This bit is set to indicate that a 're-arm' or 'Set Event Receiver'
	 * command has been used to request an update of the sensor status, and that
	 * update has not occurred yet. Software should use this bit to avoid
	 * getting an incorrect status while the first sensor update is in progress.
	 */
	private boolean sensorStateValid;

	private boolean scanningEnabled;

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
	 * @return false when the BMC reports sensor scanning as disabled (byte 2 bit 6): the reading and the states
	 *         are then not to be used
	 */
	public boolean isScanningEnabled() {
		return scanningEnabled;
	}

	/**
	 * @param scanningEnabled whether the BMC reports sensor scanning as enabled (byte 2 bit 6)
	 */
	public void setScanningEnabled(boolean scanningEnabled) {
		this.scanningEnabled = scanningEnabled;
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
	 *        - value received via
	 *        {@link FullSensorRecord#getEventReadingType()},
	 *        {@link CompactSensorRecord#getEventReadingType()} or
	 *        {@link EventOnlyRecord#getEventReadingType()}
	 */
	public List<ReadingType> getStatesAsserted(
			SensorType sensorType,
			int sensorEventReadingType) {
		ArrayList<ReadingType> list = new ArrayList<ReadingType>();
		if (statesAsserted == null) {
			return list;
		}
		if (sensorEventReadingType == THRESHOLD_EVENT_READING_TYPE) {
			// Byte 3 of a threshold sensor holds comparison bits (Table 35-15), not event offsets: bit n means
			// "at or beyond" the threshold whose going-low (lower) or going-high (upper) event offset is below
			for (int i = 0; i < THRESHOLD_EVENT_OFFSETS.length && i < statesAsserted.length; ++i) {
				if (statesAsserted[i]) {
					list.add(ReadingType.parseInt(sensorType, sensorEventReadingType, THRESHOLD_EVENT_OFFSETS[i]));
				}
			}
			return list;
		}
		for (int i = 0; i < statesAsserted.length; ++i) {
			if (statesAsserted[i]) {
				list
						.add(
								ReadingType
										.parseInt(
												sensorType,
												sensorEventReadingType,
												i));
			}
		}
		return list;
	}

	public void setStatesAsserted(boolean[] statesAsserted) {
		this.statesAsserted = statesAsserted;
	}
}
