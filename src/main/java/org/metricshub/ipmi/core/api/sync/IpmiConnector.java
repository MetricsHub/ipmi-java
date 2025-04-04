package org.metricshub.ipmi.core.api.sync;

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

import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.async.InboundMessageListener;
import org.metricshub.ipmi.core.api.async.IpmiAsyncConnector;
import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilitiesResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.Constants;
import org.metricshub.ipmi.core.common.PropertiesManager;
import org.metricshub.ipmi.core.connection.Connection;
import org.metricshub.ipmi.core.connection.ConnectionException;
import org.metricshub.ipmi.core.connection.ConnectionManager;
import org.metricshub.ipmi.core.connection.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Random;

/**
 * <p> Synchronous API for connecting to BMC via IPMI. </p> <br>Creating connection consists of the following steps:
 * <ul>
 * <li>Create {@link Connection} and get associated with it {@link ConnectionHandle} via
 * {@link #createConnection(InetAddress)}</li> <li>Get {@link CipherSuite}s that are available for the connection via
 * {@link #getAvailableCipherSuites(ConnectionHandle)}</li> <li>Pick {@link CipherSuite} and {@link PrivilegeLevel} that will
 * be used during session and get {@link GetChannelAuthenticationCapabilitiesResponseData} to find out allowed
 * authentication options via
 * {@link #getChannelAuthenticationCapabilities(ConnectionHandle, CipherSuite, PrivilegeLevel)} </li><li>Provide username,
 * password and (if the BMC needs it) the BMC Kg key and start session via
 * {@link #openSession(ConnectionHandle, String, String, byte[])}  </li> 
 * </ul>
 * <br>
 * <p> Send message register via
 * {@link #sendMessage(ConnectionHandle, PayloadCoder)} </p> <br> <p> To close session call
 * {@link #closeSession(ConnectionHandle)} </p> <p> When done with work, clean up via {@link #tearDown()} </p> <br>
 */
public class IpmiConnector {

    private static Logger logger = LoggerFactory.getLogger(IpmiConnector.class);

    private IpmiAsyncConnector asyncConnector;

    private int retries;

    private int idleTime;

    private Random random = new Random(System.currentTimeMillis());

    /**
     * Starts {@link IpmiConnector} and initiates the {@link ConnectionManager} at the given port. Wildcard IP address
     * will be used.
     * @param port
     * - the port that will be used by {@link IpmiAsyncConnector} to communicate with the remote hosts.
     * @throws FileNotFoundException
     * when properties file was not found
     * @throws IOException
     * when properties file was not found
     */
    public IpmiConnector(int port) throws IOException {
        asyncConnector = new IpmiAsyncConnector(port);
        loadProperties();
    }

    /**
     * Starts {@link IpmiConnector} and initiates the {@link ConnectionManager} at the given port and IP interface.
     * @param port
     * - the port that will be used by {@link IpmiAsyncConnector} to communicate with the remote hosts.
     * @param address
     * - the IP address that will be used by {@link IpmiAsyncConnector} to communicate with the remote hosts.
     * @throws FileNotFoundException
     * when properties file was not found
     * @throws IOException
     * when properties file was not found
     */
    public IpmiConnector(int port, InetAddress address) throws IOException {
        asyncConnector = new IpmiAsyncConnector(port, address);
        loadProperties();
    }

	/**
	 * Starts {@link IpmiConnector} and initiates the {@link ConnectionManager} at
	 * the given port. Wildcard IP address will be used.
	 * 
	 * @param port       the port that will be used by {@link IpmiAsyncConnector} to
	 *                   communicate with the remote hosts.
	 * @param pingPeriod the period in milliseconds used to send the keep alive
	 *                   messages.<br>
	 *                   0 If keep-alive messages should be disabled.
	 * @throws IOException When IpmiAsyncConnector cannot be created.
	 */
	public IpmiConnector(int port, long pingPeriod) throws IOException {
		asyncConnector = new IpmiAsyncConnector(port, pingPeriod);
		loadProperties();
	}

	/**
	 * Loads properties from the properties file.
	 */
	private void loadProperties() {
		PropertiesManager manager = PropertiesManager.getInstance();
		retries = Integer.parseInt(manager.getProperty("retries"));
		idleTime = Integer.parseInt(manager.getProperty("idleTime"));
	}

    /**
     * Creates connection to the remote host on default IPMI port.
     * @param address
     * - {@link InetAddress} of the remote host
     * @return handle to the connection to the remote host
     * @throws IOException
     * when properties file was not found
     * @throws FileNotFoundException
     * when properties file was not found
     */
    public ConnectionHandle createConnection(InetAddress address) throws IOException {
        return createConnection(address, Constants.IPMI_PORT);
    }

    /**
     * Creates connection to the remote host on specified port.
     * @param address
     * - {@link InetAddress} of the remote host
     * @param port
     * - remote UDP port
     * @return handle to the connection to the remote host
     * @throws IOException
     * when properties file was not found
     * @throws FileNotFoundException
     * when properties file was not found
     */
    public ConnectionHandle createConnection(InetAddress address, int port) throws IOException {
        return asyncConnector.createConnection(address, port);
    }
    
    /**
     * Creates connection to the remote host, with pre set {@link CipherSuite} and {@link PrivilegeLevel}, skipping the
     * getAvailableCipherSuites and getChannelAuthenticationCapabilities phases.
     * @param address
     * - {@link InetAddress} of the remote host
     * @return handle to the connection to the remote host
     * @throws IOException
     * when properties file was not found
     * @throws FileNotFoundException
     * when properties file was not found
     */
    public ConnectionHandle createConnection(InetAddress address, CipherSuite cipherSuite, PrivilegeLevel privilegeLevel)
            throws IOException {        
        return createConnection(address, Constants.IPMI_PORT, cipherSuite, privilegeLevel);
    }

    /**
     * Creates connection to the remote host, with pre set {@link CipherSuite} and {@link PrivilegeLevel}, skipping the
     * getAvailableCipherSuites and getChannelAuthenticationCapabilities phases.
     * @param address
     * - {@link InetAddress} of the remote host
     * @param port
     * - remote UDP port
     * @return handle to the connection to the remote host
     * @throws IOException
     * when properties file was not found
     * @throws FileNotFoundException
     * when properties file was not found
     */
    public ConnectionHandle createConnection(InetAddress address, int port, CipherSuite cipherSuite, PrivilegeLevel privilegeLevel)
            throws IOException {
        return asyncConnector.createConnection(address, port, cipherSuite, privilegeLevel);
    }

    /**
     * Gets {@link CipherSuite}s available for the connection with the remote host.
     * @param connectionHandle
     * {@link ConnectionHandle} to the connection created before
     * @see #createConnection(InetAddress)
     * @return list of the {@link CipherSuite}s that are allowed during the connection
     * @throws Exception
     * when sending message to the managed system fails
     */
    public List<CipherSuite> getAvailableCipherSuites(ConnectionHandle connectionHandle) throws Exception {
        return asyncConnector.getAvailableCipherSuites(connectionHandle);
    }

    /**
     * Gets the authentication capabilities for the connection with the remote host.
     * @param connectionHandle
     * - {@link ConnectionHandle} associated with the host
     * @param cipherSuite
     * - {@link CipherSuite} that will be used during the connection
     * @param requestedPrivilegeLevel
     * - {@link PrivilegeLevel} that is requested for the session
     * @return - {@link GetChannelAuthenticationCapabilitiesResponseData}
     * @throws ConnectionException
     * when connection is in the state that does not allow to perform this operation.
     * @throws Exception
     * when sending message to the managed system fails
     */
    public GetChannelAuthenticationCapabilitiesResponseData getChannelAuthenticationCapabilities(
            ConnectionHandle connectionHandle, CipherSuite cipherSuite, PrivilegeLevel requestedPrivilegeLevel)
            throws Exception {
        return asyncConnector.getChannelAuthenticationCapabilities(connectionHandle, cipherSuite,
                requestedPrivilegeLevel);
    }

    /**
     * Establishes the session with the remote host.
     * @param connectionHandle
     * - {@link ConnectionHandle} associated with the remote host.
     * @param username
     * - the username
     * @param password
     * - password matching the username
     * @param bmcKey
     * - the key that should be provided if the two-key authentication is enabled, null otherwise.
     *
     * @return object representing newly created {@link Session}
     *
     * @throws ConnectionException
     * when connection is in the state that does not allow to perform this operation.
     * @throws Exception
     * when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
     */
    public Session openSession(ConnectionHandle connectionHandle, String username, String password, byte[] bmcKey)
            throws Exception {
        return asyncConnector.openSession(connectionHandle, username, password, bmcKey);
    }

    /**
     * Returns session already bound to given connection handle fulfilling given criteria.
     *
     * @param remoteAddress
     *          IP addres of the managed system
     * @param remotePort
     *          UDP port of the managed system
     * @param user
     *          IPMI user for whom the connection is established
     * @return session object fulfilling given criteria, or null if no session was registered for such connection.
     */
    public Session getExistingSessionForCriteria(InetAddress remoteAddress, int remotePort, String user) {
        return asyncConnector.getExistingSessionForCriteria(remoteAddress, remotePort, user);
    }

    /**
     * Closes the session with the remote host if it is currently in open state.
     * @param connectionHandle
     * - {@link ConnectionHandle} associated with the remote host.
     * @throws ConnectionException
     * when connection is in the state that does not allow to perform this operation.
     * @throws Exception
     * when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
     */
    public void closeSession(ConnectionHandle connectionHandle) throws Exception {
        asyncConnector.closeSession(connectionHandle);
    }

    /**
     * Sends the IPMI message to the remote host.
     * @param connectionHandle
     * - {@link ConnectionHandle} associated with the remote host.
     * @param request
     * - {@link PayloadCoder} containing the request to be sent
     * @return {@link ResponseData} for the <b>request</b>
     * @throws ConnectionException
     * when connection is in the state that does not allow to perform this operation.
     * @throws Exception
     * when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
     */
    public ResponseData sendMessage(ConnectionHandle connectionHandle, PayloadCoder request) throws Exception {
        return sendMessage(connectionHandle, request, true);
    }

    /**
     * Sends the IPMI message to the remote host and doesn't wait for any response.
     * @param connectionHandle
     * - {@link ConnectionHandle} associated with the remote host.
     * @param request
     * - {@link PayloadCoder} containing the request to be sent
     * @throws ConnectionException
     * when connection is in the state that does not allow to perform this operation.
     * @throws Exception
     * when sending message to the managed system or initializing one of the cipherSuite's algorithms fails
     */
    public void sendOneWayMessage(ConnectionHandle connectionHandle, PayloadCoder request) throws Exception {
        sendMessage(connectionHandle, request, false);
    }

    /**
     * Re-sends message with given tag having given {@link PayloadType}, using passed {@link ConnectionHandle}.
     *
     * @param connectionHandle
     *          - {@link ConnectionHandle} associated with the remote host.
     * @param tag
     *          - tag of the message to retry
     * @param messagePayloadType
     *             - {@link PayloadType} of the message that should be retried
     * @return {@link ResponseData} for the re-sent request or null if message could not be resent.
     * @throws Exception
     * when sending message to the managed system fails
     */
    public ResponseData retryMessage(ConnectionHandle connectionHandle, byte tag, PayloadType messagePayloadType) throws Exception {
        MessageListener listener = new MessageListener(connectionHandle);
        asyncConnector.registerListener(listener);

        int retryResult = asyncConnector.retry(connectionHandle, tag, messagePayloadType);

        ResponseData data = retryResult != -1 ? listener.waitForAnswer(tag) : null;

        asyncConnector.unregisterListener(listener);

        return data;
    }

    private ResponseData sendMessage(ConnectionHandle connectionHandle, PayloadCoder request, boolean waitForResponse) throws Exception {
        MessageListener listener = new MessageListener(connectionHandle);

        if (waitForResponse) {
            asyncConnector.registerListener(listener);
        }

        ResponseData data = sendThroughAsyncConnector(request, connectionHandle, listener, waitForResponse);

        if (waitForResponse) {
            asyncConnector.unregisterListener(listener);
        }

        return data;
    }

    private ResponseData sendThroughAsyncConnector(PayloadCoder request, ConnectionHandle connectionHandle,
                                                   MessageListener listener, boolean waitForResponse) throws Exception {
        ResponseData responseData = null;

        int tries = 0;
        int tag = -1;
        boolean messageSent = false;

        while (!messageSent) {
            try {
                ++tries;

                if (tag >= 0) {
                    tag = asyncConnector.retry(connectionHandle, tag, request.getSupportedPayloadType());
                }

                if (tag < 0){
                    tag = asyncConnector.sendMessage(connectionHandle, request, !waitForResponse);
                }

                logger.debug("Sending message with tag {}, try {}", tag, tries);

                if (waitForResponse) {
                    responseData = listener.waitForAnswer(tag);
                }

                messageSent = true;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (IPMIException e) {
                handleErrorResponse(tries, e);
            } catch (Exception e) {
                handleRetriesWhenException(tries, e);
            }
        }

        return responseData;
    }

    private void handleRetriesWhenException(int tries, Exception e) throws Exception {
        if (tries > retries) {
            throw e;
        } else {
            long sleepTime = (random.nextLong() % (idleTime / 2)) + (idleTime / 2);

            Thread.sleep(sleepTime);
            logger.warn("Receiving message failed, retrying", e);
        }
    }

    private void handleErrorResponse(int tries, IPMIException e) throws Exception {
        if (e.getCompletionCode() == CompletionCode.InitializationInProgress
                || e.getCompletionCode() == CompletionCode.InsufficientResources
                || e.getCompletionCode() == CompletionCode.NodeBusy
                || e.getCompletionCode() == CompletionCode.Timeout) {

            handleRetriesWhenException(tries, e);
        } else {
            throw e;
        }
    }

    /**
     * Registers {@link InboundMessageListener} that will react on any request sent from remote system to the application.
     *
     * @param listener
     *          listener to be registered.
     */
    public void registerIncomingMessageListener(InboundMessageListener listener) {
        asyncConnector.registerIncomingPayloadListener(listener);
    }

    /**
     * Closes the connection with the given handle
     */
    public void closeConnection(ConnectionHandle handle) {
        asyncConnector.closeConnection(handle);
    }

    /**
     * Finalizes the connector and closes all connections.
     */
    public void tearDown() {
        asyncConnector.tearDown();
    }

    /**
     * Changes the timeout value for connection with the given handle.
     * @param handle
     * - {@link ConnectionHandle} associated with the remote host.
     * @param timeout
     * - new timeout value in ms
     */
    public void setTimeout(ConnectionHandle handle, int timeout) {
        asyncConnector.setTimeout(handle, timeout);
    }

    /**
     * Returns configured number of retries.
     *
     * @return number of retries when message could not be sent
     */
    public int getRetries() {
        return retries;
    }
}
