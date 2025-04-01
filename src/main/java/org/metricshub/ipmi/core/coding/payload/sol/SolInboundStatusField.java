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
 * {@link SolInboundStatusField} is a transfer object for status sent by remote system to this application in {@link SolInboundMessage}.
 */
public class SolInboundStatusField {

    /**
     * Acknowledge state of {@link SolMessage} that this message is response for.
     */
    private final SolAckState ackState;

    /**
     * Set of statuses indicated by BMC in this message.
     */
    private final Set<SolStatus> statuses;

    /**
     * Creates new instance if {@link SolInboundStatusField} without Acknowledge data.
     *
     * @param statuses
     *          Set of SOL specific statuses for inbound message
     */
    public SolInboundStatusField(Set<SolStatus> statuses) {
        this.ackState = SolAckState.ACK;
        this.statuses = statuses;
    }

    /**
     * Creates new instance of {@link SolInboundStatusField} filled with given data.
     *
     * @param ackState
     *          Acknowledge state carried by this object
     * @param statuses
     *          Set of SOL specific statuses for inbound message
     */
    public SolInboundStatusField(SolAckState ackState, Set<SolStatus> statuses) {
        this.ackState = ackState;
        this.statuses = statuses;
    }

    /**
     * Creates new instance of {@link SolInboundStatusField} from raw byte.
     *
     * @param raw
     *          byte carrying information about SOL status
     */
    public SolInboundStatusField(byte raw) {
        this.ackState = SolAckState.extractFromByte(raw);
        this.statuses = extractStatusesFromByte(raw);
    }

    private Set<SolStatus> extractStatusesFromByte(byte raw) {
        Set<SolStatus> result = new HashSet<SolStatus>();

        for (SolStatus status : SolStatus.values()) {
            if (TypeConverter.isBitSetOnPosition(status.getStatusNumber(), raw)) {
                result.add(status);
            }
        }

        return result;
    }

    public Set<SolStatus> getStatuses() {
        return statuses;
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

        for (SolStatus status : statuses) {
            value = TypeConverter.setBitOnPosition(status.getStatusNumber(), value);
        }

        return value;
    }
}
