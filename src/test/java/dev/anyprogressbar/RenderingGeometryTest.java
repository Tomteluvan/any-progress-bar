package dev.anyprogressbar;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntPredicate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Pixel- and geometry-level checks of what the painter actually draws, not just that something was drawn. */
class RenderingGeometryTest {
    private static final int WIDTH = 320;
    private static final Color BLACK = Color.BLACK;

    @TempDir Path temp;

    private record Bounds(int minX, int maxX, int minY, int maxY, int count) {
        double centerX() { return (minX + maxX + 1) / 2.0; }
        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
    }

    private static boolean red(int argb) {
        return ((argb >> 16) & 0xff) > 160 && ((argb >> 8) & 0xff) < 80 && (argb & 0xff) < 80 && (argb >>> 24) == 0xff;
    }
    private static boolean blue(int argb) {
        return (argb & 0xff) > 160 && ((argb >> 16) & 0xff) < 80 && ((argb >> 8) & 0xff) < 80 && (argb >>> 24) == 0xff;
    }
    private static boolean green(int argb) {
        return ((argb >> 8) & 0xff) > 160 && ((argb >> 16) & 0xff) < 80 && (argb & 0xff) < 80 && (argb >>> 24) == 0xff;
    }

    private static Bounds bounds(BufferedImage image, IntPredicate matches) {
        int minX = Integer.MAX_VALUE, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1, count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!matches.test(image.getRGB(x, y))) continue;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                count++;
            }
        }
        return new Bounds(minX, maxX, minY, maxY, count);
    }

    private Path svg(String name, String body, int width, int height) throws IOException {
        Path file = temp.resolve(name);
        Files.writeString(file, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height
            + "\" viewBox=\"0 0 " + width + " " + height + "\">" + body + "</svg>");
        return file;
    }

    private Path redSquare() throws IOException {
        return svg("red.svg", "<rect width=\"10\" height=\"10\" fill=\"#ff0000\"/>", 10, 10);
    }

    /** Red on the left half, blue on the right half: shows whether the object has been mirrored. */
    private Path redBlue() throws IOException {
        return svg("redblue.svg", "<rect width=\"5\" height=\"10\" fill=\"#ff0000\"/><rect x=\"5\" width=\"5\" height=\"10\" fill=\"#0000ff\"/>", 10, 10);
    }

    private Path greenTile() throws IOException {
        return svg("green.svg", "<rect width=\"10\" height=\"10\" fill=\"#00ff00\"/>", 10, 10);
    }

    private static Design design(Path object, int size) {
        Design d = new Design();
        d.objectImage = object == null ? "" : object.toString();
        d.backgroundColor = "#000000";
        d.size = size;
        d.motion = Design.Motion.GLIDE;
        return d;
    }

    private static BufferedImage render(Design design, int width, int height, double progress, boolean indeterminate,
                                        double seconds, boolean leftToRight, Color fallback) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { new ProgressPainter(design).paint(g, width, height, progress, indeterminate, seconds, fallback, leftToRight); }
        finally { g.dispose(); }
        return image;
    }

    @Test void determinateObjectStartsAtTheLeftEdgeEndsAtTheRightEdgeAndCentresInTheMiddle() throws IOException {
        Design d = design(redSquare(), 20);
        Bounds start = bounds(render(d, WIDTH, 32, 0, false, 0, true, BLACK), RenderingGeometryTest::red);
        Bounds middle = bounds(render(d, WIDTH, 32, 0.5, false, 0, true, BLACK), RenderingGeometryTest::red);
        Bounds end = bounds(render(d, WIDTH, 32, 1, false, 0, true, BLACK), RenderingGeometryTest::red);
        assertEquals(0, start.minX(), 1);
        assertEquals(20, start.width(), 2);
        assertEquals(WIDTH / 2.0, middle.centerX(), 1.5);
        assertEquals(WIDTH - 1, end.maxX(), 1);
        assertEquals(20, end.width(), 2);
        // the object is centred vertically and never taller than requested
        assertEquals(20, middle.height(), 2);
        assertEquals(16.0, (middle.minY() + middle.maxY() + 1) / 2.0, 1);
    }

    @Test void progressOutsideZeroToOneStaysOnTheBar() throws IOException {
        Design d = design(redSquare(), 20);
        Bounds below = bounds(render(d, WIDTH, 32, -3, false, 0, true, BLACK), RenderingGeometryTest::red);
        Bounds above = bounds(render(d, WIDTH, 32, 7, false, 0, true, BLACK), RenderingGeometryTest::red);
        assertEquals(0, below.minX(), 1);
        assertEquals(WIDTH - 1, above.maxX(), 1);
    }

    @Test void compactBarsMirrorTheObjectPositionForRightToLeft() throws IOException {
        Design d = design(redSquare(), 14);
        for (double progress : new double[]{0.1, 0.25, 0.5, 0.8}) {
            Bounds ltr = bounds(render(d, WIDTH, 16, progress, false, 0, true, BLACK), RenderingGeometryTest::red);
            Bounds rtl = bounds(render(d, WIDTH, 16, progress, false, 0, false, BLACK), RenderingGeometryTest::red);
            assertTrue(ltr.count() > 0 && rtl.count() > 0, "object must be visible at " + progress);
            assertEquals(WIDTH, ltr.centerX() + rtl.centerX(), 1.5, "RTL must mirror LTR at " + progress);
        }
    }

    @Test void rightToLeftFlipsTheArtworkItself() throws IOException {
        Design d = design(redBlue(), 20);
        BufferedImage ltr = render(d, WIDTH, 32, 0.5, false, 0, true, BLACK);
        BufferedImage rtl = render(d, WIDTH, 32, 0.5, false, 0, false, BLACK);
        assertTrue(bounds(ltr, RenderingGeometryTest::red).centerX() < bounds(ltr, RenderingGeometryTest::blue).centerX());
        assertTrue(bounds(rtl, RenderingGeometryTest::red).centerX() > bounds(rtl, RenderingGeometryTest::blue).centerX());
    }

    @Test void pingPongFlipsTheObjectOnTheReturnLegAndKeepsTheTrailNearIt() throws IOException {
        Design d = design(redBlue(), 20);
        d.motion = Design.Motion.PING_PONG;
        d.trailImage = greenTile().toString();
        d.trailLength = 100;
        d.speed = 100; // one leg takes 4 seconds
        BufferedImage out = render(d, WIDTH, 32, 0, true, 1.5, true, BLACK);
        BufferedImage back = render(d, WIDTH, 32, 0, true, 6.5, true, BLACK);
        Bounds outRed = bounds(out, RenderingGeometryTest::red), outBlue = bounds(out, RenderingGeometryTest::blue);
        Bounds backRed = bounds(back, RenderingGeometryTest::red), backBlue = bounds(back, RenderingGeometryTest::blue);
        assertTrue(outRed.centerX() < outBlue.centerX(), "outbound object faces right");
        assertTrue(backRed.centerX() > backBlue.centerX(), "returning object faces left");
        // 1.5 s out and 6.5 s (a lap and a half later, travelling back) are at the same position on the bar
        assertEquals((outRed.minX() + outBlue.maxX() + 1) / 2.0, (backBlue.minX() + backRed.maxX() + 1) / 2.0, 2.0);
        for (BufferedImage frame : new BufferedImage[]{out, back}) {
            Bounds trail = bounds(frame, RenderingGeometryTest::green);
            assertTrue(trail.count() > 0, "trail must be drawn");
            assertTrue(trail.width() <= 100 + 4, "trail is no longer than trailLength, was " + trail.width());
        }
        Bounds outTrail = bounds(out, RenderingGeometryTest::green);
        assertTrue(outTrail.maxX() <= outBlue.maxX(), "outbound trail trails the object");
    }

    @Test void trailLengthZeroDrawsNoTrail() throws IOException {
        Design d = design(redSquare(), 20);
        d.trailImage = greenTile().toString();
        d.trailLength = 0;
        assertEquals(0, bounds(render(d, WIDTH, 32, 0.5, false, 0, true, BLACK), RenderingGeometryTest::green).count());
        d.trailLength = 80;
        Bounds trail = bounds(render(d, WIDTH, 32, 0.5, false, 0, true, BLACK), RenderingGeometryTest::green);
        assertTrue(trail.count() > 0);
    }

    @Test void nothingIsDrawnOutsideTheBarAndTheEndsArePillShaped() throws IOException {
        Design d = design(redSquare(), 32);
        d.trailImage = greenTile().toString();
        d.trailLength = 600;
        int pad = 10;
        BufferedImage canvas = new BufferedImage(WIDTH + 2 * pad, 32 + 2 * pad, BufferedImage.TYPE_INT_ARGB);
        var g = canvas.createGraphics();
        try {
            g.translate(pad, pad);
            new ProgressPainter(d).paint(g, WIDTH, 32, 1, false, 0, BLACK, true);
        } finally { g.dispose(); }
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                boolean inside = x >= pad && x < pad + WIDTH && y >= pad && y < pad + 32;
                if (!inside) assertEquals(0, canvas.getRGB(x, y) >>> 24, "pixel outside the bar at " + x + "," + y);
            }
        }
        assertEquals(0, canvas.getRGB(pad, pad) >>> 24, "top-left corner is rounded away");
        assertEquals(0, canvas.getRGB(pad + WIDTH - 1, pad + 31) >>> 24, "bottom-right corner is rounded away");
        assertEquals(0xff, canvas.getRGB(pad + WIDTH / 2, pad + 16) >>> 24, "the middle is covered");
        assertEquals(0xff, canvas.getRGB(pad, pad + 16) >>> 24, "the end of the pill is covered at mid height");
    }

    @Test void transparentFallbackLeavesEmptyAreasTransparentAndKeepsTheObjectOpaque() throws IOException {
        Design d = new Design(); // IDE colour, so the transparent fallback applies
        d.objectImage = redSquare().toString();
        d.size = 20;
        BufferedImage image = render(d, WIDTH, 32, 0.5, false, 0, true, new Color(0, 0, 0, 0));
        assertEquals(0, image.getRGB(20, 16) >>> 24, "empty bar area stays transparent");
        assertEquals(0, image.getRGB(WIDTH - 20, 16) >>> 24);
        assertTrue(red(image.getRGB(WIDTH / 2, 16)), "the object itself is opaque");
    }

    @Test void transparentPixelsInArtworkShowTheBarColourBehindThem() throws IOException {
        BufferedImage art = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++) for (int x = 5; x < 10; x++) art.setRGB(x, y, 0xffff0000);
        Path png = temp.resolve("half.png");
        ImageIO.write(art, "png", png.toFile());
        Design d = design(png, 20);
        d.backgroundColor = "#00ff00";
        BufferedImage image = render(d, WIDTH, 32, 0.5, false, 0, true, BLACK);
        Bounds object = bounds(image, RenderingGeometryTest::red);
        int y = 16;
        assertTrue(green(image.getRGB(object.minX() - 14, y)), "bar colour beside the object");
        assertTrue(green(image.getRGB(object.minX() - 4, y)), "transparent half shows the bar colour");
        assertTrue(red(image.getRGB(object.maxX() - 2, y)), "opaque half is drawn");
    }

    @Test void rasterObjectsScaleToTheRequestedHeightKeepingTheirAspectRatio() throws IOException {
        for (int[] source : new int[][]{{100, 100}, {20, 10}, {10, 40}}) {
            BufferedImage art = new BufferedImage(source[0], source[1], BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source[1]; y++) for (int x = 0; x < source[0]; x++) art.setRGB(x, y, 0xffff0000);
            Path png = temp.resolve("raster-" + source[0] + "x" + source[1] + ".png");
            ImageIO.write(art, "png", png.toFile());
            Design d = design(png, 24);
            Bounds object = bounds(render(d, WIDTH, 32, 0.5, false, 0, true, BLACK), RenderingGeometryTest::red);
            double aspect = (double) source[0] / source[1];
            assertEquals(24, object.height(), 2, source[0] + "x" + source[1] + " height");
            assertEquals(24 * aspect, object.width(), 2, source[0] + "x" + source[1] + " width");
            // scaling a solid colour must not introduce fringes: the interior stays pure red
            assertTrue(red(render(d, WIDTH, 32, 0.5, false, 0, true, BLACK).getRGB((int) object.centerX(), 16)));
        }
    }

    @Test void rasterScalingFollowsTheBarHeightAndRepeatedFramesAreIdentical() throws IOException {
        BufferedImage art = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) art.setRGB(x, y, 0xffff0000);
        Path png = temp.resolve("big.png");
        ImageIO.write(art, "png", png.toFile());
        Design d = design(png, 64);
        ProgressPainter painter = new ProgressPainter(d);
        int[] heights = {16, 24, 40, 64};
        int previous = 0;
        for (int height : heights) {
            BufferedImage first = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
            BufferedImage second = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
            for (BufferedImage image : new BufferedImage[]{first, second}) {
                var g = image.createGraphics();
                try { painter.paint(g, WIDTH, height, 0.5, false, 0, BLACK, true); } finally { g.dispose(); }
            }
            assertArrayEquals(first.getRGB(0, 0, WIDTH, height, null, 0, WIDTH), second.getRGB(0, 0, WIDTH, height, null, 0, WIDTH));
            Bounds object = bounds(first, RenderingGeometryTest::red);
            assertEquals(height, object.height(), 2, "object fills a bar of " + height);
            assertTrue(object.height() > previous, "taller bars draw a taller object");
            previous = object.height();
        }
    }
}
