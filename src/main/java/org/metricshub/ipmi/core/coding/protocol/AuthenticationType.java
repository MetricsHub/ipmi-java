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
 * Available types of authentication. For IPMI v2.0 format RMCPPlus should be
 * used.
 */
public enum AuthenticationType {
    None(AuthenticationType.NONE), Md2(AuthenticationType.MD2), Md5(
            AuthenticationType.MD5), Simple(AuthenticationType.SIMPLE), Oem(
            AuthenticationType.OEM), RMCPPlus(AuthenticationType.RMCPPLUS), ;

    private static final int NONE = 0;
    private static final int MD2 = 1;
    private static final int MD5 = 2;
    private static final int SIMPLE = 4;
    private static final int OEM = 5;
    private static final int RMCPPLUS = 6;

    private int code;

    AuthenticationType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AuthenticationType parseInt(int value) {
        switch (value) {
        case NONE:
            return None;
        case MD2:
            return Md2;
        case MD5:
            return Md5;
        case SIMPLE:
            return Simple;
        case OEM:
            return Oem;
        case RMCPPLUS:
            return RMCPPlus;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
