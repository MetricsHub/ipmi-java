package org.metricshub.ipmi.core.sm.states;

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

import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.events.StateMachineEvent;

/**
 * The abstract for state of the {@link StateMachine}.
 */
public abstract class State {
    /**
     * Defines the action performed when the state is entered.
     *
     * @param stateMachine
     *            - the context
     */
    public void onEnter(StateMachine stateMachine) {
    }

    /**
     * Performs the state transition
     *
     * @param stateMachine
     *            - the context
     * @param machineEvent
     *            - the {@link StateMachineEvent} that was the cause of the
     *            transition
     */
    public abstract void doTransition(StateMachine stateMachine,
            StateMachineEvent machineEvent);

    /**
     * Defines the action that should be performed when a response form the
     * remote system arrives in the current state.
     *
     * @param stateMachine
     *            - the context
     * @param message
     *            - the message that appeared
     */
    public abstract void doAction(StateMachine stateMachine, RmcpMessage message);
}
