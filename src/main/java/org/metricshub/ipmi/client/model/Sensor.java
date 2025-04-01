package org.metricshub.ipmi.client.model;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
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

import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;

/**
 * Wraps Sensor information:
 * <ul>
 * <li>The Sensor record which can be either {@link CompactSensorRecord} or {@link FullSensorRecord}. Note that the compact sensor record is
 * used to discover and collect the state of the devices, where the full sensor record is used to collect the devices reading values, i.e.
 * temperature, power consumption, voltage, ...etc.</li>
 * <li>The sensor reading response data wrapping the reading value</li>
 * <li>The states of the sensor record formatted as the following: <em>$sensorName=$state|$sensorName=$state|...|$sensorName=$state</em></li>
 * </ul>
 */
public class Sensor {

	private SensorRecord sensorRecord;
	private GetSensorReadingResponseData data;
	private String states;

	public Sensor(SensorRecord sensorRecord, GetSensorReadingResponseData data, String states) {
		this.sensorRecord = sensorRecord;
		this.data = data;
		this.states = states;
	}

	public SensorRecord getRecord() {
		return sensorRecord;
	}

	public GetSensorReadingResponseData getData() {
		return data;
	}

	public boolean isCompact() {
		return sensorRecord instanceof CompactSensorRecord;
	}

	public boolean isFull() {
		return sensorRecord instanceof FullSensorRecord;
	}

	/**
	 * Cast the current record to a {@link CompactSensorRecord}
	 *
	 * @return {@link CompactSensorRecord} instance
	 */
	private CompactSensorRecord getCompactSensorRecord() {
		return (CompactSensorRecord) sensorRecord;
	}

	/**
	 * Cast the current record to a {@link FullSensorRecord}
	 *
	 * @return {@link FullSensorRecord} instance
	 */
	private FullSensorRecord getFullSensorRecord() {
		return (FullSensorRecord) sensorRecord;
	}

	/**
	 * Get the {@link EntityId} from the current <code>SensorRecord record;</code> instance
	 *
	 * @return {@link EntityId} instance
	 */
	public EntityId getEntityId() {

		if (isCompact()) {
			return getCompactSensorRecord().getEntityId();
		} else if (isFull()) {
			return getFullSensorRecord().getEntityId();
		}
		return null;
	}

	/**
	 * Get the device identifier
	 *
	 * @return {@link Byte} value or <code>null</code> if the type of the sensor cannot be detected.
	 */
	public Byte getDeviceId() {
		if (isCompact()) {
			return getCompactSensorRecord().getEntityInstanceNumber();
		} else if (isFull()) {
			return getFullSensorRecord().getEntityInstanceNumber();
		}
		return null;
	}

	/**
	 * Get the name of the sensor record
	 *
	 * @return {@link String} value
	 */
	public String getName() {
		if (isCompact()) {
			return getCompactSensorRecord().getName();
		} else if (isFull()) {
			return getFullSensorRecord().getName();
		}
		return null;
	}

	/**
	 * @return state values formatted as: <em>$sensorName=$state|$sensorName=$state...|$sensorName=$state</em>
	 */
	public String getStates() {
		return states;
	}
}
