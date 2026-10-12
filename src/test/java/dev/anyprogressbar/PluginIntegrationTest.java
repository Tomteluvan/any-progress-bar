package dev.anyprogressbar;

import com.intellij.openapi.options.ConfigurationException;
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

    @Override protected void setUp() throws Exception {
        super.setUp();
        previous = ProgressSettings.getInstance().getState().copy();
        ProgressSettings.getInstance().loadState(new ProgressSettings.Data());
    }

    @Override protected void tearDown() throws Exception {
        try {
            ProgressSettings.getInstance().loadState(previous);
            ProgressInstallation.getInstance().reload();
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
        installation.reload();
        Object original = UIManager.get("ProgressBarUI");
        data.enabled = true;
        ProgressSettings.getInstance().loadState(data);
        installation.reload();
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
        installation.reload();
        assertEquals(original, UIManager.get("ProgressBarUI"));
        bar.updateUI();
        assertFalse(bar.getUI() instanceof AnimalProgressBarUI);
        assertNull(UIManager.get(AnimalProgressBarUI.class.getName()));
    }

    public void testEditorCreatesAppliesDuplicatesAndDeletesDesigns() throws ConfigurationException {
        DesignConfigurable configurable = new DesignConfigurable();
        try {
            JComponent root = configurable.createComponent();
            assertFalse(configurable.isModified());
            findButton(root, "New").doClick();
            assertTrue(configurable.isModified());
            configurable.apply();
            assertEquals(Design.presets().size() + 1, ProgressSettings.getInstance().getState().designs.size());
            assertEquals("My design", ProgressSettings.getInstance().getState().selected().name);
            assertFalse(configurable.isModified());
            findButton(root, "Duplicate").doClick();
            configurable.apply();
            assertEquals(Design.presets().size() + 2, ProgressSettings.getInstance().getState().designs.size());
            assertEquals("My design (copy)", ProgressSettings.getInstance().getState().selected().name);
            findButton(root, "Delete").doClick();
            configurable.apply();
            assertEquals(Design.presets().size() + 1, ProgressSettings.getInstance().getState().designs.size());
        } finally { configurable.disposeUIResources(); }
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
