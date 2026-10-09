package org.metricshub.ipmi.core.sm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.sm.actions.ErrorAction;
import org.metricshub.ipmi.core.sm.actions.StateMachineAction;
import org.metricshub.ipmi.core.sm.events.GetChannelCipherSuitesPending;
import org.metricshub.ipmi.core.sm.states.Uninitialized;
import org.metricshub.ipmi.core.transport.SilentMessenger;
import org.metricshub.ipmi.core.transport.UdpMessage;

class StateMachineTest {

	@Test
	void observersAreNotifiedOutsideTheLockAndTheStateIsRolledBackFirst() throws Exception {
		StateMachine machine = new StateMachine(new SilentMessenger() {
			@Override
			public void send(UdpMessage message) {
				throw new IllegalStateException("cable unplugged");
			}
		});
		machine.start(InetAddress.getLoopbackAddress(), 623);
		List<StateMachineAction> actions = new CopyOnWriteArrayList<>();
		AtomicBoolean lockHeld = new AtomicBoolean(true);
		AtomicBoolean rolledBack = new AtomicBoolean();
		machine.register(action -> {
			actions.add(action);
			lockHeld.set(Thread.holdsLock(machine));
			rolledBack.set(machine.getCurrent() instanceof Uninitialized);
		});

		machine.doTransition(new GetChannelCipherSuitesPending(1));

		assertEquals(1, actions.size());
		assertTrue(actions.get(0) instanceof ErrorAction, String.valueOf(actions.get(0)));
		assertFalse(lockHeld.get(), "the observer must not run under the state machine lock");
		assertTrue(rolledBack.get(), "the transition must be complete when the observer runs");
	}
}
