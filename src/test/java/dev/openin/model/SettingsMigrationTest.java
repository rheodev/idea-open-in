package dev.openin.model;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import dev.openin.settings.OpenInSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsMigrationTest {
    @Test
    void oldTabSettingsKeepDisabledStateOrderingAndApplicationLocations() throws Exception {
        String oldState = """
            <SettingsState>
              <option name="applications">
                <list>
                  <AppEntry>
                    <option name="id" value="builtin.ghostty"/>
                    <option name="name" value="My Ghostty"/>
                    <option name="enabled" value="false"/>
                    <option name="applicationPath" value="/custom/Ghostty.app"/>
                    <option name="launchType" value="GHOSTTY"/>
                    <option name="targetMode" value="PROJECT_ROOT"/>
                    <option name="terminalMode" value="TAB"/>
                  </AppEntry>
                  <AppEntry>
                    <option name="id" value="builtin.iterm"/>
                    <option name="name" value="iTerm2"/>
                    <option name="applicationPath" value="/custom/iTerm.app"/>
                    <option name="launchType" value="ITERM"/>
                    <option name="terminalMode" value="TAB"/>
                  </AppEntry>
                </list>
              </option>
            </SettingsState>
            """;
        var loaded = XmlSerializer.deserialize(JDOMUtil.load(oldState), OpenInSettingsService.SettingsState.class);
        List<AppEntry> entries = BuiltinApps.merge(loaded.applications);
        assertEquals(List.of("builtin.ghostty", "builtin.iterm", "builtin.terminal"),
            entries.stream().map(entry -> entry.id).toList());
        assertFalse(entries.getFirst().enabled);
        assertEquals("My Ghostty", entries.getFirst().name);
        assertEquals("/custom/Ghostty.app", entries.getFirst().applicationPath);
        assertEquals(TargetMode.PROJECT_ROOT, entries.getFirst().targetMode);
        assertTrue(entries.get(1).enabled);
        assertEquals("/custom/iTerm.app", entries.get(1).applicationPath);
    }
}
