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
 * Implementation of {@link SolMessage} for Remote Console -> BMC message.
 */
public class SolOutboundMessage extends SolMessage {

    /**
     * Operation field in {@link SolMessage} Remote Console -> BMC payload.
     */
    private final SolOutboundOperationField operationField;

    public SolOutboundMessage(byte sequenceNumber, byte ackNackSequenceNumber, byte acceptedCharacterCount, SolOutboundOperationField operationField) {
        super(sequenceNumber, ackNackSequenceNumber, acceptedCharacterCount, operationField.convertToByte());

        this.operationField = operationField;
    }

    public SolOutboundOperationField getOperationField() {
        return operationField;
    }

}
