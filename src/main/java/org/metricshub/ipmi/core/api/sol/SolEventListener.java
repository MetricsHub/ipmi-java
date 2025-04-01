package  org.metricshub.ipmi.core.api.sol;

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

import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;
import org.metricshub.ipmi.core.coding.payload.sol.SolStatus;

import java.util.Set;

/**
 * Interface for Serial over LAN (SOL) events listener.
 * Events can be sent proactively by remote server or as a response for specific request.
 */
public interface SolEventListener {

    /**
     * Process event sent proactively by remote server.
     *
     * @param statuses
     *          statuses indicated by remote server
     */
    void processRequestEvent(Set<SolStatus> statuses);

    /**
     * Process event sent by remote server as a response for some outbound message.
     *
     * @param statuses
     *          statuses indicated by remote server
     * @param correspondingRequestData
     *          data from message, that caused this event to be fired.
     *          Can be empty array if message didn't contain character data.
     * @param correspondingRequestOperations
     *          set of operations invoked on remote server's serial port that caused this event to be fired.
     *          Can be empty set if corresponding request message didn't contain any operations to be invoked
     */
    void processResponseEvent(Set<SolStatus> statuses, byte[] correspondingRequestData, Set<SolOperation> correspondingRequestOperations);

}
