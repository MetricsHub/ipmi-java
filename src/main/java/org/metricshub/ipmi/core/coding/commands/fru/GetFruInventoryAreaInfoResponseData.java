package org.metricshub.ipmi.core.coding.commands.fru;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, MetricsHub
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

import org.metricshub.ipmi.core.coding.commands.ResponseData;

/**
 * A wrapper for Get Fru Inventory Area Info response
 */
public class GetFruInventoryAreaInfoResponseData implements ResponseData {

	private int fruInventoryAreaSize;

	private BaseUnit fruUnit;

	/**
	 * Returns the size of the FRU inventory area.
	 *
	 * @return the size of the FRU inventory area in bytes (IPMI 2.0 table 34-2)
	 */
	public int getFruInventoryAreaSize() {
		return fruInventoryAreaSize;
	}

	/**
	 * Sets the size of the FRU inventory area.
	 *
	 * @param fruInventoryAreaSize the size of the FRU inventory area in bytes
	 */
	public void setFruInventoryAreaSize(int fruInventoryAreaSize) {
		this.fruInventoryAreaSize = fruInventoryAreaSize;
	}

	/**
	 * Returns how the FRU device is accessed.
	 *
	 * @return whether the device takes its offsets in bytes or in words
	 */
	public BaseUnit getFruUnit() {
		return fruUnit;
	}

	/**
	 * Sets how the FRU device is accessed.
	 *
	 * @param fruUnit whether the device takes its offsets in bytes or in words
	 */
	public void setFruUnit(BaseUnit fruUnit) {
		this.fruUnit = fruUnit;
	}
}
