package org.metricshub.ipmi.core.connection.queue;

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

import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.ResponseData;

import java.util.Date;

public class QueueElement {
    private int id;
    /**
     * @deprecated retries on message level are deprecated
     */
    @Deprecated
    private int retries;
    private boolean timedOut;

    private PayloadCoder request;
    private ResponseData response;
    private Date timestamp;

    public QueueElement(int id, PayloadCoder request) {
        this.id = id;
        this.request = request;
        timestamp = new Date();
        retries = 0;
        this.timedOut = false;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * @deprecated retries on message level are deprecated
     */
    @Deprecated
    public int getRetries() {
        return retries;
    }

    /**
     * @deprecated retries on message level are deprecated
     */
    @Deprecated
    public void setRetries(int retries) {
        this.retries = retries;
    }

    public PayloadCoder getRequest() {
        return request;
    }

    public void setRequest(PayloadCoder request) {
        this.request = request;
    }

    public ResponseData getResponse() {
        return response;
    }

    public void setResponse(ResponseData response) {
        this.response = response;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void refreshTimestamp() {
        timestamp = new Date();
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void makeTimedOut() {
        this.timedOut = true;
    }
}
