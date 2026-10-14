package org.metricshub.ipmi.core.connection.queue;

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

import org.metricshub.ipmi.core.coding.PayloadCoder;

import java.util.Date;

public class QueueElement {
	private final int id;
	private PayloadCoder request;
	private Date timestamp;
	private final boolean oneWay;

	/**
	 * @param id the tag of the request
	 * @param request the request awaiting its reply
	 * @param oneWay true when nobody waits for the reply: the reply and the timeout of the request are not reported
	 */
	public QueueElement(int id, PayloadCoder request, boolean oneWay) {
		this.id = id;
		this.request = request;
		this.oneWay = oneWay;
		timestamp = new Date();
	}

	/**
	 * Restarts the timeout of the request, which is sent again.
	 */
	void resetTimestamp() {
		timestamp = new Date();
	}

	/**
	 * @return true when nobody waits for the reply: the element only reserves the tag until the reply or the timeout
	 */
	public boolean isOneWay() {
		return oneWay;
	}

	public int getId() {
		return id;
	}

	public PayloadCoder getRequest() {
		return request;
	}

	public void setRequest(PayloadCoder request) {
		this.request = request;
	}

	public Date getTimestamp() {
		return timestamp;
	}
}
