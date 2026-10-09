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

	/**
	 * How far below the highest session sequence number received a message may be (IPMI 2.0 section 6.12.14).
	 */
	private static final int SEQUENCE_WINDOW = 16;

	/**
	 * The highest session sequence number received so far.
	 */
	private int highestReceivedSequenceNumber;

	/**
	 * Bit i is set when {@code highestReceivedSequenceNumber - i} was received.
	 */
	private int receivedSequenceNumbers = 1;

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
	 * Checks if received message is inside "sliding window range" and was not received yet, and if so,
	 * further processes the message in a implementation-specific way.
	 *
	 * @param message the message received from the BMC
	 */
	public void handleIncomingMessage(Ipmiv20Message message) {

		int seq = message.getSessionSequenceNumber();

		if (seq != 0 && !acceptSequenceNumber(seq)) {
			LOGGER.debug("Dropping message {}", seq);
			return;
		}

		handleIncomingMessageInternal(message);
	}

	/**
	 * Sliding window over the session sequence numbers received (IPMI 2.0 sections 6.12.13 and 6.12.14): drops a
	 * message received already (a replay or a duplicate) or more than {@link #SEQUENCE_WINDOW} below the highest number
	 * received. A number above it is always accepted: the message was authenticated before reaching this point, when
	 * the session has integrity, so its number cannot be forged, and a gap left by lost messages or by the messages of
	 * the other payload type (IPMI and SOL share the numbers) must not lock the session out.
	 *
	 * @param seq the session sequence number of a message received
	 * @return whether the message is accepted
	 */
	private synchronized boolean acceptSequenceNumber(int seq) {
		// The difference wraps around like the 32-bit sequence numbers
		int ahead = seq - highestReceivedSequenceNumber;
		if (ahead > 0) {
			receivedSequenceNumbers = ahead < Integer.SIZE ? (receivedSequenceNumbers << ahead) | 1 : 1;
			highestReceivedSequenceNumber = seq;
			return true;
		}
		if (ahead < -SEQUENCE_WINDOW || (receivedSequenceNumbers & (1 << -ahead)) != 0) {
			return false;
		}
		receivedSequenceNumbers |= 1 << -ahead;
		return true;
	}

	/**
	 * Forgets the session sequence numbers received: the BMC numbers the messages of each new session from 1 again
	 * (IPMI 2.0 section 6.12.13).
	 */
	synchronized void resetSequenceWindow() {
		highestReceivedSequenceNumber = 0;
		receivedSequenceNumbers = 1;
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
