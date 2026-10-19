package dev.openin.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import dev.openin.launch.ApplicationLauncher;
import dev.openin.model.AppEntry;
import dev.openin.settings.OpenInSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class OpenInApplicationsGroup extends ActionGroup implements DumbAware {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    static boolean isProjectPopup(AnActionEvent event) {
        return SystemInfo.isMac && ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace());
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(isProjectPopup(event) && TargetPathResolver.resolve(event) != null);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent event) {
        if (event == null || !isProjectPopup(event) || TargetPathResolver.resolve(event) == null) return AnAction.EMPTY_ARRAY;
        List<AnAction> actions = new ArrayList<>();
        for (AppEntry entry : OpenInSettingsService.getInstance().entries()) {
            if (entry.enabled && ApplicationLauncher.availability(entry).isEmpty()) {
                if (actions.isEmpty()) actions.add(Separator.getInstance());
                actions.add(new OpenInApplicationAction(entry));
            }
        }
        return actions.toArray(AnAction[]::new);
    }
}
