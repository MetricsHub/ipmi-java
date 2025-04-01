package org.metricshub.ipmi.core.coding.payload.sol;

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

import org.metricshub.ipmi.core.common.TypeConverter;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link SolOutboundOperationField} is a transfer object for operation sent by this application to remote system in {@link SolOutboundMessage}.
 */
public class SolOutboundOperationField {

    /**
     * Acknowledge state of {@link SolMessage} that this message is response for.
     */
    private final SolAckState ackState;

    /**
     * Set of operations to invoke on BMC.
     */
    private final Set<SolOperation> operations;

    /**
     * Creates new instance of {@link SolOutboundOperationField} filled with given data.
     *
     * @param ackState
     *          Acknowledge state carried by this object
     * @param operations
     *          Set of SOL specific operations for outbound message
     */
    public SolOutboundOperationField(SolAckState ackState, Set<SolOperation> operations) {
        this.ackState = ackState;
        this.operations = operations;
    }

    /**
     * Creates new instance of {@link SolOutboundOperationField} from raw byte.
     *
     * @param raw
     *          byte carrying information about SOL operations
     */
    public SolOutboundOperationField(byte raw) {
        this.ackState = SolAckState.extractFromByte(raw);
        this.operations = extractOperationsFromByte(raw);
    }

    protected Set<SolOperation> extractOperationsFromByte(byte raw) {
        Set<SolOperation> result = new HashSet<SolOperation>();

        for (SolOperation operation : SolOperation.values()) {
            if (TypeConverter.isBitSetOnPosition(operation.getOperationNumber(), raw)) {
                result.add(operation);
            }
        }

        return result;
    }

    public Set<SolOperation> getOperations() {
        return operations;
    }

    public SolAckState getAckState() {
        return ackState;
    }

    /**
     * Convert this object to it's raw, byte representation.
     */
    public byte convertToByte() {
        byte value = (byte) 0;

       value = ackState.encodeInByte(value);

        for (SolOperation operation : operations) {
            value = TypeConverter.setBitOnPosition(operation.getOperationNumber(), value);
        }

        return value;
    }

}
