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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.metricshub.ipmi.core.coding.commands.sdr.record.AbstractSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AddressType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.commands.sel.EventDirection;
import org.metricshub.ipmi.core.coding.commands.sel.SelRecord;
import org.metricshub.ipmi.core.coding.commands.sel.SelRecordType;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * One entry of the System Event Log, read by {@link IpmiClient#sel()}. A <b>system event</b> (record type
 * {@link SelRecordType#System}) says which sensor reported what, when: {@link #sensorType()}, {@link #sensorNumber()},
 * {@link #event()} with its {@link #description()}, {@link #direction()} and {@link #timestamp()}. An <b>OEM</b>
 * entry keeps its {@link #oemData()} and, when timestamped, its {@link #manufacturerId()} and timestamp; an entry
 * of a reserved record type has nothing but its {@link #id()} (IPMI 2.0 section 32).
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The decoded record of the protocol core is handed out "
		+ "by reference for the callers that need its every field")
public final class SelEntry {

	/**
	 * The timestamps up to this value count the seconds since the initialization of the SEL device, not since 1970
	 * (IPMI 2.0 section 37.1).
	 */
	private static final long LAST_PRE_INIT_TIMESTAMP = 0x20000000L;

	/** FFFFFFFFh: an invalid or unspecified timestamp (IPMI 2.0 section 37.1). */
	private static final long UNSPECIFIED_TIMESTAMP = 0xFFFFFFFFL;

	private static final int OFFSET_MASK = 0x0f;
	private static final int OWNER_ID_MASK = 0x7f;
	private static final int BYTE_MASK = 0xff;
	private static final int LUN_MASK = 0x03;

	private final SelRecord record;
	private final String description;

	private final int utcOffsetMinutes;

	/**
	 * Wraps a decoded record of a BMC whose SEL time is UTC.
	 *
	 * @param record the record
	 */
	SelEntry(SelRecord record) {
		this(record, 0);
	}

	/**
	 * Wraps a decoded record.
	 *
	 * @param record the record
	 * @param utcOffsetMinutes the offset from UTC of the SEL time (Get SEL Time UTC Offset), 0 when it is UTC
	 */
	SelEntry(SelRecord record, int utcOffsetMinutes) {
		this.record = record;
		this.utcOffsetMinutes = utcOffsetMinutes;
		this.description = describe();
	}

	/**
	 * Returns the record ID of the entry, unique in the log; after a Clear SEL or a Delete SEL Entry, the BMC may give it
	 * to another entry (IPMI 2.0 sections 31.4.1 and 31.4.2). It is the value to give {@link SelRequest#since(int)} to
	 * read the entries added after this one.
	 *
	 * @return the record ID, 1 to 65534
	 */
	public int id() {
		return record.getRecordId();
	}

	/**
	 * Returns the record type: a system event, an OEM entry with or without timestamp, or a reserved type.
	 *
	 * @return the record type
	 */
	public SelRecordType type() {
		return record.getRecordType();
	}

	/**
	 * Returns when the event was logged, for a system event or a timestamped OEM entry whose timestamp is a date:
	 * empty for an entry without timestamp, an unspecified timestamp (FFFFFFFFh), and an event logged before the
	 * BMC had the time ({@link #preInitSeconds()}). The offset from UTC that a BMC keeping its SEL time as local time
	 * reports ({@link SelEntryList#utcOffsetMinutes()}) is applied.
	 *
	 * @return the timestamp
	 */
	public Optional<Instant> timestamp() {
		return absolute(record.getTimestamp(), utcOffsetMinutes);
	}

	/**
	 * Returns the age of an event logged before the SEL device had the time, as the seconds since its initialization
	 * (IPMI 2.0 section 37.1: the timestamps up to 20000000h are relative); empty when {@link #timestamp()} is a
	 * date, unspecified or absent.
	 *
	 * @return the seconds since the initialization of the SEL device
	 */
	public OptionalLong preInitSeconds() {
		Date timestamp = record.getTimestamp();
		return timestamp == null || seconds(timestamp) > LAST_PRE_INIT_TIMESTAMP ?
				OptionalLong.empty() : OptionalLong.of(seconds(timestamp));
	}

	/**
	 * Returns the type of the sensor that reported a system event (IPMI 2.0 table 42-3).
	 *
	 * @return the sensor type, empty for the other record types
	 */
	public Optional<SensorType> sensorType() {
		return Optional.ofNullable(record.getSensorType());
	}

	/**
	 * Returns the number of the sensor that reported a system event, unique for its {@link #generatorId() generator}:
	 * the {@link Sensor#number()} of the sensor in the SDR repository.
	 *
	 * @return the sensor number, empty for the other record types
	 */
	public OptionalInt sensorNumber() {
		return isSystemEvent() ? OptionalInt.of(record.getSensorNumber()) : OptionalInt.empty();
	}

	/**
	 * Returns the generator ID of a system event: the slave address (20h for the BMC itself) or system software ID
	 * of what logged the event in the low byte, its channel number and LUN in the high byte (IPMI 2.0 section 32.1).
	 *
	 * @return the generator ID, empty for the other record types
	 */
	public OptionalInt generatorId() {
		return isSystemEvent() ? OptionalInt.of(record.getGeneratorId()) : OptionalInt.empty();
	}

	/**
	 * Returns the event/reading type code of a system event (IPMI 2.0 table 42-1): 01h for a threshold event, 02h to
	 * 0Ch for a generic discrete one, 6Fh for a sensor-specific one, 70h to 7Fh for an OEM one.
	 *
	 * @return the event/reading type, empty for the other record types
	 */
	public OptionalInt eventType() {
		return isSystemEvent() ? OptionalInt.of(record.getEventType()) : OptionalInt.empty();
	}

	/**
	 * Returns what happened, as the {@link ReadingType} of the event offset for the sensor type and event/reading
	 * type: {@code UpperCriticalGoingHigh}, {@code PowerSupplyFailureDetected}, {@code LogAreaReset}...
	 * {@link ReadingType#UnknownOEMEvent} for an OEM event/reading type (70h to 7Fh), or for an OEM sensor type (C0h
	 * to FFh) with a sensor-specific event/reading type (6Fh), {@link ReadingType#Unknown} for an offset the
	 * specification does not define.
	 *
	 * @return the event, empty for the other record types
	 */
	public Optional<ReadingType> event() {
		return Optional.ofNullable(record.getEvent());
	}

	/**
	 * Returns whether a system event is the assertion of its {@link #event() event} (a threshold crossed, a failure
	 * detected) or its deassertion (back within the threshold, the failure gone).
	 *
	 * @return the direction, empty for the other record types
	 */
	public Optional<EventDirection> direction() {
		return Optional.ofNullable(record.getEventDirection());
	}

	/**
	 * Returns a description of the entry. For a system event: the {@link #event() event} in the wording of
	 * {@code ipmiutil} ({@code Failure detected}, {@code Log area reset/cleared}), or of {@code ipmitool} for the few
	 * events {@code ipmiutil} does not name ({@code Installation started}); the name of the {@link ReadingType} spaced
	 * out when neither tool names it, as for the threshold events ({@code Upper Critical Going High}); the event/reading
	 * type and the offset when the specification does not define the offset ({@code Unknown event type 08h offset 6h})
	 * or the event is OEM ({@code OEM event type 71h offset 2h}). For an OEM entry, {@code OEM data} followed by its OEM
	 * bytes in hexadecimal. For a reserved record type, {@code Reserved record type}.
	 *
	 * @return the description
	 */
	public String description() {
		return description;
	}

	/**
	 * Returns the three event data bytes of a system event as the BMC sent them (IPMI 2.0 table 32-1): the event
	 * offset in the low nibble of the first byte, and what its high bits announce for the next two: for a threshold
	 * event, the raw reading that triggered it and the threshold, to convert with the
	 * {@link org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord FullSensorRecord} of the sensor.
	 *
	 * @return a copy of the event data, empty for the other record types
	 */
	public byte[] eventData() {
		return record.getEventData() == null ? new byte[0] : record.getEventData().clone();
	}

	/**
	 * Returns the manufacturer ID (IANA enterprise number) of a timestamped OEM entry.
	 *
	 * @return the manufacturer ID, empty for the other record types
	 */
	public OptionalInt manufacturerId() {
		return record.getManufacturerId() == null ? OptionalInt.empty() : OptionalInt.of(record.getManufacturerId());
	}

	/**
	 * Returns the OEM-defined bytes of an OEM entry: 6 for a timestamped one, 13 otherwise (IPMI 2.0 sections 32.2
	 * and 32.3).
	 *
	 * @return a copy of the OEM bytes, empty for the other record types
	 */
	public byte[] oemData() {
		return record.getOemData() == null ? new byte[0] : record.getOemData().clone();
	}

	/**
	 * Returns the decoded record of the protocol core, for everything else.
	 *
	 * @return the record
	 */
	public SelRecord record() {
		return record;
	}

	private boolean isSystemEvent() {
		return record.getRecordType() == SelRecordType.System;
	}

	private String describe() {
		switch (record.getRecordType()) {
		case System:
			return describeEvent();
		case OemTimestamped:
		case OemNonTimestamped:
			return "OEM data " + hex(record.getOemData());
		default:
			return "Reserved record type";
		}
	}

	private String describeEvent() {
		ReadingType event = record.getEvent();
		String known = ReadingTypeDescription.getReadingType(event);
		if (event == ReadingType.Unknown || event == ReadingType.UnknownOEMEvent) {
			// Neither the table nor the specification names it: the codes let the vendor documentation do so
			return String
					.format(
							"%s event type %02Xh offset %Xh",
							event == ReadingType.Unknown ? "Unknown" : "OEM",
							record.getEventType(),
							record.getEventData()[0] & OFFSET_MASK);
		}
		if (known != null) {
			return known;
		}
		// The threshold events and the states that have no wording: the name of the ReadingType, spaced out
		return event.name().replaceAll("(?<=[a-z])(?=[A-Z])", " ");
	}

	/**
	 * Converts a timestamp of the protocol core (a {@link Date} holding the unsigned 32-bit value) to the date it
	 * stands for: none when it is unspecified or relative to the initialization of the SEL device.
	 *
	 * @param timestamp the timestamp as the BMC sent it
	 * @param utcOffsetMinutes the offset from UTC of the SEL time: local time = UTC + offset
	 */
	static Optional<Instant> absolute(Date timestamp, int utcOffsetMinutes) {
		if (timestamp == null) {
			return Optional.empty();
		}
		long seconds = seconds(timestamp);
		return seconds <= LAST_PRE_INIT_TIMESTAMP || seconds == UNSPECIFIED_TIMESTAMP ?
				Optional.<Instant>empty() : Optional.of(timestamp.toInstant().minus(utcOffsetMinutes, ChronoUnit.MINUTES));
	}

	/**
	 * Tells whether a system event was reported by the given sensor: the sensor that owns the number of the event,
	 * for the owner that logged it. The {@link #generatorId() generator ID} is matched against the owner of the SDR
	 * record (its slave address or system software ID, whichever bit 0 says, and its LUN) and the sensor number
	 * against {@link Sensor#number()}, as {@code ipmitool sel elist} does; the channel number is not compared, as
	 * BMCs do not log it consistently.
	 *
	 * @param sensor a sensor of the SDR repository
	 * @return whether the event is from that sensor; false for the OEM and reserved entries
	 */
	public boolean isFrom(Sensor sensor) {
		Objects.requireNonNull(sensor, "sensor");
		if (!isSystemEvent() || !(sensor.record() instanceof AbstractSensorRecord)) {
			return false;
		}
		AbstractSensorRecord owner = (AbstractSensorRecord) sensor.record();
		int generator = record.getGeneratorId();
		// Generator ID byte 1 (IPMI 2.0 table 32-1): the 7-bit address or ID in bits 7:1, bit 0 set for a software ID;
		// byte 2: the channel in bits 7:4, the LUN in bits 1:0
		int address = (owner.getSensorOwnerId() & OWNER_ID_MASK) << 1
				| (owner.getAddressType() == AddressType.SystemSoftwareId ? 1 : 0);
		return record.getSensorNumber() == sensor.number()
				&& (generator & BYTE_MASK) == address
				&& (generator >> Byte.SIZE & LUN_MASK) == (owner.getSensorOwnerLun() & LUN_MASK);
	}

	/**
	 * Two entries are equal when they are the same record: same ID and same 16 bytes. An entry read again after the
	 * log was cleared and filled up to its ID again is another entry.
	 */
	@Override
	public boolean equals(Object other) {
		return other instanceof SelEntry && Arrays.equals(record.getRawData(), ((SelEntry) other).record.getRawData());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(record.getRawData());
	}

	private static long seconds(Date timestamp) {
		return timestamp.getTime() / 1000;
	}

	static String hex(byte[] bytes) {
		StringBuilder text = new StringBuilder();
		for (byte b : bytes) {
			text.append(text.length() == 0 ? "" : " ").append(String.format("%02X", b));
		}
		return text.toString();
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder("SEL entry ").append(id());
		Optional<Instant> time = timestamp();
		if (time.isPresent()) {
			text.append(" at ").append(time.get());
		} else if (preInitSeconds().isPresent()) {
			text.append(' ').append(preInitSeconds().getAsLong()).append(" s after initialization");
		}
		if (isSystemEvent()) {
			text
					.append(": ")
					.append(record.getSensorType())
					.append(" sensor ")
					.append(record.getSensorNumber())
					.append(", ")
					.append(description)
					.append(" (")
					.append(record.getEventDirection().name().toLowerCase(Locale.ROOT))
					.append(')');
		} else {
			text.append(": ").append(type());
			if (record.getManufacturerId() != null) {
				text.append(" of manufacturer ").append(record.getManufacturerId());
			}
			text.append(", ").append(description);
		}
		return text.toString();
	}
}
