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
import org.metricshub.ipmi.core.common.TypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * A wrapper class for Deactivate Payload Command.
 */
public class DeactivatePayload extends IpmiCommandCoder {

    private static final Logger logger = LoggerFactory.getLogger(DeactivatePayload.class);

    private static final int REQUEST_DATA_LENGTH = 6;

    /**
     * Payload type to be deactivated by this command.
     */
    private final PayloadType payloadType;

    /**
     * Number of payload instance to use when activating.
     */
    private final int payloadInstance;

    public DeactivatePayload(CipherSuite cipherSuite, PayloadType payloadType, int payloadInstance) {
        super(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus);
        this.payloadType = payloadType;
        this.payloadInstance = payloadInstance;
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.DEACTIVATE_PAYLOAD;
    }

    /**
     * Creates new instance of the {@link DeactivatePayload} command for deactivating given {@link PayloadType}.
     *
     * @param payloadType
     *          payload to be deactivated.
     */
    public DeactivatePayload(PayloadType payloadType, int payloadInstance) {
        this.payloadType = payloadType;
        this.payloadInstance = payloadInstance;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.ApplicationRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
        //We put just 2 bytes into the 6-bytes array, as specification for this command says to leave the rest 4 bytes as zeros
        byte[] message = MessageComposer.get(REQUEST_DATA_LENGTH)
                .appendField(TypeConverter.intToByte(payloadType.getCode()))
                .appendField(TypeConverter.intToByte(payloadInstance))
                .getMessage();

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), message, TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException("This is not a response for Deactivate Payload command");
        }

        if (!(message.getPayload() instanceof IpmiLanResponse)) {
            throw new IllegalArgumentException("Invalid response payload");
        }

        CompletionCode completionCode = ((IpmiLanResponse) message.getPayload()).getCompletionCode();

        if (completionCode != CompletionCode.Ok) {
            DeactivatePayloadCompletionCode specificCompletionCode = DeactivatePayloadCompletionCode.parseInt(completionCode.getCode());

            if (specificCompletionCode == DeactivatePayloadCompletionCode.PAYLOAD_ALREADY_DEACTIVATED) {
                logger.warn(specificCompletionCode.getMessage());
            } else {
                throw new IPMIException(((IpmiLanResponse) message.getPayload()).getCompletionCode());
            }
        }

        return new DeactivatePayloadResponseData();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeactivatePayload that = (DeactivatePayload) o;

        if (payloadInstance != that.payloadInstance) {
            return false;
        }

        if (payloadType != that.payloadType) {
            return false;
        }

        return getCipherSuite().equals(that.getCipherSuite());
    }

    @Override
    public int hashCode() {
        int result = payloadInstance;
        result = 31 * result + payloadType.getCode();
        result = 31 * result + (getCipherSuite() == null ? 0 : getCipherSuite().hashCode());
        return result;
    }
}
