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
 * Wrapper class for Full Sensor Record format
 */
public class FullSensorRecord extends AbstractSensorRecord {

	private double m;

	private double tolerance;

	private double b;

	private double accuracy;

	private int rExp;

	private double nominalReading;

	private double normalMaximum;

	private double normalMinimum;

	private double sensorMaximumReading;

	private double sensorMinmumReading;

	// A threshold the record does not define (Table 43-1, byte 12 bits [3:2] and the readable mask of byte 19) stays NaN
	private double upperNonRecoverableThreshold = Double.NaN;

	private double lowerNonRecoverableThreshold = Double.NaN;

	private double upperCriticalThreshold = Double.NaN;

	private double lowerCriticalThreshold = Double.NaN;

	private double upperNonCriticalThreshold = Double.NaN;

	private double lowerNonCriticalThreshold = Double.NaN;

	private byte sensorUnits1;

	private int linearization;

	private static final int NO_ANALOG_READING = 3;

	/** Last linearization code (cube root, Table 43-1 byte 24) the library can apply. */
	private static final int LAST_LINEARIZABLE = 0x0b;

	@Override
	protected void populateTypeSpecficValues(
			byte[] recordData,
			SensorRecord record) {
		populateSensorHeader(recordData, 12);
		populateCapabilitiesAndUnits(recordData);

		int calcM = TypeConverter.byteToInt(recordData[24]);

		calcM |= (TypeConverter.byteToInt(recordData[25]) & 0xc0) << 2;

		setM(TypeConverter.decode2sComplement(calcM, 9));

		sensorUnits1 = recordData[20];
		// Needed by calcFormula(): set before the first conversion
		linearization = TypeConverter.byteToInt(recordData[23]) & 0x7f;

		int calcB = TypeConverter.byteToInt(recordData[26]);

		calcB |= (TypeConverter.byteToInt(recordData[27]) & 0xc0) << 2;

		setB(TypeConverter.decode2sComplement(calcB, 9));

		int calcAcc = TypeConverter.byteToInt(recordData[27]) & 0x3f;

		calcAcc |= (TypeConverter.byteToInt(recordData[28]) & 0xf0) << 2;

		int exp = (TypeConverter.byteToInt(recordData[28]) & 0x0c) >> 2;

		setAccuracy((double) calcAcc / 10000 * Math.pow(10, exp));

		setSensorDirection(
				SensorDirection
						.parseInt(
								TypeConverter
										.byteToInt(recordData[28])
										& 0x3));

		setrExp(
				TypeConverter
						.decode2sComplement(
								(TypeConverter.byteToInt(recordData[29]) & 0xf0) >> 4,
								3));

		int bExp = TypeConverter
				.decode2sComplement(
						TypeConverter.byteToInt(recordData[29]) & 0xf,
						3);

		setB(getB() * Math.pow(10, bExp));

		setNominalReading(calcFormula(TypeConverter.byteToInt(recordData[31])));
		setNormalMaximum(calcFormula(TypeConverter.byteToInt(recordData[32])));
		setNormalMinimum(calcFormula(TypeConverter.byteToInt(recordData[33])));

		setSensorMaximumReading(
				calcFormula(
						TypeConverter
								.byteToInt(recordData[34])));
		setSensorMinmumReading(
				calcFormula(
						TypeConverter
								.byteToInt(recordData[35])));

		// Byte 12 bits [3:2] (Table 43-1): 00b means the sensor has no thresholds
		if ((TypeConverter.byteToInt(recordData[11]) & 0x0c) != 0) {
			if ((TypeConverter.byteToInt(recordData[18]) & 0x20) != 0) {
				setUpperNonRecoverableThreshold(
						calcFormula(
								TypeConverter
										.byteToInt(recordData[36])));
			}
			if ((TypeConverter.byteToInt(recordData[18]) & 0x10) != 0) {
				setUpperCriticalThreshold(
						calcFormula(
								TypeConverter
										.byteToInt(recordData[37])));
			}
			if ((TypeConverter.byteToInt(recordData[18]) & 0x8) != 0) {
				setUpperNonCriticalThreshold(
						calcFormula(
								TypeConverter
										.byteToInt(recordData[38])));
			}

			if ((TypeConverter.byteToInt(recordData[18]) & 0x4) != 0) {
				setLowerNonRecoverableThreshold(
						calcFormula(
								TypeConverter
										.byteToInt(recordData[39])));
			}
			if ((TypeConverter.byteToInt(recordData[18]) & 0x2) != 0) {
				setLowerCriticalThreshold(
						calcFormula(
								TypeConverter
										.byteToInt(recordData[40])));
			}
			if ((TypeConverter.byteToInt(recordData[18]) & 0x1) != 0) {
				setLowerNonCriticalThreshold(
						calcFormula(
								TypeConverter
										.byteToInt(recordData[41])));
			}
		}

		populateName(recordData, 47);

		// Tolerance in half raw counts (byte 26 bits [5:0]): +/- tolerance / 2 x |M| x 10^R (Table 43-1)
		setTolerance((TypeConverter.byteToInt(recordData[25]) & 0x3f) / 2.0 * Math.abs(getM()) * Math.pow(10, getrExp()));
	}

	private double getM() {
		return m;
	}

	private void setM(double m) {
		this.m = m;
	}

	public double getTolerance() {
		return tolerance;
	}

	public void setTolerance(double mTolerance) {
		this.tolerance = mTolerance;
	}

	private double getB() {
		return b;
	}

	private void setB(double b) {
		this.b = b;
	}

	public double getAccuracy() {
		return accuracy;
	}

	public void setAccuracy(double bAccuracy) {
		this.accuracy = bAccuracy;
	}

	private void setrExp(int rExp) {
		this.rExp = rExp;
	}

	private int getrExp() {
		return rExp;
	}

	public double getNominalReading() {
		return nominalReading;
	}

	public void setNominalReading(double nominalReading) {
		this.nominalReading = nominalReading;
	}

	public double getNormalMaximum() {
		return normalMaximum;
	}

	public void setNormalMaximum(double normalMaximum) {
		this.normalMaximum = normalMaximum;
	}

	public double getNormalMinimum() {
		return normalMinimum;
	}

	public void setNormalMinimum(double normalMinimum) {
		this.normalMinimum = normalMinimum;
	}

	public double getSensorMaximumReading() {
		return sensorMaximumReading;
	}

	public void setSensorMaximumReading(double sensorMaximumReading) {
		this.sensorMaximumReading = sensorMaximumReading;
	}

	public double getSensorMinmumReading() {
		return sensorMinmumReading;
	}

	public void setSensorMinmumReading(double sensorMinmumReading) {
		this.sensorMinmumReading = sensorMinmumReading;
	}

	public double getUpperNonRecoverableThreshold() {
		return upperNonRecoverableThreshold;
	}

	public void setUpperNonRecoverableThreshold(
			double upperNonRecoverableThreshold) {
		this.upperNonRecoverableThreshold = upperNonRecoverableThreshold;
	}

	public double getLowerNonRecoverableThreshold() {
		return lowerNonRecoverableThreshold;
	}

	public void setLowerNonRecoverableThreshold(
			double lowerNonRecoverableThreshold) {
		this.lowerNonRecoverableThreshold = lowerNonRecoverableThreshold;
	}

	public double getUpperCriticalThreshold() {
		return upperCriticalThreshold;
	}

	public void setUpperCriticalThreshold(double upperCriticalThreshold) {
		this.upperCriticalThreshold = upperCriticalThreshold;
	}

	public double getLowerCriticalThreshold() {
		return lowerCriticalThreshold;
	}

	public void setLowerCriticalThreshold(double lowerCriticalThreshold) {
		this.lowerCriticalThreshold = lowerCriticalThreshold;
	}

	public double getUpperNonCriticalThreshold() {
		return upperNonCriticalThreshold;
	}

	public void setUpperNonCriticalThreshold(double upperNonCriticalThreshold) {
		this.upperNonCriticalThreshold = upperNonCriticalThreshold;
	}

	public double getLowerNonCriticalThreshold() {
		return lowerNonCriticalThreshold;
	}

	public void setLowerNonCriticalThreshold(double lowerNonCriticalThreshold) {
		this.lowerNonCriticalThreshold = lowerNonCriticalThreshold;
	}

	/**
	 * Tells whether the reading byte of this sensor converts to a value: false when the data format of Sensor Units 1
	 * is 11b (no analog reading, Table 43-1), or when the linearization is non-linear (70h-7Fh) or reserved, as the
	 * conversion then needs the Get Sensor Reading Factors command, which the library does not implement.
	 *
	 * @return whether {@link #calcFormula(int)} gives a meaningful value
	 */
	public boolean hasAnalogReading() {
		return ((TypeConverter.byteToInt(sensorUnits1) & 0xc0) >> 6) != NO_ANALOG_READING
				&& linearization <= LAST_LINEARIZABLE;
	}

	/**
	 * Converts to units-based value using the 'y=Mx+B' formula. 1's or 2's
	 * complement signed or unsigned per flag bits in Sensor Units 1.
	 *
	 * @param value
	 *        - Value to be converted. Length of 8 is assumed.
	 * @return converted value, {@link Double#NaN} when the sensor has no analog reading
	 *         ({@link #hasAnalogReading()})
	 */
	public double calcFormula(int value) {
		return calcFormula(value, 8, sensorUnits1);
	}

	/**
	 * Converts to units-based value using the 'y=Mx+B' formula. 1's or 2's
	 * complement signed or unsigned per flag bits in Sensor Units 1.
	 *
	 * @param value
	 *        - value to be converted
	 * @param length
	 *        - number of bits of value
	 * @param units1
	 *        - byte containing numeric data format
	 * @return converted value
	 * @throws IllegalArgumentException
	 *         when record's numeric values are linearized in a way that is
	 *         not supported
	 */
	protected double calcFormula(int value, int length, byte units1) {
		int dataFormat = (TypeConverter.byteToInt(units1) & 0xc0) >> 6;

		int base = 0;

		switch (dataFormat) {
		case 0: // unsigned
			base = value;
			break;
		case 1: // 1's complement
			base = TypeConverter.decode1sComplement(value, length - 1);
			break;
		case 2: // 2's complement
			base = TypeConverter.decode2sComplement(value, length - 1);
			break;
		case 3: // no analog reading
			base = value;
			break;
		default:
			throw new IllegalArgumentException(
					"Invalid data format in sensorUnits1");
		}

		double result = (getM() * base + getB()) * Math.pow(10, getrExp());

		switch (linearization) {
		case 0:
			return result;
		case 1:
			return Math.log(result);
		case 2:
			return Math.log10(result);
		case 3:
			return Math.log(result) / Math.log(2);
		case 4:
			return Math.exp(result);
		case 5:
			return Math.pow(10, result);
		case 6:
			return Math.pow(2, result);
		case 7:
			return 1 / result;
		case 8:
			return Math.pow(result, 2);
		case 9:
			return Math.pow(result, 3);
		case 10:
			return Math.pow(result, 0.5);
		case 11:
			return Math.cbrt(result);
		default:
			// 70h-7Fh are non-linear: the SDR factors hold at the nominal reading only and the conversion needs
			// Get Sensor Reading Factors, which the library does not implement; the rest is reserved
			return Double.NaN;
		}
	}

	public double getSensorResolution() {
		return Math.abs(getM() / 2.0 * Math.pow(10, getrExp()));
	}
}
