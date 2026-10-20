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

/**
 * The status of a sensor: for a threshold sensor, the worst threshold its reading is at or beyond (IPMI 2.0 table
 * 35-15); for a discrete sensor, whether it answers.
 */
public enum SensorStatus {
	/** The reading is within the thresholds, or the discrete sensor answers. */
	OK,
	/** The reading is at or beyond a non-critical threshold. */
	NON_CRITICAL,
	/** The reading is at or beyond a critical threshold. */
	CRITICAL,
	/** The reading is at or beyond a non-recoverable threshold. */
	NON_RECOVERABLE,
	/**
	 * The sensor has no reading and no states: the BMC or the controller that owns the sensor refused the reading or
	 * sent a malformed reply, that controller did not answer through the BMC, the sensor belongs to system software,
	 * or the sensor is not scanned or its reading is not valid yet ({@link Sensor#problem()} says which).
	 */
	UNAVAILABLE
}
