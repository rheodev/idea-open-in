package dev.openin.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BuiltinApps {
    private BuiltinApps() {}

    public static List<AppEntry> defaults() {
        return List.of(
            preset("builtin.terminal", "Terminal (macOS)", LaunchType.TERMINAL, "com.apple.Terminal"),
            preset("builtin.iterm", "iTerm2", LaunchType.ITERM, "com.googlecode.iterm2"),
            preset("builtin.ghostty", "Ghostty", LaunchType.GHOSTTY, "com.mitchellh.ghostty")
        );
    }

    private static AppEntry preset(String id, String name, LaunchType type, String bundleId) {
        AppEntry entry = new AppEntry();
        entry.id = id;
        entry.name = name;
        entry.launchType = type;
        entry.bundleId = bundleId;
        return entry;
    }

    /** Append new presets without changing existing enablement, order or configuration. */
    public static List<AppEntry> merge(List<AppEntry> saved) {
        List<AppEntry> merged = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (saved != null) {
            for (AppEntry entry : saved) {
                if (entry != null && entry.id != null && !entry.id.isBlank() && ids.add(entry.id)) {
                    merged.add(entry.copy());
                }
            }
        }
        for (AppEntry preset : defaults()) {
            if (ids.add(preset.id)) merged.add(preset);
        }
        return merged;
    }
}
