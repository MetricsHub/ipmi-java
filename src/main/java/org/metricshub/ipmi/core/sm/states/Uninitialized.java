package org.metricshub.ipmi.core.sm.states;

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

import org.metricshub.ipmi.core.coding.Encoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelCipherSuites;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv15Encoder;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.events.Default;
import org.metricshub.ipmi.core.sm.events.GetChannelCipherSuitesPending;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;

/**
 * The initial state. Transits to {@link AuthcapWaiting} on {@link Default}, sending Get Channel Authentication
 * Capabilities in the IPMI v1.5 format, and to {@link CiphersWaiting} on {@link GetChannelCipherSuitesPending}.
 */
public class Uninitialized extends State {

	@Override
	public void doTransition(
			StateMachine stateMachine,
			StateMachineEvent machineEvent) {
		if (machineEvent instanceof GetChannelCipherSuitesPending) {
			Default event = (GetChannelCipherSuitesPending) machineEvent;
			GetChannelCipherSuites cipherSuites = new GetChannelCipherSuites(
					TypeConverter.intToByte(0xE),
					(byte) 0);
			try {
				stateMachine
						.setCurrent(
								new CiphersWaiting(
										0,
										event
												.getSequenceNumber()));

				stateMachine
						.sendMessage(
								Encoder
										.encode(
												new Protocolv20Encoder(),
												cipherSuites,
												event.getSequenceNumber(),
												0,
												0));
			} catch (Exception e) {
				stateMachine.setCurrent(this);
				stateMachine.doExternalAction(new ErrorAction(e));
			}
		} else if (machineEvent instanceof Default) {
			// IPMI 2.0 section 13.13: Get Channel Authentication Capabilities in the IPMI v1.5 format, which every BMC
			// answers, with the IPMI v2.0+ extended data (table 22-15), before any RMCP+ packet
			Default event = (Default) machineEvent;
			GetChannelAuthenticationCapabilities authCap = new GetChannelAuthenticationCapabilities(
					IpmiVersion.V15,
					IpmiVersion.V20,
					event.getCipherSuite(),
					event.getPrivilegeLevel(),
					TypeConverter.intToByte(0xe));
			try {
				stateMachine.setCurrent(new AuthcapWaiting(event.getSequenceNumber()));
				stateMachine
						.sendMessage(Encoder.encode(new Protocolv15Encoder(), authCap, event.getSequenceNumber(), 0, 0));
			} catch (Exception e) {
				stateMachine.setCurrent(this);
				stateMachine.doExternalAction(new ErrorAction(e));
			}
		} else {
			stateMachine
					.doExternalAction(
							new ErrorAction(
									new IllegalArgumentException("Invalid transition")));
		}
	}

	@Override
	public void doAction(StateMachine stateMachine, RmcpMessage message) {
		// No action is needed
	}

}
