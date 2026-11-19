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

import java.awt.Component;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import org.openide.awt.HtmlRenderer;

public final class OutlineSearchHighlightRenderer implements TreeCellRenderer {
    private static final String MATCH_START = "<b><u>"; // NOI18N
    private static final String MATCH_END = "</u></b>"; // NOI18N

    private final TreeCellRenderer delegate;
    private final OutlineSearchController searchController;

    public OutlineSearchHighlightRenderer(TreeCellRenderer delegate, OutlineSearchController searchController) {
        this.delegate = delegate;
        this.searchController = searchController;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Component component = delegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        TreePath path = row >= 0 ? tree.getPathForRow(row) : null;
        if (path == null || !searchController.isSearchActive() || !searchController.isMatchedPath(path)) {
            return component;
        }
        String query = searchController.getSearchText();
        if (query.isEmpty()) {
            return component;
        }
        String displayText = tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
        if (displayText == null || displayText.isEmpty()) {
            return component;
        }
        String highlightedHtml = toHighlightedHtml(displayText, query);
        if (component instanceof HtmlRenderer.Renderer) {
            HtmlRenderer.Renderer htmlRenderer = (HtmlRenderer.Renderer) component;
            htmlRenderer.setHtml(true);
            htmlRenderer.setText(highlightedHtml);
        } else if (component instanceof JLabel) {
            ((JLabel) component).setText(highlightedHtml);
        }
        return component;
    }

    private static String toHighlightedHtml(String text, String query) {
        String normalizedText = text.toUpperCase(Locale.ROOT);
        String normalizedQuery = query.toUpperCase(Locale.ROOT);
        int queryLength = normalizedQuery.length();
        StringBuilder html = new StringBuilder(text.length() + 32);
        html.append("<html>"); // NOI18N

        int start = 0;
        while (start < text.length()) {
            int matchIndex = normalizedText.indexOf(normalizedQuery, start);
            if (matchIndex < 0) {
                appendEscaped(html, text, start, text.length());
                break;
            }
            appendEscaped(html, text, start, matchIndex);
            html.append(MATCH_START);
            appendEscaped(html, text, matchIndex, matchIndex + queryLength);
            html.append(MATCH_END);
            start = matchIndex + queryLength;
        }
        html.append("</html>"); // NOI18N
        return html.toString();
    }

    private static void appendEscaped(StringBuilder html, String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&':
                    html.append("&amp;");
                    break;
                case '<':
                    html.append("&lt;");
                    break;
                case '>':
                    html.append("&gt;");
                    break;
                default:
                    html.append(ch);
                    break;
            }
        }
    }
}
