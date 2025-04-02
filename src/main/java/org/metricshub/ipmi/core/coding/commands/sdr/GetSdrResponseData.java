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
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;

/**
 * Wrapper for Get SDR command response.
 */
public class GetSdrResponseData implements ResponseData {

    /**
     * ID of the next record in the repository.
     */
    private int nextRecordId;

    /**
     * Sensor record data
     */
    private byte[] sensorRecordData;

    public void setNextRecordId(int nextRecordId) {
        this.nextRecordId = nextRecordId;
    }

    public int getNextRecordId() {
        return nextRecordId;
    }

    public void setSensorRecordData(byte[] sensorRecordData) {
        this.sensorRecordData = sensorRecordData;
    }

    /**
     * @return Unparsed sensor record data. Might contain only part of the
     *         record, depending on offset and size specified in the request. To
     *         parse data use {@link SensorRecord#populateSensorRecord(byte[])}.
     */
    public byte[] getSensorRecordData() {
        return sensorRecordData;
    }
}
