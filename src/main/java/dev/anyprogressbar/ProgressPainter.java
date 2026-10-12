package dev.anyprogressbar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;

public final class ProgressPainter {
    private final Design design;
    private final Artwork object;
    private final Artwork trail;
    private final Artwork background;

    public ProgressPainter(Design design) throws IOException {
        design.validate();
        this.design = design.copy();
        object = Artwork.load(design.objectImage);
        trail = Artwork.load(design.trailImage);
        background = Artwork.load(design.backgroundImage);
    }

    public static double position(boolean indeterminate, double progress, double seconds, int speed) {
        if (!indeterminate) return Math.max(0, Math.min(1, progress));
        return (seconds * speed / 400.0) % 1.0;
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
            if (!leftToRight) { g.translate(width, 0); g.scale(-1, 1); }
            double phase = seconds * design.speed / 100.0 * Math.PI * 2;
            int objectHeight = Math.min(design.size, height);
            int objectWidth = object == null ? 0 : Math.min(width, Math.max(1, Math.round(objectHeight * object.aspect())));
            double fraction = position(indeterminate, progress, seconds, design.speed);
            int x = (int) Math.round(fraction * (width - objectWidth));
            int edge = object == null ? (int) Math.round(fraction * width) : x + objectWidth / 2;
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
                }
                object.draw(g, x, y, objectWidth, objectHeight);
            }
        } finally { g.dispose(); }
    }
}
