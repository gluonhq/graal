/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.visualizer.coordinator;

import org.graalvm.visualizer.coordinator.actions.AutoFreezeSessionsAction;
import org.graalvm.visualizer.coordinator.actions.FreezeSessionsAction;
import org.graalvm.visualizer.coordinator.actions.ImportAction;
import org.graalvm.visualizer.coordinator.actions.RemoveAction;
import org.graalvm.visualizer.coordinator.actions.RemoveAllAction;
import org.graalvm.visualizer.coordinator.actions.SaveAllAction;
import org.graalvm.visualizer.coordinator.actions.SaveAsAction;
import org.graalvm.visualizer.coordinator.impl.OutlineSearchController;
import org.graalvm.visualizer.coordinator.impl.OutlineTreeView;
import org.graalvm.visualizer.coordinator.impl.SessionManagerImpl;
import org.graalvm.visualizer.coordinator.impl.SessionNode;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.util.ExternalDropTarget;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerEvent;
import org.graalvm.visualizer.view.api.DiagramViewerListener;
import org.netbeans.spi.project.ui.PathFinder;
import org.openide.ErrorManager;
import org.openide.awt.Actions;
import org.openide.awt.QuickSearch;
import org.openide.awt.Toolbar;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeAdapter;
import org.openide.nodes.NodeMemberEvent;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.NodeAction;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.dnd.DropTarget;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

public final class OutlineTopComponent extends TopComponent implements ExplorerManager.Provider, LookupListener {
    private static final String EXPAND_ICON = "org/graalvm/visualizer/search/resources/expandTree.png";
    private static final String COLLAPSE_ICON = "org/graalvm/visualizer/search/resources/collapseTree.png";
    private static final String SEARCH_ICON = "org/openide/awt/resources/quicksearch/find.png";
    private static final String PREVIOUS_MATCH_ICON = "org/graalvm/visualizer/search/resources/prev.png";
    private static final String NEXT_MATCH_ICON = "org/graalvm/visualizer/search/resources/next.png";

    public static OutlineTopComponent instance;
    public static final String PREFERRED_ID = "OutlineTopComponent";
    private ExplorerManager manager;
    private final GraphDocument document = SessionManagerImpl.getInstance().getCurrentDocument();
    private Node root;
    private DropTarget dt;
    private DiagramOutlineSynchronized sync;
    private OutlineSearchController searchController;
    private JPanel searchStripPanel;
    private QuickSearch quickSearch;
    private JTextField quickSearchTextField;
    private JToggleButton searchToggleButton;
    private JLabel searchStatusLabel;

    private OutlineTopComponent() {
        initComponents();

        setName(NbBundle.getMessage(OutlineTopComponent.class, "CTL_OutlineTopComponent"));
        setToolTipText(NbBundle.getMessage(OutlineTopComponent.class, "HINT_OutlineTopComponent"));
        initListView();
        initToolbar();

        dt = ExternalDropTarget.createDropTarget(this);
        setDropTarget(dt);
        dt.setActive(true);
    }

    @Override
    protected void componentShowing() {
        super.componentShowing();
        if (sync == null) {
            sync = new DiagramOutlineSynchronized(Lookup.getDefault().lookup(GraphViewer.class));
        }
    }

    private OutlineTreeView outlineTreeView() {
        return (OutlineTreeView) treeView;
    }

    private void initListView() {
        manager = new ExplorerManager();
        resetFolderRoot();
        outlineTreeView().setRootVisible(false);

        // accept external files on outline's contents.
        Component view = treeView.getViewport().getView();
        DropTarget dt = ExternalDropTarget.createDropTarget((JComponent) view);
        dt.setActive(true);
        view.setDropTarget(dt);

        associateLookup(ExplorerUtils.createLookup(manager, getActionMap()));
    }

    private void selectPathOrIgnore(InputGraph element, Queue<FolderElement> path) {
        Node cur = Arrays.asList(manager.getSelectedNodes()).stream().
                filter(n -> n.getLookup().lookup(InputGraph.class) == element).
                findAny().orElse(null);
        if (cur != null) {
            return;
        }
        Node n = findPath(path);
        if (n == null) {
            return;
        }
        try {
            manager.setSelectedNodes(new Node[]{n});
        } catch (PropertyVetoException ex) {
            // ignore
        }
    }

    private Node findPath(Queue<FolderElement> path) {

        Node n = manager.getRootContext();
        FolderElement child;

        while ((child = path.poll()) != null) {
            PathFinder pf = n.getLookup().lookup(PathFinder.class);
            Node n2 = null;

            if (pf != null) {
                n2 = pf.findPath(n, child);
            }
            if (n2 == null) {
                final FolderElement toFind = child;
                n2 = Arrays.asList(n.getChildren().getNodes()).stream().
                        filter(t -> t.getLookup().lookup(FolderElement.class) == toFind).
                        findFirst().orElse(null);
            }
            if (n2 == null) {
                return null;
            }
            n = n2;
        }
        return n;
    }

    private static class SessionChildren extends Children.Keys implements ChangedListener<SessionManagerImpl> {
        private final SessionManagerImpl impl;

        public SessionChildren(SessionManagerImpl impl) {
            this.impl = impl;
        }

        @Override
        protected void removeNotify() {
            impl.getChangedEvent().removeListener(this);
            super.removeNotify();
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            impl.getChangedEvent().addListener(this);
            changed(null);
        }

        @Override
        public void changed(SessionManagerImpl source) {
            SwingUtilities.invokeLater(() -> setKeys(impl.getSessions()));
        }

        @Override
        protected Node[] createNodes(Object t) {
            assert t instanceof GraphDocument;
            return new Node[]{
                    new SessionNode((GraphDocument) t)
            };
        }

    }

    class NewDataExpander extends NodeAdapter {
        @Override
        public void childrenAdded(NodeMemberEvent ev) {
            OutlineTopComponent.this.requestAttention(true);
            Node[] ns = ev.getDelta();
            if (ns.length == 0) {
                return;
            }
            if (ev.getNode() == root) {
                // also expand the new node's children:
                OutlineTreeView btv = outlineTreeView();
                btv.expandNode(ns[0]);
            } else if (ev.getNode().getParentNode() != root) {
                return;
            }
            // add listener just for the highlight
            ns[0].addNodeListener(this);
        }
    }

    private void resetFolderRoot() {
        root = new AbstractNode(new SessionChildren(SessionManagerImpl.getInstance()));
        root.addNodeListener(new NewDataExpander());
        manager.setRootContext(root);
    }

    private void initToolbar() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        this.add(topPanel, BorderLayout.NORTH);

        Toolbar toolbar = new Toolbar();
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); // NOI18N
        toolbar.setBorder(b);
        topPanel.add(toolbar, BorderLayout.NORTH);

        toolbar.add(ImportAction.get(ImportAction.class));

        toolbar.add(((NodeAction) SaveAsAction.get(SaveAsAction.class)).createContextAwareInstance(this.getLookup()));
        toolbar.add(SaveAllAction.get(SaveAllAction.class));

        toolbar.add(((NodeAction) RemoveAction.get(RemoveAction.class)).createContextAwareInstance(this.getLookup()));
        toolbar.add(RemoveAllAction.get(RemoveAllAction.class));

        // Add the explicit toolbar presenters so these actions keep their JToggleButton behavior.
        // Relying on toolbar.add(action) here can lose the custom toggle presenter and its selected-state UI.
        toolbar.add(((Presenter.Toolbar) Actions.forID("IGV", FreezeSessionsAction.ID)).getToolbarPresenter());
        toolbar.add(((Presenter.Toolbar) Actions.forID("IGV", AutoFreezeSessionsAction.ID)).getToolbarPresenter());

        toolbar.add(createTreeToolbarButton(EXPAND_ICON, "Expand all Outline nodes", () -> outlineTreeView().expandAllNodes()));
        toolbar.add(createTreeToolbarButton(COLLAPSE_ICON, "Collapse all Outline nodes", () -> outlineTreeView().collapseAllNodes()));

        searchToggleButton = createSearchToolbarButton();
        toolbar.add(searchToggleButton);
        updateSearchToggleButton();

        searchStatusLabel = new JLabel();
        searchStatusLabel.setToolTipText("Current search match and total matches");

        Color searchStripBackground = resolveQuickSearchStripBackground();

        searchStatusLabel.setOpaque(true);
        searchStatusLabel.setBackground(searchStripBackground);

        JPanel searchControlsPanel = new JPanel();
        searchControlsPanel.setLayout(new BoxLayout(searchControlsPanel, BoxLayout.X_AXIS));
        searchControlsPanel.setOpaque(true);
        searchControlsPanel.setBackground(searchStripBackground);
        searchControlsPanel.add(createSearchNavigationButton(PREVIOUS_MATCH_ICON, "Previous Outline search match (Shift+F3)", () -> searchController.previousMatch()));
        searchControlsPanel.add(createSearchNavigationButton(NEXT_MATCH_ICON, "Next Outline search match (F3)", () -> searchController.nextMatch()));
        searchControlsPanel.add(Box.createHorizontalStrut(4));
        searchControlsPanel.add(searchStatusLabel);

        searchStripPanel = new JPanel(new BorderLayout(6, 0));
        searchStripPanel.setOpaque(true);
        searchStripPanel.setBackground(searchStripBackground);
        Color searchStripSeparator = UIManager.getColor("controlShadow");
        if (searchStripSeparator == null) {
            searchStripSeparator = UIManager.getColor("Separator.foreground");
        }
        searchStripPanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, searchStripSeparator),
                        BorderFactory.createEmptyBorder(0, 4, 0, 4)));
        searchStripPanel.add(searchControlsPanel, BorderLayout.EAST);
        searchStripPanel.setVisible(false);
        topPanel.add(searchStripPanel, BorderLayout.SOUTH);

        searchController = new OutlineSearchController(outlineTreeView());
        searchController.attachStatusLabel(searchStatusLabel);
        searchController.setCloseHandler(this::hideSearchStrip);
        outlineTreeView().installSearchHighlighting(searchController);
        searchController.install();

        quickSearch = QuickSearch.attach(searchStripPanel, BorderLayout.CENTER, new OutlineQuickSearchCallback());
        installTreeQuickSearchRouting();
    }

    private JButton createTreeToolbarButton(String iconResource, String toolTipText, Runnable action) {
        Icon icon = ImageUtilities.loadImageIcon(iconResource, true);
        JButton button = new JButton(icon);
        button.setToolTipText(toolTipText);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JButton createSearchNavigationButton(String iconResource, String toolTipText, Runnable action) {
        JButton button = new JButton(ImageUtilities.loadImageIcon(iconResource, true));
        button.setToolTipText(toolTipText);
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        Dimension buttonSize = new Dimension(20, 20);
        button.setMinimumSize(buttonSize);
        button.setPreferredSize(buttonSize);
        button.setMaximumSize(buttonSize);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JToggleButton createSearchToolbarButton() {
        Icon icon = ImageUtilities.loadImageIcon(SEARCH_ICON, true);
        JToggleButton button = new JToggleButton(icon);
        button.addActionListener(e -> {
            if (button.isSelected()) {
                showSearchStrip(true);
            } else {
                hideSearchStrip();
            }
        });
        return button;
    }

    private void installTreeQuickSearchRouting() {
        outlineTreeView().getTreeComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                forwardTreeEventToQuickSearch(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                forwardTreeEventToQuickSearch(e);
            }
        });
    }

    private void forwardTreeEventToQuickSearch(KeyEvent e) {
        if (quickSearch == null || !shouldForwardTreeEventToQuickSearch(e)) {
            return;
        }
        if (!isSearchStripVisible()) {
            if (e.getID() != KeyEvent.KEY_TYPED || !isSearchStartEvent(e)) {
                return;
            }
            showSearchStrip(false);
        }
        if (applyTreeEventToVisibleSearchField(e)) {
            e.consume();
        }
    }

    private boolean shouldForwardTreeEventToQuickSearch(KeyEvent e) {
        return switch (e.getID()) {
            case KeyEvent.KEY_TYPED -> isSearchStartEvent(e);
            case KeyEvent.KEY_PRESSED -> isSearchEditEvent(e);
            default -> false;
        };
    }

    private boolean isSearchEditEvent(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.VK_BACK_SPACE &&
                        !e.isAltDown() &&
                        !e.isControlDown() &&
                        !e.isMetaDown();
    }

    private boolean isSearchStartEvent(KeyEvent e) {
        char keyChar = e.getKeyChar();
        return keyChar != KeyEvent.CHAR_UNDEFINED &&
                        !Character.isISOControl(keyChar) &&
                        !e.isActionKey() &&
                        !e.isAltDown() &&
                        !e.isControlDown() &&
                        !e.isMetaDown();
    }

    private boolean isSearchStripVisible() {
        return searchStripPanel != null && searchStripPanel.isVisible();
    }

    private void showSearchStrip(boolean focusSearchField) {
        if (searchStripPanel == null || quickSearch == null) {
            return;
        }
        searchStripPanel.setVisible(true);
        quickSearch.setAlwaysShown(true);
        updateSearchToggleButton();
        ensureQuickSearchFieldHooksInstalled();
        if (focusSearchField) {
            SwingUtilities.invokeLater(this::focusQuickSearchField);
        }
    }

    private void hideSearchStrip() {
        if (quickSearchTextField != null && !quickSearchTextField.getText().isEmpty()) {
            quickSearchTextField.setText("");
        } else if (searchController != null) {
            searchController.clearSearch();
        }
        if (quickSearch != null) {
            quickSearch.setAlwaysShown(false);
        }
        if (searchStripPanel != null) {
            searchStripPanel.setVisible(false);
        }
        updateSearchToggleButton();
        outlineTreeView().getTreeComponent().requestFocusInWindow();
    }

    private void updateSearchToggleButton() {
        if (searchToggleButton == null) {
            return;
        }
        boolean visible = isSearchStripVisible();
        searchToggleButton.setSelected(visible);
        searchToggleButton.setToolTipText(visible ? "Hide Outline search" : "Show Outline search");
    }

    private void focusQuickSearchField() {
        ensureQuickSearchFieldHooksInstalled();
        if (quickSearchTextField != null) {
            quickSearchTextField.requestFocusInWindow();
            quickSearchTextField.selectAll();
        }
    }

    private boolean applyTreeEventToVisibleSearchField(KeyEvent e) {
        ensureQuickSearchFieldHooksInstalled();
        if (quickSearchTextField == null) {
            return false;
        }
        if (e.getID() == KeyEvent.KEY_TYPED) {
            quickSearchTextField.setText(quickSearchTextField.getText() + e.getKeyChar());
            int end = quickSearchTextField.getDocument().getLength();
            quickSearchTextField.select(end, end);
            return true;
        }
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            String text = quickSearchTextField.getText();
            if (!text.isEmpty()) {
                quickSearchTextField.setText(text.substring(0, text.length() - 1));
            }
            int end = quickSearchTextField.getDocument().getLength();
            quickSearchTextField.select(end, end);
            return true;
        }
        return false;
    }

    private void ensureQuickSearchFieldHooksInstalled() {
        if (quickSearchTextField == null) {
            quickSearchTextField = findDescendant(searchStripPanel, JTextField.class);
        }
        if (quickSearchTextField == null) {
            return;
        }
        quickSearchTextField.setToolTipText("Search Outline (F3 next, Shift+F3 previous, Esc closes)");
        applyQuickSearchFieldLayoutTweaks();
    }

    private void applyQuickSearchFieldLayoutTweaks() {
        if (quickSearchTextField == null) {
            return;
        }
        Dimension preferredSize = quickSearchTextField.getPreferredSize();
        quickSearchTextField.setMaximumSize(new Dimension(Short.MAX_VALUE, preferredSize.height));
        searchStripPanel.revalidate();
    }

    private Color resolveQuickSearchStripBackground() {
        Color top = UIManager.getColor("NbExplorerView.quicksearch.background.top");
        Color bottom = UIManager.getColor("NbExplorerView.quicksearch.background.bottom");
        if (top != null && bottom != null) {
            return blendColors(top, bottom);
        }
        return new JPanel().getBackground();
    }

    private static Color blendColors(Color a, Color b) {
        return new Color((a.getRed() + b.getRed()) / 2,
                        (a.getGreen() + b.getGreen()) / 2,
                        (a.getBlue() + b.getBlue()) / 2,
                        (a.getAlpha() + b.getAlpha()) / 2);
    }

    private static <T extends Component> T findDescendant(Component component, Class<T> type) {
        if (type.isInstance(component)) {
            return type.cast(component);
        }
        if (!(component instanceof java.awt.Container container)) {
            return null;
        }
        for (Component child : container.getComponents()) {
            T match = findDescendant(child, type);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private final class OutlineQuickSearchCallback implements QuickSearch.Callback {
        @Override
        public void quickSearchUpdate(String searchText) {
            searchController.setSearchText(searchText);
        }

        @Override
        public void showNextSelection(boolean forward) {
            if (forward) {
                searchController.nextMatch();
            } else {
                searchController.previousMatch();
            }
        }

        @Override
        public String findMaxPrefix(String prefix) {
            return searchController.findMaxPrefix(prefix);
        }

        @Override
        public void quickSearchConfirmed() {
            SwingUtilities.invokeLater(() -> outlineTreeView().getTreeComponent().requestFocusInWindow());
        }

        @Override
        public void quickSearchCanceled() {
            searchController.clearSearch();
            SwingUtilities.invokeLater(() -> outlineTreeView().getTreeComponent().requestFocusInWindow());
        }
    }

    public void clear() {
        document.clear();
        SessionManagerImpl mgr = SessionManagerImpl.getInstance();
        for (GraphDocument gd : mgr.getSessions()) {
            mgr.removeElement(gd);
        }
        resetFolderRoot();
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    public GraphDocument getDocument() {
        return document;
    }

    /**
     * Gets default instance. Do not use directly: reserved for *.settings files only, i.e.
     * deserialization routines; otherwise you could get a non-deserialized instance. To obtain the
     * singleton instance, use {@link findInstance}.
     */
    public static synchronized OutlineTopComponent getDefault() {
        if (instance == null) {
            instance = new OutlineTopComponent();
        }
        return instance;
    }

    /**
     * Obtain the OutlineTopComponent instance. Never call {@link #getDefault} directly!
     */
    public static synchronized OutlineTopComponent findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            ErrorManager.getDefault().log(ErrorManager.WARNING, "Cannot find Outline component. It will not be located properly in the window system.");
            return getDefault();
        }
        if (win instanceof OutlineTopComponent) {
            return (OutlineTopComponent) win;
        }
        ErrorManager.getDefault().log(ErrorManager.WARNING, "There seem to be multiple components with the '" + PREFERRED_ID + "' ID. That is a potential source of errors and unexpected behavior.");
        return getDefault();
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    @Override
    public void componentOpened() {
        this.requestActive();
    }

    @Override
    public void componentClosed() {
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public void requestActive() {
        super.requestActive();
        treeView.requestFocus();
    }

    @Override
    public boolean requestFocus(boolean temporary) {
        treeView.requestFocus();
        return super.requestFocus(temporary);
    }

    @Override
    protected boolean requestFocusInWindow(boolean temporary) {
        treeView.requestFocus();
        return super.requestFocusInWindow(temporary);
    }

    @Override
    public void resultChanged(LookupEvent lookupEvent) {
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        // Not called when user starts application for the first time
        super.readExternal(objectInput);
        outlineTreeView().setRootVisible(false);
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
    }

    static final class ResolvableHelper implements Serializable {

        private static final long serialVersionUID = 1L;

        public Object readResolve() {
            return OutlineTopComponent.getDefault();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT
     * modify this code. The content of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        treeView = new OutlineTreeView();

        setLayout(new java.awt.BorderLayout());
        add(treeView, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane treeView;
    // End of variables declaration//GEN-END:variables

    /**
     * This class watches out for InputGraph changes in the current component, as well as for
     * the graph-providing component change.
     */
    private class DiagramOutlineSynchronized implements ChangeListener, DiagramViewerListener {
        private final GraphViewer viewerService;
        private DiagramViewer viewer;
        private DiagramViewerListener viewerL;

        public DiagramOutlineSynchronized(GraphViewer viewerService) {
            this.viewerService = viewerService;

            viewerService.addChangeListener(WeakListeners.change(this, viewerService));
        }

        private void syncViewer(DiagramViewer vwr) {
            if (this.viewer == vwr) {
                return;
            }
            if (this.viewer != null && viewerL != null) {
                this.viewer.removeDiagramViewerListener(viewerL);
            }
            this.viewer = vwr;
            if (vwr != null) {
                viewerL = WeakListeners.create(DiagramViewerListener.class, this, vwr);
                vwr.addDiagramViewerListener(viewerL);
            }
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            InputGraphProvider provider = viewerService.getActiveViewer();
            if (provider == null) {
                return;
            }
            DiagramViewer vwr = provider.getLookup().lookup(DiagramViewer.class);
            syncViewer(vwr);
            if (vwr == null) {
                return;
            }
            updateToGraph(vwr.getGraph());
        }

        private void updateToGraph(InputGraph graph) {
            Deque<FolderElement> path = new LinkedList<>();
            for (FolderElement p = graph; p != null; p = p.getParent()) {
                path.addFirst(p);
            }
            OutlineTopComponent.this.selectPathOrIgnore(graph, path);
        }

        @Override
        public void stateChanged(DiagramViewerEvent ev) {
        }

        @Override
        public void interactionChanged(DiagramViewerEvent ev) {
        }

        @Override
        public void displayChanged(DiagramViewerEvent ev) {
        }

        @Override
        public void diagramChanged(DiagramViewerEvent ev) {
            updateToGraph(ev.getModel().getGraphToView());
        }

        @Override
        public void diagramReady(DiagramViewerEvent ev) {
        }
    }
}
