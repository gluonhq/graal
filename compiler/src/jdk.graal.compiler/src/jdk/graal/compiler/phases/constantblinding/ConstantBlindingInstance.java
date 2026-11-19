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

import java.util.function.LongSupplier;
import java.util.function.Predicate;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.nodes.simd.SimdConcatNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Actual work of {@link ConstantBlindingPhase}.
 */
public class ConstantBlindingInstance {

    public static final CounterKey BlindedConstants = DebugContext.counter("ConstantBlinding_BlindedConstants");
    public static final CounterKey BlindedSIMDConstants = DebugContext.counter("ConstantBlinding_BlindedSIMDConstants");
    public static final CounterKey SkippedConstants = DebugContext.counter("ConstantBlinding_SkippedConstants");

    private final LongSupplier keyGen;
    /**
     * Specifies whether constants in frame states should be blinded.
     */
    private final boolean blindConstantsInStates;

    /**
     * Create a new constant blinding phase instance.
     */
    ConstantBlindingInstance(LongSupplier keyGen, boolean blindConstantsInStates) {
        this.keyGen = keyGen;
        this.blindConstantsInStates = blindConstantsInStates;
    }

    /**
     * Determine whether the assembler should always emit 4-byte displacements in address operands.
     * This is a partial mitigation for cases where an attacker can synthesize multi-byte constants
     * out of unblinded single-byte immediates.
     */
    public static boolean shouldForce4ByteDisplacements(OptionValues options) {
        return ConstantBlindingPhase.Options.BlindConstants.getValue(options) && ConstantBlindingPhase.Options.MinimumBlindedConstantSize.getValue(options) > 1;
    }

    @SuppressWarnings("try")
    void run(StructuredGraph graph, LowTierContext context) {
        VectorArchitecture vectorArch = null;
        if (context != null && context.getLowerer() instanceof VectorLoweringProvider vectorLoweringProvider) {
            vectorArch = vectorLoweringProvider.getVectorArchitecture();
        }

        // Remove any pre-blinded constant wrappers. These were needed to make some constants opaque
        // to other phases. We want to blind them now and force their usages to use a blinded
        // constant, because these constants were present before low tier lowering. We must assume
        // they are user-controlled.
        NodeBitMap forceBlindedConstants = graph.createNodeBitMap();
        for (PreBlindedConstantNode preBlindedConstant : graph.getNodes().filter(PreBlindedConstantNode.class)) {
            for (Node usage : preBlindedConstant.usages()) {
                forceBlindedConstants.mark(usage);
            }
            preBlindedConstant.unwrapAndDelete();
        }

        DebugContext debug = graph.getDebug();
        for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class).snapshot()) {
            Constant constantValue = constantNode.getValue();

            ValueNode blindConstantNode = null;
            if (constantValue instanceof PrimitiveConstant) {
                PrimitiveConstant primitiveConstant = (PrimitiveConstant) constantValue;

                // The default value of a primitive constant cannot represent the bytes of
                // (potentially dangerous) machine code
                if (primitiveConstant.isDefaultForKind()) {
                    continue;
                }

                int numBytesUsed = BlindedConstantNode.getNumberOfBytesInValue(primitiveConstant);

                if (numBytesUsed < ConstantBlindingPhase.Options.MinimumBlindedConstantSize.getValue(graph.getOptions())) {
                    SkippedConstants.increment(debug);
                    debug.log(DebugContext.DETAILED_LEVEL, "Skipping constant node %s as it only uses %d bytes", constantNode, numBytesUsed);
                    continue;
                }

                Constant blindingKey = createBlindingKey(primitiveConstant.getJavaKind(), numBytesUsed);
                blindConstantNode = new BlindedConstantNode(constantValue, blindingKey, constantNode.stamp(NodeView.DEFAULT));
                blindConstantNode.setNodeSourcePosition(constantNode.getNodeSourcePosition());
            } else if (constantValue instanceof SimdConstant) {
                SimdConstant simdConstantValue = (SimdConstant) constantValue;

                /*
                 * We only blind primitive constants in a SIMD constant. If all Primitive constants
                 * have their default value, we don't need to blind them (see the primitive constant
                 * case above). If a value is not a primitive constant, we don't blind the SIMD
                 * constant anyway.
                 */
                if (simdConstantValue.isDefaultForKind()) {
                    continue;
                }

                int length = simdConstantValue.getVectorLength();
                boolean onlyPrimitives = true;
                Constant[] keyValues = new Constant[length];
                for (int i = 0; i < length; i++) {
                    Constant value = simdConstantValue.getValue(i);
                    if (!(value instanceof PrimitiveConstant)) {
                        onlyPrimitives = false;
                        break;
                    }

                    PrimitiveConstant primitiveConstant = (PrimitiveConstant) value;
                    if (!primitiveConstant.isDefaultForKind()) {
                        keyValues[i] = createBlindingKey(primitiveConstant.getJavaKind(), BlindedConstantNode.getNumberOfBytesInValue(primitiveConstant));
                    } else {
                        // We don't need a real key if the value doesn't contain any useful data
                        keyValues[i] = JavaConstant.forPrimitive(primitiveConstant.getJavaKind(), 0);
                    }
                }

                // If the SIMD constant contained non-primitive constants, skip it.
                if (!onlyPrimitives) {
                    continue;
                }

                try (DebugCloseable position = constantNode.withNodeSourcePosition()) {
                    SimdConstant simdKey = new SimdConstant(keyValues);
                    SimdStamp simdStamp = (SimdStamp) constantNode.stamp(NodeView.DEFAULT);
                    blindConstantNode = blindSimdConstant(vectorArch, simdConstantValue, simdKey, simdStamp);
                }
            }

            if (blindConstantNode == null) {
                continue;
            }

            NodeBitMap visitedPhiNodes = graph.createNodeBitMap();

            NodeBitMap eligibleUsages = graph.createNodeBitMap();
            NodeBitMap needReprocessing = graph.createNodeBitMap();
            NodeMap<Node> splitNodeMap = graph.createNodeMap();

            do {
                needReprocessing.clearAll();
                collectEligibleUsages(constantNode, forceBlindedConstants, visitedPhiNodes, eligibleUsages, splitNodeMap, needReprocessing);
                visitedPhiNodes.clearAll();
            } while (needReprocessing.isNotEmpty());

            if (constantNode.usages().filter(eligibleUsages::isMarkedAndGrow).isNotEmpty()) {
                blindConstantNode = graph.addOrUniqueWithInputs(blindConstantNode);
                constantNode.replaceAtUsages(blindConstantNode, eligibleUsages::isMarkedAndGrow);

                if (constantNode.hasNoUsages()) {
                    GraphUtil.killWithUnusedFloatingInputs(constantNode, true);
                }
                debug.log(DebugContext.VERBOSE_LEVEL, "Blinded constant node %s as %s", constantNode, blindConstantNode);
                BlindedConstants.increment(debug);
                if (constantValue instanceof SimdConstant) {
                    BlindedSIMDConstants.increment(debug);
                }

            }
        }
    }

    /**
     * Create a legal blinded constant value for the given constant and key. Ideally this is just a
     * {@link BlindedConstantNode}. However, materialization of blinded constants requires an XOR
     * operation, and exotic architectures like AVX1 don't necessarily support XORs of the same
     * width as constants. We might therefore need several operations to stitch together the
     * intended value.
     *
     * @param vectorArch the vector architecture; this may be {@code null} when running in
     *            verification mode, in which case this will return a possibly illegal blinded
     *            constant. This is OK for the purposes of verification because it only cares about
     *            whether any blinded constants were created at all.
     */
    private static ValueNode blindSimdConstant(VectorArchitecture vectorArch, SimdConstant simdConstant, SimdConstant simdKey, SimdStamp simdStamp) {
        // The element stamp for the purposes of the XOR operation.
        IntegerStamp elementStamp = IntegerStamp.create(PrimitiveStamp.getBits(simdStamp.getComponent(0)));
        int vectorLength = simdStamp.getVectorLength();
        int supportedXorLength = vectorLength;
        if (vectorArch != null) {
            /*
             * We can get vectors like 2xbyte for which the vector architecture might claim no XOR
             * instruction exists. But we can use a larger XOR and just ignore the extra elements.
             * So round up to the smallest supported vector size into which this vector fits.
             */
            int coveringLength = vectorArch.getShortestCoveringVectorLength(elementStamp, vectorLength);
            supportedXorLength = vectorArch.getSupportedVectorArithmeticLength(elementStamp, coveringLength, elementStamp.getOps().getXor());
        }
        if (supportedXorLength >= vectorLength) {
            return new BlindedConstantNode(simdConstant, simdKey, simdStamp);
        }
        // Parfait_ALLOW impossible-redundant-condition
        GraalError.guarantee(supportedXorLength < vectorLength, "bad vector length computation");

        // We need to split the constant and the key into two halves, blind them separately, then
        // combine the halves.
        GraalError.guarantee(vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength / 2, elementStamp.getOps().getXor()) == vectorLength / 2, "cannot XOR even halved vectors");
        int halfVectorBytes = (vectorLength / 2) * (elementStamp.getBits() / Byte.SIZE);
        GraalError.guarantee(vectorArch.supportsVectorConcat(halfVectorBytes), "cannot concatenate blinded parts");

        ValueNode blindedLower = blindSimdConstant(vectorArch, simdConstant.lowerHalf(), simdKey.lowerHalf(), simdStamp.lowerHalf());
        ValueNode blindedUpper = blindSimdConstant(vectorArch, simdConstant.upperHalf(), simdKey.upperHalf(), simdStamp.upperHalf());

        SimdConcatNode concat = new SimdConcatNode(blindedLower, blindedUpper);
        GraalError.guarantee(concat.stamp(NodeView.DEFAULT).equals(simdStamp), "bad concat of blinded parts");
        return concat;
    }

    private PrimitiveConstant createBlindingKey(JavaKind kind, int size) {
        long blindingKey = keyGen.getAsLong();

        // Mask the key to match the (actual) size of the blinded constant
        blindingKey = maskToNumberOfBytes(blindingKey, size);

        // Create a key constant with the same kind as the blinded constant
        return JavaConstant.forPrimitive(kind, blindingKey);
    }

    /**
     * Collects the nodes which are eligible usages of a blinded constant. Eligible usage are nodes whose
     * constant argument is eventually materialized to machine code. If phi nodes have eligible usages as
     * well as non-eligible usages, the phi nodes are split. Afterwards, the original phi node contains only
     * non-eligible usages and the new phi node only eligible usages.
     *
     * In rare cases multiple iterations are necessary. For example, consider the following graph where
     * E is an eligible usage and C is the constant to be blinded.
     *
     * // @formatter:off
     *    E
     *    |
     *    v
     *   phi1 ---> C
     *   ^ |
     *   | v
     *   phi2
     * // @formatter:on
     *
     * Phi1 is split after Phi2 was visited, but the new phi node resulting from splitting phi1 has phi2
     * as input. Therefore, phi2 now has an eligible usage, but will only be visited in a second iteration.
     * Whenever {@code usageChanged} contains any nodes, the usages of an already visited phi node were
     * changed and the marked nodes need reprocessing.
     *
     * @param startNode the node from which to start collecting eligible usages
     * @param forceBlindedConstants a bitmap containing nodes that the caller requires to be blinded
     * @param visitedPhiNodes a bitmap containing the phi nodes already visited to avoid infinite loops
     * @param eligibleUsages a bitmap containing all eligible usages with a path to {@code startNode}
     * @param splitNodeMap a map that maps non-eligible phi nodes to their eligible counterparts.
     *                     This map is used to break up cycles in which an eligible phi node would always
     *                     end up with a non-eligible phi node as an input, which in turn would lead to splitting
     *                     the non-eligible phi node again
     * @param usageChanged a bitmap containing all phi nodes who received an eligible usage after being processed.
     *                     {@code collectEligibleUsages} needs to be repeated until this bitmap is empty.
     */
    private void collectEligibleUsages(Node startNode, NodeBitMap forceBlindedConstants, NodeBitMap visitedPhiNodes, NodeBitMap eligibleUsages, NodeMap<Node> splitNodeMap, NodeBitMap usageChanged) {
        for (Node usage : startNode.usages().snapshot()) {
            if (!shouldUseBlindedConstant(usage, forceBlindedConstants)) {
                continue;
            }

            if (eligibleUsages.isMarkedAndGrow(usage)) {
                continue;
            }

            if (usage instanceof PhiNode) {
                // Sometimes phi nodes form a cycle
                if (visitedPhiNodes.isMarkedAndGrow(usage)) {
                    continue;
                }
                visitedPhiNodes.markAndGrow(usage);

                collectEligibleUsages(usage, forceBlindedConstants, visitedPhiNodes, eligibleUsages, splitNodeMap, usageChanged);

                /*
                 * If some nodes eligible for using a blinded constant uses this phi node, but
                 * others don't, split the phi node into a version used by the eligible nodes, and a
                 * version used by the others
                 */
                Predicate<Node> isEligible = eligibleUsages::isMarkedAndGrow;
                if (anyUsageMatches(usage, isEligible)) {
                    /*
                     * We cannot compare the usage count because sometimes a usage occurs twice in
                     * usage.usages(), but is only counted once in the eligibleUsagesOfPhi bitmap
                     */
                    if (anyUsageMatches(usage, isEligible.negate())) {
                        Node newPhi = usage.copyWithInputs();
                        usageChanged.clearAndGrow(usage);
                        splitNodeMap.setAndGrow(usage, newPhi);
                        // Mark the new phi node before replacing the usages to guarantee that self
                        // references are replaced too
                        eligibleUsages.markAndGrow(newPhi);
                        usage.replaceAtUsages(newPhi, eligibleUsages::isMarkedAndGrow);

                        for (Node input : newPhi.inputs().snapshot()) {
                            Node equivalentSplitNode = splitNodeMap.getAndGrow(input);
                            if (equivalentSplitNode != null) {
                                // Replace the input pointing to a non-eligible phi node with an
                                // eligible phi node to cut loops
                                newPhi.replaceFirstInput(input, splitNodeMap.getAndGrow(input));
                            } else if (visitedPhiNodes.isMarkedAndGrow(input) && !eligibleUsages.isMarkedAndGrow(input)) {
                                // The new phi node has an already visited and non-eligible phi node
                                // as input. The input might need to be reprocessed
                                usageChanged.markAndGrow(input);
                            }
                        }
                    } else {
                        eligibleUsages.markAndGrow(usage);
                    }
                }

                continue;
            }

            eligibleUsages.markAndGrow(usage);
        }
    }

    private static boolean anyUsageMatches(Node node, Predicate<Node> predicate) {
        for (Node usage : node.usages()) {
            if (predicate.test(usage)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether the supplied node should use a blinded constant or a regular constant. Only
     * nodes controllable by the user and nodes whose constant arguments are materialized in machine
     * code or other executable sections should have their constant arguments replaced.
     *
     * @param node the node to check
     * @param forceBlindedConstants nodes that should use blinded constant inputs in any case
     * @return {@code true} if the constant input to {@code node} should be blinded, {@code false}
     *         if {@code node} should keep its unblinded constant input
     */
    private boolean shouldUseBlindedConstant(Node node, NodeBitMap forceBlindedConstants) {
        if (forceBlindedConstants.isMarkedAndGrow(node)) {
            return true;
        }
        if (blindConstantsInStates && node instanceof VirtualState) {
            return true;
        }
        // Any addresses with user-controlled constant inputs should have had those constants
        // pre-blinded and then end up in forceBlindedConstants. Other addresses (i.e., without
        // user-controlled constants, such as GC card writes) are benign and need not be blinded.
        if (node instanceof AddressNode) {
            return false;
        }
        // The constants encoding the deoptimization reason and action are not user-controlled.
        if (node instanceof AbstractDeoptimizeNode) {
            return false;
        }
        // Blind everything else.
        return true;
    }

    private static long maskToNumberOfBytes(long value, int numBytes) {
        long mask = 0xFF;
        for (int i = 1; i < numBytes; i++) {
            mask |= (mask << 8);
        }
        return value & mask;
    }
}
