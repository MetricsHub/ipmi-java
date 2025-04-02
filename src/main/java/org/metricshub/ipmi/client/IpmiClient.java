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

import static org.metricshub.ipmi.client.Utils.execute;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.metricshub.ipmi.client.model.Fru;
import org.metricshub.ipmi.client.model.Sensor;
import org.metricshub.ipmi.client.runner.GetChassisStatusRunner;
import org.metricshub.ipmi.client.runner.GetFrusRunner;
import org.metricshub.ipmi.client.runner.GetSensorsRunner;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;

/**
 * This class is the entry point of the IPMI Client library
 */
public class IpmiClient {

	private IpmiClient() {
	}

	/**
	 * Run the get Chassis status IPMI request
	 *
	 * @param ipmiConfiguration Wraps the IPMI device hostname and the credentials
	 * @return {@link GetChassisStatusResponseData} instance
	 *
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public static GetChassisStatusResponseData getChassisStatus(final IpmiClientConfiguration ipmiConfiguration)
			throws InterruptedException, ExecutionException, TimeoutException {
		try (GetChassisStatusRunner runner = new GetChassisStatusRunner(ipmiConfiguration)){
			return execute(runner, ipmiConfiguration.getTimeout() * 1000);
		}
	}

	/**
	 * Get the sensors
	 *
	 * @param ipmiConfiguration Wraps the IPMI device hostname and the credentials
	 * @return List of {@link Sensor} instances
	 *
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public static List<Sensor> getSensors(final IpmiClientConfiguration ipmiConfiguration)
			throws InterruptedException, ExecutionException, TimeoutException {
		try (GetSensorsRunner runner = new GetSensorsRunner(ipmiConfiguration)) {
			return execute(runner, ipmiConfiguration.getTimeout() * 1000);
		}
	}

	/**
	 * Get FRU information
	 *
	 * @param ipmiConfiguration Wraps the IPMI device hostname and the credentials
	 * @return List of {@link Fru} instances
	 *
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public static List<Fru> getFrus(final IpmiClientConfiguration ipmiConfiguration)
			throws InterruptedException, ExecutionException, TimeoutException {
		try (GetFrusRunner runner = new GetFrusRunner(ipmiConfiguration)) {
			return execute(runner, ipmiConfiguration.getTimeout() * 1000);
		}
	}

	/**
	 * Run the get Chassis status IPMI request then convert the result to String
	 *
	 * @param ipmiConfiguration Wraps the IPMI device hostname and the credentials
	 * @return Chassis status as String value
	 * @throws TimeoutException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static String getChassisStatusAsStringResult(final IpmiClientConfiguration ipmiConfiguration)
			throws InterruptedException, ExecutionException, TimeoutException {
		return IpmiResultConverter.convertResult(getChassisStatus(ipmiConfiguration));
	}

	/**
	 * Run the FRUs and Sensors request then convert the result to a String value
	 *
	 * @param ipmiConfiguration Wraps the IPMI device hostname and the credentials
	 * @return All sensors (FRUs, Sensors readings and Sensors states) as String value
	 * @throws TimeoutException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static String getFrusAndSensorsAsStringResult(final IpmiClientConfiguration ipmiConfiguration)
			throws InterruptedException, ExecutionException, TimeoutException {
		return IpmiResultConverter.convertResult(getFrus(ipmiConfiguration), getSensors(ipmiConfiguration));
	}
}
