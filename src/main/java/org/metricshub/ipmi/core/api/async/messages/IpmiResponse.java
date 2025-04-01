package org.metricshub.ipmi.core.api.async.messages;

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

import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.async.IpmiResponseListener;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

/**
 * Interface for response messages delivered to {@link IpmiResponseListener}s
 */
public abstract class IpmiResponse {
    private int tag;
    private ConnectionHandle handle;

    /**
     * {@link ConnectionHandle} to the message that was an origin of the
     * response Handle contains only the id of the connection, not the
     * {@link CipherSuite} and {@link PrivilegeLevel} used in that connection.
     */
    public ConnectionHandle getHandle() {
        return handle;
    }

    /**
     * Tag of the message that is associated with the {@link IpmiResponse}
     */
    public int getTag() {
        return tag;
    }

    public IpmiResponse(int tag, ConnectionHandle handle) {
        this.tag = tag;
        this.handle = handle;
    }
}
