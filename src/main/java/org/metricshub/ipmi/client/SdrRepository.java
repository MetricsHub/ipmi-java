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

import java.util.Collections;
import java.util.List;

import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrRepositoryInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;

/**
 * The SDR repository as read by one walk: its records, the records skipped, and the Get SDR Repository Info reply
 * that identifies this version of the repository (IPMI 2.0 section 33.9).
 */
final class SdrRepository {

	private final List<SensorRecord> records;
	private final List<SkippedRecord> skippedRecords;
	private final GetSdrRepositoryInfoResponseData info;

	SdrRepository(List<SensorRecord> records, List<SkippedRecord> skippedRecords, GetSdrRepositoryInfoResponseData info) {
		this.records = Collections.unmodifiableList(records);
		this.skippedRecords = Collections.unmodifiableList(skippedRecords);
		this.info = info;
	}

	List<SensorRecord> records() {
		return records;
	}

	List<SkippedRecord> skippedRecords() {
		return skippedRecords;
	}

	/**
	 * Tells whether the repository is still the one that was walked: same record count and same timestamps of the
	 * most recent addition and erase.
	 *
	 * @param current the current Get SDR Repository Info reply, null when the BMC does not answer the command
	 * @return whether the records can be reused
	 */
	boolean isCurrent(GetSdrRepositoryInfoResponseData current) {
		return info != null
				&& current != null
				&& info.getRecordCount() == current.getRecordCount()
				&& info.getAddTimestamp() == current.getAddTimestamp()
				&& info.getDelTimestamp() == current.getDelTimestamp();
	}
}
