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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.metricshub.ipmi.core.coding.commands.sdr.record.AbstractSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;

/**
 * Reads the sensors of the BMC: the Full and Compact sensor records of the SDR repository, each with its Get
 * Sensor Reading. The selection is applied before the readings are requested, so that an unselected sensor costs no
 * round trip.
 */
public final class SensorsRequest {

	private final IpmiClient client;
	private Set<SensorType> types;
	private List<Pattern> names;
	private Duration timeout;
	private Consumer<SkippedRecord> onSkippedRecord = record -> {};

	SensorsRequest(IpmiClient client) {
		this.client = client;
	}

	/**
	 * Reads only the sensors of the given types; without argument, it reads none. A later call replaces the types.
	 * With {@link #names(String...)}, a sensor is read when it matches both.
	 *
	 * @param sensorTypes the types to read
	 * @return this request
	 */
	public SensorsRequest types(SensorType... sensorTypes) {
		Objects.requireNonNull(sensorTypes, "sensorTypes");
		this.types = EnumSet.noneOf(SensorType.class);
		this.types.addAll(Arrays.asList(sensorTypes));
		return this;
	}

	/**
	 * Reads only the sensors whose name matches one of the given patterns, case-insensitively: {@code *} matches
	 * any characters and {@code ?} one character ({@code "CPU? Temp"}, {@code "*Fan*"}). A pattern matches the whole
	 * name. Without argument, it reads none. A later call replaces the patterns. With {@link #types(SensorType...)}, a
	 * sensor is read when it matches both.
	 *
	 * @param globs the name patterns
	 * @return this request
	 */
	public SensorsRequest names(String... globs) {
		Objects.requireNonNull(globs, "globs");
		this.names = new ArrayList<Pattern>();
		for (String glob : globs) {
			this.names.add(toPattern(glob));
		}
		return this;
	}

	/**
	 * Be told of each SDR record the repository walk could not use: the records are given to the consumer, on the
	 * calling thread, once the walk is done, before {@link #execute()} returns or {@link #stream()} yields its first
	 * sensor. {@link #execute()} also lists them in {@link SensorList#skippedRecords()}.
	 *
	 * @param consumer receives each skipped record, with the reason
	 * @return this request
	 */
	public SensorsRequest onSkippedRecord(Consumer<SkippedRecord> consumer) {
		this.onSkippedRecord = Objects.requireNonNull(consumer, "consumer");
		return this;
	}

	/**
	 * Overrides the deadline of the client for this request. For {@link #execute()}, it is the time the whole
	 * operation may take, session setup included; for {@link #stream()}, an <i>inactivity</i> timeout: the longest
	 * wait for the SDR walk before the first sensor, then for each sensor, with no limit on the whole.
	 *
	 * @param deadline the deadline
	 * @return this request
	 * @throws IllegalArgumentException when the duration is shorter than one millisecond or longer than
	 *         {@link Integer#MAX_VALUE} milliseconds
	 */
	public SensorsRequest timeout(Duration deadline) {
		this.timeout = IpmiClient.checkTimeout(deadline, "timeout");
		return this;
	}

	/**
	 * Reads the selected sensors.
	 *
	 * @return the sensors, in the order of the SDR repository
	 * @throws IpmiClientException when the session cannot be opened, the SDR repository cannot be read, the BMC stops
	 *         answering, or the deadline expires; a reading that the BMC or a controller behind it refuses or answers
	 *         with a malformed reply, or that such a controller does not answer, costs that sensor only
	 *         ({@link Sensor#problem()})
	 */
	public SensorList execute() {
		SensorList sensors = client.readSensors(this::selects, timeout);
		sensors.skippedRecords().forEach(onSkippedRecord);
		return sensors;
	}

	/**
	 * Reads the selected sensors and streams them: each sensor is yielded as soon as it is read, in the order of
	 * {@link #execute()}, and the next one is read only as the stream advances, so that a caller that stops early
	 * ({@code limit(n)}, {@code findFirst()}) does not pay for the rest.
	 *
	 * <pre>
	 * try (Stream&lt;Sensor&gt; sensors = bmc.sensors().types(SensorType.Temperature).stream()) {
	 * 	sensors.forEach(sensor -&gt; process(sensor));
	 * }
	 * </pre>
	 * <p>
	 * <b>Close the stream</b> (try-with-resources), as with {@link java.nio.file.Files#lines}: closing it before the
	 * last sensor ends the walk, and no further command is sent. The stream holds nothing between two sensors: each
	 * one is read as an operation of the client of its own, so that the operations of other threads run in between,
	 * and a stream that is abandoned blocks nothing, not even {@link IpmiClient#close()}.
	 * </p>
	 * <p>
	 * The SDR repository is walked here, and the records it skipped are given to {@link #onSkippedRecord(Consumer)}
	 * before this method returns. The timeout is an inactivity timeout ({@link #timeout(Duration)}). A sensor that the
	 * BMC or a controller behind it refuses, or that such a controller does not answer, is yielded with its
	 * {@link Sensor#problem()}, as with {@link #execute()}. A session lost while a sensor is read is replaced and that
	 * sensor read again, once; the sensors already yielded are not read again.
	 * </p>
	 *
	 * @return a lazy, sequential stream of the sensors, to use with try-with-resources
	 * @throws IpmiClientException when the session cannot be opened, the SDR repository cannot be read, or the walk
	 *         does not complete within the timeout; the same exceptions are thrown from the operations of the stream
	 *         ({@code forEach}, {@code collect}, {@code iterator().next()}) when the BMC stops answering or a sensor
	 *         is not read within the timeout
	 * @throws IllegalStateException from the operations of the stream when the client was closed meanwhile
	 */
	public Stream<Sensor> stream() {
		return client.streamSensors(this::selects, timeout, onSkippedRecord);
	}

	boolean selects(AbstractSensorRecord record, String name) {
		if (types != null && !types.contains(record.getSensorType())) {
			return false;
		}
		if (names == null) {
			return true;
		}
		for (Pattern pattern : names) {
			if (pattern.matcher(name).matches()) {
				return true;
			}
		}
		return false;
	}

	static Pattern toPattern(String glob) {
		StringBuilder regex = new StringBuilder();
		for (char c : glob.toCharArray()) {
			if (c == '*') {
				regex.append(".*");
			} else if (c == '?') {
				regex.append('.');
			} else {
				regex.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}
}
