package dev.anyprogressbar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ProgressPainter {
    private final Design design;
    private final Artwork object;
    private final Artwork trail;
    private final Artwork background;

    private final List<String> skippedLayers = new ArrayList<>();

    /** Strict: any unreadable image is an error, so the editor can report it. */
    public ProgressPainter(Design design) throws IOException {
        this(design, false);
    }

    /** Lenient: unreadable image layers are skipped (see {@link #skippedLayers()}) and the rest still renders. */
    public static ProgressPainter lenient(Design design) {
        try {
            return new ProgressPainter(design, true);
        } catch (IOException e) {
            throw new IllegalStateException(e); // not thrown in lenient mode
        }
    }

    private ProgressPainter(Design design, boolean lenient) throws IOException {
        design.validate();
        this.design = design.copy();
        object = layer("Object image", design.objectImage, lenient);
        trail = layer("Trail image", design.trailImage, lenient);
        background = layer("Background image", design.backgroundImage, lenient);
    }

    private Artwork layer(String label, String reference, boolean lenient) throws IOException {
        try {
            return Artwork.load(reference);
        } catch (IOException | RuntimeException e) {
            if (!lenient) throw e;
            skippedLayers.add(label + ": " + e.getMessage());
            return null;
        }
    }

    /** Human-readable reasons for layers left out of a lenient painter; empty when everything loaded. */
    public List<String> skippedLayers() { return List.copyOf(skippedLayers); }

    /** Bars shorter than this are drawn as a plain fill: artwork is unreadable at that size. */
    static final int MIN_ARTWORK_HEIGHT = 12;

    /** Share of the object's width the trail runs under it: the trail sits behind the object, tucked in just enough to avoid gaps. */
    private static final float TRAIL_TUCK = 0.2f;

    public static double position(boolean indeterminate, double progress, double seconds, int speed) {
        return position(indeterminate, progress, seconds, speed, Design.Motion.GLIDE);
    }

    public static double position(boolean indeterminate, double progress, double seconds, int speed, Design.Motion motion) {
        if (!indeterminate) return Math.max(0, Math.min(1, progress));
        double lap = seconds * speed / 400.0;
        if (motion != Design.Motion.PING_PONG) return lap % 1.0;
        double t = lap % 2.0;
        return t <= 1 ? t : 2 - t;
    }

    /** True while an indeterminate ping-pong bar is travelling back (right to left). */
    private static boolean returning(boolean indeterminate, double seconds, int speed, Design.Motion motion) {
        return indeterminate && motion == Design.Motion.PING_PONG && (seconds * speed / 400.0) % 2.0 > 1;
    }

    public void paint(Graphics2D graphics, int width, int height, double progress, boolean indeterminate,
                      double seconds, Color fallback, boolean leftToRight) {
        if (width <= 0 || height <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.clip(new RoundRectangle2D.Double(0, 0, width, height, height, height));
            g.setColor(design.color(fallback));
            g.fillRect(0, 0, width, height);
            if (background != null) background.draw(g, 0, 0, width, height);
            if (height < MIN_ARTWORK_HEIGHT) { paintPlain(g, width, height, progress, indeterminate, seconds); return; }
            if (!leftToRight) { g.translate(width, 0); g.scale(-1, 1); }
            double phase = seconds * design.speed / 100.0 * Math.PI * 2;
            int objectHeight = Math.min(design.size, height);
            int objectWidth = object == null ? 0 : Math.min(width, Math.max(1, Math.round(objectHeight * object.aspect())));
            double fraction = position(indeterminate, progress, seconds, design.speed, design.motion);
            int x = (int) Math.round(fraction * (width - objectWidth));
            int edge = object == null ? (int) Math.round(fraction * width) : x + Math.round(objectWidth * TRAIL_TUCK);
            if (trail != null && design.trailLength > 0) {
                Graphics2D t = (Graphics2D) g.create();
                try {
                    int start = Math.max(0, edge - design.trailLength);
                    t.clipRect(start, 0, edge - start, height);
                    int tileWidth = Math.max(1, Math.round(height * trail.aspect()));
                    int offset = (int) (seconds * design.speed / 4) % tileWidth;
                    for (int tx = start - tileWidth + offset; tx < edge; tx += tileWidth) {
                        trail.draw(t, tx, 0, tileWidth, height);
                    }
                } finally { t.dispose(); }
            }
            if (object != null) {
                int y = (height - objectHeight) / 2;
                if (design.motion == Design.Motion.BOUNCE) {
                    objectHeight = Math.max(1, (int) (objectHeight * (0.85 + 0.15 * Math.abs(Math.sin(phase)))));
                    y = height - objectHeight;
                } else if (design.motion == Design.Motion.WIGGLE) {
                    g.rotate(Math.sin(phase) * 0.12, x + objectWidth / 2.0, height / 2.0);
                } else if (design.motion == Design.Motion.HOP) {
                    y = Math.max(0, height - objectHeight) - (int) Math.round(Math.abs(Math.sin(phase / 2)) * Math.max(2, height * 0.3));
                } else if (design.motion == Design.Motion.BOB) {
                    y += (int) Math.round(Math.sin(phase) * height * 0.12);
                } else if (design.motion == Design.Motion.SPIN) {
                    g.rotate(phase / 2, x + objectWidth / 2.0, y + objectHeight / 2.0);
                } else if (design.motion == Design.Motion.PULSE) {
                    double scale = 0.85 + 0.15 * Math.sin(phase);
                    g.translate(x + objectWidth / 2.0, y + objectHeight / 2.0);
                    g.scale(scale, scale);
                    g.translate(-(x + objectWidth / 2.0), -(y + objectHeight / 2.0));
                }
                if (returning(indeterminate, seconds, design.speed, design.motion)) {
                    g.translate(x * 2 + objectWidth, 0);
                    g.scale(-1, 1);
                }
                object.draw(g, x, y, objectWidth, objectHeight);
            }
        } finally { g.dispose(); }
    }

    /** Artwork-free rendering for very short bars (status bar, inline): a rounded fill with a simple marker. */
    private void paintPlain(Graphics2D g, int width, int height, double progress, boolean indeterminate, double seconds) {
        Color base = g.getColor();
        g.setColor(base.darker());
        g.fillRect(0, 0, width, height);
        g.setColor(accent(base));
        double fraction = position(indeterminate, progress, seconds, design.speed, design.motion);
        if (indeterminate) {
            int marker = Math.max(height, width / 5);
            g.fillRect((int) Math.round(fraction * (width - marker)), 0, marker, height);
        } else {
            g.fillRect(0, 0, (int) Math.round(fraction * width), height);
        }
    }

    private static Color accent(Color base) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        return Color.getHSBColor(hsb[0], Math.min(1f, hsb[1] + 0.2f), Math.min(1f, hsb[2] + 0.45f));
    }
}
