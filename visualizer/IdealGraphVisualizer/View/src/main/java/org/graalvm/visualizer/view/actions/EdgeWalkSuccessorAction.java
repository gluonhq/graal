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
package org.graalvm.visualizer.view.actions;

import org.graalvm.visualizer.view.EditorTopComponent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@NbBundle.Messages({
        "ACTION_EdgeWalkSuccessor=Navigate along successor edge (Alt+Down)",
})
@ActionID(category = "Diagram", id = EdgeWalkSuccessorAction.ID)
@ActionReference(path = "NodeGraphViewer/Actions", position = 13600)
@ActionRegistration(displayName = "#ACTION_EdgeWalkSuccessor",
        iconBase = "org/graalvm/visualizer/view/images/edgeWalkSuccessor.svg",
        lazy = true)
public final class EdgeWalkSuccessorAction extends CallableSystemAction {
    public static final String ID = "org.graalvm.visualizer.view.actions.EdgeWalkSuccessorAction"; // NOI18N
    public static final KeyStroke KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);

    public EdgeWalkSuccessorAction() {
        putValue(Action.SHORT_DESCRIPTION, Bundle.ACTION_EdgeWalkSuccessor());
        putValue(Action.ACCELERATOR_KEY, KEY_STROKE);
    }

    @Override
    public void performAction() {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            editor.edgeWalkToSuccessor();
        }
    }

    @Override
    public String getName() {
        return Bundle.ACTION_EdgeWalkSuccessor();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/view/images/edgeWalkSuccessor.svg"; // NOI18N
    }
}
