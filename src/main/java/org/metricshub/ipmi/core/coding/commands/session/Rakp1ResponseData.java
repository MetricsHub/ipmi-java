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
 * A wrapper for RAKP 2 message.
 * @see Rakp1
 */
public class Rakp1ResponseData implements ResponseData {

    private byte messageTag;

    private byte statusCode;

    private int remoteConsoleSessionId;

    private byte[] managedSystemRandomNumber;

    private byte[] managedSystemGuid;

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

    public void setRemoteConsoleSessionId(int remoteConsoleSessionId) {
        this.remoteConsoleSessionId = remoteConsoleSessionId;
    }

    public int getRemoteConsoleSessionId() {
        return remoteConsoleSessionId;
    }

    public void setManagedSystemGuid(byte[] managedSystemGuid) {
        this.managedSystemGuid = managedSystemGuid;
    }

    public byte[] getManagedSystemGuid() {
        return managedSystemGuid;
    }

    public void setManagedSystemRandomNumber(byte[] randomNumber) {
        this.managedSystemRandomNumber = randomNumber;
    }

    public byte[] getManagedSystemRandomNumber() {
        return managedSystemRandomNumber;
    }
}
