/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.truffle.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.DeoptBarrier;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Truffle-specific marker that forces a precise frame state for the current bytecode position.
 * <p>
 * During Truffle runtime compilation this node is introduced by intrinsifying
 * {@code CompilerDirectives.preserveFrameStateHere()}.
 * <p>
 * The Truffle API is effectively used as a "checkpoint" to prevent deoptimization from happening
 * earlier; consequently, this node is a {@link DeoptBarrier} to prevent deoptimization from
 * floating past it.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class TrufflePreserveFrameStateNode extends AbstractStateSplit
                implements IterableNodeType, LIRLowerable, Canonicalizable, DeoptBarrier {
    public static final NodeClass<TrufflePreserveFrameStateNode> TYPE = NodeClass.create(TrufflePreserveFrameStateNode.class);

    public TrufflePreserveFrameStateNode() {
        super(TYPE, StampFactory.forVoid());
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // Marker node: emits no machine code.
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (graph() != null && graph().isAfterStage(GraphState.StageFlag.FSA)) {
            return null;
        }
        if (predecessor() instanceof StateSplit predecessorStateSplit) {
            FrameState predecessorState = predecessorStateSplit.stateAfter();
            FrameState currentState = stateAfter();
            if (predecessorState != null && currentState != null && currentState.dataFlowEquals(predecessorState)) {
                return null;
            }
        }
        return this;
    }
}
