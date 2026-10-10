package org.metricshub.ipmi.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.security.ConfidentialityNone;
import org.metricshub.ipmi.core.transport.SilentMessenger;

class MessageHandlerTest {

	/**
	 * Feeds messages with the given session sequence numbers to a handler and returns the numbers it delivered.
	 */
	private static List<Integer> deliver(int... sequenceNumbers) {
		List<Integer> delivered = new ArrayList<>();
		MessageHandler handler = new MessageHandler(new Connection(new SilentMessenger(), 0), 1000, 1, 63) {
			@Override
			protected void handleIncomingMessageInternal(Ipmiv20Message message) {
				delivered.add(message.getSessionSequenceNumber());
			}
		};
		try {
			for (int sequenceNumber : sequenceNumbers) {
				Ipmiv20Message message = new Ipmiv20Message(new ConfidentialityNone());
				message.setSessionSequenceNumber(sequenceNumber);
				handler.handleIncomingMessage(message);
			}
		} finally {
			handler.tearDown();
		}
		return delivered;
	}

	@Test
	void aMessageReceivedTwiceIsDeliveredOnce() {
		// 2 and 4 replayed, 4 and 3 late
		assertEquals(Arrays.asList(1, 2, 5, 4, 3), deliver(1, 2, 2, 5, 4, 4, 3, 2));
	}

	@Test
	void aLateMessageIsDeliveredUpTo16BelowTheHighestNumber() {
		// IPMI 2.0 section 6.12.14: "within plus 15 or minus 16 counts" of the highest number received
		assertEquals(Arrays.asList(40, 24), deliver(40, 23, 24));
	}

	@Test
	void anyNumberAboveTheHighestIsDelivered() {
		// After 20 lost messages (or 20 SOL messages, which share the numbers), the session must go on
		assertEquals(Arrays.asList(1, 22, 23), deliver(1, 22, 23));
	}

	@Test
	void theWindowWrapsAroundLike32BitNumbers() {
		// 0x7FFFFFFF to 0x80000001 crosses the sign of an int, 0xFFFFFFFF to 1 the end of the 32-bit numbers
		assertEquals(
				Arrays.asList(0x7ffffff0, 0x80000001, 0x80000000, 0xffffffff, 1, 0xfffffffe),
				deliver(0x7ffffff0, 0x80000001, 0x80000000, 0x7ffffff0, 0xffffffff, 1, 0xfffffffe, 1));
	}
}
