package dev.anyprogressbar;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

@Service(Service.Level.APP)
public final class ProgressInstallation implements Disposable {
    private static final String KEY = "ProgressBarUI";
    private static final String CUSTOM = AnimalProgressBarUI.class.getName();
    private Object original;
    private Object previousClass;
    private boolean installed;
    private boolean disposed;
    private ProgressPainter painter;

    public ProgressInstallation() {
        ApplicationManager.getApplication().getMessageBus().connect(this)
            .subscribe(LafManagerListener.TOPIC, source -> reload());
    }

    public static ProgressInstallation getInstance() {
        return ApplicationManager.getApplication().getService(ProgressInstallation.class);
    }

    public ProgressPainter painter() { return painter; }

    public void reload() {
        if (disposed) return;
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(this::reload);
            return;
        }
        Object current = UIManager.get(KEY);
        if (!CUSTOM.equals(current)) original = current;
        painter = null;
        ProgressSettings.Data data = ProgressSettings.getInstance().getState();
        if (data.enabled) {
            try {
                painter = ProgressPainter.lenient(data.selected());
                if (!painter.skippedLayers().isEmpty()) {
                    String details = String.join("; ", painter.skippedLayers());
                    Logger.getInstance(ProgressInstallation.class).warn("Skipped progress bar layers: " + details);
                    NotificationGroupManager.getInstance().getNotificationGroup("Any Progress Bar")
                        .createNotification("Some progress bar images could not be loaded",
                            details + ". The rest of the design is still used. Open Any Progress Bar settings to replace them.",
                            NotificationType.WARNING).notify(null);
                }
            } catch (IllegalArgumentException e) {
                Logger.getInstance(ProgressInstallation.class).warn("Cannot load progress bar design", e);
                NotificationGroupManager.getInstance().getNotificationGroup("Any Progress Bar")
                    .createNotification("Progress bar design could not be loaded",
                        e.getMessage() + ". Open Any Progress Bar settings to fix the design. Using the IDE progress bar.",
                        NotificationType.WARNING).notify(null);
            }
        }
        if (painter != null) {
            if (!installed) previousClass = UIManager.getDefaults().get(CUSTOM);
            UIManager.put(CUSTOM, AnimalProgressBarUI.class);
            UIManager.put(KEY, CUSTOM);
            installed = true;
        } else {
            restore();
        }
        refreshWindows();
    }

    private void restore() {
        if (!installed) return;
        if (CUSTOM.equals(UIManager.get(KEY))) UIManager.put(KEY, original);
        UIManager.put(CUSTOM, previousClass);
        installed = false;
    }

    private static void refreshWindows() {
        for (Window window : Window.getWindows()) refresh(window);
    }

    private static void refresh(Component component) {
        if (component instanceof JProgressBar bar) {
            bar.updateUI();
            bar.revalidate();
            bar.repaint();
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) refresh(child);
        }
    }

    @Override public void dispose() {
        disposed = true;
        Runnable cleanup = () -> { restore(); painter = null; refreshWindows(); };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) cleanup.run();
        else javax.swing.SwingUtilities.invokeLater(cleanup);
    }
}
