package org.metricshub.ipmi.core.coding.commands.fru.record;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, MetricsHub
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * The Serial Presence Detect (SPD) data of a memory module, which the FRU device of a DIMM returns instead of an IPMI
 * FRU inventory: the module type, capacity, manufacturer, part number and serial number, decoded from the JEDEC SPD
 * layouts of DDR3 (JEDEC Standard 21-C, annex K), DDR4 (annex L) and DDR5 (JESD400-5) modules.
 */
public class SpdInfo extends FruRecord {

	private static final int DDR3 = 0x0B;
	private static final int DDR4 = 0x0C;
	private static final int DDR5 = 0x12;

	/** The DRAM device types of SPD byte 2, as JEDEC names them. */
	private static final Map<Integer, String> MEMORY_TYPES = new HashMap<Integer, String>();

	private static final String[] DDR3_MODULE_TYPES = {
			null,
			"RDIMM",
			"UDIMM",
			"SO-DIMM",
			"Micro-DIMM",
			"Mini-RDIMM",
			"Mini-UDIMM",
			"Mini-CDIMM",
			"72b-SO-UDIMM",
			"72b-SO-RDIMM",
			"72b-SO-CDIMM",
			"LRDIMM",
			"16b-SO-DIMM",
			"32b-SO-DIMM" };
	private static final String[] DDR4_MODULE_TYPES = {
			null,
			"RDIMM",
			"UDIMM",
			"SO-DIMM",
			"LRDIMM",
			"Mini-RDIMM",
			"Mini-UDIMM",
			null,
			"72b-SO-RDIMM",
			"72b-SO-UDIMM",
			null,
			null,
			"16b-SO-DIMM",
			"32b-SO-DIMM" };
	private static final String[] DDR5_MODULE_TYPES = {
			null,
			"RDIMM",
			"UDIMM",
			"SO-DIMM",
			"LRDIMM",
			"CUDIMM",
			"CSODIMM",
			"MRDIMM",
			"CAMM2",
			null,
			"DDIMM",
			"Solder down" };

	/** JEDEC JEP106 manufacturer names by bank (1-based) and code, loaded on first use. */
	private static Map<Integer, String> manufacturers;

	static {
		MEMORY_TYPES.put(0x07, "DDR");
		MEMORY_TYPES.put(0x08, "DDR2");
		MEMORY_TYPES.put(DDR3, "DDR3");
		MEMORY_TYPES.put(DDR4, "DDR4");
		MEMORY_TYPES.put(0x0E, "DDR4E");
		MEMORY_TYPES.put(0x0F, "LPDDR3");
		MEMORY_TYPES.put(0x10, "LPDDR4");
		MEMORY_TYPES.put(0x11, "LPDDR4X");
		MEMORY_TYPES.put(DDR5, "DDR5");
		MEMORY_TYPES.put(0x13, "LPDDR5");
		MEMORY_TYPES.put(0x15, "LPDDR5X");
	}

	private final String memoryType;
	private final String moduleType;
	private final int capacityMegabytes;
	private final boolean ecc;
	private final String manufacturer;
	private final String dramManufacturer;
	private final String partNumber;
	private final String serialNumber;
	private final int manufacturingYear;
	private final int manufacturingWeek;

	/**
	 * Decodes the SPD data of a DDR3, DDR4 or DDR5 module.
	 *
	 * @param fruData the SPD bytes, from the start of the FRU data
	 * @param offset the offset of SPD byte 0 in the data
	 * @throws IllegalArgumentException when the data is not SPD data, is of a DRAM type the library does not decode
	 *         (DDR2, LPDDR4...), or ends before the module manufacturer, part number and serial number
	 */
	public SpdInfo(byte[] fruData, int offset) {
		super();
		memoryType = memoryType(fruData, offset);
		if (memoryType == null) {
			throw new IllegalArgumentException("Not SPD data");
		}
		int type = TypeConverter.byteToInt(fruData[offset + 2]);
		int identification; // the offset of the manufacturing information (module manufacturer ID to serial number)
		int partNumberOffset;
		int partNumberLength;
		int revisionLength;
		switch (type) {
		case DDR3:
			// Manufacturer ID (2), location (1), date (2), serial number (4), then the SPD CRC (2) before the part number
			identification = 117;
			partNumberOffset = 128;
			partNumberLength = 18;
			revisionLength = 2;
			break;
		case DDR4:
			identification = 320;
			partNumberOffset = 329;
			partNumberLength = 20;
			revisionLength = 1;
			break;
		case DDR5:
			identification = 512;
			partNumberOffset = 521;
			partNumberLength = 30;
			revisionLength = 1;
			break;
		default:
			throw new IllegalArgumentException("The SPD data of a " + memoryType + " module is not decoded");
		}
		// The DRAM manufacturer ID (2) follows the part number and the revision code; it is at the end and some
		// devices return fewer bytes than that
		int dramManufacturerOffset = partNumberOffset + partNumberLength + revisionLength;
		if (fruData.length < offset + partNumberOffset + partNumberLength) {
			throw new IllegalArgumentException(
					"The " + memoryType + " SPD data is truncated: " + (fruData.length - offset) + " of "
							+ (partNumberOffset + partNumberLength) + " bytes read");
		}
		byte[] spd = new byte[fruData.length - offset];
		System.arraycopy(fruData, offset, spd, 0, spd.length);

		int moduleTypeCode = spd[3] & 0x0f;
		String[] names = type == DDR3 ? DDR3_MODULE_TYPES : type == DDR4 ? DDR4_MODULE_TYPES : DDR5_MODULE_TYPES;
		String name = moduleTypeCode < names.length ? names[moduleTypeCode] : null;
		moduleType = name == null ? String.format("Module type %Xh", moduleTypeCode) : name;
		capacityMegabytes = type == DDR5 ? ddr5Capacity(spd) : ddr3Ddr4Capacity(spd, type);
		// Bus width extension (DDR3 byte 8, DDR4 byte 13, DDR5 byte 235 bits 4:3): the extra bits carry the ECC
		ecc = ((spd[type == DDR3 ? 8 : type == DDR4 ? 13 : 235] >> 3) & 0x03) != 0;
		manufacturer = manufacturer(spd, identification);
		// A date left zero-filled or FFh-filled is not programmed
		int year = bcd(spd[identification + 3]);
		manufacturingYear = year <= 0 ? 0 : 2000 + year;
		manufacturingWeek = Math.max(0, bcd(spd[identification + 4]));
		serialNumber = serialNumber(spd, identification + 5);
		partNumber = ascii(spd, partNumberOffset, partNumberLength);
		dramManufacturer = spd.length >= dramManufacturerOffset + 2 ? manufacturer(spd, dramManufacturerOffset) : "";
	}

	/**
	 * Tells whether FRU data is SPD data rather than an IPMI FRU inventory: no FRU common header (format version 1)
	 * and a JEDEC DRAM device type in byte 2.
	 *
	 * @param fruData the FRU data
	 * @param offset the offset of its first byte
	 * @return true for SPD data, decodable or not
	 */
	public static boolean isSpd(byte[] fruData, int offset) {
		return memoryType(fruData, offset) != null;
	}

	private static String memoryType(byte[] fruData, int offset) {
		if (fruData.length < offset + 3 || fruData[offset] == 0x01) {
			return null;
		}
		return MEMORY_TYPES.get(TypeConverter.byteToInt(fruData[offset + 2]));
	}

	/**
	 * Module capacity of a DDR3 or DDR4 module (JEDEC 21-C annexes K and L): the SDRAM capacity divided by the
	 * device width, times the primary bus width and the number of ranks. A DDR4 rank counts the dies of a 3DS stack,
	 * and the odd ranks of an asymmetrical DDR4 module (byte 12 bit 6) hold the secondary SDRAMs of byte 10, whose
	 * density is the primary one divided by the ratio of its bits 3:2.
	 */
	private static int ddr3Ddr4Capacity(byte[] spd, int type) {
		int organization = spd[type == DDR3 ? 7 : 12];
		int ranks = ((organization >> 3) & 0x07) + 1;
		int busWidth = 8 << (spd[type == DDR3 ? 8 : 13] & 0x07);
		int deviceWidth = 4 << (organization & 0x07);
		int density = densityMegabits(spd[4] & 0x0f);
		if (type == DDR3) {
			return density / 8 * busWidth / deviceWidth * ranks;
		}
		boolean asymmetrical = (organization & 0x40) != 0;
		int evenRanks = asymmetrical ? (ranks + 1) / 2 : ranks;
		int capacity = ddr4RankCapacity(density, spd[6], busWidth, deviceWidth) * evenRanks;
		if (asymmetrical) {
			int secondaryDensity = density >> ((spd[10] >> 2) & 0x03);
			capacity += ddr4RankCapacity(secondaryDensity, spd[10], busWidth, deviceWidth) * (ranks - evenRanks);
		}
		return capacity;
	}

	/** The capacity of one DDR4 package rank, whose SDRAM package type byte gives the dies of a 3DS stack. */
	private static int ddr4RankCapacity(int density, int packageType, int busWidth, int deviceWidth) {
		int dies = (packageType & 0x03) == 0x02 ? ((packageType >> 4) & 0x07) + 1 : 1; // 3DS: a logical rank per die
		return density / 8 * busWidth / deviceWidth * dies;
	}

	/**
	 * Module capacity of a DDR5 module (JESD400-5): per channel, the capacity of a package (density per die times
	 * dies) divided by its I/O width, times the bus width of the channel and the ranks, the odd ranks of an
	 * asymmetrical module being made of the second SDRAM type.
	 */
	private static int ddr5Capacity(byte[] spd) {
		int ranks = ((spd[234] >> 3) & 0x07) + 1;
		int channels = 1 << ((spd[235] >> 5) & 0x03);
		int busWidth = 8 << (spd[235] & 0x07);
		boolean asymmetrical = (spd[234] & 0x40) != 0;
		int evenRanks = asymmetrical ? (ranks + 1) / 2 : ranks;
		int capacity = ddr5RankCapacity(spd, 4, busWidth) * evenRanks;
		if (asymmetrical) {
			capacity += ddr5RankCapacity(spd, 8, busWidth) * (ranks - evenRanks);
		}
		return capacity * channels;
	}

	private static int ddr5RankCapacity(byte[] spd, int densityByte, int busWidth) {
		int[] densities = { 0, 4096, 8192, 12288, 16384, 24576, 32768, 49152, 65536 }; // Mb per die
		int density = spd[densityByte] & 0x1f;
		int dies = 1 << ((spd[densityByte] >> 5) & 0x07);
		int ioWidth = 4 << ((spd[densityByte + 2] >> 5) & 0x07);
		return (density < densities.length ? densities[density] : 0) / 8 * dies * busWidth / ioWidth;
	}

	/** The total SDRAM capacity per die of DDR3 and DDR4 (byte 4 bits 3:0), in megabits. */
	private static int densityMegabits(int code) {
		switch (code) {
		case 8:
			return 12288;
		case 9:
			return 24576;
		default:
			return code <= 7 ? 256 << code : 0;
		}
	}

	/** The name of the JEDEC JEP106 manufacturer whose bank (number of continuation codes + 1) and code are given. */
	private static String manufacturer(byte[] spd, int offset) {
		int bank = (spd[offset] & 0x7f) + 1;
		int code = TypeConverter.byteToInt(spd[offset + 1]);
		if (code == 0x00 || code == 0xff) {
			return ""; // not programmed
		}
		String name = manufacturers().get(bank << 8 | code);
		return name == null ? String.format("JEDEC bank %d code %02Xh", bank, code) : name;
	}

	private static synchronized Map<Integer, String> manufacturers() {
		if (manufacturers == null) {
			Map<Integer, String> map = new HashMap<Integer, String>();
			try (
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(
									SpdInfo.class.getResourceAsStream("jedec-manufacturers.txt"),
									StandardCharsets.UTF_8))) {
				String line = reader.readLine();
				while (line != null) {
					if (!line.startsWith("#")) {
						// bank, code, name (which may hold spaces)
						String[] fields = line.split(" ", 3);
						map.put(Integer.parseInt(fields[0]) << 8 | Integer.parseInt(fields[1], 16), fields[2]);
					}
					line = reader.readLine();
				}
			} catch (IOException e) {
				throw new IllegalStateException("Cannot read the JEDEC manufacturer table", e);
			}
			manufacturers = map;
		}
		return manufacturers;
	}

	/** The serial number as 8 hexadecimal digits, empty when the 4 bytes are not programmed. */
	private static String serialNumber(byte[] spd, int offset) {
		StringBuilder text = new StringBuilder();
		boolean zeros = true;
		boolean ones = true;
		for (int i = offset; i < offset + 4; i++) {
			text.append(String.format("%02X", spd[i] & 0xff));
			zeros &= spd[i] == 0x00;
			ones &= spd[i] == (byte) 0xff;
		}
		return zeros || ones ? "" : text.toString();
	}

	/** The printable ASCII characters of a field, without the trailing spaces and the padding. */
	private static String ascii(byte[] spd, int offset, int length) {
		StringBuilder text = new StringBuilder();
		for (int i = offset; i < offset + length; i++) {
			if (spd[i] >= 0x20 && spd[i] < 0x7f) {
				text.append((char) spd[i]);
			}
		}
		return text.toString().trim();
	}

	/** A two-digit BCD byte as a number, -1 when it is not one (not programmed: 00h or FFh counts as 0 or none). */
	private static int bcd(byte value) {
		int high = (value >> 4) & 0x0f;
		int low = value & 0x0f;
		return high > 9 || low > 9 ? -1 : high * 10 + low;
	}

	/**
	 * Returns the DRAM device type: DDR3, DDR4 or DDR5.
	 *
	 * @return the memory type
	 */
	public String getMemoryType() {
		return memoryType;
	}

	/**
	 * Returns the module type: RDIMM, UDIMM, SO-DIMM, LRDIMM...
	 *
	 * @return the module type, or "Module type Xh" for a code JEDEC does not define
	 */
	public String getModuleType() {
		return moduleType;
	}

	/**
	 * Returns the capacity of the module, as JEDEC computes it from the SDRAM density, the device and bus widths and
	 * the ranks.
	 *
	 * @return the capacity in megabytes (mebibytes), 0 when the SPD data does not give it
	 */
	public int getCapacityMegabytes() {
		return capacityMegabytes;
	}

	/**
	 * Tells whether the module carries ECC bits (its bus width extension).
	 *
	 * @return true for an ECC module
	 */
	public boolean isEcc() {
		return ecc;
	}

	/**
	 * Returns the manufacturer of the module, from its JEDEC JEP106 identification code.
	 *
	 * @return the manufacturer name, "JEDEC bank &lt;n&gt; code &lt;xx&gt;h" for a code the library does not name,
	 *         empty when the code is not programmed
	 */
	public String getManufacturer() {
		return manufacturer;
	}

	/**
	 * Returns the manufacturer of the DRAM devices of the module, from its JEDEC JEP106 identification code.
	 *
	 * @return the manufacturer name, as for {@link #getManufacturer()}, empty when the SPD data ends before it
	 */
	public String getDramManufacturer() {
		return dramManufacturer;
	}

	/**
	 * Returns the part number of the module, as the manufacturer programmed it.
	 *
	 * @return the part number, without the trailing spaces, empty when it is not programmed
	 */
	public String getPartNumber() {
		return partNumber;
	}

	/**
	 * Returns the serial number of the module.
	 *
	 * @return the 4 bytes of the serial number as 8 hexadecimal digits, empty when they are not programmed
	 */
	public String getSerialNumber() {
		return serialNumber;
	}

	/**
	 * Returns the year the module was manufactured.
	 *
	 * @return the year (2019 for 19h), 0 when it is not programmed
	 */
	public int getManufacturingYear() {
		return manufacturingYear;
	}

	/**
	 * Returns the week of the year the module was manufactured.
	 *
	 * @return the week, 1 to 53, 0 when it is not programmed
	 */
	public int getManufacturingWeek() {
		return manufacturingWeek;
	}

	@Override
	public String toString() {
		return memoryType + " " + moduleType + " " + capacityMegabytes + " MB " + manufacturer + " " + partNumber
				+ " S/N " + serialNumber;
	}
}
