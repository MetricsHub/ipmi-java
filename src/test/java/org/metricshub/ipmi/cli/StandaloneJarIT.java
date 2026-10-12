package org.metricshub.ipmi.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

/**
 * The packaged jars (run by failsafe after {@code package}): the manifest of the standalone jar, its content, and a
 * launch in a separate JVM.
 */
class StandaloneJarIT {

	private static final long PROCESS_TIMEOUT_SECONDS = 30L;

	@Test
	void packagedJarHasManifestAndLaunchesInSeparateJvm() throws Exception {
		Path regularJar = Paths.get(System.getProperty("regularJar"));
		Path standaloneJar = Paths.get(System.getProperty("standaloneJar"));
		String projectVersion = System.getProperty("projectVersion");

		assertTrue(Files.isRegularFile(regularJar), "Missing regular jar: " + regularJar);
		assertTrue(Files.isRegularFile(standaloneJar), "Missing standalone jar: " + standaloneJar);
		try (JarFile jar = new JarFile(standaloneJar.toFile())) {
			Attributes attributes = jar.getManifest().getMainAttributes();
			assertEquals("org.metricshub.ipmi.cli.IpmiCli", attributes.getValue(Attributes.Name.MAIN_CLASS));
			assertEquals(projectVersion, attributes.getValue("Implementation-Version"));
			// The library has no dependency: no third-party class may leak into the standalone jar
			jar
					.stream()
					.map(entry -> entry.getName())
					.filter(name -> name.endsWith(".class"))
					.forEach(
							name -> assertTrue(
									name.startsWith("org/metricshub/ipmi/"),
									"Unexpected third-party class in the standalone jar: " + name));
		}

		ProcessResult help = launch(standaloneJar, "--help");
		assertEquals(0, help.exitCode, help.stderr);
		assertTrue(help.stdout.startsWith("Usage:"), help.stdout);
		assertTrue(
				help.stdout.contains("java -jar ipmi-java-" + projectVersion + "-standalone.jar -h <host> probe"),
				help.stdout);
		assertEquals("", help.stderr);

		ProcessResult version = launch(standaloneJar, "--version");
		assertEquals(0, version.exitCode, version.stderr);
		assertEquals("ipmi-java " + projectVersion + System.lineSeparator(), version.stdout);
		assertEquals("", version.stderr);

		ProcessResult usage = launch(standaloneJar, "chassis");
		assertEquals(64, usage.exitCode);
		assertEquals("", usage.stdout);
		assertTrue(usage.stderr.startsWith("ipmicli: missing required option --hostname"), usage.stderr);
	}

	private static ProcessResult launch(Path jar, String argument) throws Exception {
		String java = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
		Process process = new ProcessBuilder(java, "-jar", jar.toString(), argument).start();
		process.getOutputStream().close();
		String stdout = read(process.getInputStream());
		String stderr = read(process.getErrorStream());
		if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IOException("The CLI process did not finish");
		}
		return new ProcessResult(process.exitValue(), stdout, stderr);
	}

	private static String read(InputStream stream) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int count = stream.read(buffer);
		while (count >= 0) {
			bytes.write(buffer, 0, count);
			count = stream.read(buffer);
		}
		return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
	}

	private static final class ProcessResult {
		private final int exitCode;
		private final String stdout;
		private final String stderr;

		private ProcessResult(int exitCode, String stdout, String stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}
}
