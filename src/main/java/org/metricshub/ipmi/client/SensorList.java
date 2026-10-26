package org.metricshub.ipmi.client;

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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The sensors read by {@link IpmiClient#sensors()}: an unmodifiable list, with the SDR records the repository walk
 * skipped.
 */
public final class SensorList extends AbstractList<Sensor> {

	private final List<Sensor> sensors;
	private final List<SkippedRecord> skippedRecords;

	/**
	 * Creates the list.
	 *
	 * @param sensors the sensors, in the order of the SDR repository
	 * @param skippedRecords the records the walk skipped
	 */
	SensorList(List<Sensor> sensors, List<SkippedRecord> skippedRecords) {
		this.sensors = Collections.unmodifiableList(new ArrayList<Sensor>(sensors));
		this.skippedRecords = Collections.unmodifiableList(new ArrayList<SkippedRecord>(skippedRecords));
	}

	@Override
	public Sensor get(int index) {
		return sensors.get(index);
	}

	@Override
	public int size() {
		return sensors.size();
	}

	/**
	 * Returns the SDR records the repository walk could not use, with the reason.
	 *
	 * @return the skipped records, empty when every record was read and decoded
	 */
	public List<SkippedRecord> skippedRecords() {
		return skippedRecords;
	}

	@Override
	public String toString() {
		return sensors.size() + " sensor(s)"
				+ (skippedRecords.isEmpty() ? "" : ", " + skippedRecords.size() + " SDR record(s) skipped") + ": " + sensors;
	}
}
