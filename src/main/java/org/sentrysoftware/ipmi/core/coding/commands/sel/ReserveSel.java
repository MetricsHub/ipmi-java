package org.sentrysoftware.ipmi.core.coding.commands.sel;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, Sentry Software
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

import org.sentrysoftware.ipmi.core.coding.commands.CommandCodes;
import org.sentrysoftware.ipmi.core.coding.commands.IpmiCommandCoder;
import org.sentrysoftware.ipmi.core.coding.commands.IpmiVersion;
import org.sentrysoftware.ipmi.core.coding.commands.ResponseData;
import org.sentrysoftware.ipmi.core.coding.payload.CompletionCode;
import org.sentrysoftware.ipmi.core.coding.payload.IpmiPayload;
import org.sentrysoftware.ipmi.core.coding.payload.lan.IPMIException;
import org.sentrysoftware.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.sentrysoftware.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.sentrysoftware.ipmi.core.coding.payload.lan.NetworkFunction;
import org.sentrysoftware.ipmi.core.coding.protocol.AuthenticationType;
import org.sentrysoftware.ipmi.core.coding.protocol.IpmiMessage;
import org.sentrysoftware.ipmi.core.coding.security.CipherSuite;
import org.sentrysoftware.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Wrapper for Reserve SEL command.
 */
public class ReserveSel extends IpmiCommandCoder {

    /**
     * Initiates ReserveSel for both encoding and decoding.
     *
     * @param version
     *            - IPMI version of the command.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     */
    public ReserveSel(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType) {
        super(version, cipherSuite, authenticationType);
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.RESERVE_SEL;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.StorageRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber)
            throws NoSuchAlgorithmException, InvalidKeyException {
        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null,
                TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message)
            throws IPMIException,
            NoSuchAlgorithmException, InvalidKeyException {

        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException(
                    "This is not a response for Reserve SEL command");
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

        if (raw == null || raw.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid response payload length: " + (raw != null ? raw.length : "null"));
        }

        ReserveSelResponseData responseData = new ReserveSelResponseData();

        byte[] buffer = new byte[4];

        buffer[0] = raw[0];
        buffer[1] = raw[1];
        buffer[2] = 0;
        buffer[3] = 0;

        responseData.setReservationId(TypeConverter
                .littleEndianByteArrayToInt(buffer));

        return responseData;
    }

}
