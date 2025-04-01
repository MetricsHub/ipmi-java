package org.metricshub.ipmi.core.coding.commands;

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

import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

/**
 * A wrapper for IPMI command.
 * 
 * Parameterless constructors in classes derived from IpmiCommandCoder are meant
 * to be used for decoding. To avoid omitting setting an important parameter
 * when encoding message use parametered constructors rather than the
 * parameterless ones.
 * 
 */
public abstract class IpmiCommandCoder extends PayloadCoder {

    public IpmiCommandCoder() {

    }

    public IpmiCommandCoder(IpmiVersion version, CipherSuite cipherSuite,
                            AuthenticationType authenticationType) {
        super(version, cipherSuite, authenticationType);
    }

    @Override
    public PayloadType getSupportedPayloadType() {
        return PayloadType.Ipmi;
    }

    /**
     * Checks if given message contains response command specific for this
     * class.
     *
     * @param message {@link IpmiMessage} wrapping the IPMI message
     * @return True if message contains response command specific for this
     *         class, false otherwise.
     */
    public boolean isCommandResponse(IpmiMessage message) {
        if (message.getPayload() instanceof IpmiPayload) {
            if (message.getPayload() instanceof IpmiLanResponse) {
                return ((IpmiLanResponse) message.getPayload()).getCommand() == getCommandCode();
            } else  {
                return message.getPayload() instanceof PlainMessage;
            }
        } else {
            return false;
        }
    }

    /**
     * Retrieves command code specific for command represented by this class
     *
     * @return command code
     */
    public abstract byte getCommandCode();

    /**
     * Retrieves network function specific for command represented by this
     * class.
     *
     * @return network function
     * @see NetworkFunction
     */
    public abstract NetworkFunction getNetworkFunction();

    /**
     * Used in several derived classes - converts {@link PrivilegeLevel} to
     * byte.
     *
     * @param privilegeLevel
     * @return privilegeLevel encoded as a byte due to {@link CommandsConstants}
     */
    protected byte encodePrivilegeLevel(PrivilegeLevel privilegeLevel) {
        switch (privilegeLevel) {
        case MaximumAvailable:
            return CommandsConstants.AL_HIGHEST_AVAILABLE;
        case Callback:
            return CommandsConstants.AL_CALLBACK;
        case User:
            return CommandsConstants.AL_USER;
        case Operator:
            return CommandsConstants.AL_OPERATOR;
        case Administrator:
            return CommandsConstants.AL_ADMINISTRATOR;
        default:
            throw new IllegalArgumentException("Invalid privilege level");
        }
    }
}
