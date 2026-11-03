package dev.anyprogressbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.JBUI;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Animated preview of a design as indeterminate, determinate, compact and status bar progress bars. */
final class DesignPreview extends JPanel {
    private final long started = System.nanoTime();
    final Timer timer = new Timer(33, e -> repaint());
    ProgressPainter painter;
    boolean stale;
    double progress = 0.65;

    DesignPreview() { setPreferredSize(JBUI.size(400, 190)); setMinimumSize(JBUI.size(240, 190)); }
    @Override public void addNotify() { super.addNotify(); timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (painter == null) return;
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            // A bare Graphics2D draws unsmoothed text; Swing components get the IDE's text antialiasing set up for them.
            UISettings.setupAntialiasing(g);
            g.setFont(JBUI.Fonts.label());
            g.setColor(getForeground());
            g.drawString("Indeterminate", 0, JBUI.scale(12));
            g.translate(0, JBUI.scale(18));
            painter.paint(g, getWidth(), JBUI.scale(30), 0, true, seconds, getBackground(), true);
            g.translate(0, JBUI.scale(42));
            g.setColor(getForeground());
            g.drawString("Determinate", 0, 0);
            g.translate(0, JBUI.scale(6));
            painter.paint(g, getWidth(), JBUI.scale(30), progress, false, seconds, getBackground(), true);
            g.translate(0, JBUI.scale(42));
            g.setColor(getForeground());
            g.drawString("Compact (16 px) and status bar (8 px)", 0, 0);
            g.translate(0, JBUI.scale(6));
            painter.paint(g, getWidth(), JBUI.scale(16), progress, true, seconds, getBackground(), true);
            g.translate(0, JBUI.scale(22));
            painter.paint(g, getWidth(), JBUI.scale(8), progress, false, seconds, getBackground(), true);
            if (stale) {
                g.translate(0, JBUI.scale(22));
                g.setColor(JBUI.CurrentTheme.Label.errorForeground());
                g.drawString("Showing the last valid version of this design", 0, 0);
            }
        } finally { g.dispose(); }
    }
}
