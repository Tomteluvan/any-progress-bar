package dev.anyprogressbar;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MotionBoundsTest {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 32;

    @TempDir Path temp;

    /** A design whose only layer is a solid red square on a black bar, so the visible red is exactly the object. */
    private ProgressPainter redSquare(Design.Motion motion, int size) throws IOException {
        Path svg = temp.resolve("square.svg");
        Files.writeString(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
            + "<rect width=\"10\" height=\"10\" fill=\"#ff0000\"/></svg>");
        Design d = new Design();
        d.objectImage = svg.toString();
        d.backgroundColor = "#000000";
        d.size = size;
        d.motion = motion;
        return new ProgressPainter(d);
    }

    /** Coverage of the object in pixels: the red channel summed over the bar, which stays accurate on antialiased edges. */
    private static double visibleArea(ProgressPainter painter, double seconds) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { painter.paint(g, WIDTH, HEIGHT, 0.5, false, seconds, Color.DARK_GRAY, true); }
        finally { g.dispose(); }
        double sum = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) sum += ((image.getRGB(x, y) >> 16) & 0xff) / 255.0;
        return sum;
    }

    @Test void objectsAreNeverClippedByTheirOwnMotion() throws IOException {
        for (Design.Motion motion : Design.Motion.values()) {
            ProgressPainter painter = redSquare(motion, 32);
            int fitted = ProgressPainter.motionSafeHeight(motion, HEIGHT, 32, 1f);
            double expected = (double) fitted * fitted;
            // bounce and pulse shrink the artwork on purpose: down to 85% and 70% in each direction
            if (motion == Design.Motion.BOUNCE) expected *= 0.85 * 0.85;
            if (motion == Design.Motion.PULSE) expected *= 0.7 * 0.7;
            for (double seconds = 0; seconds < 4; seconds += 0.071) {
                double area = visibleArea(painter, seconds);
                double lower = expected * 0.94; // antialiased edges cost a few percent
                assertTrue(area >= lower, motion + " lost area at t=" + seconds + ": " + area + " < " + lower);
            }
        }
    }

    @Test void motionsThatMoveOrRotateGetASmallerObjectOnTallObjects() {
        assertEquals(32, ProgressPainter.motionSafeHeight(Design.Motion.GLIDE, 32, 32, 1f));
        assertEquals(32, ProgressPainter.motionSafeHeight(Design.Motion.PING_PONG, 32, 32, 1f));
        assertEquals(32, ProgressPainter.motionSafeHeight(Design.Motion.BOUNCE, 32, 32, 1f));
        assertEquals(32, ProgressPainter.motionSafeHeight(Design.Motion.PULSE, 32, 32, 1f));
        assertTrue(ProgressPainter.motionSafeHeight(Design.Motion.HOP, 32, 32, 1f) <= 32 - 10);
        assertTrue(ProgressPainter.motionSafeHeight(Design.Motion.BOB, 32, 32, 1f) <= 32 - 8);
        assertTrue(ProgressPainter.motionSafeHeight(Design.Motion.SPIN, 32, 32, 1f) <= 23);
        assertTrue(ProgressPainter.motionSafeHeight(Design.Motion.WIGGLE, 32, 32, 1f) < 32);
    }

    @Test void fittedObjectsStayInsideTheBarForEveryHeightAndAspect() {
        for (int height = 12; height <= 64; height += 4) {
            for (float aspect : new float[]{0.5f, 1f, 2f, 4f}) {
                for (Design.Motion motion : Design.Motion.values()) {
                    int h = ProgressPainter.motionSafeHeight(motion, height, 64, aspect);
                    String message = motion + " height " + height + " aspect " + aspect;
                    if (h == 1) continue; // nothing smaller can be drawn
                    switch (motion) {
                        case HOP -> assertTrue(h + Math.ceil(Math.max(2, height * 0.3)) <= height, message);
                        case BOB -> assertTrue(h + 2 * Math.ceil(height * 0.12) <= height, message);
                        case WIGGLE -> assertTrue(h * (Math.cos(0.12) + aspect * Math.sin(0.12)) <= height, message);
                        case SPIN -> assertTrue(h * Math.hypot(1, aspect) <= height, message);
                        default -> assertTrue(h <= height, message);
                    }
                }
            }
        }
    }

    @Test void smallObjectsKeepTheirRequestedSizeWhenTheyAlreadyFit() {
        for (Design.Motion motion : Design.Motion.values()) {
            assertEquals(8, ProgressPainter.motionSafeHeight(motion, 64, 8, 1f), motion.name());
            assertTrue(ProgressPainter.motionSafeHeight(motion, 12, 64, 3f) >= 1, motion.name());
        }
    }

    @Test void rotatingObjectsReserveRoomAtBothEnds() {
        assertEquals(0, ProgressPainter.motionSafePadding(Design.Motion.GLIDE, 20, 20));
        assertEquals(0, ProgressPainter.motionSafePadding(Design.Motion.HOP, 20, 20));
        assertTrue(ProgressPainter.motionSafePadding(Design.Motion.WIGGLE, 20, 20) > 0);
        assertTrue(ProgressPainter.motionSafePadding(Design.Motion.SPIN, 20, 20) >= 4);
    }
}
