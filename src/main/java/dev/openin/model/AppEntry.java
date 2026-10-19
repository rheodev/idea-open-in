package dev.openin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AppEntry {
    public String id = "";
    public String name = "";
    public String applicationPath = "";
    public String bundleId = "";
    public String executable = "";
    public boolean enabled = true;
    public LaunchType launchType = LaunchType.APPLICATION;
    public TargetMode targetMode = TargetMode.DIRECTORY;
    public List<String> arguments = new ArrayList<>();

    public boolean isBuiltin() {
        return id.startsWith("builtin.");
    }

    public AppEntry copy() {
        AppEntry copy = new AppEntry();
        copy.id = id;
        copy.name = name;
        copy.applicationPath = applicationPath;
        copy.bundleId = bundleId;
        copy.executable = executable;
        copy.enabled = enabled;
        copy.launchType = launchType;
        copy.targetMode = targetMode;
        copy.arguments = new ArrayList<>(arguments);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AppEntry entry)) return false;
        return enabled == entry.enabled && Objects.equals(id, entry.id)
            && Objects.equals(name, entry.name) && Objects.equals(applicationPath, entry.applicationPath)
            && Objects.equals(bundleId, entry.bundleId) && Objects.equals(executable, entry.executable)
            && launchType == entry.launchType && targetMode == entry.targetMode
            && Objects.equals(arguments, entry.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, applicationPath, bundleId, executable, enabled,
            launchType, targetMode, arguments);
    }
}
