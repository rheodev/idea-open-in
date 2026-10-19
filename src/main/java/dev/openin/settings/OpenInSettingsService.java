package dev.openin.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import dev.openin.launch.ApplicationLauncher;
import dev.openin.model.AppEntry;
import dev.openin.model.BuiltinApps;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service(Service.Level.APP)
@State(name = "OpenInSettings", storages = @Storage(value = "open-in.xml", roamingType = RoamingType.DISABLED))
public final class OpenInSettingsService implements PersistentStateComponent<OpenInSettingsService.SettingsState> {
    public static final class SettingsState {
        public List<AppEntry> applications = new ArrayList<>();
    }

    private volatile List<AppEntry> applications = List.copyOf(BuiltinApps.defaults());

    public OpenInSettingsService() {
        ApplicationLauncher.refreshAvailability(entries(), () -> {});
    }

    public static OpenInSettingsService getInstance() {
        return ApplicationManager.getApplication().getService(OpenInSettingsService.class);
    }

    public List<AppEntry> entries() {
        return applications.stream().map(AppEntry::copy).toList();
    }

    public void replaceEntries(List<AppEntry> entries) {
        Set<String> ids = new HashSet<>();
        for (AppEntry entry : entries) {
            if (entry.id == null || entry.id.isBlank() || !ids.add(entry.id)) {
                throw new IllegalArgumentException("Each application must have a unique ID.");
            }
            if (entry.name == null || entry.name.isBlank() || entry.launchType == null
                || entry.targetMode == null) {
                throw new IllegalArgumentException("Application name and launch options are required.");
            }
        }
        applications = List.copyOf(BuiltinApps.merge(entries));
        ApplicationLauncher.refreshAvailability(entries(), () -> {});
    }

    @Override
    public @NotNull SettingsState getState() {
        SettingsState state = new SettingsState();
        state.applications = new ArrayList<>(entries());
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        applications = List.copyOf(BuiltinApps.merge(state.applications));
        ApplicationLauncher.refreshAvailability(entries(), () -> {});
    }
}
