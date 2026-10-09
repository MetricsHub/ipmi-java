package org.metricshub.ipmi.client.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.IpmiResultConverterTest;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;

class GetFrusRunnerTest {

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	/** A chassis info area: version 1, length 2 (16 bytes), rack mount chassis, part number "PN", end, padding. */
	private static final byte[] CHASSIS_AREA = bytes(
			0x01,
			0x02,
			0x17,
			0xc2,
			'P',
			'N',
			0xc1,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00);

	/** A board info area: version 1, length 3 (24 bytes), English, no date, "IBM", product "X123", end, padding. */
	private static final byte[] BOARD_AREA = bytes(
			0x01,
			0x03,
			0x00,
			0x00,
			0x00,
			0x00,
			0xc3,
			'I',
			'B',
			'M',
			0xc4,
			'X',
			'1',
			'2',
			'3',
			0xc1,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00);

	@Test
	void systemBoardIsNamedAfterWhateverAreaDescribesIt() {
		ProductInfo product = new ProductInfo(IpmiResultConverterTest.BASE_BOARD_PRODUCT_INFO, 360);

		assertEquals("System x3650 M2", GetFrusRunner.systemBoardName(Collections.singletonList(product)));
		assertEquals("System Board", GetFrusRunner.systemBoardName(Collections.emptyList()));
	}

	@Test
	void boardAreaNamesTheSystemBoardWhateverTheAreaOrder() {
		ChassisInfo chassis = new ChassisInfo(CHASSIS_AREA, 0);
		BoardInfo board = new BoardInfo(BOARD_AREA, 0);

		// decodeFruData() lists the chassis area first: the board product name must still win
		assertEquals("X123", GetFrusRunner.systemBoardName(Arrays.asList(chassis, board)));
		assertEquals("PN", GetFrusRunner.systemBoardName(Collections.singletonList(chassis)));
	}
}
