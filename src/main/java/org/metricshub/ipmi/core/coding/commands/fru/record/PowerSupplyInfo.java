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
 * Power Supply Information record from FRU Multi Record Area
 */
public class PowerSupplyInfo extends MultiRecordInfo {

	private int capacity;

	private int peakVa;

	private int maximumInrush;

	private int lowEndInputVoltage1;

	private int highEndInputVoltage1;

	private int lowEndInputVoltage2;

	private int highEndInputVoltage2;

	private int lowEndInputFrequencyRange;

	private int highEndInputFrequencyRange;

	/**
	 * Creates and populates record
	 *
	 * @param fruData
	 *        - raw data containing record
	 * @param offset
	 *        - offset to the record in the data
	 */
	public PowerSupplyInfo(byte[] fruData, int offset) {
		super();

		// 12-bit capacity, least significant byte first (FRU spec section 18.1)
		capacity = TypeConverter.byteToInt(fruData[offset]);
		capacity |= (TypeConverter.byteToInt(fruData[offset + 1]) & 0x0f) << 8;

		peakVa = TypeConverter.byteToInt(fruData[offset + 2]);
		peakVa |= TypeConverter.byteToInt(fruData[offset + 3]) << 8;

		if (peakVa == 0xffff) {
			peakVa = -1;
		}

		maximumInrush = TypeConverter.byteToInt(fruData[offset + 4]);

		lowEndInputVoltage1 = TypeConverter.byteToInt(fruData[offset + 6]);
		lowEndInputVoltage1 |= TypeConverter.byteToInt(fruData[offset + 7]) << 8;

		highEndInputVoltage1 = TypeConverter.byteToInt(fruData[offset + 8]);
		highEndInputVoltage1 |= TypeConverter.byteToInt(fruData[offset + 9]) << 8;

		lowEndInputVoltage2 = TypeConverter.byteToInt(fruData[offset + 10]);
		lowEndInputVoltage2 |= TypeConverter.byteToInt(fruData[offset + 11]) << 8;

		highEndInputVoltage2 = TypeConverter.byteToInt(fruData[offset + 12]);
		highEndInputVoltage2 |= TypeConverter.byteToInt(fruData[offset + 13]) << 8;

		lowEndInputFrequencyRange = TypeConverter
				.byteToInt(fruData[offset + 14]);
		highEndInputFrequencyRange = TypeConverter
				.byteToInt(fruData[offset + 15]);
	}

	/**
	 * Returns the overall capacity of the power supply.
	 *
	 * @return the overall capacity in watts (12 bits)
	 */
	public int getCapacity() {
		return capacity;
	}

	/**
	 * Sets the overall capacity of the power supply.
	 *
	 * @param capacity the overall capacity in watts (12 bits)
	 */
	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	/**
	 * Returns the highest instantaneous VA value that the power supply draws during operation.
	 *
	 * @return the peak VA, -1 when the record leaves it unspecified (FFFFh)
	 */
	public int getPeakVa() {
		return peakVa;
	}

	/**
	 * Sets the highest instantaneous VA value that the power supply draws during operation.
	 *
	 * @param peakVa the peak VA, -1 when the record leaves it unspecified (FFFFh)
	 */
	public void setPeakVa(int peakVa) {
		this.peakVa = peakVa;
	}

	/**
	 * Returns the maximum inrush of current into the power supply.
	 *
	 * @return the maximum inrush current in amperes
	 */
	public int getMaximumInrush() {
		return maximumInrush;
	}

	/**
	 * Sets the maximum inrush of current into the power supply.
	 *
	 * @param maximumInrush the maximum inrush current in amperes
	 */
	public void setMaximumInrush(int maximumInrush) {
		this.maximumInrush = maximumInrush;
	}

	/**
	 * Returns the low end of the acceptable input voltage range 1.
	 * Range 2 is used when the power supply does not autoswitch: range 1 is then the 110 V range and
	 * range 2 the 220 V range.
	 *
	 * @return the voltage in units of 10 mV
	 */
	public int getLowEndInputVoltage1() {
		return lowEndInputVoltage1;
	}

	/**
	 * Sets the low end of the acceptable input voltage range 1.
	 *
	 * @param lowEndInputVoltage1 the voltage in units of 10 mV
	 */
	public void setLowEndInputVoltage1(int lowEndInputVoltage1) {
		this.lowEndInputVoltage1 = lowEndInputVoltage1;
	}

	/**
	 * Returns the high end of the acceptable input voltage range 1.
	 * Range 2 is used when the power supply does not autoswitch: range 1 is then the 110 V range and
	 * range 2 the 220 V range.
	 *
	 * @return the voltage in units of 10 mV
	 */
	public int getHighEndInputVoltage1() {
		return highEndInputVoltage1;
	}

	/**
	 * Sets the high end of the acceptable input voltage range 1.
	 *
	 * @param highEndInputVoltage1 the voltage in units of 10 mV
	 */
	public void setHighEndInputVoltage1(int highEndInputVoltage1) {
		this.highEndInputVoltage1 = highEndInputVoltage1;
	}

	/**
	 * Returns the low end of the acceptable input voltage range 2.
	 * Range 2 is used when the power supply does not autoswitch: range 1 is then the 110 V range and
	 * range 2 the 220 V range.
	 *
	 * @return the voltage in units of 10 mV
	 */
	public int getLowEndInputVoltage2() {
		return lowEndInputVoltage2;
	}

	/**
	 * Sets the low end of the acceptable input voltage range 2.
	 *
	 * @param lowEndInputVoltage2 the voltage in units of 10 mV
	 */
	public void setLowEndInputVoltage2(int lowEndInputVoltage2) {
		this.lowEndInputVoltage2 = lowEndInputVoltage2;
	}

	/**
	 * Returns the high end of the acceptable input voltage range 2.
	 * Range 2 is used when the power supply does not autoswitch: range 1 is then the 110 V range and
	 * range 2 the 220 V range.
	 *
	 * @return the voltage in units of 10 mV
	 */
	public int getHighEndInputVoltage2() {
		return highEndInputVoltage2;
	}

	/**
	 * Sets the high end of the acceptable input voltage range 2.
	 *
	 * @param highEndInputVoltage2 the voltage in units of 10 mV
	 */
	public void setHighEndInputVoltage2(int highEndInputVoltage2) {
		this.highEndInputVoltage2 = highEndInputVoltage2;
	}

	/**
	 * Returns the low end of the acceptable input frequency range.
	 *
	 * @return the frequency in Hz
	 */
	public int getLowEndInputFrequencyRange() {
		return lowEndInputFrequencyRange;
	}

	/**
	 * Sets the low end of the acceptable input frequency range.
	 *
	 * @param lowEndInputFrequencyRange the frequency in Hz
	 */
	public void setLowEndInputFrequencyRange(int lowEndInputFrequencyRange) {
		this.lowEndInputFrequencyRange = lowEndInputFrequencyRange;
	}

	/**
	 * Returns the high end of the acceptable input frequency range.
	 *
	 * @return the frequency in Hz
	 */
	public int getHighEndInputFrequencyRange() {
		return highEndInputFrequencyRange;
	}

	/**
	 * Sets the high end of the acceptable input frequency range.
	 *
	 * @param highEndInputFrequencyRange the frequency in Hz
	 */
	public void setHighEndInputFrequencyRange(int highEndInputFrequencyRange) {
		this.highEndInputFrequencyRange = highEndInputFrequencyRange;
	}

}
