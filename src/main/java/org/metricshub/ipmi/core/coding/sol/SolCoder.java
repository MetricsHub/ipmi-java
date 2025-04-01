package org.metricshub.ipmi.core.coding.sol;

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

import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.sol.SolAckState;
import org.metricshub.ipmi.core.coding.payload.sol.SolInboundMessage;
import org.metricshub.ipmi.core.coding.payload.sol.SolInboundStatusField;
import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;
import org.metricshub.ipmi.core.coding.payload.sol.SolOutboundMessage;
import org.metricshub.ipmi.core.coding.payload.sol.SolOutboundOperationField;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of {@link PayloadCoder} for payload type {@link PayloadType#Sol}.
 * This coder is used during exchanging SOL messages between BMC and application.
 */
public class SolCoder extends PayloadCoder {

    private final byte[] message;
    private final byte ackNackSequenceNumber;
    private final byte acceptedCharacters;
    private final SolAckState ackState;
    private final Set<SolOperation> operations;
    private boolean acknowledgeOnly;


    /**
     * Creates new {@link SolCoder}. Use this constructor if you want to send character data, operations and ACK/NACK in single packet.
     *
     * @param message
     *          data to send as byte array
     * @param ackNackSequenceNumber
     *          sequence number of packet that is ACKd/NACKd
     * @param acceptedCharacters
     *          number of characters accepted from the ACKd/NACKd packet
     * @param ackState
     *          actual acknowledge state - {@link SolAckState#ACK} or {@link SolAckState#NACK}
     * @param operations
     *          set of operations to invoke on remote serial port
     * @param cipherSuite
     *          {@link CipherSuite} containing authentication, confidentiality and integrity algorithms for this session
     */
    public SolCoder(byte[] message, byte ackNackSequenceNumber, byte acceptedCharacters, SolAckState ackState,
                    Set<SolOperation> operations, CipherSuite cipherSuite) {
        super(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus);
        this.message = message;
        this.ackNackSequenceNumber = ackNackSequenceNumber;
        this.acceptedCharacters = acceptedCharacters;
        this.ackState = ackState;
        this.operations = operations;
    }

    /**
     * Creates new {@link SolCoder}. Use this constructor for character data only packets (no ACK/NACK and no operations will be sent).
     *
     * @param message
     *          data to send as byte array
     * @param cipherSuite
     *          {@link CipherSuite} containing authentication, confidentiality and integrity algorithms for this session.
     */
    public SolCoder(byte[] message, CipherSuite cipherSuite) {
        this(message, (byte) 0, (byte) 0, SolAckState.ACK, new HashSet<SolOperation>(), cipherSuite);
    }

    /**
     * Creates new {@link SolCoder}. Use this constructor for packets that should only invoke operations on remote serial port.
     *
     * @param operations
     *          set of operations to invoke on remote serial port.
     * @param cipherSuite
     *          {@link CipherSuite} containing authentication, confidentiality and integrity algorithms for this session
     */
    public SolCoder(Set<SolOperation> operations, CipherSuite cipherSuite) {
        this(new byte[] { '\0' }, (byte) 0, (byte) 0, SolAckState.ACK, operations, cipherSuite);
    }

    /**
     * Creates new {@link SolCoder}. Use this constructor for ACK/NACK packet, that only sends ACK/NACK for specific remote message.
     *
     * @param ackNackSequenceNumber
     *           sequence number of packet that is ACKd/NACKd
     * @param acceptedCharacters
     *          number of characters accepted from the ACKd/NACKd packet
     * @param ackState
     *          actual acknowledge state - {@link SolAckState#ACK} or {@link SolAckState#NACK}
     * @param cipherSuite
     *          {@link CipherSuite} containing authentication, confidentiality and integrity algorithms for this session.
     */
    public SolCoder(byte ackNackSequenceNumber, byte acceptedCharacters, SolAckState ackState, CipherSuite cipherSuite) {
        this(null, ackNackSequenceNumber, acceptedCharacters, ackState, new HashSet<SolOperation>(), cipherSuite);

        this.acknowledgeOnly = true;
    }

    @Override
    public PayloadType getSupportedPayloadType() {
        return PayloadType.Sol;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
        byte actualSequenceNumber = acknowledgeOnly ? 0 : TypeConverter.intToByte(sequenceNumber);

        SolOutboundMessage request = new SolOutboundMessage(actualSequenceNumber, ackNackSequenceNumber, acceptedCharacters,
                new SolOutboundOperationField(ackState, operations));

        if (message != null && message.length > 0) {
            request.setData(message);
        }

        return request;
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        final SolInboundMessage payload = (SolInboundMessage) message.getPayload();
        SolInboundStatusField statusField = payload.getStatusField();

        return new SolResponseData(payload.getAckNackSequenceNumber(), statusField.getAckState(), statusField.getStatuses(), payload.getAcceptedCharacterCount());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SolCoder solCoder = (SolCoder) o;

        if (ackNackSequenceNumber != solCoder.ackNackSequenceNumber) return false;
        if (acceptedCharacters != solCoder.acceptedCharacters) return false;
        if (acknowledgeOnly != solCoder.acknowledgeOnly) return false;
        if (!Arrays.equals(message, solCoder.message)) return false;
        if (ackState != solCoder.ackState) return false;
        return operations != null ? operations.equals(solCoder.operations) : solCoder.operations == null;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(message);
        result = 31 * result + (int) ackNackSequenceNumber;
        result = 31 * result + (int) acceptedCharacters;
        result = 31 * result + (ackState != null ? ackState.hashCode() : 0);
        result = 31 * result + (operations != null ? operations.hashCode() : 0);
        result = 31 * result + (acknowledgeOnly ? 1 : 0);
        return result;
    }
}
