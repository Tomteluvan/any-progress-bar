package dev.anyprogressbar;

import com.intellij.ui.JBColor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;

public final class AnimalProgressBarUI extends BasicProgressBarUI implements AnimationClock.Client {
    private HierarchyListener visibilityListener;
    private ChangeListener valueListener;
    private PropertyChangeListener propertyListener;
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
        // setIndeterminate() and setOrientation() fire property changes, not the model change the listener above sees.
        propertyListener = event -> {
            String property = event.getPropertyName();
            if ("orientation".equals(property)) updateRegistration();
            if ("indeterminate".equals(property) || "orientation".equals(property)) {
                progressBar.repaint();
                AnimationClock.getInstance().wake();
            }
        };
        component.addHierarchyListener(visibilityListener);
        progressBar.addChangeListener(valueListener);
        progressBar.addPropertyChangeListener(propertyListener);
        updateRegistration();
    }

    private void updateRegistration() {
        boolean wanted = progressBar.isShowing();
        if (wanted && !registered) AnimationClock.getInstance().register(this);
        else if (!wanted && registered) AnimationClock.getInstance().unregister(this);
        registered = wanted;
    }

    @Override public void uninstallUI(JComponent component) {
        if (registered) AnimationClock.getInstance().unregister(this);
        registered = false;
        component.removeHierarchyListener(visibilityListener);
        progressBar.removeChangeListener(valueListener);
        progressBar.removePropertyChangeListener(propertyListener);
        super.uninstallUI(component);
    }

    @Override public boolean isAnimating() {
        if (!progressBar.isShowing()) return false;
        if (progressBar.isIndeterminate()) return true;
        double progress = progressBar.getPercentComplete();
        return progress > 0 && progress < 1;
    }

    @Override public void tick() {
        if (progressBar.isIndeterminate() && usesInheritedPainting()) {
            // Vertical bars (and bars painted without a design) use the inherited painting, which needs its frame index
            // advanced; derive it from the shared clock so they stay in sync without the inherited timer.
            int interval = UIManager.getInt("ProgressBar.repaintInterval");
            if (interval <= 0) interval = 50;
            int frames = Math.max(1, getFrameCount());
            setAnimationIndex((int) (AnimationClock.getInstance().seconds() * 1000 / interval) % frames);
        }
        progressBar.repaint();
    }

    /** The shared clock is the only animation driver: the inherited per-bar timer must never run. */
    @Override protected void startAnimationTimer() {}

    private boolean usesInheritedPainting() {
        return progressBar.getOrientation() != JProgressBar.HORIZONTAL || ProgressInstallation.getInstance().painter() == null;
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
                AnimationClock.getInstance().seconds(),
                background == null ? JBColor.DARK_GRAY : background,
                progressBar.getComponentOrientation().isLeftToRight());
        } finally { g.dispose(); }
        if (progressBar.isStringPainted() && width > 0 && height > 0) {
            paintString(graphics, insets.left, insets.top, width, height, getAmountFull(insets, width, height), insets);
        }
    }
}
