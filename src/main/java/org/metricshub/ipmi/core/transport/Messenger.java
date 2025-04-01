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

import java.io.IOException;

/**
 * Low level connection handler.
 * 
 */
public interface Messenger {
    /**
     * Sends {@link UdpMessage}.
     *
     * @param message
     *            - {@link UdpMessage} to send.
     * @throws IOException
     *             when sending of the message fails
     */
    void send(UdpMessage message) throws IOException;

    /**
     * Registers listener in the {@link Messenger} so it will be notified via
     * {@link UdpListener#notifyMessage(UdpMessage)} when new message arrives.
     *
     * @param listener
     *            - {@link UdpListener} to register.
     */
    void register(UdpListener listener);

    /**
     * Unregisters listener from {@link Messenger} so it no longer will be
     * notified.
     *
     * @param listener
     *            - {@link UdpListener} to unregister
     */
    void unregister(UdpListener listener);

    /**
     * Closes the connection.
     */
    void closeConnection();
}
