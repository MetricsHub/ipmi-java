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
 * The reply to Get Sensor Reading (IPMI 2.0 table 35-15): the reading byte, the status bits and the threshold or
 * discrete states of the sensor. The byte numbers below are those of the table, where byte 1 is the completion code.
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

	/**
	 * Returns the reading converted to the unit of the sensor with the factors of its Full Sensor Record
	 * ({@link FullSensorRecord#calcFormula(int)}).
	 *
	 * @param sensorRecord the Full Sensor Record of the sensor
	 * @return the reading, {@link Double#NaN} when the sensor has no analog reading or is non-linear
	 *         ({@link FullSensorRecord#isNonLinear()}): the readings of a non-linear sensor convert with the factors
	 *         of Get Sensor Reading Factors
	 */
	public double getSensorReading(FullSensorRecord sensorRecord) {
		return sensorRecord.calcFormula(TypeConverter.byteToInt(sensorReading));
	}

	/**
	 * Returns the reading byte as the BMC sent it (byte 2), unsigned and not converted.
	 *
	 * @return the reading byte, 0 to 255
	 */
	public double getPlainSensorReading() {
		return TypeConverter.byteToInt(sensorReading);
	}

	/**
	 * Sets the reading byte.
	 *
	 * @param sensorReading the reading byte (byte 2)
	 */
	public void setSensorReading(byte sensorReading) {
		this.sensorReading = sensorReading;
	}

	/**
	 * Tells whether the reading and the states are available: false when the BMC sets the reading/state unavailable bit
	 * (byte 3 bit 5), for instance while the first update of the sensor after a re-arm is in progress, or when the
	 * entity is not present.
	 *
	 * @return whether the reading and the states are available
	 */
	public boolean isSensorStateValid() {
		return sensorStateValid;
	}

	/**
	 * Sets whether the reading and the states are available.
	 *
	 * @param sensorStateValid whether the reading and the states are available (byte 3 bit 5 clear)
	 */
	public void setSensorStateValid(boolean sensorStateValid) {
		this.sensorStateValid = sensorStateValid;
	}

	/**
	 * Tells whether the BMC reports sensor scanning as enabled (byte 3 bit 6).
	 *
	 * @return false when the BMC reports sensor scanning as disabled: the reading and the states are then not to be
	 *         used
	 */
	public boolean isScanningEnabled() {
		return scanningEnabled;
	}

	/**
	 * Sets whether the BMC reports sensor scanning as enabled.
	 *
	 * @param scanningEnabled whether the BMC reports sensor scanning as enabled (byte 3 bit 6)
	 */
	public void setScanningEnabled(boolean scanningEnabled) {
		this.scanningEnabled = scanningEnabled;
	}

	/**
	 * Returns the most severe threshold the reading is at or beyond (byte 4 bits 5:0), for a threshold sensor: the
	 * value means nothing for a discrete sensor.
	 *
	 * @return the state, {@link SensorState#Ok} when the reading is within all the thresholds, null when the reply has
	 *         no byte 4
	 */
	public SensorState getSensorState() {
		return sensorState;
	}

	/**
	 * Sets the most severe threshold the reading is at or beyond.
	 *
	 * @param sensorState the state
	 */
	public void setSensorState(SensorState sensorState) {
		this.sensorState = sensorState;
	}

	/**
	 * Returns the response data as the BMC sent it, from byte 2 (the completion code is not included).
	 *
	 * @return the response data
	 */
	public byte[] getRaw() {
		return raw;
	}

	/**
	 * Sets the response data as the BMC sent it.
	 *
	 * @param raw the response data, from byte 2
	 */
	public void setRaw(byte[] raw) {
		this.raw = raw;
	}

	/**
	 * Returns the states the sensor asserts (bytes 4 and 5): for a discrete sensor, one state per bit set; for a
	 * threshold sensor, the going-low or going-high event of each threshold the reading is at or beyond.
	 *
	 * @param sensorType
	 *        - the type of the sensor, from its record
	 * @param sensorEventReadingType
	 *        - value received via
	 *        {@link FullSensorRecord#getEventReadingType()},
	 *        {@link CompactSensorRecord#getEventReadingType()} or
	 *        {@link EventOnlyRecord#getEventReadingType()}
	 * @return the asserted states, empty when the reply has no state byte
	 */
	public List<ReadingType> getStatesAsserted(
			SensorType sensorType,
			int sensorEventReadingType) {
		ArrayList<ReadingType> list = new ArrayList<ReadingType>();
		if (statesAsserted == null) {
			return list;
		}
		if (sensorEventReadingType == THRESHOLD_EVENT_READING_TYPE) {
			// Byte 4 of a threshold sensor holds comparison bits (Table 35-15), not event offsets: bit n means
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

	/**
	 * Sets the state bits.
	 *
	 * @param statesAsserted the state bits of bytes 4 and 5, bit 0 of byte 4 first
	 */
	public void setStatesAsserted(boolean[] statesAsserted) {
		this.statesAsserted = statesAsserted;
	}
}
