package org.metricshub.ipmi.core.coding.commands.session;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
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

/**
 * The reply to Get Session Info (IPMI 2.0 table 22-26): the session counts of the BMC, and the user ID, privilege
 * level and channel of the session looked up when the BMC has it.
 */
public class GetSessionInfoResponseData implements ResponseData {

	private int sessionHandle;
	private int maxSessions;
	private int activeSessions;
	private boolean sessionFound;
	private int userId;
	private int privilegeLevel;
	private int channel;

	/**
	 * @return the session handle of the session looked up, 0 when the BMC has no such session
	 */
	public int getSessionHandle() {
		return sessionHandle;
	}

	/**
	 * @param sessionHandle the session handle
	 */
	public void setSessionHandle(int sessionHandle) {
		this.sessionHandle = sessionHandle;
	}

	/**
	 * @return how many sessions the BMC can have at once
	 */
	public int getMaxSessions() {
		return maxSessions;
	}

	/**
	 * @param maxSessions how many sessions the BMC can have at once
	 */
	public void setMaxSessions(int maxSessions) {
		this.maxSessions = maxSessions;
	}

	/**
	 * @return how many sessions the BMC has now
	 */
	public int getActiveSessions() {
		return activeSessions;
	}

	/**
	 * @param activeSessions how many sessions the BMC has now
	 */
	public void setActiveSessions(int activeSessions) {
		this.activeSessions = activeSessions;
	}

	/**
	 * @return whether the BMC has the session looked up: the reply then carries its user ID, privilege and channel
	 */
	public boolean isSessionFound() {
		return sessionFound;
	}

	/**
	 * @param sessionFound whether the BMC has the session looked up
	 */
	public void setSessionFound(boolean sessionFound) {
		this.sessionFound = sessionFound;
	}

	/**
	 * @return the user ID of the session, when found
	 */
	public int getUserId() {
		return userId;
	}

	/**
	 * @param userId the user ID of the session
	 */
	public void setUserId(int userId) {
		this.userId = userId;
	}

	/**
	 * @return the privilege level of the session (2 User, 3 Operator, 4 Administrator), when found
	 */
	public int getPrivilegeLevel() {
		return privilegeLevel;
	}

	/**
	 * @param privilegeLevel the privilege level of the session
	 */
	public void setPrivilegeLevel(int privilegeLevel) {
		this.privilegeLevel = privilegeLevel;
	}

	/**
	 * @return the channel of the session, when found
	 */
	public int getChannel() {
		return channel;
	}

	/**
	 * @param channel the channel of the session
	 */
	public void setChannel(int channel) {
		this.channel = channel;
	}

	@Override
	public String toString() {
		return "GetSessionInfoResponseData{sessionHandle=" + sessionHandle + ", maxSessions=" + maxSessions
				+ ", activeSessions=" + activeSessions
				+ (sessionFound ?
						", userId=" + userId + ", privilegeLevel=" + privilegeLevel + ", channel=" + channel :
						", session not found")
				+ "}";
	}
}
