package org.metricshub.ipmi.core.coding.commands.payload;

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

import org.metricshub.ipmi.core.coding.commands.CommandCodes;
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
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.MessageComposer;
import org.metricshub.ipmi.core.common.MessageReader;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Wrapper for Get Payload Activation Status Command.
 */
public class GetPayloadActivationStatus extends IpmiCommandCoder {

    private static final int REQUEST_DATA_LENGTH = 1;
    private static final int INSTANCE_CAPACITY_FIELD_LENGTH = 1;
    private static final int AVAILABLE_INSTANCES_FIELD_LENGTH = 2;

    private final PayloadType payloadType;

    public GetPayloadActivationStatus(CipherSuite cipherSuite, PayloadType payloadType) {
        super(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus);
        this.payloadType = payloadType;
    }

    public GetPayloadActivationStatus(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.GET_PAYLOAD_ACTIVATION_STATUS;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.ApplicationRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] message = MessageComposer.get(REQUEST_DATA_LENGTH)
                .appendField(TypeConverter.intToByte(payloadType.getCode()))
                .getMessage();

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), message, TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException("This is not a response for Get Payload Activation Status");
        }

        if (!(message.getPayload() instanceof IpmiLanResponse)) {
            throw new IllegalArgumentException("Invalid response payload");
        }

        if (((IpmiLanResponse) message.getPayload()).getCompletionCode() != CompletionCode.Ok) {
            throw new IPMIException(((IpmiLanResponse) message.getPayload()).getCompletionCode());
        }

        MessageReader messageReader = new MessageReader(message.getPayload().getData());

        GetPayloadActivationStatusResponseData responseData = new GetPayloadActivationStatusResponseData();

        byte instaceCapacity = messageReader.readNextField(INSTANCE_CAPACITY_FIELD_LENGTH)[0];
        responseData.setInstanceCapacity(instaceCapacity);

        byte[] availableInstancesData = messageReader.readNextField(AVAILABLE_INSTANCES_FIELD_LENGTH);
        responseData.setAvailableInstances(availableInstancesData);

        return responseData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GetPayloadActivationStatus that = (GetPayloadActivationStatus) o;

        if (payloadType != that.payloadType) {
            return false;
        }

        return getCipherSuite().equals(that.getCipherSuite());
    }

    @Override
    public int hashCode() {
        int result = payloadType.getCode();
        result = 31 * result + (getCipherSuite() != null ? getCipherSuite().hashCode() : 0);
        return result;
    }
}
