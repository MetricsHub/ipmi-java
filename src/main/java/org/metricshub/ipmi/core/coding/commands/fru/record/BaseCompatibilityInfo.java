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

import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Base Compatibility record (type 04h) from the FRU MultiRecord area
 */
public class BaseCompatibilityInfo extends MultiRecordInfo {

	private int manufacturerId;

	private EntityId entityId;

	private int compatibilityBase;

	private int codeStart;

	private byte[] codeRangeMasks;

	/**
	 * Creates and populates record
	 *
	 * @param fruData
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 * @param length
	 *        - length of the record
	 */
	public BaseCompatibilityInfo(byte[] fruData, int offset, int length) {
		super();

		byte[] buffer = new byte[4];

		System.arraycopy(fruData, offset, buffer, 0, 3);
		buffer[3] = 0;

		manufacturerId = TypeConverter.littleEndianByteArrayToInt(buffer);
		entityId = EntityId.parseInt(TypeConverter.byteToInt(fruData[offset + 3]));
		compatibilityBase = TypeConverter.byteToInt(fruData[offset + 4]);
		codeStart = TypeConverter.byteToInt(fruData[offset + 5]) & 0x7f;
		codeRangeMasks = new byte[length - 6];
		System.arraycopy(fruData, offset + 6, codeRangeMasks, 0, length - 6);
	}

	/**
	 * Returns the manufacturer ID of the record.
	 *
	 * @return the IANA manufacturer ID (3 bytes in the record, least significant byte first)
	 */
	public int getManufacturerId() {
		return manufacturerId;
	}

	/**
	 * Sets the manufacturer ID of the record.
	 *
	 * @param manufacturerId the IANA manufacturer ID
	 */
	public void setManufacturerId(int manufacturerId) {
		this.manufacturerId = manufacturerId;
	}

	/**
	 * Returns the entity ID of the record.
	 *
	 * @return the entity ID, {@link EntityId#Other} for a code that {@link EntityId} does not list
	 */
	public EntityId getEntityId() {
		return entityId;
	}

	/**
	 * Sets the entity ID of the record.
	 *
	 * @param entityId the entity ID
	 */
	public void setEntityId(EntityId entityId) {
		this.entityId = entityId;
	}

	/**
	 * Returns the compatibility base of the record.
	 *
	 * @return the compatibility base, 0 to 255
	 */
	public int getCompatibilityBase() {
		return compatibilityBase;
	}

	/**
	 * Sets the compatibility base of the record.
	 *
	 * @param compatibilityBase the compatibility base
	 */
	public void setCompatibilityBase(int compatibilityBase) {
		this.compatibilityBase = compatibilityBase;
	}

	/**
	 * Returns the compatibility code start value of the record.
	 *
	 * @return the code start value (bits 6:0 of its byte)
	 */
	public int getCodeStart() {
		return codeStart;
	}

	/**
	 * Sets the compatibility code start value of the record.
	 *
	 * @param codeStart the code start value
	 */
	public void setCodeStart(int codeStart) {
		this.codeStart = codeStart;
	}

	/**
	 * Returns the code range masks of the record.
	 *
	 * @return the code range mask bytes that follow the code start
	 */
	public byte[] getCodeRangeMasks() {
		return codeRangeMasks;
	}

	/**
	 * Sets the code range masks of the record.
	 *
	 * @param codeRangeMasks the code range mask bytes
	 */
	public void setCodeRangeMasks(byte[] codeRangeMasks) {
		this.codeRangeMasks = codeRangeMasks;
	}

}
