package org.sentrysoftware.ipmi.core.sm.events;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, Sentry Software
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

import org.sentrysoftware.ipmi.core.coding.commands.PrivilegeLevel;
import org.sentrysoftware.ipmi.core.coding.security.CipherSuite;
import org.sentrysoftware.ipmi.core.sm.StateMachine;
import org.sentrysoftware.ipmi.core.sm.states.Authcap;
import org.sentrysoftware.ipmi.core.sm.states.OpenSessionWaiting;

/**
 * Performs transition from {@link Authcap} to {@link OpenSessionWaiting}.
 * 
 * @see StateMachine
 */
public class Authorize extends Default {

    private int sessionId;

    public int getSessionId() {
        return sessionId;
    }

    public Authorize(CipherSuite cipherSuite, int sequenceNumber,
            PrivilegeLevel privilegeLevel, int sessionId) {
        super(cipherSuite, sequenceNumber, privilegeLevel);
        this.sessionId = sessionId;
    }

}
