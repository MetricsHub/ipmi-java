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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.metricshub.ipmi.core.coding.commands.sel.GetSelEntry;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelEntryResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelInfo;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelTimeUtcOffset;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelTimeUtcOffsetResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.ReserveSel;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;

/**
 * Reads the System Event Log entry by entry (IPMI 2.0 section 31): Get SEL Time UTC Offset, Get SEL Info, then Get
 * SEL Entry from the first record ID (0000h) or the one given, following the next record IDs until FFFFh, and Get
 * SEL Info again to see whether the log was erased meanwhile. {@link #read} does it all at once; a stream opens the
 * reader with {@link #open} and reads one entry per {@link #next} call.
 */
final class SelReader {

	/** First entry (IPMI 2.0 section 31.5). */
	static final int FIRST_RECORD_ID = 0;

	/** Given as the next record ID by the last entry. */
	static final int LAST_RECORD_ID = 0xFFFF;

	/** The completion code of Get SEL Entry while a Clear SEL is in progress (IPMI 2.0 section 31.5). */
	static final int SEL_ERASE_IN_PROGRESS = 0x81;

	/** How long to wait for a Clear SEL in progress before asking again, and how many times. */
	private static final long ERASE_WAIT_MS = 500;
	private static final int ERASE_WAITS = 20;
	private static final long MS_PER_SECOND = 1000;

	/** The session to read from: a new one after a lost session, given with each {@link #next}. */
	private Exchange exchange;
	/** 0 until the BMC asks for a reservation: a whole entry is read without one (IPMI 2.0 section 31.5). */
	private int reservationId;
	private int utcOffsetMinutes;
	/**
	 * The entry given to since() when the log still holds it, else the first entry the walk read: read again after the
	 * walk.
	 */
	private SelEntry anchor;
	/** Get SEL Info before the walk, the baseline, and after it. */
	private GetSelInfoResponseData before;
	private GetSelInfoResponseData after;
	/** The record ID to read next, {@link #LAST_RECORD_ID} once the last entry is read. */
	private int next = LAST_RECORD_ID;
	private final Set<Integer> read = new HashSet<Integer>();
	private int previous;
	/** Whether the checks of the end of the walk were done. */
	private boolean finished;
	/** Whether the log changed under the walk: an entry named as the next one is gone, or the log was erased. */
	private boolean changed;

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
		SelReader reader = open(exchange, since, sinceEntry);
		List<SelEntry> entries = reader.all();
		if (reader.changed) {
			// The log changed under the walk (an entry deleted, the log cleared): read it again, once, as it is now, and
			// whole: the entry after which to read may be another one by now
			reader.restart();
			entries = reader.all();
			if (reader.changed) {
				throw new IpmiClientException(
						"The System Event Log of " + host + " changed twice while it was read: it was erased, or an entry"
								+ " named as the next one is not there any more");
			}
		}
		return new SelEntryList(entries, reader.after, reader.utcOffsetMinutes);
	}

	/**
	 * Starts a read: Get SEL Time UTC Offset, Get SEL Info, and the entry given to since(), to find the first entry to
	 * read.
	 *
	 * @param exchange the session to read from
	 * @param since the record ID of the entry after which to read, -1 for the whole log
	 * @param sinceEntry the entry after which to read, null when the ID alone is given or for the whole log
	 * @return the reader, positioned on the first entry to read
	 * @throws IpmiClientException when the BMC does not answer, or refuses a command the read cannot do without
	 */
	static SelReader open(Exchange exchange, int since, SelEntry sinceEntry) {
		SelReader reader = new SelReader(exchange);
		reader.utcOffsetMinutes = reader.utcOffset();
		reader.before = reader.info();
		reader.locate(since, sinceEntry);
		return reader;
	}

	/** Starts the walk again, whole, with the Get SEL Info read after the first walk as the baseline. */
	private void restart() {
		before = after;
		after = null;
		anchor = null;
		read.clear();
		finished = false;
		changed = false;
		locate(-1, null);
	}

	/** Finds the first entry to read. */
	private void locate(int since, SelEntry sinceEntry) {
		int sinceId = sinceEntry != null ? sinceEntry.id() : since;
		previous = sinceId;
		if (before != null && before.getEntriesCount() == 0) {
			// An empty log is not walked: Get SEL Entry fails on it
			return;
		}
		next = FIRST_RECORD_ID;
		if (sinceId >= 0) {
			// The entry read last gives the next one. When the log does not hold it any more, or holds another entry
			// under its ID (the log was cleared and its IDs started over), the whole log is read
			GetSelEntryResponseData last = entry(sinceId);
			if (last != null
					&& last.getSelRecord().getRecordId() == sinceId
					&& (sinceEntry == null || sinceEntry.equals(new SelEntry(last.getSelRecord())))) {
				next = last.getNextRecordId();
				anchor = new SelEntry(last.getSelRecord());
			}
		}
	}

	/** Tells, without a command, whether the last entry was read. */
	boolean exhausted() {
		return next == LAST_RECORD_ID;
	}

	/**
	 * Tells whether the log changed under the walk (an entry named as the next one is not there any more, the log was
	 * erased): known once {@link #next} returned null or the last entry.
	 */
	boolean changed() {
		return changed;
	}

	private List<SelEntry> all() {
		List<SelEntry> entries = new ArrayList<SelEntry>();
		for (SelEntry entry = next(exchange); entry != null; entry = next(exchange)) {
			entries.add(entry);
		}
		return entries;
	}

	/**
	 * Reads the next entry. The checks of the end of the walk (the anchor read again, Get SEL Info) are part of the
	 * read of the last entry, so that the end of a stream costs no further operation, and of the first call on an
	 * empty log. A read that fails leaves its entry as the next one.
	 *
	 * @param session the session to read from
	 * @return the entry, null after the last one, or when the log changed under the walk ({@link #changed()})
	 * @throws IpmiClientException when the BMC does not answer, refuses the entry, or returns a malformed one
	 */
	SelEntry next(Exchange session) {
		this.exchange = session;
		if (next == LAST_RECORD_ID) {
			if (!finished) {
				finish();
			}
			return null;
		}
		GetSelEntryResponseData reply = entry(next);
		if (reply == null) {
			// Nothing at the first record: an empty log (of a BMC that refuses Get SEL Info, or cleared just now).
			// Nothing at a record another entry named as the next one: the log changed under the walk
			changed = next != FIRST_RECORD_ID;
			next = LAST_RECORD_ID;
			finish();
			return null;
		}
		SelEntry entry = new SelEntry(reply.getSelRecord(), utcOffsetMinutes);
		if (entry.id() <= FIRST_RECORD_ID || entry.id() >= LAST_RECORD_ID) {
			// 0000h and FFFFh are selectors, not the IDs of entries (IPMI 2.0 section 31.5 and table 32-1)
			throw new IpmiClientException(
					String.format("The System Event Log answered an entry whose record ID is %04Xh", entry.id()));
		}
		if (next != FIRST_RECORD_ID && entry.id() != next) {
			// A malformed reply: following it would skip the entry asked for
			throw new IpmiClientException(
					String.format("The System Event Log answered entry %04Xh when asked for entry %04Xh", entry.id(), next));
		}
		int following = reply.getNextRecordId();
		boolean self = following == entry.id();
		if (read.contains(entry.id()) || self) {
			// A BMC whose next record IDs loop would be read for ever
			throw new IpmiClientException(
					String.format(
							"The System Event Log loops: entry %04Xh names entry %04Xh as the next one, which was read already",
							self ? entry.id() : previous,
							entry.id()));
		}
		if (anchor == null) {
			anchor = entry;
		}
		if (following == LAST_RECORD_ID) {
			finish();
		}
		// The caller gave up at its deadline: the entry is read again by the next operation of a stream
		IpmiClient.failIfInterrupted("SEL entry " + entry.id());
		read.add(entry.id());
		previous = entry.id();
		next = following;
		return entry;
	}

	/**
	 * Ends the walk: the anchor is read again, then Get SEL Info, so that a change seen by either leaves the info read
	 * after it as the baseline of a second walk.
	 */
	private void finish() {
		finished = true;
		// A change known already (an entry gone) needs no confirmation by the anchor
		changed = changed || anchorChanged();
		after = info();
		changed |= erased(before, after);
	}

	/**
	 * Tells whether the log was erased, in whole or in part, between two Get SEL Info: its IDs may have been reused
	 * meanwhile, so what the walk read may mix two logs.
	 */
	private static boolean erased(GetSelInfoResponseData before, GetSelInfoResponseData after) {
		return before != null && after != null && !Objects.equals(before.getEraseTimestamp(), after.getEraseTimestamp());
	}

	/**
	 * Tells whether the anchor of the walk (the entry given to since() when the log still holds it, else the first
	 * entry the walk read) is still in the log, byte for byte: a log cleared and filled again while it was walked holds
	 * another entry under its ID, even when two erases within a second left the erase timestamp unchanged.
	 */
	private boolean anchorChanged() {
		if (anchor == null) {
			return false;
		}
		GetSelEntryResponseData now = entry(anchor.id());
		return now == null || !anchor.equals(new SelEntry(now.getSelRecord()));
	}

	private GetSelInfoResponseData info() {
		int erasing = 0;
		while (true) {
			try {
				return exchange.send(new GetSelInfo());
			} catch (IpmiCompletionCodeException e) {
				if (e.completionCode() == CompletionCode.InvalidCommand
						|| e.completionCode() == CompletionCode.CommandNotSupported) {
					// A BMC without Get SEL Info: an empty log then answers "not present" to the first Get SEL Entry
					return null;
				}
				if (e.rawCompletionCode() != SEL_ERASE_IN_PROGRESS) {
					// A refusal the caller can act on (privilege, busy): without the erase timestamp, a log cleared
					// under the walk would go unnoticed
					throw e;
				}
				erasing = waitForErase(erasing, e);
			}
		}
	}

	/**
	 * Waits for the end of a Clear SEL in progress, once more.
	 *
	 * @param waits how many times the end was waited for already
	 * @return the new count
	 * @throws IpmiClientException when the Clear SEL takes too long
	 */
	private static int waitForErase(int waits, IpmiCompletionCodeException e) {
		if (waits >= ERASE_WAITS) {
			throw new IpmiClientException(
					"The System Event Log is still being erased after " + ERASE_WAITS * ERASE_WAIT_MS / MS_PER_SECOND + " s",
					e);
		}
		pause();
		return waits + 1;
	}

	private int utcOffset() {
		try {
			GetSelTimeUtcOffsetResponseData offset = exchange.send(new GetSelTimeUtcOffset());
			// 07FFh, unspecified: the SEL time is taken as UTC, the best there is
			return offset.isUnspecified() ? 0 : offset.getOffsetMinutes();
		} catch (IpmiCompletionCodeException e) {
			// The SEL time of a BMC without the command (IPMI 2.0 revision 1.1) is taken as UTC. Any other refusal
			// (privilege, busy) is the caller's to act on: the timestamps would be off by an unknown offset
			if (e.completionCode() == CompletionCode.InvalidCommand
					|| e.completionCode() == CompletionCode.CommandNotSupported) {
				return 0;
			}
			throw e;
		}
	}

	/**
	 * Reads one entry whole.
	 *
	 * @return the reply, or null when the BMC does not have the entry
	 */
	private GetSelEntryResponseData entry(int recordId) {
		boolean reserved = false;
		int erasing = 0;
		while (true) {
			try {
				return exchange.send(new GetSelEntry(reservationId, recordId));
			} catch (IpmiCompletionCodeException e) {
				if (e.completionCode() == CompletionCode.DataNotPresent) {
					return null;
				}
				if (e.rawCompletionCode() == SEL_ERASE_IN_PROGRESS) {
					// A Clear SEL is in progress: wait for its end, within the deadline of the operation
					erasing = waitForErase(erasing, e);
					continue;
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

	private static void pause() {
		try {
			Thread.sleep(ERASE_WAIT_MS);
		} catch (InterruptedException e) {
			// The caller gave up at its deadline
			Thread.currentThread().interrupt();
			throw new IpmiClientException("Interrupted while waiting for the end of a Clear SEL", e);
		}
	}
}
