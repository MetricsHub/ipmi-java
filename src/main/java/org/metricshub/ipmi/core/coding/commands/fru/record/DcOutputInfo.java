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
 * DC Output record (type 01h) from the FRU MultiRecord area
 */
public class DcOutputInfo extends MultiRecordInfo {

	private int outputNumber;

	private int nominalVoltage;

	private int maximumNegativeDeviation;

	private int maximumPositiveDeviation;

	/**
	 * Creates and populates record
	 *
	 * @param fruData
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 */
	public DcOutputInfo(byte[] fruData, int offset) {
		super();

		outputNumber = TypeConverter.byteToInt(fruData[offset]) & 0xf;

		nominalVoltage = TypeConverter.byteToInt(fruData[offset + 1]);
		nominalVoltage |= TypeConverter.byteToInt(fruData[offset + 2]) << 8;
		nominalVoltage = TypeConverter.decode2sComplement(nominalVoltage, 15);

		maximumNegativeDeviation = TypeConverter.byteToInt(fruData[offset + 3]);
		maximumNegativeDeviation |= TypeConverter.byteToInt(fruData[offset + 4]) << 8;
		maximumNegativeDeviation = TypeConverter.decode2sComplement(maximumNegativeDeviation, 15);

		maximumPositiveDeviation = TypeConverter.byteToInt(fruData[offset + 5]);
		maximumPositiveDeviation |= TypeConverter.byteToInt(fruData[offset + 6]) << 8;
		maximumPositiveDeviation = TypeConverter.decode2sComplement(maximumPositiveDeviation, 15);
	}

	/**
	 * Returns the number of the DC output.
	 *
	 * @return the output number, 0 to 15
	 */
	public int getOutputNumber() {
		return outputNumber;
	}

	/**
	 * Sets the number of the DC output.
	 *
	 * @param outputNumber the output number, 0 to 15
	 */
	public void setOutputNumber(int outputNumber) {
		this.outputNumber = outputNumber;
	}

	/**
	 * Returns the nominal voltage of the output.
	 *
	 * @return the voltage in units of 10 mV, signed
	 */
	public int getNominalVoltage() {
		return nominalVoltage;
	}

	/**
	 * Sets the nominal voltage of the output.
	 *
	 * @param nominalVoltage the voltage in units of 10 mV, signed
	 */
	public void setNominalVoltage(int nominalVoltage) {
		this.nominalVoltage = nominalVoltage;
	}

	/**
	 * Returns the maximum negative deviation from the nominal voltage.
	 *
	 * @return the deviation in units of 10 mV, signed
	 */
	public int getMaximumNegativeDeviation() {
		return maximumNegativeDeviation;
	}

	/**
	 * Sets the maximum negative deviation from the nominal voltage.
	 *
	 * @param maximumNegativeDeviation the deviation in units of 10 mV, signed
	 */
	public void setMaximumNegativeDeviation(int maximumNegativeDeviation) {
		this.maximumNegativeDeviation = maximumNegativeDeviation;
	}

	/**
	 * Returns the maximum positive deviation from the nominal voltage.
	 *
	 * @return the deviation in units of 10 mV, signed
	 */
	public int getMaximumPositiveDeviation() {
		return maximumPositiveDeviation;
	}

	/**
	 * Sets the maximum positive deviation from the nominal voltage.
	 *
	 * @param maximumPositiveDeviation the deviation in units of 10 mV, signed
	 */
	public void setMaximumPositiveDeviation(int maximumPositiveDeviation) {
		this.maximumPositiveDeviation = maximumPositiveDeviation;
	}

}
