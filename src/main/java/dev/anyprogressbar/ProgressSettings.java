package dev.anyprogressbar;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "AnyProgressBar", storages = @Storage("any-progress-bar.xml"))
public final class ProgressSettings implements PersistentStateComponent<ProgressSettings.Data> {
    public static final class Data {
        public static final int PRESET_VERSION = 4;
        public boolean enabled = true;
        public int presetVersion = 0; // 0 = saved before theme presets existed
        public String selectedId = "preset-cat";
        public List<Design> designs = new ArrayList<>(Design.presets());

        public Data copy() {
            Data d = new Data();
            d.enabled = enabled;
            d.selectedId = selectedId;
            d.presetVersion = presetVersion;
            d.designs = new ArrayList<>(designs.stream().map(Design::copy).toList());
            return d;
        }

        /** True when {@code other} holds the same settings: what the editor compares to know whether anything changed. */
        public boolean sameContent(Data other) {
            if (enabled != other.enabled || !Objects.equals(selectedId, other.selectedId) || designs.size() != other.designs.size()) {
                return false;
            }
            for (int i = 0; i < designs.size(); i++) if (!designs.get(i).sameContent(other.designs.get(i))) return false;
            return true;
        }

        public void validate() {
            if (designs == null || designs.isEmpty()) throw new IllegalArgumentException("Keep at least one design.");
            var ids = new HashSet<String>();
            for (Design design : designs) {
                design.validate();
                if (!ids.add(design.id)) throw new IllegalArgumentException("Design IDs must be unique.");
            }
            if (designs.stream().noneMatch(d -> Objects.equals(d.id, selectedId))) {
                throw new IllegalArgumentException("Select an active design.");
            }
        }

        /**
         * Returns a usable copy that keeps every valid design and repairs the selection, instead of rejecting the whole
         * library for one bad entry. Each repair is described in {@code notes}; nothing is added when the data is sound.
         */
        public Data repaired(List<String> notes) {
            Data result = new Data();
            result.enabled = enabled;
            result.presetVersion = presetVersion;
            result.selectedId = selectedId;
            result.designs = new ArrayList<>();
            var ids = new HashSet<String>();
            int position = 0;
            if (designs != null) {
                for (Design design : designs) {
                    position++;
                    if (design == null) {
                        notes.add("Design " + position + " was empty and was removed.");
                        continue;
                    }
                    try {
                        design.validate();
                    } catch (IllegalArgumentException e) {
                        notes.add("Removed " + describe(design, position) + ": " + e.getMessage());
                        continue;
                    }
                    if (!ids.add(design.id)) {
                        notes.add("Removed " + describe(design, position) + ": its ID duplicates another design.");
                        continue;
                    }
                    result.designs.add(design.copy());
                }
            }
            if (result.designs.isEmpty()) {
                notes.add("No valid designs were left, so the built-in presets were restored.");
                result.designs = new ArrayList<>(Design.presets());
                result.presetVersion = PRESET_VERSION;
                result.selectedId = result.designs.getFirst().id;
            } else if (result.designs.stream().noneMatch(d -> Objects.equals(d.id, result.selectedId))) {
                result.selectedId = result.designs.getFirst().id;
                notes.add("The active design was missing; \"" + result.designs.getFirst().name + "\" is active now.");
            }
            return result;
        }

        private static String describe(Design design, int position) {
            return design.name == null || design.name.isBlank() ? "design " + position : "\"" + design.name + "\"";
        }

        /** Adds themed presets introduced since {@link #presetVersion}; presets from earlier versions stay deleted if removed. */
        public void migratePresets() {
            if (presetVersion >= PRESET_VERSION) return;
            var ids = new HashSet<String>();
            for (Design design : designs) ids.add(design.id);
            for (Design preset : Design.themePresetsAddedAfter(presetVersion)) if (!ids.contains(preset.id)) designs.add(preset);
            presetVersion = PRESET_VERSION;
        }

        public Design selected() {
            return designs.stream().filter(d -> d.id.equals(selectedId)).findFirst().orElseThrow();
        }
    }

    private volatile Data data = fresh();

    /** Settings for a new install already hold every current preset, so none are treated as missing on a later upgrade. */
    private static Data fresh() {
        Data fresh = new Data();
        fresh.presetVersion = Data.PRESET_VERSION;
        return fresh;
    }
    public static ProgressSettings getInstance() {
        return ApplicationManager.getApplication().getService(ProgressSettings.class);
    }
    @Override public Data getState() { return data; }
    @Override public void loadState(@NotNull Data state) {
        var notes = new ArrayList<String>();
        Data repaired = state.repaired(notes);
        if (!notes.isEmpty()) reportRepair(state, notes);
        data = repaired;
        data.migratePresets();
    }

    private static void reportRepair(Data original, List<String> notes) {
        Logger log = Logger.getInstance(ProgressSettings.class);
        log.warn("Repaired saved Any Progress Bar settings: " + String.join(" ", notes));
        String saved = "";
        try {
            Path file = backUp(original, Path.of(PathManager.getConfigPath(), "any-progress-bar", "recovered"));
            saved = " The original settings were saved to " + file + ".";
        } catch (IOException | RuntimeException e) {
            log.warn("Cannot save a copy of the original settings", e);
        }
        String message = String.join("<br>", notes) + saved;
        try {
            NotificationGroupManager.getInstance().getNotificationGroup("Any Progress Bar")
                .createNotification("Some saved progress bar designs were repaired", message, NotificationType.WARNING)
                .notify(null);
        } catch (RuntimeException e) {
            log.warn("Cannot show the settings repair notification", e);
        }
    }

    /** Writes the settings exactly as they were loaded to a new timestamped file in {@code directory}. */
    static Path backUp(Data original, Path directory) throws IOException {
        Files.createDirectories(directory);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path file = directory.resolve("any-progress-bar-" + stamp + ".xml");
        Files.writeString(file, JDOMUtil.write(XmlSerializer.serialize(original)), StandardCharsets.UTF_8);
        return file;
    }
}
