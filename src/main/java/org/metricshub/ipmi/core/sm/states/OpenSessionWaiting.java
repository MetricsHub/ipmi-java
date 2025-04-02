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

import org.metricshub.ipmi.core.coding.commands.session.OpenSession;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.protocol.decoder.PlainCommandv20Decoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.ProtocolDecoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv20Decoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.actions.ResponseAction;
import org.metricshub.ipmi.core.sm.events.DefaultAck;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;
import org.metricshub.ipmi.core.sm.events.Timeout;

/**
 * Waiting for the {@link OpenSession} response.<br>
 * <ul>
 * <li>Transition to {@link OpenSessionComplete} on {@link DefaultAck}</li>
 * <li> Transition to {@link Authcap} on {@link Timeout}</li>
 * </ul>
 */
public class OpenSessionWaiting extends State {

    private int tag;

    public OpenSessionWaiting(int tag) {
        this.tag = tag;
    }

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof DefaultAck) {
            stateMachine.setCurrent(new OpenSessionComplete());
        } else if (machineEvent instanceof Timeout) {
            stateMachine.setCurrent(new Authcap());
        } else {
            stateMachine.doExternalAction(new ErrorAction(
                    new IllegalArgumentException("Invalid transition")));
        }
    }

    @Override
    public void doAction(StateMachine stateMachine, RmcpMessage message) {
        if (ProtocolDecoder.decodeAuthenticationType(message) != AuthenticationType.RMCPPlus) {
            return; // this isn't IPMI v2.0 message so we ignore it
        }
        PlainCommandv20Decoder decoder = new PlainCommandv20Decoder(
                CipherSuite.getEmpty());
        if (Protocolv20Decoder.decodePayloadType(message.getData()[1]) != PayloadType.RmcpOpenSessionResponse) {
            return;
        }
        IpmiMessage ipmiMessage = null;
        try {
            ipmiMessage = decoder.decode(message);
            OpenSession openSession = new OpenSession(CipherSuite.getEmpty());
            if (openSession.isCommandResponse(ipmiMessage)
                    && TypeConverter.byteToInt(((PlainMessage) ipmiMessage
                            .getPayload()).getPayloadData()[0]) == tag) {
                stateMachine.doExternalAction(new ResponseAction(openSession
                        .getResponseData(ipmiMessage)));
            }
        } catch (Exception e) {
            stateMachine.doExternalAction(new ErrorAction(e));
        }
    }
}
