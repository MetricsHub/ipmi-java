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
import org.metricshub.ipmi.core.coding.payload.sol.SolMessage;
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
	 * The packet answered with a Suspend NACK for lack of room, until a newer packet shows that the BMC resumed: the
	 * reads that make room for it resume the BMC.
	 */
	private SolInboundMessage suspendedPacket;
	private long lastResumeNanos;

	/**
	 * How many times the suspended packet is acknowledged again once all its characters are received: the BMC may still
	 * be suspended (its copy left before the Suspend NACK arrived, or the acknowledgement was lost), or may have nothing
	 * more to send.
	 */
	private static final int COMPLETION_ACKS = 10;
	private int completionAcks;

	/**
	 * How many packets back a copy of a packet is recognized: fewer than the 15 sequence numbers, which a new packet uses
	 * again.
	 */
	private static final int HISTORY = 7;

	/**
	 * The packets of the BMC seen lately, by sequence number: the most characters accepted of their copies, the statuses
	 * reported for them (as bits of the ordinals of {@link SolStatus}), and their rank among the packets seen, accepted
	 * or not. The BMC sends a packet again, with the same number and possibly more characters or statuses, when it
	 * misses the acknowledgement (IPMI 2.0 section 15.9), and a copy may arrive late, after newer packets, or shorter.
	 */
	private final byte[][] receivedCharacters = new byte[SolMessage.MAX_SEQUENCE_NUMBER + 1][];
	private final int[] reportedStatuses = new int[SolMessage.MAX_SEQUENCE_NUMBER + 1];
	private final long[] ranks = new long[SolMessage.MAX_SEQUENCE_NUMBER + 1];
	private long seenPackets;
	private int lastSequenceNumber;

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

		boolean accepted;
		int acceptedCount = 0;
		Set<SolStatus> statuses = EnumSet.noneOf(SolStatus.class);
		synchronized (buffer) {
			boolean copy = isCopy(packet);
			// Only a copy of the last packet brings characters: the BMC left an older one once its characters were
			// acknowledged, or dropped them (retries exhausted), and newer characters already followed them
			int known = !copy ?
					0 : sequenceNumber == lastSequenceNumber ?
							Math.min(receivedCharacters[sequenceNumber].length, characters.length) : characters.length;
			byte[] newCharacters = Arrays.copyOfRange(characters, known, characters.length);
			accepted = newCharacters.length <= buffer.remainingSpace();
			if (sequenceNumber != 0 && !copy) {
				// A new packet, seen from now on, even when its characters must wait for room
				ranks[sequenceNumber] = ++seenPackets;
				receivedCharacters[sequenceNumber] = new byte[0];
				reportedStatuses[sequenceNumber] = 0;
				lastSequenceNumber = sequenceNumber;
			}
			if (accepted) {
				buffer.write(newCharacters);
				if (sequenceNumber != 0) {
					if (characters.length > receivedCharacters[sequenceNumber].length) {
						receivedCharacters[sequenceNumber] = characters;
					}
					// All the characters received of the packet, also from a longer copy: fewer would be a Partial ACK,
					// which makes the BMC send the packet again
					acceptedCount = receivedCharacters[sequenceNumber].length;
					// Only a newer packet shows that the BMC resumed: a copy of the suspended packet may have left before
					// the Suspend NACK arrived
					if (!copy) {
						suspendedPacket = null;
					}
				}
			} else if (sequenceNumber == 0) {
				LOGGER.warn("Dropping {} characters received in a SOL packet without sequence number", characters.length);
			}
			// The statuses of a packet are reported as they come, even when its characters must wait for room (the BMC may
			// never send it again: SOL deactivated), and only once: a copy of the packet reports only a new status
			Set<SolStatus> received = packet.getStatusField().getStatuses();
			if (received != null) {
				for (SolStatus status : received) {
					int bit = 1 << status.ordinal();
					if (sequenceNumber == 0 || (reportedStatuses[sequenceNumber] & bit) == 0) {
						statuses.add(status);
						if (sequenceNumber != 0) {
							reportedStatuses[sequenceNumber] |= bit;
						}
					}
				}
			}
			// The readers waiting for characters
			buffer.notifyAll();
		}

		if (sequenceNumber != 0) {
			// Every packet with a sequence number is acknowledged, with the count of its characters received, even when
			// it carries none (IPMI 2.0 section 15.11)
			acknowledge(packet.getSequenceNumber(), accepted ? SolAckState.ACK : SolAckState.NACK, acceptedCount);
		}

		if (!accepted && sequenceNumber != 0) {
			// Recorded once the Suspend NACK is sent, so that no Resume ACK can overtake it; a read may have made room
			// meanwhile, and no other will come for a reader waiting for more characters
			int[] resume;
			synchronized (buffer) {
				suspendedPacket = packet;
				lastResumeNanos = System.nanoTime() - RESUME_INTERVAL_NANOS;
				completionAcks = 0;
				resume = takeResume();
				buffer.notifyAll();
			}
			resume(resume);
		}

		if (!statuses.isEmpty()) {
			for (SolEventListener listener : eventListeners) {
				listener.processRequestEvent(statuses);
			}
		}
	}

	/**
	 * Sends an ACK-only packet: an ACK of the given number of characters, a Suspend NACK (a NACK of 0 characters) or a
	 * Resume ACK (an ACK of 0 characters), with the line states the console holds (IPMI 2.0 table 15-3).
	 */
	private void acknowledge(byte sequenceNumber, SolAckState ackState, int characterCount) {
		try {
			SolCoder acknowledgement = new SolCoder(
					sequenceNumber,
					(byte) characterCount,
					ackState,
					lineOperations);
			connector.sendOneWayMessage(connectionHandle, acknowledgement);
		} catch (Exception e) {
			LOGGER.error("Could not send " + ackState + " for packet " + sequenceNumber, e);
		}
	}

	/**
	 * Sends the acknowledgement that {@link #takeResume()} returned, if any.
	 */
	private void resume(int[] resume) {
		if (resume != null) {
			acknowledge((byte) resume[0], SolAckState.ACK, resume[1]);
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
		int[] resume;
		synchronized (buffer) {
			result = buffer.read(numberOfBytes);
			resume = takeResume();
		}
		resume(resume);
		return result;
	}

	/**
	 * Returns the acknowledgement that resumes the BMC when there is room for the suspended packet and none was sent
	 * lately: an ACK of the characters received of the packet, a Resume ACK when none were, a Partial ACK when some were,
	 * after which the BMC sends the packet again, or a Completion ACK when all were (IPMI 2.0 table 15-3). It is sent
	 * again in case it is lost, until a newer packet shows that the BMC resumed, but only 10 times once all the
	 * characters are received. Called with the buffer locked.
	 *
	 * @return the sequence number and the character count of the acknowledgement, or null
	 */
	private int[] takeResume() {
		if (suspendedPacket == null
				|| unseen(suspendedPacket) > buffer.remainingSpace()
				|| System.nanoTime() - lastResumeNanos < RESUME_INTERVAL_NANOS) {
			return null;
		}
		if (unseen(suspendedPacket) == 0) {
			if (completionAcks == COMPLETION_ACKS) {
				return null;
			}
			completionAcks++;
		}
		// ponytail: an acknowledgement every 100 ms, whatever the round trip to the BMC
		lastResumeNanos = System.nanoTime();
		int sequenceNumber = suspendedPacket.getSequenceNumber();
		return new int[] { sequenceNumber, receivedCharacters[sequenceNumber].length };
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
			int[] resume;
			synchronized (buffer) {
				long remaining = deadline - System.nanoTime();
				if (buffer.size() >= numberOfBytes || remaining <= 0 || isFull()) {
					break;
				}
				// While waiting for a suspended packet to come back, its acknowledgement is sent again from time to time
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
			resume(resume);
		}
		return readBytes(numberOfBytes);
	}

	/**
	 * Tells whether no character can arrive before a read: the buffer is full, or a packet is suspended for lack of
	 * room. Called with the buffer locked.
	 */
	private boolean isFull() {
		return buffer.remainingSpace() == 0 || suspendedPacket != null && unseen(suspendedPacket) > buffer.remainingSpace();
	}

	/**
	 * Tells whether a packet is a copy of a recent packet of the BMC: of the last one (a retry), or of an older one
	 * (late), whose characters then start like those received. Called with the buffer locked.
	 */
	private boolean isCopy(SolInboundMessage packet) {
		int sequenceNumber = packet.getSequenceNumber();
		byte[] received = sequenceNumber == 0 ? null : receivedCharacters[sequenceNumber];
		if (received == null || seenPackets - ranks[sequenceNumber] >= HISTORY) {
			return false;
		}
		if (sequenceNumber == lastSequenceNumber) {
			return true;
		}
		byte[] characters = packet.getData();
		int common = Math.min(received.length, characters.length);
		return Arrays.equals(Arrays.copyOf(received, common), Arrays.copyOf(characters, common));
	}

	/**
	 * Returns how many characters of a packet were not received yet: all of them, except for a copy of a recent packet,
	 * which may have characters appended (IPMI 2.0 section 15.9). Called with the buffer locked.
	 */
	private int unseen(SolInboundMessage packet) {
		int length = packet.getData().length;
		return isCopy(packet) ? Math.max(0, length - receivedCharacters[packet.getSequenceNumber()].length) : length;
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
