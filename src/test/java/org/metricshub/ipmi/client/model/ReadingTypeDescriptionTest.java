package org.metricshub.ipmi.client.model;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;

class ReadingTypeDescriptionTest {

	@Test
	void testGetReadingType() {
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.Temperature));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.Voltage));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.Current));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.Fan));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.CoolingDevice));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.OtherUnitsBasedSensor));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.PostMemoryResize));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.ModuleBoard));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.MicrocontrollerCoprocessor));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.AddInCard));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.Chassis));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.OtherFru));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.Terminator));
		assertNull(ReadingTypeDescription.getReadingType(ReadingType.MonitorAsicIc));
	}

}
