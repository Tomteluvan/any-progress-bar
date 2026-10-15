package dev.anyprogressbar;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jetbrains.annotations.NotNull;

public final class DesignConfigurable implements Configurable {
    private ProgressSettings.Data draft;
    private JPanel root;
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
    private Preview preview;
    private boolean updating;
    private boolean dirty;

    @Override public String getDisplayName() { return "Any Progress Bar"; }

    @Override public JComponent createComponent() {
        root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(JBUI.Borders.empty(12));
        enabled = new JCheckBox("Enable custom progress bars");
        enabled.addActionListener(e -> dirty = true);
        root.add(enabled, BorderLayout.NORTH);

        model = new DefaultListModel<>();
        designs = new JList<>(model);
        designs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        designs.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && selected() != null) {
                draft.selectedId = selected().id;
                dirty = true;
                showDesign();
            }
        });
        JPanel library = new JPanel(new BorderLayout(6, 6));
        JScrollPane scroll = new JScrollPane(designs);
        scroll.setPreferredSize(JBUI.size(200, 300));
        library.add(scroll);
        JPanel actions = new JPanel(new GridLayout(0, 1, 4, 4));
        actions.add(button("New", () -> add(new Design())));
        actions.add(button("Duplicate", () -> {
            if (selected() == null) return;
            Design copy = selected().copy();
            copy.id = UUID.randomUUID().toString();
            copy.name += " (copy)";
            add(copy);
        }));
        actions.add(button("Delete", () -> {
            if (model.size() == 1) {
                Messages.showInfoMessage(root, "Keep at least one design.", getDisplayName());
                return;
            }
            int index = designs.getSelectedIndex();
            if (index < 0) return;
            draft.designs.remove(index);
            model.remove(index);
            designs.setSelectedIndex(Math.min(index, model.size() - 1));
            dirty = true;
        }));
        actions.add(button("Restore presets", () -> {
            if (Messages.showYesNoDialog(root, "Replace your design library with the built-in presets?",
                    getDisplayName(), Messages.getQuestionIcon()) != Messages.YES) return;
            draft.designs = new java.util.ArrayList<>(Design.presets());
            draft.selectedId = draft.designs.getFirst().id;
            populate();
            dirty = true;
        }));
        actions.add(button("Export...", this::exportSelected));
        actions.add(button("Import...", this::importDesign));
        library.add(actions, BorderLayout.SOUTH);
        root.add(library, BorderLayout.WEST);

        JPanel editor = new JPanel(new GridBagLayout());
        name = new JTextField(24);
        object = imageField();
        trail = imageField();
        background = imageField();
        color = new JTextField(12);
        size = new JSpinner(new SpinnerNumberModel(24, 8, 64, 1));
        speed = new JSpinner(new SpinnerNumberModel(100, 10, 400, 10));
        trailLength = new JSpinner(new SpinnerNumberModel(160, 0, 600, 10));
        motion = new JComboBox<>(Design.Motion.values());
        row(editor, 0, "Name", name);
        row(editor, 1, "Object image", imageRow(object, value -> selected().objectImage = value));
        row(editor, 2, "Trail image", imageRow(trail, value -> selected().trailImage = value));
        row(editor, 3, "Background image", imageRow(background, value -> selected().backgroundImage = value));
        JPanel colors = new JPanel(new BorderLayout(4, 0));
        colors.add(color);
        swatch = new ColorPanel();
        swatch.addActionListener(e -> {
            Color chosen = swatch.getSelectedColor();
            if (chosen != null) color.setText(String.format("#%06X", chosen.getRGB() & 0xffffff));
        });
        JPanel colorButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        colorButtons.add(swatch);
        colorButtons.add(button("Use IDE colour", () -> color.setText("")));
        colors.add(colorButtons, BorderLayout.EAST);
        row(editor, 4, "Background colour", colors);
        row(editor, 5, "Object size (px, 8-64)", size);
        row(editor, 6, "Speed (%, 10-400)", speed);
        row(editor, 7, "Trail length (px, 0-600)", trailLength);
        size.setToolTipText("Height of the object in pixels (8-64); capped to the bar height.");
        speed.setToolTipText("Percent. 100 = one lap every 4 seconds for indeterminate bars (10-400).");
        trailLength.setToolTipText("Pixels of trail drawn behind the object (0-600); 0 hides the trail.");
        motion.setToolTipText("How the object moves. Ping-pong only changes indeterminate bars.");
        color.setToolTipText("#RRGGBB, or empty to use the IDE colour.");
        row(editor, 8, "Motion", motion);
        row(editor, 9, "", new JLabel("Clear any image to omit it. Empty colour uses the IDE colour."));
        row(editor, 10, "", new JLabel("Background images overlay the colour. PNG, JPG and static SVG supported."));
        error = new JLabel(" ");
        error.setForeground(JBUI.CurrentTheme.Label.errorForeground());
        row(editor, 11, "", error);
        preview = new Preview();
        row(editor, 12, "Live preview", preview);
        JSlider progress = new JSlider(0, 100, 65);
        progress.addChangeListener(e -> { preview.progress = progress.getValue() / 100.0; preview.repaint(); });
        row(editor, 13, "Determinate progress", progress);
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = 14;
        filler.weighty = 1;
        editor.add(new JPanel(), filler);
        root.add(editor, BorderLayout.CENTER);
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

    private static JTextField imageField() {
        JTextField field = new JTextField(24);
        field.setEditable(false);
        return field;
    }

    private JPanel imageRow(JTextField field, Consumer<String> assign) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(field);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttons.add(button("Browse", () -> {
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
                Messages.showErrorDialog(root, e.getMessage(), "Could not import image");
            }
        }));
        buttons.add(button("Clear", () -> { assign.accept(""); field.setText(""); changed(); }));
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private static void row(JPanel panel, int index, String label, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = index;
        c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insets(4);
        panel.add(new JLabel(label), c);
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
        dirty = true;
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
        dirty = true;
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
            Messages.showErrorDialog(root, e.getMessage(), "Could not export design");
        }
    }

    private void importDesign() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Any Progress Bar design", DesignPack.EXTENSION));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        try {
            add(DesignPack.importFrom(chooser.getSelectedFile().toPath(),
                Path.of(PathManager.getConfigPath(), "any-progress-bar", "assets")));
        } catch (IOException | IllegalArgumentException e) {
            Messages.showErrorDialog(root, e.getMessage(), "Could not import design");
        }
    }

    private void updatePreview() {
        try {
            preview.painter = new ProgressPainter(selected());
            error.setText(" ");
        } catch (IOException | IllegalArgumentException e) {
            preview.painter = null;
            error.setText(e.getMessage());
        }
        preview.repaint();
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

    @Override public boolean isModified() { return dirty; }

    @Override public void apply() throws ConfigurationException {
        try {
            size.commitEdit();
            speed.commitEdit();
            trailLength.commitEdit();
            draft.enabled = enabled.isSelected();
            draft.validate();
            if (draft.enabled) new ProgressPainter(draft.selected());
            ProgressSettings.getInstance().loadState(draft);
            ProgressInstallation.getInstance().reload();
            dirty = false;
        } catch (IOException | IllegalArgumentException | java.text.ParseException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    @Override public void reset() {
        if (root == null) return;
        draft = ProgressSettings.getInstance().getState().copy();
        enabled.setSelected(draft.enabled);
        populate();
        dirty = false;
    }

    @Override public void disposeUIResources() {
        if (preview != null) { preview.timer.stop(); preview.painter = null; }
        root = null;
        draft = null;
        model = null;
        designs = null;
        preview = null;
    }

    private static final class Preview extends JPanel {
        private final long started = System.nanoTime();
        private final Timer timer = new Timer(33, e -> repaint());
        private ProgressPainter painter;
        private double progress = 0.65;

        Preview() { setPreferredSize(new Dimension(400, 170)); }
        @Override public void addNotify() { super.addNotify(); timer.start(); }
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (painter == null) return;
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(getForeground());
                g.drawString("Indeterminate", 0, 12);
                g.translate(0, 18);
                painter.paint(g, getWidth(), 30, 0, true, seconds, getBackground(), true);
                g.translate(0, 42);
                g.setColor(getForeground());
                g.drawString("Determinate", 0, 0);
                g.translate(0, 6);
                painter.paint(g, getWidth(), 30, progress, false, seconds, getBackground(), true);
                g.translate(0, 42);
                g.setColor(getForeground());
                g.drawString("Compact (16 px) and status bar (8 px)", 0, 0);
                g.translate(0, 6);
                painter.paint(g, getWidth(), 16, progress, true, seconds, getBackground(), true);
                g.translate(0, 22);
                painter.paint(g, getWidth(), 8, progress, false, seconds, getBackground(), true);
            } finally { g.dispose(); }
        }
    }
}
