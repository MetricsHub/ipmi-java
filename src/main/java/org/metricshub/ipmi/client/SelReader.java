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

import org.metricshub.ipmi.core.coding.commands.sel.GetSelEntry;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelEntryResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelInfo;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelTimeUtcOffset;
import org.metricshub.ipmi.core.coding.commands.sel.ReserveSel;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;

/**
 * Reads the System Event Log entry by entry (IPMI 2.0 section 31): Get SEL Info, Get SEL Time UTC Offset, then Get
 * SEL Entry from the first record ID (0000h) or the one given, following the next record IDs until FFFFh.
 */
final class SelReader {

	/** First entry (IPMI 2.0 section 31.6). */
	static final int FIRST_RECORD_ID = 0;

	/** Given as the next record ID by the last entry. */
	static final int LAST_RECORD_ID = 0xFFFF;

	private final Exchange exchange;
	/** 0 until the BMC asks for a reservation: a whole entry is read without one (IPMI 2.0 section 31.5). */
	private int reservationId;
	private int utcOffsetMinutes;

	private SelReader(Exchange exchange) {
		this.exchange = exchange;
	}

	/**
	 * Reads the log.
	 *
	 * @param exchange the session to read it from
	 * @param host the BMC, for the messages
	 * @param since the record ID of the entry after which to read, -1 for the whole log
	 * @param sinceEntry the entry after which to read, null when the ID alone is given or for the whole log
	 * @return the entries
	 * @throws IpmiClientException when an entry cannot be read at all: the BMC does not answer, refuses it, returns
	 *         a malformed one, or keeps changing the log while it is read
	 */
	static SelEntryList read(Exchange exchange, String host, int since, SelEntry sinceEntry) {
		SelReader reader = new SelReader(exchange);
		reader.utcOffsetMinutes = reader.utcOffset();
		GetSelInfoResponseData info = reader.info();
		List<SelEntry> entries = reader.walk(since, sinceEntry, info);
		if (entries == null) {
			// The log changed under the walk (an entry deleted, the log cleared): read it again, once, as it is now
			info = reader.info();
			entries = reader.walk(since, sinceEntry, info);
			if (entries == null) {
				throw new IpmiClientException(
						"The System Event Log of " + host
								+ " changed twice while it was read: an entry named as the next one is not there any more");
			}
		}
		return new SelEntryList(entries, info, reader.utcOffsetMinutes);
	}

	private GetSelInfoResponseData info() {
		try {
			return exchange.send(new GetSelInfo());
		} catch (IpmiCompletionCodeException e) {
			// A BMC that refuses Get SEL Info: an empty log then answers "not present" to the first Get SEL Entry
			return null;
		}
	}

	private int utcOffset() {
		try {
			return exchange.send(new GetSelTimeUtcOffset()).getOffsetMinutes();
		} catch (IpmiCompletionCodeException e) {
			// A BMC without the command (IPMI 2.0 revision 1.1) keeps its SEL time in UTC
			return 0;
		}
	}

	/**
	 * Walks the log.
	 *
	 * @return the entries, or null when an entry named as the next one is not there any more: the log changed under
	 *         the walk
	 */
	private List<SelEntry> walk(int since, SelEntry sinceEntry, GetSelInfoResponseData info) {
		List<SelEntry> entries = new ArrayList<SelEntry>();
		if (info != null && info.getEntriesCount() == 0) {
			return entries;
		}
		int next = FIRST_RECORD_ID;
		int sinceId = sinceEntry != null ? sinceEntry.id() : since;
		if (sinceId >= 0) {
			// The entry read last gives the next one. When the log does not hold it any more, or holds another entry
			// under its ID (the log was cleared and its IDs started over), the whole log is read
			GetSelEntryResponseData last = entry(sinceId);
			if (last != null && (sinceEntry == null || sinceEntry.equals(new SelEntry(last.getSelRecord())))) {
				next = last.getNextRecordId();
			}
		}
		while (next != LAST_RECORD_ID) {
			GetSelEntryResponseData reply = entry(next);
			if (reply == null) {
				// Nothing at the first record: an empty log (of a BMC that refuses Get SEL Info, or cleared just now).
				// Nothing at a record another entry named as the next one: the log changed under the walk
				return next == FIRST_RECORD_ID && entries.isEmpty() ? entries : null;
			}
			entries.add(new SelEntry(reply.getSelRecord(), utcOffsetMinutes));
			next = reply.getNextRecordId();
		}
		return entries;
	}

	/**
	 * Reads one entry whole.
	 *
	 * @return the reply, or null when the BMC does not have the entry
	 */
	private GetSelEntryResponseData entry(int recordId) {
		boolean reserved = false;
		while (true) {
			try {
				return exchange.send(new GetSelEntry(reservationId, recordId));
			} catch (IpmiCompletionCodeException e) {
				if (e.completionCode() == CompletionCode.DataNotPresent) {
					return null;
				}
				// A BMC that wants a reservation for a whole entry, or canceled the one it gave (it does so when another
				// session appears): take a new one and read the entry again, once
				if (e.completionCode() != CompletionCode.ReservationCanceled || reserved) {
					throw e;
				}
				reserved = true;
				reservationId = exchange.send(new ReserveSel()).getReservationId();
			}
		}
	}
}
