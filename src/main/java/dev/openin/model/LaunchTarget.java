package dev.openin.model;

import java.nio.file.Path;
import java.util.Objects;

public record LaunchTarget(Path selected, Path directory, Path projectRoot) {
    public LaunchTarget {
        Objects.requireNonNull(selected, "Selected path");
        Objects.requireNonNull(directory, "Directory");
    }

    public Path resolve(TargetMode mode) {
        return switch (mode) {
            case SELECTED -> selected;
            case DIRECTORY -> directory;
            case PROJECT_ROOT -> {
                if (projectRoot == null) throw new IllegalArgumentException("The project has no local root directory.");
                yield projectRoot;
            }
        };
    }
}
