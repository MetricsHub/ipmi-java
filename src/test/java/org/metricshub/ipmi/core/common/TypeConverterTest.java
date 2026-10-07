package org.metricshub.ipmi.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TypeConverterTest {

	@Test
	void decode1sComplement() {
		// 4-bit 1's complement: 0111 = 7, 1000 = -7, 1110 = -1, 1111 = -0
		assertEquals(7, TypeConverter.decode1sComplement(0x7, 3));
		assertEquals(-7, TypeConverter.decode1sComplement(0x8, 3));
		assertEquals(-1, TypeConverter.decode1sComplement(0xe, 3));
		assertEquals(0, TypeConverter.decode1sComplement(0xf, 3));
		assertEquals(0, TypeConverter.decode1sComplement(0x0, 3));
	}

	@Test
	void decode2sComplement() {
		assertEquals(7, TypeConverter.decode2sComplement(0x7, 3));
		assertEquals(-8, TypeConverter.decode2sComplement(0x8, 3));
		assertEquals(-1, TypeConverter.decode2sComplement(0xf, 3));
	}
}
