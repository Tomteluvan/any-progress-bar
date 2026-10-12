package dev.anyprogressbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public final class ProgressStartupActivity implements StartupActivity.DumbAware {
    @Override public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> ProgressInstallation.getInstance().reload());
    }
}
