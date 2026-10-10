package org.metricshub.ipmi.core.coding.payload.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IpmiLanMessageTest {

	@Test
	void theRequestCarriesTheResponderLunInTheNetFnByteAndItsOwnLunInTheSequenceByte() {
		// IPMI 2.0 figure 13-4: rsAddr, netFn/rsLUN, checksum, rqAddr, rqSeq/rqLUN, cmd, data, checksum
		byte[] request = new IpmiLanRequest(
				NetworkFunction.SensorRequest,
				(byte) 0x2d,
				new byte[]
				{ 0x42 },
				(byte) 5,
				(byte) 3).getPayloadData();

		assertEquals((0x04 << 2) | 3, request[1] & 0xff); // Sensor/Event request, LUN 3 of the BMC
		assertEquals(5 << 2, request[4] & 0xff); // sequence 5, LUN 0 of the remote console
	}

	@Test
	void aResponseShorterThanItsFixedFieldsIsRejected() {
		// rqAddr, netFn/rqLUN, checksum 1, rsAddr, rqSeq/rsLUN, cmd, completion code, checksum 2
		for (int length = 0; length < 8; length++) {
			byte[] raw = new byte[length];
			assertThrows(IllegalArgumentException.class, () -> new IpmiLanResponse(raw), length + " bytes");
		}
	}
}
