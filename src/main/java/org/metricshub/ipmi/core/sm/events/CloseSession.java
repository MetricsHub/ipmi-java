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

import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.states.Authcap;
import org.metricshub.ipmi.core.sm.states.SessionValid;
import org.metricshub.ipmi.core.sm.states.State;

/**
 * {@link StateMachineEvent} that will make {@link StateMachine} in the
 * {@link SessionValid} {@link State} to send
 * {@link org.metricshub.ipmi.core.coding.commands.session.CloseSession} and
 * transit to {@link Authcap} {@link State} the session.
 */
public class CloseSession extends StateMachineEvent {
    private int sessionId;
    private int messageSequenceNumber;
    private int sessionSequenceNumber;

    /**
     * Prepares {@link CloseSession}
     *
     * @param sessionId
     *            - managed system session ID
     *
     * @param messageSequenceNumber
     *            - generated sequence number for the message to send
     */
    public CloseSession(int sessionId, int messageSequenceNumber, int sessionSequenceNumber) {
        this.messageSequenceNumber = messageSequenceNumber;
        this.sessionSequenceNumber = sessionSequenceNumber;
        this.sessionId = sessionId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public int getMessageSequenceNumber() {
        return messageSequenceNumber;
    }

    public int getSessionSequenceNumber() {
        return sessionSequenceNumber;
    }
}
