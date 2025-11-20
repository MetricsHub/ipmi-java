package org.metricshub.ipmi.client.runner;

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

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Callable;

import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdr;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;
import org.metricshub.ipmi.core.connection.Connection;

/**
 * This abstract class implements common features required by FRUs, Sensor and Chassis Status runners.
 * 
 * @param <T> Represent the data type managed by the runner 
 */
public abstract class AbstractIpmiRunner<T> implements AutoCloseable, Callable<T> {

	private static final int DEFAULT_LOCAL_UDP_PORT = 0;

	/**
	 * This is the value of Last Record ID (FFFFh). In order to retrieve the full set of SDR records, client must repeat reading SDR records
	 * until MAX_REPO_RECORD_ID is returned as next record ID. For further information see section 33.12 of the IPMI specification ver. 2.0
	 */
	protected static final int MAX_REPO_RECORD_ID =  65535; 

	/**
	 * Size of the initial GetSdr message to get record header and size
	 */
	protected static final int INITIAL_CHUNK_SIZE = 8;

	/**
	 * Chunk size depending on buffer size of the IPMI server. Bigger values will improve performance. If server is returning "Cannot return
	 * number of requested data bytes." error during GetSdr command, CHUNK_SIZE should be decreased.
	 */
	protected static final int CHUNK_SIZE = 16;

	/**
	 * Size of SDR record header
	 */
	protected static final int HEADER_SIZE = 5;

	protected IpmiClientConfiguration ipmiConfiguration;


	protected IpmiConnector connector;
	protected ConnectionHandle handle;

	protected int nextRecId;

	protected AbstractIpmiRunner(IpmiClientConfiguration ipmiConfiguration) {
		this.ipmiConfiguration = ipmiConfiguration;
	}

	/**
	 * Create the {@link IpmiConnector} instance, perform the authentication if required then start the session. <br>
	 * This method will instantiate the internal fields: <em></em>
	 * 
	 * @throws Exception If an error occurs when starting the session
	 */
	protected void startSession() throws Exception {
		// Create the connector, specify port that will be used to communicate
		// with the remote host. The UDP layer starts listening at this port, so
		// no 2 connectors can work at the same time on the same port.
		connector = new IpmiConnector(DEFAULT_LOCAL_UDP_PORT, ipmiConfiguration.getPingPeriod());

		// Should we perform the authentication
		if (!ipmiConfiguration.isSkipAuth()) {
			authenticate();
		} else {
			handle = connector.createConnection(InetAddress.getByName(ipmiConfiguration.getHostname()),
					ipmiConfiguration.getPort(), Connection.getDefaultCipherSuite(), PrivilegeLevel.User);
		}

		// Start the session, provide user name and password, and optionally the
		// BMC key (only if the remote host has two-key authentication enabled,
		// otherwise this parameter should be null)
		connector.openSession(handle, ipmiConfiguration.getUsername(),
				String.valueOf(ipmiConfiguration.getPassword()), ipmiConfiguration.getBmcKey());
	}

	/**
	 * Authenticate IPMI
	 * 
	 * @throws Exception If the authentication fails
	 */
	public void authenticate() throws Exception  {
		// Create the connection and get the handle, specify IP address of the
		// remote host. The connection is being registered in ConnectionManager,
		// the handle will be needed to identify it among other connections
		// (target IP address isn't enough, since we can handle multiple
		// connections to the same host)
		handle = connector.createConnection(InetAddress.getByName(ipmiConfiguration.getHostname()),
				ipmiConfiguration.getPort());

		// Get available cipher suites list via getAvailableCipherSuites and
		// pick one of them that will be used further in the session.
		CipherSuite cs = getAvailableCipherSuite();

		// Provide chosen cipher suite and privilege level to the remote host.
		// From now on, your connection handle will contain these information.
		connector.getChannelAuthenticationCapabilities(handle, cs, PrivilegeLevel.User);
	}

	/**
	 * Get the available cipher suite. Get the last available if many cipher suites coexist.<br>
	 * 
	 * @return {@link CipherSuite} instance
	 * @throws Exception when sending message to the managed system fails or suites not found
	 */
	protected CipherSuite getAvailableCipherSuite() throws Exception {
	
		// Get cipher suites supported by the remote host
		List<CipherSuite> suites = connector.getAvailableCipherSuites(handle);

		if (suites == null || suites.isEmpty()) {
			throw new Exception("Cannot get the available cipher suites.");
		}

		// Return the cipher suite based on the available suites length
		if (suites.size() > 3) {
			return suites.get(3);
		} else if (suites.size() > 2) {
			return suites.get(2);
		} else if (suites.size() > 1) {
			return suites.get(1);
		}

		return suites.get(0);
	}

	@Override
	public void close() {
		if (handle != null) {
			// Close the session
			try {
				connector.closeSession(handle);
			} catch (Exception e) {
				// Ignore
			}
		}

		// Close connection manager and release the listener port.
		connector.tearDown();
	}

	/**
	 * Using the reservation id, get the {@link SensorRecord} instance by running a GetSdr IPMI request.<br>
	 * When the {@link SensorRecord} cannot be fetched using one request we try a second method, see <em>getSensorViaChunks</em>
	 * 
	 * @param reservationId The reservation identifier that needs to be sent to the BMC so that it handles correctly the request
	 * @return {@link SensorRecord} instance
	 * @throws Exception at sendMessage or if the error completion code is CannotRespond or UnspecifiedError
	 */
	protected SensorRecord getSensorData(int reservationId) throws Exception {
		try {
			// BMC capabilities are limited - that means that sometimes the
			// record size exceeds maximum size of the message. Since we don't
			// know what is the size of the record, we try to get whole one first
			GetSdrResponseData data = (GetSdrResponseData) connector.sendMessage(handle,
					new GetSdr(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus, reservationId, nextRecId));

			// If getting whole record succeeded we create SensorRecord from
			// received data...
			SensorRecord sensorDataToPopulate = SensorRecord.populateSensorRecord(data.getSensorRecordData());

			// ... and update the ID of the next record
			nextRecId = data.getNextRecordId();
			return sensorDataToPopulate;

		} catch (IPMIException e) {

			// The following error codes mean that record is too large to be
			// sent in one chunk. This means we need to split the data in
			// smaller parts.
			if (e.getCompletionCode() != CompletionCode.CannotRespond && e.getCompletionCode() != CompletionCode.UnspecifiedError) {
				throw e;
			}

			return getSensorViaChunks(reservationId);

		} catch (Exception e) {
			throw e;
		}
	}

	/**
	 * Get SDR (sensor data record) by chunks of {@link #CHUNK_SIZE} bytes. We get the full record size from the first request, then
	 * we query the IPMI interface to get the remaining parts.
	 * 
	 * @param reservationId The reservation identifier that needs to be sent to the BMC so that it handles correctly the request
	 * @return {@link SensorRecord} instance
	 * @throws Exception if one of the sendMessage calls fails
	 */
	protected SensorRecord getSensorViaChunks(int reservationId) throws Exception {
		// First we get the header of the record to find out its size.
		GetSdrResponseData data = (GetSdrResponseData) connector.sendMessage(handle, new GetSdr(IpmiVersion.V20, handle.getCipherSuite(),
				AuthenticationType.RMCPPlus, reservationId, nextRecId, 0, INITIAL_CHUNK_SIZE));

		// The record size is 5th byte of the record. It does not take
		// into account the size of the header, so we need to add it.
		int recSize = TypeConverter.byteToInt(data.getSensorRecordData()[4]) + HEADER_SIZE;
		int read = INITIAL_CHUNK_SIZE;

		byte[] bytes = new byte[recSize];

		System.arraycopy(data.getSensorRecordData(), 0, bytes, 0, data.getSensorRecordData().length);

		// We get the rest of the record in chunks (watch out for
		// exceeding the record size, since this will result in BMC's
		// error.
		while (read < recSize) {

			int bytesToRead = CHUNK_SIZE;
			if (recSize - read < bytesToRead) {
				bytesToRead = recSize - read;
			}

			GetSdrResponseData part = (GetSdrResponseData) connector.sendMessage(handle, new GetSdr(IpmiVersion.V20, handle.getCipherSuite(),
					AuthenticationType.RMCPPlus, reservationId, nextRecId, read, bytesToRead));

			// Append the new bytes
			System.arraycopy(part.getSensorRecordData(), 0, bytes, read, bytesToRead);

			read += bytesToRead;
		}

		// Finally we populate the sensor record with the gathered
		// data...
		SensorRecord sensorDataToPopulate = SensorRecord.populateSensorRecord(bytes);

		// ... and update the ID of the next record
		nextRecId = data.getNextRecordId();

		return sensorDataToPopulate;
	}
}
