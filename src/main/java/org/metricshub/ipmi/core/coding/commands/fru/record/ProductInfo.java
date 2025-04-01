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
 * FRU record containing Board info.<br>
 * This area is present if the FRU itself is a separate product. This is
 * typically seen when the FRU is an add-in card. When this area is provided in
 * the FRU Information Device that contains the Chassis Info Area, the product
 * info is for the overall system, as initially manufactured.
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
     *            - raw data containing record
     * @param offset
     *            - offset to the record in the data
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

    private ArrayList<String> readCustomInfo(final byte[] fruData, final int languageCode, final int partNumber, final int offset) {
        int index = 0;

        ArrayList<String> customInfo = new ArrayList<String>();

        int currentOffset = offset;
        int currentPartNumber = partNumber;

        while (currentPartNumber != 0xc1 && currentOffset < fruData.length) {

            int partType = (currentPartNumber & 0xc0) >> 6;

            int partDataLength = (currentPartNumber & 0x3f);

            if (partDataLengthWithinBounds(fruData, currentOffset, partDataLength)) {

                byte[] partNumberData = new byte[partDataLength];

                System.arraycopy(fruData, currentOffset, partNumberData, 0,
                        partDataLength);

                currentOffset += partDataLength;

                switch (index) {
                case 0:
                    setManufacturerName(FruRecord.decodeString(partType,
                            partNumberData, isEnglishLanguageCode(languageCode)));
                    break;
                case 1:
                    setProductName(FruRecord.decodeString(partType,
                            partNumberData, isEnglishLanguageCode(languageCode)));
                    break;
                case 2:
                    setProductModelNumber(FruRecord.decodeString(partType,
                            partNumberData, isEnglishLanguageCode(languageCode)));
                    break;
                case 3:
                    setProductVersion(FruRecord.decodeString(partType,
                            partNumberData, isEnglishLanguageCode(languageCode)));
                    break;
                case 4:
                    setProductSerialNumber(FruRecord.decodeString(partType,
                            partNumberData, true));
                    break;
                case 5:
                    setAssetTag(FruRecord.decodeString(partType,
                            partNumberData, isEnglishLanguageCode(languageCode)));
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
                    customInfo.add(FruRecord.decodeString(partType,
                            partNumberData, isEnglishLanguageCode(languageCode)));
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
        return languageCode != 0 && languageCode != 25;
    }

    private boolean partDataLengthWithinBounds(byte[] fruData, int currentOffset, int partDataLength) {
        return partDataLength > 0 && partDataLength + currentOffset < fruData.length;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductModelNumber() {
        return productModelNumber;
    }

    public void setProductModelNumber(String productModelNumber) {
        this.productModelNumber = productModelNumber;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public void setProductSerialNumber(String productSerialNumber) {
        this.productSerialNumber = productSerialNumber;
    }

    public String getProductSerialNumber() {
        return productSerialNumber;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag;
    }

    public byte[] getFruFileId() {
        return fruFileId;
    }

    public void setFruFileId(byte[] fruFileId) {
        this.fruFileId = fruFileId;
    }

    public String[] getCustomProductInfo() {
        return customProductInfo;
    }

    public void setCustomProductInfo(String[] customProductInfo) {
        this.customProductInfo = customProductInfo;
    }

}
