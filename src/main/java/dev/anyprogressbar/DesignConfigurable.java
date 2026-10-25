package dev.anyprogressbar;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jetbrains.annotations.NotNull;

public final class DesignConfigurable implements Configurable {
    private ProgressSettings.Data draft;
    private JComponent root;
    private JPanel content;
    private DefaultListModel<Design> model;
    private JList<Design> designs;
    private JCheckBox enabled;
    private JTextField name;
    private JTextField object;
    private JTextField trail;
    private JTextField background;
    private JTextField color;
    private ColorPanel swatch;
    private JSpinner size;
    private JSpinner speed;
    private JSpinner trailLength;
    private JComboBox<Design.Motion> motion;
    private JLabel error;
    private final Map<Design.Field, JLabel> fieldErrors = new EnumMap<>(Design.Field.class);
    private final Map<Design.Field, JComponent> fieldControls = new EnumMap<>(Design.Field.class);
    private final Map<JComponent, String> baseDescriptions = new HashMap<>();
    private String announced = "";
    private Preview preview;
    private boolean updating;
    private JButton useButton;

    @Override public String getDisplayName() { return "Any Progress Bar"; }

    @Override public JComponent createComponent() {
        fieldErrors.clear();
        fieldControls.clear();
        baseDescriptions.clear();
        content = new JPanel(new BorderLayout(JBUI.scale(12), JBUI.scale(12)));
        content.setBorder(JBUI.Borders.empty(12));
        // The settings window can be small and the UI scale large: let the whole editor scroll instead of clipping it.
        JBScrollPane page = new JBScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        page.setBorder(JBUI.Borders.empty());
        page.getVerticalScrollBar().setUnitIncrement(JBUI.scale(16));
        page.getHorizontalScrollBar().setUnitIncrement(JBUI.scale(16));
        root = page;
        enabled = new JCheckBox("Enable custom progress bars");
        enabled.addActionListener(e -> draft.enabled = enabled.isSelected());
        content.add(enabled, BorderLayout.NORTH);

        model = new DefaultListModel<>();
        designs = new JBList<>(model);
        designs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        designs.getAccessibleContext().setAccessibleName("Designs");
        designs.getAccessibleContext().setAccessibleDescription("Your design library. Select a design to edit it.");
        designs.addListSelectionListener(e -> {
            // Selecting a design only chooses what to edit; "Use this design" is what makes it the active one.
            if (!updating && !e.getValueIsAdjusting() && selected() != null) showDesign();
        });
        designs.setCellRenderer(new ColoredListCellRenderer<>() {
            @Override protected void customizeCellRenderer(@NotNull JList<? extends Design> list, Design value, int index,
                                                           boolean selected, boolean hasFocus) {
                if (value == null) return;
                boolean active = draft != null && value.id.equals(draft.selectedId);
                append(value.name == null || value.name.isBlank() ? "(unnamed)" : value.name,
                    active ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (active) append("  Active", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
            }
        });
        JPanel library = new JPanel(new BorderLayout(JBUI.scale(6), JBUI.scale(6)));
        JBScrollPane scroll = new JBScrollPane(designs);
        scroll.setPreferredSize(JBUI.size(200, 300));
        library.add(scroll);
        JPanel actions = new JPanel(new GridLayout(0, 1, JBUI.scale(4), JBUI.scale(4)));
        useButton = button("Use this design", KeyEvent.VK_G, () -> {
            if (selected() == null) return;
            draft.selectedId = selected().id;
            designs.repaint();
            updateUseButton();
        });
        useButton.getAccessibleContext().setAccessibleDescription("Make the selected design the one used for progress bars.");
        actions.add(useButton);
        actions.add(button("New", KeyEvent.VK_W, () -> add(new Design())));
        actions.add(button("Duplicate", KeyEvent.VK_A, () -> {
            if (selected() == null) return;
            Design copy = selected().copy();
            copy.id = UUID.randomUUID().toString();
            copy.name += " (copy)";
            add(copy);
        }));
        actions.add(button("Delete", KeyEvent.VK_E, () -> {
            if (model.size() == 1) {
                Messages.showInfoMessage(root, "Keep at least one design.", getDisplayName());
                return;
            }
            int index = designs.getSelectedIndex();
            if (index < 0) return;
            boolean wasActive = draft.designs.get(index).id.equals(draft.selectedId);
            draft.designs.remove(index);
            model.remove(index);
            int next = Math.min(index, model.size() - 1);
            if (wasActive) draft.selectedId = draft.designs.get(next).id; // the neighbour takes over as the active design
            designs.setSelectedIndex(next);
            designs.repaint();
        }));
        actions.add(button("Restore presets", KeyEvent.VK_R, () -> {
            if (Messages.showYesNoDialog(root, "Replace your design library with the built-in presets?",
                    getDisplayName(), Messages.getQuestionIcon()) != Messages.YES) return;
            draft.designs = new java.util.ArrayList<>(Design.presets());
            draft.selectedId = draft.designs.getFirst().id;
            populate();
        }));
        actions.add(button("Export...", KeyEvent.VK_X, this::exportSelected));
        actions.add(button("Import...", KeyEvent.VK_I, this::importDesign));
        actions.add(button("Export library...", this::exportLibrary));
        actions.add(button("Import library...", this::importLibrary));
        library.add(actions, BorderLayout.SOUTH);
        content.add(library, BorderLayout.WEST);

        JPanel editor = new JPanel(new GridBagLayout());
        name = new JTextField(24);
        object = imageField();
        trail = imageField();
        background = imageField();
        color = new JTextField(12);
        size = new JSpinner(new SpinnerNumberModel(24, 8, 64, 1));
        speed = new JSpinner(new SpinnerNumberModel(100, 10, 400, 10));
        trailLength = new JSpinner(new SpinnerNumberModel(160, 0, 600, 10));
        motion = new ComboBox<>(Design.Motion.values());
        motion.setRenderer(SimpleListCellRenderer.create((label, value, index) -> label.setText(value == null ? "" : value.label())));
        row(editor, 0, "Name", 'N', withError(Design.Field.NAME, name, name), name);
        row(editor, 1, "Object image", 'O', withError(Design.Field.OBJECT_IMAGE,
            imageRow("object image", object, value -> selected().objectImage = value), object), object);
        row(editor, 2, "Trail image", 'T', withError(Design.Field.TRAIL_IMAGE,
            imageRow("trail image", trail, value -> selected().trailImage = value), trail), trail);
        row(editor, 3, "Background image", 'B', withError(Design.Field.BACKGROUND_IMAGE,
            imageRow("background image", background, value -> selected().backgroundImage = value), background), background);
        JPanel colors = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        colors.add(color);
        swatch = new ColorPanel();
        name(swatch, "Pick background colour");
        swatch.addActionListener(e -> {
            Color chosen = swatch.getSelectedColor();
            if (chosen != null) color.setText(String.format("#%06X", chosen.getRGB() & 0xffffff));
        });
        JPanel colorButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        colorButtons.add(swatch);
        colorButtons.add(button("Use IDE colour", KeyEvent.VK_U, () -> color.setText("")));
        colors.add(colorButtons, BorderLayout.EAST);
        row(editor, 4, "Background colour", 'C', withError(Design.Field.BACKGROUND_COLOR, colors, color), color);
        row(editor, 5, "Object size (px, 8-64)", 'S', withError(Design.Field.SIZE, size, spinnerField(size)), spinnerField(size));
        row(editor, 6, "Speed (%, 10-400)", 'P', withError(Design.Field.SPEED, speed, spinnerField(speed)), spinnerField(speed));
        row(editor, 7, "Trail length (px, 0-600)", 'L', withError(Design.Field.TRAIL_LENGTH, trailLength, spinnerField(trailLength)),
            spinnerField(trailLength));
        size.setToolTipText("Height of the object in pixels (8-64); capped to the bar height.");
        speed.setToolTipText("Percent. 100 = one lap every 4 seconds for indeterminate bars (10-400).");
        trailLength.setToolTipText("Pixels of trail drawn behind the object (0-600); 0 hides the trail.");
        motion.setToolTipText("How the object moves. Ping-pong only changes indeterminate bars.");
        color.setToolTipText("#RRGGBB, or empty to use the IDE colour.");
        describe(spinnerField(size), size.getToolTipText());
        describe(spinnerField(speed), speed.getToolTipText());
        describe(spinnerField(trailLength), trailLength.getToolTipText());
        describe(motion, motion.getToolTipText());
        describe(color, color.getToolTipText());
        describe(name, "The name shown in the design list.");
        row(editor, 8, "Motion", 'M', withError(Design.Field.MOTION, motion, motion), motion);
        row(editor, 9, "", new JLabel("Clear any image to omit it. Empty colour uses the IDE colour."));
        row(editor, 10, "", new JLabel("Background images overlay the colour. PNG, JPG and static SVG supported."));
        error = new JLabel(" ");
        error.setForeground(JBUI.CurrentTheme.Label.errorForeground());
        error.getAccessibleContext().setAccessibleName("Validation message");
        row(editor, 11, "", error);
        preview = new Preview();
        preview.getAccessibleContext().setAccessibleName("Live preview");
        preview.getAccessibleContext().setAccessibleDescription(
            "Animated preview of the selected design as indeterminate, determinate, compact and status bar progress bars.");
        row(editor, 12, "Live preview", preview);
        JSlider progress = new JSlider(0, 100, 65);
        describe(progress, "Progress shown in the determinate previews, 0 to 100 percent.");
        progress.addChangeListener(e -> { preview.progress = progress.getValue() / 100.0; preview.repaint(); });
        row(editor, 13, "Determinate progress", 'D', progress, progress);
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = 14;
        filler.weighty = 1;
        editor.add(new JPanel(), filler);
        content.add(editor, BorderLayout.CENTER);
        DocumentAdapter listener = new DocumentAdapter() {
            @Override protected void textChanged(@NotNull DocumentEvent e) { changed(); }
        };
        name.getDocument().addDocumentListener(listener);
        color.getDocument().addDocumentListener(listener);
        size.addChangeListener(e -> changed());
        speed.addChangeListener(e -> changed());
        trailLength.addChangeListener(e -> changed());
        motion.addActionListener(e -> changed());
        reset();
        return root;
    }

    private static JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(e -> action.run());
        return button;
    }

    /** A button whose mnemonic is the given {@link KeyEvent} key; the key must occur in the label so it is underlined. */
    private static JButton button(String label, int mnemonic, Runnable action) {
        JButton button = button(label, action);
        button.setMnemonic(mnemonic);
        return button;
    }

    /** Wraps a control with a message line that appears under it when {@code field} has a problem. */
    private JPanel withError(Design.Field field, JComponent component, JComponent control) {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(2)));
        panel.add(component);
        JLabel message = new JLabel();
        message.setFont(JBUI.Fonts.smallFont());
        message.setForeground(JBUI.CurrentTheme.Label.errorForeground());
        message.setVisible(false);
        name(message, field.label() + " error");
        panel.add(message, BorderLayout.SOUTH);
        fieldErrors.put(field, message);
        fieldControls.put(field, control);
        return panel;
    }

    private static void describe(JComponent component, String description) {
        var context = component.getAccessibleContext(); // some platform components do not provide one
        if (context != null) context.setAccessibleDescription(description);
    }

    private static void name(JComponent component, String accessibleName) {
        var context = component.getAccessibleContext();
        if (context != null) context.setAccessibleName(accessibleName);
    }

    /** The text field inside a spinner: what a label's mnemonic should focus and what assistive technology reads. */
    private static JComponent spinnerField(JSpinner spinner) {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    }

    private static JTextField imageField() {
        JTextField field = new JTextField(24);
        field.setEditable(false);
        return field;
    }

    private JPanel imageRow(String layer, JTextField field, Consumer<String> assign) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        row.add(field);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0));
        JButton browse = button("Browse", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, SVG)", "png", "jpg", "jpeg", "svg"));
            if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
            try {
                String reference = Artwork.importFile(chooser.getSelectedFile().toPath(),
                    Path.of(PathManager.getConfigPath(), "any-progress-bar", "assets"));
                assign.accept(reference);
                field.setText(reference);
                field.setToolTipText(reference);
                changed();
            } catch (IOException | IllegalArgumentException e) {
                Messages.showErrorDialog(root, e.getMessage(), "Could Not Import Image");
            }
        });
        browse.getAccessibleContext().setAccessibleName("Browse for " + layer);
        JButton clear = button("Clear", () -> { assign.accept(""); field.setText(""); changed(); });
        clear.getAccessibleContext().setAccessibleName("Clear " + layer);
        browse.getAccessibleContext().setAccessibleDescription("Choose a PNG, JPG or SVG file for the " + layer + ".");
        clear.getAccessibleContext().setAccessibleDescription("Remove the " + layer + " from this design.");
        field.getAccessibleContext().setAccessibleDescription("Location of the stored " + layer + "; empty when there is none.");
        buttons.add(browse);
        buttons.add(clear);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private static void row(JPanel panel, int index, String label, JComponent component) {
        row(panel, index, label, 0, component, null);
    }

    /** Adds a labelled row; a non-null {@code target} is the control the label (and its mnemonic) activates. */
    private static void row(JPanel panel, int index, String label, int mnemonic, JComponent component, JComponent target) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = index;
        c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insets(4);
        JLabel text = new JLabel(label);
        if (mnemonic != 0) text.setDisplayedMnemonic(mnemonic);
        if (target != null) text.setLabelFor(target);
        panel.add(text, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, c);
    }

    private Design selected() { return designs.getSelectedValue(); }

    private void add(Design design) {
        draft.designs.add(design);
        model.addElement(design);
        designs.setSelectedValue(design, true);
    }

    private void updateUseButton() {
        Design design = selected();
        useButton.setEnabled(design != null && !design.id.equals(draft.selectedId));
    }

    private void changed() {
        if (updating || selected() == null) return;
        Design d = selected();
        syncSwatch();
        d.name = name.getText();
        d.backgroundColor = color.getText().trim();
        d.size = (Integer) size.getValue();
        d.speed = (Integer) speed.getValue();
        d.trailLength = (Integer) trailLength.getValue();
        d.motion = (Design.Motion) motion.getSelectedItem();
        designs.repaint();
        updatePreview();
    }

    private void syncSwatch() {
        String text = color.getText().trim();
        Color parsed = null;
        if (text.matches("#[0-9a-fA-F]{6}")) parsed = Color.decode(text);
        if (!java.util.Objects.equals(parsed, swatch.getSelectedColor())) swatch.setSelectedColor(parsed);
    }

    private void exportSelected() {
        Design design = selected();
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
                getDisplayName(), Messages.getQuestionIcon()) != Messages.YES) return;
        try {
            DesignPack.export(design, target);
            Messages.showInfoMessage(root, "Exported to " + target, getDisplayName());
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Export Design");
        }
    }

    private void exportLibrary() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("any-progress-bar-library." + LibraryPack.EXTENSION));
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar library", LibraryPack.EXTENSION));
        if (chooser.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("." + LibraryPack.EXTENSION)) {
            target = target.resolveSibling(target.getFileName() + "." + LibraryPack.EXTENSION);
        }
        if (java.nio.file.Files.exists(target) && Messages.showYesNoDialog(root, target.getFileName() + " already exists. Replace it?",
                getDisplayName(), Messages.getQuestionIcon()) != Messages.YES) return;
        try {
            LibraryPack.export(draft.designs, draft.selectedId, target);
            Messages.showInfoMessage(root, "Exported " + draft.designs.size() + " designs to " + target, getDisplayName());
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Export Library");
        }
    }

    private void importLibrary() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar library", LibraryPack.EXTENSION));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        LibraryPack.Imported imported;
        try {
            imported = LibraryPack.importFrom(chooser.getSelectedFile().toPath(),
                Path.of(PathManager.getConfigPath(), "any-progress-bar", "assets"));
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Import Library");
            return;
        }
        var plan = LibraryPack.planMerge(draft.designs, imported.designs());
        String summary = imported.designs().size() + " designs in the file";
        if (plan.duplicates() > 0) summary += ", " + plan.duplicates() + " of them already in your library";
        if (plan.additions().isEmpty()) {
            int answer = Messages.showDialog(root, summary + ". Nothing new would be merged.\n\nReplace your library with the "
                    + imported.designs().size() + " imported designs instead?", "Import Library",
                new String[]{"Replace Library", "Cancel"}, 1, Messages.getQuestionIcon());
            if (answer == 0) replaceLibrary(imported);
            return;
        }
        int answer = Messages.showDialog(root, summary + ".\n\nMerge adds the " + plan.additions().size()
                + " new designs and keeps everything you have. Replace Library discards your current " + draft.designs.size()
                + " designs in favour of the imported ones.", "Import Library",
            new String[]{"Merge", "Replace Library", "Cancel"}, 0, Messages.getQuestionIcon());
        if (answer == 0) mergeImported(plan);
        else if (answer == 1) replaceLibrary(imported);
    }

    /** Adds the designs a merge found to be new; the active design stays as it is. */
    void mergeImported(LibraryPack.MergePlan plan) {
        for (Design design : plan.additions()) {
            draft.designs.add(design);
            model.addElement(design);
        }
        if (!plan.additions().isEmpty()) designs.setSelectedValue(plan.additions().getFirst(), true);
    }

    /** Replaces the whole library; the design that was active in the file becomes active, else the first one. */
    void replaceLibrary(LibraryPack.Imported imported) {
        draft.designs = new java.util.ArrayList<>(imported.designs());
        Design active = imported.active();
        draft.selectedId = (active != null ? active : draft.designs.getFirst()).id;
        populate();
    }

    private void importDesign() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar design", DesignPack.EXTENSION));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        try {
            add(DesignPack.importFrom(chooser.getSelectedFile().toPath(),
                Path.of(PathManager.getConfigPath(), "any-progress-bar", "assets")));
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could Not Import Design");
        }
    }

    /**
     * Checks the selected design and shows what is wrong next to the control that caused it. The preview keeps showing
     * the last valid version of the design while an edit is incomplete.
     */
    private void updatePreview() {
        Design design = selected().copy();
        var problems = new EnumMap<Design.Field, String>(Design.Field.class);
        for (Design.Problem problem : design.problems()) problems.putIfAbsent(problem.field(), problem.message());
        ProgressPainter built = null;
        if (problems.isEmpty()) {
            ProgressPainter candidate = ProgressPainter.lenient(design); // lenient: report every broken layer, not just the first
            for (Design.Problem problem : candidate.layerProblems()) problems.putIfAbsent(problem.field(), problem.message());
            if (problems.isEmpty()) built = candidate;
        }
        if (built != null) preview.painter = built;
        preview.stale = built == null && preview.painter != null;
        showProblems(problems);
        preview.repaint();
    }

    private void showProblems(Map<Design.Field, String> problems) {
        for (Design.Field field : Design.Field.values()) {
            JLabel message = fieldErrors.get(field);
            JComponent control = fieldControls.get(field);
            if (message == null || control == null) continue;
            String text = problems.get(field);
            message.setText(text == null ? "" : text);
            message.setVisible(text != null);
            control.putClientProperty("JComponent.outline", text == null ? null : "error");
            var context = control.getAccessibleContext();
            if (context != null) {
                String base = baseDescriptions.computeIfAbsent(control, c -> context.getAccessibleDescription() == null ? "" : context.getAccessibleDescription());
                context.setAccessibleDescription(text == null ? base : (base + " Error: " + text).trim());
            }
            control.repaint();
        }
        String other = problems.get(Design.Field.OTHER);
        String all = String.join(" ", problems.values());
        String summary = problems.isEmpty() ? " "
            : (other != null ? other + " " : "") + (problems.size() == 1 && other == null ? "Fix the highlighted field."
                : "Fix the highlighted fields.") + " The preview shows the last valid version.";
        setError(summary, all);
    }

    /** Shows the summary line; assistive technology gets the full text of every problem when it appears or changes. */
    private void setError(String summary, String details) {
        error.setText(summary);
        error.getAccessibleContext().setAccessibleDescription(details.isBlank() ? null : details);
        if (!details.equals(announced)) {
            announced = details;
            if (!details.isBlank() && AccessibleAnnouncerUtil.isAnnouncingAvailable()) AccessibleAnnouncerUtil.announce(error, details, true);
        }
    }

    private void showDesign() {
        updating = true;
        try {
            Design d = selected();
            name.setText(d.name);
            object.setText(d.objectImage);
            trail.setText(d.trailImage);
            background.setText(d.backgroundImage);
            color.setText(d.backgroundColor);
            syncSwatch();
            size.setValue(d.size);
            speed.setValue(d.speed);
            trailLength.setValue(d.trailLength);
            motion.setSelectedItem(d.motion);
        } finally { updating = false; }
        preview.painter = null; // a different design must never show the previous one's preview
        updateUseButton();
        updatePreview();
    }

    private void populate() {
        updating = true;
        try {
            model.clear();
            for (Design d : draft.designs) model.addElement(d);
            designs.setSelectedValue(draft.selected(), true);
        } finally { updating = false; }
        showDesign();
    }

    /** Compares the draft with what is saved, so changing something back is no longer a modification. */
    @Override public boolean isModified() {
        if (draft == null) return false;
        draft.enabled = enabled.isSelected();
        return !draft.sameContent(Objects.requireNonNull(ProgressSettings.getInstance().getState()));
    }

    @Override public void apply() throws ConfigurationException {
        try {
            size.commitEdit();
            speed.commitEdit();
            trailLength.commitEdit();
            draft.enabled = enabled.isSelected();
            for (Design design : draft.designs) {
                var problems = design.problems();
                if (problems.isEmpty()) continue;
                designs.setSelectedValue(design, true); // show the offending design so its fields are highlighted
                String label = design.name == null || design.name.isBlank() ? "A design" : "\"" + design.name + "\"";
                throw new ConfigurationException(label + ": " + problems.getFirst().message());
            }
            draft.validate();
            if (draft.enabled) new ProgressPainter(draft.selected());
            ProgressSettings.getInstance().loadState(draft);
            ProgressInstallation.getInstance().reload();
            designs.repaint();
            updateUseButton();
        } catch (IOException | IllegalArgumentException | java.text.ParseException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    @Override public void reset() {
        if (root == null) return;
        draft = Objects.requireNonNull(ProgressSettings.getInstance().getState()).copy();
        enabled.setSelected(draft.enabled);
        populate();
    }

    @Override public void disposeUIResources() {
        if (preview != null) { preview.timer.stop(); preview.painter = null; }
        fieldErrors.clear();
        fieldControls.clear();
        baseDescriptions.clear();
        announced = "";
        root = null;
        content = null;
        draft = null;
        model = null;
        designs = null;
        preview = null;
    }

    private static final class Preview extends JPanel {
        private final long started = System.nanoTime();
        private final Timer timer = new Timer(33, e -> repaint());
        private ProgressPainter painter;
        private boolean stale;
        private double progress = 0.65;

        Preview() { setPreferredSize(JBUI.size(400, 190)); setMinimumSize(JBUI.size(240, 190)); }
        @Override public void addNotify() { super.addNotify(); timer.start(); }
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (painter == null) return;
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setFont(JBUI.Fonts.label());
                g.setColor(getForeground());
                g.drawString("Indeterminate", 0, JBUI.scale(12));
                g.translate(0, JBUI.scale(18));
                painter.paint(g, getWidth(), JBUI.scale(30), 0, true, seconds, getBackground(), true);
                g.translate(0, JBUI.scale(42));
                g.setColor(getForeground());
                g.drawString("Determinate", 0, 0);
                g.translate(0, JBUI.scale(6));
                painter.paint(g, getWidth(), JBUI.scale(30), progress, false, seconds, getBackground(), true);
                g.translate(0, JBUI.scale(42));
                g.setColor(getForeground());
                g.drawString("Compact (16 px) and status bar (8 px)", 0, 0);
                g.translate(0, JBUI.scale(6));
                painter.paint(g, getWidth(), JBUI.scale(16), progress, true, seconds, getBackground(), true);
                g.translate(0, JBUI.scale(22));
                painter.paint(g, getWidth(), JBUI.scale(8), progress, false, seconds, getBackground(), true);
                if (stale) {
                    g.translate(0, JBUI.scale(22));
                    g.setColor(JBUI.CurrentTheme.Label.errorForeground());
                    g.drawString("Showing the last valid version of this design", 0, 0);
                }
            } finally { g.dispose(); }
        }
    }
}
