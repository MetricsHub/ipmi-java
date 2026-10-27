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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Reads the FRU inventories of the BMC: those that the FRU Device Locator records of the SDR repository point at,
 * and the one of the system board.
 */
public final class FrusRequest {

	private final IpmiClient client;
	private Set<Integer> ids;
	private Duration timeout;
	private Consumer<SkippedRecord> onSkippedRecord = record -> {};

	FrusRequest(IpmiClient client) {
		this.client = client;
	}

	/**
	 * Reads only the FRUs with the given device IDs (0 is the system board on most BMCs), on every controller and LUN:
	 * a device ID is unique per controller and LUN only. The SDR repository is read all the same. Without argument, it
	 * reads none. A later call replaces the IDs.
	 *
	 * @param deviceIds the FRU device IDs to read
	 * @return this request
	 */
	public FrusRequest ids(int... deviceIds) {
		Objects.requireNonNull(deviceIds, "deviceIds");
		this.ids = new HashSet<Integer>();
		for (int id : deviceIds) {
			this.ids.add(id);
		}
		return this;
	}

	/**
	 * Be told of each SDR record the repository walk could not use: the records are given to the consumer, on the
	 * calling thread, once the walk is done, before {@link #execute()} returns or {@link #stream()} yields its first
	 * FRU. {@link #execute()} also lists them in {@link FruList#skippedRecords()}.
	 *
	 * @param consumer receives each skipped record, with the reason
	 * @return this request
	 */
	public FrusRequest onSkippedRecord(Consumer<SkippedRecord> consumer) {
		this.onSkippedRecord = Objects.requireNonNull(consumer, "consumer");
		return this;
	}

	/**
	 * Overrides the deadline of the client for this request. For {@link #execute()}, it is the time the whole
	 * operation may take, session setup included; for {@link #stream()}, an <i>inactivity</i> timeout: the longest
	 * wait for the SDR walk before the first FRU, then for each FRU, with no limit on the whole.
	 *
	 * @param deadline the deadline
	 * @return this request
	 * @throws IllegalArgumentException when the duration is shorter than one millisecond or longer than
	 *         {@link Integer#MAX_VALUE} milliseconds
	 */
	public FrusRequest timeout(Duration deadline) {
		this.timeout = IpmiClient.checkTimeout(deadline, "timeout");
		return this;
	}

	/**
	 * Reads the selected FRUs.
	 *
	 * @return the FRUs, in the order of their locator records; a FRU that could not be read entirely is returned
	 *         with its problems
	 * @throws IpmiClientException when the session cannot be opened, the SDR repository cannot be read, the BMC stops
	 *         answering, or the deadline expires
	 */
	public FruList execute() {
		FruList frus = client.readFrus(ids, timeout);
		frus.skippedRecords().forEach(onSkippedRecord);
		return frus;
	}

	/**
	 * Reads the selected FRUs and streams them: each FRU is yielded as soon as it is read, in the order of
	 * {@link #execute()}, and the next one is read only as the stream advances, so that a caller that stops early
	 * ({@code limit(n)}, {@code findFirst()}) does not pay for the rest.
	 *
	 * <pre>
	 * try (Stream&lt;Fru&gt; frus = bmc.frus().stream()) {
	 * 	frus.forEach(fru -&gt; process(fru));
	 * }
	 * </pre>
	 * <p>
	 * <b>Close the stream</b> (try-with-resources), as with {@link java.nio.file.Files#lines}: closing it before the
	 * last FRU ends the walk, and no further command is sent. The stream holds nothing between two FRUs: each one
	 * is read as an operation of the client of its own, so that the operations of other threads run in between, and
	 * a stream that is abandoned blocks nothing, not even {@link IpmiClient#close()}.
	 * </p>
	 * <p>
	 * The SDR repository is walked here, and the records it skipped are given to {@link #onSkippedRecord(Consumer)}
	 * before this method returns. The timeout is an inactivity timeout ({@link #timeout(Duration)}). A FRU that could
	 * not be read entirely is yielded with its {@link Fru#problems()}, as with {@link #execute()}. A session lost while
	 * a FRU is read is replaced and that FRU read again, once; the FRUs already yielded are not read again.
	 * </p>
	 *
	 * @return a lazy, sequential stream of the FRUs, to use with try-with-resources
	 * @throws IpmiClientException when the session cannot be opened, the SDR repository cannot be read, or the walk
	 *         does not complete within the timeout; the same exceptions are thrown from the operations of the stream
	 *         ({@code forEach}, {@code collect}, {@code iterator().next()}) when the BMC stops answering or a FRU
	 *         is not read within the timeout
	 * @throws IllegalStateException from the operations of the stream when the client was closed meanwhile
	 */
	public Stream<Fru> stream() {
		return client.streamFrus(ids, timeout, onSkippedRecord);
	}
}
