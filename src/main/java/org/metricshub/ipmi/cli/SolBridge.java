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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.metricshub.ipmi.client.SolConsole;
import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;

/**
 * Bridges a {@link SolConsole} with the standard streams: the bytes of the BMC go to stdout unmodified, the bytes of
 * stdin go to the BMC, with the escape sequences of {@code ssh} recognized after a newline ({@code ~.} closes the
 * console, {@code ~B} sends a break, {@code ~?} lists the sequences, {@code ~~} sends the escape character).
 */
final class SolBridge {

	/** The help printed by {@code ~?}, with the escape character substituted. */
	static final String ESCAPE_HELP = "Supported escape sequences (after a newline):\n" + "  ~.  close the console\n"
			+ "  ~B  send a serial break\n" + "  ~?  print this help\n" + "  ~~  send the escape character itself\n";

	private static final int CLOSED = 0;
	private static final int DEACTIVATED = 1;
	private static final int OUTPUT_FAILED = 2;
	private static final int INPUT_FAILED = 3;
	private static final int INPUT_READ_FAILED = 4;
	private static final int BUFFER_SIZE = 4096;
	private static final long NANOS_PER_MILLI = 1_000_000L;

	private SolBridge() {}

	/**
	 * Runs the console until the user closes it ({@code ~.}), the input ends, the BMC deactivates it, or a stream fails.
	 * The console is closed before returning.
	 *
	 * @param console the open console
	 * @param stdin the local input
	 * @param out the local output
	 * @param err where the escape help and the diagnostics go
	 * @param escape the escape character, {@code -1} for none
	 * @param cooked whether the input is a line-oriented terminal, whose Enter (CR LF, or a lone LF) is sent as CR
	 * @param verbose whether to print, at the end, the packets and bytes exchanged and the mean acknowledgement time
	 * @return the exit code of the CLI
	 * @throws InterruptedException when interrupted while waiting for the end
	 */
	static int run(
			SolConsole console,
			InputStream stdin,
			PrintStream out,
			PrintStream err,
			int escape,
			boolean cooked,
			boolean verbose)
			throws InterruptedException {
		BlockingQueue<Integer> outcome = new LinkedBlockingQueue<Integer>();
		IOException[] failure = new IOException[1];
		Stats stats = new Stats();
		daemon("ipmicli-sol-output", () -> outcome.add(pumpOutput(console, out, stats)));
		daemon("ipmicli-sol-input", () -> outcome.add(pumpInput(console, stdin, err, escape, cooked, failure, stats)));
		int first = outcome.take();
		IOException readFailure = failure[0];
		long closing = System.nanoTime();
		console.close();
		if (verbose) {
			long packets = stats.packets.get();
			IpmiCli
					.diagnostic(
							err,
							"SOL console closed in " + (System.nanoTime() - closing) / NANOS_PER_MILLI + " ms: " + packets
									+ " packet(s) and " + stats.bytesSent.get() + " byte(s) sent, acknowledged in "
									+ (packets == 0 ? 0 : stats.ackNanos.get() / packets / NANOS_PER_MILLI) + " ms on average, "
									+ stats.bytesReceived.get() + " byte(s) received");
		}
		switch (first) {
		case DEACTIVATED:
			IpmiCli.diagnostic(err, "the console was deactivated by the BMC");
			return IpmiCli.EXIT_PROTOCOL;
		case OUTPUT_FAILED:
			IpmiCli.diagnostic(err, "cannot write to standard output");
			return IpmiCli.EXIT_IO;
		case INPUT_FAILED:
			IpmiCli.diagnostic(err, "the BMC did not accept the input");
			return IpmiCli.EXIT_PROTOCOL;
		case INPUT_READ_FAILED:
			IpmiCli.diagnostic(err, "cannot read the input" + (readFailure == null ? "" : ": " + readFailure.getMessage()));
			return IpmiCli.EXIT_IO;
		default:
			return 0;
		}
	}

	private static void daemon(String name, Runnable task) {
		Thread thread = new Thread(task, name);
		thread.setDaemon(true);
		thread.start();
	}

	private static int pumpOutput(SolConsole console, PrintStream out, Stats stats) {
		byte[] buffer = new byte[BUFFER_SIZE];
		try {
			InputStream input = console.input();
			while (true) {
				int count = input.read(buffer);
				if (count < 0) {
					break;
				}
				stats.bytesReceived.addAndGet(count);
				out.write(buffer, 0, count);
				out.flush();
				if (out.checkError()) {
					return OUTPUT_FAILED;
				}
			}
		} catch (IOException e) {
			// The read was interrupted: the console is over either way
		}
		return console.deactivated() ? DEACTIVATED : CLOSED;
	}

	private static int pumpInput(
			SolConsole console,
			InputStream stdin,
			PrintStream err,
			int escape,
			boolean cooked,
			IOException[] failure,
			Stats stats) {
		OutputStream toBmc = console.output();
		ByteArrayOutputStream pending = new ByteArrayOutputStream();
		byte[] buffer = new byte[BUFFER_SIZE];
		boolean atLineStart = true;
		boolean escapePending = false;
		boolean afterCr = false;
		// In line mode, a local sequence (~? ~B) is typed with Enter, which must not reach the BMC as a command
		boolean swallowEnter = false;
		try {
			while (true) {
				int count;
				try {
					count = stdin.read(buffer);
				} catch (IOException e) {
					// A redirected input that fails after some bytes, or the key reader of the console gone: not a clean end
					failure[0] = e;
					return INPUT_READ_FAILED;
				}
				if (count < 0) {
					if (escapePending) {
						// A lone escape character at the end of the input is no sequence: it is data
						pending.write(escape);
						flush(pending, toBmc, stats);
					}
					return CLOSED;
				}
				for (int i = 0; i < count; i++) {
					int b = buffer[i] & 0xff;
					if (cooked) {
						boolean lfOfCrLf = b == '\n' && afterCr;
						afterCr = b == '\r';
						if (lfOfCrLf) {
							continue;
						}
						if (b == '\n') {
							b = '\r';
						}
						boolean swallowed = swallowEnter && b == '\r';
						swallowEnter = false;
						if (swallowed) {
							continue;
						}
					}
					if (escapePending) {
						escapePending = false;
						if (b == '.') {
							flush(pending, toBmc, stats);
							return CLOSED;
						}
						if (b == 'B') {
							flush(pending, toBmc, stats);
							if (!console.invokeOperations(SolOperation.Break)) {
								return INPUT_FAILED;
							}
							// Nothing was sent since the newline: the next sequence is recognized too
							swallowEnter = cooked;
							continue;
						}
						if (b == '?') {
							err.print(ESCAPE_HELP.replace('~', (char) escape));
							err.flush();
							swallowEnter = cooked;
							continue;
						}
						if (b != escape) {
							pending.write(escape);
						}
					} else if (atLineStart && b == escape) {
						escapePending = true;
						continue;
					}
					pending.write(b);
					atLineStart = b == '\r' || b == '\n';
				}
				// One packet per read: a keystroke in raw mode, a line or a block from a pipe
				flush(pending, toBmc, stats);
			}
		} catch (IOException e) {
			return INPUT_FAILED;
		}
	}

	private static void flush(ByteArrayOutputStream pending, OutputStream toBmc, Stats stats) throws IOException {
		if (pending.size() > 0) {
			long start = System.nanoTime();
			toBmc.write(pending.toByteArray());
			stats.ackNanos.addAndGet(System.nanoTime() - start);
			stats.packets.incrementAndGet();
			stats.bytesSent.addAndGet(pending.size());
			pending.reset();
		}
	}

	/** What the console exchanged, for the verbose summary. */
	private static final class Stats {
		private final AtomicLong packets = new AtomicLong();
		private final AtomicLong bytesSent = new AtomicLong();
		private final AtomicLong ackNanos = new AtomicLong();
		private final AtomicLong bytesReceived = new AtomicLong();
	}
}
