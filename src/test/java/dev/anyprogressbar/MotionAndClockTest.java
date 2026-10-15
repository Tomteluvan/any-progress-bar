package dev.anyprogressbar;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionAndClockTest {
    private static int[] render(ProgressPainter painter, int height, double progress, boolean indeterminate, double seconds) {
        BufferedImage image = new BufferedImage(320, Math.max(height, 1), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { painter.paint(g, 320, height, progress, indeterminate, seconds, Color.DARK_GRAY, true); }
        finally { g.dispose(); }
        return image.getRGB(0, 0, 320, image.getHeight(), null, 0, 320);
    }

    @Test void everyMotionAnimatesAnIndeterminateBar() throws IOException {
        for (Design.Motion motion : Design.Motion.values()) {
            Design d = Design.presets().getFirst();
            d.motion = motion;
            ProgressPainter painter = new ProgressPainter(d);
            assertFalse(Arrays.equals(render(painter, 32, 0, true, 0.3), render(painter, 32, 0, true, 0.9)), motion + " must animate");
        }
    }

    @Test void pingPongTravelsThereAndBack() {
        var m = Design.Motion.PING_PONG;
        assertEquals(0.5, ProgressPainter.position(true, 0, 1, 200, m), 1e-9);
        assertEquals(1.0, ProgressPainter.position(true, 0, 2, 200, m), 1e-9);
        assertEquals(0.5, ProgressPainter.position(true, 0, 3, 200, m), 1e-9);
        assertEquals(0.5, ProgressPainter.position(true, 0, 3, 200, Design.Motion.GLIDE), 1e-9);
        assertEquals(0.25, ProgressPainter.position(false, 0.25, 9, 200, m), 1e-9);
    }

    @Test void shortBarsFallBackToAPlainFillWithoutArtwork() throws IOException {
        ProgressPainter painter = new ProgressPainter(Design.presets().getFirst());
        int[] half = render(painter, 8, 0.5, false, 0);
        int[] full = render(painter, 8, 1.0, false, 0);
        assertFalse(Arrays.equals(half, full));
        // no artwork: every row of a plain bar is identical
        assertEquals(half[0], half[7 * 320]);
        assertNotEquals(half[10], half[310]);
    }

    @Test void clockRunsOnlyWhileAClientIsAnimating() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnimationClock clock = AnimationClock.getInstance();
            boolean[] animating = {false};
            AnimationClock.Client client = new AnimationClock.Client() {
                @Override public boolean isAnimating() { return animating[0]; }
                @Override public void tick() {}
            };
            clock.register(client);
            assertFalse(clock.isRunning(), "idle client must not start the timer");
            animating[0] = true;
            clock.wake();
            assertTrue(clock.isRunning());
            clock.unregister(client);
            assertFalse(clock.isRunning());
        });
    }
}
