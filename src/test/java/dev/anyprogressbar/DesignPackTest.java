package dev.anyprogressbar;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesignPackTest {
    @TempDir Path temp;

    private static byte[] png() throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] text(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }

    /** Writes a zip with the given entries in insertion order. */
    private Path pack(Map<String, byte[]> entries) throws IOException {
        Path file = Files.createTempFile(temp, "pack", "." + DesignPack.EXTENSION);
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    private Path assets() { return temp.resolve("assets"); }

    private static Map<String, byte[]> entries(String descriptor) {
        var map = new LinkedHashMap<String, byte[]>();
        map.put("design.properties", text(descriptor));
        return map;
    }

    @Test void importsAMinimalPackWithDefaults() throws IOException {
        Design d = DesignPack.importFrom(pack(entries("name=Tiny\n")), assets());
        assertEquals("Tiny", d.name);
        assertEquals(24, d.size);
        assertEquals(100, d.speed);
        assertEquals(160, d.trailLength);
        assertEquals(Design.Motion.GLIDE, d.motion);
        assertEquals("", d.objectImage);
    }

    @Test void rejectsPackWithoutDescriptor() throws IOException {
        var map = new LinkedHashMap<String, byte[]>();
        map.put("object.png", png());
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("design.properties"));
    }

    @Test void rejectsOversizedEntries() throws IOException {
        var map = entries("name=Big\nobject=file:object.png\n");
        map.put("object.png", new byte[10 * 1024 * 1024 + 1]);
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("10 MB"));
        assertFalse(Files.exists(assets()));
    }

    @Test void rejectsOversizedDescriptor() throws IOException {
        var map = new LinkedHashMap<String, byte[]>();
        map.put("design.properties", new byte[10 * 1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
    }

    @Test void rejectsDescriptorsBeyondTheDescriptorLimit() throws IOException {
        var map = entries("name=Big\n#" + "x".repeat(64 * 1024) + "\n");
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("descriptor"));
        assertNotNull(DesignPack.importFrom(pack(entries("name=Fine\n#" + "x".repeat(60 * 1024) + "\n")), assets()));
    }

    @Test void rejectsTooManyEntriesEvenIfUnrecognised() throws IOException {
        var map = entries("name=Many\n");
        for (int i = 0; i < 40; i++) map.put("extra" + i + ".txt", new byte[1]);
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("too many entries"));
    }

    @Test void unrecognisedEntriesCountTowardsTheTotalSize() throws IOException {
        var map = entries("name=Bomb\n");
        for (int i = 0; i < 5; i++) map.put("junk" + i + ".bin", new byte[9 * 1024 * 1024]);
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("in total"));
    }

    @Test void retainedImagesAreBoundedAcrossLayers() throws IOException {
        var map = entries("name=Heavy\nobject=file:object.png\n");
        for (String name : new String[]{"object.png", "object.jpg", "object.svg", "trail.png"}) {
            map.put(name, new byte[9 * 1024 * 1024]);
        }
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("images may total"));
        assertFalse(Files.exists(assets()));
    }

    @Test void rejectsDuplicateEntries() throws IOException {
        Path file = Files.createTempFile(temp, "dup", "." + DesignPack.EXTENSION);
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String name : new String[]{"design.properties", "design.properties"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(text("name=Twice\n"));
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException e) {
            return; // the JDK refuses to write duplicates; nothing to import
        }
        assertThrows(IOException.class, () -> DesignPack.importFrom(file, assets()));
    }

    @Test void rejectsOverlongNamesOnImportAndExport() throws IOException {
        String longName = "n".repeat(DesignPack.MAX_NAME_LENGTH + 1);
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(entries("name=" + longName + "\n")), assets()));
        assertTrue(e.getMessage().contains("name"));
        Design design = new Design();
        design.name = longName;
        assertThrows(IllegalArgumentException.class, () -> DesignPack.export(design, temp.resolve("long." + DesignPack.EXTENSION)));
        design.name = "n".repeat(DesignPack.MAX_NAME_LENGTH);
        Path ok = temp.resolve("ok." + DesignPack.EXTENSION);
        DesignPack.export(design, ok);
        assertEquals(design.name, DesignPack.importFrom(ok, assets()).name);
    }

    @Test void rejectsReferenceToMissingImage() throws IOException {
        var map = entries("name=Broken\nobject=file:object.png\n");
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("missing image"));
    }

    @Test void rejectsUnknownBundledArtwork() throws IOException {
        var map = entries("name=Ghost\nobject=bundled:does-not-exist.svg\n");
        var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        assertTrue(e.getMessage().contains("Unknown bundled artwork"));
    }

    @Test void rejectsBundledReferencesThatEscapeTheArtFolder() throws IOException {
        for (String bad : new String[] {
            "../META-INF/plugin.xml", "../META-INF/pluginIcon.svg", "stars.svg/../../META-INF/pluginIcon.svg",
            "..\\META-INF\\pluginIcon.svg", "/META-INF/pluginIcon.svg", "plugin.xml", "stars"}) {
            var map = entries("name=Escape\nobject=bundled:" + bad.replace("\\", "\\\\") + "\n");
            var e = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()), bad);
            assertTrue(e.getMessage().contains("bundled artwork"), bad + ": " + e.getMessage());
        }
    }

    @Test void rejectsReferenceToArbitraryFilesystemPaths() throws IOException {
        Path secret = Files.writeString(temp.resolve("secret.png"), "x");
        var map = entries("name=Sneaky\nobject=" + secret.toString().replace("\\", "\\\\") + "\n");
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
    }

    @Test void entriesAreReadByFixedNameOnlyAndNeverExtractedByPath() throws IOException {
        var map = entries("name=Traversal\nobject=file:object.png\n");
        map.put("object.png", png());
        map.put("../escape.png", png());
        map.put("object/../../escape2.png", png());
        map.put("/abs/object.png", png());
        map.put("object.sub/evil.png", png());
        map.put("notes.txt", text("ignored"));
        Design d = DesignPack.importFrom(pack(map), assets());
        assertTrue(d.objectImage.startsWith(assets().toString()));
        try (Stream<Path> files = Files.walk(temp)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().startsWith("escape")),
                "no entry may be written outside the assets directory");
        }
        try (Stream<Path> files = Files.list(assets())) { assertEquals(1, files.count()); }
    }

    @Test void importedImagesAreStoredByContentHash() throws IOException {
        var map = entries("name=Twice\nobject=file:object.png\ntrail=file:trail.png\n");
        map.put("object.png", png());
        map.put("trail.png", png());
        Design d = DesignPack.importFrom(pack(map), assets());
        assertEquals(d.objectImage, d.trailImage, "identical images share one stored file");
        Design again = DesignPack.importFrom(pack(map), assets());
        assertEquals(d.objectImage, again.objectImage);
        assertNotEquals(d.id, again.id);
        try (Stream<Path> files = Files.list(assets())) { assertEquals(1, files.count()); }
    }

    @Test void rejectsInvalidDesignValues() throws IOException {
        for (String bad : new String[] {
            "size=9999", "size=abc", "speed=1", "trailLength=-5", "motion=TELEPORT", "backgroundColor=red"}) {
            var map = entries("name=Bad\n" + bad + "\n");
            assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()), bad);
        }
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(entries("name=\n")), assets()));
    }

    @Test void rejectsHostileSvgAndLeavesNothingBehind() throws IOException {
        var map = entries("name=Evil\nobject=file:object.svg\n");
        map.put("object.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
        if (Files.exists(assets())) {
            try (Stream<Path> files = Files.list(assets())) { assertEquals(0, files.count()); }
        }
    }

    @Test void rejectsSvgWithExternalReference() throws IOException {
        var map = entries("name=Evil\nobject=file:object.svg\n");
        map.put("object.svg", ("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
            + "width=\"10\" height=\"10\"><image xlink:href=\"file:///etc/passwd\" width=\"10\" height=\"10\"/></svg>")
            .getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
    }

    @Test void rejectsImageThatIsNotAnImage() throws IOException {
        var map = entries("name=Fake\nobject=file:object.png\n");
        map.put("object.png", text("this is not a png"));
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
    }

    @Test void rejectsUnsupportedImageExtensions() throws IOException {
        var map = entries("name=Exe\nobject=file:object.exe\n");
        map.put("object.exe", png());
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(map), assets()));
    }

    @Test void exportRejectsInvalidDesignsAndMissingImages() throws IOException {
        Design invalid = new Design();
        invalid.name = " ";
        assertThrows(IllegalArgumentException.class, () -> DesignPack.export(invalid, temp.resolve("a.apbdesign")));
        Design missing = new Design();
        missing.objectImage = temp.resolve("gone.png").toString();
        assertThrows(IOException.class, () -> DesignPack.export(missing, temp.resolve("b.apbdesign")));
    }

    @Test void exportedPackContainsOnlyTheDescriptorAndLayerImages() throws IOException {
        Path image = Files.write(temp.resolve("o.png"), png());
        Design d = new Design();
        d.name = "Names: with = special # chars";
        d.objectImage = Artwork.importFile(image, temp.resolve("assets-x"));
        d.trailImage = "bundled:stars.svg";
        Path out = temp.resolve("out.apbdesign");
        DesignPack.export(d, out);
        try (var zip = new java.util.zip.ZipFile(out.toFile())) {
            var names = zip.stream().map(ZipEntry::getName).sorted().toList();
            assertEquals(java.util.List.of("design.properties", "object.png"), names);
        }
        assertEquals(d.name, DesignPack.importFrom(out, assets()).name);
    }

    @Test void failedExportLeavesAnExistingFileUntouched() throws IOException {
        Path out = Files.writeString(temp.resolve("keep.apbdesign"), "previous contents");
        Design missing = new Design();
        missing.objectImage = temp.resolve("gone.png").toString();
        assertThrows(IOException.class, () -> DesignPack.export(missing, out));
        assertEquals("previous contents", Files.readString(out));
        try (Stream<Path> files = Files.list(temp)) {
            assertTrue(files.noneMatch(f -> f.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test void exportReplacesAnExistingFileOnSuccess() throws IOException {
        Path out = Files.writeString(temp.resolve("replace.apbdesign"), "previous contents");
        Design d = new Design();
        d.name = "New";
        DesignPack.export(d, out);
        assertEquals("New", DesignPack.importFrom(out, assets()).name);
    }

    @Test void acceptsCurrentAndUnversionedPacks() throws IOException {
        assertEquals("A", DesignPack.importFrom(pack(entries("format=1\nname=A\n")), assets()).name);
        assertEquals("B", DesignPack.importFrom(pack(entries("name=B\n")), assets()).name);
    }

    @Test void rejectsUnsupportedPackFormats() throws IOException {
        var newer = assertThrows(IOException.class, () -> DesignPack.importFrom(pack(entries("format=999\nname=X\n")), assets()));
        assertTrue(newer.getMessage().contains("format 999"));
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(entries("format=0\nname=X\n")), assets()));
        assertThrows(IOException.class, () -> DesignPack.importFrom(pack(entries("format=abc\nname=X\n")), assets()));
    }
}
