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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.metricshub.ipmi.core.coding.commands.fru.BaseUnit;
import org.metricshub.ipmi.core.coding.commands.fru.GetFruInventoryAreaInfo;
import org.metricshub.ipmi.core.coding.commands.fru.GetFruInventoryAreaInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.ReadFruData;
import org.metricshub.ipmi.core.coding.commands.fru.ReadFruDataResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.DeviceType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;

/**
 * Reads the FRUs that the SDR repository locates: the inventory of each logical FRU Device Locator record, and the
 * FRU of the system board (device 0) when a System Board sensor record is met before any locator points at it.
 */
final class FruReader {

	/** ID of the built-in FRU, the one of the system board on most BMCs. */
	static final int SYSTEM_BOARD_FRU_ID = 0;

	/**
	 * Bytes read by one Read FRU Data: BMCs answer "Invalid data field in request" above this.
	 */
	private static final int READ_SIZE = 16;

	/** Name of the system board FRU when none of its areas names it. */
	private static final String SYSTEM_BOARD_NAME = "System Board";

	/** Device type modifier of a FRU inventory device that is a System Processor Cartridge FRU / PIROM. */
	private static final int PIROM_MODIFIER = 0x03;

	private final Exchange exchange;
	private final Set<Integer> selectedIds;
	private final Set<Integer> returnedIds = new HashSet<Integer>();
	private final List<Fru> frus = new ArrayList<Fru>();

	private FruReader(Exchange exchange, Set<Integer> selectedIds) {
		this.exchange = exchange;
		this.selectedIds = selectedIds;
	}

	/**
	 * Reads the FRUs located by the records of the repository, in their order.
	 *
	 * @param exchange the session to read them from
	 * @param records the records of the SDR repository
	 * @param ids the FRU device IDs to read, null for all of them
	 * @return the FRUs, each once
	 * @throws IpmiClientException when the BMC does not answer a read after all the tries (a read it refuses only
	 *         costs that FRU, which is returned with the problem)
	 */
	static List<Fru> read(Exchange exchange, List<SensorRecord> records, Set<Integer> ids) {
		FruReader reader = new FruReader(exchange, ids);
		reader.read(records);
		return reader.frus;
	}

	private void read(List<SensorRecord> records) {
		// FRU 0 describes the system board on most BMCs; not all of them expose it, and the walk must not depend on it
		List<String> systemBoardProblems = new ArrayList<String>();
		List<FruRecord> systemBoard = isSelected(SYSTEM_BOARD_FRU_ID) ?
				inventory(SYSTEM_BOARD_FRU_ID, systemBoardProblems) : Collections.<FruRecord>emptyList();
		for (SensorRecord record : records) {
			if (record instanceof FruDeviceLocatorRecord) {
				FruDeviceLocatorRecord locator = (FruDeviceLocatorRecord) record;
				int id = locator.getDeviceId();
				if (locator.isLogical() && isSelected(id) && returnedIds.add(id)) {
					if (id == SYSTEM_BOARD_FRU_ID) {
						frus.add(new Fru(locator, systemBoard, systemBoardProblems));
					} else {
						List<String> problems = new ArrayList<String>();
						frus.add(new Fru(locator, inventory(locator, problems), problems));
					}
				}
			} else
				if ((!systemBoard.isEmpty() || !systemBoardProblems.isEmpty())
						&& !returnedIds.contains(SYSTEM_BOARD_FRU_ID)
						&& record instanceof CompactSensorRecord
						&& EntityId.SystemBoard.equals(((CompactSensorRecord) record).getEntityId())) {
							// No locator pointed at FRU 0 so far: build one for the system board, named after whichever area
							// describes it
							CompactSensorRecord sensor = (CompactSensorRecord) record;
							FruDeviceLocatorRecord locator = new FruDeviceLocatorRecord();
							locator.setDeviceId(SYSTEM_BOARD_FRU_ID);
							locator.setLogical(true);
							locator.setFruEntityId(EntityId.SystemBoard.getCode());
							locator.setFruEntityInstance(sensor.getEntityInstanceNumber());
							locator.setName(systemBoardName(systemBoard) + " " + sensor.getEntityInstanceNumber());
							frus.add(new Fru(locator, systemBoard, systemBoardProblems));
							returnedIds.add(SYSTEM_BOARD_FRU_ID);
						}
		}
	}

	private boolean isSelected(int id) {
		return selectedIds == null || selectedIds.contains(id);
	}

	/**
	 * Returns the name of the system board from its board, product or chassis area, whichever exists.
	 */
	static String systemBoardName(List<FruRecord> records) {
		// In priority order, whatever the order of the areas in the FRU: board, then product, then chassis
		String name = firstName(records, BoardInfo.class, BoardInfo::getBoardProductName);
		if (name == null) {
			name = firstName(records, ProductInfo.class, ProductInfo::getProductName);
		}
		if (name == null) {
			name = firstName(records, ChassisInfo.class, ChassisInfo::getChassisPartNumber);
		}
		return name == null ? SYSTEM_BOARD_NAME : name;
	}

	private static <T extends FruRecord> String firstName(
			List<FruRecord> records,
			Class<T> type,
			Function<T, String> getter) {
		for (FruRecord record : records) {
			if (type.isInstance(record)) {
				String name = getter.apply(type.cast(record));
				if (name != null && !name.trim().isEmpty()) {
					return name;
				}
			}
		}
		return null;
	}

	/**
	 * Reads and decodes the inventory of a located FRU, except the processor information ROM (PIROM) of a processor
	 * cartridge (device type 10h with modifier 03h, IPMI 2.0 table 43-12): an Intel format the library does not
	 * decode, so the FRU is reported without reading it.
	 */
	private List<FruRecord> inventory(FruDeviceLocatorRecord locator, List<String> problems) {
		if (locator.getDeviceType() == DeviceType.FRUInventoryDevice
				&& locator.getDeviceTypeModifier() == PIROM_MODIFIER) {
			problems
					.add(
							"FRU " + locator.getDeviceId()
									+ " is the processor information ROM (PIROM) of a processor, which is not decoded");
			return Collections.emptyList();
		}
		return inventory(locator.getDeviceId(), problems);
	}

	/**
	 * Reads and decodes the inventory of a FRU. A read the BMC refuses (absent FRU, timeout on its side), a reply it
	 * malforms or an inventory that cannot be decoded costs that FRU, which is reported with the problem, not the
	 * whole walk; a BMC that does not answer at all fails the walk.
	 */
	private List<FruRecord> inventory(int fruId, List<String> problems) {
		List<ReadFruDataResponseData> chunks = new ArrayList<ReadFruDataResponseData>();
		try {
			GetFruInventoryAreaInfoResponseData info = exchange.send(new GetFruInventoryAreaInfo(fruId));
			int size = info.getFruInventoryAreaSize();
			BaseUnit unit = info.getFruUnit();
			// The inventory is read in chunks, as its size exceeds what one message carries
			for (int offset = 0; offset < size; offset += READ_SIZE) {
				chunks.add(exchange.send(new ReadFruData(fruId, unit, offset, Math.min(READ_SIZE, size - offset))));
			}
		} catch (IpmiTimeoutException | IpmiConnectionException e) {
			// The BMC stopped answering: the walk cannot go on
			throw e;
		} catch (IpmiClientException e) {
			// A read the BMC refused, or a reply it malformed. Stop here: the chunks after a gap would shift into its
			// place and decode into wrong fields
			problems
					.add(
							chunks.isEmpty() ?
									"Cannot read FRU " + fruId + ": " + e.getMessage() :
									"FRU " + fruId + " is truncated at offset " + chunks.size() * READ_SIZE + ": " + e.getMessage());
		}
		if (chunks.isEmpty()) {
			return Collections.emptyList();
		}
		try {
			return ReadFruData.decodeFruData(chunks, problems);
		} catch (RuntimeException e) {
			problems.add("Cannot decode FRU " + fruId + ": " + e.getMessage());
			return Collections.emptyList();
		}
	}
}
