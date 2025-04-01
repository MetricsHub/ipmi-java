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
import org.metricshub.ipmi.core.coding.commands.session.GetChannelCipherSuites;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.protocol.decoder.ProtocolDecoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv20Decoder;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.actions.ResponseAction;
import org.metricshub.ipmi.core.sm.events.DefaultAck;
import org.metricshub.ipmi.core.sm.events.GetChannelCipherSuitesPending;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;
import org.metricshub.ipmi.core.sm.events.Timeout;

/**
 * State at which getting Channel Cipher Suites is in progress. Transits back to
 * {@link Uninitialized} on {@link Timeout}, further proceeds with getting
 * Cipher Suites on {@link GetChannelCipherSuitesPending} and moves on to
 * {@link Ciphers} on {@link DefaultAck}
 */
public class CiphersWaiting extends State {

    private int index;

    private int tag;

    /**
     * Initializes state.
     *
     * @param index
     *            - Index of the channel cipher suite package to get
     * @param tag
     *            - Tag of the message
     */
    public CiphersWaiting(int index, int tag) {
        this.index = index;
        this.tag = tag;
    }

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof Timeout) {
            stateMachine.setCurrent(new Uninitialized());
        } else if (machineEvent instanceof GetChannelCipherSuitesPending) {
            GetChannelCipherSuitesPending event = (GetChannelCipherSuitesPending) machineEvent;
            GetChannelCipherSuites cipherSuites = new GetChannelCipherSuites(
                    TypeConverter.intToByte(0xE),
                    TypeConverter.intToByte(index + 1));
            try {
                tag = event.getSequenceNumber();
                stateMachine.sendMessage(Encoder.encode(
                        new Protocolv20Encoder(), cipherSuites,
                        event.getSequenceNumber(), 0, 0));
                ++index;
            } catch (Exception e) {
                stateMachine.doExternalAction(new ErrorAction(e));
            }
        } else if (machineEvent instanceof DefaultAck) {
            stateMachine.setCurrent(new Ciphers());
        } else {
            stateMachine.doExternalAction(new ErrorAction(
                    new IllegalArgumentException("Invalid transition")));
        }
    }

    @Override
    public void doAction(StateMachine stateMachine, RmcpMessage message) {
        if(ProtocolDecoder.decodeAuthenticationType(message) != AuthenticationType.RMCPPlus) {
            return;    //this isn't IPMI v2.0 message so we ignore it
        }
        if(Protocolv20Decoder.decodeSessionID(message) != 0){
            return;    //this isn't sessionless message so we drop it
        }
        if (Protocolv20Decoder.decodePayloadType(message.getData()[1]) != PayloadType.Ipmi) {
            return;
        }
        Protocolv20Decoder decoder = new Protocolv20Decoder(
                CipherSuite.getEmpty());
        if(decoder.decodeAuthentication(message.getData()[1])) {
            return;    //message is authenticated so it does belong to the other session
        }
        IpmiMessage ipmiMessage = null;
        try {
            ipmiMessage = decoder.decode(message);
            GetChannelCipherSuites suites = new GetChannelCipherSuites();
            if (suites.isCommandResponse(ipmiMessage)
                    && TypeConverter.byteToInt(((IpmiLanResponse) ipmiMessage
                            .getPayload()).getSequenceNumber()) == tag) {
                stateMachine.doExternalAction(new ResponseAction(suites
                        .getResponseData(ipmiMessage)));
            }
        } catch (Exception e) {
            stateMachine.doExternalAction(new ErrorAction(e));
        }
    }

}
