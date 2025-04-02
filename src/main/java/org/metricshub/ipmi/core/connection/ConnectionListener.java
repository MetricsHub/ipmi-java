package org.metricshub.ipmi.core.connection;

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

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;

/**
 * Interface for the {@link Connection} listener.
 */
public interface ConnectionListener {

    /**
     * Notifies the {@link ConnectionListener}s of the one of the following
     * events: <ul><li>response to the request tagged with id arrived</li> <li>request
     * tagged with id timed out</li></ul>
     *
     * @param responseData
     *            - {@link ResponseData} specific for the request if it was
     *            completed successfully, null if it timed out or an error
     *            occured during decoding.
     * @param handle
     *            - the id of the connection that received the message
     * @param id
     *            - tag of the request-response pair
     * @param exception
     *            - null if everything went correctly or timeout occured,
     *            contains exception that occured during decoding if it failed.
     */
    void processResponse(ResponseData responseData, int handle, int id,
                         Exception exception);

    void processRequest(IpmiPayload payload);
}
