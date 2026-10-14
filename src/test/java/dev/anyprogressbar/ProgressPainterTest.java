package dev.anyprogressbar;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProgressPainterTest {
    @TempDir Path temp;

    @Test void allUniquePresetsHaveWorkingArtworkAndAnimation() throws IOException {
        var presets = Design.presets();
        assertTrue(presets.size() >= 8);
        assertEquals(presets.size(), presets.stream().map(d -> d.id).distinct().count());
        for (Design d : presets) {
            d.validate();
            assertNotNull(Artwork.load(d.objectImage));
            assertNotNull(Artwork.load(d.trailImage));
            ProgressPainter painter = new ProgressPainter(d);
            BufferedImage a = render(painter, 0.3, true, 0, true);
            BufferedImage b = render(painter, 0.3, true, 1, true);
            assertFalse(java.util.Arrays.equals(pixels(a), pixels(b)), d.name + " must animate");
        }
    }

    @Test void everyCombinationOfOptionalLayersCanRender() throws IOException {
        Design preset = Design.presets().getFirst();
        for (int mask = 0; mask < 16; mask++) {
            Design d = preset.copy();
            if ((mask & 1) == 0) d.objectImage = "";
            if ((mask & 2) == 0) d.trailImage = "";
            d.backgroundImage = (mask & 4) == 0 ? "" : "bundled:bubbles.svg";
            if ((mask & 8) == 0) d.backgroundColor = "";
            ProgressPainter painter = new ProgressPainter(d);
            for (boolean indeterminate : new boolean[]{false, true}) {
                BufferedImage result = render(painter, 0.5, indeterminate, 1, true);
                assertNotEquals(0, result.getRGB(160, 16));
            }
        }
    }

    @Test void colorOnlyDesignDoesNotInventObjectOrTrail() throws IOException {
        Design d = new Design();
        d.backgroundColor = "#123456";
        BufferedImage result = render(new ProgressPainter(d), 0.6, false, 1, true);
        assertEquals(Color.decode("#123456").getRGB(), result.getRGB(40, 16));
        assertEquals(result.getRGB(40, 16), result.getRGB(200, 16));
    }

    @Test void emptyDesignUsesIdeBackground() throws IOException {
        BufferedImage result = render(new ProgressPainter(new Design()), 0.5, false, 1, true);
        assertEquals(Color.DARK_GRAY.getRGB(), result.getRGB(100, 16));
    }

    @Test void determinatePositionTracksProgressAndSpeedOnlyAffectsIndeterminate() {
        assertEquals(0, ProgressPainter.position(false, -1, 100, 400));
        assertEquals(1, ProgressPainter.position(false, 2, 100, 400));
        assertEquals(0.42, ProgressPainter.position(false, 0.42, 8, 10));
        assertEquals(0.25, ProgressPainter.position(true, 0, 1, 100));
        assertEquals(0.5, ProgressPainter.position(true, 0, 1, 200));
        assertEquals(0, ProgressPainter.position(true, 0, 4, 100));
    }

    @Test void rightToLeftMirrorsProgress() throws IOException {
        Design d = Design.presets().getFirst();
        d.motion = Design.Motion.GLIDE;
        ProgressPainter painter = new ProgressPainter(d);
        BufferedImage ltr = render(painter, 0.25, false, 0, true);
        BufferedImage rtl = render(painter, 0.25, false, 0, false);
        assertNotEquals(ltr.getRGB(75, 16), rtl.getRGB(75, 16));
        assertEquals(ltr.getRGB(75, 16), rtl.getRGB(244, 16));
    }

    @Test void importsAreDeduplicatedAndIndependentOfOriginals() throws IOException {
        for (String extension : new String[]{"png", "jpg"}) {
            Path original = temp.resolve("source." + extension);
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            assertTrue(ImageIO.write(image, extension, original.toFile()));
            Path assets = temp.resolve("assets");
            String imported = Artwork.importFile(original, assets);
            assertEquals(imported, Artwork.importFile(original, assets));
            Files.delete(original);
            assertNotNull(Artwork.load(imported));
        }
        Path svg = temp.resolve("source.svg");
        Files.copy(getClass().getResourceAsStream("/art/cat.svg"), svg);
        String imported = Artwork.importFile(svg, temp.resolve("assets"));
        Files.delete(svg);
        assertNotNull(Artwork.load(imported));
    }

    @Test void malformedAndMissingImagesAreExplicitErrors() throws IOException {
        Path bad = temp.resolve("bad.png");
        Files.writeString(bad, "not an image");
        assertThrows(IOException.class, () -> Artwork.importFile(bad, temp.resolve("assets")));
        assertThrows(IOException.class, () -> Artwork.load(temp.resolve("missing.svg").toString()));
        Path text = temp.resolve("image.txt");
        Files.writeString(text, "text");
        assertThrows(IOException.class, () -> Artwork.importFile(text, temp.resolve("assets")));
    }

    @Test void svgRejectsExternalReferencesAndEntitiesButAllowsLocalGradients() throws IOException {
        Path svg = temp.resolve("external.svg");
        Files.writeString(svg, """
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
              <image href="https://example.com/image.png"/>
            </svg>
            """);
        assertThrows(IOException.class, () -> Artwork.load(svg.toString()));
        Files.writeString(svg, """
            <!DOCTYPE svg [<!ENTITY file SYSTEM "file:///does-not-exist">]>
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">&file;</svg>
            """);
        assertThrows(IOException.class, () -> Artwork.load(svg.toString()));
        Files.writeString(svg, """
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
              <defs><linearGradient id="g"><stop stop-color="red"/><stop offset="1" stop-color="blue"/></linearGradient></defs>
              <rect width="10" height="10" fill="url(#g)"/>
            </svg>
            """);
        assertNotNull(Artwork.load(svg.toString()));
    }

    @Test void validationRejectsInvalidRangesAndColors() {
        Design d = new Design();
        d.backgroundColor = "blue";
        assertThrows(IllegalArgumentException.class, d::validate);
        d.backgroundColor = "";
        d.speed = 0;
        assertThrows(IllegalArgumentException.class, d::validate);
        d.speed = 100;
        d.size = 65;
        assertThrows(IllegalArgumentException.class, d::validate);
        d.size = 24;
        d.name = "";
        assertThrows(IllegalArgumentException.class, d::validate);
    }

    @Test void copyingDesignDoesNotMutateOriginal() {
        Design original = Design.presets().getFirst();
        Design copy = original.copy();
        copy.name = "Changed";
        copy.objectImage = "";
        assertNotEquals(copy.name, original.name);
        assertFalse(original.objectImage.isEmpty());
        assertEquals(original.id, copy.id);
    }

    @Test void tinyBarsAndAllMotionModesRenderWithoutErrors() throws IOException {
        for (Design.Motion motion : Design.Motion.values()) {
            Design d = Design.presets().getFirst();
            d.motion = motion;
            ProgressPainter painter = new ProgressPainter(d);
            var g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                painter.paint(g, 1, 1, 1, false, 1, Color.GRAY, true);
                painter.paint(g, 0, 0, 0, false, 1, Color.GRAY, false);
            } finally { g.dispose(); }
        }
    }

    private static BufferedImage render(ProgressPainter painter, double progress, boolean indeterminate,
                                        double seconds, boolean leftToRight) {
        BufferedImage image = new BufferedImage(320, 32, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { painter.paint(g, 320, 32, progress, indeterminate, seconds, Color.DARK_GRAY, leftToRight); }
        finally { g.dispose(); }
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test void lenientPainterSkipsBrokenLayersButStillRenders() throws IOException {
        Design d = Design.presets().getFirst().copy();
        d.trailImage = temp.resolve("deleted.png").toString();
        assertThrows(IOException.class, () -> new ProgressPainter(d));
        ProgressPainter painter = ProgressPainter.lenient(d);
        assertEquals(1, painter.skippedLayers().size());
        assertTrue(painter.skippedLayers().getFirst().startsWith("Trail image"));
        assertNotNull(render(painter, 0.5, false, 1, true));
        assertTrue(ProgressPainter.lenient(Design.presets().getFirst()).skippedLayers().isEmpty());
    }

    @Test void designPackRoundTripsCustomAndBundledImages() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Path png = temp.resolve("object.png");
        ImageIO.write(image, "png", png.toFile());
        Design d = new Design();
        d.name = "Shared \u00e5\u00e4\u00f6";
        d.objectImage = Artwork.importFile(png, temp.resolve("assets-a"));
        d.trailImage = "bundled:stars.svg";
        d.backgroundColor = "#123456";
        d.motion = Design.Motion.BOUNCE;
        d.speed = 150;
        Path pack = temp.resolve("shared." + DesignPack.EXTENSION);
        DesignPack.export(d, pack);
        Design imported = DesignPack.importFrom(pack, temp.resolve("assets-b"));
        assertNotEquals(d.id, imported.id);
        assertEquals(d.name, imported.name);
        assertEquals("bundled:stars.svg", imported.trailImage);
        assertEquals("", imported.backgroundImage);
        assertEquals("#123456", imported.backgroundColor);
        assertEquals(Design.Motion.BOUNCE, imported.motion);
        assertEquals(150, imported.speed);
        assertTrue(imported.objectImage.startsWith(temp.resolve("assets-b").toString()));
        assertNotNull(Artwork.load(imported.objectImage));
    }

    @Test void designPackRejectsGarbage() throws IOException {
        Path junk = temp.resolve("junk." + DesignPack.EXTENSION);
        Files.writeString(junk, "not a zip");
        assertThrows(IOException.class, () -> DesignPack.importFrom(junk, temp.resolve("assets")));
    }
}
