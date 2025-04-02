package org.metricshub.ipmi.core.api.sync;

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
import org.metricshub.ipmi.core.api.async.IpmiAsyncConnector;
import org.metricshub.ipmi.core.api.async.IpmiResponseListener;
import org.metricshub.ipmi.core.api.async.messages.IpmiError;
import org.metricshub.ipmi.core.api.async.messages.IpmiResponse;
import org.metricshub.ipmi.core.api.async.messages.IpmiResponseData;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.connection.Connection;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens to the {@link IpmiAsyncConnector} waiting for concrete message to
 * arrive. Must be registered via
 * {@link IpmiAsyncConnector#registerListener(IpmiResponseListener)} to receive
 * messages.
 */
public class MessageListener implements IpmiResponseListener {

    private ConnectionHandle handle;

    private int tag;

    private IpmiResponse response;

    /**
     * Messages that have proper connection handle but arrived before tag was
     * set - they need to be checked in case expected message arrived very early
     * between sending the request and starting waiting for answer (waiting
     * cannot be initialized before sending message since tag is not yet known
     * then)
     */
    private List<IpmiResponse> quickMessages;

    /**
     * Initiates the {@link MessageListener}
     *
     * @param handle
     *            - {@link ConnectionHandle} associated with the
     *            {@link Connection} {@link MessageListener} is expecting
     *            message from.
     */
    public MessageListener(ConnectionHandle handle) {
        quickMessages = new ArrayList<IpmiResponse>();
        this.handle = handle;
        tag = -1;
        response = null;
    }

    /**
     * Blocks the invoking thread until deserved message arrives (tag and handle
     * as specified in {@link #MessageListener(ConnectionHandle)}).
     *
     * @param tag
     *            - tag of the expected message
     * @return {@link ResponseData} for message.
     * @throws Exception
     *             when message delivery fails
     */
    public ResponseData waitForAnswer(int tag) throws Exception {
        if (tag < 0 || tag > 63) {
            throw new IllegalArgumentException("Corrupted message tag");
        }
        this.tag = tag;
        for (IpmiResponse quickResponse : quickMessages) {
            this.notify(quickResponse);
        }

        while (response == null) {
            Thread.sleep(1);
        }
        if (response instanceof IpmiResponseData) {
            this.tag = -1;
            quickMessages.clear();
            return ((IpmiResponseData) response).getResponseData();
        } else /* response instanceof IpmiError */{
            throw ((IpmiError) response).getException();
        }
    }

    @Override
    public synchronized void notify(IpmiResponse response) {
        if (response.getHandle().getHandle() == handle.getHandle()) {
            if (tag == -1) {
                quickMessages.add(response);
            } else if (response.getTag() == tag) {
                this.response = response;
            }
        }
    }

}
