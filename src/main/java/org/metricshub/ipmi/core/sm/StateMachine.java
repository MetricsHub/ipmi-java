package org.metricshub.ipmi.core.sm;

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

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.metricshub.ipmi.core.coding.rmcp.RmcpDecoder;
import org.metricshub.ipmi.core.common.Constants;
import org.metricshub.ipmi.core.sm.actions.MessageAction;
import org.metricshub.ipmi.core.sm.actions.StateMachineAction;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;
import org.metricshub.ipmi.core.sm.states.SessionValid;
import org.metricshub.ipmi.core.sm.states.State;
import org.metricshub.ipmi.core.sm.states.Uninitialized;
import org.metricshub.ipmi.core.transport.Messenger;
import org.metricshub.ipmi.core.transport.UdpListener;
import org.metricshub.ipmi.core.transport.UdpMessage;

/**
 * State machine for connecting and acquiring session with the remote host via
 * IPMI v.2.0.
 */
public class StateMachine implements UdpListener {

	private final List<MachineObserver> observers = new CopyOnWriteArrayList<MachineObserver>();

	/**
	 * In-session messages received while the lock is held, dispatched by {@link #doTransition(StateMachineEvent)}
	 * and {@link #notifyMessage(UdpMessage)} once they release it, so that no application listener runs under the
	 * lock. The other actions (a handshake reply, an error, the session key) are published under the lock: the
	 * caller then sees the reply together with the state it produced, and cannot time the request out in between.
	 */
	private final List<StateMachineAction> pendingActions = new ArrayList<StateMachineAction>();

	private volatile State current;

	private Messenger messenger;
	private volatile InetAddress remoteMachineAddress;
	private volatile int remoteMachinePort;

	private volatile boolean initialized;

	public State getCurrent() {
		return current;
	}

	public void setCurrent(State current) {
		this.current = current;
		current.onEnter(this);
	}

	/**
	 * Initializes the State Machine
	 *
	 * @param messenger
	 *        - {@link Messenger} connected to the
	 *        {@link Constants#IPMI_PORT}
	 */
	public StateMachine(Messenger messenger) {
		this.messenger = messenger;
		initialized = false;
	}

	/**
	 * Sends message via {@link #messenger} to the managed system.
	 *
	 * @param message
	 *        - the encoded message
	 * @throws IOException
	 *         - when sending of the message fails
	 */
	public void sendMessage(byte[] message) throws IOException {
		UdpMessage udpMessage = new UdpMessage();
		udpMessage.setAddress(getRemoteMachineAddress());
		udpMessage.setPort(getRemoteMachinePort());
		udpMessage.setMessage(message);
		messenger.send(udpMessage);
	}

	public InetAddress getRemoteMachineAddress() {
		return remoteMachineAddress;
	}

	public int getRemoteMachinePort() {
		return remoteMachinePort;
	}

	/**
	 * Sends a notification of an action to all {@link MachineObserver}s. A {@link MessageAction} emitted by a state
	 * while the lock is held is deferred until the transition releases it; the other actions are published at once.
	 *
	 * @param action
	 *        - a {@link StateMachineAction} to perform
	 */
	public void doExternalAction(StateMachineAction action) {
		if (action instanceof MessageAction && Thread.holdsLock(this)) {
			pendingActions.add(action);
		} else {
			notifyObservers(action);
		}
	}

	private void notifyObservers(StateMachineAction action) {
		for (MachineObserver observer : observers) {
			if (observer != null) {
				observer.notify(action);
			}
		}
	}

	private void dispatch(List<StateMachineAction> actions) {
		for (StateMachineAction action : actions) {
			notifyObservers(action);
		}
	}

	/** Returns the actions emitted so far and clears them; called under the lock. */
	private List<StateMachineAction> drainPendingActions() {
		List<StateMachineAction> actions = new ArrayList<StateMachineAction>(pendingActions);
		pendingActions.clear();
		return actions;
	}

	/**
	 * Sets the State Machine in the initial state.
	 *
	 * @param address
	 *        - IP address of the remote machine.
	 * @param port
	 *        - UDP remoteMachinePort of the remote machine
	 * @see #stop()
	 */
	public void start(InetAddress address, int port) {
		messenger.register(this);
		remoteMachineAddress = address;
		this.remoteMachinePort = port;
		setCurrent(new Uninitialized());
		initialized = true;
	}

	/**
	 * Cleans up the machine resources and leaves the current state: a stopped machine no longer reports a valid
	 * session.
	 *
	 * @see #start(InetAddress, int)
	 */
	public void stop() {
		messenger.unregister(this);
		initialized = false;
		current = new Uninitialized();
	}

	/**
	 * @return true if {@link StateMachine} is initialized, false otherwise.
	 * @see #start(InetAddress, int)
	 * @see #stop()
	 */
	public boolean isActive() {
		return initialized;
	}

	/**
	 * Performs a {@link State} transition according to the event and
	 * {@link #current} state. Transitions and received messages are serialized, so a late reply cannot interleave
	 * with the timeout or close of the request it answers; the in-session messages are dispatched once the lock is
	 * released.
	 *
	 * @param event
	 *        - {@link StateMachineEvent} invoking the transition
	 * @throws NullPointerException
	 *         - when machine was not yet started
	 * @see #start(InetAddress, int)
	 */
	public void doTransition(StateMachineEvent event) {
		List<StateMachineAction> actions;
		synchronized (this) {
			if (!initialized) {
				throw new NullPointerException("State machine not started");
			}
			current.doTransition(this, event);
			actions = drainPendingActions();
		}
		dispatch(actions);
	}

	@Override
	public void notifyMessage(UdpMessage message) {
		if (!message.getAddress().equals(getRemoteMachineAddress()) || message.getPort() != getRemoteMachinePort()) {
			return;
		}
		List<StateMachineAction> actions;
		synchronized (this) {
			current.doAction(this, RmcpDecoder.decode(message.getMessage()));
			actions = drainPendingActions();
		}
		dispatch(actions);
	}

	/**
	 * Registers the listener in the {@link StateMachine} so it will be notified
	 * of the {@link StateMachineAction}s performed via
	 * {@link #doExternalAction(StateMachineAction)}
	 *
	 * @param observer
	 *        - {@link MachineObserver} to register
	 */
	public void register(MachineObserver observer) {
		observers.add(observer);
	}

	/**
	 * @return true if {@link StateMachine} is at the point when it acquires
	 *         session and will send sessionless messages
	 */
	public boolean isSessionChallenging() {
		return !initialized || getCurrent().getClass() == SessionValid.class;
	}
}
