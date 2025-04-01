package org.metricshub.ipmi.core.transport;

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

import java.net.InetAddress;

/**
 * Container for UDP message.
 */
public class UdpMessage {
    /**
     * Target port when sending message. Sender port when receiving
     * message.
     */
    private int port;

    /**
     * Target address when sending message. Sender address when receiving
     * message.
     */
    private InetAddress address;

    private byte[] message;

    /**
     * Target port when sending message. Sender port when receiving
     * message.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Target port when sending message. Sender port when receiving
     * message.
     */
    public int getPort() {
        return port;
    }

    /**
     * Target address when sending message. Sender address when receiving
     * message.
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Target address when sending message. Sender address when receiving
     * message.
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public byte[] getMessage() {
        return message;
    }

    public void setMessage(byte[] message) {
        this.message = message;
    }
}
