/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * Try to replace box operations with dominating box operations.
 *
 * Consider the following code snippet:
 *
 * <pre>
 * boxedVal1 = box(primitiveVal)
 * ...
 * boxedVal2 = box(primitiveVal)
 * </pre>
 *
 * which can be rewritten to (if the assignment to boxedVal1 dominates the assignment to boxedVal2)
 *
 * <pre>
 * boxedVal1 = box(primitiveVal)
 * ...
 * boxedVal2 = boxedVal1;
 * </pre>
 */
public class BoxNodeOptimizationPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public BoxNodeOptimizationPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        ControlFlowGraph cfg = null;
        Graph.Mark before = graph.getMark();
        NodeBitMap boxesToKill = null;
        EconomicMap<HIRBlock, EconomicMap<BoxNode, Integer>> boxOrderByBlock = EconomicMap.create(Equivalence.IDENTITY);
        for (BoxNode box : graph.getNodes(BoxNode.TYPE)) {
            if (!isOptimizableBox(box) || graph.isNew(before, box)) {
                continue;
            }
            final ValueNode primitiveVal = box.getValue();
            assert primitiveVal != null : "Box " + box + " has no value";
            // try to optimize with dominating box of the same value
            // Note: deferred box deletion keeps primitiveVal usages stable during this scan.
            for (Node usage : primitiveVal.usages()) {
                if (usage == box || !(usage instanceof BoxNode boxUsageOnBoxedVal) || graph.isNew(before, boxUsageOnBoxedVal)) {
                    continue;
                }
                if (!isReplacementCandidate(boxUsageOnBoxedVal, box)) {
                    continue;
                }
                if (cfg == null) {
                    cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeDominators(true).build();
                }
                if (isDominatingBox(cfg.blockFor(boxUsageOnBoxedVal), cfg.blockFor(box), boxUsageOnBoxedVal, box, boxOrderByBlock)) {
                    boxesToKill = replaceWithDominatingBox(graph, box, boxUsageOnBoxedVal, boxesToKill);
                    break;
                }
            }
        }

        if (boxesToKill != null) {
            GraphUtil.killAllWithUnusedFloatingInputs(boxesToKill, false);
        }
    }

    /**
     * Returns whether {@code box} is still in the graph and eligible for replacement.
     */
    private static boolean isOptimizableBox(BoxNode box) {
        return box.isAlive() && !isUnlinked(box) && !box.hasIdentity();
    }

    /**
     * Returns whether {@code candidate} can be considered as a replacement for {@code box} before
     * dominance is checked.
     */
    private static boolean isReplacementCandidate(BoxNode candidate, BoxNode box) {
        return candidate.isAlive() && !isUnlinked(candidate) && candidate.getBoxingKind() == box.getBoxingKind();
    }

    /**
     * Replaces {@code box} with {@code dominatingBox} at all usages, unlinks it and marks it for
     * deletion.
     */
    private NodeBitMap replaceWithDominatingBox(StructuredGraph graph, BoxNode box, BoxNode dominatingBox, NodeBitMap boxesToKillOrNull) {
        box.replaceAtUsages(dominatingBox);
        graph.getOptimizationLog().report(getClass(), "BoxUsageReplacement", box);
        GraphUtil.unlinkFixedNode(box);
        NodeBitMap boxesToKill = boxesToKillOrNull != null ? boxesToKillOrNull : graph.createNodeBitMap();
        boxesToKill.mark(box);
        return boxesToKill;
    }

    private static boolean isUnlinked(BoxNode box) {
        return box.predecessor() == null;
    }

    /**
     * Returns whether {@code dominatorCandidate} dominates {@code box}, including fixed-node order
     * when both boxes are in the same block.
     */
    private static boolean isDominatingBox(HIRBlock dominatorCandidateBlock, HIRBlock boxBlock, BoxNode dominatorCandidate, BoxNode box,
                    EconomicMap<HIRBlock, EconomicMap<BoxNode, Integer>> boxOrderByBlock) {
        if (dominatorCandidateBlock.getLoop() != null && boxBlock.getLoop() != dominatorCandidateBlock.getLoop()) {
            // avoid proxy creation for now
            return false;
        }
        if (!dominatorCandidateBlock.dominates(boxBlock)) {
            return false;
        }
        if (dominatorCandidateBlock == boxBlock) {
            /*
             * Both boxes are in the same block, i.e. the usage block does not strictly dominate the
             * box block, so the relative fixed-node order decides dominance. If this check fails,
             * the later box can still be optimized when it is visited by the outer loop.
             */
            return dominatesWithinBlock(dominatorCandidateBlock, dominatorCandidate, box, boxOrderByBlock);
        }
        return true;
    }

    /**
     * Returns whether {@code dominatorCandidate} appears before {@code box} in {@code block}.
     */
    private static boolean dominatesWithinBlock(HIRBlock block, BoxNode dominatorCandidate, BoxNode box,
                    EconomicMap<HIRBlock, EconomicMap<BoxNode, Integer>> boxOrderByBlock) {
        EconomicMap<BoxNode, Integer> boxOrder = boxOrderByBlock.get(block);
        if (boxOrder == null) {
            boxOrder = computeBoxOrder(block);
            boxOrderByBlock.put(block, boxOrder);
        }
        assert boxOrder.containsKey(dominatorCandidate) && boxOrder.containsKey(box) : Assertions.errorMessage("block must contain both boxes", dominatorCandidate, box, block);
        return boxOrder.get(dominatorCandidate) < boxOrder.get(box);
    }

    /**
     * Computes the fixed-node order of all {@link BoxNode}s in {@code block}.
     */
    private static EconomicMap<BoxNode, Integer> computeBoxOrder(HIRBlock block) {
        EconomicMap<BoxNode, Integer> boxOrder = EconomicMap.create(Equivalence.IDENTITY);
        int order = 0;
        for (FixedNode fixed : block.getNodes()) {
            if (fixed instanceof BoxNode blockBox) {
                boxOrder.put(blockBox, order++);
            }
        }
        return boxOrder;
    }
}
