package org.metricshub.ipmi.core.coding.commands.session;

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

import org.metricshub.ipmi.core.coding.commands.CommandsConstants;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.ResponseData;

/**
 * A wrapper for Open Session command response
 * 
 * @see OpenSession
 */
public class OpenSessionResponseData implements ResponseData {
    /**
     * The BMC returns the Message Tag value that was passed by the remote
     * console in the Open Session Request message
     */
    private byte messageTag;

    /**
     * Identifies the status of the previous message. If the previous message
     * generated an error, then only the Status Code, Reserved, and Remote
     * Console Session ID fields are returned.
     */
    private byte statusCode;

    /**
     * Indicates the Maximum Privilege Level allowed for the session.
     */
    private byte privilegeLevel;

    /**
     * The Remote Console Session ID specified by RMCP+ Open Session Request
     * message associated with this response.
     */
    private int remoteConsoleSessionId;

    /**
     * The Session ID selected by the Managed System for this new session.
     */
    private int managedSystemSessionId;

    /**
     * This payload defines the authentication algorithm proposal selected by
     * the Managed System to be used for this session
     */
    private byte authenticationAlgorithm;

    /**
     * This payload defines the integrity algorithm proposal selected by the
     * Managed System to be used for this session
     */
    private byte integrityAlgorithm;

    /**
     * This payload defines the confidentiality algorithm proposal selected by
     * the Managed System to be used for this session
     */
    private byte confidentialityAlgorithm;

    public void setMessageTag(byte messageTag) {
        this.messageTag = messageTag;
    }

    public byte getMessageTag() {
        return messageTag;
    }

    public void setStatusCode(byte statusCode) {
        this.statusCode = statusCode;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public void setPrivilegeLevel(byte privilegeLevel) {
        this.privilegeLevel = privilegeLevel;
    }

    public PrivilegeLevel getPrivilegeLevel() {
        switch (privilegeLevel) {
        case CommandsConstants.AL_CALLBACK:
            return PrivilegeLevel.Callback;
        case CommandsConstants.AL_USER:
            return PrivilegeLevel.User;
        case CommandsConstants.AL_OPERATOR:
            return PrivilegeLevel.Operator;
        case CommandsConstants.AL_ADMINISTRATOR:
            return PrivilegeLevel.Administrator;
        default:
            throw new IllegalArgumentException("Invalid privilege level");
        }
    }

    public void setRemoteConsoleSessionId(int remoteConsoleSessionId) {
        this.remoteConsoleSessionId = remoteConsoleSessionId;
    }

    public int getRemoteConsoleSessionId() {
        return remoteConsoleSessionId;
    }

    public void setManagedSystemSessionId(int managedSystemSessionId) {
        this.managedSystemSessionId = managedSystemSessionId;
    }

    public int getManagedSystemSessionId() {
        return managedSystemSessionId;
    }

    public void setAuthenticationAlgorithm(byte authenticationAlgorithm) {
        this.authenticationAlgorithm = authenticationAlgorithm;
    }

    public byte getAuthenticationAlgorithm() {
        return authenticationAlgorithm;
    }

    public void setIntegrityAlgorithm(byte integrityAlgorithm) {
        this.integrityAlgorithm = integrityAlgorithm;
    }

    public byte getIntegrityAlgorithm() {
        return integrityAlgorithm;
    }

    public void setConfidentialityAlgorithm(byte confidentialityAlgorithm) {
        this.confidentialityAlgorithm = confidentialityAlgorithm;
    }

    public byte getConfidentialityAlgorithm() {
        return confidentialityAlgorithm;
    }
}
