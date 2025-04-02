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

/**
 * Represents IPMI payload fully specified by user - contains wrapped byte array
 * which is returned as a payload via {@link #getPayloadData()}. Used for
 * OpenSession and RAKP messages.
 */
public class PlainMessage extends IpmiPayload {

    @Override
    public byte[] getPayloadData() {
        return getData();
    }

    @Override
    public int getPayloadLength() {
        return getData().length;
    }

    /**
     * Creates IPMI payload.
     * @param data
     * - byte array containing payload for IPMI message.
     */
    public PlainMessage(byte[] data) {
        setData(data);
    }

    @Override
    public byte[] getIpmiCommandData() {
        return getData();
    }

}
