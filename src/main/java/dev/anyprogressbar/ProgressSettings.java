package dev.anyprogressbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
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
        state.validate();
        data = state.copy();
        data.migratePresets();
    }
}
