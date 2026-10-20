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
 * A Full Sensor Record (SDR type 01h, IPMI 2.0 table 43-1): one sensor, with the fields of
 * {@link AbstractSensorRecord}, the factors that convert its reading bytes to values in its unit
 * ({@link #calcFormula(int)}), its nominal and normal readings, its reading range and its thresholds.
 */
public class FullSensorRecord extends AbstractSensorRecord {

	private SensorReadingFactors factors;

	private double tolerance;

	private double accuracy;

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

	/** Event/reading type code of the threshold sensors, the only ones with an analog reading (IPMI 2.0 table 42-1). */
	private static final int THRESHOLD_EVENT_READING_TYPE = 1;

	/** Last linearization code (cube root, Table 43-1 byte 24) the library can apply. */
	private static final int LAST_LINEARIZABLE = 0x0b;

	/** Linearization codes of the non-linear sensors (Table 43-1 byte 24): 70h, and 71h-7Fh OEM defined. */
	private static final int FIRST_NON_LINEAR = 0x70;

	private static final int LAST_NON_LINEAR = 0x7f;

	@Override
	protected void populateTypeSpecficValues(
			byte[] recordData,
			SensorRecord record) {
		populateSensorHeader(recordData, 12);
		populateCapabilitiesAndUnits(recordData);

		factors = new SensorReadingFactors(recordData, 24);
		setTolerance(factors.getTolerance());
		setAccuracy(factors.getAccuracy());

		sensorUnits1 = recordData[20];
		// Needed by calcFormula(): set before the first conversion
		linearization = TypeConverter.byteToInt(recordData[23]) & 0x7f;

		setSensorDirection(
				SensorDirection
						.parseInt(
								TypeConverter
										.byteToInt(recordData[28])
										& 0x3));

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
	}

	/**
	 * Returns the tolerance of the readings of the sensor, in its unit, from the factors of the record (IPMI 2.0
	 * section 36.4.1).
	 *
	 * @return the tolerance, +/-
	 */
	public double getTolerance() {
		return tolerance;
	}

	/**
	 * Sets the tolerance of the readings.
	 *
	 * @param mTolerance the tolerance, +/-, in the unit of the sensor
	 */
	public void setTolerance(double mTolerance) {
		this.tolerance = mTolerance;
	}

	/**
	 * Returns the accuracy of the readings of the sensor, from the factors of the record (bytes 28 and 29 of the
	 * record).
	 *
	 * @return the accuracy, as a fraction (0.01 for 1%)
	 */
	public double getAccuracy() {
		return accuracy;
	}

	/**
	 * Sets the accuracy of the readings.
	 *
	 * @param bAccuracy the accuracy, as a fraction (0.01 for 1%)
	 */
	public void setAccuracy(double bAccuracy) {
		this.accuracy = bAccuracy;
	}

	/**
	 * Returns the nominal reading of the sensor (byte 32 of the record), converted to the unit of the sensor, whether
	 * or not the analog characteristic flags of byte 31 (bit 0) say that the record specifies it.
	 *
	 * @return the value, {@link Double#NaN} when the data format of Sensor Units 1 is 11b (no analog reading) or the
	 *         sensor is
	 *         non-linear ({@link #isNonLinear()})
	 */
	public double getNominalReading() {
		return nominalReading;
	}

	/**
	 * Sets the nominal reading of the sensor.
	 *
	 * @param nominalReading the nominal reading, in the unit of the sensor
	 */
	public void setNominalReading(double nominalReading) {
		this.nominalReading = nominalReading;
	}

	/**
	 * Returns the normal maximum of the sensor (byte 33 of the record), converted to the unit of the sensor, whether
	 * or not the analog characteristic flags of byte 31 (bit 1) say that the record specifies it.
	 *
	 * @return the value, {@link Double#NaN} when the data format of Sensor Units 1 is 11b (no analog reading) or the
	 *         sensor is
	 *         non-linear ({@link #isNonLinear()})
	 */
	public double getNormalMaximum() {
		return normalMaximum;
	}

	/**
	 * Sets the normal maximum of the sensor.
	 *
	 * @param normalMaximum the normal maximum, in the unit of the sensor
	 */
	public void setNormalMaximum(double normalMaximum) {
		this.normalMaximum = normalMaximum;
	}

	/**
	 * Returns the normal minimum of the sensor (byte 34 of the record), converted to the unit of the sensor, whether
	 * or not the analog characteristic flags of byte 31 (bit 2) say that the record specifies it.
	 *
	 * @return the value, {@link Double#NaN} when the data format of Sensor Units 1 is 11b (no analog reading) or the
	 *         sensor is
	 *         non-linear ({@link #isNonLinear()})
	 */
	public double getNormalMinimum() {
		return normalMinimum;
	}

	/**
	 * Sets the normal minimum of the sensor.
	 *
	 * @param normalMinimum the normal minimum, in the unit of the sensor
	 */
	public void setNormalMinimum(double normalMinimum) {
		this.normalMinimum = normalMinimum;
	}

	/**
	 * Returns the maximum reading of the sensor (byte 35 of the record), converted to the unit of the sensor.
	 *
	 * @return the value, {@link Double#NaN} when the data format of Sensor Units 1 is 11b (no analog reading) or the
	 *         sensor is
	 *         non-linear ({@link #isNonLinear()})
	 */
	public double getSensorMaximumReading() {
		return sensorMaximumReading;
	}

	/**
	 * Sets the maximum reading of the sensor.
	 *
	 * @param sensorMaximumReading the maximum reading, in the unit of the sensor
	 */
	public void setSensorMaximumReading(double sensorMaximumReading) {
		this.sensorMaximumReading = sensorMaximumReading;
	}

	/**
	 * Returns the minimum reading of the sensor (byte 36 of the record), converted to the unit of the sensor.
	 *
	 * @return the value, {@link Double#NaN} when the data format of Sensor Units 1 is 11b (no analog reading) or the
	 *         sensor is
	 *         non-linear ({@link #isNonLinear()})
	 */
	public double getSensorMinmumReading() {
		return sensorMinmumReading;
	}

	/**
	 * Sets the minimum reading of the sensor.
	 *
	 * @param sensorMinmumReading the minimum reading, in the unit of the sensor
	 */
	public void setSensorMinmumReading(double sensorMinmumReading) {
		this.sensorMinmumReading = sensorMinmumReading;
	}

	/**
	 * Returns the upper non-recoverable threshold of the sensor (byte 37 of the record), converted to the unit of the
	 * sensor.
	 *
	 * @return the threshold, {@link Double#NaN} when the record does not define it (byte 12 bits 3:2 and the readable
	 *         threshold mask of byte 19), when the data format of Sensor Units 1 is 11b (no analog reading) or when
	 *         it is non-linear ({@link #isNonLinear()})
	 */
	public double getUpperNonRecoverableThreshold() {
		return upperNonRecoverableThreshold;
	}

	/**
	 * Sets the upper non-recoverable threshold of the sensor.
	 *
	 * @param upperNonRecoverableThreshold the threshold, in the unit of the sensor, {@link Double#NaN} when the record
	 *        does not define it
	 */
	public void setUpperNonRecoverableThreshold(
			double upperNonRecoverableThreshold) {
		this.upperNonRecoverableThreshold = upperNonRecoverableThreshold;
	}

	/**
	 * Returns the lower non-recoverable threshold of the sensor (byte 40 of the record), converted to the unit of the
	 * sensor.
	 *
	 * @return the threshold, {@link Double#NaN} when the record does not define it (byte 12 bits 3:2 and the readable
	 *         threshold mask of byte 19), when the data format of Sensor Units 1 is 11b (no analog reading) or when
	 *         it is non-linear ({@link #isNonLinear()})
	 */
	public double getLowerNonRecoverableThreshold() {
		return lowerNonRecoverableThreshold;
	}

	/**
	 * Sets the lower non-recoverable threshold of the sensor.
	 *
	 * @param lowerNonRecoverableThreshold the threshold, in the unit of the sensor, {@link Double#NaN} when the record
	 *        does not define it
	 */
	public void setLowerNonRecoverableThreshold(
			double lowerNonRecoverableThreshold) {
		this.lowerNonRecoverableThreshold = lowerNonRecoverableThreshold;
	}

	/**
	 * Returns the upper critical threshold of the sensor (byte 38 of the record), converted to the unit of the sensor.
	 *
	 * @return the threshold, {@link Double#NaN} when the record does not define it (byte 12 bits 3:2 and the readable
	 *         threshold mask of byte 19), when the data format of Sensor Units 1 is 11b (no analog reading) or when
	 *         it is non-linear ({@link #isNonLinear()})
	 */
	public double getUpperCriticalThreshold() {
		return upperCriticalThreshold;
	}

	/**
	 * Sets the upper critical threshold of the sensor.
	 *
	 * @param upperCriticalThreshold the threshold, in the unit of the sensor, {@link Double#NaN} when the record does
	 *        not define it
	 */
	public void setUpperCriticalThreshold(double upperCriticalThreshold) {
		this.upperCriticalThreshold = upperCriticalThreshold;
	}

	/**
	 * Returns the lower critical threshold of the sensor (byte 41 of the record), converted to the unit of the sensor.
	 *
	 * @return the threshold, {@link Double#NaN} when the record does not define it (byte 12 bits 3:2 and the readable
	 *         threshold mask of byte 19), when the data format of Sensor Units 1 is 11b (no analog reading) or when
	 *         it is non-linear ({@link #isNonLinear()})
	 */
	public double getLowerCriticalThreshold() {
		return lowerCriticalThreshold;
	}

	/**
	 * Sets the lower critical threshold of the sensor.
	 *
	 * @param lowerCriticalThreshold the threshold, in the unit of the sensor, {@link Double#NaN} when the record does
	 *        not define it
	 */
	public void setLowerCriticalThreshold(double lowerCriticalThreshold) {
		this.lowerCriticalThreshold = lowerCriticalThreshold;
	}

	/**
	 * Returns the upper non-critical threshold of the sensor (byte 39 of the record), converted to the unit of the
	 * sensor.
	 *
	 * @return the threshold, {@link Double#NaN} when the record does not define it (byte 12 bits 3:2 and the readable
	 *         threshold mask of byte 19), when the data format of Sensor Units 1 is 11b (no analog reading) or when
	 *         it is non-linear ({@link #isNonLinear()})
	 */
	public double getUpperNonCriticalThreshold() {
		return upperNonCriticalThreshold;
	}

	/**
	 * Sets the upper non-critical threshold of the sensor.
	 *
	 * @param upperNonCriticalThreshold the threshold, in the unit of the sensor, {@link Double#NaN} when the record
	 *        does not define it
	 */
	public void setUpperNonCriticalThreshold(double upperNonCriticalThreshold) {
		this.upperNonCriticalThreshold = upperNonCriticalThreshold;
	}

	/**
	 * Returns the lower non-critical threshold of the sensor (byte 42 of the record), converted to the unit of the
	 * sensor.
	 *
	 * @return the threshold, {@link Double#NaN} when the record does not define it (byte 12 bits 3:2 and the readable
	 *         threshold mask of byte 19), when the data format of Sensor Units 1 is 11b (no analog reading) or when
	 *         it is non-linear ({@link #isNonLinear()})
	 */
	public double getLowerNonCriticalThreshold() {
		return lowerNonCriticalThreshold;
	}

	/**
	 * Sets the lower non-critical threshold of the sensor.
	 *
	 * @param lowerNonCriticalThreshold the threshold, in the unit of the sensor, {@link Double#NaN} when the record
	 *        does not define it
	 */
	public void setLowerNonCriticalThreshold(double lowerNonCriticalThreshold) {
		this.lowerNonCriticalThreshold = lowerNonCriticalThreshold;
	}

	/**
	 * Tells whether the reading byte of this sensor converts to a value: false for a discrete sensor (an event/reading
	 * type other than 01h, whose Get Sensor Reading reply carries states, IPMI 2.0 section 42.1 and table 35-15), when
	 * the data format of Sensor Units 1 is 11b (no analog reading, Table 43-1), or when the linearization is reserved.
	 * The reading of a non-linear sensor ({@link #isNonLinear()}) converts with the factors of
	 * {@link #calcFormula(int, SensorReadingFactors)} only. {@link #calcFormula(int)} does not depend on this test.
	 *
	 * @return whether the reading byte gives a meaningful value
	 */
	public boolean hasAnalogReading() {
		return getEventReadingType() == THRESHOLD_EVENT_READING_TYPE
				&& ((TypeConverter.byteToInt(sensorUnits1) & 0xc0) >> 6) != NO_ANALOG_READING
				&& (linearization <= LAST_LINEARIZABLE || isNonLinear());
	}

	/**
	 * Tells whether the sensor is non-linear (linearization 70h-7Fh, Table 43-1): the factors of the record hold at the
	 * nominal reading only, and the factors of any other reading come from the Get Sensor Reading Factors command
	 * (IPMI 2.0 section 36.2).
	 *
	 * @return whether the sensor is non-linear
	 */
	public boolean isNonLinear() {
		return linearization >= FIRST_NON_LINEAR && linearization <= LAST_NON_LINEAR;
	}

	/**
	 * Converts to units-based value using the 'y=Mx+B' formula. 1's or 2's
	 * complement signed or unsigned per flag bits in Sensor Units 1.
	 *
	 * @param value
	 *        - Value to be converted. Length of 8 is assumed.
	 * @return converted value, {@link Double#NaN} when the sensor has no analog reading
	 *         ({@link #hasAnalogReading()}) or is non-linear ({@link #isNonLinear()})
	 */
	public double calcFormula(int value) {
		return calcFormula(value, 8, sensorUnits1);
	}

	/**
	 * Converts a reading with the factors the BMC returned for it in reply to Get Sensor Reading Factors, as a
	 * non-linear sensor needs ({@link #isNonLinear()}). The linearization function of the record applies on top of the
	 * factors, except for a non-linear sensor, whose factors already hold for the reading (IPMI 2.0 section 36.2).
	 *
	 * @param value the reading byte, 0 to 255
	 * @param readingFactors the factors the BMC returned for that reading byte
	 * @return converted value, {@link Double#NaN} when the sensor has no analog reading
	 */
	public double calcFormula(int value, SensorReadingFactors readingFactors) {
		double result = convert(value, 8, sensorUnits1, readingFactors);
		return isNonLinear() ? result : linearize(result);
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
	 */
	protected double calcFormula(int value, int length, byte units1) {
		return linearize(convert(value, length, units1, factors));
	}

	private static double convert(int value, int length, byte units1, SensorReadingFactors readingFactors) {
		int dataFormat = (TypeConverter.byteToInt(units1) & 0xc0) >> 6;

		switch (dataFormat) {
		case 0: // unsigned
			return readingFactors.convert(value);
		case 1: // 1's complement
			return readingFactors.convert(TypeConverter.decode1sComplement(value, length - 1));
		case 2: // 2's complement
			return readingFactors.convert(TypeConverter.decode2sComplement(value, length - 1));
		default: // no analog reading: the byte is not a value to convert
			return Double.NaN;
		}
	}

	private double linearize(double result) {
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
			// 70h-7Fh are non-linear: the factors of the record hold at the nominal reading only and the conversion
			// needs those of Get Sensor Reading Factors (calcFormula(int, SensorReadingFactors)); the rest is reserved
			return Double.NaN;
		}
	}

	/**
	 * Returns the resolution of a linear sensor: the difference in units between two successive raw readings,
	 * {@code |M x 10^R|} (IPMI 2.0 section 36.4.2). The resolution of a linearizable or non-linear sensor varies with
	 * the reading: section 36.4a derives it from the neighboring readings, {@code calcFormula(x + 1) - calcFormula(x)}
	 * (with the factors of each reading for a non-linear sensor, {@link #calcFormula(int, SensorReadingFactors)}).
	 *
	 * @return the resolution, {@link Double#NaN} when the sensor is not linear
	 */
	public double getSensorResolution() {
		return linearization == 0 ? Math.abs(factors.getM() * Math.pow(10, factors.getRExp())) : Double.NaN;
	}
}
