package org.metricshub.ipmi.core.coding.commands.fru;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.Fixtures;
import org.metricshub.ipmi.core.coding.commands.fru.record.BaseCompatibilityInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.DcLoadInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.DcOutputInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ExtendedCompatibilityInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.InternalUseInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ManagementAccessInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ManagementAccessRecordType;
import org.metricshub.ipmi.core.coding.commands.fru.record.MultiRecordInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.OemInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.PiromInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.PowerSupplyInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.RawMultiRecordInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.SpdInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;

class ReadFruDataTest {

	/** 1996-01-01 00:00 UTC, the origin of the manufacturing date. */
	private static final long MFG_DATE_EPOCH_MS = 820454400000L;

	/** 2023-12-18 20:28 UTC, as ipmiutil prints the test board, in minutes since the origin. */
	private static final int MFG_DATE_MINUTES = 14707948;

	private static final int POWER_SUPPLY_RECORD = 0x00;

	private static final int EXTENDED_DC_OUTPUT_RECORD = 0x09;

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	private static byte[] concat(byte[]... parts) {
		int length = 0;
		for (byte[] part : parts) {
			length += part.length;
		}
		byte[] result = new byte[length];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, result, offset, part.length);
			offset += part.length;
		}
		return result;
	}

	private static byte checksum(byte[] data, int offset, int length) {
		int sum = 0;
		for (int i = offset; i < offset + length; i++) {
			sum += data[i];
		}
		return (byte) -sum;
	}

	/** A type 3 (8-bit ASCII) field: type/length byte, then the characters. */
	private static byte[] text(String value) {
		byte[] chars = value.getBytes(StandardCharsets.ISO_8859_1);
		return concat(bytes(0xc0 | chars.length), chars);
	}

	/** An info area: format version 1, length in 8-byte units, the body, zero padding, checksum. */
	private static byte[] area(byte[] body) {
		int length = (2 + body.length + 1 + 7) / 8 * 8;
		byte[] area = new byte[length];
		area[0] = 1;
		area[1] = (byte) (length / 8);
		System.arraycopy(body, 0, area, 2, body.length);
		area[length - 1] = checksum(area, 0, length - 1);
		return area;
	}

	/** A multirecord: 5-byte header (type, end-of-list flag and format version 2, length, checksums), then the data. */
	private static byte[] multirecord(int type, boolean last, byte[] data) {
		byte[] header = bytes(type, (last ? 0x80 : 0) | 0x02, data.length, checksum(data, 0, data.length), 0);
		header[4] = checksum(header, 0, 4);
		return concat(header, data);
	}

	private static byte[] boardArea() {
		return area(
				concat(
						bytes(0, MFG_DATE_MINUTES & 0xff, (MFG_DATE_MINUTES >> 8) & 0xff, (MFG_DATE_MINUTES >> 16) & 0xff),
						text("IBM"),
						text("X123"),
						text("S1"),
						text("P1"),
						bytes(0xc0, 0xc1)));
	}

	private static byte[] productArea() {
		return area(
				concat(
						bytes(25),
						text("IBM"),
						text("Model"),
						text("PN"),
						text("V1"),
						text("SN"),
						text("AT"),
						bytes(0xc0, 0xc1)));
	}

	private static byte[] powerSupplyRecord(boolean last) {
		byte[] data = new byte[24];
		data[0] = (byte) 0xee; // 750 W = 0x2EE, least significant byte first
		data[1] = 0x02;
		return multirecord(POWER_SUPPLY_RECORD, last, data);
	}

	/** A FRU image with a board area, a product area and two multirecords, the last one carrying the end flag. */
	private static byte[] image() {
		byte[] board = boardArea();
		byte[] product = productArea();
		byte[] multirecords = concat(
				multirecord(EXTENDED_DC_OUTPUT_RECORD, false, bytes(1, 2, 3)),
				powerSupplyRecord(true));
		byte[] header = bytes(1, 0, 0, 1, 1 + board.length / 8, 1 + (board.length + product.length) / 8, 0, 0);
		header[7] = checksum(header, 0, 7);
		return concat(header, board, product, multirecords);
	}

	private static List<FruRecord> decode(byte[] image) {
		ReadFruDataResponseData chunk = new ReadFruDataResponseData();
		chunk.setFruData(image);
		return ReadFruData.decodeFruData(Collections.singletonList(chunk));
	}

	@Test
	void everyAreaAndTheLastMultirecordAreDecoded() {
		List<FruRecord> records = decode(image());

		assertEquals(4, records.size(), "board, product, the raw Extended DC Output record and the power supply record");
		assertEquals(EXTENDED_DC_OUTPUT_RECORD, assertInstanceOf(RawMultiRecordInfo.class, records.get(2)).getRecordType());

		BoardInfo board = assertInstanceOf(BoardInfo.class, records.get(0));
		assertEquals("IBM", board.getBoardManufacturer());
		assertEquals("X123", board.getBoardProductName());
		assertEquals("S1", board.getBoardSerialNumber());
		assertEquals("P1", board.getBoardPartNumber());
		assertEquals(new Date(MFG_DATE_EPOCH_MS + MFG_DATE_MINUTES * 60000L), board.getMfgDate());

		ProductInfo product = assertInstanceOf(ProductInfo.class, records.get(1));
		assertEquals("IBM", product.getManufacturerName());
		assertEquals("Model", product.getProductName());

		PowerSupplyInfo powerSupply = assertInstanceOf(PowerSupplyInfo.class, records.get(3));
		assertEquals(750, powerSupply.getCapacity());
	}

	@Test
	void theInternalUseAreaIsKeptRaw() {
		byte[] internal = bytes(0x01, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00);
		byte[] board = boardArea();
		// Internal use area at 8 (one 8-byte block), board area right after it
		byte[] header = bytes(1, 1, 0, 2, 0, 0, 0, 0);
		header[7] = checksum(header, 0, 7);

		List<FruRecord> records = decode(concat(header, internal, board));

		assertEquals(2, records.size(), "the internal use area and the board area");
		assertArrayEquals(internal, assertInstanceOf(InternalUseInfo.class, records.get(0)).getData());
		assertInstanceOf(BoardInfo.class, records.get(1));
	}

	@Test
	void anInternalUseAreaBeyondTheDataIsReported() {
		byte[] board = boardArea();
		// Internal use area declared at offset 64, past the data read
		byte[] header = bytes(1, 8, 0, 1, 0, 0, 0, 0);
		header[7] = checksum(header, 0, 7);
		ReadFruDataResponseData chunk = new ReadFruDataResponseData();
		chunk.setFruData(concat(header, board));
		List<String> problems = new java.util.ArrayList<>();

		List<FruRecord> records = ReadFruData.decodeFruData(Collections.singletonList(chunk), problems);

		assertEquals(1, records.size(), "the board area");
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).contains("internal use area"), problems.get(0));
	}

	@Test
	void unspecifiedManufacturingDateIsNull() {
		byte[] board = area(concat(bytes(0, 0, 0, 0), text("IBM"), bytes(0xc1)));
		byte[] header = bytes(1, 0, 0, 1, 0, 0, 0, 0);
		header[7] = checksum(header, 0, 7);

		List<FruRecord> records = decode(concat(header, board));

		assertNull(assertInstanceOf(BoardInfo.class, records.get(0)).getMfgDate());
	}

	@Test
	void truncatedReadKeepsTheCompleteAreas() {
		byte[] image = image();
		byte[] truncated = Arrays.copyOf(image, 8 + boardArea().length + 3);

		List<FruRecord> records = decode(truncated);

		assertEquals(1, records.size(), "only the board area is complete");
		assertInstanceOf(BoardInfo.class, records.get(0));
	}

	@Test
	void multirecordWithAnInvalidRecordChecksumIsStillDecoded() {
		byte[] image = image();
		// the data of the last multirecord (the power supply record) starts 5 bytes after its header, 24 bytes long:
		// a Dell iDRAC 8 writes a record checksum off by one on genuine power supply records
		image[image.length - 1] ^= 0x01;

		List<FruRecord> records = decode(image);

		assertEquals(4, records.size(), "board, product, the raw record and the power supply record, checksum or not");
		assertEquals(750, assertInstanceOf(PowerSupplyInfo.class, records.get(3)).getCapacity());
	}

	@Test
	void aMultirecordAreaWithoutItsEndOfListRecordIsReported() {
		byte[] image = image();
		byte[] truncated = Arrays.copyOf(image, image.length - 24 - 5); // without the last multirecord
		ReadFruDataResponseData chunk = new ReadFruDataResponseData();
		chunk.setFruData(truncated);
		List<String> problems = new java.util.ArrayList<>();

		List<FruRecord> records = ReadFruData.decodeFruData(Collections.singletonList(chunk), problems);

		assertEquals(3, records.size(), "board, product and the raw Extended DC Output record");
		assertEquals(EXTENDED_DC_OUTPUT_RECORD, assertInstanceOf(RawMultiRecordInfo.class, records.get(2)).getRecordType());
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).contains("end-of-list"), problems.get(0));
		assertEquals(3, ReadFruData.decodeFruData(Collections.singletonList(chunk)).size());
	}

	@Test
	void multirecordWithAnInvalidHeaderChecksumEndsTheArea() {
		byte[] image = image();
		// header checksum of the last multirecord: byte 4 of its 5-byte header
		image[image.length - 24 - 1] ^= 0x01;

		List<FruRecord> records = decode(image);

		assertEquals(3, records.size(), "board, product and the raw record; the area is not read past the corrupt header");
	}

	@Test
	void spdDataOfAMemoryModuleGivesItsSpdRecord() {
		List<FruRecord> records = decode(Fixtures.DDR4_SPD);
		assertEquals(1, records.size());
		assertEquals("M393A2K43BB1-CTD", assertInstanceOf(SpdInfo.class, records.get(0)).getPartNumber());

		List<FruRecord> pirom = decode(Fixtures.PIROM);
		assertEquals(1, pirom.size());
		assertEquals("SR3GH", assertInstanceOf(PiromInfo.class, pirom.get(0)).getSSpec());

		byte[] ddr2 = { (byte) 0x80, 0x08, 0x08, 0x0e, 0x0a, 0x01, 0x40, 0x00 };
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> decode(ddr2));
		assertEquals("The SPD data of a DDR2 module is not decoded", refused.getMessage());
	}

	@Test
	void invalidHeaderChecksumIsRejected() {
		byte[] image = image();
		image[7] ^= 0x01;

		assertThrows(IllegalArgumentException.class, () -> decode(image));
	}

	@Test
	void aWordIsTwoBytes() {
		assertEquals(1, BaseUnit.Bytes.getSize());
		assertEquals(2, BaseUnit.Words.getSize());
	}

	@Test
	void wordAddressedDeviceGetsItsOffsetInWordsAndItsCountInBytes() throws Exception {
		// Table 34-3: FRU ID, offset (LS byte first) in the unit of the device, count in bytes
		ReadFruData bytes = new ReadFruData(3, BaseUnit.Bytes, 32, 16);
		assertArrayEquals(bytes(3, 32, 0, 16), bytes.preparePayload(1).getData());

		ReadFruData words = new ReadFruData(3, BaseUnit.Words, 32, 16);
		assertArrayEquals(bytes(3, 16, 0, 16), words.preparePayload(1).getData());
	}

	@Test
	void baseCompatibilityMasksAreReadAtTheRecordOffset() {
		byte[] junk = new byte[10];
		// manufacturer ID, entity ID 7 (system board), compatibility base, code start, 3 mask bytes
		byte[] record = bytes(0x4c, 0x4c, 0x00, 0x07, 0x01, 0x02, 0xaa, 0xbb, 0xcc);

		BaseCompatibilityInfo info = new BaseCompatibilityInfo(concat(junk, record), junk.length, record.length);

		assertEquals(0x4c4c, info.getManufacturerId());
		assertArrayEquals(bytes(0xaa, 0xbb, 0xcc), info.getCodeRangeMasks());
	}

	@Test
	void everyModeledMultirecordTypeIsDecodedAtItsOffset() {
		// Platform Management FRU Information Storage Definition v1.0, sections 18.2 to 18.6 and OEM type C0h
		byte[] dcOutput = multirecord(
				0x01,
				false,
				bytes(
						0x81, // standby output, output number 1
						0xe0,
						0x2e, // nominal 12000 (10 mV units)
						0xa8,
						0xfd, // maximum negative deviation -600
						0x58,
						0x02, // maximum positive deviation +600
						0x64,
						0x00, // ripple and noise, not modeled
						0x00,
						0x00,
						0xe8,
						0x03)); // current draw, not modeled
		byte[] dcLoad = multirecord(
				0x02,
				false,
				bytes(
						0xf2, // output number 2, high nibble reserved
						0x78,
						0xec, // nominal -5000
						0x3f,
						0x0c, // minimum voltage 3135
						0x89,
						0x0d, // maximum voltage 3465
						0x32,
						0x00, // ripple and noise, not modeled
						0x64,
						0x00, // minimum current load 100
						0xc4,
						0x09)); // maximum current load 2500
		byte[] managementAccess = multirecord(
				0x03,
				false,
				concat(bytes(0x02), "node-1".getBytes(StandardCharsets.ISO_8859_1))); // system name, a bare string
		byte[] extendedCompatibility = multirecord(
				0x05,
				false,
				bytes(
						0x57,
						0x01,
						0x00, // manufacturer 343 (Intel)
						0x07, // entity ID 7, system board
						0x01, // compatibility base
						0x82, // code start 2, bit 7 reserved
						0xaa,
						0xbb));
		byte[] oem = multirecord(0xc0, true, bytes(0x57, 0x01, 0x00, 0xde, 0xad));
		byte[] junk = new byte[7];
		byte[] image = concat(junk, dcOutput, dcLoad, managementAccess, extendedCompatibility, oem);

		int offset = junk.length;
		DcOutputInfo output = assertInstanceOf(DcOutputInfo.class, MultiRecordInfo.populateMultiRecord(image, offset));
		assertEquals(1, output.getOutputNumber());
		assertEquals(12000, output.getNominalVoltage());
		assertEquals(-600, output.getMaximumNegativeDeviation());
		assertEquals(600, output.getMaximumPositiveDeviation());

		offset += dcOutput.length;
		DcLoadInfo load = assertInstanceOf(DcLoadInfo.class, MultiRecordInfo.populateMultiRecord(image, offset));
		assertEquals(2, load.getOutputNumber());
		assertEquals(-5000, load.getNominalVoltage());
		assertEquals(3135, load.getMinimumVoltage());
		assertEquals(3465, load.getMaximumVoltage());
		assertEquals(100, load.getMinimumCurrentLoad());
		assertEquals(2500, load.getMaximumCurrentLoad());

		offset += dcLoad.length;
		ManagementAccessInfo access = assertInstanceOf(
				ManagementAccessInfo.class,
				MultiRecordInfo.populateMultiRecord(image, offset));
		assertEquals(ManagementAccessRecordType.SystemName, access.getRecordType());
		assertEquals("node-1", access.getAccessInfo());

		offset += managementAccess.length;
		ExtendedCompatibilityInfo extended = assertInstanceOf(
				ExtendedCompatibilityInfo.class,
				MultiRecordInfo.populateMultiRecord(image, offset));
		assertEquals(343, extended.getManufacturerId());
		assertEquals(EntityId.SystemBoard, extended.getEntityId());
		assertEquals(1, extended.getCompatibilityBase());
		assertEquals(2, extended.getCodeStart());
		assertArrayEquals(bytes(0xaa, 0xbb), extended.getCodeRangeMasks());

		offset += extendedCompatibility.length;
		OemInfo oemInfo = assertInstanceOf(OemInfo.class, MultiRecordInfo.populateMultiRecord(image, offset));
		assertEquals(343, oemInfo.getManufacturerId());
		assertArrayEquals(bytes(0xde, 0xad), oemInfo.getOemData());
	}

	@Test
	void aMultirecordHeaderOfAnotherFormatVersionIsRejected() {
		byte[] header = bytes(0x00, 0x81, 0x00, 0x00, 0x00); // power supply, end of list, format version 1

		assertThrows(IllegalArgumentException.class, () -> MultiRecordInfo.populateMultiRecord(header, 0));
	}
}
