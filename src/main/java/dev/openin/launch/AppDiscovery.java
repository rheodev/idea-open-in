package dev.openin.launch;

import dev.openin.model.AppEntry;
import dev.openin.model.LaunchType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

final class AppDiscovery {
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(10);

    private AppDiscovery() {}

    record Installation(Path path, String unavailable) {
        static Installation missing(String reason) { return new Installation(null, reason); }
    }

    static Installation detect(AppEntry entry) throws IOException, InterruptedException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return Installation.missing("Open In requires macOS.");
        }
        if (entry.launchType == null) return Installation.missing("Choose a launch type in Open In settings.");
        if (entry.launchType == LaunchType.COMMAND) {
            if (entry.executable == null || entry.executable.isBlank()) {
                return Installation.missing("Choose an absolute executable path in Open In settings.");
            }
            Path executable = absolutePath(entry.executable);
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
                return Installation.missing("Executable is missing or not executable: " + executable);
            }
            return new Installation(executable, "");
        }

        String defaultPath = defaultPath(entry.launchType);
        String configured = entry.applicationPath == null ? "" : entry.applicationPath;
        if (!configured.isBlank()) {
            Path path = absolutePath(configured);
            if (Files.isDirectory(path)) return validate(path, entry.launchType);
            // Canonical built-in defaults are search hints, not a restriction to /Applications.
            if (!configured.equals(defaultPath)) {
                return Installation.missing("Application is missing: " + configured
                        + ". Select its installed .app in Open In settings.");
            }
        }
        if (!defaultPath.isEmpty()) {
            Path systemApplication = Path.of(defaultPath);
            if (Files.isDirectory(systemApplication)) return validate(systemApplication, entry.launchType);
            Path userApplication = Path.of(System.getProperty("user.home"), "Applications",
                    systemApplication.getFileName().toString());
            if (Files.isDirectory(userApplication)) return validate(userApplication, entry.launchType);
        }

        String bundleId = entry.bundleId == null || entry.bundleId.isBlank()
                ? defaultBundleId(entry.launchType) : entry.bundleId;
        if (!bundleId.isBlank()) {
            LaunchProcess.Result result = LaunchProcess.run(
                    List.of("/usr/bin/mdfind", "kMDItemCFBundleIdentifier == '" + bundleId + "'"), null,
                    null, LOOKUP_TIMEOUT);
            if (result.exitCode() == 0) {
                for (String candidate : result.stdout().lines().map(String::strip).filter(line -> !line.isEmpty()).toList()) {
                    try {
                        Path path = absolutePath(candidate);
                        if (Files.isDirectory(path)) return validate(path, entry.launchType);
                    } catch (IllegalArgumentException ignored) {
                        // Spotlight can return stale or non-local entries; keep looking.
                    }
                }
            }
        }
        return Installation.missing("Application is not installed or could not be located. Install it or select its .app in Open In settings.");
    }

    private static Installation validate(Path path, LaunchType type) throws IOException, InterruptedException {
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".app")
                || !Files.isRegularFile(path.resolve("Contents/Info.plist"))) {
            return Installation.missing("Not a macOS application bundle: " + path);
        }
        if (type == LaunchType.GHOSTTY) {
            LaunchProcess.Result result = LaunchProcess.run(
                    List.of("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleShortVersionString",
                            path.resolve("Contents/Info.plist").toString()), null, null, LOOKUP_TIMEOUT);
            String version = result.stdout().strip();
            if (result.exitCode() != 0 || !supportsGhosttyService(version)) {
                return Installation.missing("Ghostty 1.3 or newer is required for native new-window launch"
                        + " Update Ghostty or choose a newer .app.");
            }
        }
        return new Installation(path, "");
    }

    private static boolean supportsGhosttyService(String version) {
        String[] parts = version.split("\\.", 3);
        if (parts.length < 2) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 1 || major == 1 && minor >= 3;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static Path absolutePath(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute() || !path.getFileSystem().equals(Path.of("/").getFileSystem())) {
            throw new IllegalArgumentException("A local absolute path is required: " + value);
        }
        return path.normalize();
    }


    static String helperExecutable() throws IOException {
        try (var stream = AppDiscovery.class.getResourceAsStream("/native/open-in-service-launcher")) {
            if (stream == null) throw new IOException("Bundled service launcher is missing. Reinstall the Open In plugin.");
            Path staged = Files.createTempFile("open-in-service-launcher-", "");
            Files.write(staged, stream.readAllBytes());
            staged.toFile().setExecutable(true, true);
            staged.toFile().deleteOnExit();
            return staged.toString();
        }
    }

    private static String defaultPath(LaunchType type) {
        return switch (type) {
            case TERMINAL -> "/System/Applications/Utilities/Terminal.app";
            case ITERM -> "/Applications/iTerm.app";
            case GHOSTTY -> "/Applications/Ghostty.app";
            default -> "";
        };
    }

    private static String defaultBundleId(LaunchType type) {
        return switch (type) {
            case TERMINAL -> "com.apple.Terminal";
            case ITERM -> "com.googlecode.iterm2";
            case GHOSTTY -> "com.mitchellh.ghostty";
            default -> "";
        };
    }
}
