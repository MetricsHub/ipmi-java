package org.metricshub.ipmi.core.coding.payload;

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

import org.metricshub.ipmi.core.coding.security.ConfidentialityAlgorithm;

/**
 * Payload for IPMI messages
 */
public abstract class IpmiPayload {

    private byte[] data;

    private byte[] encryptedPayload;

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Returns encrypted payload encoded in byte array.
     *
     * Migth be null if payload was not yet encrypted.
     *
     * @see #encryptPayload(ConfidentialityAlgorithm)
     */
    public byte[] getEncryptedPayload() {
        return encryptedPayload;
    }

    /**
     * Returns unencrypted payload encoded in byte array (owner is responsible
     * for encryption).
     *
     * @return payload
     */
    public abstract byte[] getPayloadData();

    /**
     * Returns encoded but UNENCRYPTED payload length.
     */
    public abstract int getPayloadLength();

    /**
     * Returns IPMI command encapsulated in IPMI Payload.
     */
    public abstract byte[] getIpmiCommandData();

    /**
     * Encrypts {@link #getPayloadData()}.
     *
     * @param confidentialityAlgorithm
     *            {@link ConfidentialityAlgorithm} to be used to encrypt payload
     *            data.
     * @throws InvalidKeyException
     *             - when confidentiality algorithm fails.
     * @see IpmiPayload#getEncryptedPayload()
     */
    public void encryptPayload(ConfidentialityAlgorithm confidentialityAlgorithm)
            throws InvalidKeyException {
        encryptedPayload = confidentialityAlgorithm.encrypt(getPayloadData());
    }
}
