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

/**
 * The response to {@link GetDeviceSdrInfo} (IPMI 2.0 table 35-2).
 */
public class GetDeviceSdrInfoResponseData implements ResponseData {

	private final int count;

	private final boolean dynamicPopulation;

	private final int lunsWithSensors;

	private final Long populationChangeIndicator;

	/**
	 * Creates the response.
	 *
	 * @param count the number of SDRs of the device, or of sensors at the LUN, as the request asked
	 * @param dynamicPopulation whether the sensor population of the device may change at run time
	 * @param lunsWithSensors the LUNs that have sensors, bit n set for LUN n
	 * @param populationChangeIndicator the timestamp or counter of the last change of a dynamic population, null for
	 *        a static one
	 */
	public GetDeviceSdrInfoResponseData(int count, boolean dynamicPopulation, int lunsWithSensors,
			Long populationChangeIndicator) {
		this.count = count;
		this.dynamicPopulation = dynamicPopulation;
		this.lunsWithSensors = lunsWithSensors;
		this.populationChangeIndicator = populationChangeIndicator;
	}

	/**
	 * @return the number of SDRs of the device, or of sensors at the LUN, as the request asked
	 */
	public int getCount() {
		return count;
	}

	/**
	 * @return whether the sensor population of the device may change at run time
	 */
	public boolean isDynamicPopulation() {
		return dynamicPopulation;
	}

	/**
	 * @return the LUNs that have sensors: bit n set for LUN n
	 */
	public int getLunsWithSensors() {
		return lunsWithSensors;
	}

	/**
	 * @return the timestamp or counter the device updates each time its sensor population changes, null for a static
	 *         population
	 */
	public Long getPopulationChangeIndicator() {
		return populationChangeIndicator;
	}
}
