package org.metricshub.ipmi.client;

import static org.metricshub.ipmi.core.transport.FakeSdrRepository.compactSensorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.fruDeviceLocatorRecord;
import static org.metricshub.ipmi.core.transport.FakeSdrRepository.record;
import static org.metricshub.ipmi.core.transport.FakeSel.oemTimestamped;
import static org.metricshub.ipmi.core.transport.FakeSel.selRecord;
import static org.metricshub.ipmi.core.transport.FakeSel.systemEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;
import org.metricshub.ipmi.core.transport.FakeSdrRepository;
import org.metricshub.ipmi.core.transport.FakeSel;

/**
 * Real SDR and FRU bytes: the Full Sensor record of the "Ambient Temp" sensor of a front panel board (record ID 1,
 * sensor number 50) and the FRU inventory of an IBM System x3650 M2 system board (chassis, board and product areas);
 * and {@link #bmc()}, a fake BMC built from them for the client and CLI tests.
 */
public final class Fixtures {

	/** Cipher suites 1 to 19 as a Lenovo XCC lists them: 17 is preferred, then 3. */
	public static final byte[] SUITES = bytes(
			"c0 01 01 40 80 c0 02 01 41 80 c0 03 01 41 81 c0 04 01 41 82 c0 05 01 41 83 c0 06 02 40 80 c0 07 02 42 80"
					+ " c0 08 02 42 81 c0 09 02 42 82 c0 0a 02 42 83 c0 0b 02 43 80 c0 0c 02 43 81 c0 0d 02 43 82 c0 0e 02 43 83"
					+ " c0 0f 03 40 80 c0 10 03 44 80 c0 11 03 44 81 c0 12 03 44 82 c0 13 03 44 83");
	public static final String USER = "admin";
	public static final String PASSWORD = "secret";
	public static final int TEMPERATURE_SENSOR = 50;
	public static final int FAN_SENSOR = 0x10;
	public static final int BOARD_SENSOR = 0x11;
	public static final int PSU_FRU = 1;
	/** 2024-05-15T09:15:25Z: when the SEL of {@link #bmc()} was reset. */
	public static final long SEL_RESET_TIME = 1715764525L;
	/** The IANA enterprise number of Lenovo. */
	public static final int LENOVO = 19046;

	private Fixtures() {}

	/**
	 * A BMC with a repository of 6 records: a temperature (Full), a fan presence and a system board presence
	 * (Compact), the locator of FRU 1 (a power supply), an OEM record and a record of a reserved type.
	 *
	 * @return the fake BMC, to close
	 * @throws IOException when no UDP port can be bound
	 */
	public static FakeRmcpPlusBmc bmc() throws IOException {
		FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null);
		new FakeSdrRepository()
				.add(FRONT_PANEL_FULL_RECORD)
				.add(compactSensorRecord(2, FAN_SENSOR, EntityId.Fan.getCode(), 1, SensorType.Fan.getCode(), 0x08, "Fan 1"))
				.add(
						compactSensorRecord(
								3,
								BOARD_SENSOR,
								EntityId.SystemBoard.getCode(),
								1,
								SensorType.EntityPresence.getCode(),
								0x08,
								"Board"))
				.add(fruDeviceLocatorRecord(4, PSU_FRU, EntityId.PowerSupply.getCode(), 1, "PSU 1"))
				.add(record(5, 0xc0, new byte[]
				{ 1, 2, 3 }))
				.add(record(6, 0x0b, new byte[]
				{ 1, 2 }))
				.reading(TEMPERATURE_SENSOR, 0x9a, 0x40, 0x00)
				.reading(FAN_SENSOR, 0x00, 0x40, 0x02)
				.reading(BOARD_SENSOR, 0x00, 0x40, 0x02)
				.fru(0, BASE_BOARD_PRODUCT_INFO)
				.fru(PSU_FRU, BASE_BOARD_PRODUCT_INFO)
				.install(bmc);
		sel().install(bmc);
		return bmc;
	}

	/**
	 * The System Event Log of {@link #bmc()}: as a Lenovo IMM logs it, a log reset, an OEM record, a temperature
	 * threshold crossed, a power-off deassertion, a non-timestamped OEM record, and an event logged before the BMC
	 * had the time.
	 *
	 * @return the log
	 */
	public static FakeSel sel() {
		return new FakeSel()
				.add(
						systemEvent(
								1,
								SEL_RESET_TIME,
								SensorType.EventLoggingDisabled.getCode(),
								0x72,
								0x6f,
								true,
								0x02,
								0xff,
								0xff))
				.add(oemTimestamped(2, 0xc0, SEL_RESET_TIME + 5, LENOVO, 0x02, 0x01, 0x05, 0x00, 0x00, 0x00))
				.add(
						systemEvent(
								3,
								SEL_RESET_TIME + 482,
								SensorType.Temperature.getCode(),
								TEMPERATURE_SENSOR,
								0x01,
								true,
								0x57,
								0x9a,
								0x98))
				.add(
						systemEvent(
								4,
								SEL_RESET_TIME + 489,
								SensorType.PowerUnit.getCode(),
								1,
								0x6f,
								false,
								0x00,
								0xff,
								0xff))
				.add(selRecord(5, 0xe0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13))
				.add(systemEvent(6, 5, SensorType.Processor.getCode(), 2, 0x6f, true, 0x00, 0xff, 0xff));
	}

	/**
	 * @param text a string
	 * @return its UTF-8 bytes
	 */
	public static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * @param hex bytes in hexadecimal, separated by spaces
	 * @return the bytes
	 */
	public static byte[] bytes(String hex) {
		String[] parts = hex.trim().split("\\s+");
		byte[] result = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			result[i] = (byte) Integer.parseInt(parts[i], 16);
		}
		return result;
	}

	/** The FRU device ID of the memory module whose SPD data is {@link #DDR4_SPD}. */
	public static final int DIMM_FRU = 2;

	/**
	 * The SPD data of a Samsung 16 GB 2Rx8 DDR4-2666 ECC RDIMM (M393A2K43BB1-CTD): 8 Gb SDRAMs, 8 bits wide, on a
	 * 64-bit bus with 8 bits of ECC, 2 ranks, made in week 23 of 2019.
	 */
	public static final byte[] DDR4_SPD = ddr4Spd();

	private static byte[] ddr4Spd() {
		byte[] spd = new byte[512];
		spd[0] = 0x23; // 384 bytes used of 512
		spd[1] = 0x11; // SPD revision 1.1
		spd[2] = 0x0C; // DDR4
		spd[3] = 0x01; // RDIMM
		spd[4] = (byte) 0x85; // 8 Gb, 4 banks, 2 bank groups
		spd[12] = 0x09; // 2 ranks, x8
		spd[13] = 0x0B; // 64 bits + 8 bits of ECC
		identify(spd, 320, 329, 1, 0xCE, 19, 23, "409F3CA2", "M393A2K43BB1-CTD", 20, 1);
		return spd;
	}

	/**
	 * Writes the manufacturing information of a memory module into its SPD data: the JEDEC ID of the module
	 * manufacturer, the manufacturing date, the serial number, the part number and, after the revision code, the JEDEC
	 * ID of the DRAM manufacturer, taken to be the same.
	 *
	 * @param spd the SPD data
	 * @param offset the offset of the module manufacturer ID (117 for DDR3, 320 for DDR4, 512 for DDR5)
	 * @param partNumberOffset the offset of the part number (128 for DDR3, after the CRC; 329 for DDR4; 521 for DDR5)
	 * @param bank the JEDEC JEP106 bank of the manufacturer, 1 for the first one
	 * @param code the JEDEC JEP106 code of the manufacturer, with its parity bit
	 * @param year the year of manufacture, 2000-based
	 * @param week the week of manufacture
	 * @param serial the serial number, 8 hexadecimal digits
	 * @param partNumber the part number
	 * @param partNumberLength the length of the part number field (18 for DDR3, 20 for DDR4, 30 for DDR5)
	 * @param revisionLength the length of the revision code field (2 for DDR3, 1 for DDR4 and DDR5)
	 */
	public static void identify(
			byte[] spd,
			int offset,
			int partNumberOffset,
			int bank,
			int code,
			int year,
			int week,
			String serial,
			String partNumber,
			int partNumberLength,
			int revisionLength) {
		spd[offset] = (byte) (0x80 | (bank - 1)); // the parity bit, set for bank 1 (no continuation code)
		spd[offset + 1] = (byte) code;
		spd[offset + 2] = 0x01; // location
		spd[offset + 3] = (byte) Integer.parseInt(String.valueOf(year), 16); // BCD
		spd[offset + 4] = (byte) Integer.parseInt(String.valueOf(week), 16);
		for (int i = 0; i < 4; i++) {
			spd[offset + 5 + i] = (byte) Integer.parseInt(serial.substring(2 * i, 2 * i + 2), 16);
		}
		byte[] part = String.format("%-" + partNumberLength + "s", partNumber).getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(part, 0, spd, partNumberOffset, partNumberLength);
		spd[partNumberOffset + partNumberLength + revisionLength] = (byte) (0x80 | (bank - 1));
		spd[partNumberOffset + partNumberLength + revisionLength + 1] = (byte) code;
	}

	public static final byte[] BASE_BOARD_PRODUCT_INFO = {
			1,
			0,
			1,
			8,
			45,
			0,
			0,
			-55,
			1,
			7,
			1,
			0,
			-34,
			75,
			68,
			57,
			48,
			57,
			56,
			67,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			16,
			102,
			82,
			-95,
			-100,
			-109,
			-120,
			57,
			41,
			-72,
			-89,
			-42,
			-49,
			55,
			-119,
			-77,
			-61,
			-63,
			0,
			0,
			-16,
			1,
			37,
			0,
			32,
			-66,
			108,
			-34,
			32,
			73,
			66,
			77,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-32,
			83,
			121,
			115,
			116,
			101,
			109,
			32,
			66,
			111,
			97,
			114,
			100,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-34,
			89,
			75,
			49,
			53,
			57,
			48,
			57,
			55,
			90,
			48,
			57,
			89,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-34,
			52,
			57,
			89,
			54,
			52,
			57,
			56,
			32,
			32,
			32,
			32,
			32,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-51,
			52,
			51,
			86,
			55,
			48,
			55,
			50,
			32,
			32,
			32,
			32,
			32,
			0,
			2,
			0,
			2,
			2,
			0,
			0,
			2,
			0,
			80,
			-34,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			5,
			1,
			6,
			60,
			1,
			0,
			48,
			0,
			33,
			94,
			-37,
			23,
			-112,
			0,
			33,
			94,
			-37,
			23,
			-110,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			16,
			102,
			82,
			-95,
			-100,
			-109,
			-120,
			57,
			41,
			-72,
			-89,
			-42,
			-49,
			55,
			-119,
			-77,
			-61,
			1,
			2,
			16,
			17,
			17,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			8,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			2,
			1,
			44,
			2,
			0,
			0,
			-63,
			0,
			-71,
			1,
			21,
			0,
			-34,
			32,
			73,
			66,
			77,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-32,
			83,
			121,
			115,
			116,
			101,
			109,
			32,
			120,
			51,
			54,
			53,
			48,
			32,
			77,
			50,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-34,
			55,
			57,
			52,
			55,
			50,
			50,
			71,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-34,
			75,
			68,
			57,
			48,
			57,
			56,
			67,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			-31,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			32,
			0,
			0,
			-63,
			0,
			26 };

	public static final byte[] FRONT_PANEL_FULL_RECORD = {
			1,
			0,
			81,
			1,
			55,
			32,
			0,
			50,
			12,
			1,
			127,
			104,
			1,
			1,
			-128,
			10,
			-128,
			122,
			56,
			0,
			0,
			1,
			0,
			0,
			1,
			0,
			124,
			-64,
			0,
			0,
			1,
			-103,
			-78,
			-120,
			-1,
			0,
			-79,
			-83,
			-86,
			0,
			0,
			0,
			4,
			0,
			0,
			0,
			0,
			-52,
			65,
			109,
			98,
			105,
			101,
			110,
			116,
			32,
			84,
			101,
			109,
			112 };
}
