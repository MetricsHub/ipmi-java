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

public class Utils {

	private Utils() {}

	public static final String EMPTY = "";

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
	 *
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public static <T> T execute(final AbstractIpmiRunner<T> callable, long timeout)
			throws InterruptedException, ExecutionException, TimeoutException {

		final ExecutorService executorService = Executors.newSingleThreadExecutor();
		final Future<T> future = executorService.submit(callable);

		try {
			return future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		} catch (TimeoutException e) {
			future.cancel(true);
			throw e;
		} finally {
			executorService.shutdownNow();
		}
	}
}
