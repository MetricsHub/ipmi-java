package org.metricshub.ipmi.core.coding.protocol.encoder;

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

import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Adds IPMI session header to encrypted payload. Should be used to encode
 * regular RMCP+ messages (excluding Open Session and RAKP Messages)
 */
public abstract class ProtocolEncoder implements IpmiEncoder {

    protected byte encodeAuthenticationType(
            AuthenticationType authenticationType) {
        return TypeConverter.intToByte(authenticationType.getCode());
    }

    /**
     * Converts integer value into byte array using little endian convention and
     * inserts it into message at given offset.
     *
     * @param value
     * @param message
     *            - IPMI message being created
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     */
    protected void encodeInt(int value, byte[] message, int offset) {
        byte[] array = TypeConverter.intToLittleEndianByteArray(value);

        if (array.length + offset > message.length) {
            throw new IndexOutOfBoundsException("Message is too short");
        }

        System.arraycopy(array, 0, message, offset, array.length);
    }

    /**
     * Encodes session sequence number and inserts it into message at given
     * offset.
     *
     * @param value
     * @param message
     *            - IPMI message being created
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     */
    protected void encodeSessionSequenceNumber(int value, byte[] message, int offset) {
        encodeInt(value, message, offset);
    }

    /**
     * Encodes session id and inserts it into message at given offset.
     *
     * @param value
     * @param message
     *            - IPMI message being created
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     */
    protected void encodeSessionId(int value, byte[] message, int offset) {
        encodeInt(value, message, offset);
    }

    /**
     * Encodes payload length and inserts it into message at given offset.
     *
     * @param value
     * @param message
     *            - IPMI message being created
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     */
    protected abstract void encodePayloadLength(int value, byte[] message, int offset);

    /**
     * Encodes payload and inserts it into message at given offset. <br>
     * When payload == null it will not be inserted.
     *
     * @param payload
     *            - IPMI payload to encode.
     * @param message
     *            - IPMI message being created.
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     * @return offset + encoded message length
     */
    protected int encodePayload(byte[] payload, byte[] message, int offset) {
        if (payload == null) {
            return offset;
        }
        if (payload.length + offset > message.length) {
            throw new IndexOutOfBoundsException("Message is too short");
        }
        System.arraycopy(payload, 0, message, offset, payload.length);
        return offset + payload.length;
    }
}
