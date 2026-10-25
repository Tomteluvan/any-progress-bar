package dev.anyprogressbar;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Installs the progress bars when the plugin is enabled or installed without restarting the IDE. At startup
 * {@link ProgressAppListener} does this; it never runs for a plugin that is loaded later. Unloading needs no code here:
 * disposing {@link ProgressInstallation} restores the IDE's own progress bar UI.
 */
public final class ProgressPluginListener implements DynamicPluginListener {
    static final String PLUGIN_ID = "dev.anyprogressbar";

    @Override public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        if (!PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) return;
        ProgressInstallation.getInstance().reload();
    }
}
