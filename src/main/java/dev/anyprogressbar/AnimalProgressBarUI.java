package dev.anyprogressbar;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;

public final class AnimalProgressBarUI extends BasicProgressBarUI implements AnimationClock.Client {
    private HierarchyListener visibilityListener;
    private ChangeListener valueListener;
    private boolean registered;

    public static ComponentUI createUI(JComponent component) { return new AnimalProgressBarUI(); }

    @Override public void installUI(JComponent component) {
        super.installUI(component);
        visibilityListener = event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) updateRegistration();
        };
        valueListener = event -> {
            // A bar that was idle (0% / 100%) must repaint and may need to start animating again.
            progressBar.repaint();
            AnimationClock.getInstance().wake();
        };
        component.addHierarchyListener(visibilityListener);
        progressBar.addChangeListener(valueListener);
        updateRegistration();
    }

    private void updateRegistration() {
        boolean wanted = progressBar.isShowing() && progressBar.getOrientation() == JProgressBar.HORIZONTAL;
        if (wanted && !registered) AnimationClock.getInstance().register(this);
        else if (!wanted && registered) AnimationClock.getInstance().unregister(this);
        registered = wanted;
    }

    @Override public void uninstallUI(JComponent component) {
        if (registered) AnimationClock.getInstance().unregister(this);
        registered = false;
        component.removeHierarchyListener(visibilityListener);
        progressBar.removeChangeListener(valueListener);
        super.uninstallUI(component);
    }

    @Override public boolean isAnimating() {
        if (!progressBar.isShowing()) return false;
        if (progressBar.isIndeterminate()) return true;
        double progress = progressBar.getPercentComplete();
        return progress > 0 && progress < 1;
    }

    @Override public void tick() { progressBar.repaint(); }

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
                AnimationClock.getInstance().seconds(),
                background == null ? Color.DARK_GRAY : background,
                progressBar.getComponentOrientation().isLeftToRight());
        } finally { g.dispose(); }
        if (progressBar.isStringPainted() && width > 0 && height > 0) {
            paintString(graphics, insets.left, insets.top, width, height, getAmountFull(insets, width, height), insets);
        }
    }
}
