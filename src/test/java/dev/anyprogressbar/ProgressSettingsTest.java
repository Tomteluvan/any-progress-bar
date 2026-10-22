package dev.anyprogressbar;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressSettingsTest {
    private static Set<String> ids(ProgressSettings.Data data) {
        return data.designs.stream().map(d -> d.id).collect(Collectors.toSet());
    }

    @Test void upgradeDoesNotRestorePresetsFromEarlierVersions() {
        var data = new ProgressSettings.Data();
        data.presetVersion = 2;
        data.designs.removeIf(d -> d.id.equals("preset-tung-night"));
        data.migratePresets();
        assertFalse(ids(data).contains("preset-tung-night"));
        assertTrue(ids(data).contains("preset-chair"));
        assertEquals(ProgressSettings.Data.PRESET_VERSION, data.presetVersion);
    }

    @Test void upgradeAddsPresetsIntroducedSinceTheSavedVersion() {
        var data = new ProgressSettings.Data();
        data.presetVersion = 2;
        data.designs.removeIf(d -> d.id.equals("preset-chair") || d.id.equals("preset-mug"));
        data.migratePresets();
        assertTrue(ids(data).containsAll(Set.of("preset-chair", "preset-mug")));
    }

    @Test void libraryFromBeforeThemePresetsGetsAllOfThem() {
        var data = new ProgressSettings.Data();
        data.designs.removeIf(d -> d.id.equals("preset-tung-night") || d.id.equals("preset-chair"));
        data.migratePresets();
        assertTrue(ids(data).containsAll(Set.of("preset-tung-night", "preset-chair")));
    }

    @Test void migrationIsIdempotentAndKeepsExistingDesigns() {
        var data = new ProgressSettings.Data();
        data.presetVersion = 2;
        data.migratePresets();
        int size = data.designs.size();
        data.migratePresets();
        assertEquals(size, data.designs.size());
        assertEquals(ids(data).size(), data.designs.size());
    }

    @Test void everyThemedPresetHasAnIntroductionVersionWithinTheCurrentOne() {
        for (Design preset : Design.themePresets()) {
            int version = Design.introducedIn(preset);
            assertTrue(version >= 2 && version <= ProgressSettings.Data.PRESET_VERSION, preset.id);
        }
    }

    @Test void motionsHaveReadableLabels() {
        assertEquals("Ping-pong", Design.Motion.PING_PONG.label());
        for (Design.Motion motion : Design.Motion.values()) assertFalse(motion.label().contains("_"));
    }
}
