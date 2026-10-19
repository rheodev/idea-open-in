package dev.openin.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import dev.openin.model.LaunchTarget;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class TargetPathResolver {
    private TargetPathResolver() {}

    public static @Nullable LaunchTarget resolve(AnActionEvent event) {
        VirtualFile[] selection = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        VirtualFile file;
        if (selection != null) {
            if (selection.length != 1) return null;
            file = selection[0];
        } else {
            file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        }
        if (file == null || !file.isValid() || !file.isInLocalFileSystem()) return null;
        VirtualFile directory = file.isDirectory() ? file : file.getParent();
        if (directory == null || !directory.isValid()) return null;
        try {
            Path selected = Path.of(file.getPath());
            Path parent = Path.of(directory.getPath());
            String basePath = event.getProject() == null ? null : event.getProject().getBasePath();
            Path root = basePath == null ? null : Path.of(basePath);
            return new LaunchTarget(selected, parent, root);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }
}
