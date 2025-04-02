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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * AES-CBC-128 confidentiality algorithm
 */
public class ConfidentialityAesCbc128 extends ConfidentialityAlgorithm {

	protected static final byte[] CONST2 = new byte[20];
	static {
		Arrays.fill(CONST2, (byte) 2);
	}

    private Cipher cipher;

    private SecretKeySpec cipherKey;

    @Override
    public byte getCode() {
        return SecurityConstants.CA_AES_CBC128;
    }

    @Override
    public void initialize(byte[] sik, AuthenticationAlgorithm authenticationAlgorithm) throws InvalidKeyException,
            NoSuchAlgorithmException, NoSuchPaddingException {
        super.initialize(sik, authenticationAlgorithm);

        final String algorithmName = authenticationAlgorithm.getAlgorithmName();
		SecretKeySpec k2 = new SecretKeySpec(sik, algorithmName);

        Mac mac = Mac.getInstance(algorithmName);
        mac.init(k2);

        byte[] ckey = mac.doFinal(CONST2);

        byte[] ciphKey = new byte[16];

        System.arraycopy(ckey, 0, ciphKey, 0, 16);

        cipherKey = new SecretKeySpec(ciphKey, "AES");

        cipher = Cipher.getInstance("AES/CBC/NoPadding");
    }

    @Override
    public byte[] encrypt(byte[] data) throws InvalidKeyException {
        int length = data.length + 17;
        int pad = 0;
        if (length % 16 != 0) {
            pad = 16 - length % 16;
        }
        length += pad;

        byte[] result = new byte[length - 16];

        cipher.init(Cipher.ENCRYPT_MODE, cipherKey);

        System.arraycopy(data, 0, result, 0, data.length);

        for (int i = 0; i < pad; ++i) {
            result[i + data.length] = TypeConverter.intToByte(i + 1);
        }

        result[length - 17] = TypeConverter.intToByte(pad);

        try {
            byte[] encrypted = cipher.doFinal(result);

            result = new byte[encrypted.length + 16];

            System.arraycopy(encrypted, 0, result, 16, encrypted.length); // encrypted
                                                                            // payload
            System.arraycopy(cipher.getIV(), 0, result, 0, 16); // Initialization
                                                                // vector

            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data) {

        byte[] decrypted = null;
        try {
            byte[] iv = new byte[16];

            System.arraycopy(data, 0, iv, 0, 16);

            byte[] encrypted = new byte[data.length - 16];

            System.arraycopy(data, 16, encrypted, 0, encrypted.length);

            cipher.init(Cipher.DECRYPT_MODE, cipherKey, new IvParameterSpec(iv));
            decrypted = cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalArgumentException("Decryption failed", e);
        }

        int pad = TypeConverter.byteToInt(decrypted[decrypted.length - 1]);

        byte[] result = new byte[decrypted.length - pad - 1];

        System.arraycopy(decrypted, 0, result, 0, result.length);

        return result;
    }

    @Override
    public int getConfidentialityOverheadSize(int payloadSize) {
        int size = 17;
        if ((size + payloadSize) % 16 != 0) {
            size += 16 - (size + payloadSize) % 16;
        }
        return size;
    }

}
