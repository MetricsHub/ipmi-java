package org.metricshub.ipmi.core.coding.commands.chassis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GetChassisStatusResponseDataTest {

	private static PowerRestorePolicy policy(int currentPowerState) {
		GetChassisStatusResponseData data = new GetChassisStatusResponseData();
		data.setCurrentPowerState((byte) currentPowerState);
		return data.getPowerRestorePolicy();
	}

	@Test
	void everyPowerRestorePolicyValueIsDecoded() {
		// IPMI 2.0 Table 28-3, bits [6:5] of the current power state
		assertEquals(PowerRestorePolicy.PoweredOff, policy(0x00));
		assertEquals(PowerRestorePolicy.PowerRestored, policy(0x20));
		assertEquals(PowerRestorePolicy.PoweredUp, policy(0x40));
		assertEquals(PowerRestorePolicy.Unknown, policy(0x60));
	}
}
