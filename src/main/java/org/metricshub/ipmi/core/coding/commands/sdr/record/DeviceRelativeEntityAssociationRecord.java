package org.metricshub.ipmi.core.coding.commands.sdr.record;

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
 * Device-relative Entity Association record, SDR type 09h (IPMI 2.0 section 43.5): the same as the
 * {@link EntityAssociationRecord}, except that it supports describing associations between entities that have
 * device-relative Entity Instance values as well as system-relative values.
 * <p>
 * The SDR walk returns this class for type 09h. It extends the misspelled {@link DeviceRelativeEntityAssiciationRecord}
 * so that the {@code instanceof} checks of code built against 2.0 keep matching; in 3.0, the code moves here and the
 * old class goes.
 */
@SuppressWarnings("deprecation")
public class DeviceRelativeEntityAssociationRecord extends DeviceRelativeEntityAssiciationRecord {}
