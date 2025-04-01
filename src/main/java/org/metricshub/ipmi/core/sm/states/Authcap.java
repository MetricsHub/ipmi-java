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
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.commands.session.OpenSession;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.events.Authorize;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;

/**
 * {@link GetChannelAuthenticationCapabilities} response was received. At this
 * point the Session Challenge is going to start. Transits to
 * {@link OpenSessionWaiting} on {@link Authorize}.
 */
public class Authcap extends State {

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof Authorize) {
            Authorize event = (Authorize) machineEvent;

            OpenSession openSession = new OpenSession(event.getSessionId(),
                    event.getPrivilegeLevel(), event.getCipherSuite());

            try {
                stateMachine.setCurrent(new OpenSessionWaiting(event.getSequenceNumber()));
                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(), openSession,
                        event.getSequenceNumber(), 0, 0));
            } catch (Exception e) {
                stateMachine.setCurrent(this);
                stateMachine.doExternalAction(new ErrorAction(e));
            }
        } else {
            stateMachine.doExternalAction(new ErrorAction(
                    new IllegalArgumentException("Invalid transition: "
                            + machineEvent.getClass().getSimpleName())));
        }

    }

    @Override
    public void doAction(StateMachine stateMachine, RmcpMessage message) {
        // No action needed
    }

}
