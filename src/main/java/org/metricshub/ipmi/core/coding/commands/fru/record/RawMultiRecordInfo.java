package org.metricshub.ipmi.core.coding.commands.fru.record;

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

/**
 * A multirecord of a type the library does not model (Extended DC Output, Extended DC Load, a reserved type...):
 * its type and its data as they are, so that nothing of the FRU is lost.
 */
public class RawMultiRecordInfo extends MultiRecordInfo {

	private final int recordType;
	private final byte[] data;

	/**
	 * Creates the record.
	 *
	 * @param recordType the record type ID (FRU specification table 16-1)
	 * @param fruData the FRU data
	 * @param offset the offset of the record data, after its 5-byte header
	 * @param length the length of the record data
	 */
	public RawMultiRecordInfo(int recordType, byte[] fruData, int offset, int length) {
		this.recordType = recordType;
		this.data = Arrays.copyOfRange(fruData, offset, offset + length);
	}

	/**
	 * Returns the record type ID.
	 *
	 * @return the record type, 0 to 255
	 */
	public int getRecordType() {
		return recordType;
	}

	/**
	 * Returns the record data, after its header.
	 *
	 * @return a copy of the data
	 */
	public byte[] getData() {
		return data.clone();
	}
}
