package org.metricshub.ipmi.core.coding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.commands.sdr.GetDeviceSdr;
import org.metricshub.ipmi.core.coding.commands.sdr.GetDeviceSdrInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;

/**
 * The names fixed by #217: the deprecated aliases are the new constants, and only the new spelling is listed.
 */
@SuppressWarnings("deprecation")
class MisspelledNamesTest {

	@Test
	void sensorTypeAlias() {
		assertSame(SensorType.SystemFirmwareProgress, SensorType.SystemFirmwareProgess);
		assertSame(SensorType.SystemFirmwareProgress, SensorType.parseInt(0x0f));
		assertEquals("SystemFirmwareProgress", SensorType.SystemFirmwareProgess.name());
		assertSame(SensorType.SystemFirmwareProgress, SensorType.valueOf("SystemFirmwareProgress"));
		assertThrows(IllegalArgumentException.class, () -> SensorType.valueOf("SystemFirmwareProgess"));
		assertFalse(Arrays.stream(SensorType.values()).anyMatch(t -> t.name().equals("SystemFirmwareProgess")));
	}

	@Test
	void completionCodeAlias() {
		assertSame(CompletionCode.InsufficientPrivilege, CompletionCode.InsufficentPrivilege);
		assertSame(CompletionCode.InsufficientPrivilege, CompletionCode.parseInt(0xd4));
		assertEquals("InsufficientPrivilege", CompletionCode.InsufficentPrivilege.name());
		assertThrows(IllegalArgumentException.class, () -> CompletionCode.valueOf("InsufficentPrivilege"));
	}

	@Test
	void deviceSdrCommandCodes() {
		assertEquals(0x20, CommandCodes.GET_DEVICE_SDR_INFO);
		assertEquals(0x21, CommandCodes.GET_DEVICE_SDR);
		assertEquals(CommandCodes.GET_DEVICE_SDR_INFO, new GetDeviceSdrInfo(false).getCommandCode());
		assertEquals(CommandCodes.GET_DEVICE_SDR, new GetDeviceSdr(0, 0).getCommandCode());
	}
}
