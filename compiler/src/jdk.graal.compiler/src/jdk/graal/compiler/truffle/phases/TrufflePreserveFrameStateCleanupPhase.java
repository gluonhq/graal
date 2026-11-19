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
package jdk.graal.compiler.truffle.phases;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.nodes.TrufflePreserveFrameStateNode;

/**
 * Removes redundant Truffle preserve-frame-state markers introduced by inlining during PE.
 *
 * A marker is redundant if it is dominated by another marker preserving the same bytecode
 * location in the same inlining context.
 */
public final class TrufflePreserveFrameStateCleanupPhase extends BasePhase<TruffleTierContext> {

    @Override
    protected void run(StructuredGraph graph, TruffleTierContext context) {
        graph.checkCancellation();
        if (graph.getNodes(TrufflePreserveFrameStateNode.TYPE).count() <= 1) {
            return;
        }
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeDominators(true).build();
        cfg.visitDominatorTreeDefault(new RedundantPreserveNodeCleanupVisitor(graph));
    }

    private static final class RedundantPreserveNodeCleanupVisitor implements ControlFlowGraph.RecursiveVisitor<Integer> {
        /**
         * Graph containing the preserve markers being cleaned up.
         */
        private final StructuredGraph graph;
        /**
         * Preserved locations contributed by markers on the current dominator path.
         */
        private final ArrayList<NodeSourcePosition> markerLocationsOnCurrentPath;
        /**
         * Membership set used to recognize repeated preserved locations on the current dominator
         * path.
         */
        private final LinkedHashSet<NodeSourcePosition> seenMarkerLocationsOnCurrentPath;

        private RedundantPreserveNodeCleanupVisitor(StructuredGraph graph) {
            this.graph = graph;
            // Most paths seen here contain only a small number of preserve markers.
            this.markerLocationsOnCurrentPath = new ArrayList<>(4);
            this.seenMarkerLocationsOnCurrentPath = new LinkedHashSet<>(4);
        }

        @Override
        public Integer enter(HIRBlock block) {
            int previousDepth = markerLocationsOnCurrentPath.size();
            for (TrufflePreserveFrameStateNode marker : collectMarkers(block)) {
                if (!marker.isAlive()) {
                    continue;
                }
                NodeSourcePosition markerLocation = markerLocation(marker);
                if (seenMarkerLocationsOnCurrentPath.add(markerLocation)) {
                    markerLocationsOnCurrentPath.add(markerLocation);
                } else {
                    graph.removeFixed(marker);
                }
            }
            return previousDepth;
        }

        @Override
        public void exit(HIRBlock block, Integer previousDepth) {
            while (markerLocationsOnCurrentPath.size() > previousDepth) {
                NodeSourcePosition markerLocation = markerLocationsOnCurrentPath.remove(markerLocationsOnCurrentPath.size() - 1);
                seenMarkerLocationsOnCurrentPath.remove(markerLocation);
            }
        }
    }

    private static NodeSourcePosition markerLocation(TrufflePreserveFrameStateNode marker) {
        /*
         * NodeSourcePosition gives a compact structural key for the deoptimization location encoded
         * by the marker's FrameState: method, BCI, and caller chain. The cleanup phase only needs
         * to distinguish those locations, not compare the full FrameState contents.
         */
        FrameState stateAfter = marker.stateAfter();
        return FrameState.toSourcePosition(stateAfter);
    }

    private static ArrayList<TrufflePreserveFrameStateNode> collectMarkers(HIRBlock block) {
        ArrayList<TrufflePreserveFrameStateNode> markers = new ArrayList<>(2);
        for (FixedNode node : block.getNodes()) {
            if (node instanceof TrufflePreserveFrameStateNode marker && marker.isAlive()) {
                markers.add(marker);
            }
        }
        return markers;
    }
}
