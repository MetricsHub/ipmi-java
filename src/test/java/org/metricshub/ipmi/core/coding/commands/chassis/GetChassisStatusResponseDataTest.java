package org.metricshub.ipmi.core.coding.commands.chassis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

	@Test
	void everyIdentifyStateValueIsDecoded() {
		// IPMI 2.0 table 28-3, byte 4: bit 6 says the state is reported, bits 5:4 are the state (#207)
		GetChassisStatusResponseData data = new GetChassisStatusResponseData();
		data.setMiscChassisState((byte) 0x40);
		assertEquals(ChassisIdentifyState.Off, data.getChassisIdentifyState());
		data.setMiscChassisState((byte) 0x50);
		assertEquals(ChassisIdentifyState.TemporaryOn, data.getChassisIdentifyState());
		data.setMiscChassisState((byte) 0x60);
		assertEquals(ChassisIdentifyState.IndefiniteOn, data.getChassisIdentifyState());
		data.setMiscChassisState((byte) 0x70);
		assertEquals(ChassisIdentifyState.Unknown, data.getChassisIdentifyState());
		data.setMiscChassisState((byte) 0x30);
		assertThrows(IllegalStateException.class, data::getChassisIdentifyState);
	}
}
