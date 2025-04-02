package org.metricshub.ipmi.core.coding.commands.sdr;

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
 * Wrapper for Get SDR Repository Info response.
 */
public class GetSdrRepositoryInfoResponseData implements ResponseData {
    /**
     * Version number of the SDR command set for the SDR Device.
     */
    private int sdrVersion;

    /**
     * Number of records in the SDR Repository
     */
    private int recordCount;

    /**
     * Most recent addition timestamp.
     */
    private int addTimestamp;

    /**
     * Most recent erase (delete or clear) timestamp.
     */
    private int delTimestamp;

    /**
     * Reserve SDR Repository command supported
     */
    private boolean reserveSupported;

    public void setSdrVersion(int sdrVersion) {
        this.sdrVersion = sdrVersion;
    }

    public int getSdrVersion() {
        return sdrVersion;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setAddTimestamp(int addTimestamp) {
        this.addTimestamp = addTimestamp;
    }

    public int getAddTimestamp() {
        return addTimestamp;
    }

    public void setDelTimestamp(int delTimestamp) {
        this.delTimestamp = delTimestamp;
    }

    public int getDelTimestamp() {
        return delTimestamp;
    }

    public void setReserveSupported(boolean reserveSupported) {
        this.reserveSupported = reserveSupported;
    }

    public boolean isReserveSupported() {
        return reserveSupported;
    }


}
