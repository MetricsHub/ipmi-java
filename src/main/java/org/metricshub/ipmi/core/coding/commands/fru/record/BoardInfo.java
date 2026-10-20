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
import java.util.Date;

/**
 * FRU record containing Board info. <br>
 * This area provides Serial Number, Part Number, and other information about
 * the board that the FRU Information Device is located on.
 * <p>
 * Each string field is decoded according to its type/length byte: text without its leading and trailing spaces, BCD
 * plus, 6-bit ASCII, or binary data as lowercase hexadecimal digits. A field the area does not give is empty, never
 * null.
 */
public class BoardInfo extends FruRecord {

	/** 1996-01-01 00:00:00 UTC. */
	private static final long MFG_DATE_EPOCH_MS = 820454400000L;

	private Date mfgDate;

	private String boardManufacturer = "";

	private String boardProductName = "";

	private String boardSerialNumber = "";

	private String boardPartNumber = "";

	private byte[] fruFileId = new byte[0];

	private String[] customBoardInfo = new String[0];

	/**
	 * Creates and populates record
	 *
	 * @param fruData
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 */
	public BoardInfo(final byte[] fruData, final int offset) {
		validateFruData(fruData[offset]);

		int languageCode = TypeConverter.byteToInt(fruData[offset + 2]);

		byte[] buffer = new byte[4];

		buffer[0] = fruData[offset + 3];
		buffer[1] = fruData[offset + 4];
		buffer[2] = fruData[offset + 5];
		buffer[3] = 0;

		// Minutes since 1996-01-01 00:00 UTC (FRU spec section 11); 0 means unspecified
		long minutes = TypeConverter.littleEndianByteArrayToInt(buffer);
		setMfgDate(minutes == 0 ? null : new Date(MFG_DATE_EPOCH_MS + minutes * 60000L));

		int partNumber = TypeConverter.byteToInt(fruData[offset + 6]);

		ArrayList<String> customInfo = readCustomInfo(fruData, languageCode, partNumber, offset + 7);

		customBoardInfo = new String[customInfo.size()];
		customBoardInfo = customInfo.toArray(customBoardInfo);
	}

	private void validateFruData(byte fruDatum) {
		if (fruDatum != 0x1) {
			throw new IllegalArgumentException("Invalid format version");
		}
	}

	private ArrayList<String> readCustomInfo(
			final byte[] fruData,
			final int languageCode,
			final int partNumber,
			final int offset) {
		ArrayList<String> customInfo = new ArrayList<String>();
		int index = 0;

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
					setBoardManufacturer(
							decodeString(
									partType,
									partNumberData,
									languageCode == 0 || languageCode == 25));
					break;
				case 1:
					setBoardProductName(
							decodeString(
									partType,
									partNumberData,
									languageCode == 0 || languageCode == 25));
					break;
				case 2:
					setBoardSerialNumber(
							decodeString(
									partType,
									partNumberData,
									true));
					break;
				case 3:
					setBoardPartNumber(
							decodeString(
									partType,
									partNumberData,
									languageCode == 0 || languageCode == 25));
					break;
				case 4:
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
											languageCode == 0 || languageCode == 25));
					break;
				}
			}

			currentPartNumber = TypeConverter.byteToInt(fruData[currentOffset]);

			++currentOffset;

			++index;
		}

		return customInfo;
	}

	private boolean partDataLengthWithinBounds(byte[] fruData, int currentOfset, int partDataLength) {
		return partDataLength > 0 && partDataLength + currentOfset < fruData.length;
	}

	/**
	 * Returns the manufacturing date and time of the board, which the area gives as minutes since 1996-01-01 00:00
	 * UTC (FRU specification section 11).
	 *
	 * @return the date, null when the area leaves it unspecified
	 */
	public Date getMfgDate() {
		return mfgDate;
	}

	/**
	 * Sets the manufacturing date and time of the board.
	 *
	 * @param mfgDate the date, null when unspecified
	 */
	public void setMfgDate(Date mfgDate) {
		this.mfgDate = mfgDate;
	}

	/**
	 * Returns the board manufacturer.
	 *
	 * @return the board manufacturer, empty when the area does not give it
	 */
	public String getBoardManufacturer() {
		return boardManufacturer;
	}

	/**
	 * Sets the board manufacturer.
	 *
	 * @param boardManufacturer the board manufacturer
	 */
	public void setBoardManufacturer(String boardManufacturer) {
		this.boardManufacturer = boardManufacturer;
	}

	/**
	 * Returns the board product name.
	 *
	 * @return the board product name, empty when the area does not give it
	 */
	public String getBoardProductName() {
		return boardProductName;
	}

	/**
	 * Sets the board product name.
	 *
	 * @param boardProductName the board product name
	 */
	public void setBoardProductName(String boardProductName) {
		this.boardProductName = boardProductName;
	}

	/**
	 * Returns the board serial number.
	 *
	 * @return the board serial number, empty when the area does not give it
	 */
	public String getBoardSerialNumber() {
		return boardSerialNumber;
	}

	/**
	 * Sets the board serial number.
	 *
	 * @param boardSerialNumber the board serial number
	 */
	public void setBoardSerialNumber(String boardSerialNumber) {
		this.boardSerialNumber = boardSerialNumber;
	}

	/**
	 * Returns the board part number.
	 *
	 * @return the board part number, empty when the area does not give it
	 */
	public String getBoardPartNumber() {
		return boardPartNumber;
	}

	/**
	 * Sets the board part number.
	 *
	 * @param boardPartNumber the board part number
	 */
	public void setBoardPartNumber(String boardPartNumber) {
		this.boardPartNumber = boardPartNumber;
	}

	/**
	 * Returns the FRU File ID field, a manufacturing aid that identifies the file the FRU information was loaded from.
	 *
	 * @return the raw bytes of the field, empty when the board area does not give it
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
	 * Returns the custom fields of the board area, those that follow its predefined fields.
	 *
	 * @return the custom fields in order, empty when there are none
	 */
	public String[] getCustomBoardInfo() {
		return customBoardInfo;
	}

	/**
	 * Sets the custom fields of the board area.
	 *
	 * @param customBoardInfo the custom fields in order
	 */
	public void setCustomBoardInfo(String[] customBoardInfo) {
		this.customBoardInfo = customBoardInfo;
	}

}
