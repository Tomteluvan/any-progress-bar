package dev.anyprogressbar;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressSettingsTest {
    private static final Set<String> PLAYFUL_PRESET_IDS = Set.of(
        "preset-snail-mail", "preset-cowsmic", "preset-ramen-racer", "preset-frog-wizard", "preset-disco-duck");

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

    @Test void versionThreeLibrariesReceiveExactlyTheFiveNewPresetsOnce() {
        var data = new ProgressSettings.Data();
        data.presetVersion = 3;
        data.enabled = false;
        data.designs.removeIf(d -> PLAYFUL_PRESET_IDS.contains(d.id) || d.id.equals("preset-chair"));
        Design custom = new Design();
        custom.name = "My custom design";
        custom.objectImage = "bundled:fox.svg";
        data.designs.add(custom);
        data.selectedId = custom.id;
        int previousSize = data.designs.size();

        assertEquals(PLAYFUL_PRESET_IDS, Design.themePresetsAddedAfter(3).stream()
            .map(d -> d.id).collect(Collectors.toSet()));
        data.migratePresets();
        data.validate();

        assertEquals(previousSize + 5, data.designs.size());
        assertTrue(ids(data).containsAll(PLAYFUL_PRESET_IDS));
        assertFalse(ids(data).contains("preset-chair"));
        assertSame(custom, data.selected());
        assertEquals("bundled:fox.svg", data.selected().objectImage);
        assertFalse(data.enabled);
        assertEquals(ProgressSettings.Data.PRESET_VERSION, data.presetVersion);

        data.designs.removeIf(d -> d.id.equals("preset-snail-mail"));
        data.migratePresets();
        assertFalse(ids(data).contains("preset-snail-mail"));
        assertEquals(previousSize + 4, data.designs.size());
    }

    @Test void motionsHaveReadableLabels() {
        assertEquals("Ping-pong", Design.Motion.PING_PONG.label());
        for (Design.Motion motion : Design.Motion.values()) assertFalse(motion.label().contains("_"));
    }
}
