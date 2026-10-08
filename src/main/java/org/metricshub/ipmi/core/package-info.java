/**
 * The library for communicating with server via IPMI protocol. <br>
 * The API is included in {@link org.metricshub.ipmi.core.api}
 *
 * @see org.metricshub.ipmi.core.api
 */
// Applies to all the subpackages. EI_EXPOSE_REP also matches EI_EXPOSE_REP2
@SuppressFBWarnings(value = { "CT_CONSTRUCTOR_THROW", "EI_EXPOSE_REP" }, justification = "CT_CONSTRUCTOR_THROW: "
		+ "constructors reject invalid arguments and packets, and no class guards security-sensitive state that a "
		+ "finalizer attack could exploit. EI_EXPOSE_REP: the protocol core shares buffers, records and "
		+ "collaborators by reference by design; messages are built and decoded in place, and response data and "
		+ "records are mutable holders with public setters, so a defensive copy would add allocations without "
		+ "protecting any invariant")
package org.metricshub.ipmi.core;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
