package org.metricshub.ipmi.core.coding.commands.fru.record;

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

import java.util.ArrayList;

/**
 * FRU record containing Product info.<br>
 * This area is present if the FRU itself is a separate product. This is
 * typically seen when the FRU is an add-in card. When this area is provided in
 * the FRU Information Device that contains the Chassis Info Area, the product
 * info is for the overall system, as initially manufactured.
 * <p>
 * Each string field is decoded according to its type/length byte: text without its leading and trailing spaces, BCD
 * plus, 6-bit ASCII, or binary data as lowercase hexadecimal digits. A field the area does not give is empty, never
 * null.
 */
public class ProductInfo extends FruRecord {

	private String manufacturerName = "";

	private String productName = "";

	private String productModelNumber = "";

	private String productVersion = "";

	private String productSerialNumber = "";

	private String assetTag = "";

	private byte[] fruFileId = new byte[0];

	private String[] customProductInfo = new String[0];

	/**
	 * Creates and populates record
	 *
	 * @param fruData
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 */
	public ProductInfo(final byte[] fruData, final int offset) {
		super();

		if (fruData[offset] != 0x1) {
			throw new IllegalArgumentException("Invalid format version");
		}

		int languageCode = TypeConverter.byteToInt(fruData[offset + 2]);

		int partNumber = TypeConverter.byteToInt(fruData[offset + 3]);

		ArrayList<String> customInfo = readCustomInfo(fruData, languageCode, partNumber, offset + 4);

		customProductInfo = new String[customInfo.size()];
		customProductInfo = customInfo.toArray(customProductInfo);
	}

	private ArrayList<String> readCustomInfo(
			final byte[] fruData,
			final int languageCode,
			final int partNumber,
			final int offset) {
		int index = 0;

		ArrayList<String> customInfo = new ArrayList<String>();

		int currentOffset = offset;
		int currentPartNumber = partNumber;

		while (currentPartNumber != 0xc1 && currentOffset < fruData.length) {

			int partType = (currentPartNumber & 0xc0) >> 6;

			int partDataLength = currentPartNumber & 0x3f;

			if (partDataLengthWithinBounds(fruData, currentOffset, partDataLength)) {

				byte[] partNumberData = new byte[partDataLength];

				System
						.arraycopy(
								fruData,
								currentOffset,
								partNumberData,
								0,
								partDataLength);

				currentOffset += partDataLength;

				switch (index) {
				case 0:
					setManufacturerName(
							decodeString(
									partType,
									partNumberData,
									isEnglishLanguageCode(languageCode)));
					break;
				case 1:
					setProductName(
							decodeString(
									partType,
									partNumberData,
									isEnglishLanguageCode(languageCode)));
					break;
				case 2:
					setProductModelNumber(
							decodeString(
									partType,
									partNumberData,
									isEnglishLanguageCode(languageCode)));
					break;
				case 3:
					setProductVersion(
							decodeString(
									partType,
									partNumberData,
									isEnglishLanguageCode(languageCode)));
					break;
				case 4:
					setProductSerialNumber(
							decodeString(
									partType,
									partNumberData,
									true));
					break;
				case 5:
					setAssetTag(
							decodeString(
									partType,
									partNumberData,
									isEnglishLanguageCode(languageCode)));
					break;
				case 6:
					setFruFileId(partNumberData);
					break;
				default:
					if (partDataLength == 0) {
						currentPartNumber = TypeConverter.byteToInt(fruData[currentOffset]);
						++currentOffset;
						continue;
					}
					customInfo
							.add(
									decodeString(
											partType,
											partNumberData,
											isEnglishLanguageCode(languageCode)));
					break;
				}
			}

			currentPartNumber = TypeConverter.byteToInt(fruData[currentOffset]);

			++currentOffset;

			++index;
		}
		return customInfo;
	}

	private boolean isEnglishLanguageCode(int languageCode) {
		return languageCode == 0 || languageCode == 25;
	}

	private boolean partDataLengthWithinBounds(byte[] fruData, int currentOffset, int partDataLength) {
		return partDataLength > 0 && partDataLength + currentOffset < fruData.length;
	}

	/**
	 * Returns the product manufacturer name.
	 *
	 * @return the product manufacturer name, empty when the area does not give it
	 */
	public String getManufacturerName() {
		return manufacturerName;
	}

	/**
	 * Sets the product manufacturer name.
	 *
	 * @param manufacturerName the product manufacturer name
	 */
	public void setManufacturerName(String manufacturerName) {
		this.manufacturerName = manufacturerName;
	}

	/**
	 * Returns the product name.
	 *
	 * @return the product name, empty when the area does not give it
	 */
	public String getProductName() {
		return productName;
	}

	/**
	 * Sets the product name.
	 *
	 * @param productName the product name
	 */
	public void setProductName(String productName) {
		this.productName = productName;
	}

	/**
	 * Returns the product part or model number.
	 *
	 * @return the product part or model number, empty when the area does not give it
	 */
	public String getProductModelNumber() {
		return productModelNumber;
	}

	/**
	 * Sets the product part or model number.
	 *
	 * @param productModelNumber the product part or model number
	 */
	public void setProductModelNumber(String productModelNumber) {
		this.productModelNumber = productModelNumber;
	}

	/**
	 * Returns the product version.
	 *
	 * @return the product version, empty when the area does not give it
	 */
	public String getProductVersion() {
		return productVersion;
	}

	/**
	 * Sets the product version.
	 *
	 * @param productVersion the product version
	 */
	public void setProductVersion(String productVersion) {
		this.productVersion = productVersion;
	}

	/**
	 * Sets the product serial number.
	 *
	 * @param productSerialNumber the product serial number
	 */
	public void setProductSerialNumber(String productSerialNumber) {
		this.productSerialNumber = productSerialNumber;
	}

	/**
	 * Returns the product serial number.
	 *
	 * @return the product serial number, empty when the area does not give it
	 */
	public String getProductSerialNumber() {
		return productSerialNumber;
	}

	/**
	 * Returns the asset tag.
	 *
	 * @return the asset tag, empty when the area does not give it
	 */
	public String getAssetTag() {
		return assetTag;
	}

	/**
	 * Sets the asset tag.
	 *
	 * @param assetTag the asset tag
	 */
	public void setAssetTag(String assetTag) {
		this.assetTag = assetTag;
	}

	/**
	 * Returns the FRU File ID field, a manufacturing aid that identifies the file the FRU information was loaded from.
	 *
	 * @return the raw bytes of the field, empty when the product area does not give it
	 */
	public byte[] getFruFileId() {
		return fruFileId;
	}

	/**
	 * Sets the FRU File ID field.
	 *
	 * @param fruFileId the raw bytes of the field
	 */
	public void setFruFileId(byte[] fruFileId) {
		this.fruFileId = fruFileId;
	}

	/**
	 * Returns the custom fields of the product area, those that follow its predefined fields.
	 *
	 * @return the custom fields in order, empty when there are none
	 */
	public String[] getCustomProductInfo() {
		return customProductInfo;
	}

	/**
	 * Sets the custom fields of the product area.
	 *
	 * @param customProductInfo the custom fields in order
	 */
	public void setCustomProductInfo(String[] customProductInfo) {
		this.customProductInfo = customProductInfo;
	}

}
