/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi.nodes;

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import java.util.List;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.replacements.nodes.ReverseBytesNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConcatNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdInsertNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteWithVectorIndicesNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsic node for the {@code VectorSupport.unaryOp} method. This operation applies a unary
 * arithmetic operation to each element of a vector {@code v}, producing a result vector:
 * <p/>
 *
 * {@code
 *     result = <OP(v.0), OP(v.1), ..., OP(v.n)>
 * }
 *
 * <p/>
 * A mask is currently not supported. The unary operation is identified by an integer opcode which
 * we map to the corresponding Graal operation.
 */
@NodeInfo(nameTemplate = "VectorAPIUnaryOp {p#op/s}")
public class VectorAPIUnaryOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIUnaryOpNode> TYPE = NodeClass.create(VectorAPIUnaryOpNode.class);

    private final SimdStamp vectorStamp;
    private final ArithmeticOpTable.UnaryOp<?> op;

    private static final int VECTOR_OP_REVERSE = vectorOpcode("VECTOR_OP_REVERSE");
    private static final int VECTOR_OP_REVERSE_BYTES = vectorOpcode("VECTOR_OP_REVERSE_BYTES");
    private static final int VECTOR_OP_BIT_COUNT = vectorOpcode("VECTOR_OP_BIT_COUNT");
    private static final int VECTOR_OP_TZ_COUNT = vectorOpcode("VECTOR_OP_TZ_COUNT");
    private static final int VECTOR_OP_LZ_COUNT = vectorOpcode("VECTOR_OP_LZ_COUNT");

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int VALUE_ARG_INDEX = 5;
    private static final int MASK_ARG_INDEX = 6;

    protected VectorAPIUnaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.UnaryOp<?> op, SimdConstant constantValue) {
        this(macroParams, vectorStamp, op, constantValue, null);
    }

    protected VectorAPIUnaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.UnaryOp<?> op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = maybeConstantVectorStamp(vectorStamp, constantValue);
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIUnaryOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ArithmeticOpTable.UnaryOp<?> op = computeOp(null, macroParams.arguments, vectorStamp);
        SimdConstant constantValue = improveConstant(null, vectorStamp, op, macroParams.arguments, providers);
        return new VectorAPIUnaryOpNode(macroParams, vectorStamp, op, constantValue);
    }

    private static ArithmeticOpTable.UnaryOp<?> computeOp(ArithmeticOpTable.UnaryOp<?> oldOp, ValueNode[] arguments, SimdStamp vectorStamp) {
        if (oldOp != null) {
            return oldOp;
        }
        int opcode = oprIdAsConstantInt(arguments, OPRID_ARG_INDEX, vectorStamp);
        if (opcode == -1) {
            return null;
        }
        if (vectorStamp == null) {
            return null;
        }
        if (vectorStamp.isIntegerStamp()) {
            return VectorAPIOperations.lookupIntegerUnaryOp(opcode);
        } else {
            return VectorAPIOperations.lookupFloatingPointUnaryOp(opcode);
        }
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newVectorStamp, ArithmeticOpTable.UnaryOp<?> newOp, ValueNode[] args, CoreProviders providers) {
        if (oldConstant != null) {
            return oldConstant;
        }
        if (newVectorStamp == null) {
            return null;
        }
        if (newOp == null) {
            return null;
        }
        ValueNode mask = args[MASK_ARG_INDEX];
        if (!mask.isNullConstant()) {
            /* TODO GR-62819: masked constant folding */
            return null;
        }
        SimdConstant xConstant = maybeConstantValue(args[VALUE_ARG_INDEX], providers);
        if (xConstant == null) {
            return null;
        }
        ArithmeticOpTable.UnaryOp<?> simdOp = (ArithmeticOpTable.UnaryOp<?>) newVectorStamp.liftScalarOp(newOp);
        return (SimdConstant) simdOp.foldConstant(xConstant);
    }

    private ValueNode getVector() {
        return getArgument(VALUE_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(MASK_ARG_INDEX);
    }

    private static int operationCode(ValueNode[] arguments) {
        ValueNode opId = arguments[OPRID_ARG_INDEX];
        if (!opId.isJavaConstant() || opId.asJavaConstant().getJavaKind() != JavaKind.Int) {
            return -1;
        }
        return opId.asJavaConstant().asInt();
    }

    private static boolean isBitReverse(int operationCode) {
        return operationCode == VECTOR_OP_REVERSE;
    }

    private static boolean isReverseBytes(int operationCode) {
        return operationCode == VECTOR_OP_REVERSE_BYTES;
    }

    private static boolean isBitCount(int operationCode) {
        return operationCode == VECTOR_OP_BIT_COUNT;
    }

    private static boolean isTrailingZerosCount(int operationCode) {
        return operationCode == VECTOR_OP_TZ_COUNT;
    }

    private static boolean isLeadingZerosCount(int operationCode) {
        return operationCode == VECTOR_OP_LZ_COUNT;
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(getVector(), mask());
    }

    @Override
    public SimdStamp vectorStamp() {
        return vectorStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        SimdConstant constantValue = maybeConstantValue(this, tool);
        if (speciesStamp.isExactType() && vectorStamp != null && op != null && constantValue != null) {
            /* Nothing to improve. */
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        ArithmeticOpTable.UnaryOp<?> newOp = computeOp(op, args, newVectorStamp);
        SimdConstant newConstantValue = improveConstant(constantValue, newVectorStamp, newOp, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || (newOp != null && !newOp.equals(op)) || (newConstantValue != null && !newConstantValue.equals(constantValue))) {
            return new VectorAPIUnaryOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newOp, newConstantValue, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        ValueNode[] args = toArgumentArray();
        int opcode = operationCode(args);
        boolean bitReverse = isBitReverse(opcode);
        boolean reverseBytes = isReverseBytes(opcode);
        boolean bitCount = isBitCount(opcode);
        boolean trailingZeros = isTrailingZerosCount(opcode);
        boolean leadingZeros = isLeadingZerosCount(opcode);
        if (!((ObjectStamp) stamp).isExactType() || vectorStamp == null || (op == null && !bitReverse && !reverseBytes && !bitCount && !trailingZeros && !leadingZeros)) {
            return false;
        }
        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp.getVectorLength();
        if (!mask().isNullConstant() && vectorArch.getSupportedVectorBlendLength(elementStamp, vectorLength) != vectorLength) {
            return false;
        }
        if (reverseBytes) {
            return canExpandReverseBytes(vectorArch, elementStamp, vectorLength);
        }
        if (bitReverse) {
            return canExpandBitReverse(vectorArch, elementStamp, vectorLength);
        }
        if (bitCount) {
            return canExpandBitCount(vectorArch, elementStamp, vectorLength);
        }
        if (trailingZeros) {
            return canExpandTrailingZerosCount(vectorArch, elementStamp, vectorLength);
        }
        if (leadingZeros) {
            return canExpandLeadingZerosCount(vectorArch, elementStamp, vectorLength);
        }
        if (isLongAbsOp(elementStamp) && canExpandLongAbsViaLogicalMask(vectorArch, elementStamp, vectorLength)) {
            return true;
        }
        return vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, op) == vectorLength;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            SimdConstant constantValue = maybeConstantValue(this, null);
            return new ConstantNode(constantValue, vectorStamp);
        }
        ValueNode value = expanded.get(getVector());
        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp.getVectorLength();
        ValueNode[] args = toArgumentArray();
        int opcode = operationCode(args);
        ValueNode result;
        if (isReverseBytes(opcode)) {
            result = expandReverseBytes(value, elementStamp);
        } else if (isBitReverse(opcode)) {
            result = expandBitReverse(vectorArch, value, elementStamp, vectorLength);
        } else if (isBitCount(opcode)) {
            result = expandBitCount(vectorArch, value, elementStamp, vectorLength);
        } else if (isTrailingZerosCount(opcode)) {
            result = expandTrailingZerosCount(vectorArch, value, elementStamp, vectorLength);
        } else if (isLeadingZerosCount(opcode)) {
            result = expandLeadingZerosCount(vectorArch, value, elementStamp, vectorLength);
        } else if (isLongAbsOp(elementStamp) &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, op) != vectorLength &&
                        canExpandLongAbsViaLogicalMask(vectorArch, elementStamp, vectorLength)) {
            result = expandLongAbsViaLogicalMask(value);
        } else if (value.stamp(NodeView.DEFAULT).isIntegerStamp()) {
            result = UnaryArithmeticNode.unaryIntegerOp(value, NodeView.DEFAULT, op);
        } else {
            result = UnaryArithmeticNode.unaryFloatOp(value, NodeView.DEFAULT, op);
        }
        if (!mask().isNullConstant()) {
            ValueNode mask = expanded.get(mask());
            result = VectorAPIBlendNode.expandBlendHelper(mask, value, result);
        }
        return result;
    }

    private boolean isLongAbsOp(Stamp elementStamp) {
        return elementStamp instanceof IntegerStamp && PrimitiveStamp.getBits(elementStamp) == Long.SIZE && IntegerStamp.OPS.getAbs().equals(op);
    }

    private static boolean canExpandLongAbsViaLogicalMask(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        return vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, IntegerStamp.OPS.getUShr()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getSub()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getXor()) == vectorLength;
    }

    /**
     * Expands long-lane absolute value with logical shifts and arithmetic:
     * <ul>
     * <li>extract lane sign bits via {@code x >>> 63},</li>
     * <li>build a lane mask of {@code 0} or {@code -1},</li>
     * <li>compute {@code (x ^ mask) - mask}, which equals {@code abs(x)} for each lane.</li>
     * </ul>
     */
    private ValueNode expandLongAbsViaLogicalMask(ValueNode inputVector) {
        int vectorLength = vectorStamp.getVectorLength();
        ValueNode zero = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forLong(0), vectorLength));
        ValueNode signBits = ShiftNode.shiftOp(inputVector, ConstantNode.forInt(Long.SIZE - 1), NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        ValueNode signMask = BinaryArithmeticNode.sub(zero, signBits, NodeView.DEFAULT);
        ValueNode xorSign = BinaryArithmeticNode.xor(inputVector, signMask);
        return BinaryArithmeticNode.sub(xorSign, signMask, NodeView.DEFAULT);
    }

    private static boolean canExpandReverseBytes(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!(elementStamp instanceof IntegerStamp integerStamp)) {
            return false;
        } else if (PrimitiveStamp.getBits(integerStamp) == Byte.SIZE) {
            return true;
        } else {
            int byteVectorLength = vectorLength * (PrimitiveStamp.getBits(integerStamp) / Byte.SIZE);
            IntegerStamp byteStamp = IntegerStamp.create(Byte.SIZE);
            return vectorArch.getSupportedVectorPermuteLength(byteStamp, byteVectorLength) == byteVectorLength;
        }
    }

    private ValueNode expandReverseBytes(ValueNode inputVector, Stamp elementStamp) {
        if (PrimitiveStamp.getBits(elementStamp) == Byte.SIZE) {
            return inputVector;
        } else {
            return graph().addOrUniqueWithInputs(new ReverseBytesNode(inputVector));
        }
    }

    private static boolean canExpandBitReverse(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!(elementStamp instanceof IntegerStamp integerStamp)) {
            return false;
        } else {
            return canBitReverseViaDirectNibbleLookup(vectorArch, integerStamp, vectorLength) ||
                            canBitReverseViaPaddedNibbleLookup(vectorArch, integerStamp, vectorLength);
        }
    }

    private static int vectorSizeInBytes(IntegerStamp elementStamp, int vectorLength) {
        return vectorLength * (PrimitiveStamp.getBits(elementStamp) / Byte.SIZE);
    }

    /**
     * Direct nibble lookup needs at least 16 input bytes because the lookup table has 16 entries
     * and every nibble value in [0, 15] must also be a legal byte-lane index for the permute.
     * Smaller vectors are padded to 128 bits first.
     */
    private static boolean needsPaddedNibbleLookupInput(int vectorSizeInBytes) {
        return vectorSizeInBytes < 16;
    }

    private static boolean canBitReverseViaDirectNibbleLookup(VectorArchitecture vectorArch, IntegerStamp elementStamp, int vectorLength) {
        int byteVectorLength = vectorSizeInBytes(elementStamp, vectorLength);
        if (needsPaddedNibbleLookupInput(byteVectorLength)) {
            return false;
        } else {
            int shortVectorLength = byteVectorLength / (Short.SIZE / Byte.SIZE);
            IntegerStamp byteStamp = IntegerStamp.create(Byte.SIZE);
            IntegerStamp shortStamp = IntegerStamp.create(Short.SIZE);
            return canExpandReverseBytes(vectorArch, elementStamp, vectorLength) &&
                            vectorArch.getSupportedVectorPermuteLength(byteStamp, byteVectorLength) == byteVectorLength &&
                            vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, shortVectorLength, IntegerStamp.OPS.getShl()) == shortVectorLength &&
                            vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, shortVectorLength, IntegerStamp.OPS.getUShr()) == shortVectorLength &&
                            vectorArch.getSupportedVectorArithmeticLength(shortStamp, shortVectorLength, IntegerStamp.OPS.getAnd()) == shortVectorLength &&
                            vectorArch.getSupportedVectorArithmeticLength(shortStamp, shortVectorLength, IntegerStamp.OPS.getOr()) == shortVectorLength;
        }
    }

    private static boolean canBitReverseViaPaddedNibbleLookup(VectorArchitecture vectorArch, IntegerStamp elementStamp, int vectorLength) {
        if (!needsPaddedNibbleLookupInput(vectorSizeInBytes(elementStamp, vectorLength))) {
            return false;
        } else {
            return canBitReverseViaDirectNibbleLookup(vectorArch, elementStamp, vectorLength * 2) &&
                            canPadNibbleLookupInput(vectorArch, elementStamp, vectorLength);
        }
    }

    private static boolean canPadNibbleLookupInput(VectorArchitecture vectorArch, IntegerStamp elementStamp, int vectorLength) {
        int vectorSizeInBytes = vectorSizeInBytes(elementStamp, vectorLength);
        SimdStamp paddedStamp = SimdStamp.broadcast(elementStamp, vectorLength * 2);
        SimdStamp narrowStamp = SimdStamp.broadcast(elementStamp, vectorLength);
        return vectorArch.supportsVectorConcat(vectorSizeInBytes) || vectorArch.supportsVectorInsert(paddedStamp, narrowStamp, 0);
    }

    private ValueNode expandBitReverse(VectorArchitecture vectorArch, ValueNode inputVector, Stamp elementStamp, int vectorLength) {
        IntegerStamp integerStamp = (IntegerStamp) elementStamp;
        if (canBitReverseViaDirectNibbleLookup(vectorArch, integerStamp, vectorLength)) {
            return bitReverseViaDirectNibbleLookup(inputVector, integerStamp, vectorLength);
        } else {
            GraalError.guarantee(canBitReverseViaPaddedNibbleLookup(vectorArch, integerStamp, vectorLength), "unexpected bit reverse shape: %s x %s", integerStamp, vectorLength);
            ValueNode paddedInput = padNibbleLookupInput(vectorArch, inputVector, integerStamp, vectorLength);
            ValueNode paddedResult = bitReverseViaDirectNibbleLookup(paddedInput, integerStamp, vectorLength * 2);
            return graph().addOrUnique(new SimdCutNode(paddedResult, 0, vectorLength));
        }
    }

    /**
     * Implements bit reversal in two stages:
     *
     * <pre>
     * shorts = reinterpret(input as short[])
     * lowNibbles = shorts & 0x0F0F
     * highNibbles = (shorts >>> 4) & 0x0F0F
     * bitReversedBytes = (lut[lowNibbles] << 4) | lut[highNibbles]
     * return reverseBytes(reinterpret(bitReversedBytes as the original element type))
     * </pre>
     *
     * The nibble extraction and merge run in short lanes because the vector backends expose the
     * needed shifts for short lanes, while byte-lane shifts are generally not available.
     */
    private ValueNode bitReverseViaDirectNibbleLookup(ValueNode inputVector, IntegerStamp elementStamp, int vectorLength) {
        int byteVectorLength = vectorSizeInBytes(elementStamp, vectorLength);
        int shortVectorLength = byteVectorLength / (Short.SIZE / Byte.SIZE);
        SimdStamp shortVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Short.SIZE), shortVectorLength);
        SimdStamp byteVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), byteVectorLength);

        ValueNode inputShorts = ReinterpretNode.create(shortVectorStamp, inputVector, NodeView.DEFAULT);
        ValueNode lowNibbleMask = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(Short.SIZE, 0x0F0F), shortVectorLength));
        ValueNode lowNibbles = BinaryArithmeticNode.and(inputShorts, lowNibbleMask);
        ValueNode highNibbles = BinaryArithmeticNode.and(
                        ShiftNode.shiftOp(inputShorts, ConstantNode.forInt(4), NodeView.DEFAULT, IntegerStamp.OPS.getUShr()),
                        lowNibbleMask);

        ValueNode lowNibbleIndices = ReinterpretNode.create(byteVectorStamp, lowNibbles, NodeView.DEFAULT);
        ValueNode highNibbleIndices = ReinterpretNode.create(byteVectorStamp, highNibbles, NodeView.DEFAULT);
        ValueNode bitReverseNibbleLut = bitReverseNibbleLookupTable(byteVectorLength);
        ValueNode reversedLowNibbles = SimdPermuteWithVectorIndicesNode.create(bitReverseNibbleLut, lowNibbleIndices);
        ValueNode reversedHighNibbles = SimdPermuteWithVectorIndicesNode.create(bitReverseNibbleLut, highNibbleIndices);

        ValueNode reversedLowNibblesShort = ReinterpretNode.create(shortVectorStamp, reversedLowNibbles, NodeView.DEFAULT);
        ValueNode reversedHighNibblesShort = ReinterpretNode.create(shortVectorStamp, reversedHighNibbles, NodeView.DEFAULT);
        ValueNode bitReversedBytesShort = BinaryArithmeticNode.or(
                        ShiftNode.shiftOp(reversedLowNibblesShort, ConstantNode.forInt(4), NodeView.DEFAULT, IntegerStamp.OPS.getShl()),
                        reversedHighNibblesShort);
        ValueNode reversedElements = graph().addOrUniqueWithInputs(ReinterpretNode.create(SimdStamp.broadcast(elementStamp, vectorLength), bitReversedBytesShort, NodeView.DEFAULT));
        return expandReverseBytes(reversedElements, elementStamp);
    }

    /**
     * Pads the input vector with zero lanes in the upper half so nibble-lookup expansions can run
     * on a 16-byte vector and then cut the original lanes back out.
     */
    private ValueNode padNibbleLookupInput(VectorArchitecture vectorArch, ValueNode inputVector, IntegerStamp elementStamp, int vectorLength) {
        int elementBits = PrimitiveStamp.getBits(elementStamp);
        int vectorSizeInBytes = vectorSizeInBytes(elementStamp, vectorLength);
        SimdStamp paddedStamp = SimdStamp.broadcast(elementStamp, vectorLength * 2);
        SimdStamp narrowStamp = SimdStamp.broadcast(elementStamp, vectorLength);
        ValueNode zeroHalf = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, 0), vectorLength));
        if (vectorArch.supportsVectorConcat(vectorSizeInBytes)) {
            return graph().addOrUnique(new SimdConcatNode(inputVector, zeroHalf));
        } else {
            GraalError.guarantee(vectorArch.supportsVectorInsert(paddedStamp, narrowStamp, 0), "unsupported padded nibble lookup input on %s", vectorArch.getClass().getSimpleName());
            ValueNode zeroVector = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, 0), vectorLength * 2));
            return graph().addOrUnique(SimdInsertNode.create(zeroVector, inputVector, 0));
        }
    }

    /**
     * Builds a byte lookup-table vector where every element is the 4-bit reverse of its low nibble.
     * Values are repeated across the whole vector so indices in [0, 15] are always valid.
     */
    private ValueNode bitReverseNibbleLookupTable(int byteVectorLength) {
        Constant[] values = new Constant[byteVectorLength];
        for (int i = 0; i < byteVectorLength; i++) {
            int nibble = i & 0x0F;
            int reversed = Integer.reverse(nibble) >>> (Integer.SIZE - 4);
            values[i] = JavaConstant.forByte((byte) reversed);
        }
        SimdStamp byteVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), byteVectorLength);
        return graph().unique(ConstantNode.forConstant(byteVectorStamp, new SimdConstant(values), null));
    }

    private static boolean canExpandBitCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!(elementStamp instanceof IntegerStamp integerStamp)) {
            return false;
        }
        if (PrimitiveStamp.getBits(integerStamp) == Byte.SIZE) {
            return canBitCountWithMasks(vectorArch, integerStamp, vectorLength) ||
                            canBitCountByteViaNibbleLookup(vectorArch, integerStamp, vectorLength);
        } else {
            return canBitCountWithMasks(vectorArch, integerStamp, vectorLength);
        }
    }

    private static boolean canBitCountByteViaNibbleLookup(VectorArchitecture vectorArch, IntegerStamp byteStamp, int vectorLength) {
        if (!needsPaddedNibbleLookupInput(vectorLength)) {
            return canBitCountByteViaDirectNibbleLookup(vectorArch, byteStamp, vectorLength);
        } else {
            return canBitCountByteViaDirectNibbleLookup(vectorArch, byteStamp, vectorLength * 2) &&
                            canPadNibbleLookupInput(vectorArch, byteStamp, vectorLength);
        }
    }

    private static boolean canBitCountByteViaDirectNibbleLookup(VectorArchitecture vectorArch, IntegerStamp byteStamp, int vectorLength) {
        return !needsPaddedNibbleLookupInput(vectorLength) &&
                        canExtractByteNibblesViaShortOps(vectorArch, vectorLength) &&
                        vectorArch.getSupportedVectorPermuteLength(byteStamp, vectorLength) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(byteStamp, vectorLength, IntegerStamp.OPS.getAdd()) == vectorLength;
    }

    private static boolean canExtractByteNibblesViaShortOps(VectorArchitecture vectorArch, int byteVectorLength) {
        int shortVectorLength = byteVectorLength / (Short.SIZE / Byte.SIZE);
        IntegerStamp shortStamp = IntegerStamp.create(Short.SIZE);
        return vectorArch.getSupportedVectorArithmeticLength(shortStamp, shortVectorLength, IntegerStamp.OPS.getAnd()) == shortVectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, shortVectorLength, IntegerStamp.OPS.getUShr()) == shortVectorLength;
    }

    private static boolean canBitCountWithMasks(VectorArchitecture vectorArch, IntegerStamp elementStamp, int vectorLength) {
        return vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, IntegerStamp.OPS.getUShr()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getAnd()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getAdd()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getSub()) == vectorLength;
    }

    private ValueNode expandBitCount(VectorArchitecture vectorArch, ValueNode inputVector, Stamp elementStamp, int vectorLength) {
        IntegerStamp integerStamp = (IntegerStamp) elementStamp;
        int elementBits = PrimitiveStamp.getBits(integerStamp);
        if (elementBits == Byte.SIZE && !canBitCountWithMasks(vectorArch, integerStamp, vectorLength)) {
            GraalError.guarantee(canBitCountByteViaNibbleLookup(vectorArch, integerStamp, vectorLength), "unexpected byte BIT_COUNT shape: %s x %s", integerStamp, vectorLength);
            return bitCountViaNibbleLookup(vectorArch, inputVector, vectorLength);
        } else {
            return bitCountWithMasks(inputVector, elementBits, vectorLength, elementBits);
        }
    }

    /**
     * Counts set bits in byte lanes via nibble lookup: lookup low/high nibble counts and add.
     */
    private ValueNode bitCountViaNibbleLookup(VectorArchitecture vectorArch, ValueNode inputVector, int byteVectorLength) {
        IntegerStamp byteStamp = IntegerStamp.create(Byte.SIZE);
        if (needsPaddedNibbleLookupInput(byteVectorLength)) {
            ValueNode paddedInput = padNibbleLookupInput(vectorArch, inputVector, byteStamp, byteVectorLength);
            ValueNode paddedResult = bitCountViaDirectNibbleLookup(paddedInput, byteVectorLength * 2);
            return graph().addOrUnique(new SimdCutNode(paddedResult, 0, byteVectorLength));
        } else {
            return bitCountViaDirectNibbleLookup(inputVector, byteVectorLength);
        }
    }

    private ValueNode bitCountViaDirectNibbleLookup(ValueNode inputVector, int byteVectorLength) {
        ValueNode[] lowHighNibbleVectors = extractByteNibblesWithShortOps(inputVector, byteVectorLength);
        ValueNode nibbleLut = bitCountNibbleLookupTable(byteVectorLength);
        ValueNode lowCounts = SimdPermuteWithVectorIndicesNode.create(nibbleLut, lowHighNibbleVectors[0]);
        ValueNode highCounts = SimdPermuteWithVectorIndicesNode.create(nibbleLut, lowHighNibbleVectors[1]);
        return graph().addOrUniqueWithInputs(BinaryArithmeticNode.add(lowCounts, highCounts, NodeView.DEFAULT));
    }

    /**
     * Builds a byte lookup-table vector where each element is the population count of its low
     * nibble. Values are repeated across the whole vector so indices in [0, 15] are always valid.
     */
    private ValueNode bitCountNibbleLookupTable(int byteVectorLength) {
        Constant[] values = new Constant[byteVectorLength];
        for (int i = 0; i < byteVectorLength; i++) {
            int nibble = i & 0x0F;
            values[i] = JavaConstant.forByte((byte) Integer.bitCount(nibble));
        }
        SimdStamp byteVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), byteVectorLength);
        return graph().unique(ConstantNode.forConstant(byteVectorStamp, new SimdConstant(values), null));
    }

    /**
     * Extracts low and high nibbles from every byte lane by reinterpreting to shorts and applying
     * lane-local masks and shifts in 16-bit lanes.
     */
    private ValueNode[] extractByteNibblesWithShortOps(ValueNode inputVector, int byteVectorLength) {
        int shortVectorLength = byteVectorLength / (Short.SIZE / Byte.SIZE);
        SimdStamp shortVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Short.SIZE), shortVectorLength);
        SimdStamp byteVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), byteVectorLength);
        ValueNode inputShorts = ReinterpretNode.create(shortVectorStamp, inputVector, NodeView.DEFAULT);
        ValueNode lowNibbleMask = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(Short.SIZE, 0x0F0F), shortVectorLength));
        ValueNode lowNibbles = BinaryArithmeticNode.and(inputShorts, lowNibbleMask);
        ValueNode highNibbles = BinaryArithmeticNode.and(ShiftNode.shiftOp(inputShorts, ConstantNode.forInt(4), NodeView.DEFAULT, IntegerStamp.OPS.getUShr()), lowNibbleMask);
        return new ValueNode[]{
                        ReinterpretNode.create(byteVectorStamp, lowNibbles, NodeView.DEFAULT),
                        ReinterpretNode.create(byteVectorStamp, highNibbles, NodeView.DEFAULT)
        };
    }

    /**
     * Counts the set bits in each lane using the classic SWAR popcount network.
     *
     * For 32-bit lanes, this computes:
     *
     * <pre>
     * x = x - ((x >>> 1) & 0x55555555)
     * x = (x & 0x33333333) + ((x >>> 2) & 0x33333333)
     * x = (x + (x >>> 4)) & 0x0f0f0f0f
     * x = x + (x >>> 8)
     * x = x + (x >>> 16)
     * return x & 0x3f
     * </pre>
     */
    private ValueNode bitCountWithMasks(ValueNode inputVector, int elementBits, int vectorLength, int bitsToCount) {
        ValueNode m1 = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, lowBitMaskPattern(bitsToCount, 1)), vectorLength));
        ValueNode m2 = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, lowBitMaskPattern(bitsToCount, 2)), vectorLength));
        ValueNode m4 = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, lowBitMaskPattern(bitsToCount, 4)), vectorLength));
        ValueNode result = inputVector;
        ValueNode halfBits = BinaryArithmeticNode.and(ShiftNode.shiftOp(result, ConstantNode.forInt(1), NodeView.DEFAULT, IntegerStamp.OPS.getUShr()), m1);
        result = BinaryArithmeticNode.sub(result, halfBits, NodeView.DEFAULT);
        ValueNode lowPairs = BinaryArithmeticNode.and(result, m2);
        ValueNode highPairs = BinaryArithmeticNode.and(ShiftNode.shiftOp(result, ConstantNode.forInt(2), NodeView.DEFAULT, IntegerStamp.OPS.getUShr()), m2);
        result = BinaryArithmeticNode.add(lowPairs, highPairs, NodeView.DEFAULT);
        ValueNode nibbleSums = BinaryArithmeticNode.add(result, ShiftNode.shiftOp(result, ConstantNode.forInt(4), NodeView.DEFAULT, IntegerStamp.OPS.getUShr()), NodeView.DEFAULT);
        result = BinaryArithmeticNode.and(nibbleSums, m4);
        for (int shift = 8; shift < bitsToCount; shift <<= 1) {
            result = BinaryArithmeticNode.add(result, ShiftNode.shiftOp(result, ConstantNode.forInt(shift), NodeView.DEFAULT, IntegerStamp.OPS.getUShr()), NodeView.DEFAULT);
        }
        int resultBitWidth = Integer.SIZE - Integer.numberOfLeadingZeros(bitsToCount);
        long countMask = (1L << resultBitWidth) - 1;
        return BinaryArithmeticNode.and(result, graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, countMask), vectorLength)));
    }

    /**
     * Builds the repeating low-half mask used by the SWAR popcount stages: for each contiguous
     * 2*shift-bit group in the low "bits" bits, set the low "shift" bits and clear the high "shift"
     * bits.
     */
    private static long lowBitMaskPattern(int bits, int shift) {
        long mask = 0;
        int groupSize = shift << 1;
        for (int base = 0; base < bits; base += groupSize) {
            for (int i = 0; i < shift && base + i < bits; i++) {
                mask |= 1L << (base + i);
            }
        }
        return mask;
    }

    private static boolean canExpandTrailingZerosCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!(elementStamp instanceof IntegerStamp integerStamp)) {
            return false;
        }
        if (PrimitiveStamp.getBits(integerStamp) == Byte.SIZE) {
            return canTrailingZerosWithBitCount(vectorArch, integerStamp, vectorLength) ||
                            canZeroCountByteViaNibbleLookup(vectorArch, integerStamp, vectorLength);
        } else {
            return canTrailingZerosWithBitCount(vectorArch, integerStamp, vectorLength);
        }
    }

    private static boolean canExpandLeadingZerosCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!(elementStamp instanceof IntegerStamp integerStamp)) {
            return false;
        }
        if (PrimitiveStamp.getBits(integerStamp) == Byte.SIZE) {
            return canLeadingZerosWithBitCount(vectorArch, integerStamp, vectorLength) ||
                            canZeroCountByteViaNibbleLookup(vectorArch, integerStamp, vectorLength);
        } else {
            return canLeadingZerosWithBitCount(vectorArch, integerStamp, vectorLength);
        }
    }

    private static boolean canTrailingZerosWithBitCount(VectorArchitecture vectorArch, IntegerStamp elementStamp, int vectorLength) {
        return canBitCountWithMasks(vectorArch, elementStamp, vectorLength);
    }

    private static boolean canLeadingZerosWithBitCount(VectorArchitecture vectorArch, IntegerStamp elementStamp, int vectorLength) {
        return canBitCountWithMasks(vectorArch, elementStamp, vectorLength) &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getOr()) == vectorLength;
    }

    private static boolean canZeroCountByteViaNibbleLookup(VectorArchitecture vectorArch, IntegerStamp byteStamp, int vectorLength) {
        if (!needsPaddedNibbleLookupInput(vectorLength)) {
            return canZeroCountByteViaDirectNibbleLookup(vectorArch, byteStamp, vectorLength);
        } else {
            return canZeroCountByteViaDirectNibbleLookup(vectorArch, byteStamp, vectorLength * 2) &&
                            canPadNibbleLookupInput(vectorArch, byteStamp, vectorLength);
        }
    }

    private static boolean canZeroCountByteViaDirectNibbleLookup(VectorArchitecture vectorArch, IntegerStamp byteStamp, int vectorLength) {
        return !needsPaddedNibbleLookupInput(vectorLength) &&
                        canExtractByteNibblesViaShortOps(vectorArch, vectorLength) &&
                        vectorArch.getSupportedVectorPermuteLength(byteStamp, vectorLength) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(byteStamp, vectorLength, IntegerStamp.OPS.getAdd()) == vectorLength &&
                        vectorArch.getSupportedVectorComparisonLength(byteStamp, CanonicalCondition.EQ, vectorLength) == vectorLength &&
                        vectorArch.getSupportedVectorBlendLength(byteStamp, vectorLength) == vectorLength;
    }

    private ValueNode expandTrailingZerosCount(VectorArchitecture vectorArch, ValueNode inputVector, Stamp elementStamp, int vectorLength) {
        IntegerStamp integerStamp = (IntegerStamp) elementStamp;
        int elementBits = PrimitiveStamp.getBits(integerStamp);
        if (elementBits == Byte.SIZE && !canTrailingZerosWithBitCount(vectorArch, integerStamp, vectorLength)) {
            GraalError.guarantee(canZeroCountByteViaNibbleLookup(vectorArch, integerStamp, vectorLength), "unexpected byte TZ_COUNT shape: %s x %s", integerStamp, vectorLength);
            return trailingZerosByteViaNibbleLookup(vectorArch, inputVector, vectorLength);
        } else {
            return trailingZerosWithBitCount(inputVector, elementBits, vectorLength, elementBits);
        }
    }

    /**
     * Counts trailing zeros in byte lanes via nibble lookup:
     * <ul>
     * <li>if low nibble is non-zero, use its 4-bit trailing-zero count,</li>
     * <li>otherwise use {@code 4 + tzcnt(highNibble)}.</li>
     * </ul>
     */
    private ValueNode trailingZerosByteViaNibbleLookup(VectorArchitecture vectorArch, ValueNode inputVector, int byteVectorLength) {
        return zeroCountByteViaNibbleLookup(vectorArch, inputVector, byteVectorLength);
    }

    /**
     * Builds a byte lookup-table vector where each element is the trailing-zero count of its low
     * nibble. Values are repeated across the whole vector so indices in [0, 15] are always valid.
     */
    private ValueNode trailingZeroNibbleLookupTable(int byteVectorLength) {
        Constant[] values = new Constant[byteVectorLength];
        for (int i = 0; i < byteVectorLength; i++) {
            int nibble = i & 0x0F;
            int tzcnt = (nibble == 0) ? 4 : Integer.numberOfTrailingZeros(nibble);
            values[i] = JavaConstant.forByte((byte) tzcnt);
        }
        SimdStamp byteVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), byteVectorLength);
        return graph().unique(ConstantNode.forConstant(byteVectorStamp, new SimdConstant(values), null));
    }

    /**
     * Counts trailing zeros in each lane using {@code ctz(x) = popcount((x & -x) - 1)}. This
     * identity naturally returns the lane width when {@code x == 0}.
     */
    private ValueNode trailingZerosWithBitCount(ValueNode inputVector, int elementBits, int vectorLength, int bitsToCount) {
        ValueNode zero = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, 0), vectorLength));
        ValueNode one = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, 1), vectorLength));
        ValueNode negative = BinaryArithmeticNode.sub(zero, inputVector, NodeView.DEFAULT);
        ValueNode isolatedLowBit = BinaryArithmeticNode.and(inputVector, negative);
        ValueNode lowMask = BinaryArithmeticNode.sub(isolatedLowBit, one, NodeView.DEFAULT);
        return bitCountWithMasks(lowMask, elementBits, vectorLength, bitsToCount);
    }

    private ValueNode expandLeadingZerosCount(VectorArchitecture vectorArch, ValueNode inputVector, Stamp elementStamp, int vectorLength) {
        IntegerStamp integerStamp = (IntegerStamp) elementStamp;
        int elementBits = PrimitiveStamp.getBits(integerStamp);
        if (elementBits == Byte.SIZE && !canLeadingZerosWithBitCount(vectorArch, integerStamp, vectorLength)) {
            GraalError.guarantee(canZeroCountByteViaNibbleLookup(vectorArch, integerStamp, vectorLength), "unexpected byte LZ_COUNT shape: %s x %s", integerStamp, vectorLength);
            return leadingZerosByteViaNibbleLookup(vectorArch, inputVector, vectorLength);
        } else {
            return leadingZerosWithBitCount(inputVector, elementBits, vectorLength, elementBits);
        }
    }

    /**
     * Counts leading zeros in byte lanes via nibble lookup:
     * <ul>
     * <li>if high nibble is non-zero, use its 4-bit leading-zero count,</li>
     * <li>otherwise use {@code 4 + lzcnt(lowNibble)}.</li>
     * </ul>
     */
    private ValueNode leadingZerosByteViaNibbleLookup(VectorArchitecture vectorArch, ValueNode inputVector, int byteVectorLength) {
        return zeroCountByteViaNibbleLookup(vectorArch, inputVector, byteVectorLength);
    }

    private ValueNode zeroCountByteViaNibbleLookup(VectorArchitecture vectorArch, ValueNode inputVector, int byteVectorLength) {
        IntegerStamp byteStamp = IntegerStamp.create(Byte.SIZE);
        if (needsPaddedNibbleLookupInput(byteVectorLength)) {
            ValueNode paddedInput = padNibbleLookupInput(vectorArch, inputVector, byteStamp, byteVectorLength);
            ValueNode paddedResult = graph().addOrUniqueWithInputs(zeroCountByteViaDirectNibbleLookup(vectorArch, paddedInput, byteVectorLength * 2));
            return graph().addOrUnique(new SimdCutNode(paddedResult, 0, byteVectorLength));
        } else {
            return zeroCountByteViaDirectNibbleLookup(vectorArch, inputVector, byteVectorLength);
        }
    }

    /**
     * Counts byte-lane trailing or leading zeros via nibble lookup, depending on this node's
     * opcode. Trailing-zero count uses the low nibble as the primary nibble and otherwise falls
     * back to {@code 4 + tzcnt(highNibble)}; leading-zero count uses the high nibble as the primary
     * nibble and otherwise falls back to {@code 4 + lzcnt(lowNibble)}.
     */
    private ValueNode zeroCountByteViaDirectNibbleLookup(VectorArchitecture vectorArch, ValueNode inputVector, int byteVectorLength) {
        ValueNode[] args = toArgumentArray();
        int opcode = operationCode(args);
        boolean trailingZeros = opcode == VECTOR_OP_TZ_COUNT;
        GraalError.guarantee(trailingZeros || opcode == VECTOR_OP_LZ_COUNT, "unexpected zero-count opcode: %s", opcode);
        ValueNode[] lowHighNibbleVectors = extractByteNibblesWithShortOps(inputVector, byteVectorLength);
        ValueNode lowNibbles = lowHighNibbleVectors[0];
        ValueNode highNibbles = lowHighNibbleVectors[1];
        ValueNode nibbleLut = trailingZeros ? trailingZeroNibbleLookupTable(byteVectorLength) : leadingZeroNibbleLookupTable(byteVectorLength);
        ValueNode lowCounts = SimdPermuteWithVectorIndicesNode.create(nibbleLut, lowNibbles);
        ValueNode highCounts = SimdPermuteWithVectorIndicesNode.create(nibbleLut, highNibbles);
        ValueNode primaryNibbles = trailingZeros ? lowNibbles : highNibbles;
        ValueNode primaryCounts = trailingZeros ? lowCounts : highCounts;
        ValueNode secondaryCounts = trailingZeros ? highCounts : lowCounts;
        ValueNode zero = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(Byte.SIZE, 0), byteVectorLength));
        ValueNode four = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(Byte.SIZE, 4), byteVectorLength));
        ValueNode primaryNibblesAreZero = SimdPrimitiveCompareNode.simdCompare(CanonicalCondition.EQ, primaryNibbles, zero, false, vectorArch);
        ValueNode secondaryCountsPlusFour = BinaryArithmeticNode.add(secondaryCounts, four, NodeView.DEFAULT);
        return VectorAPIBlendNode.expandBlendHelper(primaryNibblesAreZero, primaryCounts, secondaryCountsPlusFour);
    }

    /**
     * Builds a byte lookup-table vector where each element is the leading-zero count of its low
     * nibble. Values are repeated across the whole vector so indices in [0, 15] are always valid.
     */
    private ValueNode leadingZeroNibbleLookupTable(int byteVectorLength) {
        Constant[] values = new Constant[byteVectorLength];
        for (int i = 0; i < byteVectorLength; i++) {
            int nibble = i & 0x0F;
            int lzcnt = (nibble == 0) ? 4 : Integer.numberOfLeadingZeros(nibble) - (Integer.SIZE - 4);
            values[i] = JavaConstant.forByte((byte) lzcnt);
        }
        SimdStamp byteVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), byteVectorLength);
        return graph().unique(ConstantNode.forConstant(byteVectorStamp, new SimdConstant(values), null));
    }

    /**
     * Counts leading zeros in each lane by spreading the highest set bit to the right and then
     * subtracting the resulting popcount from the lane width.
     *
     * For 32-bit lanes, this computes:
     *
     * <pre>
     * x |= x >>> 1
     * x |= x >>> 2
     * x |= x >>> 4
     * x |= x >>> 8
     * x |= x >>> 16
     * return 32 - popcount(x)
     * </pre>
     */
    private ValueNode leadingZerosWithBitCount(ValueNode inputVector, int elementBits, int vectorLength, int bitsToCount) {
        ValueNode spreadBits = inputVector;
        for (int shift = 1; shift < bitsToCount; shift <<= 1) {
            ValueNode shifted = ShiftNode.shiftOp(spreadBits, ConstantNode.forInt(shift), NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
            spreadBits = BinaryArithmeticNode.or(spreadBits, shifted);
        }
        ValueNode setBits = bitCountWithMasks(spreadBits, elementBits, vectorLength, bitsToCount);
        ValueNode laneWidth = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, bitsToCount), vectorLength));
        return BinaryArithmeticNode.sub(laneWidth, setBits, NodeView.DEFAULT);
    }
}
