package dev.openin.ui;

import dev.openin.model.AppEntry;
import dev.openin.model.LaunchType;
import dev.openin.model.TargetMode;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/** Structural validation only: an uninstalled application can still be saved. */
final class EntryValidation {
    record Problem(String field, String message) {}

    private EntryValidation() {}

    static Problem validate(AppEntry entry) {
        if (entry.name == null || entry.name.isBlank()) {
            return new Problem("name", "Enter a name to show in the Open In menu.");
        }
        if (entry.name.chars().anyMatch(Character::isISOControl)) {
            return new Problem("name", "Use a single-line name without control characters.");
        }
        if (entry.launchType == null) {
            return new Problem("mode", "Choose Application or Command line.");
        }
        if (entry.targetMode == null) {
            return new Problem("target", "Choose the path to open.");
        }
        boolean terminal = entry.launchType == LaunchType.TERMINAL
                || entry.launchType == LaunchType.ITERM || entry.launchType == LaunchType.GHOSTTY;
        if (terminal && entry.targetMode == TargetMode.SELECTED) {
            return new Problem("target", "For a terminal, choose Containing directory or Project root.");
        }
        if (entry.launchType == LaunchType.COMMAND) {
            if (entry.executable == null || entry.executable.isBlank()) {
                return new Problem("executable", "Enter the absolute path of the executable, or choose it with Browse.");
            }
            Problem pathProblem = validateAbsolutePath(entry.executable, "executable", "Executable");
            if (pathProblem != null) return pathProblem;
        } else {
            if (entry.applicationPath != null && !entry.applicationPath.isBlank()) {
                Problem pathProblem = validateAbsolutePath(entry.applicationPath, "application", "Application");
                if (pathProblem != null) return pathProblem;
                if (!entry.applicationPath.toLowerCase(Locale.ROOT).endsWith(".app")) {
                    return new Problem("application", "Choose a macOS application bundle ending in .app.");
                }
            }
            if (entry.bundleId != null && !entry.bundleId.isEmpty()
                    && (entry.bundleId.chars().anyMatch(Character::isWhitespace) || entry.bundleId.indexOf('\0') >= 0)) {
                return new Problem("bundle", "Enter a bundle identifier without spaces, for example com.apple.Terminal.");
            }
            if (!terminal && (entry.applicationPath == null || entry.applicationPath.isBlank())
                    && (entry.bundleId == null || entry.bundleId.isBlank())) {
                return new Problem("application", "Choose an application .app path or enter its bundle identifier.");
            }
        }
        if (entry.arguments == null) {
            return new Problem("arguments", "Enter arguments one per line, or leave the field empty for no arguments.");
        }
        for (String argument : entry.arguments) {
            if (argument == null || argument.indexOf('\0') >= 0 || argument.contains("\n") || argument.contains("\r")) {
                return new Problem("arguments", "Each argument must occupy one line and must not contain a null character.");
            }
        }
        return null;
    }

    private static Problem validateAbsolutePath(String value, String field, String label) {
        try {
            if (!Path.of(value).isAbsolute()) {
                return new Problem(field, label + " path must be absolute. Use Browse; ~ and $HOME are not expanded.");
            }
        } catch (InvalidPathException exception) {
            return new Problem(field, label + " path is invalid. Choose the path again with Browse.");
        }
        return null;
    }
}
