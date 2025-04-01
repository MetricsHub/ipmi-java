package org.metricshub.ipmi.core.coding.security;

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

import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Interface for Confidentiality Algorithms. All classes extending this one must
 * implement constructor(byte[]).
 */
public abstract class ConfidentialityAlgorithm {
    protected byte[] sik;

    /**
     * Initializes Confidentiality Algorithm
     *
     * @param sik
     *            - Session Integrity Key calculated during the opening of the
     *            session or user password if 'one-key' logins are enabled.
     * @param authenticationAlgorithm
     *           - Algorithm used for authentication.
     * @throws InvalidKeyException
     *             - when initiation of the algorithm fails
     * @throws NoSuchAlgorithmException
     *             - when initiation of the algorithm fails
     * @throws NoSuchPaddingException
     *             - when initiation of the algorithm fails
     */
    public void initialize(byte[] sik, AuthenticationAlgorithm authenticationAlgorithm) throws InvalidKeyException,
            NoSuchAlgorithmException, NoSuchPaddingException {
        this.sik = sik;
    }

    /**
     * Returns the algorithm's ID.
     */
    public abstract byte getCode();

    /**
     * Encrypts the data.
     *
     * @param data
     *            - payload to be encrypted
     * @return encrypted data encapsulated in COnfidentiality Header and
     *         Trailer.
     * @throws InvalidKeyException
     *             - when initiation of the algorithm fails
     */
    public abstract byte[] encrypt(byte[] data) throws InvalidKeyException;

    /**
     * Decrypts the data.
     *
     * @param data
     *            - encrypted data encapsulated in COnfidentiality Header and
     *            Trailer.
     * @return decrypted data.
     * @throws IllegalArgumentException
     *             - when initiation of the algorithm fails
     */
    public abstract byte[] decrypt(byte[] data);

    /**
     * Calculates size of the confidentiality header and trailer specific for
     * the algorithm.
     *
     * @param payloadSize
     *            - size of the data that will be encrypted
     */
    public abstract int getConfidentialityOverheadSize(int payloadSize);
}
