package dev.anyprogressbar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssetCleanupTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final Duration GRACE = Duration.ofHours(1);

    @TempDir Path assets;

    private static String hash(char c) { return String.valueOf(c).repeat(64); }

    private Path stored(char c, String extension, Duration age) throws IOException {
        Path file = assets.resolve(hash(c) + "." + extension);
        Files.writeString(file, "image " + c);
        Files.setLastModifiedTime(file, FileTime.from(NOW.minus(age)));
        return file;
    }

    private static Design using(Path... images) {
        Design d = new Design();
        d.objectImage = images.length > 0 ? images[0].toString() : "";
        d.trailImage = images.length > 1 ? images[1].toString() : "bundled:stars.svg";
        d.backgroundImage = images.length > 2 ? images[2].toString() : "";
        return d;
    }

    @Test void removesOldImagesNothingReferences() throws IOException {
        Path used = stored('a', "png", Duration.ofDays(3));
        Path trail = stored('b', "svg", Duration.ofDays(3));
        Path unused = stored('c', "jpg", Duration.ofDays(3));
        Path alsoUnused = stored('d', "jpeg", Duration.ofDays(30));
        var removed = AssetCleanup.removeUnused(assets, List.of(using(used, trail)), NOW, GRACE);
        assertEquals(2, removed.size());
        assertTrue(Files.exists(used) && Files.exists(trail));
        assertFalse(Files.exists(unused));
        assertFalse(Files.exists(alsoUnused));
    }

    @Test void neverTouchesRecentFilesThatAnEditOrImportMayStillHold() throws IOException {
        Path justImported = stored('a', "png", Duration.ofMinutes(5));
        Path justReused = stored('b', "png", Duration.ofMinutes(59));
        Path old = stored('c', "png", Duration.ofMinutes(61));
        AssetCleanup.removeUnused(assets, List.of(), NOW, GRACE);
        assertTrue(Files.exists(justImported));
        assertTrue(Files.exists(justReused));
        assertFalse(Files.exists(old));
    }

    @Test void referencesAreMatchedByFileNameSoMovedConfigFoldersAreSafe() throws IOException {
        Path file = stored('a', "png", Duration.ofDays(2));
        Design elsewhere = new Design();
        elsewhere.objectImage = "/some/other/config/any-progress-bar/assets/" + file.getFileName();
        AssetCleanup.removeUnused(assets, List.of(elsewhere), NOW, GRACE);
        assertTrue(Files.exists(file));
    }

    @Test void onlyDeletesFilesThePluginStoredItself() throws IOException {
        Path notes = assets.resolve("notes.txt");
        Files.writeString(notes, "mine");
        Path shortName = assets.resolve("abc.png");
        Files.writeString(shortName, "mine");
        Path upper = assets.resolve(hash('A') + ".png");
        Files.writeString(upper, "mine");
        Path directory = Files.createDirectory(assets.resolve(hash('e') + ".png"));
        Path inside = Files.writeString(directory.resolve("keep.txt"), "keep");
        for (Path p : new Path[]{notes, shortName, upper, directory}) Files.setLastModifiedTime(p, FileTime.from(NOW.minus(Duration.ofDays(9))));
        assertTrue(AssetCleanup.removeUnused(assets, List.of(), NOW, GRACE).isEmpty());
        assertTrue(Files.exists(notes) && Files.exists(shortName) && Files.exists(upper) && Files.exists(inside));
    }

    @Test void aMissingFolderOrBundledOnlyLibraryIsFine() throws IOException {
        assertTrue(AssetCleanup.removeUnused(assets.resolve("missing"), List.of(), NOW, GRACE).isEmpty());
        Path old = stored('a', "png", Duration.ofDays(2));
        Design bundled = new Design();
        bundled.objectImage = "bundled:cat.svg";
        assertEquals(1, AssetCleanup.removeUnused(assets, List.of(bundled), NOW, GRACE).size());
        assertFalse(Files.exists(old));
    }

    @Test void importingAnExistingImageAgainMarksItAsInUse() throws IOException {
        byte[] svg;
        try (var in = getClass().getResourceAsStream("/art/cat.svg")) { svg = in.readAllBytes(); }
        Path stored = Path.of(Artwork.importBytes(svg, "svg", assets));
        Files.setLastModifiedTime(stored, FileTime.from(Instant.now().minus(Duration.ofDays(5))));
        Artwork.importBytes(svg, "svg", assets);
        assertTrue(AssetCleanup.removeUnused(assets, List.of(), Instant.now(), GRACE).isEmpty());
        assertTrue(Files.exists(stored));
    }
}
