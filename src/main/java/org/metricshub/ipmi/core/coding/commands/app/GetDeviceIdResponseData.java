package org.metricshub.ipmi.core.coding.commands.app;

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

import java.util.Arrays;

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Wrapper for the Get Device ID response (IPMI 2.0 table 20-2): the device ID and revision, the firmware revision,
 * the IPMI version, the functions the device provides, the manufacturer and product IDs and the optional auxiliary
 * firmware revision. The byte numbers below are those of the table, where byte 1 is the completion code.
 */
public class GetDeviceIdResponseData implements ResponseData {

	/** The length of the response data without the auxiliary firmware revision. */
	public static final int MINIMUM_LENGTH = 11;
	/** The length of the response data with the auxiliary firmware revision. */
	public static final int LENGTH_WITH_AUXILIARY_REVISION = 15;

	private static final int MANUFACTURER_ID_OFFSET = 6;
	private static final int PRODUCT_ID_OFFSET = 9;

	private final byte[] raw;

	/**
	 * Decodes the response data.
	 *
	 * @param responseData the response data bytes, after the completion code
	 * @throws IllegalArgumentException when they are shorter than {@link #MINIMUM_LENGTH}
	 */
	public GetDeviceIdResponseData(byte[] responseData) {
		if (responseData == null || responseData.length < MINIMUM_LENGTH) {
			throw new IllegalArgumentException("Invalid response payload length");
		}
		raw = responseData.clone();
	}

	/**
	 * Returns the device ID (byte 2).
	 *
	 * @return the device ID, 00h when unspecified
	 */
	public int getDeviceId() {
		return TypeConverter.byteToInt(raw[0]);
	}

	/**
	 * Returns the device revision (byte 3, bits 3:0).
	 *
	 * @return the device revision
	 */
	public int getDeviceRevision() {
		return raw[1] & 0x0f;
	}

	/**
	 * Tells whether the device provides Device SDRs (byte 3, bit 7).
	 *
	 * @return true when the device provides Device SDRs
	 */
	public boolean providesDeviceSdrs() {
		return (raw[1] & 0x80) != 0;
	}

	/**
	 * Tells whether the device is available (byte 4, bit 7).
	 *
	 * @return true when the device is in normal operation, false when a firmware update, an SDR repository update or
	 *         the self-initialization of the device is in progress
	 */
	public boolean isDeviceAvailable() {
		return (raw[2] & 0x80) == 0;
	}

	/**
	 * Returns the major firmware revision (byte 4, bits 6:0, binary).
	 *
	 * @return the major firmware revision
	 */
	public int getMajorFirmwareRevision() {
		return raw[2] & 0x7f;
	}

	/**
	 * Returns the minor firmware revision byte (byte 5).
	 *
	 * @return the minor firmware revision byte, BCD encoded by the specification: {@code 44h} for .44
	 */
	public int getMinorFirmwareRevision() {
		return TypeConverter.byteToInt(raw[3]);
	}

	/**
	 * Returns the firmware revision as {@code ipmitool} prints it.
	 *
	 * @return the major revision, a dot and the two BCD digits of the minor revision ({@code 1.44}, {@code 2.05})
	 */
	public String getFirmwareRevision() {
		return getMajorFirmwareRevision() + "." + String.format("%02x", getMinorFirmwareRevision());
	}

	/**
	 * Returns the IPMI version the device implements (byte 6, BCD).
	 *
	 * @return the IPMI version ({@code 2.0}, {@code 1.5})
	 */
	public String getIpmiVersion() {
		return (raw[4] & 0x0f) + "." + ((raw[4] >> 4) & 0x0f);
	}

	/**
	 * Returns the additional device support byte (byte 7), the functions the device provides.
	 *
	 * @return the additional device support byte: bit 0 sensor device, 1 SDR repository device, 2 SEL device, 3 FRU
	 *         inventory device, 4 IPMB event receiver, 5 IPMB event generator, 6 bridge, 7 chassis device
	 */
	public byte getAdditionalDeviceSupport() {
		return raw[5];
	}

	/**
	 * Returns the manufacturer ID (bytes 8 to 10, 20 bits), the IANA enterprise number of the manufacturer.
	 *
	 * @return the manufacturer ID; 0 when unspecified, {@code 0FFFFFh} when reserved
	 */
	public int getManufacturerId() {
		return TypeConverter.byteToInt(raw[MANUFACTURER_ID_OFFSET])
				| TypeConverter.byteToInt(raw[MANUFACTURER_ID_OFFSET + 1]) << 8
				| (raw[MANUFACTURER_ID_OFFSET + 2] & 0x0f) << 16;
	}

	/**
	 * Returns the product ID (bytes 11 and 12).
	 *
	 * @return the product ID, as the manufacturer numbers its products
	 */
	public int getProductId() {
		return TypeConverter.byteToInt(raw[PRODUCT_ID_OFFSET]) | TypeConverter.byteToInt(raw[PRODUCT_ID_OFFSET + 1]) << 8;
	}

	/**
	 * Returns the auxiliary firmware revision information (bytes 13 to 16), whose meaning the manufacturer defines.
	 *
	 * @return the auxiliary firmware revision information, empty when the device does not send it
	 */
	public byte[] getAuxiliaryFirmwareRevision() {
		return raw.length >= LENGTH_WITH_AUXILIARY_REVISION ?
				Arrays.copyOfRange(raw, MINIMUM_LENGTH, LENGTH_WITH_AUXILIARY_REVISION) :
				new byte[0];
	}
}
