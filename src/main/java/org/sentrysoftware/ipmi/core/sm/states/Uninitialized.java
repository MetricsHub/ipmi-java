package org.sentrysoftware.ipmi.core.sm.states;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, Sentry Software
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

import org.sentrysoftware.ipmi.core.coding.Encoder;
import org.sentrysoftware.ipmi.core.coding.commands.session.GetChannelCipherSuites;
import org.sentrysoftware.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.sentrysoftware.ipmi.core.coding.rmcp.RmcpMessage;
import org.sentrysoftware.ipmi.core.common.TypeConverter;
import org.sentrysoftware.ipmi.core.sm.StateMachine;
import org.sentrysoftware.ipmi.core.sm.actions.ErrorAction;
import org.sentrysoftware.ipmi.core.sm.events.Default;
import org.sentrysoftware.ipmi.core.sm.events.GetChannelCipherSuitesPending;
import org.sentrysoftware.ipmi.core.sm.events.StateMachineEvent;

/**
 * The initial state. Transits to {@link CiphersWaiting} on
 * {@link GetChannelCipherSuitesPending}.
 */
public class Uninitialized extends State {

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof GetChannelCipherSuitesPending) {
            Default event = (GetChannelCipherSuitesPending) machineEvent;
            GetChannelCipherSuites cipherSuites = new GetChannelCipherSuites(
                    TypeConverter.intToByte(0xE), (byte) 0);
            try {
                stateMachine.setCurrent(new CiphersWaiting(0, event
                        .getSequenceNumber()));

                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(), cipherSuites,
                        event.getSequenceNumber(), 0,0));
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
