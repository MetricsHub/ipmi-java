package org.metricshub.ipmi.core.coding.commands.session;

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

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.ConfidentialityNone;
import org.metricshub.ipmi.core.coding.security.SecurityConstants;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * A wrapper for RMCP+ RAKP3 message and it's response - RAKP4 message.
 */
public class Rakp3 extends IpmiCommandCoder {

    /**
     * Status of the previous message.
     */
    private byte statusCode;

    /**
     * The Managed System's Session ID for this session. Must be as returned by
     * the Managed System in the Open Session Response message.
     */
    private int managedSystemSessionId;

    private Rakp1 rakp1;

    private Rakp1ResponseData rakp1ResponseData;

    public void setStatusCode(byte statusCode) {
        this.statusCode = statusCode;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public void setManagedSystemSessionId(int managedSystemSessionId) {
        this.managedSystemSessionId = managedSystemSessionId;
    }

    public int getManagedSystemSessionId() {
        return managedSystemSessionId;
    }

    /**
     * Initiates class for decoding. Sets IPMI version to
     * {@link IpmiVersion#V20} since RAKP1 is a RMCP+ command. Sets
     * Authentication Type to RMCP+.
     *
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     *            Only authentication algorithm is used at this point of
     *            creating a session.
     * @param rakp1
     *            - RAKP Message 1 sent earlier in the authentification process
     * @param rakp1ResponseData
     *            - RAKP Message 2 received earlier in the authentification
     *            process
     *
     */
    public Rakp3(CipherSuite cipherSuite, Rakp1 rakp1,
            Rakp1ResponseData rakp1ResponseData) {
        super(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus);
        this.rakp1 = rakp1;
        this.rakp1ResponseData = rakp1ResponseData;
        setCipherSuite(new CipherSuite((byte) 0, cipherSuite
                .getAuthenticationAlgorithm().getCode(), (byte) 0, (byte) 0));
    }

    /**
     * Initiates class for encoding and decoding. Sets IPMI version to
     * {@link IpmiVersion#V20} since RAKP1 is a RMCP+ command. Sets
     * Authentication Type to RMCP+.
     *
     * @param statusCode
     *            - Status of the previous message.
     * @param managedSystemSessionId
     *            - The Managed System�s Session ID for this session. Must be as
     *            returned by the Managed System in the Open Session Response
     *            message.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     *            Only authentication algorithm is used at this point of
     *            creating a session.
     * @param rakp1
     *            - RAKP Message 1 sent earlier in the authentification process
     * @param rakp1ResponseData
     *            - RAKP Message 2 received earlier in the authentification
     *            process
     */
    public Rakp3(byte statusCode, int managedSystemSessionId,
            CipherSuite cipherSuite, Rakp1 rakp1,
            Rakp1ResponseData rakp1ResponseData) {
        super(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus);
        setStatusCode(statusCode);
        setManagedSystemSessionId(managedSystemSessionId);
        this.rakp1 = rakp1;
        this.rakp1ResponseData = rakp1ResponseData;
        setCipherSuite(new CipherSuite((byte) 0, cipherSuite
                .getAuthenticationAlgorithm().getCode(), (byte) 0, (byte) 0));
    }

    @Override
    public IpmiMessage encodePayload(int messageSequenceNumber, int sessionSequenceNumber, int sessionId)
            throws NoSuchAlgorithmException, InvalidKeyException {

        if (sessionId != 0) {
            throw new IllegalArgumentException("Session ID must be 0");
        }

        Ipmiv20Message message = new Ipmiv20Message(new ConfidentialityNone());

        message.setPayloadType(PayloadType.Rakp3);
        message.setSessionID(0);
        message.setSessionSequenceNumber(0);
        message.setAuthenticationType(getAuthenticationType());
        message.setPayloadAuthenticated(getCipherSuite()
                .getIntegrityAlgorithm().getCode() != SecurityConstants.IA_NONE);
        message.setPayloadEncrypted(false);

        message.setPayload(preparePayload(messageSequenceNumber));

        message.setAuthCode(getCipherSuite()
                .getIntegrityAlgorithm()
                .generateAuthCode(
                        message.getIntegrityAlgorithmBase(new Protocolv20Encoder())));

        return message;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] payload = new byte[8];

        payload[0] = TypeConverter.intToByte(sequenceNumber); // message
                                                                    // tag

        payload[1] = getStatusCode(); // last message status code

        payload[2] = 0; // reserved
        payload[3] = 0; // reserved

        byte[] manSesId = TypeConverter
                .intToLittleEndianByteArray(getManagedSystemSessionId());

        System.arraycopy(manSesId, 0, payload, 4, 4); // managed system session
                                                        // id

        byte[] exchangeAuthCode = getCipherSuite().getAuthenticationAlgorithm()
                .getKeyExchangeAuthenticationCode(
                        prepareKeyExchangeAuthenticationCodeBase(rakp1,
                                rakp1ResponseData), rakp1.getPassword());

        byte[] result = null;

        if (exchangeAuthCode != null) {
            result = new byte[8 + exchangeAuthCode.length];
            System.arraycopy(exchangeAuthCode, 0, result, 8,
                    exchangeAuthCode.length);
        } else {
            result = new byte[8];
        }

        System.arraycopy(payload, 0, result, 0, 8);

        return new PlainMessage(result);
    }

    /**
     * @return byte array holding prepared base for calculating
     *         KeyExchangeAuthenticationCode for RAKP Message 3
     */
    private byte[] prepareKeyExchangeAuthenticationCodeBase(Rakp1 rakp1,
            Rakp1ResponseData responseData) {
        int length = 22;
        if (rakp1.getUsername() != null) {
            length += rakp1.getUsername().length();
        }
        byte[] keac = new byte[length];

        System.arraycopy(responseData.getManagedSystemRandomNumber(), 0, keac,
                0, 16);

        System.arraycopy(TypeConverter.intToLittleEndianByteArray(responseData
                .getRemoteConsoleSessionId()), 0, keac, 16, 4);

        keac[20] = TypeConverter.intToByte(encodePrivilegeLevel(rakp1
                .getRequestedMaximumPrivilegeLevel()) | 0x10);

        if (rakp1.getUsername() != null) {
            keac[21] = TypeConverter.intToByte(rakp1.getUsername().length());
            if (rakp1.getUsername().length() > 0) {
                System.arraycopy(rakp1.getUsername().getBytes(), 0, keac, 22,
                        rakp1.getUsername().length());
            }
        } else {
            keac[21] = 0;
        }

        return keac;
    }

    @Override
    public byte getCommandCode() {
        return 0;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return null;
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, InvalidKeyException, NoSuchAlgorithmException {

        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException("This is not RAKP 4 message!");
        }

        byte[] payload = message.getPayload().getPayloadData();

        Rakp3ResponseData data = new Rakp3ResponseData();

        data.setMessageTag(payload[0]);

        data.setStatusCode(payload[1]);

        if (payload[1] != 0) {
            throw new IPMIException(CompletionCode.parseInt(TypeConverter
                    .byteToInt(payload[1])));
        }

        if (payload.length < 8) {
            throw new IllegalArgumentException("Invalid payload length");
        }

        byte[] buffer = new byte[4];

        System.arraycopy(payload, 4, buffer, 0, 4);

        data.setConsoleSessionId(TypeConverter
                .littleEndianByteArrayToInt(buffer));

        byte[] integrityCheck = null;

        if (payload.length > 8) {
            integrityCheck = new byte[getCipherSuite()
                    .getAuthenticationAlgorithm().getIntegrityCheckBaseLength()];
            System.arraycopy(payload, 8, integrityCheck, 0, getCipherSuite()
                    .getAuthenticationAlgorithm().getIntegrityCheckBaseLength());
        }

        if (!getCipherSuite().getAuthenticationAlgorithm().doIntegrityCheck(
                prepareIntegrityCheckBase(rakp1, rakp1ResponseData),
                integrityCheck, rakp1.calculateSik(rakp1ResponseData))) {
            throw new IllegalArgumentException("Integrity check failed");
        }

        return data;
    }

    /**
     * @return byte array holding prepared base for calculating Integrity Check
     */
    private byte[] prepareIntegrityCheckBase(Rakp1 rakp1,
            Rakp1ResponseData responseData) {
        byte[] icb = new byte[36];

        System.arraycopy(rakp1.getConsoleRandomNumber(), 0, icb, 0, 16);

        System.arraycopy(TypeConverter.intToLittleEndianByteArray(rakp1
                .getManagedSystemSessionId()), 0, icb, 16, 4);

        System.arraycopy(responseData.getManagedSystemGuid(), 0, icb, 20, 16);

        return icb;
    }

    @Override
    public boolean isCommandResponse(IpmiMessage message) {
        return message instanceof Ipmiv20Message && ((Ipmiv20Message) message).getPayloadType() == PayloadType.Rakp4;
    }

}
