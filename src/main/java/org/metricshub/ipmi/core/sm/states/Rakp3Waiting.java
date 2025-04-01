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

import org.metricshub.ipmi.core.coding.commands.session.Rakp1;
import org.metricshub.ipmi.core.coding.commands.session.Rakp1ResponseData;
import org.metricshub.ipmi.core.coding.commands.session.Rakp3;
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
 * At this point of session challenge, RAKP Message 3 was sent,
 * {@link StateMachine} is waiting for RAKP Message 4.<br>
 * Transition to:
 * <ul>
 * <li>{@link Rakp3Complete} on {@link DefaultAck}</li>
 * <li>{@link Authcap} on {@link Timeout}</li>
 * </ul>
 */
public class Rakp3Waiting extends State {

    private Rakp1 rakp1;
    private Rakp1ResponseData rakp1ResponseData;
    private CipherSuite cipherSuite;
    private int tag;

    /**
     * Initiates state.
     *
     * @param rakp1
     *            - the {@link Rakp1} message that was sent earlier in the
     *            authentification process.
     * @param rakp1ResponseData
     *            - the {@link Rakp1ResponseData} that was received earlier in
     *            the authentification process.
     * @param cipherSuite
     *            - the {@link CipherSuite} used during this session.
     */
    public Rakp3Waiting(int tag, Rakp1 rakp1,
            Rakp1ResponseData rakp1ResponseData, CipherSuite cipherSuite) {
        this.rakp1 = rakp1;
        this.rakp1ResponseData = rakp1ResponseData;
        this.cipherSuite = cipherSuite;
        this.tag = tag;
    }

    @Override
    public void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent) {
        if (machineEvent instanceof DefaultAck) {
            stateMachine.setCurrent(new Rakp3Complete());
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
        if (Protocolv20Decoder.decodePayloadType(message.getData()[1]) != PayloadType.Rakp4) {
            return;
        }

        IpmiMessage ipmiMessage = null;
        Rakp3 rakp3 = new Rakp3(cipherSuite, rakp1, rakp1ResponseData);
        try {
            ipmiMessage = decoder.decode(message);
            if (rakp3.isCommandResponse(ipmiMessage)
                    && TypeConverter.byteToInt(((PlainMessage) ipmiMessage
                            .getPayload()).getPayloadData()[0]) == tag) {
                stateMachine.doExternalAction(new ResponseAction(rakp3
                        .getResponseData(ipmiMessage)));
            }
        } catch (Exception e) {
            stateMachine.doExternalAction(new ErrorAction(e));
        }
    }

}
