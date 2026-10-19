package dev.openin.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinAppsTest {
    @Test
    void upgradePreservesDisabledPresetAndCustomOrderWithoutDuplicates() {
        AppEntry terminal = BuiltinApps.defaults().getFirst();
        terminal.enabled = false;
        terminal.name = "My Terminal";
        terminal.applicationPath = "/custom/Terminal.app";
        AppEntry custom = new AppEntry();
        custom.id = "custom.editor";
        custom.name = "Editor";
        List<AppEntry> merged = BuiltinApps.merge(List.of(custom, terminal));
        assertEquals(List.of("custom.editor", "builtin.terminal", "builtin.iterm", "builtin.ghostty"),
            merged.stream().map(entry -> entry.id).toList());
        assertEquals(terminal, merged.get(1));
        assertFalse(merged.get(1).enabled);
        assertEquals(merged, BuiltinApps.merge(merged));
    }

    @Test
    void mergedDraftDoesNotMutateSavedConfiguration() {
        AppEntry custom = new AppEntry();
        custom.id = "custom.command";
        custom.arguments = new ArrayList<>(List.of("--folder", "{path}"));
        List<AppEntry> merged = BuiltinApps.merge(List.of(custom));
        merged.getFirst().enabled = false;
        merged.getFirst().arguments.set(1, "/different/path");
        assertTrue(custom.enabled);
        assertEquals(List.of("--folder", "{path}"), custom.arguments);
    }
}
