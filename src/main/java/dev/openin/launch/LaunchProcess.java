package dev.openin.launch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs literal argv without a shell, draining both pipes even after the capture limit. */
final class LaunchProcess {
    private static final int OUTPUT_LIMIT = 64 * 1024;

    private LaunchProcess() {}

    record Result(int exitCode, String stdout, String stderr) {
        String diagnostic() {
            return (stderr.isBlank() ? stdout : stderr).strip();
        }
    }

    static Result run(List<String> command, Path directory, byte[] input, Duration timeout)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) builder.directory(directory.toFile());
        Process process = builder.start();
        Capture stdout = new Capture(process.getInputStream());
        Capture stderr = new Capture(process.getErrorStream());
        Thread stdoutReader = Thread.ofVirtual().name("open-in-stdout").start(stdout);
        Thread stderrReader = Thread.ofVirtual().name("open-in-stderr").start(stderr);
        try {
            try (var stdin = process.getOutputStream()) {
                if (input != null) stdin.write(input);
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw new IOException("The launch process timed out after " + timeout.toSeconds()
                        + " seconds. Check whether the application is responding.");
            }
            // A detached child may inherit the pipes. Never let its lifetime block the launcher.
            stdoutReader.join(1000);
            stderrReader.join(1000);
            if (stdoutReader.isAlive()) process.getInputStream().close();
            if (stderrReader.isAlive()) process.getErrorStream().close();
            return new Result(process.exitValue(), stdout.text(), stderr.text());
        } catch (InterruptedException e) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw e;
        } catch (IOException e) {
            terminate(process);
            throw e;
        } finally {
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    private static void terminate(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        descendants.forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
    }

    private static final class Capture implements Runnable {
        private final InputStream stream;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Capture(InputStream stream) { this.stream = stream; }

        @Override public void run() {
            byte[] buffer = new byte[4096];
            try {
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    synchronized (bytes) {
                        int keep = Math.min(count, OUTPUT_LIMIT - bytes.size());
                        if (keep > 0) bytes.write(buffer, 0, keep);
                    }
                }
            } catch (IOException ignored) {
                // The controlling thread closes inherited pipes after the bounded join.
            }
        }

        String text() {
            synchronized (bytes) {
                return bytes.toString(StandardCharsets.UTF_8);
            }
        }
    }
}
