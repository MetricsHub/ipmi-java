package org.metricshub.ipmi.core.coding.commands.sdr;

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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Wrapper for Get Sensor Reading request.
 */
public class GetSensorReading extends IpmiCommandCoder {

    private byte sensorId;

    /**
     * Initiates class for both encoding and decoding.
     *
     * @param version
     *            - IPMI version of the command.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     * @param sensorId
     *            - ID of the sensor which reading is to be retrieved
     */
    public GetSensorReading(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType, int sensorId) {
        super(version, cipherSuite, authenticationType);
        this.sensorId = TypeConverter.intToByte(sensorId);
    }

    @Override
    public byte getCommandCode() {
        return 0x2d;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.SensorRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] payloadData = new byte[] { sensorId };
        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(),
                payloadData, TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {

        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException(
                    "This is not a response for Get Sensor Reading command");
        }
        if (!(message.getPayload() instanceof IpmiLanResponse)) {
            throw new IllegalArgumentException("Invalid response payload");
        }
        if (((IpmiLanResponse) message.getPayload()).getCompletionCode() != CompletionCode.Ok) {
            throw new IPMIException(
                    ((IpmiLanResponse) message.getPayload())
                            .getCompletionCode());
        }

        byte[] raw = message.getPayload().getIpmiCommandData();

        if (raw.length < 2) {
            throw new IllegalStateException("Invalid data length");
        }

        GetSensorReadingResponseData responseData = new GetSensorReadingResponseData();

        responseData.setRaw(raw);

        responseData.setSensorReading(raw[0]);

        responseData
                .setSensorStateValid((TypeConverter.byteToInt(raw[1]) & 0x20) == 0);

        if (raw.length >= 3) {
            responseData.setSensorState(SensorState.parseInt((TypeConverter
                    .byteToInt(raw[2])) & 0x3f));

            boolean[] states = null;

            if (raw.length > 3) {
                states = new boolean[16];
            } else {
                states = new boolean[8];
            }

            for (int i = 0; i < 8; ++i) {
                states[i] = (TypeConverter.byteToInt(raw[2]) & (0x1 << i)) != 0;
            }

            if (raw.length > 3) {
                for (int i = 0; i < 7; ++i) {
                    states[i + 8] = (TypeConverter.byteToInt(raw[3]) & (0x1 << i)) != 0;
                }
                states[15] = false;
            }

            responseData.setStatesAsserted(states);

        }

        return responseData;
    }

}
