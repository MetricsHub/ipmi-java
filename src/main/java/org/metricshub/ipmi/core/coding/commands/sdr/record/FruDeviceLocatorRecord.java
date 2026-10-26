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
 * A FRU Device Locator record (SDR type 11h, IPMI 2.0 table 43-7): it locates FRU information that is on the IPMB,
 * on a private bus behind a management controller, or accessed with the FRU commands of a management controller.
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

		// IPMI 2.0 Table 43-7 byte 8: bit 7 logical, bits [4:3] LUN, bits [2:0] private bus ID
		setAccessLun((TypeConverter.byteToInt(recordData[7]) & 0x18) >> 3);

		setManagementChannelNumber((TypeConverter.byteToInt(recordData[8]) & 0xf0) >> 4);

		setDeviceType(DeviceType.parseInt(TypeConverter.byteToInt(recordData[10])));

		setDeviceTypeModifier(TypeConverter.byteToInt(recordData[11]));

		setFruEntityId(TypeConverter.byteToInt(recordData[12]));

		setFruEntityInstance(TypeConverter.byteToInt(recordData[13]));

		byte[] nameData = new byte[recordData.length - 16];

		System.arraycopy(recordData, 16, nameData, 0, nameData.length);

		setName(decodeName(recordData[15], nameData));
	}

	/**
	 * Returns the slave address of the controller used to access the FRU device, in its 7-bit form (byte 6 bits 7:1 of
	 * the record), 0 when the device is directly on the IPMB.
	 * {@link org.metricshub.ipmi.core.coding.commands.app.SendMessage} takes the slave address in its 8-bit form: this
	 * value shifted left by one.
	 *
	 * @return the 7-bit slave address of the controller
	 */
	public int getDeviceAccessAddress() {
		return deviceAccessAddress;
	}

	/**
	 * Sets the slave address of the controller used to access the FRU device.
	 *
	 * @param deviceAccessAddress the 7-bit slave address of the controller
	 */
	public void setDeviceAccessAddress(int deviceAccessAddress) {
		this.deviceAccessAddress = deviceAccessAddress;
	}

	/**
	 * Returns the FRU device ID of a logical FRU device (byte 7 of the record), the ID that the FRU commands of its
	 * controller take, or the 7-bit I2C slave address of a non-logical FRU device (byte 7 bits 7:1).
	 *
	 * @return the FRU device ID, or the 7-bit slave address of a non-logical FRU device
	 */
	public int getDeviceId() {
		return deviceId;
	}

	/**
	 * Sets the FRU device ID, or the slave address of a non-logical FRU device.
	 *
	 * @param deviceId the FRU device ID, or the 7-bit slave address of a non-logical FRU device
	 */
	public void setDeviceId(int deviceId) {
		this.deviceId = deviceId;
	}

	/**
	 * Tells whether the FRU device is a logical FRU device, accessed with the FRU commands of its management
	 * controller, rather than a non-intelligent device accessed with Master Write-Read (byte 8 bit 7 of the record).
	 *
	 * @return whether the FRU device is a logical FRU device
	 */
	public boolean isLogical() {
		return logical;
	}

	/**
	 * Sets whether the FRU device is a logical FRU device.
	 *
	 * @param logical whether the FRU device is a logical FRU device
	 */
	public void setLogical(boolean logical) {
		this.logical = logical;
	}

	/**
	 * Returns the LUN of the FRU commands or Master Write-Read commands that access the FRU device (byte 8 bits 4:3 of
	 * the record).
	 *
	 * @return the LUN, 0 to 3
	 */
	public int getAccessLun() {
		return accessLun;
	}

	/**
	 * Sets the LUN of the commands that access the FRU device.
	 *
	 * @param accessLun the LUN, 0 to 3
	 */
	public void setAccessLun(int accessLun) {
		this.accessLun = accessLun;
	}

	/**
	 * Returns the channel number of the management controller used to access the FRU device (byte 9 bits 7:4 of the
	 * record), 0 when the device or its controller is on the primary IPMB.
	 *
	 * @return the channel number, 0 to 15
	 */
	public int getManagementChannelNumber() {
		return managementChannelNumber;
	}

	/**
	 * Sets the channel number of the management controller used to access the FRU device.
	 *
	 * @param managementChannelNumber the channel number, 0 to 15
	 */
	public void setManagementChannelNumber(int managementChannelNumber) {
		this.managementChannelNumber = managementChannelNumber;
	}

	/**
	 * Returns the type of the FRU device (byte 11 of the record, IPMI 2.0 table 43-12).
	 *
	 * @return the device type
	 */
	public DeviceType getDeviceType() {
		return deviceType;
	}

	/**
	 * Sets the type of the FRU device.
	 *
	 * @param deviceType the device type
	 */
	public void setDeviceType(DeviceType deviceType) {
		this.deviceType = deviceType;
	}

	/**
	 * Returns the device type modifier of the FRU device (byte 12 of the record, IPMI 2.0 table 43-12).
	 *
	 * @return the device type modifier code
	 */
	public int getDeviceTypeModifier() {
		return deviceTypeModifier;
	}

	/**
	 * Sets the device type modifier of the FRU device.
	 *
	 * @param deviceTypeModifier the device type modifier code
	 */
	public void setDeviceTypeModifier(int deviceTypeModifier) {
		this.deviceTypeModifier = deviceTypeModifier;
	}

	/**
	 * Returns the ID of the entity associated with this FRU information (byte 13 of the record, IPMI 2.0 table 43-13).
	 *
	 * @return the entity ID code
	 */
	public int getFruEntityId() {
		return fruEntityId;
	}

	/**
	 * Sets the ID of the entity associated with this FRU information.
	 *
	 * @param fruEntityId the entity ID code
	 */
	public void setFruEntityId(int fruEntityId) {
		this.fruEntityId = fruEntityId;
	}

	/**
	 * Returns the instance number of the entity associated with this FRU information (byte 14 of the record).
	 *
	 * @return the entity instance number
	 */
	public int getFruEntityInstance() {
		return fruEntityInstance;
	}

	/**
	 * Sets the instance number of the entity associated with this FRU information.
	 *
	 * @param fruEntityInstance the entity instance number
	 */
	public void setFruEntityInstance(int fruEntityInstance) {
		this.fruEntityInstance = fruEntityInstance;
	}

	/**
	 * Sets the device ID string of the FRU device.
	 *
	 * @param name the device ID string
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the device ID string of the FRU device (from byte 17 of the record), decoded by its type/length byte
	 * (byte 16).
	 *
	 * @return the device ID string, empty when the record has none
	 */
	public String getName() {
		return name;
	}

}
