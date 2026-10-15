package org.metricshub.ipmi.core.coding.commands.sdr;

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

import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;

/**
 * Get Device SDR (IPMI 2.0 section 35.3): reads a record of the SDRs a Sensor Device holds itself, for a controller
 * without an SDR Repository. The request and the response are those of {@link GetSdr}.
 */
public class GetDeviceSdr extends GetSdr {

	/** Record changed (table 35-3): a record was altered since the read of its first bytes. */
	private static final int RECORD_CHANGED = 0x80;

	/**
	 * Creates the command that reads a whole record.
	 *
	 * @param reservationId the reservation ID from {@link ReserveDeviceSdrRepository}, only needed for a partial read
	 * @param recordId the ID of the record, 0 for the first one
	 */
	public GetDeviceSdr(int reservationId, int recordId) {
		super(reservationId, recordId);
	}

	/**
	 * Creates the command that reads part of a record.
	 *
	 * @param reservationId the reservation ID from {@link ReserveDeviceSdrRepository}
	 * @param recordId the ID of the record, 0 for the first one
	 * @param offset the offset of the first byte to read in the record
	 * @param bytesToRead the number of bytes to read, FFh for the whole record
	 */
	public GetDeviceSdr(int reservationId, int recordId, int offset, int bytesToRead) {
		super(reservationId, recordId, offset, bytesToRead);
	}

	@Override
	public byte getCommandCode() {
		return 0x21;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.SensorRequest;
	}

	@Override
	protected CompletionCode decodeCommandSpecificCompletionCode(int rawCode) {
		// A changed record calls for a new reservation and a new read, as a canceled reservation of Get SDR does
		return rawCode == RECORD_CHANGED ? CompletionCode.ReservationCanceled : CompletionCode.Unknown;
	}
}
