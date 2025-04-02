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
 * An abstract wrapper class for Activate Payload Command. Every {@link PayloadType} should have it's own concrete implementation.
 */
public abstract class ActivatePayload extends IpmiCommandCoder {

    /**
     * Length (in bytes) of request data payload.
     */
    private static final int REQUEST_DATA_LENGTH = 6;

    /**
     * Length (in bytes) of auxilary data field in request/response payload.
     */
    private static final int AUXILARY_DATA_LENGTH = 4;

    /**
     * Length (in bytes) of inbound payload size field in response payload.
     */
    private static final int INBOUND_PAYLOAD_SIZE_LENGTH = 2;

    /**
     * Length (in bytes) of outbound data field response payload.
     */
    private static final int OUTBOUND_PAYLOAD_SIZE_LENGTH = 2;

    /**
     * Length (in bytes) of udp port number field in response payload.
     */
    private static final int PAYLOAD_UDP_PORT_NUMBER_DATA_LENGTH = 2;

    /**
     * Length (in bytes) of VLAN number field in response payload.
     */
    private static final int PAYLOAD_VLAN_NUMBER_DATA_LENGTH = 2;

    /**
     * Number of payload instance to use when activating.
     */
    private final int payloadInstance;

    /**
     * @return type of the payload activated by this command.
     */
    public abstract PayloadType getPayloadType();

    protected ActivatePayload(IpmiVersion ipmiVersion, CipherSuite cipherSuite, AuthenticationType authenticationType, int payloadInstance) {
        super(ipmiVersion, cipherSuite, authenticationType);

        this.payloadInstance = payloadInstance;
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.ACTIVATE_PAYLOAD;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.ApplicationRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
       byte[] message = MessageComposer.get(REQUEST_DATA_LENGTH)
               .appendField(TypeConverter.intToByte(getPayloadType().getCode()))
               .appendField(TypeConverter.intToByte(payloadInstance))
               .appendField(prepareAuxilaryRequestData())
               .getMessage();

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), message, TypeConverter.intToByte(sequenceNumber));
    }

    /**
     * Prepares Auxilary Data field for the request payload.
     *
     * @return Auxilary Data bytes.
     */
    protected abstract byte[] prepareAuxilaryRequestData();

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException("This is not a response for Activate Payload command");
        }

        if (!(message.getPayload() instanceof IpmiLanResponse)) {
            throw new IllegalArgumentException("Invalid response payload");
        }

        if (((IpmiLanResponse) message.getPayload()).getCompletionCode() != CompletionCode.Ok) {
            throw new IPMIException(((IpmiLanResponse) message.getPayload()).getCompletionCode());
        }

        MessageReader messageReader = new MessageReader(message.getPayload().getData());

        ActivatePayloadResponseData responseData = createEmptyResponse();

        responseData.setAuxilaryInformationData(messageReader.readNextField(AUXILARY_DATA_LENGTH));
        responseData.setInboundPayloadSize(messageReader.readNextField(INBOUND_PAYLOAD_SIZE_LENGTH));
        responseData.setOutboundPayloadSize(messageReader.readNextField(OUTBOUND_PAYLOAD_SIZE_LENGTH));
        responseData.setPayloadUdpPortNumber(messageReader.readNextField(PAYLOAD_UDP_PORT_NUMBER_DATA_LENGTH));
        responseData.setPayloadVlanNumber(messageReader.readNextField(PAYLOAD_VLAN_NUMBER_DATA_LENGTH));

        return responseData;
    }

    /**
     * Creates empty {@link ResponseData} object.
     *
     * @return created {@link ActivatePayloadResponseData} instance.
     */
    protected abstract ActivatePayloadResponseData createEmptyResponse();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActivatePayload that = (ActivatePayload) o;

        if (payloadInstance != that.payloadInstance) {
            return false;
        }

        if (getPayloadType() != that.getPayloadType()) {
            return false;
        }

        return getCipherSuite().equals(that.getCipherSuite());
    }

    @Override
    public int hashCode() {
        int result = payloadInstance;
        result = 31 * result + getPayloadType().getCode();
        result = 31 * result + (getCipherSuite() != null ? getCipherSuite().hashCode() : 0);
        return result;
    }
}
