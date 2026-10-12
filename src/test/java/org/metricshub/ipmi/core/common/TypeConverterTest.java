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

	@Test
	void decodeDateIsUnsigned() {
		// IPMI 2.0 section 37.1: an unsigned count of seconds, so neither 2038 nor the FFFFFFFFh marker wraps
		assertEquals(0L, TypeConverter.decodeDate(0).getTime());
		assertEquals(0x7fffffffL * 1000, TypeConverter.decodeDate(0x7fffffff).getTime());
		assertEquals(0x80000000L * 1000, TypeConverter.decodeDate(0x80000000).getTime());
		assertEquals(0xffffffffL * 1000, TypeConverter.decodeDate(0xffffffff).getTime());
	}
}
