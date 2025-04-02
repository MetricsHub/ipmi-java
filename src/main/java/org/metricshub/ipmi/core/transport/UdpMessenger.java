package org.metricshub.ipmi.core.transport;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the UDP connection.
 */
public class UdpMessenger extends Thread implements Messenger {

    private int port;

    private DatagramSocket socket;

    private List<UdpListener> listeners;

    private boolean closing = false;

    private static final String DEFAULT_ADDRESS = "0.0.0.0";

    /**
     * Size of the message data buffer. Default
     * {@link UdpMessenger#DEFAULTBUFFERSIZE}.
     */
    private int bufferSize;

    private static final int DEFAULTBUFFERSIZE = 512;

    private static Logger logger = LoggerFactory.getLogger(UdpMessenger.class);

    public int getPort() {
        return port;
    }

    /**
     * Initiates UdpMessenger, binds it to the specified port and starts
     * listening. Wildcard IP address will be used.
     *
     * @param port
     *            - port to bind socket to.
     * @throws SocketException
     *             if the socket could not be opened, or the socket could not
     *             bind to the specified local port.
     * @throws UnknownHostException
     */
    public UdpMessenger(int port) throws SocketException, UnknownHostException {
        this(port, InetAddress.getByName(DEFAULT_ADDRESS));
    }

    /**
     * Initiates UdpMessenger, binds it to the specified port and IP address and
     * starts listening.
     *
     * @param port
     *            - port to bind socket to.
     * @param address
     *            - IP address to bind socket to.
     * @throws SocketException
     *             if the socket could not be opened, or the socket could not
     *             bind to the specified local port.
     */
    public UdpMessenger(int port, InetAddress address) throws SocketException {
        sentPackets = 0;
        this.port = port;
        listeners = new ArrayList<UdpListener>();
        bufferSize = DEFAULTBUFFERSIZE;
        socket = new DatagramSocket(this.port, address);
        socket.setSoTimeout(0);
        this.start();
    }

    /**
     * Sets message data buffer size to bufferSize.
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * @return Size of the message data buffer
     */
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public void run() {
        super.run();

        boolean run = true;

        while (run) {
            DatagramPacket response = new DatagramPacket(new byte[512], 512);

            try {
                socket.receive(response);
                UdpMessage message = new UdpMessage();
                message.setAddress(response.getAddress());
                message.setPort(response.getPort());
                byte[] buffer = new byte[response.getLength()];
                System.arraycopy(response.getData(), 0, buffer, 0,
                        buffer.length);
                message.setMessage(buffer);

                notifyListeners(message);

            } catch (SocketException se) {
                if (closing) {
                    run = false;
                } else {
                    logger.error(se.getMessage(), se);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                if (socket.isClosed()) {
                    run = false;
                }
            }
        }
    }

    private void notifyListeners(UdpMessage message) {
        synchronized (listeners) {
            for (UdpListener listener : listeners) {
                if (listener != null) {
                    listener.notifyMessage(message);
                }
            }
        }
    }

    /**
     * Closes the socket and releases port.
     */
    public void closeConnection() {
        closing = true;
        socket.close();
    }

    /**
     * Registers listener in the UdpMessenger so it will be notified via
     * {@link UdpListener#notifyMessage(UdpMessage)} when new message arrives.
     *
     * @param listener
     *            - {@link UdpListener} to register.
     */
    public void register(UdpListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters listener from UdpMessenger so it no longer will be notified.
     *
     * @param listener
     *            - {@link UdpListener} to unregister
     */
    public void unregister(UdpListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private static int sentPackets = 0;

    /**
     * Returns number of packets sent since last creation of the instance of
     * {@link UdpMessenger}. For debug/testing purposes only.
     */
    public static int getSentPackets() {
        return sentPackets;
    }

    /**
     * Sends {@link UdpMessage}.
     *
     * @param message
     *            - {@link UdpMessage} to send.
     * @throws IOException
     *             when sending of the message fails
     */
    public synchronized void send(UdpMessage message) throws IOException {
        DatagramPacket packet = new DatagramPacket(message.getMessage(),
                message.getMessage().length, message.getAddress(),
                message.getPort());
        socket.send(packet);
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            // TODO: log
        }
        ++sentPackets;
    }
}
