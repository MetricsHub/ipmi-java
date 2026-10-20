package org.metricshub.ipmi.core.coding.commands.sel;

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
 * Whether a system event is an assertion or a deassertion (bit 7 of the Event Dir | Event Type byte, IPMI 2.0 table
 * 32-1).
 */
public enum EventDirection {

	/**
	 * The event asserts its state.
	 */
	Assertion(EventDirection.ASSERTION),
	/**
	 * The event deasserts its state.
	 */
	Deassertion(EventDirection.DEASSERTION),;

	private static final int ASSERTION = 0;
	private static final int DEASSERTION = 1;

	private int code;

	EventDirection(int code) {
		this.code = code;
	}

	/**
	 * Returns the value of the event direction bit.
	 *
	 * @return 0 for Assertion, 1 for Deassertion
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Returns the event direction of an event direction bit.
	 *
	 * @param value the event direction bit, 0 or 1
	 * @return the event direction
	 * @throws IllegalArgumentException when the value is not 0 or 1
	 */
	public static EventDirection parseInt(int value) {
		switch (value) {
		case ASSERTION:
			return Assertion;
		case DEASSERTION:
			return Deassertion;
		default:
			throw new IllegalArgumentException("Invalid value: " + value);
		}
	}
}
