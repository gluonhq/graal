/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.coordinator.impl;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public final class OutlineSearchController {
    private static final String ACTION_SEARCH_NEXT = "outline-search-next"; // NOI18N
    private static final String ACTION_SEARCH_PREVIOUS = "outline-search-previous"; // NOI18N
    private static final String ACTION_SEARCH_CLEAR = "outline-search-clear"; // NOI18N

    private final JTree tree;

    private JLabel searchStatusLabel;
    private Runnable closeHandler;
    private String searchText = "";

    private List<TreePath> matches = List.of();
    private Set<TreePath> matchedPaths = Set.of();
    private int currentMatchIndex = -1;
    private TreePath[] originalSelectionPaths;
    private boolean searchActive;

    public OutlineSearchController(OutlineTreeView treeView) {
        this.tree = treeView.getTreeComponent();
    }

    public void attachStatusLabel(JLabel statusLabel) {
        this.searchStatusLabel = statusLabel;
        updateStatus();
    }

    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void install() {
        installSearchActions(tree);
        installTreeRefreshListeners();
    }

    public String getSearchText() {
        return searchText;
    }

    public boolean isSearchActive() {
        return !getSearchText().isEmpty();
    }

    public boolean isMatchedPath(TreePath path) {
        return path != null && matchedPaths.contains(path);
    }

    public boolean isCurrentMatch(TreePath path) {
        return path != null && currentMatchIndex >= 0 && currentMatchIndex < matches.size() && path.equals(matches.get(currentMatchIndex));
    }

    private void installSearchActions(JComponent component) {
        InputMap inputMap = component.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = component.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), ACTION_SEARCH_NEXT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK), ACTION_SEARCH_PREVIOUS);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ACTION_SEARCH_CLEAR);

        actionMap.put(ACTION_SEARCH_NEXT, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                nextMatch();
            }
        });
        actionMap.put(ACTION_SEARCH_PREVIOUS, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                previousMatch();
            }
        });
        actionMap.put(ACTION_SEARCH_CLEAR, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!searchText.isEmpty() || closeHandler == null) {
                    clearSearch();
                } else {
                    closeHandler.run();
                }
            }
        });
    }

    private void installTreeRefreshListeners() {
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                refreshMatchesLater();
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                refreshMatchesLater();
            }
        });

        TreeModel model = tree.getModel();
        model.addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {
                refreshMatchesLater();
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                refreshMatchesLater();
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
                refreshMatchesLater();
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                refreshMatchesLater();
            }
        });
    }

    private void refreshMatchesLater() {
        if (!searchActive || searchText.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(this::refreshMatches);
    }

    public void setSearchText(String newSearchText) {
        String query = newSearchText == null ? "" : newSearchText;
        if (query.equals(searchText)) {
            return;
        }
        searchText = query;
        refreshMatches();
    }

    public void clearSearch() {
        setSearchText("");
    }

    private void refreshMatches() {
        String query = searchText;
        if (query.isEmpty()) {
            restoreOriginalSelection();
            matches = List.of();
            matchedPaths = Set.of();
            currentMatchIndex = -1;
            originalSelectionPaths = null;
            searchActive = false;
            updateStatus();
            tree.repaint();
            return;
        }

        captureOriginalSelection();
        matches = collectMatches(query);
        matchedPaths = new LinkedHashSet<>(matches);
        currentMatchIndex = findInitialMatchIndex(matches);
        selectCurrentMatch();
        tree.repaint();
    }

    private void captureOriginalSelection() {
        if (!searchActive) {
            originalSelectionPaths = tree.getSelectionPaths();
            searchActive = true;
        }
    }

    private void restoreOriginalSelection() {
        if (!searchActive) {
            return;
        }
        if (originalSelectionPaths == null || originalSelectionPaths.length == 0) {
            tree.clearSelection();
            return;
        }
        tree.setSelectionPaths(originalSelectionPaths);
        for (TreePath path : originalSelectionPaths) {
            if (path != null && tree.getRowForPath(path) >= 0) {
                tree.scrollPathToVisible(path);
                break;
            }
        }
    }

    private List<TreePath> collectMatches(String query) {
        List<TreePath> found = new ArrayList<>();
        String normalizedQuery = query.toUpperCase(Locale.ROOT);
        for (int row = 0; row < tree.getRowCount(); row++) {
            TreePath path = tree.getPathForRow(row);
            if (path == null) {
                continue;
            }
            if (textForPath(path, row).toUpperCase(Locale.ROOT).contains(normalizedQuery)) {
                found.add(path);
            }
        }
        return found;
    }

    private String textForPath(TreePath path, int row) {
        Object value = path.getLastPathComponent();
        String text = tree.convertValueToText(
                        value,
                        tree.isPathSelected(path),
                        tree.isExpanded(path),
                        tree.getModel().isLeaf(value),
                        row,
                        tree.hasFocus());
        return text == null ? "" : text;
    }

    private int findInitialMatchIndex(List<TreePath> currentMatches) {
        if (currentMatches.isEmpty()) {
            return -1;
        }
        int selectedRow = tree.getLeadSelectionRow();
        if (selectedRow < 0 && originalSelectionPaths != null && originalSelectionPaths.length > 0) {
            selectedRow = tree.getRowForPath(originalSelectionPaths[0]);
        }
        if (selectedRow < 0) {
            return 0;
        }
        for (int i = 0; i < currentMatches.size(); i++) {
            if (tree.getRowForPath(currentMatches.get(i)) >= selectedRow) {
                return i;
            }
        }
        return 0;
    }

    private void selectCurrentMatch() {
        if (matches.isEmpty()) {
            tree.clearSelection();
            updateStatus();
            return;
        }
        currentMatchIndex = Math.floorMod(currentMatchIndex, matches.size());
        TreePath match = matches.get(currentMatchIndex);
        tree.setSelectionPath(match);
        tree.scrollPathToVisible(match);
        updateStatus();
    }

    public void nextMatch() {
        if (matches.isEmpty()) {
            return;
        }
        currentMatchIndex++;
        selectCurrentMatch();
    }

    public void previousMatch() {
        if (matches.isEmpty()) {
            return;
        }
        currentMatchIndex--;
        selectCurrentMatch();
    }

    public String findMaxPrefix(String prefix) {
        if (matches.isEmpty()) {
            return prefix;
        }
        String common = null;
        for (TreePath match : matches) {
            int row = tree.getRowForPath(match);
            if (row < 0) {
                continue;
            }
            String text = textForPath(match, row);
            common = common == null ? text : commonPrefix(common, text);
            if (common.isEmpty()) {
                break;
            }
        }
        return common == null ? prefix : common;
    }

    private void updateStatus() {
        if (searchStatusLabel == null) {
            return;
        }
        if (searchText.isEmpty()) {
            searchStatusLabel.setText("");
        } else if (matches.isEmpty()) {
            searchStatusLabel.setText("0");
        } else {
            searchStatusLabel.setText((currentMatchIndex + 1) + "/" + matches.size());
        }
    }

    private static String commonPrefix(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && Character.toLowerCase(a.charAt(i)) == Character.toLowerCase(b.charAt(i))) {
            i++;
        }
        return a.substring(0, i);
    }
}
