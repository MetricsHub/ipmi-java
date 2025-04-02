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

import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.security.ConfidentialityAlgorithm;

/**
 * Wrapper class for IPMI message
 */
public abstract class IpmiMessage {
    private AuthenticationType authenticationType;

    private int sessionSequenceNumber;
    
    private int sessionID;

    private byte[] authCode;

    private IpmiPayload payload;
    
    private int payloadLength;
    
    /**
     * Confidentiality Algorithm used for encryption and decryption.
     */
    private ConfidentialityAlgorithm confidentialityAlgorithm;

    public void setAuthenticationType(AuthenticationType authenticationType) {
        this.authenticationType = authenticationType;
    }

    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public void setSessionSequenceNumber(int sessionSequenceNumber) {
        this.sessionSequenceNumber = sessionSequenceNumber;
    }

    public int getSessionSequenceNumber() {
        return sessionSequenceNumber;
    }

    public void setSessionID(int sessionID) {
        this.sessionID = sessionID;
    }

    public int getSessionID() {
        return sessionID;
    }

    public void setAuthCode(byte[] authCode) {
        this.authCode = authCode;
    }

    public byte[] getAuthCode() {
        return authCode;
    }

    /**
     * Sets {@link #payload} and {@link #payloadLength}
     * @param payload
     */
    public void setPayload(IpmiPayload payload) {
        setPayloadLength(payload.getPayloadLength());
        this.payload = payload;
    }

    public IpmiPayload getPayload() {
        return payload;
    }

    public void setPayloadLength(int payloadLength) {
        this.payloadLength = payloadLength;
    }

    /**
     * @return Length of the UNENCRYPTED payload.
     */
    public int getPayloadLength() {
        return payloadLength;
    }

    public void setConfidentialityAlgorithm(ConfidentialityAlgorithm confidentialityAlgorithm) {
        this.confidentialityAlgorithm = confidentialityAlgorithm;
    }

    public ConfidentialityAlgorithm getConfidentialityAlgorithm() {
        return confidentialityAlgorithm;
    }

}
