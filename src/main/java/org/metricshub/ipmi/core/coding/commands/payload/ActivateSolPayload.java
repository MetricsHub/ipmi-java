package org.metricshub.ipmi.core.coding.commands.payload;

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

import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.SecurityConstants;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Concrete implementation of {@link ActivatePayload} command for {@link PayloadType#SOL}.
 */
public class ActivateSolPayload extends ActivatePayload {

	/**
	 * Instantiate a new {@link ActivateSolPayload}
	 * @param cipherSuite     Provides cipher suite (authentication, confidentiality
	 *                        and integrity algorithms used during the session).
	 * @param payloadInstance Paylaod instance as int
	 */
    public ActivateSolPayload(CipherSuite cipherSuite, int payloadInstance) {
        super(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus, payloadInstance);
    }

    @Override
    public PayloadType getPayloadType() {
        return PayloadType.Sol;
    }

    @Override
    protected byte[] prepareAuxilaryRequestData() {
        byte[] result = new byte[4];

        boolean isAuthenticationEnabled = getCipherSuite().getAuthenticationAlgorithm().getCode() != SecurityConstants.AA_RAKP_NONE;
        boolean isEncryptionEnabled = getCipherSuite().getConfidentialityAlgorithm().getCode() != SecurityConstants.CA_NONE;

        if (isEncryptionEnabled) {
            //encryption bit
            result[0] = TypeConverter.setBitOnPosition(7, result[0]);
        }

        if (isAuthenticationEnabled) {
            //authentication bit
            result[0] = TypeConverter.setBitOnPosition(6, result[0]);
        }

        /*The following settings determine what happens to serial alerts
        if IPMI over Serial and SOL are sharing the same baseboard serial controller.
        Bit 2 set and bit 3 reset mean, that serial/modem alerts are deferred while SOL active*/
        result[0] = TypeConverter.setBitOnPosition(2, result[0]);

        return result;
    }

    @Override
    protected ActivatePayloadResponseData createEmptyResponse() {
        return new ActivateSolPayloadResponseData();
    }
}
