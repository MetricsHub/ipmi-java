package org.metricshub.ipmi.core.sm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.Encoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.actions.MessageAction;
import org.metricshub.ipmi.core.sm.actions.StateMachineAction;
import org.metricshub.ipmi.core.sm.events.GetChannelCipherSuitesPending;
import org.metricshub.ipmi.core.sm.states.SessionValid;
import org.metricshub.ipmi.core.sm.states.Uninitialized;
import org.metricshub.ipmi.core.transport.SilentMessenger;
import org.metricshub.ipmi.core.transport.UdpMessage;

class StateMachineTest {

	private static final int SESSION_ID = 1;

	private final List<StateMachineAction> actions = new CopyOnWriteArrayList<>();

	private final AtomicBoolean lockHeld = new AtomicBoolean();

	private final AtomicBoolean rolledBack = new AtomicBoolean();

	private StateMachine machine;

	private void start(SilentMessenger messenger) {
		machine = new StateMachine(messenger);
		machine.start(InetAddress.getLoopbackAddress(), 623);
		machine.register(action -> {
			actions.add(action);
			lockHeld.set(Thread.holdsLock(machine));
			rolledBack.set(machine.getCurrent() instanceof Uninitialized);
		});
	}

	@Test
	void aHandshakeErrorIsPublishedWithTheStateItProduced() {
		start(new SilentMessenger() {
			@Override
			public void send(UdpMessage message) {
				throw new IllegalStateException("cable unplugged");
			}
		});

		machine.doTransition(new GetChannelCipherSuitesPending(1));

		assertEquals(1, actions.size());
		assertTrue(actions.get(0) instanceof ErrorAction, String.valueOf(actions.get(0)));
		assertTrue(rolledBack.get(), "the state must be rolled back when the error is published");
		assertTrue(lockHeld.get(), "a handshake action is published under the lock, before a timeout can be applied");
	}

	@Test
	void anInSessionMessageIsDispatchedOutsideTheLock() throws Exception {
		start(new SilentMessenger());
		machine.setCurrent(new SessionValid(CipherSuite.getEmpty(), SESSION_ID));
		byte[] raw = Encoder
				.encode(
						new Protocolv20Encoder(),
						new GetChannelAuthenticationCapabilities(IpmiVersion.V20, IpmiVersion.V20, CipherSuite.getEmpty()),
						1,
						1,
						SESSION_ID);
		UdpMessage message = new UdpMessage();
		message.setAddress(InetAddress.getLoopbackAddress());
		message.setPort(623);
		message.setMessage(raw);

		machine.notifyMessage(message);

		assertEquals(1, actions.size());
		assertTrue(actions.get(0) instanceof MessageAction, String.valueOf(actions.get(0)));
		assertFalse(lockHeld.get(), "an in-session message must not be dispatched under the lock");
	}
}
