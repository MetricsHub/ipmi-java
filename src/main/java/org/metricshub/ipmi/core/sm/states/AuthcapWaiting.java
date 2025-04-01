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

import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.decoder.ProtocolDecoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv15Decoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.actions.ResponseAction;
import org.metricshub.ipmi.core.sm.events.AuthenticationCapabilitiesReceived;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;
import org.metricshub.ipmi.core.sm.events.Timeout;

/**
 * Waiting for the {@link GetChannelAuthenticationCapabilities} response. <br>
 * 
 * Transition to: <ul> <li>{@link Ciphers} on {@link Timeout} </li><li>{@link Authcap} on
 * {@link AuthenticationCapabilitiesReceived}</li></ul>.
 */
public class AuthcapWaiting extends State {

    private int tag;

    /**
     * Instantiates a new {@link AuthcapWaiting} using the given tag 
     * @param tag int value representing the authentication capabilities
     */
    public AuthcapWaiting(int tag) {
        this.tag = tag;
    }

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof Timeout) {
            stateMachine.setCurrent(new Ciphers());
        } else if (machineEvent instanceof AuthenticationCapabilitiesReceived) {
            stateMachine.setCurrent(new Authcap());
        } else {
            stateMachine.doExternalAction(new ErrorAction(
                    new IllegalArgumentException("Invalid transition")));
        }
    }

    @Override
    public void doAction(StateMachine stateMachine, RmcpMessage message) {
        if (ProtocolDecoder.decodeAuthenticationType(message) == AuthenticationType.RMCPPlus) {
            return; // this isn't IPMI v1.5 message so we ignore it
        }
        Protocolv15Decoder decoder = new Protocolv15Decoder();
        IpmiMessage ipmiMessage = null;
        try {
            ipmiMessage = decoder.decode(message);
            GetChannelAuthenticationCapabilities capabilities = new GetChannelAuthenticationCapabilities();
            if (capabilities.isCommandResponse(ipmiMessage)
                    && TypeConverter.byteToInt(((IpmiLanResponse) ipmiMessage
                            .getPayload()).getSequenceNumber()) == tag) {
                stateMachine.doExternalAction(new ResponseAction(capabilities
                        .getResponseData(ipmiMessage)));
            }
        } catch (Exception e) {
            stateMachine.doExternalAction(new ErrorAction(e));
        }
    }

}
