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

import java.time.Duration;
import java.util.Objects;

/**
 * Reads the System Event Log: {@code bmc.sel().execute()} for every entry, {@code bmc.sel().since(entry).execute()}
 * for the entries added after one already read.
 */
public final class SelRequest {

	/**
	 * The first record ID of the log (0000h) and the last (FFFFh) are not IDs of entries (IPMI 2.0 section 31.5 and
	 * table 32-1).
	 */
	private static final int LAST_ENTRY_ID = 0xFFFE;

	private final IpmiClient client;
	private int since = -1;
	private SelEntry sinceEntry;
	private Duration timeout;

	SelRequest(IpmiClient client) {
		this.client = client;
	}

	/**
	 * Reads only the entries added after the given one, the last entry of a previous read: it is read again to find
	 * the next one and left out. When the log holds another entry under its ID, or none (the log was cleared since,
	 * and its IDs started over), the whole log is read.
	 *
	 * @param entry the last entry already read
	 * @return this request
	 */
	public SelRequest since(SelEntry entry) {
		this.sinceEntry = Objects.requireNonNull(entry, "entry");
		this.since = -1;
		return this;
	}

	/**
	 * Reads only the entries added after the one with the given record ID: the {@link SelEntry#id() ID} of the last
	 * entry of a previous read, for a cursor kept without the entry itself (across restarts, for instance). When the
	 * log no longer holds that ID (it was cleared), the whole log is read. A log cleared and filled again up to that ID
	 * is not detected: compare {@link SelEntryList#lastErase()} with that of the previous read, or use
	 * {@link #since(SelEntry)}, which detects it.
	 *
	 * @param recordId the record ID of the last entry already read, 1 to 65534
	 * @return this request
	 * @throws IllegalArgumentException when the record ID is not between 1 and 65534
	 */
	public SelRequest since(int recordId) {
		if (recordId < 1 || recordId > LAST_ENTRY_ID) {
			throw new IllegalArgumentException("A SEL record ID is between 1 and " + LAST_ENTRY_ID + ": " + recordId);
		}
		this.since = recordId;
		this.sinceEntry = null;
		return this;
	}

	/**
	 * Overrides the deadline of the client for this request.
	 *
	 * @param deadline the time the request may take, session setup included
	 * @return this request
	 * @throws IllegalArgumentException when the duration is shorter than one millisecond or longer than
	 *         {@link Integer#MAX_VALUE} milliseconds
	 */
	public SelRequest timeout(Duration deadline) {
		this.timeout = IpmiClient.checkTimeout(deadline, "timeout");
		return this;
	}

	/**
	 * Reads the entries: Get SEL Time UTC Offset, Get SEL Info, then one Get SEL Entry per entry, each read whole (so
	 * without a reservation, IPMI 2.0 section 31.5; one is taken when the BMC asks for it). The first entry read (the
	 * one given to {@code since()}, when the log still holds it) is then read again and Get SEL Info sent again, to
	 * tell whether the log changed during the read. A log that changes while it is read (an entry deleted, the log
	 * cleared) is read again from the start, once, and whole: the entry given to {@code since()} may be another one
	 * by then.
	 *
	 * @return the entries, in the order of the log
	 * @throws IpmiClientException when the log cannot be read: the BMC does not answer, refuses an entry, returns a
	 *         malformed one, or changed the log twice during the read
	 */
	public SelEntryList execute() {
		return client.readSel(since, sinceEntry, timeout);
	}
}
