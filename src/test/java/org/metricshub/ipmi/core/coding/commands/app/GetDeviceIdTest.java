package org.metricshub.ipmi.core.coding.commands.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.core.coding.commands.IpmiResponses.response;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;

class GetDeviceIdTest {

	@Test
	void everyFieldIsDecodedAtItsOffset() throws Exception {
		// Device 20h revision 1 with Device SDRs, firmware update in progress, firmware 1.05, IPMI 1.5, bridge only,
		// manufacturer 0F2A7Ch, product 1234h, no auxiliary revision (table 20-2)
		GetDeviceId command = new GetDeviceId();
		assertEquals(NetworkFunction.ApplicationRequest, command.getNetworkFunction());
		assertEquals(CommandCodes.GET_DEVICE_ID, command.getCommandCode());
		GetDeviceIdResponseData data = command
				.getResponseData(
						response(
								CommandCodes.GET_DEVICE_ID,
								0x00,
								0x20,
								0x81,
								0x81,
								0x05,
								0x51,
								0x40,
								0x7c,
								0x2a,
								0x0f,
								0x34,
								0x12));
		assertEquals(0x20, data.getDeviceId());
		assertEquals(1, data.getDeviceRevision());
		assertTrue(data.providesDeviceSdrs());
		assertFalse(data.isDeviceAvailable());
		assertEquals(1, data.getMajorFirmwareRevision());
		assertEquals(0x05, data.getMinorFirmwareRevision());
		assertEquals("1.05", data.getFirmwareRevision(), "the minor revision keeps its two BCD digits");
		assertEquals("1.5", data.getIpmiVersion());
		assertEquals((byte) 0x40, data.getAdditionalDeviceSupport());
		assertEquals(0x0f2a7c, data.getManufacturerId());
		assertEquals(0x1234, data.getProductId());
		assertEquals(0, data.getAuxiliaryFirmwareRevision().length);
	}

	@Test
	void theAuxiliaryRevisionIsReadAndTheReservedBitsOfTheManufacturerIdAreIgnored() {
		// Lenovo (IANA 19046 = 4A66h) with the reserved bits of byte 9 set, firmware 127.44, IPMI 2.0
		GetDeviceIdResponseData data = new GetDeviceIdResponseData(
				new byte[]
				{
						0x00,
						0x00,
						0x7f,
						0x44,
						0x02,
						0x00,
						0x66,
						0x4a,
						(byte) 0xf0,
						0x01,
						0x00,
						(byte) 0xde,
						(byte) 0xad,
						(byte) 0xbe,
						(byte) 0xef });
		assertEquals(19046, data.getManufacturerId());
		assertEquals(1, data.getProductId());
		assertEquals("127.44", data.getFirmwareRevision());
		assertEquals("2.0", data.getIpmiVersion());
		assertTrue(data.isDeviceAvailable());
		assertArrayEquals(
				new byte[]
				{ (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef },
				data.getAuxiliaryFirmwareRevision());
	}

	@Test
	void aShortResponseIsRejected() {
		GetDeviceId command = new GetDeviceId();
		assertThrows(
				IllegalArgumentException.class,
				() -> command
						.getResponseData(response(CommandCodes.GET_DEVICE_ID, 0x00, 0x20, 0x01, 0x01, 0x44, 0x02, 0, 0, 0, 0, 0)));
	}
}
