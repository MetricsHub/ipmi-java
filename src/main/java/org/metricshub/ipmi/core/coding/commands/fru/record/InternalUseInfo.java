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
 * The internal use area of a FRU (FRU specification section 9): vendor-private data, kept as it is. Its length is
 * not in the data: the area ends where the next area starts, or where the data read ends.
 */
public class InternalUseInfo extends FruRecord {

	private final byte[] data;

	/**
	 * Creates the record.
	 *
	 * @param fruData the FRU data
	 * @param offset the offset of the area (its format version byte)
	 * @param end the offset of the next area, or the end of the data
	 */
	public InternalUseInfo(byte[] fruData, int offset, int end) {
		this.data = Arrays.copyOfRange(fruData, offset, end);
	}

	/**
	 * Returns the area as it is, format version byte first.
	 *
	 * @return a copy of the data
	 */
	public byte[] getData() {
		return data.clone();
	}
}
