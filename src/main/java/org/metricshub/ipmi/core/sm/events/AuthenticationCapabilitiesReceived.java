package org.metricshub.ipmi.core.sm.events;

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

import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.states.Authcap;
import org.metricshub.ipmi.core.sm.states.AuthcapWaiting;

/**
 * Performs transition from {@link AuthcapWaiting} to {@link Authcap}.
 * 
 * @see StateMachine
 */
public class AuthenticationCapabilitiesReceived extends StateMachineEvent {
    private int sessionId;
    private PrivilegeLevel privilegeLevel;

    public AuthenticationCapabilitiesReceived(int sessionId, PrivilegeLevel privilegeLevel) {
        this.sessionId = sessionId;
        this.privilegeLevel = privilegeLevel;
    }

    public int getSessionId() {
        return sessionId;
    }
    public PrivilegeLevel getPrivilegeLevel() {
        return privilegeLevel;
    }


}
