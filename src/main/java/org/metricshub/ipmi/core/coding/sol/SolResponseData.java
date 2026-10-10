package org.metricshub.ipmi.core.coding.sol;

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

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.sol.SolAckState;
import org.metricshub.ipmi.core.coding.payload.sol.SolStatus;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.util.Set;

/**
 * Impementation of {@link ResponseData} for {@link org.metricshub.ipmi.core.coding.payload.sol.SolMessage}s.
 */
public class SolResponseData implements ResponseData {

	/**
	 * Sequence number of corresponding request message.
	 */
	private final byte requestSequenceNumber;

	/**
	 * Information if corresponding message was ACKd or NACKd by remote system.
	 */
	private final SolAckState acknowledgeState;

	/**
	 * Set of statuses returned by the remote system in a response for corresponging message.
	 */
	private final Set<SolStatus> statuses;

	/**
	 * Number of characters accepted from the corresponding message.
	 */
	private final int acceptedCharactersNumber;

	/**
	 * Creates new instance of {@link SolResponseData} filled with given data.
	 *
	 * @param acknowledgeState
	 *        Acknowledge status for corresponding request message
	 * @param statuses
	 *        Set of statuses
	 * @param acceptedCharactersNumber
	 *        Number of characters accepted from the corresponding message, 0 to 255
	 */
	public SolResponseData(byte requestSequenceNumber, SolAckState acknowledgeState, Set<SolStatus> statuses,
			int acceptedCharactersNumber) {
		this.requestSequenceNumber = requestSequenceNumber;
		this.acknowledgeState = acknowledgeState;
		this.statuses = statuses;
		this.acceptedCharactersNumber = acceptedCharactersNumber;
	}

	/**
	 * Creates new instance of {@link SolResponseData} filled with given data, with the count as the byte of the packet.
	 *
	 * @param requestSequenceNumber
	 *        Sequence number of the corresponding request message
	 * @param acknowledgeState
	 *        Acknowledge status for corresponding request message
	 * @param statuses
	 *        Set of statuses
	 * @param acceptedCharactersNumber
	 *        Number of characters accepted from the corresponding message, read as unsigned
	 * @deprecated use {@link #SolResponseData(byte, SolAckState, Set, int)}, which takes the count as an int
	 */
	@Deprecated
	public SolResponseData(byte requestSequenceNumber, SolAckState acknowledgeState, Set<SolStatus> statuses,
			byte acceptedCharactersNumber) {
		this(requestSequenceNumber, acknowledgeState, statuses, TypeConverter.byteToInt(acceptedCharactersNumber));
	}

	public byte getRequestSequenceNumber() {
		return requestSequenceNumber;
	}

	public SolAckState getAcknowledgeState() {
		return acknowledgeState;
	}

	public Set<SolStatus> getStatuses() {
		return statuses;
	}

	/**
	 * @return the number of characters accepted from the corresponding message, 0 to 255
	 */
	public int getAcceptedCharactersNumber() {
		return acceptedCharactersNumber;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("SolResponseData{");
		sb.append("requestSequenceNumber=").append(requestSequenceNumber);
		sb.append(", acknowledgeState=").append(acknowledgeState);
		sb.append(", statuses=").append(statuses);
		sb.append(", acceptedCharactersNumber=").append(acceptedCharactersNumber);
		sb.append('}');
		return sb.toString();
	}
}
