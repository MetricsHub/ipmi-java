package org.metricshub.ipmi.core.coding.commands.sel;

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

import java.util.Date;

import org.metricshub.ipmi.core.coding.commands.ResponseData;

/**
 * Wrapper for Get SEL Info response
 */
public class GetSelInfoResponseData implements ResponseData {
    private int selVersion;

    private int entriesCount;

    /**
     * Most recent addition timestamp.
     */
    private Date additionTimestamp;

    /**
     * Most recent erase timestamp.
     */
    private Date eraseTimestamp;

    public int getSelVersion() {
        return selVersion;
    }

    public void setSelVersion(int selVersion) {
        this.selVersion = selVersion;
    }

    public int getEntriesCount() {
        return entriesCount;
    }

    public void setEntriesCount(int entriesCount) {
        this.entriesCount = entriesCount;
    }

    public Date getAdditionTimestamp() {
        return additionTimestamp;
    }

    public void setAdditionTimestamp(Date additionTimestamp) {
        this.additionTimestamp = additionTimestamp;
    }

    public Date getEraseTimestamp() {
        return eraseTimestamp;
    }

    public void setEraseTimestamp(Date eraseTimestamp) {
        this.eraseTimestamp = eraseTimestamp;
    }
}
