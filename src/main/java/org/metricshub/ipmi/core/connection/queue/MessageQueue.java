package org.metricshub.ipmi.core.connection.queue;

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
import org.metricshub.ipmi.core.connection.Connection;
import org.metricshub.ipmi.core.connection.ConnectionException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Queues messages to send and checks for timeouts.
 */
public class MessageQueue extends TimerTask {

	private List<QueueElement> queue;
	private volatile int timeout;
	private Timer timer;
	private Connection connection;
	private int lastSequenceNumber;
	private Object lastSequenceNumberLock = new Object();
	private int minSequenceNumber = 1;
	private int sequenceNumberUpperBound = 64;

	/**
	 * Frequency of checking messages for timeouts in ms.
	 */
	private static final int CLEANING_FREQUENCY = 500;

	/**
	 * Size of the queue determined by IPMI sliding window algorithm
	 * specification. <br>
	 * When queue size is 16, BMC drops some of the messages under heavy load.
	 */
	private static final int QUEUE_SIZE = 8;

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public MessageQueue(Connection connection, int timeout, int minSequenceNumber, int maxSequenceNumber) {
		this.minSequenceNumber = minSequenceNumber;
		sequenceNumberUpperBound = maxSequenceNumber + 1;
		reservedTags = new ArrayList<Integer>();
		lastSequenceNumber = minSequenceNumber - 1;
		this.connection = connection;
		queue = new ArrayList<QueueElement>();
		setTimeout(timeout);
		timer = new Timer(true);
		timer.schedule(this, CLEANING_FREQUENCY, CLEANING_FREQUENCY);
	}

	private int incrementSequenceNumber(int currentSequenceNumber) {
		int newSequenceNumber = (currentSequenceNumber + 1) % sequenceNumberUpperBound;

		if (newSequenceNumber == 0) {
			newSequenceNumber = minSequenceNumber;
		}

		return newSequenceNumber;
	}

	/**
	 * Stops the MessageQueue: the requests still waiting for a reply are reported as failed, since the timer that
	 * would report their timeout stops.
	 */
	public void tearDown() {
		timer.cancel();
		clear("Connection closed");
	}

	/**
	 * Removes every request from the queue and frees their tags: those still waiting for a reply are reported as
	 * failed, the one-way ones are dropped.
	 *
	 * @param failure the message of the {@link ConnectionException} reported to the requests still waiting
	 */
	public void clear(String failure) {
		synchronized (queue) {
			while (!queue.isEmpty()) {
				QueueElement element = queue.get(0);
				processObsoleteMessage(element, element.getRequest() == null, failure);
			}
		}
	}

	private List<Integer> reservedTags;

	/**
	 * Check if the tag is reserved.
	 */
	private synchronized boolean isReserved(int tag) {
		return reservedTags.contains(tag);
	}

	/**
	 * Reserves given tag for the use of the invoker.
	 *
	 * @param tag
	 *        - tag to reserve
	 * @return true if tag was reserved successfully, false otherwise
	 */
	private synchronized boolean reserveTag(int tag) {
		if (!isReserved(tag)) {
			reservedTags.add(tag);
			return true;
		}
		return false;
	}

	private synchronized void releaseTag(int tag) {
		reservedTags.remove((Integer) tag);
	}

	/**
	 * Adds request to the queue and generates the tag.
	 *
	 * @return Sequence number of the message if it was added to the
	 *         queue, -1 otherwise. The tag used to identify message is equal to
	 *         that value.
	 */
	public int add(PayloadCoder request) {
		return add(request, false);
	}

	/**
	 * Adds request to the queue and generates the tag. A one-way request holds its tag like any other until its reply
	 * arrives or it times out, so that a late reply cannot be taken for the reply of a newer request; its reply and
	 * its timeout are not reported to the listeners.
	 *
	 * @param request the request to queue
	 * @param oneWay true when nobody waits for the reply
	 * @return Sequence number of the message if it was added to the queue, -1 otherwise. The tag used to identify
	 *         message is equal to that value.
	 */
	public int add(PayloadCoder request, boolean oneWay) {
		run();
		synchronized (queue) {
			synchronized (lastSequenceNumberLock) {
				if (queue.size() < QUEUE_SIZE) {
					int sequenceNumber = incrementSequenceNumber(lastSequenceNumber);

					while (isReserved(sequenceNumber)) {
						sequenceNumber = incrementSequenceNumber(sequenceNumber);

						if (sequenceNumber == lastSequenceNumber) {
							// we checked all available sequence numbers, so return -1 (no available sequence numbers)
							return -1;
						}
					}

					reserveTag(sequenceNumber);

					lastSequenceNumber = sequenceNumber;

					QueueElement element = new QueueElement(sequenceNumber, request, oneWay);

					queue.add(element);
					return sequenceNumber;
				}
			}
		}
		return -1;

	}

	/**
	 * Adds request to the queue under the given tag, when no other request holds it: a SOL packet sent again keeps its
	 * packet sequence number.
	 *
	 * @param request the request to queue
	 * @param oneWay true when nobody waits for the reply
	 * @param tag the tag of the request
	 * @return the tag, or -1 when the tag is taken or the queue is full
	 */
	public int add(PayloadCoder request, boolean oneWay, int tag) {
		run();
		synchronized (queue) {
			if (queue.size() >= QUEUE_SIZE || !reserveTag(tag)) {
				return -1;
			}
			queue.add(new QueueElement(tag, request, oneWay));
			return tag;
		}
	}

	/**
	 * Returns the queued request with the given tag, which is about to be sent again, and restarts its timeout: the
	 * request moves to the end of the queue, which stays in the order of the timeouts, with its tag.
	 *
	 * @param tag the tag of the request
	 * @return the request, or null if no request with the given tag awaits a reply
	 */
	public PayloadCoder retry(int tag) {
		synchronized (queue) {
			QueueElement element = getElement(tag);
			if (element == null) {
				return null;
			}
			queue.remove(element);
			element.resetTimestamp();
			queue.add(element);
			return element.getRequest();
		}
	}

	/**
	 * Removes the given element from the queue, if it is still there: a request sent again under the same tag is
	 * another element, which stays.
	 *
	 * @param element the element of the request answered
	 * @return whether the element was still waiting for its reply: false when it timed out or failed meanwhile, and was
	 *         reported so
	 */
	public boolean remove(QueueElement element) {
		synchronized (queue) {
			// QueueElement does not override equals(): the element itself
			int index = queue.indexOf(element);
			boolean waiting = index >= 0 && element.getRequest() != null;
			removeIndex(index, element.getId());
			return waiting;
		}
	}

	/**
	 * Numbers the next request from the first sequence number again: a new SOL payload activation numbers its packets
	 * from 1.
	 */
	public void resetSequenceNumber() {
		synchronized (lastSequenceNumberLock) {
			lastSequenceNumber = minSequenceNumber - 1;
		}
	}

	/**
	 * Removes message with the given tag from the queue.
	 */
	public void remove(int tag) {
		run();
		synchronized (queue) {
			int i = 0;
			int index = -1;
			for (QueueElement element : queue) {
				if (element.getId() == tag) {
					index = i;
					break;
				}
				++i;
			}
			removeIndex(index, tag);
		}
	}

	/**
	 * Removes the element at the given index, with the answered elements that then head the queue; an element behind
	 * the head is only marked as answered, to keep the tags of the head reserved. Called with the queue locked.
	 */
	private void removeIndex(int index, int tag) {
		if (index == 0) {
			queue.remove(0);
			releaseTag(tag);
			while (!queue.isEmpty() && queue.get(0).getRequest() == null) {
				int additionalTag = queue.get(0).getId();
				queue.remove(0);
				releaseTag(additionalTag);
			}
		} else if (index > 0) {
			queue.get(index).setRequest(null);
		}
	}

	/**
	 * Returns a sequence number for a message sent outside the queue (the Close Session request): it skips the tags of
	 * the queued requests.
	 *
	 * @return a sequence number that no queued request holds
	 */
	public int getSequenceNumber() {
		synchronized (lastSequenceNumberLock) {
			int sequenceNumber = incrementSequenceNumber(lastSequenceNumber);
			while (isReserved(sequenceNumber)) {
				sequenceNumber = incrementSequenceNumber(sequenceNumber);
			}
			lastSequenceNumber = sequenceNumber;
			return sequenceNumber;
		}
	}

	/**
	 * Returns the queued request with the given tag, with its one-way flag.
	 *
	 * @param tag the tag of the request
	 * @return the {@link QueueElement} of the request, or null if no request with the given tag awaits a reply
	 */
	public QueueElement getElement(int tag) {
		synchronized (queue) {
			for (QueueElement element : queue) {
				if (element.getId() == tag && element.getRequest() != null) {
					return element;
				}
			}
		}
		return null;
	}

	/**
	 * {@link TimerTask} runner - periodically checks queue for timed out
	 * messages.
	 */
	@Override
	public void run() {
		if (queue != null) {
			synchronized (queue) {
				boolean process = true;
				while (process && !queue.isEmpty()) {
					QueueElement oldestQueueElement = queue.get(0);
					boolean done = oldestQueueElement.getRequest() == null;

					if (messageJustTimedOut(oldestQueueElement) || done) {
						processObsoleteMessage(oldestQueueElement, done, "Message timed out");
					} else {
						process = false;
					}
				}
			}
		}
	}

	private boolean messageJustTimedOut(QueueElement oldestQueueElement) {
		Date now = new Date();

		return now.getTime() - oldestQueueElement.getTimestamp().getTime() > (long) timeout;
	}

	/**
	 * Removes the oldest message from the queue; when it timed out or the queue stops (rather than being answered),
	 * the response listeners are told so, which lets the sender retry it with a fresh tag. Nobody waits for a one-way
	 * message: its failure is not reported.
	 */
	private void processObsoleteMessage(QueueElement message, boolean done, String failure) {
		int tag = message.getId();

		queue.remove(0);
		releaseTag(tag);

		if (!done && !message.isOneWay()) {
			try {
				connection.notifyResponseListeners(connection.getHandle(), tag, null, new ConnectionException(failure));
			} catch (RuntimeException e) {
				// A failing listener must not kill the timer thread that expires the other messages
			}
		}
	}

}
