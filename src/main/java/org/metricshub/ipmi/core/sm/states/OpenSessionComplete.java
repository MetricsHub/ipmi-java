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
import org.metricshub.ipmi.core.coding.commands.session.OpenSession;
import org.metricshub.ipmi.core.coding.commands.session.Rakp1;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.events.OpenSessionAck;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;

/**
 * Indicates that {@link OpenSession} response was received. Transition to
 * {@link Rakp1Waiting} on {@link OpenSessionAck}
 */
public class OpenSessionComplete extends State {

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof OpenSessionAck) {
            OpenSessionAck event = (OpenSessionAck) machineEvent;

            Rakp1 rakp1 = new Rakp1(event.getManagedSystemSessionId(),
                    event.getPrivilegeLevel(), event.getUsername(),
                    event.getPassword(), event.getBmcKey(),
                    event.getCipherSuite());

            try {
                stateMachine.setCurrent(new Rakp1Waiting(event.getSequenceNumber(), rakp1));
                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(), rakp1,
                        event.getSequenceNumber(), 0, 0));
            } catch (Exception e) {
                stateMachine.setCurrent(this);
                stateMachine.doExternalAction(new ErrorAction(e));
            }
        } else {
            stateMachine.doExternalAction(new ErrorAction(
                    new IllegalArgumentException("Invalid transition")));
        }

    }

    @Override
    public void doAction(StateMachine stateMachine, RmcpMessage message) {
        // No action is needed
    }

}
