package dev.openin.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import dev.openin.launch.ApplicationLauncher;
import dev.openin.model.AppEntry;
import dev.openin.model.LaunchTarget;
import dev.openin.model.TargetMode;
import dev.openin.settings.OpenInSettingsService;
import org.jetbrains.annotations.NotNull;

public final class OpenInApplicationAction extends DumbAwareAction {
    private final AppEntry entry;

    public OpenInApplicationAction(AppEntry entry) {
        super(entry.name, "Open the selected path in " + entry.name, null);
        this.entry = entry.copy();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        LaunchTarget target = TargetPathResolver.resolve(event);
        boolean available = OpenInApplicationsGroup.isProjectPopup(event) && target != null
            && (entry.targetMode != TargetMode.PROJECT_ROOT || target.projectRoot() != null);
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        LaunchTarget target = TargetPathResolver.resolve(event);
        if (target == null || !OpenInApplicationsGroup.isProjectPopup(event)) return;
        // Re-read saved settings: an open menu must not execute a subsequently disabled entry.
        for (AppEntry current : OpenInSettingsService.getInstance().entries()) {
            if (current.id.equals(entry.id) && current.enabled) {
                ApplicationLauncher.launch(current, target, event.getProject());
                return;
            }
        }
    }
}
