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

import java.time.Instant;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.metricshub.ipmi.core.coding.commands.sel.GetSelInfoResponseData;

/**
 * The entries read from the System Event Log by {@link SelRequest#execute()}, in the order of the log, with what
 * Get SEL Info says of the whole log: how many entries it holds, and when it was last written and erased.
 */
public final class SelEntryList extends AbstractList<SelEntry> {

	private final List<SelEntry> entries;
	private final GetSelInfoResponseData info;
	private final int utcOffsetMinutes;

	/**
	 * Creates the list.
	 *
	 * @param entries the entries, in the order of the log
	 * @param info the Get SEL Info reply, null when the BMC refused the command
	 * @param utcOffsetMinutes the offset from UTC of the SEL time, 0 when the BMC keeps it in UTC
	 */
	SelEntryList(List<SelEntry> entries, GetSelInfoResponseData info, int utcOffsetMinutes) {
		this.entries = Collections.unmodifiableList(new ArrayList<SelEntry>(entries));
		this.info = info;
		this.utcOffsetMinutes = utcOffsetMinutes;
	}

	/**
	 * Returns the offset from UTC of the SEL time, which a BMC that keeps its SEL time as local time reports with Get SEL
	 * Time UTC Offset (IPMI 2.0 revision 1.1): already applied to every timestamp of the list and its entries. 0 for a
	 * BMC without the command, or that leaves the offset unspecified (07FFh), whose SEL time is then taken as UTC. It is
	 * the offset in force when the log was read: the log records none per entry, so an entry logged under another offset
	 * (before a daylight saving change) comes out shifted by the difference.
	 *
	 * @return the offset in minutes, local time = UTC + offset
	 */
	public int utcOffsetMinutes() {
		return utcOffsetMinutes;
	}

	@Override
	public SelEntry get(int index) {
		return entries.get(index);
	}

	@Override
	public int size() {
		return entries.size();
	}

	/**
	 * Returns how many entries the whole log holds, which is more than {@link #size()} after
	 * {@link SelRequest#since(int)}.
	 *
	 * @return the number of entries of the log, empty when the BMC refused Get SEL Info
	 */
	public OptionalInt totalEntries() {
		return info == null ? OptionalInt.empty() : OptionalInt.of(info.getEntriesCount());
	}

	/**
	 * Returns when an entry was last added to the log.
	 *
	 * @return the time of the most recent addition, empty when the BMC refused Get SEL Info or reports none
	 */
	public Optional<Instant> lastAddition() {
		return info == null ? Optional.<Instant>empty() : SelEntry.absolute(info.getAdditionTimestamp(), utcOffsetMinutes);
	}

	/**
	 * Returns when entries were last deleted or the log cleared (IPMI 2.0 table 31-2): the BMC may then give the
	 * {@link SelEntry#id() record ID} of an entry read before to another entry.
	 *
	 * @return the time of the most recent erase, empty when the BMC refused Get SEL Info or reports none
	 */
	public Optional<Instant> lastErase() {
		return info == null ? Optional.<Instant>empty() : SelEntry.absolute(info.getEraseTimestamp(), utcOffsetMinutes);
	}

	/**
	 * Returns the Get SEL Info reply of the protocol core, for everything else.
	 *
	 * @return the reply, empty when the BMC refused the command
	 */
	public Optional<GetSelInfoResponseData> info() {
		return Optional.ofNullable(info);
	}

	@Override
	public String toString() {
		return entries.size() + " SEL entr" + (entries.size() == 1 ? "y" : "ies")
				+ (info == null ? "" : " of " + info.getEntriesCount()) + ": " + entries;
	}
}
