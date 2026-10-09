package org.metricshub.ipmi.core.connection;

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
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.connection.queue.MessageQueue;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.events.Sendv20Message;
import org.metricshub.ipmi.core.sm.states.SessionValid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class used for handling outgoing and incoming messages for a {@link Connection}.
 */
public abstract class MessageHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(MessageHandler.class);

	private int lastReceivedSequenceNumber = 0;
	private final MessageQueue messageQueue;
	private final Connection connection;

	/**
	 * Returns the queue of messages awaiting a response.
	 *
	 * @return the {@link MessageQueue} of this handler
	 */
	protected MessageQueue getMessageQueue() {
		return messageQueue;
	}

	/**
	 * Returns the connection this handler serves.
	 *
	 * @return the {@link Connection} of this handler
	 */
	protected Connection getConnection() {
		return connection;
	}

	/**
	 * Returns the highest sequence number received so far (sliding window state).
	 *
	 * @return the last received sequence number
	 */
	protected int getLastReceivedSequenceNumber() {
		return lastReceivedSequenceNumber;
	}

	/**
	 * Sets the highest sequence number received so far (sliding window state).
	 *
	 * @param lastReceivedSequenceNumber the last received sequence number
	 */
	protected void setLastReceivedSequenceNumber(int lastReceivedSequenceNumber) {
		this.lastReceivedSequenceNumber = lastReceivedSequenceNumber;
	}

	public MessageHandler(Connection connection, int timeout, int minSequenceNumber, int maxSequenceNumber) {
		this.messageQueue = new MessageQueue(connection, timeout, minSequenceNumber, maxSequenceNumber);
		this.connection = connection;
	}

	/**
	 * Attempts to send message encoded by given {@link PayloadCoder} to the remote system.
	 *
	 * @param payloadCoder
	 *        instance of {@link PayloadCoder} that will produce payload for the message being sent.
	 * @param stateMachine
	 *        {@link StateMachine} for the currenr connection.
	 * @param sessionId
	 *        ID of the current session.
	 * @param isOneWay
	 *        flag indicating, if message is one way and we shouldn't await response,
	 *        or it isn't and needs response from remote system. A one-way IPMI message is queued all the same, so that
	 *        its tag stays reserved until its reply arrives or it times out, but its reply and its timeout are not
	 *        reported (see {@link #takeTag(PayloadCoder, boolean)}).
	 * @return sequence number of the sent message, or -1 when the queue is full
	 * @throws ConnectionException when could not send message due to some problems with connection
	 */
	public int sendMessage(PayloadCoder payloadCoder, StateMachine stateMachine, int sessionId, boolean isOneWay)
			throws ConnectionException {
		validateSessionState(stateMachine);

		int seq = takeTag(payloadCoder, isOneWay);
		if (seq > 0) {
			stateMachine
					.doTransition(new Sendv20Message(payloadCoder, sessionId, seq, connection.getNextSessionSequenceNumber()));
		}

		return seq;
	}

	/**
	 * Takes the tag of a message to send by queuing it: the tag stays reserved until its reply arrives or it times
	 * out.
	 *
	 * @param payloadCoder the message to send
	 * @param isOneWay true when nobody waits for the reply
	 * @return the tag of the message, or -1 when the queue is full
	 */
	protected int takeTag(PayloadCoder payloadCoder, boolean isOneWay) {
		return messageQueue.add(payloadCoder, isOneWay);
	}

	/**
	 * Attempts to retry sending message with given tag, assuming that this message exists in message queue.
	 *
	 * @param tag
	 *        tag of the message that we want to resend
	 * @param stateMachine
	 *        {@link StateMachine} for the currenr connection.
	 * @param sessionId
	 *        ID of the current session.
	 * @return sequence number of the retried message (should be the same as original message tag) or -1 if no message was
	 *         found in the queue.
	 * @throws ConnectionException when could not send message due to some problems with connection
	 */
	public int retryMessage(int tag, StateMachine stateMachine, int sessionId) throws ConnectionException {
		validateSessionState(stateMachine);

		PayloadCoder payloadCoder = messageQueue.getMessageFromQueue(tag);

		if (payloadCoder == null) {
			return -1;
		}

		stateMachine
				.doTransition(new Sendv20Message(payloadCoder, sessionId, tag, connection.getNextSessionSequenceNumber()));

		return tag;
	}

	private void validateSessionState(StateMachine stateMachine) throws ConnectionException {
		if (stateMachine.getCurrent().getClass() != SessionValid.class) {
			throw new ConnectionException(
					"Illegal connection state: " + stateMachine.getCurrent().getClass().getSimpleName());
		}
	}

	/**
	 * Checks if received message is inside "sliding window range", and if it is,
	 * further processes the message in a cimplementation-specific way.
	 *
	 * @param message
	 */
	public void handleIncomingMessage(Ipmiv20Message message) {

		int seq = message.getSessionSequenceNumber();

		if (seq != 0 && (seq > lastReceivedSequenceNumber + 15 || seq < lastReceivedSequenceNumber - 16)) {
			LOGGER.debug("Dropping message " + seq);
			return; // if the message's sequence number gets out of the sliding
			// window range we need to drop it
		}

		if (seq != 0) {
			lastReceivedSequenceNumber = (seq > lastReceivedSequenceNumber ? seq : lastReceivedSequenceNumber);
		}

		handleIncomingMessageInternal(message);
	}

	public void setTimeout(int timeout) {
		messageQueue.setTimeout(timeout);
	}

	public void tearDown() {
		messageQueue.tearDown();
	}

	/**
	 * Returns a sequence number for a message sent outside the queue (the Close Session request, the SOL ACK-only
	 * packets).
	 *
	 * @return a sequence number that no queued request holds
	 */
	public int getSequenceNumber() {
		return messageQueue.getSequenceNumber();
	}

	/**
	 * Abstract method for implementation-specific logic for handling incomming IPMI message.
	 *
	 * @param message
	 *        IPMI message received from BMC
	 */
	protected abstract void handleIncomingMessageInternal(Ipmiv20Message message);

}
