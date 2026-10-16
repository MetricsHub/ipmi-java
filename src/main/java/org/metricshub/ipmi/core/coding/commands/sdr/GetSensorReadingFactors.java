package org.metricshub.ipmi.core.coding.commands.sdr;

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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorReadingFactors;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Get Sensor Reading Factors (IPMI 2.0 section 35.5): the factors that convert a given reading of a non-linear sensor.
 */
public class GetSensorReadingFactors extends IpmiCommandCoder<GetSensorReadingFactorsResponseData> {

	/** Length of the reply after the completion code: the next reading and the 6 bytes of the factors. */
	private static final int RESPONSE_LENGTH = 7;

	private final byte sensorNumber;

	private final byte reading;

	private final byte lun;

	/**
	 * Creates the command for a sensor at LUN 0 of its owner.
	 *
	 * @param sensorNumber the number of the sensor
	 * @param reading the reading byte to get the factors of, as Get Sensor Reading returned it
	 */
	public GetSensorReadingFactors(int sensorNumber, int reading) {
		this(sensorNumber, reading, 0);
	}

	/**
	 * Creates the command.
	 *
	 * @param sensorNumber the number of the sensor
	 * @param reading the reading byte to get the factors of, as Get Sensor Reading returned it
	 * @param lun the LUN of the sensor owner, from the SDR record of the sensor, 0 to 3
	 * @throws IllegalArgumentException when the LUN is not 0 to 3
	 */
	public GetSensorReadingFactors(int sensorNumber, int reading, int lun) {
		this.sensorNumber = TypeConverter.intToByte(sensorNumber);
		this.reading = TypeConverter.intToByte(reading);
		this.lun = checkLun(lun);
	}

	@Override
	public byte getCommandCode() {
		return 0x23;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.SensorRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
		return new IpmiLanRequest(
				getNetworkFunction(),
				getCommandCode(),
				new byte[]
				{ sensorNumber, reading },
				TypeConverter.intToByte(sequenceNumber),
				lun);
	}

	@Override
	public GetSensorReadingFactorsResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {
		byte[] raw = validateResponse(message);
		if (raw.length < RESPONSE_LENGTH) {
			throw new IllegalArgumentException("Invalid data length");
		}
		return new GetSensorReadingFactorsResponseData(
				TypeConverter.byteToInt(raw[0]),
				new SensorReadingFactors(raw, 1));
	}
}
