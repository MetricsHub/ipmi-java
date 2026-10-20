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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.metricshub.ipmi.core.coding.commands.app.GetDeviceIdResponseData;

/**
 * The identity of the BMC: its manufacturer and product IDs, its firmware revision, the IPMI version it implements
 * and the functions it provides (Get Device ID, IPMI 2.0 section 20.1). The manufacturer ID is the IANA enterprise
 * number of the vendor (2 for IBM, 343 for Intel, 674 for Dell, 10876 for Supermicro, 19046 for Lenovo...), which
 * tells which OEM records and commands to expect.
 */
public final class DeviceId {

	private final GetDeviceIdResponseData data;

	/**
	 * Creates the device ID from the reply of the BMC.
	 *
	 * @param data the reply to Get Device ID
	 */
	DeviceId(GetDeviceIdResponseData data) {
		this.data = data;
	}

	/**
	 * Returns the device ID the manufacturer gives the controller, 0 when unspecified.
	 *
	 * @return the device ID
	 */
	public int id() {
		return data.getDeviceId();
	}

	/**
	 * Returns the revision of the device, as the manufacturer numbers it.
	 *
	 * @return the device revision
	 */
	public int revision() {
		return data.getDeviceRevision();
	}

	/**
	 * Returns the firmware revision as {@code ipmitool} prints it: the major revision, a dot and the two digits of the
	 * minor revision ({@code 1.44}, {@code 2.05}).
	 *
	 * @return the firmware revision
	 */
	public String firmwareRevision() {
		return data.getFirmwareRevision();
	}

	/**
	 * Returns the auxiliary firmware revision, four bytes whose meaning the manufacturer defines (a build number, a
	 * boot block version).
	 *
	 * @return the four bytes, or an empty array when the BMC does not send them
	 */
	public byte[] auxiliaryFirmwareRevision() {
		return data.getAuxiliaryFirmwareRevision();
	}

	/**
	 * Returns the version of IPMI the BMC implements, {@code 2.0} in practice: a BMC without RMCP+ fails the login
	 * before this command.
	 *
	 * @return the IPMI version
	 */
	public String ipmiVersion() {
		return data.getIpmiVersion();
	}

	/**
	 * Returns the manufacturer ID: the IANA enterprise number of the vendor of the BMC.
	 *
	 * @return the manufacturer ID, 0 when unspecified
	 */
	public int manufacturerId() {
		return data.getManufacturerId();
	}

	/**
	 * Returns the product ID, as the manufacturer numbers its products.
	 *
	 * @return the product ID
	 */
	public int productId() {
		return data.getProductId();
	}

	/**
	 * Tells whether the BMC is in normal operation, rather than updating its firmware or its SDR repository or
	 * initializing itself.
	 *
	 * @return true when the device is available
	 */
	public boolean available() {
		return data.isDeviceAvailable();
	}

	/**
	 * Tells whether the device provides Device SDRs, as a satellite controller does.
	 *
	 * @return true when the device provides Device SDRs
	 */
	public boolean providesDeviceSdrs() {
		return data.providesDeviceSdrs();
	}

	/**
	 * Returns the functions the BMC provides: sensors, SDR repository, SEL, FRU inventory, chassis...
	 *
	 * @return the functions, empty when the BMC flags none
	 */
	public Set<DeviceFunction> functions() {
		Set<DeviceFunction> functions = EnumSet.noneOf(DeviceFunction.class);
		for (DeviceFunction function : DeviceFunction.values()) {
			if (function.isSetIn(data.getAdditionalDeviceSupport())) {
				functions.add(function);
			}
		}
		return Collections.unmodifiableSet(functions);
	}

	/**
	 * Returns the reply of the BMC, as the protocol core decodes it.
	 *
	 * @return the reply to Get Device ID
	 */
	public GetDeviceIdResponseData data() {
		return data;
	}

	@Override
	public String toString() {
		return "Device " + id() + " revision " + revision() + ", firmware " + firmwareRevision() + ", IPMI "
				+ ipmiVersion() + ", manufacturer " + manufacturerId() + ", product " + productId() + ", " + functions();
	}
}
