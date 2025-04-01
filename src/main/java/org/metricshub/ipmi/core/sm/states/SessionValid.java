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
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.session.CloseSession;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.protocol.decoder.ProtocolDecoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv20Decoder;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.actions.MessageAction;
import org.metricshub.ipmi.core.sm.events.Sendv20Message;
import org.metricshub.ipmi.core.sm.events.SessionUpkeep;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;
import org.metricshub.ipmi.core.sm.events.Timeout;

/**
 * {@link State} in which the session is valid and sending IPMI commands to the
 * remote machine is enabled. 
 * <ul>
 * <li>Sends an IPMI v2.0 message on
 * {@link Sendv20Message} <li>Sends {@link GetChannelAuthenticationCapabilities}
 * message to keep the session form timing out on {@link SessionUpkeep}</li>
 * <li> Transits to {@link Authcap} on {@link Timeout} <li>Sends {@link CloseSession}
 * and transits to {@link Authcap} on {@link org.metricshub.ipmi.core.coding.commands.session.CloseSession}</li>
 * </ul>
 */
public class SessionValid extends State {

    private CipherSuite cipherSuite;

    private int sessionId;

    public CipherSuite getCipherSuite() {
        return cipherSuite;
    }

    /**
     * Initiates the state.
     *
     * @param cipherSuite
     *            - {@link CipherSuite} used during the session.
     */
    public SessionValid(CipherSuite cipherSuite, int sessionId) {
        this.cipherSuite = cipherSuite;
        this.sessionId = sessionId;
    }

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof Sendv20Message) {
            Sendv20Message event = (Sendv20Message) machineEvent;
            try {
                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(), event.getPayloadCoder(), event.getMessageSequenceNumber(),
                        event.getSessionSequenceNumber(), event.getSessionId()));
            } catch (Exception e) {
                stateMachine.doExternalAction(new ErrorAction(e));
            }
        } else if (machineEvent instanceof SessionUpkeep) {
            SessionUpkeep event = (SessionUpkeep) machineEvent;
            try {
                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(),
                        new GetChannelAuthenticationCapabilities(
                                IpmiVersion.V20, IpmiVersion.V20, cipherSuite,
                                PrivilegeLevel.Callback, TypeConverter.intToByte(0xe)),
                                event.getMessageSequenceNumber(), event.getSessionSequenceNumber(), event.getSessionId()));
            } catch (Exception e) {
                stateMachine.doExternalAction(new ErrorAction(e));
            }
        } else if (machineEvent instanceof Timeout) {
            stateMachine.setCurrent(new Authcap());
        } else if (machineEvent instanceof org.metricshub.ipmi.core.sm.events.CloseSession) {
            org.metricshub.ipmi.core.sm.events.CloseSession event = (org.metricshub.ipmi.core.sm.events.CloseSession) machineEvent;

            try {
                stateMachine.setCurrent(new Authcap());
                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(),
                        new CloseSession(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus, event.getSessionId()),
                                event.getMessageSequenceNumber(), event.getSessionSequenceNumber(), event.getSessionId()));
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
        if (ProtocolDecoder.decodeAuthenticationType(message) != AuthenticationType.RMCPPlus) {
            return; // this isn't IPMI v2.0 message so we ignore it
        }
        if (Protocolv20Decoder.decodeSessionID(message) == 0) {
            return; // this is a sessionless message so we drop it
        }
        Protocolv20Decoder decoder = new Protocolv20Decoder(cipherSuite);
        PayloadType payloadType = Protocolv20Decoder.decodePayloadType(message.getData()[1]);

        if (payloadType != PayloadType.Ipmi && payloadType != PayloadType.Sol) {
            return;
        }
        if (Protocolv20Decoder.decodeSessionID(message) != sessionId) {
            return; // this message belongs to other session so we ignore it
        }
        try {
            Ipmiv20Message message20 = (Ipmiv20Message) decoder.decode(message);
            if (message20.getSessionID() == sessionId) {
                stateMachine.doExternalAction(new MessageAction(message20));
            }
        } catch (Exception e) {
            stateMachine.doExternalAction(new ErrorAction(e));
        }
    }

}
