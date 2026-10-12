package dev.anyprogressbar;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;

public final class AnimalProgressBarUI extends BasicProgressBarUI {
    private Timer timer;
    private HierarchyListener visibilityListener;
    private final long started = System.nanoTime();

    public static ComponentUI createUI(JComponent component) { return new AnimalProgressBarUI(); }

    @Override public void installUI(JComponent component) {
        super.installUI(component);
        timer = new Timer(33, event -> component.repaint());
        visibilityListener = event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) updateTimer();
        };
        component.addHierarchyListener(visibilityListener);
        updateTimer();
    }

    private void updateTimer() {
        if (progressBar.isShowing() && progressBar.getOrientation() == JProgressBar.HORIZONTAL) timer.start();
        else timer.stop();
    }

    @Override public void uninstallUI(JComponent component) {
        timer.stop();
        component.removeHierarchyListener(visibilityListener);
        super.uninstallUI(component);
    }

    @Override protected void paintDeterminate(Graphics g, JComponent c) { paintDesign(g, c, false); }
    @Override protected void paintIndeterminate(Graphics g, JComponent c) { paintDesign(g, c, true); }

    private void paintDesign(Graphics graphics, JComponent component, boolean indeterminate) {
        ProgressPainter painter = ProgressInstallation.getInstance().painter();
        if (painter == null || progressBar.getOrientation() != JProgressBar.HORIZONTAL) {
            if (indeterminate) super.paintIndeterminate(graphics, component);
            else super.paintDeterminate(graphics, component);
            return;
        }
        Insets insets = progressBar.getInsets();
        int width = progressBar.getWidth() - insets.left - insets.right;
        int height = progressBar.getHeight() - insets.top - insets.bottom;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(insets.left, insets.top);
            Color background = progressBar.getBackground();
            painter.paint(g, width, height, progressBar.getPercentComplete(), indeterminate,
                (System.nanoTime() - started) / 1_000_000_000.0,
                background == null ? Color.DARK_GRAY : background,
                progressBar.getComponentOrientation().isLeftToRight());
        } finally { g.dispose(); }
        if (progressBar.isStringPainted() && width > 0 && height > 0) {
            paintString(graphics, insets.left, insets.top, width, height, getAmountFull(insets, width, height), insets);
        }
    }
}
