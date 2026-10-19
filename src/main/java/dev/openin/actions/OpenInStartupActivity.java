package dev.openin.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import dev.openin.settings.OpenInSettingsService;
import org.jetbrains.annotations.NotNull;

public final class OpenInStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        OpenInSettingsService.getInstance();
    }
}
