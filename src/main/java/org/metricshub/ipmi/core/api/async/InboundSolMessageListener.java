package org.metricshub.ipmi.core.api.async;

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

import org.metricshub.ipmi.core.api.sol.SolEventListener;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.sol.SolAckState;
import org.metricshub.ipmi.core.coding.payload.sol.SolInboundMessage;
import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;
import org.metricshub.ipmi.core.coding.payload.sol.SolStatus;
import org.metricshub.ipmi.core.coding.sol.SolCoder;
import org.metricshub.ipmi.core.common.ByteBuffer;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for inbound SOL messages sent by remote system to this application: it buffers their characters and
 * acknowledges them (IPMI 2.0 sections 15.9 and 15.11).
 */
public class InboundSolMessageListener implements InboundMessageListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(InboundSolMessageListener.class);

	static final int BUFFER_CAPACITY = 2048;

	/**
	 * The characters received; its monitor also guards the fields below, shared by the receiving thread and the
	 * readers.
	 */
	private final ByteBuffer buffer = new ByteBuffer(BUFFER_CAPACITY);
	private final IpmiConnector connector;
	private final ConnectionHandle connectionHandle;
	private final List<SolEventListener> eventListeners;

	/**
	 * The packet answered with a Suspend NACK for lack of room, resumed once read: the BMC then sends it again.
	 */
	private SolInboundMessage suspendedPacket;

	/**
	 * The sequence number of the last packet accepted, and its number of characters: the BMC sends a packet again,
	 * with the same number and possibly more characters, when it misses the acknowledgement (IPMI 2.0 section 15.9).
	 */
	private int lastSequenceNumber;
	private int lastCharacterCount;

	/**
	 * The serial line states the console holds, set in every packet it sends.
	 */
	private volatile Set<SolOperation> lineOperations = Collections.emptySet();

	public InboundSolMessageListener(IpmiConnector connector, ConnectionHandle connectionHandle,
			List<SolEventListener> eventListeners) {
		this.connector = connector;
		this.connectionHandle = connectionHandle;
		this.eventListeners = eventListeners;
	}

	@Override
	public boolean isPayloadSupported(IpmiPayload payload) {
		return payload instanceof SolInboundMessage;
	}

	/**
	 * Takes the SOL packets of its own connection only: those of the other consoles of the connector are theirs to
	 * buffer and acknowledge.
	 */
	@Override
	public boolean isPayloadSupported(IpmiPayload payload, int handle) {
		return handle == connectionHandle.getHandle() && isPayloadSupported(payload);
	}

	/**
	 * Buffers the characters of a packet, only those it did not receive already when the BMC sends a packet again,
	 * acknowledges every packet with a sequence number (with a Suspend NACK when the characters do not fit), and
	 * reports the statuses of each packet once.
	 */
	@Override
	public void notify(IpmiPayload payload) {
		if (!isPayloadSupported(payload)) {
			return;
		}
		SolInboundMessage packet = (SolInboundMessage) payload;
		int sequenceNumber = packet.getSequenceNumber();
		byte[] characters = packet.getData();

		boolean retry;
		boolean accepted;
		synchronized (buffer) {
			retry = sequenceNumber != 0 && sequenceNumber == lastSequenceNumber;
			int received = retry ? Math.min(lastCharacterCount, characters.length) : 0;
			byte[] newCharacters = Arrays.copyOfRange(characters, received, characters.length);
			accepted = newCharacters.length <= buffer.remainingSpace();
			if (accepted) {
				buffer.write(newCharacters);
				if (sequenceNumber != 0) {
					lastSequenceNumber = sequenceNumber;
					lastCharacterCount = characters.length;
				}
			} else if (sequenceNumber != 0) {
				suspendedPacket = packet;
			} else {
				LOGGER.warn("Dropping {} characters received in a SOL packet without sequence number", characters.length);
			}
			// The readers waiting for characters: they arrived, or none will until a read makes room
			buffer.notifyAll();
		}

		if (sequenceNumber != 0) {
			// Every packet with a sequence number is acknowledged, with the count of its characters, even when it
			// carries none (IPMI 2.0 section 15.11)
			acknowledge(packet, accepted ? SolAckState.ACK : SolAckState.NACK, accepted ? characters.length : 0);
		}

		Set<SolStatus> statuses = packet.getStatusField().getStatuses();
		if (accepted && !retry && statuses != null && !statuses.isEmpty()) {
			for (SolEventListener listener : eventListeners) {
				listener.processRequestEvent(statuses);
			}
		}
	}

	/**
	 * Sends an ACK-only packet: an ACK of the given number of characters, a Suspend NACK (a NACK of 0 characters) or a
	 * Resume ACK (an ACK of 0 characters), with the line states the console holds (IPMI 2.0 table 15-3).
	 */
	private void acknowledge(SolInboundMessage packet, SolAckState ackState, int characterCount) {
		try {
			SolCoder acknowledgement = new SolCoder(
					packet.getSequenceNumber(),
					(byte) characterCount,
					ackState,
					lineOperations,
					connectionHandle.getCipherSuite());
			connector.sendOneWayMessage(connectionHandle, acknowledgement);
		} catch (Exception e) {
			LOGGER.error("Could not send " + ackState + " for packet " + packet.getSequenceNumber(), e);
		}
	}

	/**
	 * @return the serial line states (CTS, DCD/DSR, Ring) that the console holds in every packet it sends
	 */
	public Set<SolOperation> getLineOperations() {
		return lineOperations;
	}

	/**
	 * Sets the serial line states (CTS, DCD/DSR, Ring) that the console holds in every packet it sends: they are levels,
	 * which a packet without them releases (IPMI 2.0 table 15-2).
	 *
	 * @param lineOperations the line states to hold
	 */
	public void setLineOperations(Set<SolOperation> lineOperations) {
		Set<SolOperation> copy = EnumSet.noneOf(SolOperation.class);
		copy.addAll(lineOperations);
		this.lineOperations = Collections.unmodifiableSet(copy);
	}

	/**
	 * Attempts to read given number of bytes from this {@link ByteBuffer}.
	 * If buffer currently contains less bytes than requested, this method reads only available number of bytes.
	 * When the read makes room for the packet suspended for lack of it, the BMC is told to send it again.
	 *
	 * @param numberOfBytes
	 *        requested number of bytes to read
	 * @return actual bytes that could be read from this buffer.
	 */
	public byte[] readBytes(int numberOfBytes) {
		byte[] result;
		SolInboundMessage resumed = null;
		synchronized (buffer) {
			result = buffer.read(numberOfBytes);
			if (suspendedPacket != null && suspendedPacket.getData().length <= buffer.remainingSpace()) {
				resumed = suspendedPacket;
				suspendedPacket = null;
			}
		}
		if (resumed != null) {
			// A Resume ACK, sent once: the BMC sends the packet again at once
			acknowledge(resumed, SolAckState.ACK, 0);
		}
		return result;
	}

	/**
	 * Reads at most the given number of bytes, once they are available, the buffer is full (no other character
	 * arrives before a read), or the timeout expires.
	 *
	 * @param numberOfBytes
	 *        requested number of bytes to read
	 * @param timeout
	 *        the longest wait, in milliseconds
	 * @return the bytes read, possibly fewer than requested, or none
	 */
	public byte[] readBytes(int numberOfBytes, int timeout) {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
		synchronized (buffer) {
			long remaining = deadline - System.nanoTime();
			while (buffer.size() < numberOfBytes && suspendedPacket == null && remaining > 0) {
				try {
					TimeUnit.NANOSECONDS.timedWait(buffer, remaining);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				remaining = deadline - System.nanoTime();
			}
		}
		return readBytes(numberOfBytes);
	}

	/**
	 * Returns number of incoming bytes that are available to read from the buffer.
	 *
	 * @return number of available bytes to read
	 */
	public int getAvailableBytesCount() {
		return buffer.size();
	}
}
