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

import java.security.InvalidKeyException;

import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;

/**
 * Decodes IPMI session header and retrieves encrypted payload.
 */
public interface IpmiDecoder {
    
    /**
     * Decodes IPMI message.
     * @param rmcpMessage
     * - RMCP message to decode.
     * @see IpmiMessage
     * @return Decoded IPMI message
     * @throws IllegalArgumentException
     * when delivered RMCP message does not contain encapsulated IPMI message.
     * @throws InvalidKeyException 
     *             - when initiation of the integrity algorithm fails
     */
    IpmiMessage decode(RmcpMessage rmcpMessage) throws InvalidKeyException;
}
