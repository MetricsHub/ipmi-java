package org.metricshub.ipmi.core.coding.commands.session;

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

/**
 * A wrapper for RAKP 3 message.
 * @see Rakp3
 */
public class Rakp3ResponseData implements ResponseData {

    private byte messageTag;

    private byte statusCode;

    /**
     * The Console Session ID specified by the RMCP+ Open Session Request
     * message associated with this response.
     */
    private int consoleSessionId;

    public void setMessageTag(byte messageTag) {
        this.messageTag = messageTag;
    }

    public byte getMessageTag() {
        return messageTag;
    }

    public void setStatusCode(byte statusCode) {
        this.statusCode = statusCode;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public void setConsoleSessionId(int consoleSessionId) {
        this.consoleSessionId = consoleSessionId;
    }

    public int getConsoleSessionId() {
        return consoleSessionId;
    }
}
