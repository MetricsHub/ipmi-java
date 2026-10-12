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

import java.util.ArrayList;
import java.util.List;

import org.metricshub.ipmi.core.coding.commands.sdr.GetSdr;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrRepositoryInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepository;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Walks the SDR repository (IPMI 2.0 section 33.12): reads every record, whole when the BMC allows it and in chunks
 * otherwise, and decodes it. A record the BMC returns truncated or that the library cannot decode is skipped and
 * reported, never fatal.
 */
final class SdrWalker {

	/**
	 * Last Record ID (FFFFh): the walk ends when the BMC gives it as the next record ID.
	 */
	static final int LAST_RECORD_ID = 0xFFFF;

	/** Size of the first Get SDR of a chunked read: the 5-byte header and the first bytes of the record. */
	private static final int INITIAL_CHUNK_SIZE = 8;

	/**
	 * Size of the chunks of a chunked read: BMCs that cannot return a whole record answer "Cannot return number of
	 * requested data bytes" above this.
	 */
	private static final int CHUNK_SIZE = 16;

	/** Size of the SDR record header; its last byte is the length of the rest of the record. */
	private static final int HEADER_SIZE = 5;

	private final Exchange exchange;
	private final List<SensorRecord> records = new ArrayList<SensorRecord>();
	private final List<SkippedRecord> skipped = new ArrayList<SkippedRecord>();
	private int nextRecordId;
	private int reservationId;
	private int lastReservationId = -1;

	private SdrWalker(Exchange exchange) {
		this.exchange = exchange;
	}

	/**
	 * Reads the whole repository.
	 *
	 * @param exchange the session to read it from
	 * @param info the Get SDR Repository Info reply read just before, null when the BMC does not answer the command
	 * @return the repository
	 * @throws IpmiClientException when a record cannot be read at all: the BMC does not answer or refuses the read
	 */
	static SdrRepository walk(Exchange exchange, GetSdrRepositoryInfoResponseData info) {
		SdrWalker walker = new SdrWalker(exchange);
		walker.walk();
		return new SdrRepository(walker.records, walker.skipped, info);
	}

	private void walk() {
		// Record ID 0 is the first record; the walk follows the next record IDs, as there is no command listing them.
		// Some BMCs allow reading the records without a reservation, so none is taken until the BMC asks for one.
		while (nextRecordId < LAST_RECORD_ID) {
			try {
				SensorRecord record = read();
				if (record != null) {
					records.add(record);
				}
			} catch (IpmiCompletionCodeException e) {
				// The BMC canceled the reservation (it does so when another session appears): take a new one and read
				// the record again, once
				if (lastReservationId == reservationId || e.completionCode() != CompletionCode.ReservationCanceled) {
					throw e;
				}
				lastReservationId = reservationId;
				reservationId = exchange.send(new ReserveSdrRepository()).getReservationId();
			}
		}
	}

	/**
	 * Reads the next record, whole first, in chunks when the BMC cannot return it whole or returns fewer bytes than
	 * the record declares, and advances to the next record ID.
	 */
	private SensorRecord read() {
		int recordId = nextRecordId;
		GetSdrResponseData reply;
		try {
			reply = exchange.send(new GetSdr(reservationId, recordId));
		} catch (IpmiCompletionCodeException e) {
			// The record is too large for one reply
			if (e.completionCode() != CompletionCode.CannotRespond && e.completionCode() != CompletionCode.UnspecifiedError) {
				throw e;
			}
			return readInChunks(recordId);
		}
		if (isTruncated(reply.getSensorRecordData())) {
			return readInChunks(recordId);
		}
		// Advance first, so that a record that cannot be decoded never stalls the walk
		nextRecordId = reply.getNextRecordId();
		return decode(recordId, reply.getSensorRecordData());
	}

	private SensorRecord readInChunks(int recordId) {
		GetSdrResponseData first = exchange.send(new GetSdr(reservationId, recordId, 0, INITIAL_CHUNK_SIZE));
		nextRecordId = first.getNextRecordId();
		byte[] header = first.getSensorRecordData();
		if (header == null || header.length < HEADER_SIZE) {
			skipped
					.add(
							new SkippedRecord(
									recordId,
									-1,
									"The BMC returned " + (header == null ? 0 : header.length) + " byte(s) instead of the "
											+ HEADER_SIZE + "-byte header"));
			return null;
		}
		int size = TypeConverter.byteToInt(header[HEADER_SIZE - 1]) + HEADER_SIZE;
		byte[] bytes = new byte[size];
		int read = Math.min(header.length, size);
		System.arraycopy(header, 0, bytes, 0, read);
		while (read < size) {
			int toRead = Math.min(CHUNK_SIZE, size - read);
			byte[] chunk = exchange.send(new GetSdr(reservationId, recordId, read, toRead)).getSensorRecordData();
			int got = chunk == null ? 0 : Math.min(toRead, chunk.length);
			if (got == 0) {
				skipped
						.add(
								new SkippedRecord(
										recordId,
										TypeConverter.byteToInt(header[3]),
										"The BMC returned no data at offset " + read + " of " + size));
				return null;
			}
			System.arraycopy(chunk, 0, bytes, read, got);
			read += got;
		}
		return decode(recordId, bytes);
	}

	private SensorRecord decode(int recordId, byte[] data) {
		try {
			return SensorRecord.populateSensorRecord(data);
		} catch (RuntimeException e) {
			skipped
					.add(
							new SkippedRecord(
									recordId,
									data.length > 3 ? TypeConverter.byteToInt(data[3]) : -1,
									"Cannot decode the record: " + e.getMessage()));
			return null;
		}
	}

	/**
	 * Tells whether a whole-record reply is shorter than the length its own header declares: some BMCs answer with a
	 * success completion code and a truncated body instead of "Cannot return number of requested data bytes".
	 */
	static boolean isTruncated(byte[] data) {
		return data == null
				|| data.length < HEADER_SIZE
				|| data.length < TypeConverter.byteToInt(data[HEADER_SIZE - 1]) + HEADER_SIZE;
	}
}
