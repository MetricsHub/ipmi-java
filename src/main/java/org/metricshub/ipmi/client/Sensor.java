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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AbstractSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.InstanceModifierType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorReadingFactors;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorUnit;
import org.metricshub.ipmi.core.common.TypeConverter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A sensor of the BMC, with its SDR record and its reading. A threshold sensor (a temperature, a voltage, a fan
 * speed) has a {@link #reading()} converted with the formula of its record and {@link #status()} against its
 * thresholds; a discrete sensor (a presence, a power supply state) has its asserted {@link #states()}. A sensor whose
 * reading the BMC did not give says why in {@link #problem()}.
 */
// EI_EXPOSE_REP also matches EI_EXPOSE_REP2
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The decoded record and reading of the protocol core "
		+ "are handed out by reference for the callers that need their every field")
public final class Sensor {

	/** Letters of an alpha ID string instance modifier (IPMI 2.0 section 43.2). */
	private static final int ALPHABET = 26;

	/** Event/reading type code of the threshold sensors (IPMI 2.0 table 42-1). */
	private static final int THRESHOLD_EVENT_READING_TYPE = 1;

	private final AbstractSensorRecord record;
	private final int instance;
	private final GetSensorReadingResponseData reading;
	private final SensorReadingFactors factors;
	private final String problem;
	private final List<ReadingType> rawStates;
	private final List<String> states;

	private Sensor(
			AbstractSensorRecord record,
			int instance,
			GetSensorReadingResponseData reading,
			SensorReadingFactors factors,
			String problem) {
		this.record = record;
		this.instance = instance;
		this.reading = reading;
		this.factors = factors;
		this.problem = problem;
		this.rawStates = reading == null || !isValid(reading) ?
				Collections.<ReadingType>emptyList() :
				Collections
						.unmodifiableList(reading.getStatesAsserted(record.getSensorType(), record.getEventReadingType()));
		this.states = Collections.unmodifiableList(describeStates());
	}

	/**
	 * Creates the sensor from its record and the replies of the BMC to Get Sensor Reading and, for a non-linear
	 * sensor, Get Sensor Reading Factors.
	 *
	 * @param record the Full or Compact sensor record of the sensor
	 * @param reading the reply to Get Sensor Reading, or null when the BMC gave none
	 * @param factors the factors of the reading of a non-linear sensor, or null
	 * @param problem why the BMC gave no reading, or null when it gave one
	 * @return the sensor
	 */
	static Sensor of(
			AbstractSensorRecord record,
			GetSensorReadingResponseData reading,
			SensorReadingFactors factors,
			String problem) {
		return of(record, 0, reading, factors, problem);
	}

	/**
	 * Creates one of the sensors sharing a record (IPMI 2.0 table 43-2, bytes 24 and 25) from the record, its index
	 * among them and the replies of the BMC.
	 *
	 * @param record the Full or Compact sensor record of the sensor
	 * @param instance the index of the sensor among those sharing the record: 0 for the sensor number of the record
	 * @param reading the reply to Get Sensor Reading, or null when the BMC gave none
	 * @param factors the factors of the reading of a non-linear sensor, or null
	 * @param problem why the BMC gave no reading, or null when it gave one
	 * @return the sensor
	 */
	static Sensor of(
			AbstractSensorRecord record,
			int instance,
			GetSensorReadingResponseData reading,
			SensorReadingFactors factors,
			String problem) {
		String why = problem;
		if (why == null && reading != null) {
			if (!reading.isSensorStateValid()) {
				why = "The reading is not valid yet: the sensor is being re-armed or initialized";
			} else if (!reading.isScanningEnabled()) {
				why = "Sensor scanning is disabled";
			}
		}
		return new Sensor(record, instance, reading, factors, why);
	}

	/**
	 * Returns the number of sensors a record describes: its share count, 1 for a Full record and for a Compact record
	 * whose share count is 0 or 1 (IPMI 2.0 table 43-2, byte 24).
	 *
	 * @param record the Full or Compact sensor record
	 * @return the number of sensors, at least 1
	 */
	static int instances(AbstractSensorRecord record) {
		return Math.max(1, record.getShareCount());
	}

	/**
	 * Names one of the sensors sharing a record (IPMI 2.0 section 43.2, "ID String Instance Modifier"): the ID string
	 * followed by a space and the number (instance modifier offset plus the index of the sensor) for a numeric
	 * modifier, or the letters of that number in base 26 (0 is A, 25 is Z, 26 is AA) for an alpha modifier. When the ID
	 * string of a numeric modifier already ends with a number, that number is the one of the first sensor and is
	 * incremented instead: Dell iDRACs name their drive slots {@code Drive 0} (15 sensors) and {@code Drive 15} (9
	 * sensors), which this rule names {@code Drive 0} to {@code Drive 23}.
	 *
	 * @param record the Full or Compact sensor record
	 * @param instance the index of the sensor among those sharing the record
	 * @return the name, the ID string itself for a record that describes a single sensor
	 */
	static String instanceName(AbstractSensorRecord record, int instance) {
		String id = record.getName();
		if (instances(record) == 1) {
			return id;
		}
		int number = record.getIdInstanceModifierOffset() + instance;
		if (record.getIdInstanceModifierType() == InstanceModifierType.Alpha) {
			String letters = number < ALPHABET ?
					String.valueOf((char) ('A' + number)) :
					new String(new char[]
					{ (char) ('A' + number / ALPHABET - 1), (char) ('A' + number % ALPHABET) });
			return id.trim() + ' ' + letters;
		}
		String base = id.trim();
		int digits = base.length();
		while (digits > 0 && Character.isDigit(base.charAt(digits - 1))) {
			digits--;
		}
		if (digits < base.length() && base.length() - digits < 10) {
			return base.substring(0, digits) + (Integer.parseInt(base.substring(digits)) + instance);
		}
		return base + ' ' + number;
	}

	static boolean isValid(GetSensorReadingResponseData reading) {
		// IPMI 2.0 table 35-15: neither the reading nor the states are valid when the sensor is unavailable or not scanned
		return reading.isSensorStateValid() && reading.isScanningEnabled();
	}

	private List<String> describeStates() {
		if (rawStates.isEmpty()) {
			return new ArrayList<String>();
		}
		if (ReadingType.isOem(record.getEventReadingType())) {
			// An OEM event/reading type (70h-7Fh): the state bytes as they are, which only the vendor documents
			byte[] raw = reading.getRaw();
			return Collections
					.singletonList(
							raw.length == 3 ?
									String.format("0x%02x", raw[2]) :
									String.format("0x%02x%02x", raw[3], raw[2]));
		}
		Set<String> descriptions = new LinkedHashSet<String>();
		for (ReadingType state : rawStates) {
			String description = ReadingTypeDescription.getReadingType(state);
			if (description != null) {
				descriptions.add(description);
			}
		}
		return new ArrayList<String>(descriptions);
	}

	/**
	 * Returns the name of the sensor, from the ID string of its record. A sensor sharing its record with others (a
	 * Compact record with a share count above 1) gets the ID string followed by its instance modifier: {@code Drive 0},
	 * {@code Drive 1}... for a numeric modifier, {@code Temp A}, {@code Temp B}... for an alpha one (IPMI 2.0 section
	 * 43.2). When the ID string already ends with a number, as on Dell iDRACs ({@code Drive 0} for 15 drive slots), that
	 * number is incremented instead.
	 *
	 * @return the name, empty (never {@code null}) when the record has no ID string
	 */
	public String name() {
		return instanceName(record, instance);
	}

	/**
	 * Returns the sensor number, unique for the owner of the sensor: the number of the record plus the index of the
	 * sensor among those sharing it.
	 *
	 * @return the sensor number, 0 to 255
	 */
	public int number() {
		return TypeConverter.byteToInt(record.getSensorNumber()) + instance;
	}

	/**
	 * Returns what the sensor measures or monitors.
	 *
	 * @return the sensor type
	 */
	public SensorType type() {
		return record.getSensorType();
	}

	/**
	 * Returns the entity the sensor monitors.
	 *
	 * @return the entity
	 */
	public EntityId entity() {
		return record.getEntityId();
	}

	/**
	 * Returns the instance of the entity the sensor monitors: 1 for the first processor, 2 for the second one. For a
	 * sensor sharing its record with others, the instance of the record plus the index of the sensor when the record
	 * says the entity instance increments (IPMI 2.0 table 43-2, byte 25 bit 7), the instance of the record otherwise.
	 *
	 * @return the entity instance, 0 to 255
	 */
	public int entityInstance() {
		return TypeConverter.byteToInt(record.getEntityInstanceNumber())
				+ (record.isEntityInstanceIncrements() ? instance : 0);
	}

	/**
	 * Returns the unit of the reading and of the thresholds.
	 *
	 * @return the base unit
	 */
	public SensorUnit unit() {
		return record.getSensorBaseUnit();
	}

	/**
	 * Returns the reading of a threshold sensor, in its {@link #unit()}, converted with the formula and the
	 * linearization of its record, or for a non-linear sensor with the factors the BMC returned for the reading.
	 *
	 * @return the reading, or empty when the sensor has none: a discrete sensor, an unavailable reading
	 *         ({@link #problem()}), or a record without analog reading
	 */
	public OptionalDouble reading() {
		if (!rawReading().isPresent() || !(record instanceof FullSensorRecord)) {
			return OptionalDouble.empty();
		}
		FullSensorRecord full = (FullSensorRecord) record;
		if (!full.hasAnalogReading()) {
			return OptionalDouble.empty();
		}
		double value = factors == null ?
				reading.getSensorReading(full) : full.calcFormula(rawReading().getAsInt(), factors);
		return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
	}

	/**
	 * Returns the reading byte as the BMC sent it, before any conversion.
	 *
	 * @return the raw reading, 0 to 255, or empty when the reading is unavailable ({@link #problem()})
	 */
	public OptionalInt rawReading() {
		return reading == null || !isValid(reading) ?
				OptionalInt.empty() : OptionalInt.of((int) reading.getPlainSensorReading());
	}

	/**
	 * Returns the status of the sensor: for a threshold sensor, the worst threshold the reading is at or beyond; for
	 * a discrete sensor, {@link SensorStatus#OK} when it answers.
	 *
	 * @return the status, {@link SensorStatus#UNAVAILABLE} when the reading is unavailable ({@link #problem()})
	 */
	public SensorStatus status() {
		if (reading == null || !isValid(reading)) {
			return SensorStatus.UNAVAILABLE;
		}
		if (record.getEventReadingType() != THRESHOLD_EVENT_READING_TYPE || reading.getSensorState() == null) {
			return SensorStatus.OK;
		}
		switch (reading.getSensorState()) {
		case AboveUpperNonRecoverable:
		case BelowLowerNonRecoverable:
			return SensorStatus.NON_RECOVERABLE;
		case AboveUpperCritical:
		case BelowLowerCritical:
			return SensorStatus.CRITICAL;
		case AboveUpperNonCritical:
		case BelowLowerNonCritical:
			return SensorStatus.NON_CRITICAL;
		case Ok:
			return SensorStatus.OK;
		default:
			return SensorStatus.UNAVAILABLE;
		}
	}

	/**
	 * Returns the asserted states of a discrete sensor, in the wording of {@code ipmiutil} ("Present", "Device
	 * Absent", "Power Supply Failure detected"...; of {@code ipmitool} for the few states {@code ipmiutil} does not
	 * name), each once. The states of a threshold sensor are the thresholds
	 * its reading is at or beyond. For an OEM event/reading type, the single state is the hexadecimal state bytes.
	 *
	 * @return the states, empty when none is asserted, when the reading is unavailable, or when the library does
	 *         not describe the asserted states ({@link #rawStates()} still lists them)
	 */
	public List<String> states() {
		return states;
	}

	/**
	 * Returns the asserted states of the sensor as the protocol core decodes them.
	 *
	 * @return the states, empty when none is asserted or when the reading is unavailable
	 */
	public List<ReadingType> rawStates() {
		return rawStates;
	}

	/**
	 * Returns the lower non-critical threshold, in the {@link #unit()} of the sensor.
	 *
	 * @return the threshold, or empty when the sensor does not define it (a threshold of 0 is a threshold)
	 */
	public OptionalDouble lowerNonCritical() {
		return threshold(
				record instanceof FullSensorRecord ? ((FullSensorRecord) record).getLowerNonCriticalThreshold() : Double.NaN);
	}

	/**
	 * Returns the lower critical threshold, in the {@link #unit()} of the sensor.
	 *
	 * @return the threshold, or empty when the sensor does not define it
	 */
	public OptionalDouble lowerCritical() {
		return threshold(
				record instanceof FullSensorRecord ? ((FullSensorRecord) record).getLowerCriticalThreshold() : Double.NaN);
	}

	/**
	 * Returns the lower non-recoverable threshold, in the {@link #unit()} of the sensor.
	 *
	 * @return the threshold, or empty when the sensor does not define it
	 */
	public OptionalDouble lowerNonRecoverable() {
		return threshold(
				record instanceof FullSensorRecord ?
						((FullSensorRecord) record).getLowerNonRecoverableThreshold() : Double.NaN);
	}

	/**
	 * Returns the upper non-critical threshold, in the {@link #unit()} of the sensor.
	 *
	 * @return the threshold, or empty when the sensor does not define it
	 */
	public OptionalDouble upperNonCritical() {
		return threshold(
				record instanceof FullSensorRecord ? ((FullSensorRecord) record).getUpperNonCriticalThreshold() : Double.NaN);
	}

	/**
	 * Returns the upper critical threshold, in the {@link #unit()} of the sensor.
	 *
	 * @return the threshold, or empty when the sensor does not define it
	 */
	public OptionalDouble upperCritical() {
		return threshold(
				record instanceof FullSensorRecord ? ((FullSensorRecord) record).getUpperCriticalThreshold() : Double.NaN);
	}

	/**
	 * Returns the upper non-recoverable threshold, in the {@link #unit()} of the sensor.
	 *
	 * @return the threshold, or empty when the sensor does not define it
	 */
	public OptionalDouble upperNonRecoverable() {
		return threshold(
				record instanceof FullSensorRecord ?
						((FullSensorRecord) record).getUpperNonRecoverableThreshold() : Double.NaN);
	}

	private static OptionalDouble threshold(double value) {
		return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
	}

	/**
	 * Returns why the sensor has no reading and, unless only the factors of a non-linear sensor are missing, no states.
	 *
	 * @return the problem: the BMC refused the reading or the factors of a non-linear sensor (with its completion
	 *         code), the reading is not valid yet, or scanning is disabled; empty when the sensor has a reading
	 */
	public Optional<String> problem() {
		return Optional.ofNullable(problem);
	}

	/**
	 * Returns the SDR record of the sensor, as the protocol core decodes it: a {@link FullSensorRecord} for a
	 * threshold sensor, a {@link org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord} for a
	 * discrete one.
	 *
	 * @return the record
	 */
	public SensorRecord record() {
		return record;
	}

	/**
	 * Returns the reply of the BMC to Get Sensor Reading, as the protocol core decodes it.
	 *
	 * @return the reply, or empty when the BMC refused the reading
	 */
	public Optional<GetSensorReadingResponseData> readingData() {
		return Optional.ofNullable(reading);
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder(name())
				.append(" (")
				.append(type())
				.append(", ")
				.append(entity())
				.append(' ')
				.append(entityInstance())
				.append("): ");
		if (problem != null) {
			return text.append(problem).toString();
		}
		OptionalDouble value = reading();
		if (value.isPresent()) {
			text.append(value.getAsDouble()).append(' ').append(unit()).append(", ").append(status());
		} else {
			text.append(status());
		}
		if (!states.isEmpty()) {
			text.append(", states ").append(states);
		}
		return text.toString();
	}
}
