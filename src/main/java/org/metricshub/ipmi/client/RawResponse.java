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

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;

/**
 * The reply to a raw request ({@link IpmiClient#raw(int, int)}): its completion code and its data as the BMC sent
 * them, whatever the completion code.
 */
public final class RawResponse implements ResponseData {

	private final CompletionCode completionCode;
	private final int rawCompletionCode;
	private final byte[] data;

	RawResponse(CompletionCode completionCode, int rawCompletionCode, byte[] data) {
		this.completionCode = completionCode;
		this.rawCompletionCode = rawCompletionCode;
		this.data = data == null ? new byte[0] : data.clone();
	}

	/**
	 * Returns the completion code: {@link CompletionCode#Ok} when the BMC carried the request out,
	 * {@link CompletionCode#Unknown} for an OEM (01h-7Eh) or command-specific (80h-BEh) code, whose meaning only the
	 * command defines (see {@link #rawCompletionCode()}).
	 *
	 * @return the completion code
	 */
	public CompletionCode completionCode() {
		return completionCode;
	}

	/**
	 * Returns the completion code byte as the BMC sent it.
	 *
	 * @return the completion code, 0 to 255
	 */
	public int rawCompletionCode() {
		return rawCompletionCode;
	}

	/**
	 * Returns the response data after the completion code, as the BMC sent it. Under the Group Extension network
	 * function (2Ch) the first byte is the defining body (DCh for DCMI), under OEM/Group (2Eh) the first three are an
	 * IANA enterprise number (IPMI 2.0 table 5-1). After an error completion code, a BMC usually sends no data, or
	 * only these bytes.
	 *
	 * @return a copy of the data, empty when the reply has none
	 */
	public byte[] data() {
		return data.clone();
	}

	@Override
	public String toString() {
		return String.format("completion code %02Xh", rawCompletionCode)
				+ (data.length == 0 ? "" : ", data " + SelEntry.hex(data));
	}
}
