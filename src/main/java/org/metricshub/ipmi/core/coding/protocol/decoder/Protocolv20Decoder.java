package org.metricshub.ipmi.core.coding.protocol.decoder;

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
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.ConfidentialityNone;
import org.metricshub.ipmi.core.common.TypeConverter;


import java.security.InvalidKeyException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes IPMI v2.0 session header and retrieves encrypted payload.
 */
public class Protocolv20Decoder extends ProtocolDecoder {

    private static Logger logger = LoggerFactory.getLogger(Protocolv20Decoder.class);
    
    private CipherSuite cipherSuite;

    /**
     * Initiates IPMI v2.0 packet decoder.
     *
     * @param cipherSuite
     *            - {@link CipherSuite} that will be used to decode the message
     */
    public Protocolv20Decoder(CipherSuite cipherSuite) {
        super();
        this.cipherSuite = cipherSuite;
    }

    /**
     * Decodes IPMI v2.0 message fields.
     *
     * @param rmcpMessage
     *            - RMCP message to decode.
     * @return decoded message
     * @see Ipmiv20Message
     * @throws IllegalArgumentException
     *             when delivered RMCP message does not contain encapsulated
     *             IPMI message or when AuthCode field is incorrect (integrity
     *             check fails).
     * @throws InvalidKeyException
     *             - when initiation of the integrity algorithm fails
     */
    @Override
    public IpmiMessage decode(RmcpMessage rmcpMessage) throws InvalidKeyException {
        Ipmiv20Message message = new Ipmiv20Message(
                cipherSuite.getConfidentialityAlgorithm());

        byte[] raw = rmcpMessage.getData();

        message.setAuthenticationType(decodeAuthenticationType(raw[0]));

        message.setPayloadEncrypted(decodeEncryption(raw[1]));

        message.setPayloadAuthenticated(decodeAuthentication(raw[1]));

        message.setPayloadType(decodePayloadType(raw[1]));

        int offset = 2;

        if (message.getPayloadType() == PayloadType.Oem) {
            message.setOemIANA(decodeOEMIANA(raw));
            offset += 4;

            message.setOemPayloadID(decodeOEMPayloadId(raw, offset));
            offset += 2;
        }

        message.setSessionID(decodeSessionID(raw, offset));
        offset += 4;

        message.setSessionSequenceNumber(decodeSessionSequenceNumber(raw,
                offset));
        offset += 4;

        int payloadLength = decodePayloadLength(raw, offset);
        offset += 2;

        if (message.isPayloadEncrypted()) {
            message.setPayload(decodePayload(raw, offset, payloadLength,
                    message.getConfidentialityAlgorithm(), message.getPayloadType()));
        } else {
            message.setPayload(decodePayload(raw, offset, payloadLength,
                    new ConfidentialityNone(), message.getPayloadType()));
        }

        offset += payloadLength;

        if (message.getAuthenticationType() != AuthenticationType.None
                && !(message.getAuthenticationType() == AuthenticationType.RMCPPlus && !message
                        .isPayloadAuthenticated())
                && message.getSessionID() != 0) {
            offset = skipIntegrityPAD(raw, offset);
            message.setAuthCode(decodeAuthCode(raw, offset));
            if (!validateAuthCode(raw, offset)) {
                logger.warn("Integrity check failed");
            }
        }

        return message;
    }

    /**
     * Decodes first bit of Payload Type.
     *
     * @param payloadType
     * @return True if payload is encrypted, false otherwise.
     */
    private boolean decodeEncryption(byte payloadType) {
        return (payloadType & TypeConverter.intToByte(0x80)) != 0;
    }

    /**
     * Decodes second bit of Payload Type.
     *
     * @param payloadType
     * @return True if payload is authenticated, false otherwise.
     */
    public boolean decodeAuthentication(byte payloadType) {
        return (payloadType & TypeConverter.intToByte(0x40)) != 0;
    }

    public static PayloadType decodePayloadType(byte payloadType) {
        return PayloadType.parseInt(TypeConverter.intToByte(payloadType
                & TypeConverter.intToByte(0x3f)));
    }

    /**
     * Decodes OEM IANA.
     *
     * @param rawMessage
     *            - Byte array holding whole message data.
     * @return OEM IANA number.
     */
    private int decodeOEMIANA(byte[] rawMessage) {
        byte[] oemIANA = new byte[4];

        System.arraycopy(rawMessage, 3, oemIANA, 0, 3);
        oemIANA[3] = 0;

        return TypeConverter.littleEndianByteArrayToInt(oemIANA);
    }

    /**
     * Decodes OEM payload ID. To implement manufacturer-specific OEM Payload ID
     * decoding, override this function.
     *
     * @param rawMessage
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to OEM payload ID in header.
     * @return Decoded OEM payload ID.
     */
    protected Object decodeOEMPayloadId(byte[] rawMessage, int offset) {
        byte[] oemPayload = new byte[2];

        System.arraycopy(rawMessage, offset, oemPayload, 0, 2);

        return oemPayload;
    }

    @Override
    protected int decodePayloadLength(byte[] rawData, int offset) {
        byte[] payloadLength = new byte[4];
        System.arraycopy(rawData, offset, payloadLength, 0, 2);
        payloadLength[2] = 0;
        payloadLength[3] = 0;

        return TypeConverter.littleEndianByteArrayToInt(payloadLength);
    }

    /**
     * Skips the integrity pad and pad length fields.
     *
     * @param rawMessage
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to integrity pad.
     * @return Offset to Auth Code
     * @throws IndexOutOfBoundsException
     *             when message is corrupted and pad length does not appear
     *             after integrity pad or length is incorrect.
     */
    private int skipIntegrityPAD(final byte[] rawMessage, final int offset) {
        int skip = 0;
        while (TypeConverter.byteToInt(rawMessage[offset + skip]) == 0xff) {
            ++skip;
        }
        int length = TypeConverter.byteToInt(rawMessage[offset + skip]);
        if (length != skip) {
            throw new IndexOutOfBoundsException("Message is corrupted.");
        }

        int currentOffset = offset + skip + 2; // skip pad length and next header fields
        if (currentOffset >= rawMessage.length) {
            throw new IndexOutOfBoundsException("Message is corrupted.");
        }
        return currentOffset;
    }

    /**
     * Decodes the Auth Code.
     *
     * @param rawMessage
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to auth code.
     * @return Auth Code
     * @throws IndexOutOfBoundsException
     *             when message is corrupted and pad length does not appear
     *             after integrity pad or length is incorrect.
     */
    private byte[] decodeAuthCode(byte[] rawMessage, int offset) {
        byte[] authCode = new byte[rawMessage.length - offset];
        System.arraycopy(rawMessage, offset, authCode, 0, authCode.length);
        return authCode;
    }

    /**
     * Checks if Auth Code of the received message is valid
     *
     * @param rawMessage
     *            - received message
     * @param offset
     *            - offset to the AuthCode field in the message
     * @return True if AuthCode is correct, false otherwise.
     * @throws InvalidKeyException
     *             - when initiation of the integrity algorithm fails
     */
    private boolean validateAuthCode(byte[] rawMessage, int offset) {
        byte[] base = new byte[offset];

        System.arraycopy(rawMessage, 0, base, 0, offset);

        byte[] authCode = null;

        if (rawMessage.length > offset) {
            authCode = new byte[rawMessage.length - offset];
            System.arraycopy(rawMessage, offset, authCode, 0, authCode.length);
        }

        return Arrays.equals(authCode, cipherSuite.getIntegrityAlgorithm()
                .generateAuthCode(base));
    }

    /**
     * Decodes session ID.
     *
     * @param message
     *            - message to get session ID from
     * @return Session ID.
     */
    public static int decodeSessionID(RmcpMessage message) {
        int offset = 2;
        if (decodePayloadType(message.getData()[1]) == PayloadType.Oem) {
            offset += 6;
        }
        return decodeSessionID(message.getData(), offset);
    }
}
