package org.metricshub.ipmi.core.coding.rmcp;

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
 * Types of RMCP messages.
 */
public enum RmcpClassOfMessage {
    /**
     * ASF ACK Class of Message
     */
    Ack(RmcpClassOfMessage.ACK), Asf(RmcpClassOfMessage.ASF),
    /**
     * OEM-defined Class of Message
     */
    Oem(RmcpClassOfMessage.OEM), Ipmi(RmcpClassOfMessage.IPMI), ;

    private static final int ACK = 134;
    private static final int ASF = 6;
    private static final int OEM = 8;
    private static final int IPMI = 7;

    private int code;

    RmcpClassOfMessage(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static RmcpClassOfMessage parseInt(int value) {
        switch (value) {
        case ACK:
            return Ack;
        case ASF:
            return Asf;
        case OEM:
            return Oem;
        case IPMI:
            return Ipmi;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
