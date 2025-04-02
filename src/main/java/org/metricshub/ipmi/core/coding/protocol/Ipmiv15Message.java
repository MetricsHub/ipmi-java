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

import org.metricshub.ipmi.core.coding.security.ConfidentialityNone;

/**
 * Wrapper for IPMI v.1.5 message
 */
public class Ipmiv15Message extends IpmiMessage {

    public Ipmiv15Message() {
        setConfidentialityAlgorithm(new ConfidentialityNone());
    }

    @Override
    public void setAuthenticationType(AuthenticationType authenticationType) {
        if (authenticationType == AuthenticationType.RMCPPlus) {
            throw new IllegalArgumentException(
                    "IPMIv1.5 does not support RMCP+");
        }
        super.setAuthenticationType(authenticationType);
    }
}
