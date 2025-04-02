package org.metricshub.ipmi.core.sm.events;

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

import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilitiesResponseData;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.states.OpenSessionComplete;
import org.metricshub.ipmi.core.sm.states.Rakp1Waiting;

/**
 * Performs transition from {@link OpenSessionComplete} to {@link Rakp1Waiting}.
 * 
 * @see StateMachine
 */
public class OpenSessionAck extends Default {

    private int managedSystemSessionId;
    private String username;
    private String password;
    private byte[] bmcKey;

    /**
     * Provides data required to send RAKP Message 1.
     *
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param sequenceNumber
     *            - a sequence number for the message
     * @param managedSystemSessionId
     *            - The Managed System's Session ID for this session. Must be as
     *            returned by the Managed System in the Open Session Response
     *            message.
     * @param privilegeLevel
     *            - Requested Maximum {@link PrivilegeLevel}
     * @param username
     *            - ASCII character Name that the user at the Remote Console
     *            wishes to assume for this session. It's length cannot exceed
     *            16.
     * @param password
     *            - password matching username
     * @param bmcKey
     *            - BMC specific key. Should be null if Get Channel
     *            Authentication Capabilities Response indicated that Kg is
     *            disabled which means that 'one-key' logins are being used (
     *            {@link GetChannelAuthenticationCapabilitiesResponseData#isKgEnabled()}
     *            == false)
     */
    public OpenSessionAck(CipherSuite cipherSuite,
            PrivilegeLevel privilegeLevel, int sequenceNumber,
            int managedSystemSessionId, String username, String password,
            byte[] bmcKey) {
        super(cipherSuite, sequenceNumber, privilegeLevel);
        this.managedSystemSessionId = managedSystemSessionId;
        this.username = username;
        this.password = password;
        this.bmcKey = bmcKey;
    }

    public int getManagedSystemSessionId() {
        return managedSystemSessionId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public byte[] getBmcKey() {
        return bmcKey;
    }
}
