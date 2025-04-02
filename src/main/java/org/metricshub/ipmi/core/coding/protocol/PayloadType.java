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

/**
 * Types of IPMI packet payload
 */
public enum PayloadType {
    /**
     * IPMI packet
     */
    Ipmi(PayloadType.IPMI),
    /**
     * Serial over LAN packet
     */
    Sol(PayloadType.SOL),
    /**
     * OEM Explicit
     */
    Oem(PayloadType.OEM), RmcpOpenSessionRequest(
            PayloadType.RMCPOPENSESSIONREQUEST), RmcpOpenSessionResponse(
            PayloadType.RMCPOPENSESSIONRESPONSE),
    /**
     * RAKP Message 1
     */
    Rakp1(PayloadType.RAKP1),
    /**
     * RAKP Message 2
     */
    Rakp2(PayloadType.RAKP2),
    /**
     * RAKP Message 3
     */
    Rakp3(PayloadType.RAKP3),
    /**
     * RAKP Message 4
     */
    Rakp4(PayloadType.RAKP4), Oem0(PayloadType.OEM0), Oem1(PayloadType.OEM1), Oem2(
            PayloadType.OEM2), Oem3(PayloadType.OEM3), Oem4(PayloadType.OEM4), Oem5(
            PayloadType.OEM5), Oem6(PayloadType.OEM6), Oem7(PayloadType.OEM7), ;
    private static final int IPMI = 0;
    private static final int SOL = 1;
    private static final int OEM = 2;
    private static final int RMCPOPENSESSIONREQUEST = 16;
    private static final int RMCPOPENSESSIONRESPONSE = 17;
    private static final int RAKP1 = 18;
    private static final int RAKP2 = 19;
    private static final int RAKP3 = 20;
    private static final int RAKP4 = 21;
    private static final int OEM0 = 32;
    private static final int OEM1 = 33;
    private static final int OEM2 = 34;
    private static final int OEM3 = 35;
    private static final int OEM4 = 36;
    private static final int OEM5 = 37;
    private static final int OEM6 = 38;
    private static final int OEM7 = 39;

    private int code;

    PayloadType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PayloadType parseInt(int value) {
        switch (value) {
        case IPMI:
            return Ipmi;
        case SOL:
            return Sol;
        case OEM:
            return Oem;
        case RMCPOPENSESSIONREQUEST:
            return RmcpOpenSessionRequest;
        case RMCPOPENSESSIONRESPONSE:
            return RmcpOpenSessionResponse;
        case RAKP1:
            return Rakp1;
        case RAKP2:
            return Rakp2;
        case RAKP3:
            return Rakp3;
        case RAKP4:
            return Rakp4;
        case OEM0:
            return Oem0;
        case OEM1:
            return Oem1;
        case OEM2:
            return Oem2;
        case OEM3:
            return Oem3;
        case OEM4:
            return Oem4;
        case OEM5:
            return Oem5;
        case OEM6:
            return Oem6;
        case OEM7:
            return Oem7;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
