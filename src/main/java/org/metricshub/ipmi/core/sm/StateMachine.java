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

import org.metricshub.ipmi.core.coding.rmcp.RmcpDecoder;
import org.metricshub.ipmi.core.common.Constants;
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

    private List<MachineObserver> observers;

    private State current;

    private Messenger messenger;
    private InetAddress remoteMachineAddress;
    private int remoteMachinePort;

    private boolean initialized;

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
     *            - {@link Messenger} connected to the
     *            {@link Constants#IPMI_PORT}
     */
    public StateMachine(Messenger messenger) {
        this.messenger = messenger;
        observers = new ArrayList<MachineObserver>();
        initialized = false;
    }

    /**
     * Sends message via {@link #messenger} to the managed system.
     *
     * @param message
     *            - the encoded message
     * @throws IOException
     *             - when sending of the message fails
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
     * Sends a notification of an action to all {@link MachineObserver}s
     *
     * @param action
     *            - a {@link StateMachineAction} to perform
     */
    public void doExternalAction(StateMachineAction action) {
        for (MachineObserver observer : observers) {
            if (observer != null) {
                observer.notify(action);
            }
        }
    }

    /**
     * Sets the State Machine in the initial state.
     *
     * @param address
     *            - IP address of the remote machine.
     * @param port
     *             - UDP remoteMachinePort of the remote machine
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
     * Cleans up the machine resources.
     *
     * @see #start(InetAddress, int)
     */
    public void stop() {
        messenger.unregister(this);
        initialized = false;
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
     * {@link #current} state
     *
     * @param event
     *            - {@link StateMachineEvent} invoking the transition
     * @throws NullPointerException
     *             - when machine was not yet started
     * @see #start(InetAddress, int)
     */
    public void doTransition(StateMachineEvent event) {
        if (!initialized) {
            throw new NullPointerException("State machine not started");
        }
        current.doTransition(this, event);
    }

    @Override
    public void notifyMessage(UdpMessage message) {
        if (message.getAddress().equals(getRemoteMachineAddress()) && message.getPort() == getRemoteMachinePort()) {
            current.doAction(this, RmcpDecoder.decode(message.getMessage()));
        }
    }

    /**
     * Registers the listener in the {@link StateMachine} so it will be notified
     * of the {@link StateMachineAction}s performed via
     * {@link #doExternalAction(StateMachineAction)}
     *
     * @param observer
     *            - {@link MachineObserver} to register
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
