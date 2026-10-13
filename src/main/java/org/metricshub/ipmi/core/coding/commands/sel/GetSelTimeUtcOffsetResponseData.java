package org.metricshub.ipmi.core.coding.commands.sel;

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

import org.metricshub.ipmi.core.coding.commands.ResponseData;

/**
 * Wrapper for the Get SEL Time UTC Offset response.
 */
public class GetSelTimeUtcOffsetResponseData implements ResponseData {

	/** 07FFh: the offset is unspecified (IPMI 2.0 revision 1.1 section 31.11a), as after a Set SEL Time. */
	public static final int UNSPECIFIED = 0x07ff;

	private int offsetMinutes;

	/**
	 * @return the offset from UTC of the SEL time, in minutes (-1440 to 1440): local time = UTC + offset; or
	 *         {@link #UNSPECIFIED}
	 */
	public int getOffsetMinutes() {
		return offsetMinutes;
	}

	/**
	 * @return whether the BMC leaves the offset unspecified (07FFh): the SEL time is then as good as UTC
	 */
	public boolean isUnspecified() {
		return offsetMinutes == UNSPECIFIED;
	}

	/**
	 * @param offsetMinutes the offset from UTC of the SEL time, in minutes
	 */
	public void setOffsetMinutes(int offsetMinutes) {
		this.offsetMinutes = offsetMinutes;
	}
}
