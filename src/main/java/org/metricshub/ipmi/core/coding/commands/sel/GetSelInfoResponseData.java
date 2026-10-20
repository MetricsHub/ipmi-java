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

import java.util.Date;

import org.metricshub.ipmi.core.coding.commands.ResponseData;

/**
 * The reply to Get SEL Info (IPMI 2.0 section 31.2): the version of the SEL, its number of entries and the times of
 * its last addition and erasure.
 */
public class GetSelInfoResponseData implements ResponseData {
	private int selVersion;

	private int entriesCount;

	/**
	 * Most recent addition timestamp.
	 */
	private Date additionTimestamp;

	/**
	 * Most recent erase timestamp.
	 */
	private Date eraseTimestamp;

	/**
	 * Returns the version of the SEL command set, BCD-decoded: 15 for 51h, version 1.5.
	 *
	 * @return the SEL version, 15 for version 1.5
	 */
	public int getSelVersion() {
		return selVersion;
	}

	/**
	 * Sets the version of the SEL command set.
	 *
	 * @param selVersion the SEL version, BCD-decoded (15 for version 1.5)
	 */
	public void setSelVersion(int selVersion) {
		this.selVersion = selVersion;
	}

	/**
	 * Returns the number of entries in the SEL.
	 *
	 * @return the number of entries
	 */
	public int getEntriesCount() {
		return entriesCount;
	}

	/**
	 * Sets the number of entries in the SEL.
	 *
	 * @param entriesCount the number of entries
	 */
	public void setEntriesCount(int entriesCount) {
		this.entriesCount = entriesCount;
	}

	/**
	 * Returns the time of the most recent addition to the SEL as the BMC sent it: a {@link Date} of the unsigned 32-bit
	 * number of seconds, with no UTC offset applied. FFFFFFFFh means that no entry was ever added, or that the BMC lost
	 * the value.
	 *
	 * @return the time of the most recent addition
	 */
	public Date getAdditionTimestamp() {
		return additionTimestamp;
	}

	/**
	 * Sets the time of the most recent addition to the SEL.
	 *
	 * @param additionTimestamp the time of the most recent addition, as the BMC sent it
	 */
	public void setAdditionTimestamp(Date additionTimestamp) {
		this.additionTimestamp = additionTimestamp;
	}

	/**
	 * Returns the time of the most recent erasure of SEL entries as the BMC sent it: a {@link Date} of the unsigned
	 * 32-bit number of seconds, with no UTC offset applied.
	 *
	 * @return the time of the most recent erasure
	 */
	public Date getEraseTimestamp() {
		return eraseTimestamp;
	}

	/**
	 * Sets the time of the most recent erasure of SEL entries.
	 *
	 * @param eraseTimestamp the time of the most recent erasure, as the BMC sent it
	 */
	public void setEraseTimestamp(Date eraseTimestamp) {
		this.eraseTimestamp = eraseTimestamp;
	}
}
