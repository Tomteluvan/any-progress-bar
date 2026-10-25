package dev.anyprogressbar;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Future<?> pending;
    private volatile CompletableFuture<Void> latest = CompletableFuture.completedFuture(null);

    public ProgressInstallation() {
        ApplicationManager.getApplication().getMessageBus().connect(this)
            .subscribe(LafManagerListener.TOPIC, source -> reload());
    }

    public static ProgressInstallation getInstance() {
        return ApplicationManager.getApplication().getService(ProgressInstallation.class);
    }

    public ProgressPainter painter() { return painter; }

    /**
     * Reloads the active design: the images are read on a background thread and only the latest result is installed on
     * the event thread, so slow storage or large images never freeze the IDE. The returned future completes once this
     * reload has been installed or superseded by a newer one.
     */
    public CompletableFuture<Void> reload() {
        var done = new CompletableFuture<Void>();
        if (disposed) {
            done.complete(null);
            return done;
        }
        ProgressSettings.Data data = Objects.requireNonNull(ProgressSettings.getInstance().getState());
        Design design = data.enabled ? data.selected().copy() : null;
        int ticket = generation.incrementAndGet();
        CompletableFuture<Void> superseded = latest;
        latest = done;
        superseded.complete(null);
        Future<?> previous = pending;
        if (previous != null) previous.cancel(true);
        if (design == null) {
            publish(ticket, null, null, done);
            return done;
        }
        pending = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ProgressPainter built = null;
            String failure = null;
            try {
                built = ProgressPainter.lenient(design);
            } catch (IllegalArgumentException e) {
                Logger.getInstance(ProgressInstallation.class).warn("Cannot load progress bar design", e);
                failure = e.getMessage();
            } catch (RuntimeException e) {
                Logger.getInstance(ProgressInstallation.class).warn("Cannot load progress bar design", e);
                failure = String.valueOf(e.getMessage());
            }
            ProgressPainter result = built;
            String message = failure;
            ApplicationManager.getApplication().invokeLater(() -> publish(ticket, result, message, done), ModalityState.any());
        });
        return done;
    }

    /** The reload that was started last; completes when it has been installed (or replaced by a newer one). */
    CompletableFuture<Void> latestReload() { return latest; }

    /** Installs a finished reload on the event thread, unless a newer reload has been started since. */
    private void publish(int ticket, ProgressPainter built, String failure, CompletableFuture<Void> done) {
        try {
            if (disposed || ticket != generation.get()) return;
            if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
                ApplicationManager.getApplication().invokeLater(() -> publish(ticket, built, failure, done), ModalityState.any());
                return;
            }
            Object current = UIManager.get(KEY);
            if (!CUSTOM.equals(current)) original = current;
            painter = built;
            if (built != null && !built.skippedLayers().isEmpty()) {
                String details = String.join("; ", built.skippedLayers());
                Logger.getInstance(ProgressInstallation.class).warn("Skipped progress bar layers: " + details);
                NotificationGroupManager.getInstance().getNotificationGroup("Any Progress Bar")
                    .createNotification("Some progress bar images could not be loaded",
                        details + ". The rest of the design is still used. Open Any Progress Bar settings to replace them.",
                        NotificationType.WARNING).notify(null);
            }
            if (failure != null) {
                NotificationGroupManager.getInstance().getNotificationGroup("Any Progress Bar")
                    .createNotification("Progress bar design could not be loaded",
                        failure + ". Open Any Progress Bar settings to fix the design. Using the IDE progress bar.",
                        NotificationType.WARNING).notify(null);
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
        } finally { done.complete(null); }
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
        generation.incrementAndGet();
        Future<?> running = pending;
        if (running != null) running.cancel(true);
        latest.complete(null);
        Runnable cleanup = () -> { restore(); painter = null; refreshWindows(); };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) cleanup.run();
        else javax.swing.SwingUtilities.invokeLater(cleanup);
    }
}
