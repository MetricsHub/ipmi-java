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

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.app.SendMessage;
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
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanConstants;

/**
 * Reads the FRUs that the SDR repository locates: the inventory of each logical FRU Device Locator record, from the
 * controller and at the LUN the record names (IPMI 2.0 table 43-7), and the FRU of the system board (device 0 of
 * the BMC) when a System Board sensor record is met before any locator points at it.
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

	/** Key of FRU 0 of the BMC at LUN 0 in {@link #returned}. */
	private static final int SYSTEM_BOARD_KEY = key(IpmiLanConstants.BMC_ADDRESS, 0, SYSTEM_BOARD_FRU_ID);

	private final Exchange exchange;
	private final Set<Integer> selectedIds;
	/**
	 * The FRUs returned so far, by access address, access LUN and device ID: a FRU of another controller or LUN may
	 * have the ID of a FRU of the BMC.
	 */
	private final Set<Integer> returned = new HashSet<Integer>();
	/** The controllers behind the BMC, and their LUNs, that did not answer: the walk does not wait for them again. */
	private final Set<Integer> silentControllers = new HashSet<Integer>();
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
				inventory(bmcLocator(), systemBoardProblems) : Collections.<FruRecord>emptyList();
		for (SensorRecord record : records) {
			if (record instanceof FruDeviceLocatorRecord) {
				FruDeviceLocatorRecord locator = (FruDeviceLocatorRecord) record;
				int key = key(locator);
				if (locator.isLogical() && isSelected(locator.getDeviceId()) && returned.add(key)) {
					if (key == SYSTEM_BOARD_KEY) {
						frus.add(new Fru(locator, systemBoard, systemBoardProblems));
					} else {
						List<String> problems = new ArrayList<String>();
						frus.add(new Fru(locator, inventory(locator, problems), problems));
					}
				}
			} else
				if ((!systemBoard.isEmpty() || !systemBoardProblems.isEmpty())
						&& !returned.contains(SYSTEM_BOARD_KEY)
						&& record instanceof CompactSensorRecord
						&& EntityId.SystemBoard.equals(((CompactSensorRecord) record).getEntityId())) {
							// No locator pointed at FRU 0 so far: build one for the system board, named after whichever area
							// describes it
							CompactSensorRecord sensor = (CompactSensorRecord) record;
							FruDeviceLocatorRecord locator = bmcLocator();
							locator.setFruEntityId(EntityId.SystemBoard.getCode());
							locator.setFruEntityInstance(sensor.getEntityInstanceNumber());
							locator.setName(systemBoardName(systemBoard) + " " + sensor.getEntityInstanceNumber());
							frus.add(new Fru(locator, systemBoard, systemBoardProblems));
							returned.add(SYSTEM_BOARD_KEY);
						}
		}
	}

	private boolean isSelected(int id) {
		return selectedIds == null || selectedIds.contains(id);
	}

	private static int key(int address, int lun, int id) {
		return address << 10 | lun << 8 | id;
	}

	/**
	 * Identifies the FRU a locator points at: by controller, LUN and ID, the controller being the BMC whatever the
	 * channel of the record, or another address on a channel.
	 */
	private static int key(FruDeviceLocatorRecord locator) {
		int channel = isBridged(locator) ? locator.getManagementChannelNumber() : 0;
		return channel << 18 | key(address(locator), locator.getAccessLun(), locator.getDeviceId());
	}

	/** A locator of FRU 0 of the BMC at LUN 0, for the system board when no record locates it. */
	private static FruDeviceLocatorRecord bmcLocator() {
		FruDeviceLocatorRecord locator = new FruDeviceLocatorRecord();
		locator.setDeviceAccessAddress(IpmiLanConstants.BMC_ADDRESS >> 1);
		locator.setDeviceId(SYSTEM_BOARD_FRU_ID);
		locator.setLogical(true);
		return locator;
	}

	/** Returns the access address of the record in its 8-bit form (IPMI 2.0 table 43-7, byte 6). */
	private static int address(FruDeviceLocatorRecord locator) {
		return locator.getDeviceAccessAddress() << 1;
	}

	/**
	 * Tells whether the FRU is on another controller than the BMC, whatever its channel (the Supermicro "BMC FRU" is at
	 * 20h on channel 2).
	 */
	private static boolean isBridged(FruDeviceLocatorRecord locator) {
		return address(locator) != IpmiLanConstants.BMC_ADDRESS;
	}

	private static String describe(FruDeviceLocatorRecord locator) {
		return String
				.format("the controller at %02Xh on channel %d", address(locator), locator.getManagementChannelNumber())
				+ (locator.getAccessLun() == 0 ? "" : ", LUN " + locator.getAccessLun());
	}

	/**
	 * Sends a FRU command to the controller that holds the FRU: the BMC itself, or another controller through the BMC
	 * (Send Message with response tracking, IPMI 2.0 section 6.13.3).
	 */
	private <R extends ResponseData> R send(FruDeviceLocatorRecord locator, IpmiCommandCoder<R> command) {
		return isBridged(locator) ?
				exchange.send(new SendMessage<R>(locator.getManagementChannelNumber(), address(locator), command)) :
				exchange.send(command);
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
	 * Reads and decodes the inventory of a FRU from the controller and at the LUN its locator names. A read the
	 * controller refuses (absent FRU, timeout on its side), a reply it malforms, an inventory that cannot be decoded
	 * or a controller behind the BMC that does not answer costs that FRU, which is reported with the problem, not the
	 * whole walk; a BMC that does not answer at all fails the walk.
	 */
	private List<FruRecord> inventory(FruDeviceLocatorRecord locator, List<String> problems) {
		int fruId = locator.getDeviceId();
		int lun = locator.getAccessLun();
		boolean bridged = isBridged(locator);
		int controller = locator.getManagementChannelNumber() << 10 | address(locator) << 2 | lun;
		String where = bridged ? " from " + describe(locator) : "";
		if (bridged && silentControllers.contains(controller)) {
			problems.add("Cannot read FRU " + fruId + where + ": No reply through the BMC");
			return Collections.emptyList();
		}
		List<ReadFruDataResponseData> chunks = new ArrayList<ReadFruDataResponseData>();
		GetFruInventoryAreaInfoResponseData info = null;
		try {
			info = send(locator, new GetFruInventoryAreaInfo(fruId, lun));
			int size = info.getFruInventoryAreaSize();
			BaseUnit unit = info.getFruUnit();
			// The inventory is read in chunks, as its size exceeds what one message carries
			for (int offset = 0; offset < size; offset += READ_SIZE) {
				chunks.add(send(locator, new ReadFruData(fruId, unit, offset, Math.min(READ_SIZE, size - offset), lun)));
			}
		} catch (IpmiTimeoutException e) {
			if (!bridged || exchange.isLost()) {
				// The BMC stopped answering: the walk cannot go on
				throw e;
			}
			// The controller behind the BMC did not answer (absent, or the BMC does not forward its response): the
			// session is fine, and the FRUs of the other controllers are read. A controller that answered the area
			// info and not a chunk only costs this FRU
			if (info == null) {
				silentControllers.add(controller);
			}
			problems.add(prefix(fruId, chunks.size()) + where + ": No reply through the BMC");
		} catch (IpmiConnectionException e) {
			throw e;
		} catch (IpmiClientException e) {
			// A read the controller refused, or a reply it malformed. Stop here: the chunks after a gap would shift into
			// its place and decode into wrong fields
			String reason = e.getMessage();
			if (bridged
					&& e instanceof IpmiCompletionCodeException
					&& ((IpmiCompletionCodeException) e).completionCode() == CompletionCode.Unknown) {
				// A code neither command knows: the BMC's own answer to Send Message. A code the carried command
				// knows (81h: FRU device busy) is the controller's, whatever Send Message makes of the same value
				int code = ((IpmiCompletionCodeException) e).rawCompletionCode();
				String meaning = SendMessage.describeCompletionCode(code);
				if (meaning != null) {
					reason = String.format("%s (completion code %02Xh)", meaning, code);
				}
			}
			problems.add(prefix(fruId, chunks.size()) + where + ": " + reason);
		}
		if (chunks.isEmpty()) {
			return Collections.emptyList();
		}
		try {
			return ReadFruData.decodeFruData(chunks, problems);
		} catch (RuntimeException e) {
			problems.add("Cannot decode FRU " + fruId + where + ": " + e.getMessage());
			return Collections.emptyList();
		}
	}

	private static String prefix(int fruId, int chunksRead) {
		return chunksRead == 0 ?
				"Cannot read FRU " + fruId :
				"FRU " + fruId + " is truncated at offset " + chunksRead * READ_SIZE;
	}
}
