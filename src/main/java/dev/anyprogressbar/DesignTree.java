package dev.anyprogressbar;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * The design library: designs without a category are always listed, the others sit under a category that can be
 * opened and closed. The tree is rebuilt from the list of designs whenever that list changes.
 */
final class DesignTree extends Tree {
    private record Category(String name, int count) {
        @Override public String toString() { return name; }
    }

    private final Set<String> open = new HashSet<>();
    private boolean shown;
    private boolean rebuilding;
    private List<Design> shownDesigns = List.of();

    DesignTree() {
        super(new DefaultTreeModel(new DefaultMutableTreeNode()));
        setRootVisible(false);
        setShowsRootHandles(true);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setCellRenderer(new ColoredTreeCellRenderer() {
            @Override public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                                        boolean leaf, int row, boolean hasFocus) {
                Object item = ((DefaultMutableTreeNode) value).getUserObject();
                if (item instanceof Category category) {
                    append(category.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    append("  " + category.count(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else if (item != null) {
                    append(item.toString());
                }
            }
        });
        addTreeExpansionListener(new TreeExpansionListener() {
            @Override public void treeExpanded(TreeExpansionEvent event) { track(event, true); }
            @Override public void treeCollapsed(TreeExpansionEvent event) { track(event, false); }
        });
    }

    private void track(TreeExpansionEvent event, boolean expanded) {
        if (rebuilding) return;
        if (((DefaultMutableTreeNode) event.getPath().getLastPathComponent()).getUserObject() instanceof Category category) {
            if (expanded) open.add(category.name()); else open.remove(category.name());
        }
    }

    /** The selected design, or null when nothing or a category is selected. */
    Design selectedDesign() {
        TreePath path = getSelectionPath();
        if (path == null) return null;
        return ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() instanceof Design design ? design : null;
    }

    /** Every design in the tree, in library order. */
    List<Design> designs() { return shownDesigns; }

    /** Selects {@code design}, opening its category. */
    void select(Design design) { show(shownDesigns, design); }

    /**
     * Shows {@code designs} and selects {@code selected}, opening its category. Categories stay as open or closed as
     * they were; the first time only the category of the selected design is open.
     */
    void show(List<Design> designs, Design selected) {
        shownDesigns = designs;
        rebuilding = true;
        try {
            if (!shown) {
                shown = true;
                if (selected != null && !selected.category.isBlank()) open.add(selected.category);
            }
            var root = new DefaultMutableTreeNode();
            var groups = new LinkedHashMap<String, List<Design>>();
            for (String category : Design.CATEGORIES) groups.put(category, new ArrayList<>());
            DefaultMutableTreeNode selectedNode = null;
            for (Design design : designs) {
                if (design.category == null || design.category.isBlank()) {
                    var node = new DefaultMutableTreeNode(design);
                    root.add(node);
                    if (design == selected) selectedNode = node;
                } else {
                    groups.computeIfAbsent(design.category, key -> new ArrayList<>()).add(design);
                }
            }
            var categoryNodes = new ArrayList<DefaultMutableTreeNode>();
            for (var group : groups.entrySet()) {
                if (group.getValue().isEmpty()) continue;
                var categoryNode = new DefaultMutableTreeNode(new Category(group.getKey(), group.getValue().size()));
                for (Design design : group.getValue()) {
                    var node = new DefaultMutableTreeNode(design);
                    categoryNode.add(node);
                    if (design == selected) {
                        selectedNode = node;
                        open.add(group.getKey());
                    }
                }
                root.add(categoryNode);
                categoryNodes.add(categoryNode);
            }
            ((DefaultTreeModel) getModel()).setRoot(root);
            for (var categoryNode : categoryNodes) {
                if (open.contains(((Category) categoryNode.getUserObject()).name())) expandPath(new TreePath(categoryNode.getPath()));
            }
            if (selectedNode != null) setSelectionPath(new TreePath(selectedNode.getPath()));
        } finally { rebuilding = false; }
    }
}
