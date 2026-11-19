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

import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;

import org.openide.explorer.view.BeanTreeView;

public final class OutlineTreeView extends BeanTreeView {
    public OutlineTreeView() {
        setQuickSearchAllowed(false);
    }

    public JTree getTreeComponent() {
        return tree;
    }

    public void expandAllNodes() {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    public void collapseAllNodes() {
        for (int row = tree.getRowCount() - 1; row >= 0; row--) {
            tree.collapseRow(row);
        }
    }

    public void installSearchHighlighting(OutlineSearchController searchController) {
        TreeCellRenderer renderer = tree.getCellRenderer();
        if (renderer != null) {
            tree.setCellRenderer(new OutlineSearchHighlightRenderer(renderer, searchController));
        }
    }
}
