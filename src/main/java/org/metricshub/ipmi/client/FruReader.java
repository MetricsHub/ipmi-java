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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.metricshub.ipmi.core.coding.commands.sdr.record.ManagementControllerDeviceLocatorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanConstants;

/**
 * Reads the FRUs that the SDR repository locates, one at a time: the inventory of each logical FRU Device Locator
 * record, from the controller and at the LUN the record names (IPMI 2.0 table 43-7), the FRU of the system board
 * (device 0 of the BMC), and device 0 of each other controller whose Management Controller Device Locator record says
 * it holds one (table 43-8).
 */
final class FruReader {

	/** ID of the built-in FRU, the one of the system board on most BMCs. */
	static final int SYSTEM_BOARD_FRU_ID = 0;

	/**
	 * Bytes asked of the first Read FRU Data of a controller: the most the count byte can carry. Supermicro, Lenovo
	 * TSM, GIGABYTE, Fujitsu iRMC and Dell iDRAC 6 answer it; iLO 5 refuses it (C8h) and answers 240; Lenovo XCC and
	 * Dell F200 refuse it (C9h); Dell iDRAC 8, Cisco IMC and Dell F200 refuse everything above 16 (CAh).
	 */
	private static final int MAX_READ_SIZE = 255;

	/** Bytes a Read FRU Data goes down to before a refusal is taken for a failure: every BMC answers 16. */
	private static final int MIN_READ_SIZE = 16;

	/** Completion code C7h, the first of the four that say the count asked is too large. */
	private static final int REQUEST_DATA_LENGTH_INVALID = 0xC7;

	/** Completion code CAh, the last of the four that say the count asked is too large. */
	private static final int CANNOT_RETURN_REQUESTED_BYTES = 0xCA;

	/** Name of the system board FRU when none of its areas names it. */
	private static final String SYSTEM_BOARD_NAME = "System Board";

	/** Key of FRU 0 of the BMC at LUN 0 in {@link #returned}. */
	private static final int SYSTEM_BOARD_KEY = key(IpmiLanConstants.BMC_ADDRESS, 0, SYSTEM_BOARD_FRU_ID);

	private final List<SensorRecord> records;
	private final Set<Integer> selectedIds;
	/**
	 * The session the current read goes through, given by each {@link #next(Exchange)}: a new one after a lost session.
	 */
	private Exchange exchange;
	/**
	 * The FRUs returned so far, by access address, access LUN and device ID: a FRU of another controller or LUN may
	 * have the ID of a FRU of the BMC.
	 */
	private final Set<Integer> returned = new HashSet<Integer>();
	/** The controllers behind the BMC, and their LUNs, that did not answer: the walk does not wait for them again. */
	private final Set<Integer> silentControllers = new HashSet<Integer>();
	/** The bytes each controller and LUN answers to one Read FRU Data, once a refusal made the reader back off. */
	private final Map<Integer, Integer> readSizes = new HashMap<Integer, Integer>();
	/** The areas of FRU 0 of the BMC, read when a record first needs them or after the pass; null until then. */
	private List<FruRecord> systemBoard;
	private List<String> systemBoardProblems = Collections.emptyList();
	/** The next record of the pass over the FRU Device Locator records. */
	private int index;
	/** The next record of the pass over the Management Controller Device Locator records. */
	private int controllerIndex;

	/**
	 * Prepares the read of the FRUs located by the records of the repository, in their order.
	 *
	 * @param records the records of the SDR repository
	 * @param ids the FRU device IDs to read, null for all of them
	 */
	FruReader(List<SensorRecord> records, Set<Integer> ids) {
		this.records = records;
		this.selectedIds = ids;
	}

	/**
	 * Reads the next FRU, each once. A read that fails leaves its FRU as the next one.
	 *
	 * @param session the session to read it from
	 * @return the FRU, null after the last one
	 * @throws IpmiClientException when the BMC does not answer a read after all the tries (a read it refuses only
	 *         costs that FRU, which is returned with the problem)
	 */
	Fru next(Exchange session) {
		exchange = session;
		while (index < records.size()) {
			Fru fru = locate(records.get(index));
			index++;
			if (fru != null) {
				return fru;
			}
		}
		// FRU 0 of a controller is never located by a FRU Device Locator (IPMI 2.0 table 43-7, byte 7): one the BMC
		// serves is returned whatever the records, not gated on its MC Device Locator, which Supermicro and Fujitsu
		// iRMC clear while serving FRU 0
		if (!systemBoard().isEmpty() && returned.add(SYSTEM_BOARD_KEY)) {
			FruDeviceLocatorRecord locator = bmcLocator();
			locator.setFruEntityId(EntityId.SystemBoard.getCode());
			locator.setName(systemBoardName(systemBoard));
			return new Fru(locator, systemBoard, systemBoardProblems);
		}
		// FRU 0 of the other controllers whose MC Device Locator sets FRU Inventory Device (table 43-8, byte 9 bit 3),
		// read through the BMC, as ipmitool fru print does
		while (isSelected(SYSTEM_BOARD_FRU_ID) && controllerIndex < records.size()) {
			Fru fru = controllerFru0(records.get(controllerIndex));
			controllerIndex++;
			if (fru != null) {
				return fru;
			}
		}
		return null;
	}

	/** Returns the FRU a record of the first pass yields, read from its locator, or null when it yields none. */
	private Fru locate(SensorRecord record) {
		if (record instanceof FruDeviceLocatorRecord) {
			FruDeviceLocatorRecord locator = (FruDeviceLocatorRecord) record;
			if (!locates(locator)) {
				return null;
			}
			int key = key(locator);
			Fru fru = key == SYSTEM_BOARD_KEY ? new Fru(locator, systemBoard(), systemBoardProblems) : read(locator);
			returned.add(key);
			return fru;
		}
		if (anchorsSystemBoard(record) && (!systemBoard().isEmpty() || !systemBoardProblems.isEmpty())) {
			// No locator pointed at FRU 0 so far: build one for the system board, named after whichever area
			// describes it
			CompactSensorRecord sensor = (CompactSensorRecord) record;
			FruDeviceLocatorRecord locator = bmcLocator();
			locator.setFruEntityId(EntityId.SystemBoard.getCode());
			locator.setFruEntityInstance(sensor.getEntityInstanceNumber());
			locator.setName(systemBoardName(systemBoard) + " " + sensor.getEntityInstanceNumber());
			returned.add(SYSTEM_BOARD_KEY);
			return new Fru(locator, systemBoard, systemBoardProblems);
		}
		return null;
	}

	/**
	 * Tells, without a command, whether no FRU remains: false when a record may still yield one, FRU 0 of the BMC
	 * included while it is not read.
	 */
	boolean exhausted() {
		boolean fru0 = !returned.contains(SYSTEM_BOARD_KEY)
				&& (systemBoard == null ?
						isSelected(SYSTEM_BOARD_FRU_ID) :
						!systemBoard.isEmpty() || !systemBoardProblems.isEmpty());
		for (int i = index; i < records.size(); i++) {
			SensorRecord record = records.get(i);
			if (record instanceof FruDeviceLocatorRecord ?
					locates((FruDeviceLocatorRecord) record) :
					fru0 && anchorsSystemBoard(record)) {
				return false;
			}
		}
		if (fru0) {
			return false;
		}
		for (int i = controllerIndex; isSelected(SYSTEM_BOARD_FRU_ID) && i < records.size(); i++) {
			if (holdsFru0(records.get(i))) {
				return false;
			}
		}
		return true;
	}

	/** Tells whether a FRU Device Locator record yields a FRU: a logical, selected one not returned yet. */
	private boolean locates(FruDeviceLocatorRecord locator) {
		return locator.isLogical() && isSelected(locator.getDeviceId()) && !returned.contains(key(locator));
	}

	/**
	 * Tells whether a record is a Compact sensor of the system board, which stands for FRU 0 of the BMC when no
	 * locator points at it and it was not returned yet.
	 */
	private boolean anchorsSystemBoard(SensorRecord record) {
		return record instanceof CompactSensorRecord
				&& EntityId.SystemBoard.equals(((CompactSensorRecord) record).getEntityId())
				&& !returned.contains(SYSTEM_BOARD_KEY);
	}

	/** Tells whether a record is an MC Device Locator whose controller holds a FRU 0 (table 43-8, byte 9 bit 3). */
	private static boolean holdsFru0(SensorRecord record) {
		return record instanceof ManagementControllerDeviceLocatorRecord
				&& ((ManagementControllerDeviceLocatorRecord) record).isSupportsFruInventoryDevice();
	}

	/** Returns FRU 0 of the controller an MC Device Locator record describes, or null when it holds none. */
	private Fru controllerFru0(SensorRecord record) {
		if (!holdsFru0(record)) {
			return null;
		}
		ManagementControllerDeviceLocatorRecord controller = (ManagementControllerDeviceLocatorRecord) record;
		FruDeviceLocatorRecord locator = bmcLocator();
		locator.setDeviceAccessAddress(controller.getDeviceAddress());
		locator.setManagementChannelNumber(controller.getChannelNumber());
		locator.setFruEntityId(controller.getEntityId());
		locator.setFruEntityInstance(controller.getEntityInstance());
		locator.setName(controller.getName());
		if (!isBridged(locator) || returned.contains(key(locator))) {
			return null;
		}
		Fru fru = read(locator);
		returned.add(key(locator));
		return fru;
	}

	/**
	 * The areas of FRU 0 of the BMC, read on the first call: FRU 0 describes the system board on most BMCs, not all of
	 * them expose it, and the walk must not depend on it. Read no earlier than its turn, so that a stream stopped
	 * before pays nothing for it; a read that fails, or that its caller gave up, leaves it unread for the next call.
	 */
	private List<FruRecord> systemBoard() {
		if (systemBoard == null) {
			List<String> problems = new ArrayList<String>();
			List<FruRecord> areas = isSelected(SYSTEM_BOARD_FRU_ID) ?
					inventory(bmcLocator(), problems) :
					Collections.<FruRecord>emptyList();
			IpmiClient.failIfInterrupted("FRU " + SYSTEM_BOARD_FRU_ID);
			systemBoardProblems = problems;
			systemBoard = areas;
		}
		return systemBoard;
	}

	private Fru read(FruDeviceLocatorRecord locator) {
		List<String> problems = new ArrayList<String>();
		List<FruRecord> areas = inventory(locator, problems);
		IpmiClient.failIfInterrupted("FRU " + locator.getDeviceId());
		return new Fru(locator, areas, problems);
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
		int controller = (bridged ? locator.getManagementChannelNumber() : 0) << 10 | address(locator) << 2 | lun;
		String where = bridged ? " from " + describe(locator) : "";
		if (bridged && silentControllers.contains(controller)) {
			problems.add("Cannot read FRU " + fruId + where + ": No reply through the BMC");
			return Collections.emptyList();
		}
		List<ReadFruDataResponseData> chunks = new ArrayList<ReadFruDataResponseData>();
		int offset = 0;
		GetFruInventoryAreaInfoResponseData info = null;
		try {
			info = send(locator, new GetFruInventoryAreaInfo(fruId, lun));
			int size = info.getFruInventoryAreaSize();
			BaseUnit unit = info.getFruUnit();
			// The inventory is read in chunks of as many bytes as the controller answers at once: the first read asks
			// for the most, and a refusal brings the count down to the power of two below it, as ipmitool backs off,
			// to the least every BMC answers. The count a controller accepted serves its other FRUs. A word-addressed
			// device (offsets sent in words) is read an even number of bytes at a time, but for the last read
			int readSize = readSizes.getOrDefault(controller, MAX_READ_SIZE);
			if (unit == BaseUnit.Words) {
				readSize &= ~1;
			}
			while (offset < size) {
				int count = Math.min(readSize, size - offset);
				ReadFruDataResponseData chunk;
				try {
					chunk = send(locator, new ReadFruData(fruId, unit, offset, count, lun));
				} catch (IpmiCompletionCodeException e) {
					if (count > MIN_READ_SIZE && isTooLong(e.rawCompletionCode())) {
						readSize = Math.max(MIN_READ_SIZE, Integer.highestOneBit(count - 1));
						readSizes.put(controller, readSize);
						continue;
					}
					throw e;
				}
				int read = chunk.getFruData().length;
				if (read == 0) {
					// No data at an offset within the size the controller declared: the rest cannot be read
					problems.add(prefix(fruId, offset) + where + ": No data returned");
					break;
				}
				chunks.add(chunk);
				offset += read;
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
			problems.add(prefix(fruId, offset) + where + ": No reply through the BMC");
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
					reason = IPMIException.describe(meaning, code);
				}
			}
			problems.add(prefix(fruId, offset) + where + ": " + reason);
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

	/**
	 * Tells whether a completion code of Read FRU Data says the count asked exceeds what the controller returns at
	 * once: request data length invalid (C7h), request data field length limit exceeded (C8h), parameter out of range
	 * (C9h, Lenovo XCC and Dell F200) or cannot return number of requested data bytes (CAh, IPMI 2.0 table 5-2).
	 */
	private static boolean isTooLong(int completionCode) {
		return completionCode >= REQUEST_DATA_LENGTH_INVALID && completionCode <= CANNOT_RETURN_REQUESTED_BYTES;
	}

	private static String prefix(int fruId, int bytesRead) {
		return bytesRead == 0 ? "Cannot read FRU " + fruId : "FRU " + fruId + " is truncated at offset " + bytesRead;
	}
}
