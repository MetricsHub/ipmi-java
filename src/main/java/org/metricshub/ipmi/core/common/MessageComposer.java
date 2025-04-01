package org.metricshub.ipmi.core.common;

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
 * Class used for composing byte messages aout from smaller byte subarrays.
 */
public class MessageComposer {

    private final byte[] message;
    private int pointer = 0;

    public static MessageComposer get(int messageSize) {
        return new MessageComposer(messageSize);
    }

    private MessageComposer(int messageSize) {
        if (messageSize < 0) {
            throw new IllegalArgumentException("Message size cannot be negative");
        }

        this.message = new byte[messageSize];
    }

    /**
     * Append single-byte field to the message.
     *
     * @param fieldData
     *          single byte containing data that should be appended
     */
    public MessageComposer appendField(byte fieldData) {
        message[pointer++] = fieldData;

        return this;
    }

    /**
     * Append byte arrach field to the message.
     *
     * @param fieldData
     *          byte array containing data that should be appended
     */
    public MessageComposer appendField(byte[] fieldData) {
        System.arraycopy(fieldData, 0, message, pointer, fieldData.length);

        pointer += fieldData.length;

        return this;
    }

    /**
     * Returns final message consisting all messages appended till now.
     *
     * @return composed message
     */
    public byte[] getMessage() {
        return message;
    }
}
