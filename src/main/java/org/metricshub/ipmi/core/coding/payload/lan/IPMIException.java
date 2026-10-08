package org.metricshub.ipmi.core.coding.payload.lan;

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

import org.metricshub.ipmi.core.coding.payload.CompletionCode;

public class IPMIException extends Exception {

	private static final long serialVersionUID = 1L;

	private static final int OEM_CODES_START = 0x80;

	private static final int GENERIC_CODES_START = 0xC0;

	private final CompletionCode completionCode;

	private final int rawCode;

	public IPMIException(CompletionCode completionCode) {
		this(completionCode, completionCode.getCode());
	}

	/**
	 * @param completionCode the decoded completion code, {@link CompletionCode#Unknown} for a code the library
	 *        does not list
	 * @param rawCode the completion code byte as the BMC sent it
	 */
	public IPMIException(CompletionCode completionCode, int rawCode) {
		this.completionCode = completionCode;
		this.rawCode = rawCode;
	}

	public CompletionCode getCompletionCode() {
		return completionCode;
	}

	/**
	 * @return the completion code byte as the BMC sent it, meaningful when {@link #getCompletionCode()} is
	 *         {@link CompletionCode#Unknown}
	 */
	public int getRawCode() {
		return rawCode;
	}

	@Override
	public String getMessage() {
		if (completionCode == CompletionCode.Unknown && rawCode >= 0) {
			String kind = rawCode < OEM_CODES_START ? "Command-specific" : rawCode < GENERIC_CODES_START ? "OEM" : "Reserved";
			return String.format("%s completion code 0x%02X.", kind, rawCode);
		}
		return completionCode.getMessage();
	}
}
