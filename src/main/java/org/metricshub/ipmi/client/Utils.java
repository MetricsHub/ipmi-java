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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.metricshub.ipmi.client.runner.AbstractIpmiRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Utils {

	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	private Utils() {}

	public static final String EMPTY = "";

	/**
	 * How long a call that hit its deadline waits for its worker to close the session and the socket.
	 */
	private static final long CLEANUP_GRACE_MS = 1000;

	/**
	 * @param value The value to check
	 * @return whether the value is null, empty or contains only blank chars
	 */
	public static boolean isBlank(String value) {
		return value == null || isEmpty(value);
	}

	/**
	 * @param value The value to check
	 * @return whether the value is not null, nor empty nor contains only blank chars
	 */
	public static boolean isNotBlank(final String value) {
		return !isBlank(value);
	}

	/**
	 * @param value The value to check
	 * @return whether the value is empty of non-blank chars
	 * @throws NullPointerException if value is <em>null</em>
	 */
	public static boolean isEmpty(String value) {
		return value.trim().isEmpty();
	}

	/**
	 * @param value The value to return
	 * @return the given value or empty string if the value is null or empty
	 */
	public static String getValueOrEmpty(String value) {
		return isBlank(value) ? EMPTY : value;
	}

	/**
	 * Run the given {@link Callable} using the passed timeout in seconds.
	 *
	 * @param <T>
	 * @param callable
	 * @param timeout
	 * @return {@link T} result returned by the callable
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public static <T> T execute(final AbstractIpmiRunner<T> callable, long timeout)
			throws InterruptedException,
			ExecutionException,
			TimeoutException {

		final ExecutorService executorService = Executors.newSingleThreadExecutor(Utils::newWorkerThread);

		// The worker owns the connector: it also closes it, so the cleanup is covered by the deadline and never
		// runs on the calling thread while the worker is still using the connection
		final Future<T> future = executorService.submit(() -> {
			try (AbstractIpmiRunner<T> runner = callable) {
				return runner.call();
			}
		});

		try {
			return future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		} catch (TimeoutException e) {
			// Stop the worker at its current wait and give it a moment to close the session and release the port
			future.cancel(true);
			executorService.shutdownNow();
			if (!executorService.awaitTermination(CLEANUP_GRACE_MS, TimeUnit.MILLISECONDS)) {
				// A call that cannot be interrupted (name resolution, a blocking send): the worker closes the
				// connection and releases the port by itself when that call returns
				LOGGER.warn("IPMI call timed out and its worker is still busy; the port is released when it returns");
			}
			throw e;
		} finally {
			executorService.shutdownNow();
		}
	}

	private static Thread newWorkerThread(Runnable runnable) {
		Thread thread = new Thread(runnable, "ipmi-client");
		thread.setDaemon(true);
		return thread;
	}
}
