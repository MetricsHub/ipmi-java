package org.metricshub.ipmi.client;

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

/**
 * An SDR record the repository walk could not use: the BMC returned it truncated, or the library cannot decode it.
 * OEM records (types {@code C0h} to {@code FFh}) are decoded as
 * {@link org.metricshub.ipmi.core.coding.commands.sdr.record.OemRecord} and never skipped.
 */
public final class SkippedRecord {

	private final int recordId;
	private final int recordType;
	private final String reason;

	/**
	 * Creates the description of a skipped record.
	 *
	 * @param recordId the ID of the record in the repository
	 * @param recordType the record type byte, -1 when the header could not be read
	 * @param reason why the record was skipped
	 */
	SkippedRecord(int recordId, int recordType, String reason) {
		this.recordId = recordId;
		this.recordType = recordType;
		this.reason = reason;
	}

	/**
	 * Returns the ID of the record in the SDR repository.
	 *
	 * @return the record ID
	 */
	public int recordId() {
		return recordId;
	}

	/**
	 * Returns the record type (IPMI 2.0 table 43-1).
	 *
	 * @return the record type byte, 0 to 255, or -1 when the header of the record could not be read
	 */
	public int recordType() {
		return recordType;
	}

	/**
	 * Returns why the record was skipped.
	 *
	 * @return the reason
	 */
	public String reason() {
		return reason;
	}

	@Override
	public String toString() {
		return String.format("SDR record %04Xh", recordId)
				+ (recordType < 0 ? "" : String.format(" (type %02Xh)", recordType)) + ": " + reason;
	}
}
