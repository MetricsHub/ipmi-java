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
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.MultiRecordInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.PiromInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.SpdInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A Field Replaceable Unit of the system: the board, chassis, product and multirecord areas of its FRU inventory
 * (IPMI Platform Management FRU Information Storage Definition), or the SPD data of a memory module, or the
 * Processor Information ROM of a processor, located by an SDR record. A FRU that could not be read entirely is not
 * {@link #complete()} and lists its {@link #problems()}.
 */
// EI_EXPOSE_REP also matches EI_EXPOSE_REP2
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The decoded areas and locator record of the protocol "
		+ "core are handed out by reference for the callers that need their every field")
public final class Fru {

	private final FruDeviceLocatorRecord locator;
	private final List<FruRecord> records;
	private final List<String> problems;

	/**
	 * Creates the FRU.
	 *
	 * @param locator the SDR record that locates the FRU
	 * @param records the areas decoded from the FRU inventory
	 * @param problems why the FRU could not be read entirely, empty when it was
	 */
	Fru(FruDeviceLocatorRecord locator, List<FruRecord> records, List<String> problems) {
		this.locator = locator;
		this.records = Collections.unmodifiableList(new ArrayList<FruRecord>(records));
		this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
	}

	/**
	 * Returns the FRU device ID, 0 for the FRU of the system board.
	 *
	 * @return the device ID
	 */
	public int id() {
		return locator.getDeviceId();
	}

	/**
	 * Returns the name of the FRU, from the ID string of its locator record.
	 *
	 * @return the name
	 */
	public String name() {
		return locator.getName();
	}

	/**
	 * Returns the entity the FRU is part of.
	 *
	 * @return the entity, {@link EntityId#Other} for an OEM entity ID
	 */
	public EntityId entity() {
		return EntityId.parseInt(locator.getFruEntityId());
	}

	/**
	 * Returns the instance of the entity the FRU is part of: 1 for the first power supply, 2 for the second one.
	 *
	 * @return the entity instance, 0 to 255
	 */
	public int entityInstance() {
		return locator.getFruEntityInstance();
	}

	/**
	 * Returns the manufacturer, from the product area, or from the board area when the product area does not name it;
	 * the module manufacturer of a memory module; Intel for a processor.
	 *
	 * @return the manufacturer, empty when no area names it
	 */
	public String manufacturer() {
		return first(
				ProductInfo::getManufacturerName,
				BoardInfo::getBoardManufacturer,
				SpdInfo::getManufacturer,
				pirom -> "Intel");
	}

	/**
	 * Returns the model, from the product name of the product area, or from the board product name when the product
	 * area does not give it; the part number of a memory module; the S-spec of a processor.
	 *
	 * @return the model, empty when no area gives it
	 */
	public String model() {
		return first(
				ProductInfo::getProductName,
				BoardInfo::getBoardProductName,
				SpdInfo::getPartNumber,
				PiromInfo::getSSpec);
	}

	/**
	 * Returns the serial number, from the product area, or from the board area when the product area does not give it;
	 * the one of a memory module; the PPIN of a processor.
	 *
	 * @return the serial number, empty when no area gives it
	 */
	public String serialNumber() {
		return first(
				ProductInfo::getProductSerialNumber,
				BoardInfo::getBoardSerialNumber,
				SpdInfo::getSerialNumber,
				PiromInfo::getPpin);
	}

	/**
	 * Returns the part number: the product model number of the product area, or the board part number when the
	 * product area does not give it; the part number of a memory module or of a processor.
	 *
	 * @return the part number, empty when no area gives it
	 */
	public String partNumber() {
		return first(
				ProductInfo::getProductModelNumber,
				BoardInfo::getBoardPartNumber,
				SpdInfo::getPartNumber,
				PiromInfo::getPartNumber);
	}

	private String first(
			Function<ProductInfo, String> fromProduct,
			Function<BoardInfo, String> fromBoard,
			Function<SpdInfo, String> fromSpd,
			Function<PiromInfo, String> fromPirom) {
		String value = product().map(fromProduct).orElse(null);
		if (value == null || value.trim().isEmpty()) {
			value = board().map(fromBoard).orElse(null);
		}
		if (value == null || value.trim().isEmpty()) {
			value = spd().map(fromSpd).orElse(null);
		}
		if (value == null || value.trim().isEmpty()) {
			value = pirom().map(fromPirom).orElse(null);
		}
		return value == null ? "" : value.trim();
	}

	/**
	 * Returns the board info area, as the protocol core decodes it.
	 *
	 * @return the board area, empty when the FRU has none or it could not be decoded
	 */
	public Optional<BoardInfo> board() {
		return area(BoardInfo.class);
	}

	/**
	 * Returns the product info area, as the protocol core decodes it.
	 *
	 * @return the product area, empty when the FRU has none or it could not be decoded
	 */
	public Optional<ProductInfo> product() {
		return area(ProductInfo.class);
	}

	/**
	 * Returns the chassis info area, as the protocol core decodes it.
	 *
	 * @return the chassis area, empty when the FRU has none or it could not be decoded
	 */
	public Optional<ChassisInfo> chassis() {
		return area(ChassisInfo.class);
	}

	/**
	 * Returns the SPD data of a memory module, as the protocol core decodes it: the FRU device of a DIMM returns the
	 * JEDEC SPD data of the module (type, capacity, manufacturer, part and serial numbers) instead of a FRU inventory.
	 *
	 * @return the SPD data, empty when the FRU is not a memory module or its SPD data could not be decoded
	 */
	public Optional<SpdInfo> spd() {
		return area(SpdInfo.class);
	}

	/**
	 * Returns the Processor Information ROM of a processor, as the protocol core decodes it: the FRU device of a
	 * processor cartridge returns the Intel PIROM of the CPU (S-spec, cores, frequencies, caches, part number, TDP,
	 * PPIN) instead of a FRU inventory.
	 *
	 * @return the PIROM, empty when the FRU is not a processor or its ROM could not be decoded
	 */
	public Optional<PiromInfo> pirom() {
		return area(PiromInfo.class);
	}

	private <T extends FruRecord> Optional<T> area(Class<T> type) {
		for (FruRecord record : records) {
			if (type.isInstance(record)) {
				return Optional.of(type.cast(record));
			}
		}
		return Optional.empty();
	}

	/**
	 * Returns the records of the multirecord area (power supply information, DC output, management access...), as the
	 * protocol core decodes them.
	 *
	 * @return the multirecords, empty when the FRU has none
	 */
	public List<MultiRecordInfo> multiRecords() {
		List<MultiRecordInfo> multiRecords = new ArrayList<MultiRecordInfo>();
		for (FruRecord record : records) {
			if (record instanceof MultiRecordInfo) {
				multiRecords.add((MultiRecordInfo) record);
			}
		}
		return Collections.unmodifiableList(multiRecords);
	}

	/**
	 * Returns the records decoded from the FRU inventory: the internal use area
	 * ({@link org.metricshub.ipmi.core.coding.commands.fru.record.InternalUseInfo}), the chassis, board and product
	 * areas, then the multirecords, each when the inventory has it and it could be decoded. For a memory module or a
	 * processor whose FRU device returns its SPD data or its Processor Information ROM, the list holds the single
	 * {@link SpdInfo} or {@link PiromInfo} instead.
	 *
	 * @return the records, in that order
	 */
	public List<FruRecord> records() {
		return records;
	}

	/**
	 * Returns the SDR record that locates the FRU, as the protocol core decodes it.
	 *
	 * @return the locator record
	 */
	public FruDeviceLocatorRecord locator() {
		return locator;
	}

	/**
	 * Tells whether the FRU inventory was read and decoded entirely.
	 *
	 * @return false when a read failed or an area could not be decoded: {@link #problems()} says what
	 */
	public boolean complete() {
		return problems.isEmpty();
	}

	/**
	 * Returns what went wrong while reading the FRU: a read refused by the BMC (with its completion code), a read
	 * without reply, data truncated at an offset, an area that could not be decoded.
	 *
	 * @return the problems, empty when the FRU is {@link #complete()}
	 */
	public List<String> problems() {
		return problems;
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder("FRU ")
				.append(id())
				.append(' ')
				.append(name())
				.append(" (")
				.append(entity())
				.append(' ')
				.append(entityInstance())
				.append("): ")
				.append(manufacturer())
				.append(' ')
				.append(model())
				.append(" S/N ")
				.append(serialNumber());
		if (!complete()) {
			text.append(", incomplete: ").append(problems);
		}
		return text.toString();
	}
}
