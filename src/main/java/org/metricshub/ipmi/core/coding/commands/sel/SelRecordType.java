package org.metricshub.ipmi.core.coding.commands.sel;

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
 * The record type of a SEL entry (IPMI 2.0 section 31.6.1): one constant per range of record type codes.
 */
public enum SelRecordType {
	/**
	 * A timestamped OEM record (C0h-DFh, IPMI 2.0 section 32.2): a timestamp, a manufacturer ID and 6 OEM-defined
	 * bytes.
	 */
	OemTimestamped(SelRecordType.OEMTIMESTAMPED),
	/**
	 * A system event record (02h, IPMI 2.0 section 32.1).
	 */
	System(SelRecordType.SYSTEM),
	/**
	 * A non-timestamped OEM record (E0h-FFh, IPMI 2.0 section 32.3): 13 OEM-defined bytes.
	 */
	OemNonTimestamped(
			SelRecordType.OEMNONTIMESTAMPED),
	/**
	 * A reserved record type (00h-01h and 03h-BFh, IPMI 2.0 section 31.6.1): only the record ID of such an entry is
	 * meaningful.
	 */
	Reserved(SelRecordType.RESERVED),;

	/**
	 * Represents OEM timestamped record type (C0h-DFh)
	 */
	private static final int OEMTIMESTAMPED = 192;
	private static final int SYSTEM = 2;
	/**
	 * Represents OEM non-timestamped record type (E0h-FFh)
	 */
	private static final int OEMNONTIMESTAMPED = 224;
	private static final int RESERVED = -1;

	private int code;

	SelRecordType(int code) {
		this.code = code;
	}

	/**
	 * Returns the code of the record type: the code of a system event, the first code of the range for the OEM types,
	 * -1 for a reserved type. The record type byte of an entry is {@code getRawData()[2]} of its {@link SelRecord}.
	 *
	 * @return 02h for System, C0h for every timestamped OEM record, E0h for every non-timestamped one, -1 for Reserved
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Returns the record type of a record type code.
	 *
	 * @param value the record type byte, 0 to 255
	 * @return System for 02h, OemTimestamped for C0h-DFh, OemNonTimestamped for E0h-FFh, Reserved for the other codes
	 */
	public static SelRecordType parseInt(int value) {
		if (value == SYSTEM) {
			return System;
		}
		if (value >= OEMNONTIMESTAMPED) {
			return OemNonTimestamped;
		}
		if (value >= OEMTIMESTAMPED) {
			return OemTimestamped;
		}
		return Reserved;
	}
}
