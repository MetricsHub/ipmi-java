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

import org.metricshub.ipmi.core.coding.commands.session.Rakp1ResponseData;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.states.Rakp1Complete;
import org.metricshub.ipmi.core.sm.states.Rakp3Waiting;

/**
 * Performs transition from {@link Rakp1Complete} to {@link Rakp3Waiting}.
 * 
 * @see StateMachine
 */
public class Rakp2Ack extends StateMachineEvent {
    private byte statusCode;
    private CipherSuite cipherSuite;
    private int sequenceNumber;
    private int managedSystemSessionId;
    private Rakp1ResponseData rakp1ResponseData;

    /**
     * Prepares {@link Rakp2Ack}.
     *
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     *            Only authentication algorithm is used at this point of
     *            creating a session.
     * @param statusCode
     *            - Status of the previous message.
     * @param sequenceNumber
     *
     * @param managedSystemSessionId
     *            - The Managed System's Session ID for this session. Must be as
     *            returned by the Managed System in the Open Session Response
     *            message.
     * @param rakp1ResponseData
     *            - RAKP Message 2 received earlier in the authentification
     *            process
     */
    public Rakp2Ack(CipherSuite cipherSuite, int sequenceNumber,
            byte statusCode, int managedSystemSessionId,
            Rakp1ResponseData rakp1ResponseData) {
        this.statusCode = statusCode;
        this.cipherSuite = cipherSuite;
        this.sequenceNumber = sequenceNumber;
        this.managedSystemSessionId = managedSystemSessionId;
        this.rakp1ResponseData = rakp1ResponseData;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public CipherSuite getCipherSuite() {
        return cipherSuite;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getManagedSystemSessionId() {
        return managedSystemSessionId;
    }

    public Rakp1ResponseData getRakp1ResponseData() {
        return rakp1ResponseData;
    }
}
