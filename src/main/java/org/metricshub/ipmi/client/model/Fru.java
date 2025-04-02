package org.metricshub.ipmi.client.model;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.util.List;

import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;

/**
 * Wraps the Field Replaceable Unit information:
 * <ul>
 * <li>The FRU locator which contains the FRU location information.</li>
 * <li>The FRU records containing {@link BoardInfo}, {@link ChassisInfo} and/or {@link ProductInfo}.</li>
 * </ul>
 */
public class Fru {

	private FruDeviceLocatorRecord fruLocator;
	private List<FruRecord> fruRecords;

	public Fru(FruDeviceLocatorRecord fruLocator, List<FruRecord> fruRecords) {
		this.fruLocator = fruLocator;
		this.fruRecords = fruRecords;
	}

	/**
	 *
	 * @return Current {@link FruDeviceLocatorRecord} instance
	 */
	public FruDeviceLocatorRecord getFruLocator() {
		return fruLocator;
	}

	/**
	 *
	 * @return Current {@link FruRecord} instance
	 */
	public List<FruRecord> getFruRecords() {
		return fruRecords;
	}

}
