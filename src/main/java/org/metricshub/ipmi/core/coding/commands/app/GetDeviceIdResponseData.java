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
 * firmware revision.
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
	 * @return the device ID (byte 1), 00h when unspecified
	 */
	public int getDeviceId() {
		return TypeConverter.byteToInt(raw[0]);
	}

	/**
	 * @return the device revision (byte 2, bits 3:0)
	 */
	public int getDeviceRevision() {
		return raw[1] & 0x0f;
	}

	/**
	 * @return true when the device provides Device SDRs (byte 2, bit 7)
	 */
	public boolean providesDeviceSdrs() {
		return (raw[1] & 0x80) != 0;
	}

	/**
	 * @return true when the device is in normal operation, false when a firmware or SDR repository update is in
	 *         progress (byte 3, bit 7)
	 */
	public boolean isDeviceAvailable() {
		return (raw[2] & 0x80) == 0;
	}

	/**
	 * @return the major firmware revision (byte 3, bits 6:0, binary)
	 */
	public int getMajorFirmwareRevision() {
		return raw[2] & 0x7f;
	}

	/**
	 * @return the minor firmware revision byte (byte 4), BCD encoded by the specification: {@code 44h} for .44
	 */
	public int getMinorFirmwareRevision() {
		return TypeConverter.byteToInt(raw[3]);
	}

	/**
	 * @return the firmware revision as {@code ipmitool} prints it: the major revision, a dot and the two BCD digits of
	 *         the minor revision ({@code 1.44}, {@code 2.05})
	 */
	public String getFirmwareRevision() {
		return getMajorFirmwareRevision() + "." + String.format("%02x", getMinorFirmwareRevision());
	}

	/**
	 * @return the IPMI version the device implements, from the BCD byte 5 ({@code 2.0}, {@code 1.5})
	 */
	public String getIpmiVersion() {
		return (raw[4] & 0x0f) + "." + ((raw[4] >> 4) & 0x0f);
	}

	/**
	 * @return the additional device support byte (byte 6): bit 0 sensor device, 1 SDR repository device, 2 SEL
	 *         device, 3 FRU inventory device, 4 IPMB event receiver, 5 IPMB event generator, 6 bridge, 7 chassis
	 *         device
	 */
	public byte getAdditionalDeviceSupport() {
		return raw[5];
	}

	/**
	 * @return the manufacturer ID (bytes 7 to 9, 20 bits), the IANA enterprise number of the manufacturer; 0 when
	 *         unspecified, {@code 0FFFFFh} when reserved
	 */
	public int getManufacturerId() {
		return TypeConverter.byteToInt(raw[MANUFACTURER_ID_OFFSET])
				| TypeConverter.byteToInt(raw[MANUFACTURER_ID_OFFSET + 1]) << 8
				| (raw[MANUFACTURER_ID_OFFSET + 2] & 0x0f) << 16;
	}

	/**
	 * @return the product ID (bytes 10 and 11), as the manufacturer numbers its products
	 */
	public int getProductId() {
		return TypeConverter.byteToInt(raw[PRODUCT_ID_OFFSET]) | TypeConverter.byteToInt(raw[PRODUCT_ID_OFFSET + 1]) << 8;
	}

	/**
	 * @return the auxiliary firmware revision information (bytes 12 to 15), whose meaning the manufacturer defines;
	 *         empty when the device does not send it
	 */
	public byte[] getAuxiliaryFirmwareRevision() {
		return raw.length >= LENGTH_WITH_AUXILIARY_REVISION ?
				Arrays.copyOfRange(raw, MINIMUM_LENGTH, LENGTH_WITH_AUXILIARY_REVISION) :
				new byte[0];
	}
}
