package org.metricshub.ipmi.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
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

/**
 * A function the BMC provides, from the additional device support byte of Get Device ID (IPMI 2.0 table 20-2).
 */
public enum DeviceFunction {
	/** The device has sensors (bit 0). */
	SENSOR_DEVICE(0x01),
	/** The device holds the SDR repository (bit 1). */
	SDR_REPOSITORY_DEVICE(0x02),
	/** The device holds the System Event Log (bit 2). */
	SEL_DEVICE(0x04),
	/** The device holds a FRU inventory (bit 3). */
	FRU_INVENTORY_DEVICE(0x08),
	/** The device receives events over the IPMB (bit 4). */
	IPMB_EVENT_RECEIVER(0x10),
	/** The device generates events over the IPMB (bit 5). */
	IPMB_EVENT_GENERATOR(0x20),
	/**
	 * The device responds to the commands of the Bridge network function, used for ICMB bridging (bit 6). It does not
	 * tell whether the BMC forwards Send Message requests to other controllers.
	 */
	BRIDGE(0x40),
	/** The device implements the chassis commands, as the chassis device of the ICMB specification (bit 7). */
	CHASSIS_DEVICE(0x80);

	private final int bit;

	DeviceFunction(int bit) {
		this.bit = bit;
	}

	/**
	 * Tells whether this function is flagged in the additional device support byte.
	 *
	 * @param additionalDeviceSupport the byte
	 * @return true when the bit of this function is set
	 */
	public boolean isSetIn(byte additionalDeviceSupport) {
		return (additionalDeviceSupport & bit) != 0;
	}
}
