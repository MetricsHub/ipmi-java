package org.metricshub.ipmi.core.coding.commands.chassis;

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
 * Specifies types of commands that can be sent to BMC via
 * {@link ChassisControl} command.
 */
public enum PowerCommand {
    /**
     * Force system into soft off (S4/S45) state. This is for 'emergency'
     * management power down actions. The command does not initiate a clean
     * shut-down of the operating system prior to powering down the system.
     */
    PowerDown(PowerCommand.POWERDOWN), PowerUp(PowerCommand.POWERUP),
    /**
     * Hard reset. In some implementations, the BMC may not know whether a reset
     * will cause any particular effect and will pulse the system reset signal
     * regardless of power state. If the implementation can tell that no action
     * will occur if a reset is delivered in a given power state, then it is
     * recommended (but still optional) that a D5h 'Request parameter(s) not
     * supported in present state.' error completion code be returned.
     */
    HardReset(PowerCommand.HARDRESET), ;
    private static final int POWERDOWN = 0;
    private static final int POWERUP = 1;
    private static final int HARDRESET = 3;

    private int code;

    PowerCommand(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PowerCommand parseInt(int value) {
        switch (value) {
        case POWERDOWN:
            return PowerDown;
        case POWERUP:
            return PowerUp;
        case HARDRESET:
            return HardReset;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
