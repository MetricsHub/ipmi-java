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

import java.util.Collection;

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;

/**
 * A wrapper for Get Channel Authentication Capabilities command response
 * @see GetChannelAuthenticationCapabilities
 */
public class GetChannelAuthenticationCapabilitiesResponseData implements ResponseData {
    /**
     * Channel number that the Authentication Capabilities is being returned for.
     */
    private byte channelNumber;

    /**
     * IPMI v2.0 support.
     */
    private boolean ipmiv20Support;

    /**
     * Authentication Types supported for requested privilege level.
     */
    private Collection<AuthenticationType> authenticationTypes;

    /**
     * BMC key used for authentication. If false, then BMC uses user key. 
     */
    private boolean kgEnabled;

    /**
     * If Mer-message Authentication is enabled, packets to the BMC must be authenticated per Authentication Type used
     * to activate the session, and User Level Authentication setting, following. Otherwise, Authentication Type 'None'
     * accepted for packets to the BMC after the session has been activated.
     */
    private boolean perMessageAuthenticationEnabled;

    /**
     * If User Level Authentication is enabled, User Level commands must be authenticated per Authentication Type used
     * to activate the session. Otherwise, Authentication Type 'none' accepted for User Level commands to the BMC.
     */
    private boolean userLevelAuthenticationEnabled;

    /**
     * One or more users are enabled that have non-null usernames
     */
    private boolean nonNullUsernamesEnabled;

    /**
     * One or more users that have a null username, but non-null password, are presently enabled
     */
    private boolean nullUsernamesEnabled;

    /**
     * A user that has a null username and null password is presently enabled
     */
    private boolean anonymusLoginEnabled;

    /**
     * IANA Enterprise Number for OEM/Organization that specified the particular OEM Authentication Type for RMCP.
     */
    private int oemId;

    /**
     * Additional OEM-specific information for the OEM Authentication Type for RMCP.
     */
    private byte oemData;

    public void setChannelNumber(byte channelNumber) {
        this.channelNumber = channelNumber;
    }

    public byte getChannelNumber() {
        return channelNumber;
    }

    public void setIpmiv20Support(boolean ipmiv20Support) {
        this.ipmiv20Support = ipmiv20Support;
    }

    public boolean isIpmiv20Support() {
        return ipmiv20Support;
    }

    public void setAuthenticationTypes(Collection<AuthenticationType> authenticationTypes) {
        this.authenticationTypes = authenticationTypes;
    }

    public Collection<AuthenticationType> getAuthenticationTypes() {
        return authenticationTypes;
    }

    public void setKgEnabled(boolean kgEnabled) {
        this.kgEnabled = kgEnabled;
    }

    public boolean isKgEnabled() {
        return kgEnabled;
    }

    public void setPerMessageAuthenticationEnabled(boolean perMessageAuthenticationEnabled) {
        this.perMessageAuthenticationEnabled = perMessageAuthenticationEnabled;
    }

    public boolean isPerMessageAuthenticationEnabled() {
        return perMessageAuthenticationEnabled;
    }

    public void setUserLevelAuthenticationEnabled(boolean userLevelAuthenticationEnabled) {
        this.userLevelAuthenticationEnabled = userLevelAuthenticationEnabled;
    }

    public boolean isUserLevelAuthenticationEnabled() {
        return userLevelAuthenticationEnabled;
    }

    public void setNonNullUsernamesEnabled(boolean nonNullUsernamesEnabled) {
        this.nonNullUsernamesEnabled = nonNullUsernamesEnabled;
    }

    public boolean isNonNullUsernamesEnabled() {
        return nonNullUsernamesEnabled;
    }

    public void setNullUsernamesEnabled(boolean nullUsernamesEnabled) {
        this.nullUsernamesEnabled = nullUsernamesEnabled;
    }

    public boolean isNullUsernamesEnabled() {
        return nullUsernamesEnabled;
    }

    public void setAnonymusLoginEnabled(boolean anonymusLoginEnabled) {
        this.anonymusLoginEnabled = anonymusLoginEnabled;
    }

    public boolean isAnonymusLoginEnabled() {
        return anonymusLoginEnabled;
    }

    public void setOemId(int oemId) {
        this.oemId = oemId;
    }

    public int getOemId() {
        return oemId;
    }

    public void setOemData(byte oemData) {
        this.oemData = oemData;
    }

    public byte getOemData() {
        return oemData;
    }
}
