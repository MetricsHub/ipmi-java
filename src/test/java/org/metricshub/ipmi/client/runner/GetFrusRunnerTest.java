package org.metricshub.ipmi.client.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.IpmiResultConverterTest;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;

class GetFrusRunnerTest {

	@Test
	void systemBoardIsNamedAfterWhateverAreaDescribesIt() {
		ProductInfo product = new ProductInfo(IpmiResultConverterTest.BASE_BOARD_PRODUCT_INFO, 360);

		assertEquals("System x3650 M2", GetFrusRunner.systemBoardName(Collections.singletonList(product)));
		assertEquals("System Board", GetFrusRunner.systemBoardName(Collections.emptyList()));
	}
}
