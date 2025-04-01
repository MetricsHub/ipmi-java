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
import java.util.stream.Collectors;

import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.client.model.Fru;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.fru.BaseUnit;
import org.metricshub.ipmi.core.coding.commands.fru.GetFruInventoryAreaInfo;
import org.metricshub.ipmi.core.coding.commands.fru.GetFruInventoryAreaInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.ReadFruData;
import org.metricshub.ipmi.core.coding.commands.fru.ReadFruDataResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepository;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepositoryResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;

/**
 * Get FRU information
 */
public class GetFrusRunner extends AbstractIpmiRunner<List<Fru>> {

	/**
	 * Id of the built-in, default FRU
	 */
	private static final int DEFAULT_FRU_ID = 0;

	/**
	 * Size of data transmitted in single ReadFru command. Bigger values will improve performance. If server is returning "Invalid data field in
	 * Request." error during ReadFru command, FRU_READ_PACKET_SIZE should be decreased.
	 */
	private static final int FRU_READ_PACKET_SIZE = 16;

	private boolean systemBoardFruUpdated = false;

	public GetFrusRunner(IpmiClientConfiguration ipmiConfiguration) {
		super(ipmiConfiguration);
	}

	@Override
	public List<Fru> call() throws Exception {
		final List<Fru> result = new ArrayList<>();

		super.startSession();

		// Id 0 indicates first record in SDR. Next IDs can be retrieved from
		// records - they are organized in a list and there is no BMC command to
		// get all of them.
		nextRecId = 0;

		// Some BMCs allow getting sensor records without reservation, so we try
		// to do it that way first
		int reservationId = 0;
		int lastReservationId = -1;

		List<FruRecord> systemBoardFruRecords = getFruRecords(DEFAULT_FRU_ID);

		// We get sensor data until we encounter ID = 65535 which means that
		// this record is the last one.
		while (nextRecId < MAX_REPO_RECORD_ID) {

			SensorRecord sensorRecord = null;

			try {
				// Populate the sensor record and get ID of the next record in
				// repository (see #getSensorData for details).
				sensorRecord = super.getSensorData(reservationId);

				processFruRecord(result, sensorRecord, systemBoardFruRecords);

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
	 * Process the given sensor record and create the system board FRU record. The new {@link Fru} is added to th FRU list <code>result</code>
	 * 
	 * @param result                List of {@link Fru} instance to append
	 * @param sensorRecord          The sensor record to process
	 * @param systemBoardFruRecords The system board Fru records
	 * @throws Exception
	 */
	private void processFruRecord(final List<Fru> result, final SensorRecord sensorRecord, final List<FruRecord> systemBoardFruRecords) throws Exception {
		try {
			// Process the FRU record
			Fru fru = null;
			if (sensorRecord instanceof FruDeviceLocatorRecord) {
				FruDeviceLocatorRecord fruLocator = (FruDeviceLocatorRecord) sensorRecord;

				if (fruLocator.isLogical()) {
					List<FruRecord> fruRecords = getFruRecords(fruLocator.getDeviceId());

					if (!fruRecords.isEmpty()) {
						fru = new Fru(fruLocator, fruRecords);
					}
				}
			} else if (!systemBoardFruRecords.isEmpty()
					&& !systemBoardFruUpdated
					&& sensorRecord instanceof CompactSensorRecord
					&& ((CompactSensorRecord) sensorRecord).getEntityId().equals(EntityId.SystemBoard)) {

				// Since we can only access the SystemBoard components,
				// we need to build the FruDeviceLocatorRecord for SystemBoard instance. 

				// OK this can be one of the SystemBoard sensors
				CompactSensorRecord compactSensorRecord = (CompactSensorRecord) sensorRecord;

				BoardInfo boardInfo = systemBoardFruRecords.stream()
						.filter(BoardInfo.class::isInstance)
						.map(BoardInfo.class::cast)
						.findFirst()
						.orElse(null);

				if (boardInfo != null) {

					// Create the Fru locator
					FruDeviceLocatorRecord locator = new FruDeviceLocatorRecord();
					locator.setFruEntityId(EntityId.SystemBoard.getCode());
					locator.setFruEntityInstance(compactSensorRecord.getEntityInstanceNumber());
					locator.setName(boardInfo.getBoardProductName() + " " + compactSensorRecord.getEntityInstanceNumber());

					fru = new Fru(locator, systemBoardFruRecords);

					// OK, now we are good!
					systemBoardFruUpdated = true;

				}
			}

			// Add the Fru instance
			if (fru != null) {
				result.add(fru);
			}

		} catch (IPMIException e) {
			// Nothing can be done
		}

	}

	/**
	 * Get the FRU records for the given FRU identifier <code>fruId</code>
	 * 
	 * @param fruId The unique identifier of the FRU
	 * @return new List of {@link FruRecord} instances
	 * @throws Exception
	 */
	private List<FruRecord> getFruRecords(int fruId) throws Exception {
		List<ReadFruDataResponseData> fruData = new ArrayList<>();

		// get the FRU Inventory Area info
		GetFruInventoryAreaInfoResponseData info = (GetFruInventoryAreaInfoResponseData) connector.sendMessage(handle,
				new GetFruInventoryAreaInfo(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus, fruId));

		int size = info.getFruInventoryAreaSize();
		BaseUnit unit = info.getFruUnit();

		// since the size of single FRU entry can exceed maximum size of the
		// message sent via IPMI, it has to be read in chunks
		for (int i = 0; i < size; i += FRU_READ_PACKET_SIZE) {
			int fruReadPacketSize = FRU_READ_PACKET_SIZE;
			if (i + fruReadPacketSize > size) {
				fruReadPacketSize = size % FRU_READ_PACKET_SIZE;
			}
			try {
				// get single package od FRU data
				ReadFruDataResponseData data = (ReadFruDataResponseData) connector.sendMessage(handle,
						new ReadFruData(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus, fruId, unit, i, fruReadPacketSize));

				fruData.add(data);

			} catch (Exception e) {
				// Nothing can be done
			}
		}

		try {
			// after collecting all the data, we can combine and parse it
			return ReadFruData.decodeFruData(fruData).stream()
					.filter(fruRecord -> fruRecord instanceof BoardInfo || fruRecord instanceof ChassisInfo || fruRecord instanceof ProductInfo)
					.collect(Collectors.toList());

		} catch (Exception e) {
			// Nothing can be done
		}

		return new ArrayList<>();
	}
}
