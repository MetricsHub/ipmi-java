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

/**
 * Enumeration of possible acknowledge states for SOL messages.
 */
public enum SolAckState {

    /**
     * Message was acknowledged.
     */
    ACK,

    /**
     * Message was not acknowledged.
     */
    NACK;

    private static final int ACK_BIT_NUMBER = 6;

    /**
     * Extracts {@link SolAckState} from given byte.
     *
     * @param value
     *          byte with encoded {@link SolAckState}
     * @return {@link SolAckState} extracted from byte
     */
    public static SolAckState extractFromByte(byte value) {
        return TypeConverter.isBitSetOnPosition(ACK_BIT_NUMBER, value) ? SolAckState.NACK : SolAckState.ACK;
    }

    /**
     * Encode information about this {@link SolAckState} in given byte, returning updated byte.
     *
     * @param value
     *          current byte value
     * @return updated byte value
     */
    public byte encodeInByte(final byte value) {
        byte updatedValue = value;

        if (this == SolAckState.NACK) {
            updatedValue = TypeConverter.setBitOnPosition(ACK_BIT_NUMBER, value);
        }

        return updatedValue;
    }
}
