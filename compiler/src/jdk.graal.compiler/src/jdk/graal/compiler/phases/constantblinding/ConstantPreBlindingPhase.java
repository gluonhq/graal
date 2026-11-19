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
package jdk.graal.compiler.phases.constantblinding;

import java.util.Optional;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * This phase is a pre-processing step for {@link ConstantBlindingPhase}. Constant blinding runs
 * after the final canonicalization, therefore also after address lowering. This means that it
 * cannot blind constant displacements folded into {@link AddressNode}s as unboxed {@code long}
 * fields. Therefore this phase picks up constants flowing into {@link OffsetAddressNode}s before
 * address lowering and "pre-blinds" them at that point.
 */
public class ConstantPreBlindingPhase extends BasePhase<LowTierContext> {

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        /*
         * Keep this in lockstep with ConstantBlindingPhase: cached runtime suites may contain the
         * phase even when blinding is disabled for this graph.
         */
        return ConstantBlindingPhase.Options.BlindConstants.getValue(graph.getOptions());
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunBefore(this, StageFlag.LOW_TIER_LOWERING, graphState);
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        int minimumBlindedConstantSize = ConstantBlindingPhase.Options.MinimumBlindedConstantSize.getValue(graph.getOptions());
        for (Node node : graph.getNodes()) {
            if (node instanceof OffsetAddressNode) {
                OffsetAddressNode offsetAddress = (OffsetAddressNode) node;
                ValueNode preBlindedBase = maybePreBlind(offsetAddress.getBase(), minimumBlindedConstantSize);
                if (preBlindedBase != offsetAddress.getBase()) {
                    offsetAddress.setBase(preBlindedBase);
                }
                ValueNode preBlindedOffset = maybePreBlind(offsetAddress.getOffset(), minimumBlindedConstantSize);
                if (preBlindedOffset != offsetAddress.getOffset()) {
                    offsetAddress.setOffset(preBlindedOffset);
                }
            }
        }
    }

    /**
     * Wraps the given {@code node} in a {@link PreBlindedConstantNode} if it is a primitive
     * constant node whose size in bytes is at least {@code minimumBlindedConstantSize}. The
     * wrapping node is added to the graph. Otherwise, returns the original node unchanged.
     */
    @SuppressWarnings("try")
    ValueNode maybePreBlind(ValueNode node, int minimumBlindedConstantSize) {
        if (!(node.isJavaConstant() && node.asJavaConstant() instanceof PrimitiveConstant)) {
            return node;
        }

        PrimitiveConstant primitiveConstant = (PrimitiveConstant) node.asJavaConstant();
        int numBytesUsed = BlindedConstantNode.getNumberOfBytesInValue(primitiveConstant);
        if (numBytesUsed < minimumBlindedConstantSize) {
            return node;
        }

        try (DebugCloseable position = node.withNodeSourcePosition()) {
            return node.graph().addOrUnique(new PreBlindedConstantNode((ConstantNode) node));
        }
    }
}
