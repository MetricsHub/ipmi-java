package org.metricshub.ipmi.core.coding.commands.payload;

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
import org.metricshub.ipmi.core.common.TypeConverter;

import java.util.LinkedList;
import java.util.List;

/**
 * Wrapper for Get Payload Activation Status response.
 */
public class GetPayloadActivationStatusResponseData implements ResponseData {

    /**
     * Number of instances of given payload type that can be simultaneously activated on BMC.
     */
    private byte instanceCapacity;

    /**
     * List of instance ID's that are still available (not activated).
     */
    private List<Byte> availableInstances;

    public byte getInstanceCapacity() {
        return instanceCapacity;
    }

    public void setInstanceCapacity(byte instanceCapacity) {
        this.instanceCapacity = instanceCapacity;
    }

    public List<Byte> getAvailableInstances() {
        List<Byte> actuallyAvailableInstances = new LinkedList<Byte>();

        for (Byte instanceId : availableInstances) {
            if (instanceId <= instanceCapacity) {
                actuallyAvailableInstances.add(instanceId);
            }
        }

        return actuallyAvailableInstances;
    }

    public void setAvailableInstances(byte[] availableInstancesData) {
        this.availableInstances = getAvailableInstancesFromBytes(availableInstancesData);
    }

    private List<Byte> getAvailableInstancesFromBytes(byte[] availableInstancesData) {
        List<Byte> result = new LinkedList<Byte>();

        List<Byte> instancesFromFirstByte = checkForAvailableInstancesInByte(availableInstancesData[0], 0);
        List<Byte> instancesFromSecondByte = checkForAvailableInstancesInByte(availableInstancesData[1], 8);

        result.addAll(instancesFromFirstByte);
        result.addAll(instancesFromSecondByte);

        return result;
    }

    private List<Byte> checkForAvailableInstancesInByte(byte availableInstancesByte, int instanceIdOffset) {
        List<Byte> result = new LinkedList<Byte>();

        for (int i = 0; i < 8; ++i) {
            if (!TypeConverter.isBitSetOnPosition(i, availableInstancesByte)) {
                result.add((byte) (i + 1 + instanceIdOffset));
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return "GetPayloadActivationStatusResponseData{" +
                "instanceCapacity=" + getInstanceCapacity() +
                ", availableInstances=" + getAvailableInstances() +
                '}';
    }
}
