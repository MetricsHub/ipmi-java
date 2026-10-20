/**
 * The command-line client of the library, {@code ipmicli} ({@link org.metricshub.ipmi.cli.IpmiCli}):
 * {@code ipmicli [options] <subcommand> [subcommand options]}, installed with a launcher by the installer, or run with
 * {@code java -jar ipmi-java-<version>-standalone.jar}. Every subcommand is a thin layer over one operation of
 * {@link org.metricshub.ipmi.client.IpmiClient}. See the
 * <a href="https://metricshub.org/ipmi-java/cli.html">manual</a>.
 */
package org.metricshub.ipmi.cli;

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
