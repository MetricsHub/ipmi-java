package org.metricshub.ipmi.client;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilitiesResponseData;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * What the BMC supports before any login (Get Channel Cipher Suites and Get Channel Authentication Capabilities,
 * IPMI 2.0 sections 22.15 and 22.13): the cipher suites it offers and its authentication options.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The decoded reply of the protocol core is handed out "
		+ "by reference for the callers that need its every field")
public final class BmcCapabilities {

	private final GetChannelAuthenticationCapabilitiesResponseData data;
	private final List<CipherSuite> cipherSuites;

	/**
	 * Creates the capabilities from the replies of the BMC.
	 *
	 * @param data the reply to Get Channel Authentication Capabilities
	 * @param cipherSuites the suites the BMC offers, from Get Channel Cipher Suites
	 */
	BmcCapabilities(GetChannelAuthenticationCapabilitiesResponseData data, List<CipherSuite> cipherSuites) {
		this.data = data;
		this.cipherSuites = Collections.unmodifiableList(new ArrayList<CipherSuite>(cipherSuites));
	}

	/**
	 * Returns the standard cipher suites (0 to 19) the BMC offers, with their algorithms; the OEM suites and reserved
	 * suite IDs it lists are left out. {@link CipherSuite#isSupported()} tells whether the library implements the
	 * algorithms of a suite.
	 *
	 * @return the suites, in the order the BMC lists them
	 */
	public List<CipherSuite> cipherSuites() {
		return cipherSuites;
	}

	/**
	 * Tells whether the BMC supports IPMI 2.0 (RMCP+) sessions: always true, as {@link IpmiClient#capabilities()}
	 * fails with {@link IpmiClientException} for a BMC without them.
	 *
	 * @return true when IPMI 2.0 sessions are supported
	 */
	public boolean ipmi20() {
		return data.isIpmiv20Support();
	}

	/**
	 * Tells whether the BMC requires the two-key login: the BMC key (Kg) is set and must be given with the password.
	 *
	 * @return true when the BMC key is required
	 */
	public boolean bmcKeyRequired() {
		return data.isKgEnabled();
	}

	/**
	 * Tells whether the BMC accepts the anonymous login (null user name and null password).
	 *
	 * @return true when the anonymous login is enabled
	 */
	public boolean anonymousLogin() {
		return data.isAnonymusLoginEnabled();
	}

	/**
	 * Tells whether the BMC accepts a null user name with a non-null password.
	 *
	 * @return true when null user names are enabled
	 */
	public boolean nullUserNames() {
		return data.isNullUsernamesEnabled();
	}

	/**
	 * Tells whether the BMC has at least one user with a non-null user name.
	 *
	 * @return true when non-null user names are enabled
	 */
	public boolean nonNullUserNames() {
		return data.isNonNullUsernamesEnabled();
	}

	/**
	 * Tells whether the BMC authenticates every message of a session, rather than the login only.
	 *
	 * @return true when per-message authentication is enabled
	 */
	public boolean perMessageAuthentication() {
		return data.isPerMessageAuthenticationEnabled();
	}

	/**
	 * Tells whether the BMC authenticates the commands of the User privilege level.
	 *
	 * @return true when user-level authentication is enabled
	 */
	public boolean userLevelAuthentication() {
		return data.isUserLevelAuthenticationEnabled();
	}

	/**
	 * Returns the reply of the BMC, as the protocol core decodes it.
	 *
	 * @return the reply to Get Channel Authentication Capabilities
	 */
	public GetChannelAuthenticationCapabilitiesResponseData data() {
		return data;
	}

	@Override
	public String toString() {
		StringBuilder suites = new StringBuilder();
		for (CipherSuite suite : cipherSuites) {
			suites.append(suites.length() == 0 ? "" : ", ").append(suite.getId());
		}
		return "IPMI 2.0 " + (ipmi20() ? "supported" : "not supported") + ", BMC key "
				+ (bmcKeyRequired() ? "required" : "not required") + ", anonymous login "
				+ (anonymousLogin() ? "enabled" : "disabled") + ", cipher suites " + suites;
	}
}
