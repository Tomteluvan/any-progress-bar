package dev.anyprogressbar;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/** The settings page's file dialogs: exporting and importing single designs and whole libraries. */
final class DesignTransfer {
    private final JComponent root;
    private final String title;
    private final Supplier<ProgressSettings.Data> draft;
    private final Supplier<Design> selected;
    private final Consumer<Design> add;
    private final Consumer<LibraryPack.MergePlan> merge;
    private final Consumer<LibraryPack.Imported> replace;

    DesignTransfer(JComponent root, String title, Supplier<ProgressSettings.Data> draft, Supplier<Design> selected,
                   Consumer<Design> add, Consumer<LibraryPack.MergePlan> merge, Consumer<LibraryPack.Imported> replace) {
        this.root = root;
        this.title = title;
        this.draft = draft;
        this.selected = selected;
        this.add = add;
        this.merge = merge;
        this.replace = replace;
    }

    /** File work that can fail with an {@link IOException}. */
    interface IoTask<T> {
        T run() throws IOException;
    }

    /**
     * Runs file work on a background thread under a cancellable modal progress, so the settings window keeps painting and
     * a large image or slow disk cannot freeze the IDE. Throws {@link ProcessCanceledException} if the user cancels.
     */
    static <T> T inBackground(JComponent root, String title, IoTask<T> task) throws IOException {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Exception>();
        boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            try {
                result.set(task.run());
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                failure.set(e);
            }
        }, title, true, null, root);
        if (!completed) throw new ProcessCanceledException();
        Exception e = failure.get();
        if (e instanceof IOException io) throw io;
        if (e instanceof RuntimeException runtime) throw runtime;
        return result.get();
    }

    void exportSelected() {
        Design design = selected.get();
        if (design == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(design.name.replaceAll("[^A-Za-z0-9._-]+", "_") + "." + DesignPack.EXTENSION));
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar design", DesignPack.EXTENSION));
        if (chooser.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("." + DesignPack.EXTENSION)) {
            target = target.resolveSibling(target.getFileName() + "." + DesignPack.EXTENSION);
        }
        if (java.nio.file.Files.exists(target) && Messages.showYesNoDialog(root, target.getFileName() + " already exists. Replace it?",
                title, Messages.getQuestionIcon()) != Messages.YES) return;
        Design snapshot = design.copy();
        Path destination = target;
        try {
            inBackground(root, "Exporting design", () -> { DesignPack.export(snapshot, destination); return null; });
            Messages.showInfoMessage(root, "Exported to " + target, title);
        } catch (ProcessCanceledException cancelled) {
            // the user cancelled the export; the previous file, if any, is untouched
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Export Design");
        }
    }

    void exportLibrary() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("any-progress-bar-library." + LibraryPack.EXTENSION));
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar library", LibraryPack.EXTENSION));
        if (chooser.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("." + LibraryPack.EXTENSION)) {
            target = target.resolveSibling(target.getFileName() + "." + LibraryPack.EXTENSION);
        }
        if (java.nio.file.Files.exists(target) && Messages.showYesNoDialog(root, target.getFileName() + " already exists. Replace it?",
                title, Messages.getQuestionIcon()) != Messages.YES) return;
        var snapshot = draft.get().designs.stream().map(Design::copy).toList();
        String activeId = draft.get().selectedId;
        Path destination = target;
        try {
            inBackground(root, "Exporting library", () -> { LibraryPack.export(snapshot, activeId, destination); return null; });
            Messages.showInfoMessage(root, "Exported " + snapshot.size() + " designs to " + target, title);
        } catch (ProcessCanceledException cancelled) {
            // the user cancelled the export; the previous file, if any, is untouched
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Export Library");
        }
    }

    void importLibrary() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar library", LibraryPack.EXTENSION));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        LibraryPack.Imported imported;
        Path file = chooser.getSelectedFile().toPath();
        try {
            imported = inBackground(root, "Importing library", () -> LibraryPack.importFrom(file, Artwork.assetsDirectory()));
        } catch (ProcessCanceledException cancelled) {
            return; // the user cancelled the import
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Import Library");
            return;
        }
        var plan = LibraryPack.planMerge(draft.get().designs, imported.designs());
        String summary = imported.designs().size() + " designs in the file";
        if (plan.duplicates() > 0) summary += ", " + plan.duplicates() + " of them already in your library";
        if (plan.additions().isEmpty()) {
            int answer = Messages.showDialog(root, summary + ". Nothing new would be merged.\n\nReplace your library with the "
                    + imported.designs().size() + " imported designs instead?", "Import Library",
                new String[]{"Replace Library", "Cancel"}, 1, Messages.getQuestionIcon());
            if (answer == 0) replace.accept(imported);
            return;
        }
        int answer = Messages.showDialog(root, summary + ".\n\nMerge adds the " + plan.additions().size()
                + " new designs and keeps everything you have. Replace Library discards your current " + draft.get().designs.size()
                + " designs in favour of the imported ones.", "Import Library",
            new String[]{"Merge", "Replace Library", "Cancel"}, 0, Messages.getQuestionIcon());
        if (answer == 0) merge.accept(plan);
        else if (answer == 1) replace.accept(imported);
    }

    void importDesign() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar design", DesignPack.EXTENSION));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        try {
            add.accept(inBackground(root, "Importing design", () -> DesignPack.importFrom(file, Artwork.assetsDirectory())));
        } catch (ProcessCanceledException cancelled) {
            // the user cancelled the import
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Import Design");
        }
    }
}
