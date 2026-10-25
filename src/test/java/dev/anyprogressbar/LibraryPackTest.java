package dev.anyprogressbar;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryPackTest {
    @TempDir Path temp;

    private Design customDesign(String name, int shade) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff000000 | shade);
        Path png = temp.resolve("src-" + shade + ".png");
        ImageIO.write(image, "png", png.toFile());
        Design d = new Design();
        d.name = name;
        d.objectImage = Artwork.importFile(png, temp.resolve("assets-a"));
        d.trailImage = "bundled:stars.svg";
        d.backgroundColor = "#123456";
        d.motion = Design.Motion.HOP;
        return d;
    }

    private Path pack(Map<String, byte[]> entries) throws IOException {
        Path file = temp.resolve("lib-" + System.nanoTime() + "." + LibraryPack.EXTENSION);
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test void roundTripsTheWholeLibraryWithAssetsAndTheActiveDesign() throws IOException {
        var library = new ArrayList<>(Design.presets());
        Design mine = customDesign("Mine åäö", 0x123456);
        library.add(mine);
        Path file = temp.resolve("all." + LibraryPack.EXTENSION);
        LibraryPack.export(library, mine.id, file);

        var imported = LibraryPack.importFrom(file, temp.resolve("assets-b"));
        assertEquals(library.size(), imported.designs().size());
        assertEquals(library.size() - 1, imported.activeIndex());
        Design copy = imported.active();
        assertEquals("Mine åäö", copy.name);
        assertNotEquals(mine.id, copy.id);
        assertTrue(copy.objectImage.startsWith(temp.resolve("assets-b").toString()), "assets are copied into the importing machine's folder");
        assertNotNull(Artwork.load(copy.objectImage));
        assertEquals("bundled:stars.svg", copy.trailImage);
        for (int i = 0; i < library.size(); i++) {
            assertTrue(library.get(i).sameLook(imported.designs().get(i)), library.get(i).name);
        }
    }

    @Test void importingTheSameLibraryAgainIsRecognisedAsDuplicates() throws IOException {
        var library = new ArrayList<>(Design.presets());
        library.add(customDesign("Mine", 0x654321));
        Path file = temp.resolve("all." + LibraryPack.EXTENSION);
        LibraryPack.export(library, library.getFirst().id, file);
        var imported = LibraryPack.importFrom(file, temp.resolve("assets-a"));
        var plan = LibraryPack.planMerge(library, imported.designs());
        assertEquals(library.size(), plan.duplicates());
        assertTrue(plan.additions().isEmpty());
    }

    @Test void mergeAddsOnlyDesignsThatAreNew() throws IOException {
        Design shared = customDesign("Shared", 0x111111);
        Design fresh = customDesign("Fresh", 0x222222);
        Design edited = shared.copy();
        edited.name = "Shared but renamed";
        var existing = List.of(shared);
        var plan = LibraryPack.planMerge(existing, List.of(shared.copy(), fresh, edited, fresh.copy()));
        assertEquals(2, plan.duplicates());
        assertEquals(List.of("Fresh", "Shared but renamed"), plan.additions().stream().map(d -> d.name).toList());
    }

    @Test void aBrokenDesignStopsTheExportAndLeavesAnExistingFileUntouched() throws IOException {
        Path file = temp.resolve("keep." + LibraryPack.EXTENSION);
        Files.writeString(file, "previous");
        Design broken = new Design();
        broken.name = "Broken";
        broken.objectImage = temp.resolve("missing.png").toString();
        var e = assertThrows(IOException.class, () -> LibraryPack.export(List.of(Design.presets().getFirst(), broken), null, file));
        assertTrue(e.getMessage().contains("Broken"), e.getMessage());
        assertEquals("previous", Files.readString(file));
        try (var files = Files.list(temp)) {
            assertEquals(0, files.filter(p -> p.getFileName().toString().startsWith(".apb-export")).count());
        }
        assertThrows(IllegalArgumentException.class, () -> LibraryPack.export(List.of(), null, file));
    }

    @Test void rejectsFilesThatAreNotLibraries() throws IOException {
        Path junk = temp.resolve("junk." + LibraryPack.EXTENSION);
        Files.writeString(junk, "not a zip");
        assertThrows(IOException.class, () -> LibraryPack.importFrom(junk, temp.resolve("assets")));
        var noDescriptor = new LinkedHashMap<String, byte[]>();
        noDescriptor.put("readme.txt", new byte[0]);
        var e = assertThrows(IOException.class, () -> LibraryPack.importFrom(pack(noDescriptor), temp.resolve("assets")));
        assertTrue(e.getMessage().contains("library.properties"));
    }

    @Test void rejectsUnsupportedFormatsAndMismatchedCounts() throws IOException {
        Path good = temp.resolve("good." + LibraryPack.EXTENSION);
        LibraryPack.export(List.of(Design.presets().getFirst()), null, good);
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(good))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) entries.put(entry.getName(), zip.readAllBytes());
        }
        var future = new LinkedHashMap<>(entries);
        future.put("library.properties", "format=99\ncount=1\n".getBytes(StandardCharsets.ISO_8859_1));
        var e = assertThrows(IOException.class, () -> LibraryPack.importFrom(pack(future), temp.resolve("assets")));
        assertTrue(e.getMessage().contains("format 99"));
        var wrongCount = new LinkedHashMap<>(entries);
        wrongCount.put("library.properties", "format=1\ncount=5\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(IOException.class, () -> LibraryPack.importFrom(pack(wrongCount), temp.resolve("assets")));
        var empty = new LinkedHashMap<String, byte[]>();
        empty.put("library.properties", "format=1\ncount=0\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(IOException.class, () -> LibraryPack.importFrom(pack(empty), temp.resolve("assets")));
    }

    @Test void anInvalidDesignInsideRejectsTheWholeLibrary() throws IOException {
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("design-0.apbdesign", new byte[]{1, 2, 3});
        entries.put("library.properties", "format=1\ncount=1\n".getBytes(StandardCharsets.ISO_8859_1));
        var e = assertThrows(IOException.class, () -> LibraryPack.importFrom(pack(entries), temp.resolve("assets")));
        assertTrue(e.getMessage().contains("design-0"), e.getMessage());
    }

    @Test void limitsEntriesDuplicatesAndTotalSize() throws IOException {
        var many = new LinkedHashMap<String, byte[]>();
        many.put("library.properties", "format=1\ncount=1\n".getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 0; i < LibraryPack.MAX_DESIGNS + 5; i++) many.put("junk" + i + ".txt", new byte[1]);
        var e = assertThrows(IOException.class, () -> LibraryPack.importFrom(pack(many), temp.resolve("assets")));
        assertTrue(e.getMessage().contains("too many"));
        var bigDescriptor = new LinkedHashMap<String, byte[]>();
        bigDescriptor.put("library.properties", new byte[64 * 1024 + 1]);
        assertThrows(IOException.class, () -> LibraryPack.importFrom(pack(bigDescriptor), temp.resolve("assets")));
    }
}
