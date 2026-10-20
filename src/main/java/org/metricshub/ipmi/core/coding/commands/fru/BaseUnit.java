package org.metricshub.ipmi.core.coding.commands.fru;

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

/**
 * Represents unit which is used to access FRU
 */
public enum BaseUnit {
	/** The device is accessed by bytes. */
	Bytes(BaseUnit.BYTES),
	/** The device is accessed by 16-bit words: Read FRU Data takes its offset in words. */
	Words(BaseUnit.WORDS),;

	private static final int BYTES = 0;
	private static final int WORDS = 1;
	private static final int BYTESIZE = 1;
	private static final int WORDSIZE = 2;

	private int code;

	BaseUnit(int code) {
		this.code = code;
	}

	/**
	 * Returns the code of the unit.
	 *
	 * @return the access type bit of Get FRU Inventory Area Info: 0 for bytes, 1 for words
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Returns the unit of an access type bit.
	 *
	 * @param value the access type bit of Get FRU Inventory Area Info
	 * @return the matching unit
	 * @throws IllegalArgumentException when the value is neither 0 nor 1
	 */
	public static BaseUnit parseInt(int value) {
		switch (value) {
		case BYTES:
			return Bytes;
		case WORDS:
			return Words;
		default:
			throw new IllegalArgumentException("Invalid value: " + value);
		}
	}

	/**
	 * Returns the size of the unit in bytes.
	 *
	 * @return 1 for {@link #Bytes}, 2 for {@link #Words}
	 */
	public int getSize() {
		switch (this) {
		case Bytes:
			return BYTESIZE;
		case Words:
			return WORDSIZE;
		default:
			throw new IllegalArgumentException("Invalid value: " + this);
		}
	}
}
