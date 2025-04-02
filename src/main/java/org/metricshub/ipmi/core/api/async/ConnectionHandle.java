package org.metricshub.ipmi.core.api.async;

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

import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.connection.Connection;

import java.net.InetAddress;

/**
 * Handle to the {@link Connection}
 */
public class ConnectionHandle {
    private int handle;
    private CipherSuite cipherSuite;
    private PrivilegeLevel privilegeLevel;
    private InetAddress remoteAddress;
    private int remotePort;
    private String user;
    private String password;

    public ConnectionHandle(int handle, InetAddress remoteAddress, int remotePort) {
        this.handle = handle;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
    }

    public CipherSuite getCipherSuite() {
        return cipherSuite;
    }

    public void setCipherSuite(CipherSuite cipherSuite) {
        this.cipherSuite = cipherSuite;
    }

    public PrivilegeLevel getPrivilegeLevel() {
        return privilegeLevel;
    }

    public void setPrivilegeLevel(PrivilegeLevel privilegeLevel) {
        this.privilegeLevel = privilegeLevel;
    }

    public int getHandle() {
        return handle;
    }

    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ConnectionHandle{");
        sb.append("handle=").append(handle);
        sb.append(", cipherSuite=").append(cipherSuite);
        sb.append(", privilegeLevel=").append(privilegeLevel);
        sb.append(", remoteAddress=").append(remoteAddress);
        sb.append(", remotePort=").append(remotePort);
        sb.append(", user='").append(user).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
