package org.metricshub.ipmi.core.coding.commands.fru;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.fru.record.BaseCompatibilityInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.PowerSupplyInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;

class ReadFruDataTest {

	/** 1996-01-01 00:00 UTC, the origin of the manufacturing date. */
	private static final long MFG_DATE_EPOCH_MS = 820454400000L;

	/** 2023-12-18 20:28 UTC, as ipmiutil prints the test board, in minutes since the origin. */
	private static final int MFG_DATE_MINUTES = 14707948;

	private static final int POWER_SUPPLY_RECORD = 0x00;

	private static final int EXTENDED_DC_OUTPUT_RECORD = 0x06;

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

		assertEquals(3, records.size(), "board, product and the power supply record; the unknown record is skipped");

		BoardInfo board = assertInstanceOf(BoardInfo.class, records.get(0));
		assertEquals("IBM", board.getBoardManufacturer());
		assertEquals("X123", board.getBoardProductName());
		assertEquals("S1", board.getBoardSerialNumber());
		assertEquals("P1", board.getBoardPartNumber());
		assertEquals(new Date(MFG_DATE_EPOCH_MS + MFG_DATE_MINUTES * 60000L), board.getMfgDate());

		ProductInfo product = assertInstanceOf(ProductInfo.class, records.get(1));
		assertEquals("IBM", product.getManufacturerName());
		assertEquals("Model", product.getProductName());

		PowerSupplyInfo powerSupply = assertInstanceOf(PowerSupplyInfo.class, records.get(2));
		assertEquals(750, powerSupply.getCapacity());
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
	void multirecordWithAnInvalidRecordChecksumIsSkipped() {
		byte[] image = image();
		// the data of the last multirecord (the power supply record) starts 5 bytes after its header, 24 bytes long
		image[image.length - 24] ^= 0x01;

		List<FruRecord> records = decode(image);

		assertEquals(2, records.size(), "board and product; the corrupt power supply record is skipped");
	}

	@Test
	void multirecordWithAnInvalidHeaderChecksumEndsTheArea() {
		byte[] image = image();
		// header checksum of the last multirecord: byte 4 of its 5-byte header
		image[image.length - 24 - 1] ^= 0x01;

		List<FruRecord> records = decode(image);

		assertEquals(2, records.size(), "board and product; the area is not read past the corrupt header");
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
}
