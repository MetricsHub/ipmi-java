package org.metricshub.ipmi.core.coding.protocol;

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

import java.security.InvalidKeyException;

import org.metricshub.ipmi.core.coding.protocol.encoder.IpmiEncoder;
import org.metricshub.ipmi.core.coding.security.ConfidentialityAlgorithm;

/**
 * Wrapper class for IPMI v2.0 message
 */
public class Ipmiv20Message extends IpmiMessage {
    private boolean payloadEncrypted;

    private boolean payloadAuthenticated;

    private PayloadType payloadType;

    private int oemIANA;

    private Object oemPayloadID;

    public void setPayloadEncrypted(boolean payloadEncrypted) {
        this.payloadEncrypted = payloadEncrypted;
    }

    public boolean isPayloadEncrypted() {
        return payloadEncrypted;
    }

    public void setPayloadAuthenticated(boolean payloadAuthenticated) {
        this.payloadAuthenticated = payloadAuthenticated;
    }

    public boolean isPayloadAuthenticated() {
        return payloadAuthenticated;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public void setOemIANA(int oemIANA) {
        this.oemIANA = oemIANA;
    }

    public int getOemIANA() {
        return oemIANA;
    }

    public void setOemPayloadID(Object oemPayloadID) {
        this.oemPayloadID = oemPayloadID;
    }

    public Object getOemPayloadID() {
        return oemPayloadID;
    }

    public Ipmiv20Message(ConfidentialityAlgorithm confidentialityAlgorithm) {
        setConfidentialityAlgorithm(confidentialityAlgorithm);
    }

    /**
     * Gets base for integrity algorithm calculations. If used for the message
     * being created, does not contain AuthCode field so amount of Integrity Pad
     * bytes cannot be calculated. Therefore integrity algorithms using this
     * base must add proper amount of the Integrity Pad bytes and modify the Pad
     * Length byte.
     *
     * @param encoder
     *            - {@link IpmiEncoder} to be used to convert message to byte
     *            array format. Must be able to handle null authCode field with
     *            {@link #isPayloadAuthenticated()} == true since this method is
     *            used in its generation.
     * @return base for integrity algorithm calculations
     * @throws InvalidKeyException
     *             - when initiation of the confidentiality algorithm fails
     */
    public byte[] getIntegrityAlgorithmBase(IpmiEncoder encoder) throws InvalidKeyException {
        return encoder.encode(this);
    }
}
