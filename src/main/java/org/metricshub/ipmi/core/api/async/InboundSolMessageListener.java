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

	/**
	 * Room for 64 KiB of console output between two reads: the console suspends the BMC when it is full, and some BMCs
	 * (Supermicro) take the Suspend NACK for an ACK and drop the characters.
	 */
	static final int BUFFER_CAPACITY = 64 * 1024;

	/**
	 * The characters received; its monitor also guards the fields below, shared by the receiving thread and the
	 * readers.
	 */
	private final ByteBuffer buffer = new ByteBuffer(BUFFER_CAPACITY);
	private final IpmiConnector connector;
	private final ConnectionHandle connectionHandle;
	private final List<SolEventListener> eventListeners;

	/**
	 * The pause between two Resume ACKs of a packet that the BMC does not send again: the Resume ACK may be lost.
	 */
	private static final long RESUME_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

	/**
	 * The packet answered with a Suspend NACK for lack of room, until the BMC sends it again and it is accepted: the
	 * reads that make room for it resume the BMC.
	 */
	private SolInboundMessage suspendedPacket;
	private long lastResumeNanos;

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
					suspendedPacket = null;
				}
			} else if (sequenceNumber == 0) {
				LOGGER.warn("Dropping {} characters received in a SOL packet without sequence number", characters.length);
			}
			// The readers waiting for characters
			buffer.notifyAll();
		}

		if (sequenceNumber != 0) {
			// Every packet with a sequence number is acknowledged, with the count of its characters, even when it
			// carries none (IPMI 2.0 section 15.11)
			acknowledge(packet, accepted ? SolAckState.ACK : SolAckState.NACK, accepted ? characters.length : 0);
		}

		if (!accepted && sequenceNumber != 0) {
			// Recorded once the Suspend NACK is sent, so that no Resume ACK can overtake it; a read may have made room
			// meanwhile, and no other will come for a reader waiting for more characters
			SolInboundMessage resume;
			synchronized (buffer) {
				suspendedPacket = packet;
				lastResumeNanos = System.nanoTime() - RESUME_INTERVAL_NANOS;
				resume = takeResume();
				buffer.notifyAll();
			}
			if (resume != null) {
				acknowledge(resume, SolAckState.ACK, 0);
			}
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
		SolInboundMessage resume;
		synchronized (buffer) {
			result = buffer.read(numberOfBytes);
			resume = takeResume();
		}
		if (resume != null) {
			acknowledge(resume, SolAckState.ACK, 0);
		}
		return result;
	}

	/**
	 * Returns the suspended packet when there is room for it and no Resume ACK was sent for it lately: the BMC sends it
	 * again at once when it gets the Resume ACK (IPMI 2.0 table 15-3), and is sent another one if that one is lost.
	 * Called with the buffer locked.
	 *
	 * @return the packet to resume, or null
	 */
	private SolInboundMessage takeResume() {
		if (suspendedPacket == null
				|| suspendedPacket.getData().length > buffer.remainingSpace()
				|| System.nanoTime() - lastResumeNanos < RESUME_INTERVAL_NANOS) {
			return null;
		}
		// ponytail: a Resume ACK every 100 ms until the packet comes back, whatever the round trip to the BMC
		lastResumeNanos = System.nanoTime();
		return suspendedPacket;
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
		while (true) {
			SolInboundMessage resume;
			synchronized (buffer) {
				long remaining = deadline - System.nanoTime();
				if (buffer.size() >= numberOfBytes || remaining <= 0 || isFull()) {
					break;
				}
				// While waiting for a suspended packet to come back, its Resume ACK is sent again from time to time
				resume = takeResume();
				if (resume == null) {
					try {
						TimeUnit.NANOSECONDS.timedWait(buffer, Math.min(remaining, RESUME_INTERVAL_NANOS));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
					continue;
				}
			}
			acknowledge(resume, SolAckState.ACK, 0);
		}
		return readBytes(numberOfBytes);
	}

	/**
	 * Tells whether no character can arrive before a read: the buffer is full, or a packet is suspended for lack of
	 * room. Called with the buffer locked.
	 */
	private boolean isFull() {
		return buffer.remainingSpace() == 0
				|| suspendedPacket != null && suspendedPacket.getData().length > buffer.remainingSpace();
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
