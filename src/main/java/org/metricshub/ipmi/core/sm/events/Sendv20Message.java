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

import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.states.SessionValid;
import org.metricshub.ipmi.core.sm.states.State;

/**
 * Performed in {@link SessionValid} {@link State} will cause {@link #message}
 * to be sent.
 * 
 * @see StateMachine
 */
public class Sendv20Message extends StateMachineEvent {
    private PayloadCoder message;
    private int sessionId;
    private int messageSequenceNumber;
    private int sessionSequenceNumber;

    /**
     * Prepares an event for {@link StateMachine} that will perform sending an
     * IPMI command in v2.0 format. Only possible in {@link SessionValid}
     * {@link State}
     *
     * @param payloadCoder
     *            - The payload to send.
     * @param sessionId
     *            - managed system session ID
     * @param messageSequenceNumber
     *            - generated sequence number for the message to send
     */
    public Sendv20Message(PayloadCoder payloadCoder, int sessionId,
                          int messageSequenceNumber, int sessionSequenceNumber) {
        message = payloadCoder;
        this.sessionId = sessionId;
        this.messageSequenceNumber = messageSequenceNumber;
        this.sessionSequenceNumber = sessionSequenceNumber;
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

    public PayloadCoder getPayloadCoder() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Sendv20Message that = (Sendv20Message) o;

        if (sessionId != that.sessionId) return false;
        if (messageSequenceNumber != that.messageSequenceNumber) return false;
        if (sessionSequenceNumber != that.sessionSequenceNumber) return false;

        return message != null ? message.equals(that.message) : that.message == null;
    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + sessionId;
        result = 31 * result + messageSequenceNumber;
        result = 31 * result + sessionSequenceNumber;
        return result;
    }
}
