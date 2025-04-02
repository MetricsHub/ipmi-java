package org.metricshub.ipmi.core.coding.commands.fru;

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
 * Wrapper for Read FRU Data response. Contains only raw data, because size of
 * the FRU Inventory Area might exceed size of the communication packet so it
 * might come in many {@link ReadFruDataResponseData} packets and it must be
 * decoded by {@link ReadFruData#decodeFruData(java.util.List)}.
 */
public class ReadFruDataResponseData implements ResponseData {
    private byte[] fruData;

    public void setFruData(byte[] fruData) {
        this.fruData = fruData;
    }

    public byte[] getFruData() {
        return fruData;
    }
}
