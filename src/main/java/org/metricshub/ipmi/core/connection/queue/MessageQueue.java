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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private int timeout;
    private Timer timer;
    private Connection connection;
    private int lastSequenceNumber;
    private Object lastSequenceNumberLock = new Object();
    private int minSequenceNumber = 1;
    private int sequenceNumberUpperBound = 64;

    private static Logger logger = LoggerFactory.getLogger(MessageQueue.class);

    /**
     * Frequency of checking messages for timeouts in ms.
     */
    private static int cleaningFrequency = 500;

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
        timer = new Timer();
        timer.schedule(this, cleaningFrequency, cleaningFrequency);
    }

    private int incrementSequenceNumber(int currentSequenceNumber) {
        int newSequenceNumber = (currentSequenceNumber + 1) % sequenceNumberUpperBound;

        if (newSequenceNumber == 0) {
            newSequenceNumber = minSequenceNumber;
        }

        return newSequenceNumber;
    }

    /**
     * Stops the MessageQueue
     */
    public void tearDown() {
        timer.cancel();
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
     *            - tag to reserve
     * @return true if tag was reserved successfully, false otherwise
     */
    private synchronized boolean reserveTag(int tag) {
        if (isReserved(tag)) {
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
        run();
        boolean first = true;
        synchronized (queue) {
            synchronized (lastSequenceNumberLock) {
                if (queue.size() < QUEUE_SIZE) {
                    int sequenceNumber = incrementSequenceNumber(lastSequenceNumber);

                    while (isReserved(sequenceNumber)) {
                        sequenceNumber = incrementSequenceNumber(sequenceNumber);

                        if (!first) {
                            try {
                                lastSequenceNumberLock.wait(1);
                            } catch (InterruptedException e) {
                                // TODO log
                            }
                        }

                        if (sequenceNumber == lastSequenceNumber) {
                            //we checked all available sequence numbers, so return -1 (no available sequence numbers)
                            return -1;
                        }

                        first = false;
                    }

                    reserveTag(sequenceNumber);

                    lastSequenceNumber = sequenceNumber;

                    QueueElement element = new QueueElement(sequenceNumber, request);

                    queue.add(element);
                    return sequenceNumber;
                }
            }
        }
        return -1;

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
    }

    /**
     * Removes message from queue at given index.
     *
     * @param index
     */
    public void removeAt(int index) {
        if (index >= queue.size()) {
            throw new IndexOutOfBoundsException("Index out of bounds : "
                    + index);
        }

        remove(queue.get(index).getId());
    }

    /**
     * Checks if queue contains message with the given sequence number.
     */
    public boolean containsId(int sequenceNumber) {
        synchronized (queue) {

            for (QueueElement element : queue) {
                if (element.getId() == sequenceNumber
                        && element.getRequest() != null) {
                    return true;
                }
            }

        }
        return false;
    }

    /**
     * Returns valid session sequence number that cannot be used as a tag though
     */
    public int getSequenceNumber() {
        synchronized (lastSequenceNumberLock) {
            int sequenceNumber = incrementSequenceNumber(lastSequenceNumber);

            lastSequenceNumber = sequenceNumber;

            return sequenceNumber;
        }
    }

    /**
     * Returns message with the given sequence number from the queue or null if
     * no message with the given tag is currently in the queue.
     */
    public PayloadCoder getMessageFromQueue(int tag) {
        synchronized (queue) {
            for (QueueElement element : queue) {
                if (element.getId() == tag && element.getRequest() != null) {
                    return element.getRequest();
                }
            }
        }
        return null;
    }

    /**
     * Returns index of the message with the given sequence number from the
     * queue or -1 if no message with the given tag is currently in the queue.
     */
    public int getMessageIndexFromQueue(int tag) {
        synchronized (queue) {
            int i = 0;
            for (QueueElement element : queue) {
                if (element.getId() == tag && element.getRequest() != null) {
                    return i;
                }
                ++i;
            }
        }
        return -1;
    }

    /**
     * Returns number of retries that were performed on message tagged with tag
     * or -1 if no such message can be found in the queue.
     *
     * @deprecated retries are no longer supported on the message level
     */
    @Deprecated
    public int getMessageRetries(int tag) {
        synchronized (queue) {
            for (QueueElement element : queue) {
                if (element.getId() == tag && element.getRequest() != null) {
                    return element.getRetries();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the ID of the {@link QueueElement} in the queue with the given
     * tag.
     *
     * @param tag
     *            Tag of the message to find
     */
    public int getMessageSequenceNumber(int tag) {
        synchronized (queue) {
            for (QueueElement element : queue) {
                if (element.getId() == tag && element.getRequest() != null) {
                    return element.getId();
                }
            }
        }
        return -1;
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
                        processObsoleteMessage(oldestQueueElement, done);
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

    private void processObsoleteMessage(QueueElement message, boolean done) {
        int tag = message.getId();
        boolean previouslyTimedOut = message.isTimedOut();

        if (previouslyTimedOut || done) {
            queue.remove(0);
            logger.info("Removing message after timeout, tag: " + tag);
            releaseTag(tag);
        } else {
            message.makeTimedOut();
            message.refreshTimestamp();
            connection.notifyResponseListeners(connection.getHandle(), tag, null,
                    new ConnectionException("Message timed out"));
        }
    }

}
