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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.common.Constants;

/**
 * The parsed command line of {@link IpmiCli}: the connection options before the subcommand, then the subcommand with
 * its options and arguments in any order. Secrets (the password and the BMC key) are held in arrays that
 * {@link #close()} wipes, and never appear in the messages of a {@link CliUsageException}.
 */
final class CliArguments implements AutoCloseable {

	/** The subcommands, {@code HELP} and {@code VERSION} included. */
	enum Subcommand {
		HELP,
		VERSION,
		PROBE,
		CHASSIS,
		POWER,
		SENSORS,
		FRU,
		SOL
	}

	static final long DEFAULT_TIMEOUT_MS = 120_000L;
	static final long DEFAULT_MESSAGE_TIMEOUT_MS = 5_000L;
	static final int DEFAULT_RETRIES = 3;
	/** The preference of the client, repeated here for the usage text and the {@code probe} output. */
	static final int[] DEFAULT_CIPHER_SUITES = { 17, 3, 8, 16, 2, 7 };
	static final char DEFAULT_ESCAPE = '~';
	static final String SUBCOMMANDS = "probe, chassis, power, sensors, fru or sol";

	private static final int MAX_PORT = 65_535;
	private static final int MAX_KEY_BYTES = 20;
	private static final int MAX_FRU_ID = 255;
	private static final int LAST_STANDARD_SUITE = 19;
	private static final int HEX_RADIX = 16;
	private static final char DELETE = 0x7f;

	private Subcommand subcommand;
	private String hostname;
	private String username;
	private char[] password;
	private byte[] bmcKey;
	private int port = Constants.IPMI_PORT;
	private PrivilegeLevel privilege;
	private int[] cipherSuites;
	private long timeoutMs = DEFAULT_TIMEOUT_MS;
	private long messageTimeoutMs = DEFAULT_MESSAGE_TIMEOUT_MS;
	private int retries = DEFAULT_RETRIES;
	private boolean verbose;
	private boolean json;
	private boolean longFormat;
	private Set<SensorType> types;
	private final List<String> names = new ArrayList<String>();
	private final List<Integer> fruIds = new ArrayList<Integer>();
	private int escape = DEFAULT_ESCAPE;
	private boolean escapeGiven;
	private boolean noEscape;
	private String passwordOption;
	private String keyOption;

	private CliArguments() {}

	/**
	 * Parses a command line.
	 *
	 * @param args the arguments of {@code main}; the value of {@code -p} is blanked once read
	 * @return the parsed arguments, to close once used
	 * @throws CliUsageException when the command line is invalid
	 */
	static CliArguments parse(String[] args) throws CliUsageException {
		CliArguments parsed = new CliArguments();
		try {
			int index = 0;
			while (index < args.length) {
				String argument = args[index];
				if ("--".equals(argument)) {
					index++;
					break;
				}
				if (!argument.startsWith("-")) {
					break;
				}
				index = parsed.parseOption(args, index);
				if (parsed.subcommand == Subcommand.HELP || parsed.subcommand == Subcommand.VERSION) {
					return parsed;
				}
			}
			if (index >= args.length) {
				throw new CliUsageException("missing subcommand (" + SUBCOMMANDS + ")");
			}
			parsed.parseSubcommand(args[index], Arrays.copyOfRange(args, index + 1, args.length));
			if (parsed.subcommand != Subcommand.HELP) {
				parsed.validate();
			}
			return parsed;
		} catch (CliUsageException | RuntimeException e) {
			parsed.close();
			throw e;
		}
	}

	private int parseOption(String[] args, int index) throws CliUsageException {
		String argument = args[index];
		String option = optionName(argument);
		switch (option) {
		case "--help":
			subcommand = Subcommand.HELP;
			return flag(argument, option, index);
		case "--version":
			subcommand = Subcommand.VERSION;
			return flag(argument, option, index);
		case "-h":
		case "--hostname":
			hostname = optionValue(args, index, option);
			return nextIndex(argument, index);
		case "-u":
		case "--username":
			username = optionValue(args, index, option);
			return nextIndex(argument, index);
		case "-p":
		case "--password": {
			char[] value = consumePassword(args, index, option);
			exclusive(passwordOption, "--password and --password-file");
			passwordOption = option;
			replacePassword(value);
			return nextIndex(argument, index);
		}
		case "-pf":
		case "--password-file":
			exclusive(passwordOption, "--password and --password-file");
			passwordOption = option;
			replacePassword(decodeUtf8(readSecretFile(optionValue(args, index, option), option), option));
			return nextIndex(argument, index);
		case "-k":
		case "--bmc-key": {
			String value = consumeSecret(args, index, option);
			exclusive(keyOption, "--bmc-key and --bmc-key-file");
			keyOption = option;
			bmcKey = parseHex(value.getBytes(StandardCharsets.US_ASCII), option);
			return nextIndex(argument, index);
		}
		case "-kf":
		case "--bmc-key-file":
			exclusive(keyOption, "--bmc-key and --bmc-key-file");
			keyOption = option;
			bmcKey = parseHex(readSecretFile(optionValue(args, index, option), option), option);
			return nextIndex(argument, index);
		case "-P":
		case "--port":
			port = (int) parseNumber(optionValue(args, index, option), option, 1, MAX_PORT);
			return nextIndex(argument, index);
		case "-L":
		case "--privilege":
			privilege = parsePrivilege(optionValue(args, index, option), option);
			return nextIndex(argument, index);
		case "-C":
		case "--cipher-suite":
			cipherSuites = parseCipherSuites(optionValue(args, index, option), option);
			return nextIndex(argument, index);
		case "-t":
		case "--timeout":
			timeoutMs = parseNumber(optionValue(args, index, option), option, 1, Integer.MAX_VALUE);
			return nextIndex(argument, index);
		case "--message-timeout":
			messageTimeoutMs = parseNumber(optionValue(args, index, option), option, 1, Integer.MAX_VALUE);
			return nextIndex(argument, index);
		case "--retries":
			retries = (int) parseNumber(optionValue(args, index, option), option, 0, Integer.MAX_VALUE);
			return nextIndex(argument, index);
		case "-v":
		case "--verbose":
			verbose = true;
			return flag(argument, option, index);
		default:
			throw new CliUsageException("unknown option '" + option + "'");
		}
	}

	private void parseSubcommand(String name, String[] rest) throws CliUsageException {
		switch (name) {
		case "probe":
			subcommand = Subcommand.PROBE;
			break;
		case "chassis":
			subcommand = Subcommand.CHASSIS;
			break;
		case "power":
			subcommand = Subcommand.POWER;
			break;
		case "sensors":
			subcommand = Subcommand.SENSORS;
			break;
		case "fru":
			subcommand = Subcommand.FRU;
			break;
		case "sol":
			subcommand = Subcommand.SOL;
			break;
		default:
			throw new CliUsageException("unknown subcommand '" + name + "' (" + SUBCOMMANDS + ")");
		}
		List<String> positional = new ArrayList<String>();
		int index = 0;
		while (index < rest.length) {
			if (rest[index].startsWith("-") && rest[index].length() > 1) {
				index = parseSubcommandOption(rest, index);
				if (subcommand == Subcommand.HELP) {
					return;
				}
			} else {
				positional.add(rest[index++]);
			}
		}
		switch (subcommand) {
		case POWER:
			if (positional.size() != 1 || !"status".equals(positional.get(0))) {
				throw new CliUsageException("power requires one action: status");
			}
			break;
		case FRU:
			for (String id : positional) {
				fruIds.add((int) parseNumber(id, "fru <id>", 0, MAX_FRU_ID));
			}
			break;
		default:
			if (!positional.isEmpty()) {
				throw new CliUsageException(name + " takes no argument");
			}
			break;
		}
	}

	private int parseSubcommandOption(String[] rest, int index) throws CliUsageException {
		String argument = rest[index];
		String option = optionName(argument);
		switch (option) {
		case "--help":
			subcommand = Subcommand.HELP;
			return flag(argument, option, index);
		case "--json":
			requireSubcommand(option, Subcommand.PROBE, Subcommand.CHASSIS, Subcommand.SENSORS, Subcommand.FRU);
			json = true;
			return flag(argument, option, index);
		case "--type":
			requireSubcommand(option, Subcommand.SENSORS);
			if (types == null) {
				types = EnumSet.noneOf(SensorType.class);
			}
			for (String type : optionValue(rest, index, option).split(",")) {
				types.add(parseSensorType(type.trim()));
			}
			return nextIndex(argument, index);
		case "--name":
			requireSubcommand(option, Subcommand.SENSORS);
			for (String glob : optionValue(rest, index, option).split(",", -1)) {
				if (glob.trim().isEmpty()) {
					throw new CliUsageException(option + " requires a glob");
				}
				names.add(glob.trim());
			}
			if (names.isEmpty()) {
				throw new CliUsageException(option + " requires a glob");
			}
			return nextIndex(argument, index);
		case "--long":
			requireSubcommand(option, Subcommand.SENSORS);
			longFormat = true;
			return flag(argument, option, index);
		case "--escape": {
			requireSubcommand(option, Subcommand.SOL);
			String value = optionValue(rest, index, option);
			// The escape is matched against the bytes of the input: one printable ASCII character is one byte
			if (value.length() != 1 || value.charAt(0) <= ' ' || value.charAt(0) >= DELETE) {
				throw new CliUsageException(option + " requires a single printable ASCII character");
			}
			escape = value.charAt(0);
			escapeGiven = true;
			return nextIndex(argument, index);
		}
		case "--no-escape":
			requireSubcommand(option, Subcommand.SOL);
			noEscape = true;
			return flag(argument, option, index);
		default:
			throw new CliUsageException("unknown option '" + option + "'");
		}
	}

	private void requireSubcommand(String option, Subcommand... allowed) throws CliUsageException {
		if (!Arrays.asList(allowed).contains(subcommand)) {
			StringBuilder list = new StringBuilder();
			for (Subcommand candidate : allowed) {
				list.append(list.length() == 0 ? "" : " or ").append(candidate.name().toLowerCase(Locale.ROOT));
			}
			throw new CliUsageException(option + " requires the " + list + " subcommand");
		}
	}

	private void validate() throws CliUsageException {
		if (hostname == null || hostname.trim().isEmpty()) {
			throw new CliUsageException("missing required option --hostname");
		}
		if (subcommand != Subcommand.PROBE && username == null) {
			throw new CliUsageException("missing required option --username");
		}
		if (escapeGiven && noEscape) {
			throw new CliUsageException("--escape and --no-escape are mutually exclusive");
		}
		if (privilege == null) {
			privilege = subcommand == Subcommand.SOL ? PrivilegeLevel.Administrator : PrivilegeLevel.User;
		}
	}

	private static void exclusive(String given, String pair) throws CliUsageException {
		if (given != null) {
			throw new CliUsageException(pair + " are mutually exclusive");
		}
	}

	private static String optionName(String argument) {
		int separator = argument.indexOf('=');
		return separator >= 0 ? argument.substring(0, separator) : argument;
	}

	private static String optionValue(String[] args, int index, String option) throws CliUsageException {
		int separator = args[index].indexOf('=');
		if (separator >= 0) {
			return args[index].substring(separator + 1);
		}
		if (index + 1 >= args.length) {
			throw new CliUsageException(option + " requires a value");
		}
		return args[index + 1];
	}

	private static int nextIndex(String argument, int index) {
		return argument.indexOf('=') >= 0 ? index + 1 : index + 2;
	}

	/** A flag takes no value: {@code --json=false} would do the opposite of what it says. */
	private static int flag(String argument, String option, int index) throws CliUsageException {
		if (argument.indexOf('=') >= 0) {
			throw new CliUsageException(option + " takes no value");
		}
		return index + 1;
	}

	private static char[] consumePassword(String[] args, int index, String option) throws CliUsageException {
		return consumeSecret(args, index, option).toCharArray();
	}

	/** The value of a secret option, blanked in the argument array, the only other copy this process holds. */
	private static String consumeSecret(String[] args, int index, String option) throws CliUsageException {
		String value = optionValue(args, index, option);
		if (args[index].indexOf('=') >= 0) {
			args[index] = option;
		} else {
			args[index + 1] = "";
		}
		return value;
	}

	private static long parseNumber(String value, String option, long min, long max) throws CliUsageException {
		long number;
		try {
			number = Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			throw new CliUsageException(option + " must be a number", e);
		}
		if (number < min || number > max) {
			throw new CliUsageException(option + " must be between " + min + " and " + max);
		}
		return number;
	}

	private static PrivilegeLevel parsePrivilege(String value, String option) throws CliUsageException {
		switch (value.trim().toLowerCase(Locale.ROOT)) {
		case "user":
			return PrivilegeLevel.User;
		case "operator":
			return PrivilegeLevel.Operator;
		case "administrator":
			return PrivilegeLevel.Administrator;
		default:
			throw new CliUsageException(option + " must be user, operator or administrator");
		}
	}

	private static int[] parseCipherSuites(String value, String option) throws CliUsageException {
		String[] ids = value.split(",", -1);
		int[] suites = new int[ids.length];
		for (int i = 0; i < ids.length; i++) {
			suites[i] = (int) parseNumber(ids[i], option, 0, LAST_STANDARD_SUITE);
		}
		return suites;
	}

	private static SensorType parseSensorType(String name) throws CliUsageException {
		for (SensorType type : SensorType.values()) {
			if (type.name().equalsIgnoreCase(name)) {
				return type;
			}
		}
		throw new CliUsageException("unknown sensor type '" + name + "'");
	}

	/**
	 * Reads a secret file: the bytes of the file, less exactly one final LF, CRLF or CR. The read buffer is wiped.
	 */
	private static byte[] readSecretFile(String fileName, String option) throws CliUsageException {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(Paths.get(fileName));
		} catch (IOException | InvalidPathException | SecurityException e) {
			throw new CliUsageException("cannot read " + option + " file", e);
		}
		int length = bytes.length;
		if (length > 0 && bytes[length - 1] == '\n') {
			length--;
			if (length > 0 && bytes[length - 1] == '\r') {
				length--;
			}
		} else if (length > 0 && bytes[length - 1] == '\r') {
			length--;
		}
		byte[] secret = Arrays.copyOf(bytes, length);
		Arrays.fill(bytes, (byte) 0);
		return secret;
	}

	/** Decodes a secret as UTF-8, strictly, wiping the bytes and the intermediate buffers. */
	private static char[] decodeUtf8(byte[] bytes, String option) throws CliUsageException {
		CharBuffer characters = null;
		try {
			characters = StandardCharsets.UTF_8
					.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes));
			char[] decoded = new char[characters.remaining()];
			characters.get(decoded);
			return decoded;
		} catch (CharacterCodingException e) {
			throw new CliUsageException(option + " is not valid UTF-8", e);
		} finally {
			Arrays.fill(bytes, (byte) 0);
			if (characters != null && characters.hasArray()) {
				Arrays.fill(characters.array(), '\0');
			}
		}
	}

	/** Decodes hexadecimal text ({@code 0x} optional, at most 20 bytes) and wipes the text. */
	private static byte[] parseHex(byte[] text, String option) throws CliUsageException {
		try {
			int start = text.length >= 2 && text[0] == '0' && (text[1] == 'x' || text[1] == 'X') ? 2 : 0;
			int digits = text.length - start;
			if (digits == 0 || digits % 2 != 0 || digits / 2 > MAX_KEY_BYTES) {
				throw new CliUsageException(
						option + " must be an even number of hexadecimal digits, at most " + MAX_KEY_BYTES + " bytes");
			}
			byte[] key = new byte[digits / 2];
			for (int i = 0; i < key.length; i++) {
				int high = Character.digit(text[start + 2 * i], HEX_RADIX);
				int low = Character.digit(text[start + 2 * i + 1], HEX_RADIX);
				if (high < 0 || low < 0) {
					Arrays.fill(key, (byte) 0);
					throw new CliUsageException(option + " must be hexadecimal");
				}
				key[i] = (byte) (high << 4 | low);
			}
			return key;
		} finally {
			Arrays.fill(text, (byte) 0);
		}
	}

	Subcommand subcommand() {
		return subcommand;
	}

	String hostname() {
		return hostname;
	}

	/** @return the user name, {@code ""} for the null user, {@code null} when not given ({@code probe}) */
	String username() {
		return username;
	}

	/** @return the password, {@code null} when neither password option was given */
	char[] password() {
		return password;
	}

	/** Sets the password read from the console, wiping the previous one if any. */
	void replacePassword(char[] replacement) {
		if (password != null) {
			Arrays.fill(password, '\0');
		}
		password = replacement;
	}

	/** @return the BMC key, {@code null} when not given */
	byte[] bmcKey() {
		return bmcKey;
	}

	int port() {
		return port;
	}

	PrivilegeLevel privilege() {
		return privilege;
	}

	/** @return the cipher suites of {@code -C}, {@code null} for the default preference of the client */
	int[] cipherSuites() {
		return cipherSuites;
	}

	long timeoutMs() {
		return timeoutMs;
	}

	long messageTimeoutMs() {
		return messageTimeoutMs;
	}

	int retries() {
		return retries;
	}

	boolean verbose() {
		return verbose;
	}

	boolean json() {
		return json;
	}

	boolean longFormat() {
		return longFormat;
	}

	/** @return the sensor types of {@code --type}, {@code null} for all */
	Set<SensorType> types() {
		return types == null ? null : Collections.unmodifiableSet(types);
	}

	/** @return the name globs of {@code --name}, empty for all */
	List<String> names() {
		return Collections.unmodifiableList(names);
	}

	/** @return the FRU IDs of {@code fru <id>...}, empty for all */
	List<Integer> fruIds() {
		return Collections.unmodifiableList(fruIds);
	}

	/** @return the escape character of the SOL console, {@code -1} with {@code --no-escape} */
	int escape() {
		return noEscape ? -1 : escape;
	}

	@Override
	public void close() {
		replacePassword(null);
		if (bmcKey != null) {
			Arrays.fill(bmcKey, (byte) 0);
			bmcKey = null;
		}
	}
}
