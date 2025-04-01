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

/**
 * Implementation of {@link SolMessage} for BMC -> Remote Console message.
 */
public class SolInboundMessage extends SolMessage {

    /**
     * Status field in {@link SolMessage} BMC -> Remote Console payload.
     */
    private final SolInboundStatusField statusField;

    public SolInboundMessage(byte sequenceNumber, byte ackNackSequenceNumber, byte acceptedCharacterCount, SolInboundStatusField statusField) {
        super(sequenceNumber, ackNackSequenceNumber, acceptedCharacterCount, statusField.convertToByte());
        this.statusField = statusField;
    }

    public SolInboundMessage(byte[] rawData) {
        super(rawData[0], rawData[1], rawData[2], rawData[3]);

        if (rawData.length > PAYLOAD_HEADER_LENGTH) {
            byte[] characterData = new byte[rawData.length - PAYLOAD_HEADER_LENGTH];
            System.arraycopy(rawData, PAYLOAD_HEADER_LENGTH, characterData, 0, characterData.length);
            setData(characterData);
        }

        this.statusField = new SolInboundStatusField(rawData[3]);
    }

    public SolInboundStatusField getStatusField() {
        return statusField;
    }
}
