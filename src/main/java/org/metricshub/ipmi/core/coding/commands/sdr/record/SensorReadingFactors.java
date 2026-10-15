package org.metricshub.ipmi.core.coding.commands.sdr.record;

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
 * The factors that convert a raw reading into a value in the units of the sensor: M, B and their exponents, with the
 * tolerance and the accuracy. They are laid out the same way in bytes 25-30 of the Full Sensor Record (IPMI 2.0
 * table 43-1) and in bytes 3-8 of the reply to Get Sensor Reading Factors (table 35-5).
 */
public final class SensorReadingFactors {

	private final double m;
	private final double b;
	private final int rExp;
	private final double tolerance;
	private final double accuracy;

	/**
	 * Decodes the 6 bytes of the factors.
	 *
	 * @param data the bytes holding the factors
	 * @param offset the index of the factors (the M LS byte) in {@code data}
	 */
	public SensorReadingFactors(byte[] data, int offset) {
		int calcM = TypeConverter.byteToInt(data[offset]) | (TypeConverter.byteToInt(data[offset + 1]) & 0xc0) << 2;
		m = TypeConverter.decode2sComplement(calcM, 9);

		int calcB = TypeConverter.byteToInt(data[offset + 2]) | (TypeConverter.byteToInt(data[offset + 3]) & 0xc0) << 2;
		int bExp = TypeConverter.decode2sComplement(TypeConverter.byteToInt(data[offset + 5]) & 0xf, 3);
		b = TypeConverter.decode2sComplement(calcB, 9) * Math.pow(10, bExp);

		rExp = TypeConverter.decode2sComplement((TypeConverter.byteToInt(data[offset + 5]) & 0xf0) >> 4, 3);

		// Tolerance in half raw counts: +/- tolerance / 2 x |M| x 10^R (section 36.4.1)
		tolerance = (TypeConverter.byteToInt(data[offset + 1]) & 0x3f) / 2.0 * Math.abs(m) * Math.pow(10, rExp);

		int calcAcc = TypeConverter.byteToInt(data[offset + 3]) & 0x3f
				| (TypeConverter.byteToInt(data[offset + 4]) & 0xf0) << 2;
		int accExp = (TypeConverter.byteToInt(data[offset + 4]) & 0x0c) >> 2;
		accuracy = (double) calcAcc / 10000 * Math.pow(10, accExp);
	}

	/**
	 * Applies the linear part of the conversion formula (section 36.3): {@code (M x + B 10^K1) 10^K2}, without any
	 * linearization function.
	 *
	 * @param x the raw reading, already decoded as a signed or unsigned number
	 * @return the value
	 */
	public double convert(int x) {
		return (m * x + b) * Math.pow(10, rExp);
	}

	double getM() {
		return m;
	}

	int getRExp() {
		return rExp;
	}

	/**
	 * Returns the tolerance, in the units of the sensor.
	 *
	 * @return the tolerance, +/-
	 */
	public double getTolerance() {
		return tolerance;
	}

	/**
	 * Returns the accuracy.
	 *
	 * @return the accuracy, as a fraction (0.01 for 1 %)
	 */
	public double getAccuracy() {
		return accuracy;
	}
}
