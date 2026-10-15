package org.metricshub.ipmi.core.coding.commands.fru.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.Fixtures;

class PiromInfoTest {

	@Test
	void xeonSilver4110() {
		PiromInfo cpu = new PiromInfo(Fixtures.PIROM, 0);
		assertEquals("SR3GH", cpu.getSSpec());
		assertEquals(8, cpu.getCores());
		assertTrue(cpu.isHyperThreading());
		assertEquals(100, cpu.getSystemClockMhz());
		assertEquals(0x0654, cpu.getCpuid());
		assertEquals(2100, cpu.getBaseFrequencyMhz());
		assertEquals(3000, cpu.getMaxFrequencyMhz());
		assertEquals(1024, cpu.getL2KilobytesPerCore());
		assertEquals(11264, cpu.getL3Kilobytes());
		assertEquals(768, cpu.getNvmDimmMaxCapacityGigabytes());
		assertEquals("37608DC00416530", cpu.getPartNumber());
		assertEquals(77, cpu.getTcaseMax());
		assertEquals(85, cpu.getTdpWatts());
		assertEquals(90, cpu.getDtsMax());
		assertEquals("634AFBC3354D3C6D", cpu.getPpin());
		assertEquals("Intel SR3GH 8 cores 2100 MHz 37608DC00416530 PPIN 634AFBC3354D3C6D", cpu.toString());
	}

	@Test
	void anAbsentSectionLeavesItsFieldsEmpty() {
		byte[] rom = Fixtures.PIROM.clone();
		rom[0x0A] = 0; // no thermal reference data
		rom[0x0C] = 0; // no PPIN
		PiromInfo cpu = new PiromInfo(rom, 0);
		assertEquals(0, cpu.getTdpWatts());
		assertEquals(0, cpu.getTcaseMax());
		assertEquals("", cpu.getPpin());
		assertEquals("SR3GH", cpu.getSSpec());

		Arrays.fill(rom, 0x77, 0x7F, (byte) 0);
		rom[0x0C] = 0x77;
		assertEquals("", new PiromInfo(rom, 0).getPpin(), "a zero PPIN is not programmed");
	}

	@Test
	void aCorruptSectionPointerIsRefused() {
		byte[] rom = Fixtures.PIROM.clone();
		rom[0x09] = 0x78; // part numbers beyond the ROM
		IllegalArgumentException beyond = assertThrows(IllegalArgumentException.class, () -> new PiromInfo(rom, 0));
		assertEquals("The processor information ROM section pointer at 09h (78h) is outside the ROM", beyond.getMessage());
		rom[0x09] = 0x05; // into the header
		assertThrows(IllegalArgumentException.class, () -> new PiromInfo(rom, 0));
	}

	@Test
	void piromDataIsToldFromFruInventories() {
		assertTrue(PiromInfo.isPirom(Fixtures.PIROM, 0));
		assertTrue(PiromInfo.isPirom(new byte[] { 0x08, 0x00, (byte) 0x80 }, 0), "revision 8, size MSB first");
		assertFalse(PiromInfo.isPirom(Fixtures.BASE_BOARD_PRODUCT_INFO, 0), "a FRU common header");
		assertFalse(PiromInfo.isPirom(Fixtures.DDR4_SPD, 0), "SPD data");
		assertFalse(PiromInfo.isPirom(new byte[] { 0x07, (byte) 0x80 }, 0), "too short");
		assertFalse(PiromInfo.isPirom(new byte[] { 0x07, 0x00, 0x01 }, 0), "not 128 bytes");
	}

	@Test
	void anotherRevisionAndTruncatedDataAreRefused() {
		byte[] rom = Fixtures.PIROM.clone();
		rom[0] = 0x03;
		IllegalArgumentException revision = assertThrows(IllegalArgumentException.class, () -> new PiromInfo(rom, 0));
		assertEquals("The processor information ROM of data format revision 3 is not decoded", revision.getMessage());

		byte[] truncated = Arrays.copyOf(Fixtures.PIROM, 96);
		IllegalArgumentException cut = assertThrows(IllegalArgumentException.class, () -> new PiromInfo(truncated, 0));
		assertEquals("The processor information ROM is truncated: 96 of 128 bytes read", cut.getMessage());
	}
}
