package dev.anyprogressbar;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.UIManager;

public class PluginIntegrationTest extends BasePlatformTestCase {
    private ProgressSettings.Data previous;

    /** The painter is built on a background thread; wait (while dispatching events) until it has been installed. */
    private static void awaitReload() {
        PlatformTestUtil.waitForFuture(ProgressInstallation.getInstance().latestReload(), 10_000);
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        previous = ProgressSettings.getInstance().getState().copy();
        ProgressSettings.getInstance().loadState(new ProgressSettings.Data());
    }

    @Override protected void tearDown() throws Exception {
        try {
            ProgressSettings.getInstance().loadState(previous);
            ProgressInstallation.getInstance().reload();
            awaitReload();
        } finally { super.tearDown(); }
    }

    public void testSettingsRoundTripPreservesLibraryAndSelection() {
        ProgressSettings.Data state = new ProgressSettings.Data();
        Design custom = new Design();
        custom.name = "Object only";
        custom.objectImage = "bundled:fox.svg";
        custom.motion = Design.Motion.WIGGLE;
        custom.speed = 180;
        state.designs.add(custom);
        state.selectedId = custom.id;
        var xml = XmlSerializer.serialize(state);
        ProgressSettings.Data restored = XmlSerializer.deserialize(xml, ProgressSettings.Data.class);
        restored.validate();
        assertEquals(Design.presets().size() + 1, restored.designs.size());
        assertEquals("Object only", restored.selected().name);
        assertEquals(180, restored.selected().speed);
        assertEquals(Design.Motion.WIGGLE, restored.selected().motion);
        assertEquals("", restored.selected().trailImage);
        assertEquals("", restored.selected().backgroundColor);
    }

    public void testInstallingDisablingAndReenablingDelegate() {
        ProgressInstallation installation = ProgressInstallation.getInstance();
        ProgressSettings.Data data = ProgressSettings.getInstance().getState().copy();
        data.enabled = false;
        ProgressSettings.getInstance().loadState(data);
        PlatformTestUtil.waitForFuture(installation.reload(), 10_000);
        Object original = UIManager.get("ProgressBarUI");
        data.enabled = true;
        ProgressSettings.getInstance().loadState(data);
        PlatformTestUtil.waitForFuture(installation.reload(), 10_000);
        assertEquals(AnimalProgressBarUI.class.getName(), UIManager.get("ProgressBarUI"));
        JProgressBar bar = new JProgressBar();
        assertInstanceOf(bar.getUI(), AnimalProgressBarUI.class);
        bar.setSize(320, 24);
        var image = new java.awt.image.BufferedImage(320, 24, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            bar.setValue(50);
            bar.paint(graphics);
            bar.setIndeterminate(true);
            bar.paint(graphics);
        } finally { graphics.dispose(); }
        data.enabled = false;
        ProgressSettings.getInstance().loadState(data);
        PlatformTestUtil.waitForFuture(installation.reload(), 10_000);
        assertEquals(original, UIManager.get("ProgressBarUI"));
        bar.updateUI();
        assertFalse(bar.getUI() instanceof AnimalProgressBarUI);
        assertNull(UIManager.get(AnimalProgressBarUI.class.getName()));
    }

    public void testReloadLoadsImagesInTheBackgroundAndPublishesOnlyTheLatestResult() {
        ProgressInstallation installation = ProgressInstallation.getInstance();
        ProgressSettings.Data data = ProgressSettings.getInstance().getState().copy();
        data.enabled = true;
        ProgressSettings.getInstance().loadState(data);
        PlatformTestUtil.waitForFuture(installation.reload(), 10_000);
        ProgressPainter first = installation.painter();
        assertNotNull(first);

        Design broken = new Design();
        broken.name = "Broken";
        broken.objectImage = "/does-not-exist/object.png";
        data.designs.add(broken);
        data.selectedId = broken.id;
        ProgressSettings.getInstance().loadState(data);
        var superseded = installation.reload();
        assertSame("nothing is installed until the background work has finished and events are dispatched", first, installation.painter());

        data.selectedId = data.designs.getFirst().id;
        ProgressSettings.getInstance().loadState(data);
        var latest = installation.reload();
        PlatformTestUtil.waitForFuture(latest, 10_000);
        assertTrue("a newer reload completes the one it replaced", superseded.isDone());
        assertNotSame(first, installation.painter());
        assertTrue("the broken design's result must never be published", installation.painter().skippedLayers().isEmpty());
    }

    public void testEditorCreatesAppliesDuplicatesAndDeletesDesigns() throws ConfigurationException {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            assertFalse(configurable.isModified());
            findButton(root, "New").doClick();
            assertTrue(configurable.isModified());
            findButton(root, "Use this design").doClick();
            configurable.apply();
            assertEquals(Design.presets().size() + 1, ProgressSettings.getInstance().getState().designs.size());
            assertEquals("My design", ProgressSettings.getInstance().getState().selected().name);
            assertFalse(configurable.isModified());
            findButton(root, "Duplicate").doClick();
            findButton(root, "Use this design").doClick();
            configurable.apply();
            assertEquals(Design.presets().size() + 2, ProgressSettings.getInstance().getState().designs.size());
            assertEquals("My design (copy)", ProgressSettings.getInstance().getState().selected().name);
            findButton(root, "Delete").doClick();
            configurable.apply();
            assertEquals(Design.presets().size() + 1, ProgressSettings.getInstance().getState().designs.size());
        } finally { configurable.disposeUIResources(); }
    }

    @SuppressWarnings("unchecked")
    private static javax.swing.JList<Design> designList(Container root) {
        return (javax.swing.JList<Design>) find(root, c -> c instanceof javax.swing.JList<?>);
    }

    public void testBrowsingDesignsDoesNotChangeTheActiveOne() throws ConfigurationException {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            String active = ProgressSettings.getInstance().getState().selectedId;
            var list = designList(root);
            assertEquals(active, list.getSelectedValue().id);
            assertFalse("the active design is already in use", findButton(root, "Use this design").isEnabled());
            for (int i = 0; i < list.getModel().getSize(); i++) list.setSelectedIndex(i);
            assertFalse("browsing is not a modification", configurable.isModified());
            list.setSelectedIndex(list.getModel().getSize() - 1);
            assertTrue(findButton(root, "Use this design").isEnabled());
            configurable.apply();
            assertEquals("applying must keep the active design", active, ProgressSettings.getInstance().getState().selectedId);
        } finally { configurable.disposeUIResources(); }
    }

    public void testUseThisDesignActivatesTheSelectedDesignAndMarksItInTheList() throws ConfigurationException {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            var list = designList(root);
            String original = ProgressSettings.getInstance().getState().selectedId;
            list.setSelectedIndex(2);
            Design other = list.getSelectedValue();
            findButton(root, "Use this design").doClick();
            assertTrue(configurable.isModified());
            assertFalse(findButton(root, "Use this design").isEnabled());
            var renderer = list.getCellRenderer();
            var active = (javax.swing.JComponent) renderer.getListCellRendererComponent(list, other, 2, false, false);
            assertTrue(active.toString().contains("Active") || active.getAccessibleContext().getAccessibleName().contains("Active"));
            var inactive = (javax.swing.JComponent) renderer.getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false);
            assertFalse(inactive.getAccessibleContext().getAccessibleName().contains("Active"));
            configurable.apply();
            assertEquals(other.id, ProgressSettings.getInstance().getState().selectedId);
            assertFalse(configurable.isModified());
            // choosing the original again is a real change, and applying it switches back
            list.setSelectedValue(list.getModel().getElementAt(0), true);
            assertEquals(original, list.getSelectedValue().id);
            findButton(root, "Use this design").doClick();
            assertTrue(configurable.isModified());
            configurable.apply();
            assertEquals(original, ProgressSettings.getInstance().getState().selectedId);
            assertFalse(configurable.isModified());
        } finally { configurable.disposeUIResources(); }
    }

    public void testModifiedStateFollowsRealDifferences() {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            assertFalse(configurable.isModified());
            JTextField color = findEditableField(root, "#242437");
            color.setText("#000000");
            assertTrue(configurable.isModified());
            color.setText("#242437");
            assertFalse("changing a value back is not a modification", configurable.isModified());
            JCheckBox enabled = (JCheckBox) find(root, c -> c instanceof JCheckBox);
            enabled.doClick();
            assertTrue(configurable.isModified());
            enabled.doClick();
            assertFalse(configurable.isModified());
        } finally { configurable.disposeUIResources(); }
    }

    public void testDeletingTheActiveDesignActivatesItsNeighbour() throws ConfigurationException {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            var list = designList(root);
            Design active = list.getSelectedValue();
            findButton(root, "Delete").doClick();
            configurable.apply();
            var state = ProgressSettings.getInstance().getState();
            assertNotSame(active.id, state.selectedId);
            assertEquals(state.selectedId, list.getSelectedValue().id);
            state.validate();
        } finally { configurable.disposeUIResources(); }
    }

    public void testMergingAndReplacingAnImportedLibrary() throws ConfigurationException {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            Design added = new Design();
            added.name = "From another machine";
            added.objectImage = "bundled:fox.svg";
            var plan = LibraryPack.planMerge(ProgressSettings.getInstance().getState().designs, java.util.List.of(Design.presets().getFirst().copy(), added));
            assertEquals(1, plan.duplicates());
            configurable.mergeImported(plan);
            assertTrue(configurable.isModified());
            assertEquals(added.name, designList(root).getSelectedValue().name);
            configurable.apply();
            var state = ProgressSettings.getInstance().getState();
            assertEquals(Design.presets().size() + 1, state.designs.size());
            assertEquals("merging keeps the active design", "preset-cat", state.selectedId);

            Design only = new Design();
            only.name = "Only me";
            Design second = new Design();
            second.name = "Second";
            configurable.replaceLibrary(new LibraryPack.Imported(java.util.List.of(only, second), 1));
            configurable.apply();
            state = ProgressSettings.getInstance().getState();
            assertEquals(2, state.designs.size());
            assertEquals("Second", state.selected().name);
            assertEquals("Second", designList(root).getSelectedValue().name);
        } finally { configurable.disposeUIResources(); }
    }

    public void testEditorScrollsInsteadOfClippingInSmallWindows() {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            assertInstanceOf(root, javax.swing.JScrollPane.class);
            var scroll = (javax.swing.JScrollPane) root;
            assertEquals(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, scroll.getVerticalScrollBarPolicy());
            assertEquals(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, scroll.getHorizontalScrollBarPolicy());
            // everything is still reachable inside the scroll pane
            assertNotNull(findButton(root, "Export..."));
            assertTrue(scroll.getViewport().getView().getPreferredSize().height > 0);
        } finally { configurable.disposeUIResources(); }
    }

    public void testEditorControlsAreLabelledForAssistiveTechnology() {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            var labels = new java.util.ArrayList<javax.swing.JLabel>();
            var buttons = new java.util.ArrayList<JButton>();
            collect(root, labels, buttons);
            var labelled = labels.stream().filter(l -> l.getLabelFor() != null).toList();
            assertEquals(10, labelled.size());
            for (var label : labelled) {
                assertTrue(label.getText(), label.getLabelFor().isFocusable());
                assertTrue(label.getText(), label.getDisplayedMnemonic() != 0);
            }
            // a mnemonic can only be used once per window
            var mnemonics = new java.util.HashSet<Integer>();
            for (var label : labelled) assertTrue("duplicate mnemonic on " + label.getText(), mnemonics.add(Character.toUpperCase(label.getDisplayedMnemonic())));
            for (var button : buttons) {
                if (button.getMnemonic() != 0) assertTrue("duplicate mnemonic on " + button.getText(), mnemonics.add(Character.toUpperCase(button.getMnemonic())));
            }
            for (String text : new String[]{"New", "Duplicate", "Delete", "Restore presets", "Export...", "Import...", "Use IDE colour"}) {
                assertTrue(text + " needs a mnemonic", findButton(root, text).getMnemonic() != 0);
            }
            // the repeated Browse and Clear buttons say which layer they act on
            var names = new java.util.HashSet<String>();
            for (var button : buttons) {
                if (button.getText().equals("Browse") || button.getText().equals("Clear")) {
                    assertTrue(names.add(button.getAccessibleContext().getAccessibleName()));
                    assertNotNull(button.getAccessibleContext().getAccessibleDescription());
                }
            }
            assertEquals(6, names.size());
            assertTrue(names.contains("Browse for trail image"));
            assertTrue(names.contains("Clear background image"));
        } finally { configurable.disposeUIResources(); }
    }

    public void testValidationMessagesAreExposedToAssistiveTechnology() {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            JTextField color = findEditableField(root, "#242437");
            color.setText("not a color");
            var message = fieldError(root, "Background colour error");
            assertTrue(message.getText().contains("#RRGGBB"));
            assertTrue(color.getAccessibleContext().getAccessibleDescription().contains("#RRGGBB"));
            var labels = new java.util.ArrayList<javax.swing.JLabel>();
            collect(root, labels, new java.util.ArrayList<>());
            var summary = labels.stream().filter(l -> "Validation message".equals(l.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
            assertTrue(summary.getAccessibleContext().getAccessibleDescription().contains("#RRGGBB"));
        } finally { configurable.disposeUIResources(); }
    }

    private static javax.swing.JLabel fieldError(Container root, String accessibleName) {
        var labels = new java.util.ArrayList<javax.swing.JLabel>();
        collect(root, labels, new java.util.ArrayList<>());
        return labels.stream().filter(l -> accessibleName.equals(l.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    public void testProblemsAreShownNextToTheFieldThatCausedThem() {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            JTextField color = findEditableField(root, "#242437");
            var colorError = fieldError(root, "Background colour error");
            assertFalse(colorError.isVisible());
            assertNull(color.getClientProperty("JComponent.outline"));
            color.setText("blue");
            assertTrue(colorError.isVisible());
            assertEquals("error", color.getClientProperty("JComponent.outline"));
            assertFalse("other fields stay clean", fieldError(root, "Name error").isVisible());
            // fixing the value clears the message and the highlight
            color.setText("#123456");
            assertFalse(colorError.isVisible());
            assertNull(color.getClientProperty("JComponent.outline"));
        } finally { configurable.disposeUIResources(); }
    }

    public void testBrokenImageNamesTheLayerThatFailed() {
        ProgressSettings.Data data = new ProgressSettings.Data();
        Design broken = new Design();
        broken.name = "Broken";
        broken.objectImage = "/does-not-exist/object.png";
        broken.trailImage = "bundled:stars.svg";
        broken.backgroundImage = "/does-not-exist/background.png";
        data.designs.add(broken);
        data.selectedId = broken.id;
        ProgressSettings.getInstance().loadState(data);
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            PlatformTestUtil.waitForFuture(configurable.previewIdle(), 10_000);
            var object = fieldError(root, "Object image error");
            var trail = fieldError(root, "Trail image error");
            var background = fieldError(root, "Background image error");
            assertTrue(object.isVisible() && object.getText().startsWith("Object image:"));
            assertFalse(trail.isVisible());
            assertTrue(background.isVisible() && background.getText().startsWith("Background image:"));
        } finally { configurable.disposeUIResources(); }
    }

    public void testApplyShowsTheOffendingDesignAndNamesIt() {
        ProgressSettings.Data data = new ProgressSettings.Data();
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            findButton(root, "New").doClick();
            findButton(root, "New").doClick();
            JTextField colorField = (JTextField) find(root, c -> c instanceof JTextField f && f.isEditable() && f.getText().isEmpty() && f.getColumns() == 12);
            colorField.setText("nope");
            findButton(root, "Duplicate").doClick(); // selects another design, leaving the invalid one behind
            try {
                configurable.apply();
                fail("an invalid design anywhere in the library must block apply");
            } catch (ConfigurationException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("My design"));
                assertTrue(expected.getMessage().contains("#RRGGBB"));
            }
            assertTrue("the offending design is shown so its fields are highlighted", fieldError(root, "Background colour error").isVisible());
        } finally { configurable.disposeUIResources(); }
    }

    private static void collect(Container parent, java.util.List<javax.swing.JLabel> labels, java.util.List<JButton> buttons) {
        for (Component component : parent.getComponents()) {
            if (component instanceof javax.swing.JLabel label) labels.add(label);
            if (component instanceof JButton button) buttons.add(button);
            if (component instanceof Container container) collect(container, labels, buttons);
        }
    }

    public void testInvalidColorBlocksApplyAndResetDiscardsDraft() throws ConfigurationException {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            JTextField color = findEditableField(root, "#242437");
            color.setText("not a color");
            try {
                configurable.apply();
                fail("Invalid colour should prevent applying settings");
            } catch (ConfigurationException expected) {
                assertTrue(expected.getMessage().contains("#RRGGBB"));
            }
            assertEquals("#242437", ProgressSettings.getInstance().getState().selected().backgroundColor);
            configurable.reset();
            assertFalse(configurable.isModified());
            assertEquals("#242437", color.getText());
        } finally { configurable.disposeUIResources(); }
    }

    public void testMissingInactiveArtworkDoesNotBlockSwitchingOrDisabling() throws ConfigurationException {
        ProgressSettings.Data data = new ProgressSettings.Data();
        Design broken = new Design();
        broken.name = "Missing artwork";
        broken.objectImage = "/does-not-exist/animal.png";
        data.designs.add(broken);
        ProgressSettings.getInstance().loadState(data);
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            configurable.createComponent();
            configurable.apply();
            awaitReload();
            assertNotNull(ProgressInstallation.getInstance().painter());
        } finally { configurable.disposeUIResources(); }
        data.selectedId = broken.id;
        ProgressSettings.getInstance().loadState(data);
        configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            Component checkbox = find(root, c -> c instanceof JCheckBox);
            assertNotNull(checkbox);
            ((JCheckBox) checkbox).doClick();
            configurable.apply();
            assertFalse(ProgressSettings.getInstance().getState().enabled);
            awaitReload();
            assertNull(ProgressInstallation.getInstance().painter());
        } finally { configurable.disposeUIResources(); }
    }

    private static JButton findButton(Container parent, String text) {
        Component result = find(parent, c -> c instanceof JButton b && b.getText().equals(text));
        assertNotNull("Missing button " + text, result);
        return (JButton) result;
    }

    private static JTextField findEditableField(Container parent, String text) {
        Component result = find(parent, c -> c instanceof JTextField f && f.isEditable() && f.getText().equals(text));
        assertNotNull("Missing field " + text, result);
        return (JTextField) result;
    }

    private static Component find(Container parent, java.util.function.Predicate<Component> predicate) {
        for (Component component : parent.getComponents()) {
            if (predicate.test(component)) return component;
            if (component instanceof Container container) {
                Component match = find(container, predicate);
                if (match != null) return match;
            }
        }
        return null;
    }
}
