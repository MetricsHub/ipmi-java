package org.metricshub.ipmi.client.runner;

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

import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;

/**
 * Get the Chassis Status runner
 */
public class GetChassisStatusRunner extends AbstractIpmiRunner<GetChassisStatusResponseData> {

	public GetChassisStatusRunner(IpmiClientConfiguration ipmiConfiguration) {
		super(ipmiConfiguration);
	}

	@Override
	public GetChassisStatusResponseData call() throws Exception {
		super.startSession();

		// Send the UDP message and read the response
		return (GetChassisStatusResponseData) connector.sendMessage(handle,
				new GetChassisStatus(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus));

	}

}
