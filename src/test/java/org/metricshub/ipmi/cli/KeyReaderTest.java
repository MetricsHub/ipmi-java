package org.metricshub.ipmi.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The PowerShell key reader of the Windows SOL console: its {@code Encode} function is fed synthetic keys by
 * {@code key-reader-test.ps1}, which compares the bytes with those a terminal sends. Windows only: the script needs
 * PowerShell.
 */
@EnabledOnOs(OS.WINDOWS)
class KeyReaderTest {

	private static final long PROCESS_TIMEOUT_SECONDS = 60L;

	@Test
	@Timeout(120)
	void keysAreEncodedAsATerminalSendsThem(@TempDir Path directory) throws Exception {
		Path script = directory.resolve("key-reader.ps1");
		Files.write(script, IpmiCli.KEY_READER.getBytes(StandardCharsets.UTF_8));
		Path harness = directory.resolve("key-reader-test.ps1");
		try (InputStream resource = KeyReaderTest.class.getResourceAsStream("key-reader-test.ps1")) {
			Files.copy(resource, harness);
		}
		Process process = new ProcessBuilder(
				"powershell.exe",
				"-NoProfile",
				"-ExecutionPolicy",
				"Bypass",
				"-File",
				harness.toString(),
				script.toString()).redirectErrorStream(true).start();
		process.getOutputStream().close();
		// Drained on a thread while the timed wait runs: a stalled PowerShell fails the test instead of hanging it
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		Thread drain = new Thread(() -> {
			byte[] buffer = new byte[4096];
			try (InputStream stream = process.getInputStream()) {
				int count = stream.read(buffer);
				while (count >= 0) {
					output.write(buffer, 0, count);
					count = stream.read(buffer);
				}
			} catch (IOException e) {
				// The process was destroyed: what was read is what there is
			}
		});
		drain.setDaemon(true);
		drain.start();
		if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			fail("PowerShell did not finish");
		}
		drain.join(TimeUnit.SECONDS.toMillis(PROCESS_TIMEOUT_SECONDS));
		String report = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertEquals(0, process.exitValue(), report);
		assertTrue(report.contains("failed: 0"), report);
	}
}
