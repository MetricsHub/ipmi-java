package org.metricshub.ipmi.core.coding.payload.sol;

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

/**
 * Enumeration of all possible operations, that Remote Console can invoke on BMC during SOL communication.
 */
public enum SolOperation {
    /**
     * Assert RI (may not be supported on all implementations) - Goal is to allow this to be used for generating a WOR.
     */
    RingWOR(5),

    /**
     * Generate BREAK (300 ms, nominal)
     */
    Break(4),

    /**
     * Deassert CTS (clear to send) to the baseboard serial controller.
     * (This is the default state when SOL is deactivated.)
     */
    CTS(3),

    /**
     * When test mode inactive, deassert DCD/DSR to baseboard serial controller.
     * For test mode active, deassert just DCD to baseboard serial controller.
     */
    DCD_DSR(2),

    /**
     * When test mode inactive, drop (flush) data from remote console to BMC [not including data carried in this packet, if any].
     * For test mode active, deassert DSR to baseboard serial controller.
     */
    FlushInbound(1),

    /**
     * When test mode inactive, flush Outbound Character Data (flush data from BMC to remote console).
     * When test mode active, won't have any effect.
     */
    FlushOutbound(0);

    /**
     * ID of the operation (number of bit in operation field byte).
     */
    private final int operationNumber;

    SolOperation(int operationNumber) {
        this.operationNumber = operationNumber;
    }

    public int getOperationNumber() {
        return operationNumber;
    }
}
