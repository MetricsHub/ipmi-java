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

import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.ConfidentialityAlgorithm;

/**
 * Decodes IPMI session header and retrieves encrypted payload. The payload must
 * not be encapsulated in IPMI LAN message. USed for Open session and RAKP
 * messages.
 */
public class PlainCommandv20Decoder extends Protocolv20Decoder {

    public PlainCommandv20Decoder(CipherSuite cipherSuite) {
        super(cipherSuite);
    }

    /**
     *
     * @return Payload decoded into {@link PlainMessage}.
     */
    @Override
    protected IpmiPayload decodePayload(byte[] rawData, int offset, int length,
            ConfidentialityAlgorithm confidentialityAlgorithm, PayloadType payloadType) {
        byte[] payload = new byte[length];

        System.arraycopy(rawData, offset, payload, 0, length);

        return new PlainMessage(confidentialityAlgorithm.decrypt(payload));
    }
}
