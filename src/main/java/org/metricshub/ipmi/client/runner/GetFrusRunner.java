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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.client.Utils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Get FRU information
 */
public class GetFrusRunner extends AbstractIpmiRunner<List<Fru>> {

	private static final Logger LOGGER = LoggerFactory.getLogger(GetFrusRunner.class);

	/**
	 * Id of the built-in, default FRU
	 */
	private static final int DEFAULT_FRU_ID = 0;

	/**
	 * Size of data transmitted in single ReadFru command. Bigger values will improve performance. If server is returning
	 * "Invalid data field in
	 * Request." error during ReadFru command, FRU_READ_PACKET_SIZE should be decreased.
	 */
	private static final int FRU_READ_PACKET_SIZE = 16;

	/** Name of the system board FRU when none of its areas names it. */
	private static final String SYSTEM_BOARD_NAME = "System Board";

	/** Device IDs of the FRUs already returned: a FRU is returned once. */
	private final Set<Integer> returnedFruIds = new HashSet<>();

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
		setNextRecId(0);

		// Some BMCs allow getting sensor records without reservation, so we try
		// to do it that way first
		int reservationId = 0;
		int lastReservationId = -1;

		// FRU 0 describes the system board on most BMCs; not all of them expose it, and the SDR walk must not
		// depend on it
		List<FruRecord> systemBoardFruRecords = readFruRecords(DEFAULT_FRU_ID);

		// We get sensor data until we encounter ID = 65535 which means that
		// this record is the last one.
		while (getNextRecId() < MAX_REPO_RECORD_ID) {
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
				// happen many times during getting all sensors, since the BMC cannot
				// manage parallel sessions and invalidates old one if new one
				// appears.
				reservationId = ((ReserveSdrRepositoryResponseData) getConnector()
						.sendMessage(
								getHandle(),
								new ReserveSdrRepository(IpmiVersion.V20, getHandle().getCipherSuite(), AuthenticationType.RMCPPlus)))
						.getReservationId();
			}
		}

		return result;
	}

	/**
	 * Adds to the result the FRU a FRU Device Locator record points at, or FRU 0 attached to the system board when a
	 * System Board sensor record is met before any locator for it. A FRU is returned once.
	 */
	private void processFruRecord(
			final List<Fru> result,
			final SensorRecord sensorRecord,
			final List<FruRecord> systemBoardFruRecords)
			throws InterruptedException {

		if (sensorRecord instanceof FruDeviceLocatorRecord) {
			FruDeviceLocatorRecord fruLocator = (FruDeviceLocatorRecord) sensorRecord;
			int deviceId = fruLocator.getDeviceId();

			if (fruLocator.isLogical() && !returnedFruIds.contains(deviceId)) {
				List<FruRecord> fruRecords = deviceId == DEFAULT_FRU_ID ? systemBoardFruRecords : readFruRecords(deviceId);
				if (!fruRecords.isEmpty()) {
					result.add(new Fru(fruLocator, fruRecords));
					returnedFruIds.add(deviceId);
				}
			}
		} else
			if (!systemBoardFruRecords.isEmpty()
					&& !returnedFruIds.contains(DEFAULT_FRU_ID)
					&& sensorRecord instanceof CompactSensorRecord
					&& EntityId.SystemBoard.equals(((CompactSensorRecord) sensorRecord).getEntityId())) {
						// No locator pointed at FRU 0 so far: build one for the system board, named after whichever area
						// describes it
						CompactSensorRecord compactSensorRecord = (CompactSensorRecord) sensorRecord;

						FruDeviceLocatorRecord locator = new FruDeviceLocatorRecord();
						locator.setDeviceId(DEFAULT_FRU_ID);
						locator.setLogical(true);
						locator.setFruEntityId(EntityId.SystemBoard.getCode());
						locator.setFruEntityInstance(compactSensorRecord.getEntityInstanceNumber());
						locator
								.setName(systemBoardName(systemBoardFruRecords) + " " + compactSensorRecord.getEntityInstanceNumber());

						result.add(new Fru(locator, systemBoardFruRecords));
						returnedFruIds.add(DEFAULT_FRU_ID);
					}
	}

	/**
	 * @return the name of the system board from its board, product or chassis area, whichever exists
	 */
	static String systemBoardName(final List<FruRecord> fruRecords) {
		// In priority order, whatever the order of the areas in the FRU: board, then product, then chassis
		String name = firstName(fruRecords, BoardInfo.class, BoardInfo::getBoardProductName);
		if (name == null) {
			name = firstName(fruRecords, ProductInfo.class, ProductInfo::getProductName);
		}
		if (name == null) {
			name = firstName(fruRecords, ChassisInfo.class, ChassisInfo::getChassisPartNumber);
		}
		return name == null ? SYSTEM_BOARD_NAME : name;
	}

	private static <T extends FruRecord> String firstName(
			final List<FruRecord> fruRecords,
			final Class<T> type,
			final Function<T, String> getter) {
		return fruRecords
				.stream()
				.filter(type::isInstance)
				.map(type::cast)
				.map(getter)
				.filter(Utils::isNotBlank)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Reads a FRU; a FRU that cannot be read (absent, not answering, undecodable) is logged and reported empty, so
	 * that the other FRUs are still returned.
	 */
	private List<FruRecord> readFruRecords(int fruId) throws InterruptedException {
		try {
			return getFruRecords(fruId);
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.warn("Failed to read FRU {}: {}", fruId, e.getMessage());
			return new ArrayList<>();
		}
	}

	private List<FruRecord> getFruRecords(int fruId) throws Exception {
		List<ReadFruDataResponseData> fruData = new ArrayList<>();

		// get the FRU Inventory Area info
		GetFruInventoryAreaInfoResponseData info = (GetFruInventoryAreaInfoResponseData) getConnector()
				.sendMessage(
						getHandle(),
						new GetFruInventoryAreaInfo(
								IpmiVersion.V20,
								getHandle().getCipherSuite(),
								AuthenticationType.RMCPPlus,
								fruId));

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
				ReadFruDataResponseData data = (ReadFruDataResponseData) getConnector()
						.sendMessage(
								getHandle(),
								new ReadFruData(
										IpmiVersion.V20,
										getHandle().getCipherSuite(),
										AuthenticationType.RMCPPlus,
										fruId,
										unit,
										i,
										fruReadPacketSize));
				fruData.add(data);
			} catch (InterruptedException e) {
				throw e;
			} catch (Exception e) {
				// Stop here: the chunks after a gap would shift into its place and decode into wrong fields
				LOGGER
						.warn("Failed to read FRU {} at offset {}, the FRU data is truncated there: {}", fruId, i, e.getMessage());
				break;
			}
		}

		if (fruData.isEmpty()) {
			return new ArrayList<>();
		}

		// after collecting all the data, we can combine and parse it
		return ReadFruData
				.decodeFruData(fruData)
				.stream()
				.filter(
						fruRecord -> fruRecord instanceof BoardInfo
								|| fruRecord instanceof ChassisInfo
								|| fruRecord instanceof ProductInfo)
				.collect(Collectors.toList());
	}
}
