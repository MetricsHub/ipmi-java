package org.metricshub.ipmi.core.coding.commands.fru.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.metricshub.ipmi.client.Fixtures.identify;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.Fixtures;

class SpdInfoTest {

	/** A Micron 8 GB 2Rx4 PC3-12800R RDIMM: 2 Gb SDRAMs, 4 bits wide, on a 64-bit bus with ECC, 2 ranks. */
	private static byte[] ddr3() {
		byte[] spd = new byte[256];
		spd[0] = (byte) 0x92;
		spd[1] = 0x11;
		spd[2] = 0x0B;
		spd[3] = 0x01; // RDIMM
		spd[4] = 0x03; // 2 Gb, 8 banks
		spd[7] = 0x08; // 2 ranks, x4
		spd[8] = 0x0B; // 64 bits + 8 bits of ECC
		identify(spd, 117, 128, 1, 0x2C, 14, 8, "0E1F2A3B", "36JSF1G72PZ-1G6M1", 18, 2);
		spd[126] = 0x41; // the SPD CRC, between the serial number and the part number: printable here on purpose
		spd[127] = 0x42;
		return spd;
	}

	/** An SK Hynix 32 GB 2Rx8 DDR5-4800 RDIMM: 16 Gb dies, 8 bits wide, two 32-bit channels with ECC, 2 ranks. */
	private static byte[] ddr5() {
		byte[] spd = new byte[1024];
		spd[0] = 0x30;
		spd[1] = 0x10;
		spd[2] = 0x12;
		spd[3] = 0x01; // RDIMM
		spd[4] = 0x04; // 16 Gb per die, 1 die
		spd[6] = 0x20; // x8
		spd[234] = 0x08; // 2 ranks, symmetrical
		spd[235] = 0x2A; // 2 channels, 32 bits + 8 bits of ECC each
		identify(spd, 512, 521, 1, 0xAD, 23, 45, "72A0C813", "HMCG88AEBRA107N", 30, 1);
		return spd;
	}

	@Test
	void ddr4Rdimm() {
		SpdInfo spd = new SpdInfo(Fixtures.DDR4_SPD, 0);
		assertEquals("DDR4", spd.getMemoryType());
		assertEquals("RDIMM", spd.getModuleType());
		assertEquals(16384, spd.getCapacityMegabytes());
		assertTrue(spd.isEcc());
		assertEquals("Samsung", spd.getManufacturer());
		assertEquals("Samsung", spd.getDramManufacturer());
		assertEquals("M393A2K43BB1-CTD", spd.getPartNumber());
		assertEquals("409F3CA2", spd.getSerialNumber());
		assertEquals(2019, spd.getManufacturingYear());
		assertEquals(23, spd.getManufacturingWeek());
		assertEquals("DDR4 RDIMM 16384 MB Samsung M393A2K43BB1-CTD S/N 409F3CA2", spd.toString());
	}

	@Test
	void ddr4ThreeDimensionalStackCountsItsDies() {
		byte[] image = Fixtures.DDR4_SPD.clone();
		image[6] = 0x32; // 3DS package, 4 dies
		assertEquals(4 * 16384, new SpdInfo(image, 0).getCapacityMegabytes());
	}

	@Test
	void ddr4AsymmetricalModuleHalvesItsOddRanks() {
		byte[] image = Fixtures.DDR4_SPD.clone();
		image[12] = 0x49; // 2 ranks, x8, asymmetrical
		image[10] = 0x04; // secondary SDRAMs of half the density
		// 8192 / 8 x 64 / 8 + 4096 / 8 x 64 / 8
		assertEquals(8192 + 4096, new SpdInfo(image, 0).getCapacityMegabytes());
		// The density steps are the standard densities: one below 16 Gb is 12 Gb, two below is 8 Gb
		image[4] = (byte) 0x86;
		assertEquals(16384 + 12288, new SpdInfo(image, 0).getCapacityMegabytes());
		image[10] = 0x08;
		assertEquals(16384 + 8192, new SpdInfo(image, 0).getCapacityMegabytes());
	}

	@Test
	void ddr5DieCountIsALookup() {
		byte[] image = ddr5();
		image[4] = 0x44; // 16 Gb per die, code 010: 2 dies (2H 3DS)
		assertEquals(2 * 32768, new SpdInfo(image, 0).getCapacityMegabytes());
		image[4] = (byte) 0xA4; // code 101: 16 dies
		assertEquals(16 * 32768, new SpdInfo(image, 0).getCapacityMegabytes());
	}

	@Test
	void aZeroFilledDateIsNotProgrammed() {
		byte[] image = Fixtures.DDR4_SPD.clone();
		image[323] = 0;
		image[324] = 0;
		SpdInfo spd = new SpdInfo(image, 0);
		assertEquals(0, spd.getManufacturingYear());
		assertEquals(0, spd.getManufacturingWeek());
	}

	@Test
	void ddr5ChannelCountIsAPowerOfTwo() {
		byte[] image = ddr5();
		image[235] = 0x4A; // 4 channels of 32 bits + 4 bits of ECC
		assertEquals(2 * 32768, new SpdInfo(image, 0).getCapacityMegabytes());
	}

	@Test
	void ddr3Rdimm() {
		SpdInfo spd = new SpdInfo(ddr3(), 0);
		assertEquals("DDR3", spd.getMemoryType());
		assertEquals("RDIMM", spd.getModuleType());
		assertEquals(8192, spd.getCapacityMegabytes());
		assertTrue(spd.isEcc());
		assertEquals("Micron Technology", spd.getManufacturer());
		assertEquals("Micron Technology", spd.getDramManufacturer());
		assertEquals("36JSF1G72PZ-1G6M1", spd.getPartNumber());
		assertEquals("0E1F2A3B", spd.getSerialNumber());
		assertEquals(2014, spd.getManufacturingYear());
		assertEquals(8, spd.getManufacturingWeek());
	}

	@Test
	void ddr5Rdimm() {
		SpdInfo spd = new SpdInfo(ddr5(), 0);
		assertEquals("DDR5", spd.getMemoryType());
		assertEquals("RDIMM", spd.getModuleType());
		assertEquals(32768, spd.getCapacityMegabytes());
		assertTrue(spd.isEcc());
		assertEquals("SK Hynix", spd.getManufacturer());
		assertEquals("HMCG88AEBRA107N", spd.getPartNumber());
		assertEquals("72A0C813", spd.getSerialNumber());
		assertEquals(2023, spd.getManufacturingYear());
		assertEquals(45, spd.getManufacturingWeek());
	}

	@Test
	void ddr5AsymmetricalModuleMixesItsTwoSdramTypes() {
		byte[] image = ddr5();
		image[4] = 0x05; // 24 Gb on the even ranks
		image[8] = 0x04; // 16 Gb on the odd ranks
		image[10] = 0x20; // x8
		image[234] = 0x48; // 2 ranks, asymmetrical
		// 2 channels x (24576 / 8 x 32 / 8 + 16384 / 8 x 32 / 8)
		assertEquals(2 * (12288 + 8192), new SpdInfo(image, 0).getCapacityMegabytes());
	}

	@Test
	void unknownCodesAndUnprogrammedFieldsAreReportedAsSuch() {
		byte[] image = ddr5();
		image[3] = 0x0F;
		image[235] = 0x22; // no bus width extension
		image[512] = 0x0B; // bank 12
		image[513] = 0x55;
		image[515] = (byte) 0xFF;
		image[516] = (byte) 0xFF;
		for (int i = 517; i <= 520; i++) {
			image[i] = (byte) 0xFF;
		}
		image[553] = 0x00;
		SpdInfo spd = new SpdInfo(image, 0);
		assertEquals("Module type Fh", spd.getModuleType());
		assertFalse(spd.isEcc());
		assertEquals("JEDEC bank 12 code 55h", spd.getManufacturer());
		assertEquals("", spd.getDramManufacturer());
		assertEquals("", spd.getSerialNumber());
		assertEquals(0, spd.getManufacturingYear());
		assertEquals(0, spd.getManufacturingWeek());
	}

	@Test
	void spdDataIsToldFromFruInventories() {
		assertTrue(SpdInfo.isSpd(Fixtures.DDR4_SPD, 0));
		assertTrue(SpdInfo.isSpd(new byte[] { (byte) 0x80, 0x08, 0x08 }, 0), "DDR2, not decoded but recognized");
		assertTrue(SpdInfo.isSpd(new byte[] { (byte) 0x80, 0x08, 0x09 }, 0), "DDR2 FB-DIMM");
		assertTrue(SpdInfo.isSpd(new byte[] { 0x30, 0x10, 0x14 }, 0), "DDR5 NVDIMM-P");
		assertFalse(SpdInfo.isSpd(Fixtures.BASE_BOARD_PRODUCT_INFO, 0), "a FRU common header");
		assertFalse(SpdInfo.isSpd(new byte[] { 0x55, 0x00, 0x55 }, 0), "not a DRAM type");
		assertFalse(SpdInfo.isSpd(new byte[] { 0x23, 0x11 }, 0), "too short");
	}

	@Test
	void unsupportedAndTruncatedSpdDataAreRefused() {
		IllegalArgumentException ddr2 = assertThrows(
				IllegalArgumentException.class,
				() -> new SpdInfo(new byte[]
				{ (byte) 0x80, 0x08, 0x08, 0x00 }, 0));
		assertEquals("The SPD data of a DDR2 module is not decoded", ddr2.getMessage());

		byte[] truncated = new byte[300];
		System.arraycopy(Fixtures.DDR4_SPD, 0, truncated, 0, truncated.length);
		IllegalArgumentException short4 = assertThrows(IllegalArgumentException.class, () -> new SpdInfo(truncated, 0));
		assertEquals("The DDR4 SPD data is truncated: 300 of 349 bytes read", short4.getMessage());
	}
}
