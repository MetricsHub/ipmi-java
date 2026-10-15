package org.metricshub.ipmi.core.coding.commands.sdr;

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

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorReadingFactors;

/**
 * Reply to Get Sensor Reading Factors (IPMI 2.0 table 35-5).
 */
public class GetSensorReadingFactorsResponseData implements ResponseData {

	private final int nextReading;

	private final SensorReadingFactors factors;

	/**
	 * Creates the reply.
	 *
	 * @param nextReading the next reading byte for which the BMC defines other factors
	 * @param factors the factors of the requested reading byte
	 */
	public GetSensorReadingFactorsResponseData(int nextReading, SensorReadingFactors factors) {
		this.nextReading = nextReading;
		this.factors = factors;
	}

	/**
	 * Returns the next reading byte for which the BMC defines other factors; when the requested byte matches no entry
	 * of the table of the BMC, the byte that would have matched the returned factors exactly.
	 *
	 * @return the next reading byte, 0 to 255
	 */
	public int getNextReading() {
		return nextReading;
	}

	/**
	 * Returns the factors of the requested reading byte, to pass to
	 * {@link org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord#calcFormula(int, SensorReadingFactors)}.
	 *
	 * @return the factors
	 */
	public SensorReadingFactors getFactors() {
		return factors;
	}
}
