package dev.anyprogressbar;

import com.intellij.ide.AppLifecycleListener;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Installs the progress bar UI once, when the IDE frame is created, before any project opens. */
public final class ProgressAppListener implements AppLifecycleListener {
    @Override public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        ProgressInstallation.getInstance().reload();
        AssetCleanup.cleanInBackground(ProgressSettings.getInstance().getState().designs);
    }
}
