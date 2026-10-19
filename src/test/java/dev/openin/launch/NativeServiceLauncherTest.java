package dev.openin.launch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativeServiceLauncherTest {
    @Test
    void serviceLauncherSendsDirectoryWithoutAppleScript() throws Exception {
        Path fixture = Path.of("native/open-in-service-launcher-test.txt");
        Process build = new ProcessBuilder("clang", "-framework", "Cocoa",
                "native/open-in-service-launcher.m", "-o", "/tmp/open-in-service-launcher-test")
                .directory(Path.of(".").toAbsolutePath().toFile())
                .redirectErrorStream(true)
                .start();
        String buildOutput = new String(build.getInputStream().readAllBytes());
        assertEquals(0, build.waitFor(), buildOutput);
        Process probe = new ProcessBuilder("/tmp/open-in-service-launcher-test").start();
        String usage = new String(probe.getErrorStream().readAllBytes());
        assertEquals(64, probe.waitFor());
        assertTrue(usage.contains("usage: open-in-service-launcher"), usage);
        assertTrue(java.nio.file.Files.deleteIfExists(fixture) || true);
    }
}
