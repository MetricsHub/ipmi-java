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
import org.metricshub.ipmi.core.coding.commands.sel.ReserveSel;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;

/**
 * Reads the System Event Log entry by entry (IPMI 2.0 section 31): Get SEL Info, then Get SEL Entry from the first
 * record ID (0000h) or the one given, following the next record IDs until FFFFh.
 */
final class SelReader {

	/** First entry (IPMI 2.0 section 31.6). */
	static final int FIRST_RECORD_ID = 0;

	/** Given as the next record ID by the last entry. */
	static final int LAST_RECORD_ID = 0xFFFF;

	private final Exchange exchange;
	/** 0 until the BMC asks for a reservation: a whole entry is read without one (IPMI 2.0 section 31.5). */
	private int reservationId;

	private SelReader(Exchange exchange) {
		this.exchange = exchange;
	}

	/**
	 * Reads the log.
	 *
	 * @param exchange the session to read it from
	 * @param since the record ID of the entry after which to read, or -1 for the whole log
	 * @return the entries
	 * @throws IpmiClientException when an entry cannot be read at all: the BMC does not answer, refuses it, or
	 *         returns a malformed one
	 */
	static SelEntryList read(Exchange exchange, int since) {
		GetSelInfoResponseData info;
		try {
			info = exchange.send(new GetSelInfo());
		} catch (IpmiCompletionCodeException e) {
			// A BMC that refuses Get SEL Info: an empty log then answers "not present" to the first Get SEL Entry
			info = null;
		}
		List<SelEntry> entries = new ArrayList<SelEntry>();
		if (info == null || info.getEntriesCount() > 0) {
			new SelReader(exchange).walk(since, entries);
		}
		return new SelEntryList(entries, info);
	}

	private void walk(int since, List<SelEntry> entries) {
		int next = FIRST_RECORD_ID;
		if (since >= 0) {
			// The entry read last gives the next one; when the log does not hold it any more, it was cleared
			GetSelEntryResponseData last = entry(since);
			next = last == null ? FIRST_RECORD_ID : last.getNextRecordId();
		}
		while (next != LAST_RECORD_ID) {
			GetSelEntryResponseData reply = entry(next);
			if (reply == null) {
				// An empty log, or one cleared while it was read: the entries read so far are what there is
				break;
			}
			entries.add(new SelEntry(reply.getSelRecord()));
			next = reply.getNextRecordId();
		}
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
