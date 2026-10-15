package org.metricshub.ipmi.core.coding.commands.fru.record;

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

import java.util.Locale;

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * The Processor Information ROM (PIROM) of an Intel Xeon processor, which the FRU device of a processor cartridge
 * (device type 10h, modifier 03h) returns instead of an IPMI FRU inventory: the S-spec, the cores, the frequencies,
 * the caches, the part number, the thermal design power and the Protected Processor Inventory Number (PPIN), decoded
 * from the 128-byte layout of data format revision 7 (Intel Xeon Scalable Processors Datasheet, Volume One, chapter
 * 5). The header holds a pointer to each section, read as the datasheet asks; a section that is not present leaves
 * its fields at 0 or empty.
 */
public class PiromInfo extends FruRecord {

	/** The size of the PIROM in bytes, which its header announces at offset 01h-02h. */
	private static final int SIZE = 128;

	/** The data format revision this class decodes. */
	private static final int REVISION = 0x07;

	/** The size of the header: the revision, the size and the section pointers (03h to 0Ch). */
	private static final int HEADER_SIZE = 0x0D;

	private final String sSpec;
	private final int cores;
	private final boolean hyperThreading;
	private final int systemClockMhz;
	private final int cpuid;
	private final int baseFrequencyMhz;
	private final int maxFrequencyMhz;
	private final int l2KilobytesPerCore;
	private final int l3Kilobytes;
	private final int nvmDimmMaxCapacityGigabytes;
	private final String partNumber;
	private final int tcaseMax;
	private final int tdpWatts;
	private final int dtsMax;
	private final String ppin;

	/**
	 * Decodes a PIROM of data format revision 7.
	 *
	 * @param fruData the PIROM bytes, from the start of the FRU data
	 * @param offset the offset of PIROM byte 0 in the data
	 * @throws IllegalArgumentException when the data is not a PIROM, is of another data format revision, or ends
	 *         before its 128 bytes
	 */
	public PiromInfo(byte[] fruData, int offset) {
		super();
		if (!isPirom(fruData, offset)) {
			throw new IllegalArgumentException("Not a processor information ROM");
		}
		int revision = TypeConverter.byteToInt(fruData[offset]);
		if (revision != REVISION) {
			throw new IllegalArgumentException(
					"The processor information ROM of data format revision " + revision + " is not decoded");
		}
		if (fruData.length < offset + SIZE) {
			throw new IllegalArgumentException(
					"The processor information ROM is truncated: " + (fruData.length - offset) + " of " + SIZE
							+ " bytes read");
		}
		byte[] rom = new byte[SIZE];
		System.arraycopy(fruData, offset, rom, 0, SIZE);

		// Header: a byte pointer to each section (03h to 0Ch), 00h when the section is absent
		int processor = section(rom, 0x03, 11);
		int core = section(rom, 0x04, 8);
		int cache = section(rom, 0x06, 6);
		int partNumbers = section(rom, 0x09, 15);
		int thermal = section(rom, 0x0A, 5);
		int ppinData = section(rom, 0x0C, 8);

		// Processor data: S-spec (6 ASCII), sample/production (bits 1:0, left at "sample" on the production Xeon
		// Silver 4110 of a Lenovo XCC, so not decoded), cores (BCD), Hyper-Threading (bit 0), system clock (BCD, MHz)
		sSpec = processor < 0 ? "" : ascii(rom, processor, 6);
		cores = processor < 0 ? 0 : bcd(rom[processor + 7]);
		hyperThreading = processor >= 0 && (rom[processor + 8] & 0x01) != 0;
		systemClockMhz = processor < 0 ? 0 : bcd16(rom, processor + 9);
		// Processor core data: CPUID (EAX[15:0] of function 1), reserved (2), P1 and P0 frequencies (BCD, MHz)
		cpuid = core < 0 ? 0 : binary16(rom, core);
		baseFrequencyMhz = core < 0 ? 0 : bcd16(rom, core + 4);
		maxFrequencyMhz = core < 0 ? 0 : bcd16(rom, core + 6);
		// Cache data: MLC per core (KB), LLC (KB), NVM DIMM maximum capacity (multiples of 64 GB)
		l2KilobytesPerCore = cache < 0 ? 0 : binary16(rom, cache);
		l3Kilobytes = cache < 0 ? 0 : binary16(rom, cache + 2);
		nvmDimmMaxCapacityGigabytes = cache < 0 ? 0 : binary16(rom, cache + 4) * 64;
		// Part numbers: family number (7 ASCII) then SKU number (8 ASCII) make the part number marked on the processor
		partNumber = partNumbers < 0 ? "" : ascii(rom, partNumbers, 15);
		// Thermal reference: Tcase maximum (BCD, degrees Celsius), TDP (BCD, W), DTS maximum (BCD, degrees Celsius)
		tcaseMax = thermal < 0 ? 0 : bcd(rom[thermal]);
		tdpWatts = thermal < 0 ? 0 : bcd16(rom, thermal + 1);
		dtsMax = thermal < 0 ? 0 : bcd16(rom, thermal + 3);
		// PPIN: 64 bits, least significant byte first
		StringBuilder hex = new StringBuilder();
		for (int i = 7; ppinData >= 0 && i >= 0; i--) {
			hex.append(String.format(Locale.ROOT, "%02X", rom[ppinData + i] & 0xff));
		}
		ppin = hex.length() == 0 || "0000000000000000".contentEquals(hex) ? "" : hex.toString();
	}

	/**
	 * Tells whether FRU data is a processor information ROM rather than an IPMI FRU inventory: a data format
	 * revision other than 01h (the FRU format version) in byte 0 and the 128-byte size in bytes 1 and 2.
	 *
	 * @param fruData the FRU data
	 * @param offset the offset of its first byte
	 * @return true for a PIROM, decodable or not
	 */
	public static boolean isPirom(byte[] fruData, int offset) {
		if (fruData.length < offset + 3 || fruData[offset] < 0x02 || fruData[offset] > 0x0F) {
			return false;
		}
		// The size is least significant byte first up to revision 7, most significant byte first from revision 8
		byte first = fruData[offset + 1];
		byte second = fruData[offset + 2];
		return first == (byte) SIZE && second == 0x00 || first == 0x00 && second == (byte) SIZE;
	}

	/**
	 * The offset of a section of the given length from its header pointer, -1 when the section is absent (00h).
	 *
	 * @throws IllegalArgumentException when the pointer leads into the header or beyond the ROM
	 */
	private static int section(byte[] rom, int pointer, int length) {
		int offset = TypeConverter.byteToInt(rom[pointer]);
		if (offset == 0) {
			return -1;
		}
		if (offset < HEADER_SIZE || offset + length > SIZE) {
			throw new IllegalArgumentException(
					String
							.format(
									Locale.ROOT,
									"The processor information ROM section pointer at %02Xh (%02Xh) is outside the ROM",
									pointer,
									offset));
		}
		return offset;
	}

	private static int bcd(byte value) {
		return ((value >> 4) & 0x0f) * 10 + (value & 0x0f);
	}

	/** A 16-bit binary coded decimal, least significant byte first: 00h 21h is 2100. */
	private static int bcd16(byte[] rom, int offset) {
		return bcd(rom[offset + 1]) * 100 + bcd(rom[offset]);
	}

	private static int binary16(byte[] rom, int offset) {
		return TypeConverter.byteToInt(rom[offset]) | TypeConverter.byteToInt(rom[offset + 1]) << 8;
	}

	/**
	 * Returns the S-spec (or QDF) number of the processor, which identifies its exact model and stepping: SR3GH for
	 * an Intel Xeon Silver 4110.
	 *
	 * @return the S-spec, empty when not programmed
	 */
	public String getSSpec() {
		return sSpec;
	}

	/**
	 * Returns the number of cores of the processor.
	 *
	 * @return the cores, 0 when not programmed
	 */
	public int getCores() {
		return cores;
	}

	/**
	 * Tells whether the processor supports Intel Hyper-Threading Technology (two threads per core).
	 *
	 * @return true when it does
	 */
	public boolean isHyperThreading() {
		return hyperThreading;
	}

	/**
	 * Returns the system clock frequency.
	 *
	 * @return the frequency in MHz (100 for a Xeon Scalable), 0 when not programmed
	 */
	public int getSystemClockMhz() {
		return systemClockMhz;
	}

	/**
	 * Returns the low 16 bits of the CPUID signature (EAX of CPUID function 1): the processor type, family, model
	 * and stepping.
	 *
	 * @return the CPUID signature, 0654h for a Skylake-SP of stepping 4; 0 when not programmed
	 */
	public int getCpuid() {
		return cpuid;
	}

	/**
	 * Returns the base frequency of the cores (the maximum P1 core frequency).
	 *
	 * @return the frequency in MHz, 0 when not programmed
	 */
	public int getBaseFrequencyMhz() {
		return baseFrequencyMhz;
	}

	/**
	 * Returns the maximum turbo frequency of the cores (the maximum P0 core frequency).
	 *
	 * @return the frequency in MHz, 0 when not programmed
	 */
	public int getMaxFrequencyMhz() {
		return maxFrequencyMhz;
	}

	/**
	 * Returns the size of the mid-level cache (L2) of each core.
	 *
	 * @return the size in kilobytes, 0 when not programmed
	 */
	public int getL2KilobytesPerCore() {
		return l2KilobytesPerCore;
	}

	/**
	 * Returns the size of the last-level cache (L3) of the processor.
	 *
	 * @return the size in kilobytes, 0 when not programmed
	 */
	public int getL3Kilobytes() {
		return l3Kilobytes;
	}

	/**
	 * Returns the maximum capacity of non-volatile memory DIMMs (persistent memory) the processor supports, as the
	 * datasheet names the field; on the processors seen it equals the maximum memory of the model.
	 *
	 * @return the capacity in gigabytes, a multiple of 64, 0 when not programmed
	 */
	public int getNvmDimmMaxCapacityGigabytes() {
		return nvmDimmMaxCapacityGigabytes;
	}

	/**
	 * Returns the part number marked on the processor: the Intel family number followed by the SKU number.
	 *
	 * @return the part number, up to 15 characters, empty when not programmed
	 */
	public String getPartNumber() {
		return partNumber;
	}

	/**
	 * Returns the maximum case temperature of the processor.
	 *
	 * @return the temperature in degrees Celsius, 0 when not programmed
	 */
	public int getTcaseMax() {
		return tcaseMax;
	}

	/**
	 * Returns the thermal design power of the processor.
	 *
	 * @return the power in watts, 0 when not programmed
	 */
	public int getTdpWatts() {
		return tdpWatts;
	}

	/**
	 * Returns the maximum temperature of the digital thermal sensor of the processor.
	 *
	 * @return the temperature in degrees Celsius, 0 when not programmed
	 */
	public int getDtsMax() {
		return dtsMax;
	}

	/**
	 * Returns the Protected Processor Inventory Number, the unique identification number of the processor (the one
	 * the PPIN MSR gives).
	 *
	 * @return the 64-bit PPIN as 16 hexadecimal digits, most significant first; empty when not programmed
	 */
	public String getPpin() {
		return ppin;
	}

	@Override
	public String toString() {
		return "Intel " + sSpec + " " + cores + " cores " + baseFrequencyMhz + " MHz " + partNumber + " PPIN " + ppin;
	}
}
