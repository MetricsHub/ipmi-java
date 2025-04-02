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
import org.metricshub.ipmi.core.coding.commands.session.Rakp1;
import org.metricshub.ipmi.core.coding.commands.session.Rakp3;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.actions.GetSikAction;
import org.metricshub.ipmi.core.sm.events.Rakp2Ack;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;

/**
 * At this state RAKP Message 2 was received - waiting for the confirmation to
 * send RAKP Message 3. Transition to {@link Rakp3Waiting} on {@link Rakp2Ack}.
 */
public class Rakp1Complete extends State {

    private Rakp1 rakp1;

    /**
     * Initiates state.
     *
     * @param rakp1
     *            - the {@link Rakp1} message that was sent earlier in the
     *            authentification process.
     */
    public Rakp1Complete(Rakp1 rakp1) {
        this.rakp1 = rakp1;
    }

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof Rakp2Ack) {
            Rakp2Ack event = (Rakp2Ack) machineEvent;

            Rakp3 rakp3 = new Rakp3(event.getStatusCode(),
                    event.getManagedSystemSessionId(), event.getCipherSuite(),
                    rakp1, event.getRakp1ResponseData());

            try {
                stateMachine.setCurrent(new Rakp3Waiting(event
                        .getSequenceNumber(), rakp1, event
                        .getRakp1ResponseData(), event.getCipherSuite()));
                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(), rakp3,
                        event.getSequenceNumber(), 0, 0));
                stateMachine.doExternalAction(new GetSikAction(rakp1
                        .calculateSik(event.getRakp1ResponseData())));
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
