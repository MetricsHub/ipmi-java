package org.metricshub.ipmi.core.coding.commands.sdr.record;

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

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * This record is used for locating FRU information that is on the IPMB, on a
 * Private Bus behind or management controller, or accessed via FRU commands to
 * a management controller.
 */
public class FruDeviceLocatorRecord extends SensorRecord {

    private int deviceAccessAddress;

    private int deviceId;

    /**
     * false - device is not a logical FRU Device <br>
     * true - device is logical FRU Device (accessed via FRU commands to mgmt.
     * controller)
     */
    private boolean logical;

    private int accessLun;

    private int managementChannelNumber;

    private DeviceType deviceType;

    private int deviceTypeModifier;

    private int fruEntityId;

    private int fruEntityInstance;

    private String name;

    @Override
    protected void populateTypeSpecficValues(byte[] recordData, SensorRecord record) {

        setDeviceAccessAddress((TypeConverter.byteToInt(recordData[5]) & 0xfe) >> 1);

        setLogical((TypeConverter.byteToInt(recordData[7]) & 0x80) != 0);

        int deviceIdFromRecord = TypeConverter.byteToInt(recordData[6]);

        if (!isLogical()) {
            deviceIdFromRecord &= 0xfe;
            deviceIdFromRecord >>= 1;
        }

        setDeviceId(deviceIdFromRecord);
        int id = TypeConverter.byteToInt(recordData[6]);

        if (!isLogical()) {
            id >>= 1;
        }

        setId(id);

        setAccessLun((TypeConverter.byteToInt(recordData[7]) & 0xc) >> 2);

        setManagementChannelNumber((TypeConverter.byteToInt(recordData[8]) & 0xf0) >> 4);

        setDeviceType(DeviceType.parseInt(TypeConverter
                .byteToInt(recordData[10])));

        setDeviceTypeModifier(TypeConverter.byteToInt(recordData[11]));

        setFruEntityId(TypeConverter.byteToInt(recordData[12]));

        setFruEntityInstance(TypeConverter.byteToInt(recordData[13]));

        byte[] nameData = new byte[recordData.length - 16];

        System.arraycopy(recordData, 16, nameData, 0, nameData.length);

        setName(decodeName(recordData[15], nameData));
    }

    public int getDeviceAccessAddress() {
        return deviceAccessAddress;
    }

    public void setDeviceAccessAddress(int deviceAccessAddress) {
        this.deviceAccessAddress = deviceAccessAddress;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isLogical() {
        return logical;
    }

    public void setLogical(boolean logical) {
        this.logical = logical;
    }

    public int getAccessLun() {
        return accessLun;
    }

    public void setAccessLun(int accessLun) {
        this.accessLun = accessLun;
    }

    public int getManagementChannelNumber() {
        return managementChannelNumber;
    }

    public void setManagementChannelNumber(int managementChannelNumber) {
        this.managementChannelNumber = managementChannelNumber;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public int getDeviceTypeModifier() {
        return deviceTypeModifier;
    }

    public void setDeviceTypeModifier(int deviceTypeModifier) {
        this.deviceTypeModifier = deviceTypeModifier;
    }

    public int getFruEntityId() {
        return fruEntityId;
    }

    public void setFruEntityId(int fruEntityId) {
        this.fruEntityId = fruEntityId;
    }

    public int getFruEntityInstance() {
        return fruEntityInstance;
    }

    public void setFruEntityInstance(int fruEntityInstance) {
        this.fruEntityInstance = fruEntityInstance;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
