/**
 * The client of a BMC. Build an {@link org.metricshub.ipmi.client.IpmiClient} with
 * {@link org.metricshub.ipmi.client.IpmiClient#builder(String)}, then call its operations: {@code capabilities()},
 * {@code deviceId()}, {@code chassisStatus()}, {@code chassisControl()}, {@code chassisIdentify()},
 * {@code sensors()}, {@code frus()}, {@code sel()} and {@code sol()}, or {@code send()} and {@code raw()} for any
 * other command. They return typed results ({@link org.metricshub.ipmi.client.Sensor},
 * {@link org.metricshub.ipmi.client.Fru}, {@link org.metricshub.ipmi.client.SelEntry}...) and report the failures of
 * the BMC and of the network as unchecked {@link org.metricshub.ipmi.client.IpmiClientException}s, whose subclasses
 * say what to act on.
 * <p>
 * User guide: <a href="https://metricshub.org/ipmi-java/">https://metricshub.org/ipmi-java/</a>
 * </p>
 */
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
