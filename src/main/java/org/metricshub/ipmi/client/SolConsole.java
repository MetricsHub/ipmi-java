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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import org.metricshub.ipmi.core.api.sol.SerialOverLan;
import org.metricshub.ipmi.core.api.sol.SolEventListener;
import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;
import org.metricshub.ipmi.core.coding.payload.sol.SolStatus;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A Serial over LAN console, as streams: what the system writes on its serial port is read from {@link #input()},
 * what is written to {@link #output()} reaches its serial port. Closing the console deactivates the SOL payload; the
 * session of the client stays open.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The streams are the console: handing them out is the point")
public final class SolConsole implements AutoCloseable {

	/** How long a blocking read waits for characters before checking whether the console is still open. */
	private static final int READ_POLL_MS = 200;

	private final SerialOverLan sol;
	private final InputStream input;
	private final OutputStream output;
	private volatile boolean deactivated;
	private volatile boolean closed;

	/**
	 * Wraps an activated console.
	 *
	 * @param sol the console of the protocol core
	 */
	SolConsole(SerialOverLan sol) {
		this.sol = sol;
		this.input = new ConsoleInput();
		this.output = new ConsoleOutput();
		sol.registerEventListener(new SolEventListener() {
			@Override
			public void processRequestEvent(Set<SolStatus> statuses) {
				if (statuses.contains(SolStatus.SolDeactivated)) {
					deactivated = true;
				}
			}

			@Override
			public void processResponseEvent(
					Set<SolStatus> statuses,
					byte[] correspondingRequestData,
					Set<SolOperation> correspondingRequestOperations) {
				if (statuses.contains(SolStatus.SolDeactivated)) {
					deactivated = true;
				}
			}
		});
	}

	/**
	 * Returns the characters the system writes on its serial port. A read blocks until characters arrive, and
	 * returns -1 once the console is closed or the BMC deactivated it.
	 *
	 * @return the input stream of the console
	 */
	public InputStream input() {
		return input;
	}

	/**
	 * Returns the stream whose bytes reach the serial port of the system. A write returns once the BMC acknowledged
	 * the characters, and fails with an {@link IOException} when it does not.
	 *
	 * @return the output stream of the console
	 */
	public OutputStream output() {
		return output;
	}

	/**
	 * Tells whether the BMC deactivated the console: the session ended, another console took the payload, or the
	 * system reset the serial port.
	 *
	 * @return true once the BMC reported the SOL payload deactivated
	 */
	public boolean deactivated() {
		return deactivated;
	}

	/**
	 * Sends the given operations to the serial port of the system: a break, a flush, the state of the control lines
	 * (IPMI 2.0 table 15-3).
	 *
	 * @param operations the operations
	 * @return whether the packet was sent
	 */
	public boolean invokeOperations(SolOperation... operations) {
		return sol.invokeOperations(operations);
	}

	/**
	 * Registers a listener for the status events of the console (break, deactivation, overrun, control lines).
	 *
	 * @param listener the listener
	 */
	public void registerEventListener(SolEventListener listener) {
		sol.registerEventListener(listener);
	}

	/**
	 * Unregisters a listener.
	 *
	 * @param listener the listener
	 */
	public void unregisterEventListener(SolEventListener listener) {
		sol.unregisterEventListener(listener);
	}

	/**
	 * Marks the console deactivated: the session it ran in is gone, so no character can arrive any more.
	 */
	void invalidate() {
		deactivated = true;
	}

	/**
	 * Deactivates the SOL payload and releases the console. Closing a closed console does nothing.
	 */
	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		try {
			sol.close();
		} catch (IOException e) {
			// The payload was not deactivated: the BMC frees it with the session, or when the next console takes it
			deactivated = true;
		}
	}

	private final class ConsoleInput extends InputStream {

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			int count = read(one, 0, 1);
			return count < 0 ? -1 : one[0] & 0xff;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if (length == 0) {
				return 0;
			}
			while (true) {
				byte[] bytes = sol.readBytes(length, READ_POLL_MS);
				if (bytes != null && bytes.length > 0) {
					System.arraycopy(bytes, 0, buffer, offset, bytes.length);
					return bytes.length;
				}
				if (closed || deactivated) {
					return -1;
				}
				if (Thread.currentThread().isInterrupted()) {
					throw new IOException("Interrupted while reading the console");
				}
			}
		}
	}

	private final class ConsoleOutput extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			write(new byte[] { (byte) b }, 0, 1);
		}

		@Override
		public void write(byte[] buffer, int offset, int length) throws IOException {
			if (closed) {
				throw new IOException("The console is closed");
			}
			byte[] bytes = new byte[length];
			System.arraycopy(buffer, offset, bytes, 0, length);
			if (!sol.writeBytes(bytes)) {
				throw new IOException("The BMC did not acknowledge the characters");
			}
		}
	}
}
