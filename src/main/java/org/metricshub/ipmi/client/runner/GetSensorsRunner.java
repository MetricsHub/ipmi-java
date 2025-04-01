package org.metricshub.ipmi.client.runner;

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
import java.util.Objects;
import java.util.stream.Collectors;

import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.client.Utils;
import org.metricshub.ipmi.client.model.ReadingTypeDescription;
import org.metricshub.ipmi.client.model.Sensor;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReading;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepository;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepositoryResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Get Full And Compact Sensor records
 */
public class GetSensorsRunner extends AbstractIpmiRunner<List<Sensor>> {

	private static final int OEM_EVENT_READING_TYPE = 127;

	public GetSensorsRunner(IpmiClientConfiguration ipmiConfiguration) {
		super(ipmiConfiguration);
	}

	@Override
	public List<Sensor> call() throws Exception {

		final List<Sensor> result = new ArrayList<>();

		super.startSession();

		// Id 0 indicates first record in SDR. Next IDs can be retrieved from
		// records - they are organized in a list and there is no BMC command to
		// get all of them.
		nextRecId = 0;

		// Some BMCs allow getting sensor records without reservation, so we try
		// to do it that way first
		int reservationId = 0;
		int lastReservationId = -1;

		// We get sensor data until we encounter ID = 65535 which means that
		// this record is the last one.
		while (nextRecId < MAX_REPO_RECORD_ID) {

			SensorRecord sensorRecord = null;

			try {
				// Populate the sensor record and get ID of the next record in
				// repository (see #getSensorData for details).
				sensorRecord = super.getSensorData(reservationId);

				if (sensorRecord instanceof FullSensorRecord || sensorRecord instanceof CompactSensorRecord) {
					int recordReadingId = getReadingId(sensorRecord);

					// If our record has got a reading associated, we get request
					// for it
					GetSensorReadingResponseData data = getSensorRecordReading(recordReadingId);

					// Build the states e.g. deviceName=OK|deviceName=Device Present
					String states = buildStates(data, sensorRecord);

					// Add the sensor to the result
					result.add(new Sensor(sensorRecord, data, states));
				}

			} catch (IPMIException e) {

				// If getting sensor data failed, we check if it already failed
				// with this reservation ID, so that we avoid the infinite loop.
				if (lastReservationId == reservationId || e.getCompletionCode() != CompletionCode.ReservationCanceled) {
					throw e;
				}

				lastReservationId = reservationId;

				// If the cause of the failure was canceling of the
				// reservation, we get new reservationId and retry. This can
				// happen many times during getting all sensors, since BMC can't
				// manage parallel sessions and invalidates old one if new one
				// appears.
				reservationId = ((ReserveSdrRepositoryResponseData) connector.sendMessage(handle,
						new ReserveSdrRepository(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus))).getReservationId();
			}

		}

		return result;
	}

	/**
	 * Build the states representation formatted as the following deviceName=state1|deviceName=state2|...
	 * 
	 * @param data         The sensor reading data containing the sensor parameter value and the states
	 * @param sensorRecord The sensor record we wish to process {@link CompactSensorRecord} or {@link FullSensorRecord}
	 * @return String value
	 */
	static String buildStates(final GetSensorReadingResponseData data, final SensorRecord sensorRecord) {
		if (data == null) {
			return Utils.EMPTY;
		}

		try {
			final String deviceName;
			final List<ReadingType> events;
			if (sensorRecord instanceof CompactSensorRecord) {

				CompactSensorRecord compactSensorRecord = (CompactSensorRecord) sensorRecord;

				deviceName = compactSensorRecord.getName();

				if (compactSensorRecord.getEventReadingType() == OEM_EVENT_READING_TYPE) {
					return buildOemState(data.getRaw(), deviceName);
				}

				events = data.getStatesAsserted(compactSensorRecord.getSensorType(),
						compactSensorRecord.getEventReadingType());
			} else {

				FullSensorRecord fullSensorRecord = (FullSensorRecord) sensorRecord;

				deviceName = fullSensorRecord.getName();

				if (fullSensorRecord.getEventReadingType() == OEM_EVENT_READING_TYPE) {
					return buildOemState(data.getRaw(), deviceName);
				}

				events = data.getStatesAsserted(fullSensorRecord.getSensorType(),
						fullSensorRecord.getEventReadingType());
			}

			return appendReadingTypes(events, deviceName);

		} catch (Exception e) {
			return Utils.EMPTY;
		}
	}

	/**
	 * Build the state for oem event reading type (0x7f)
	 * 
	 * @param raw a byte array of the raw IPMI command data
	 * @param deviceName the name of the device
	 * @return a string value of the state in a format of deviceName"=0x"+raw[3]+raw[2]
	 */
	static String buildOemState(final byte[] raw, final String deviceName) {
		if (raw == null || raw.length < 4) {
			throw new IllegalArgumentException(
					String.format("Invalid IPMI raw command date for device %s.", deviceName));
		}
		return String.format("%s=0x%02x%02x", deviceName, raw[3], raw[2]);
	}

	/**
	 * Append event reading type values
	 * 
	 * @param readingTypes The list of reading type events
	 * @param deviceName   The name of the device
	 * @return String value formatted as deviceName=state1|deviceName=state2|...deviceName=stateN
	 */
	private static String appendReadingTypes(final List<ReadingType> readingTypes, final String deviceName) {

		return readingTypes
				.stream()
				.map(readingType -> createStateEntry(deviceName, readingType))
				.filter(Objects::nonNull)
				.collect(Collectors.toSet()) // Remove duplicates
				.stream()
				.collect(Collectors.joining("|"));

	}

	/**
	 * Create a state entry format as <em>deviceName=readingTypeDescription</em>
	 * 
	 * @param deviceName  The name of the device
	 * @param readingType The reading type event (asserted state)
	 * @return String value or <code>null</code> if the reading type description is not found
	 */
	private static String createStateEntry(final String deviceName, final ReadingType readingType) {
		String state = ReadingTypeDescription.getReadingType(readingType);
		return state != null ? deviceName + "=" + state : null;
	}

	/**
	 * Using the given reading id run the GetSensorReading request to get reading data
	 * 
	 * @param recordReadingId the reading identifier of the sensor record
	 * @return {@link GetSensorReadingResponseData} instance
	 * @throws Exception at sendMessage or if the error completion code is not DataNotPresent
	 */
	private GetSensorReadingResponseData getSensorRecordReading(final int recordReadingId) throws Exception {
		try {
			// If we have a reading id means the reading data (e.g. temperature) is potentially available so let's perform the re
			if (recordReadingId >= 0) {
				return (GetSensorReadingResponseData) connector.sendMessage(handle,
						new GetSensorReading(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus, recordReadingId));

			}
		} catch (IPMIException e) {
			if (e.getCompletionCode() != CompletionCode.DataNotPresent) {
				throw e;
			}
		}
		return null;
	}

	/**
	 * Get the reading id which is required by the BMC to answer reading commands.
	 * 
	 * @param sensorRecord {@link SensorRecord} instance expected as Full or Compact.
	 * @return The sensor number of the record otherwise -1 if cannot determine the record type.
	 */
	private int getReadingId(final SensorRecord sensorRecord) {
		// We check if the received record is either FullSensorRecord or
		// CompactSensorRecord, since these types have readings
		// associated with them (see IPMI specification for details).
		if (sensorRecord instanceof FullSensorRecord) {
			FullSensorRecord fsr = (FullSensorRecord) sensorRecord;
			return TypeConverter.byteToInt(fsr.getSensorNumber());

		} else if (sensorRecord instanceof CompactSensorRecord) {
			CompactSensorRecord csr = (CompactSensorRecord) sensorRecord;
			return TypeConverter.byteToInt(csr.getSensorNumber());
		}

		return -1;
	}
}
