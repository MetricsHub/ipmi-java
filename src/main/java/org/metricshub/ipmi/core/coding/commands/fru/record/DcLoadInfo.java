package org.metricshub.ipmi.core.coding.commands.fru.record;

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

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * DC Load record (type 02h) from the FRU MultiRecord area<br>
 * This record is used to describe the maximum load that a device requires from
 * a particular DC Output.
 */
public class DcLoadInfo extends MultiRecordInfo {

	private int outputNumber;

	private int nominalVoltage;

	private int minimumVoltage;

	private int maximumVoltage;

	private int minimumCurrentLoad;

	private int maximumCurrentLoad;

	/**
	 * Creates and populates record
	 *
	 * @param fruData
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 */
	public DcLoadInfo(byte[] fruData, int offset) {
		super();

		outputNumber = TypeConverter.byteToInt(fruData[offset]) & 0xf;

		nominalVoltage = TypeConverter.byteToInt(fruData[offset + 1]);
		nominalVoltage |= TypeConverter.byteToInt(fruData[offset + 2]) << 8;
		nominalVoltage = TypeConverter.decode2sComplement(nominalVoltage, 15);

		minimumVoltage = TypeConverter.byteToInt(fruData[offset + 3]);
		minimumVoltage |= TypeConverter.byteToInt(fruData[offset + 4]) << 8;
		minimumVoltage = TypeConverter.decode2sComplement(minimumVoltage, 15);

		maximumVoltage = TypeConverter.byteToInt(fruData[offset + 5]);
		maximumVoltage |= TypeConverter.byteToInt(fruData[offset + 6]) << 8;
		maximumVoltage = TypeConverter.decode2sComplement(maximumVoltage, 15);

		minimumCurrentLoad = TypeConverter.byteToInt(fruData[offset + 9]);
		minimumCurrentLoad |= TypeConverter.byteToInt(fruData[offset + 10]) << 8;

		maximumCurrentLoad = TypeConverter.byteToInt(fruData[offset + 11]);
		maximumCurrentLoad |= TypeConverter.byteToInt(fruData[offset + 12]) << 8;
	}

	/**
	 * Returns the number of the DC output the device draws from.
	 *
	 * @return the output number, 0 to 15
	 */
	public int getOutputNumber() {
		return outputNumber;
	}

	/**
	 * Sets the number of the DC output the device draws from.
	 *
	 * @param outputNumber the output number, 0 to 15
	 */
	public void setOutputNumber(int outputNumber) {
		this.outputNumber = outputNumber;
	}

	/**
	 * Returns the nominal voltage the device requires.
	 *
	 * @return the voltage in units of 10 mV, signed
	 */
	public int getNominalVoltage() {
		return nominalVoltage;
	}

	/**
	 * Sets the nominal voltage the device requires.
	 *
	 * @param nominalVoltage the voltage in units of 10 mV, signed
	 */
	public void setNominalVoltage(int nominalVoltage) {
		this.nominalVoltage = nominalVoltage;
	}

	/**
	 * Returns the maximum voltage the device accepts.
	 *
	 * @return the voltage in units of 10 mV, signed
	 */
	public int getMaximumVoltage() {
		return maximumVoltage;
	}

	/**
	 * Sets the maximum voltage the device accepts.
	 *
	 * @param maximumVoltage the voltage in units of 10 mV, signed
	 */
	public void setMaximumVoltage(int maximumVoltage) {
		this.maximumVoltage = maximumVoltage;
	}

	/**
	 * Returns the minimum voltage the device accepts.
	 *
	 * @return the voltage in units of 10 mV, signed
	 */
	public int getMinimumVoltage() {
		return minimumVoltage;
	}

	/**
	 * Sets the minimum voltage the device accepts.
	 *
	 * @param minimumVoltage the voltage in units of 10 mV, signed
	 */
	public void setMinimumVoltage(int minimumVoltage) {
		this.minimumVoltage = minimumVoltage;
	}

	/**
	 * Returns the maximum current load of the device on the output.
	 *
	 * @return the current in mA
	 */
	public int getMaximumCurrentLoad() {
		return maximumCurrentLoad;
	}

	/**
	 * Sets the maximum current load of the device on the output.
	 *
	 * @param maximumCurrentLoad the current in mA
	 */
	public void setMaximumCurrentLoad(int maximumCurrentLoad) {
		this.maximumCurrentLoad = maximumCurrentLoad;
	}

	/**
	 * Returns the minimum current load of the device on the output.
	 *
	 * @return the current in mA
	 */
	public int getMinimumCurrentLoad() {
		return minimumCurrentLoad;
	}

	/**
	 * Sets the minimum current load of the device on the output.
	 *
	 * @param minimumCurrentLoad the current in mA
	 */
	public void setMinimumCurrentLoad(int minimumCurrentLoad) {
		this.minimumCurrentLoad = minimumCurrentLoad;
	}

}
