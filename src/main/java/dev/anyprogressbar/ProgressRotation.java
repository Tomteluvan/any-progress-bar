package dev.anyprogressbar;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Switches to another random favourite design every few minutes, when the settings ask for it. */
@Service(Service.Level.APP)
public final class ProgressRotation implements Disposable {
    private ScheduledFuture<?> task;

    public static ProgressRotation getInstance() {
        return ApplicationManager.getApplication().getService(ProgressRotation.class);
    }

    /** Applies the current settings: stops the timer, and starts it again if an interval is set. */
    public synchronized void reschedule() {
        if (task != null) task.cancel(false);
        task = null;
        ProgressSettings.Data data = ProgressSettings.getInstance().getState();
        if (data == null || !data.enabled || !data.randomize || data.randomMinutes <= 0) return;
        task = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
            ProgressSettings.getInstance().pickRandomDesign();
            ProgressInstallation.getInstance().reload();
        }, data.randomMinutes, data.randomMinutes, TimeUnit.MINUTES);
    }

    @Override public synchronized void dispose() {
        if (task != null) task.cancel(false);
        task = null;
    }
}
