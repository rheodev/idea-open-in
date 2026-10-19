package dev.openin.launch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import dev.openin.model.AppEntry;
import dev.openin.model.LaunchTarget;
import dev.openin.model.LaunchType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** All installation discovery and process work runs outside menu updates and the EDT. */
public final class ApplicationLauncher {
    private static final Duration LAUNCH_TIMEOUT = Duration.ofSeconds(30);
    private record CachedAvailability(long generation, AppDiscovery.Installation installation) {}
    private static final Map<AvailabilityKey, CachedAvailability> AVAILABILITY = new ConcurrentHashMap<>();
    private static final AtomicLong REFRESH_GENERATION = new AtomicLong();

    private ApplicationLauncher() {}

    public static void launch(AppEntry entry, LaunchTarget target, @Nullable Project project) {
        AppEntry snapshot = entry.copy();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                launchBlocking(snapshot, target);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                notifyFailure(snapshot, project, e);
            }
        });
    }

    /** Reads memory only. Call refreshAvailability when settings load/change or Refresh is pressed. */
    public static String availability(AppEntry entry) {
        CachedAvailability cached = AVAILABILITY.get(AvailabilityKey.of(entry));
        return cached == null ? "Availability not checked. Refresh in Open In settings."
                : cached.installation().unavailable();
    }

    public static void refreshAvailability(List<AppEntry> entries, Runnable onComplete) {
        List<AppEntry> snapshots = entries.stream().map(AppEntry::copy).toList();
        long generation = REFRESH_GENERATION.incrementAndGet();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Map<AvailabilityKey, AppDiscovery.Installation> detected = new HashMap<>();
            for (AppEntry entry : snapshots) {
                AvailabilityKey key = AvailabilityKey.of(entry);
                if (detected.containsKey(key)) continue;
                try {
                    detected.put(key, AppDiscovery.detect(entry));
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    detected.put(key, AppDiscovery.Installation.missing(message(e)));
                }
            }
            // Draft and saved configurations can be refreshed independently. Never evict the
            // saved location when an unapplied draft changes an application's location.
            detected.forEach((key, installation) -> cache(key, installation, generation));
            if (!ApplicationManager.getApplication().isDisposed()) {
                ApplicationManager.getApplication().invokeLater(onComplete, ModalityState.any());
            }
        });
    }

    private static void cache(AvailabilityKey key, AppDiscovery.Installation installation, long generation) {
        AVAILABILITY.compute(key, (ignored, previous) ->
                previous == null || generation >= previous.generation()
                        ? new CachedAvailability(generation, installation) : previous);
    }

    /** Actual blocking invocation, also usable by an external integration/smoke harness. */
    public static void launchBlocking(AppEntry entry, LaunchTarget target) throws Exception {
        Objects.requireNonNull(entry, "App entry is required");
        Objects.requireNonNull(target, "Launch target is required");
        if (entry.targetMode == null) throw new IllegalArgumentException("Choose a target mode in Open In settings.");
        Path resolved = requireExisting(target.resolve(entry.targetMode), "Target");
        Path directory = requireDirectory(target.directory(), "Selected item's directory");
        Path workingDirectory = Files.isDirectory(resolved) ? resolved : requireDirectory(resolved.getParent(), "Target's parent directory");
        long generation = REFRESH_GENERATION.incrementAndGet();
        AppDiscovery.Installation installation = AppDiscovery.detect(entry);
        cache(AvailabilityKey.of(entry), installation, generation);
        if (!installation.unavailable().isEmpty()) throw new IOException(installation.unavailable());

        List<String> command = new ArrayList<>();
        Path processDirectory = null;
        switch (entry.launchType) {
            case TERMINAL, ITERM, GHOSTTY -> {
                String service = switch (entry.launchType) {
                    case TERMINAL -> "New Terminal at Folder";
                    case ITERM -> "New iTerm2 Window Here";
                    case GHOSTTY -> "New Ghostty Window Here";
                    default -> throw new IllegalStateException("Not a terminal launch type");
                };
                command.addAll(List.of(AppDiscovery.helperExecutable(), service, workingDirectory.toString()));
            }
            case APPLICATION -> {
                command.addAll(List.of("/usr/bin/open", "-a", installation.path().toString(), resolved.toString()));
                if (entry.arguments != null && !entry.arguments.isEmpty()) {
                    command.add("--args");
                    appendArguments(command, entry, target, resolved, directory);
                }
            }
            case COMMAND -> {
                command.add(installation.path().toString());
                appendArguments(command, entry, target, resolved, directory);
                processDirectory = workingDirectory;
            }
        }
        LaunchProcess.Result result = LaunchProcess.run(command, processDirectory, null, LAUNCH_TIMEOUT);
        if (result.exitCode() != 0) {
            String diagnostic = result.diagnostic();
            throw new IOException(displayName(entry) + " launch failed (exit " + result.exitCode() + ")."
                    + " Check its path and arguments in Open In settings."
                    + (diagnostic.isEmpty() ? "" : "\n" + diagnostic));
        }
    }

    private static void appendArguments(List<String> command, AppEntry entry, LaunchTarget target,
                                        Path resolved, Path directory) {
        if (entry.arguments == null) return;
        Path projectRoot = null;
        for (String argument : entry.arguments) {
            if (argument == null) throw new IllegalArgumentException("Arguments cannot contain null values.");
            if (!argument.contains("{path}") && !argument.contains("{directory}") && !argument.contains("{projectRoot}")) {
                command.add(argument);
                continue;
            }
            // One pass: a placeholder-looking substring inside a path is literal, never re-expanded.
            StringBuilder value = new StringBuilder(argument.length());
            for (int offset = 0; offset < argument.length();) {
                if (argument.startsWith("{path}", offset)) {
                    value.append(resolved);
                    offset += 6;
                } else if (argument.startsWith("{directory}", offset)) {
                    value.append(directory);
                    offset += 11;
                } else if (argument.startsWith("{projectRoot}", offset)) {
                    if (projectRoot == null) projectRoot = requireDirectory(target.projectRoot(), "Project root");
                    value.append(projectRoot);
                    offset += 13;
                } else {
                    value.append(argument.charAt(offset++));
                }
            }
            command.add(value.toString());
        }
    }

    private static Path requireExisting(@Nullable Path path, String label) {
        if (path == null) throw new IllegalArgumentException(label + " is unavailable. Select a local item in an open project.");
        if (!path.isAbsolute() || !path.getFileSystem().equals(Path.of("/").getFileSystem())) {
            throw new IllegalArgumentException(label + " must be a local absolute path: " + path);
        }
        Path normalized = path.normalize();
        if (!Files.exists(normalized)) throw new IllegalArgumentException(label + " no longer exists: " + normalized);
        return normalized;
    }

    private static Path requireDirectory(@Nullable Path path, String label) {
        Path existing = requireExisting(path, label);
        if (!Files.isDirectory(existing)) throw new IllegalArgumentException(label + " is not a directory: " + existing);
        return existing;
    }

    private static void notifyFailure(AppEntry entry, @Nullable Project project, Exception error) {
        if (ApplicationManager.getApplication().isDisposed()) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            Project liveProject = project != null && !project.isDisposed() ? project : null;
            Notification notification = NotificationGroupManager.getInstance().getNotificationGroup("Open In")
                    .createNotification("Could not open in " + displayName(entry), escapeHtml(message(error)), NotificationType.ERROR);
            notification.addAction(NotificationAction.createSimpleExpiring("Open In Settings", () ->
                    ShowSettingsUtil.getInstance().showSettingsDialog(liveProject, "Open In")));
            notification.notify(liveProject);
        }, ModalityState.any());
    }

    private static String displayName(AppEntry entry) {
        return entry.name == null || entry.name.isBlank() ? "application" : entry.name;
    }

    private static String message(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private record AvailabilityKey(LaunchType type, String applicationPath, String bundleId, String executable) {
        static AvailabilityKey of(AppEntry entry) {
            return new AvailabilityKey(entry.launchType, entry.applicationPath, entry.bundleId, entry.executable);
        }
    }
}
