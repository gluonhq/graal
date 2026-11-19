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
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

/**
 * Intrinsic node for the {@code VectorSupport.binaryOp} method. This operation applies a binary
 * arithmetic operation to the corresponding elements of two vectors {@code x} and {@code y},
 * producing a result vector:
 * <p/>
 *
 * {@code
 *     result = <OP(x.0, y.0), OP(x.1, y.1), ..., OP(x.n, y.n)>
 * }
 *
 * <p/>
 * If a mask is present, the operation is only applied to the elements selected by the mask. The
 * other elements get their value from the corresponding element of the {@code x} vector. The binary
 * operation is identified by an integer opcode which we map to the corresponding Graal operation.
 */
@NodeInfo(nameTemplate = "VectorAPIBinaryOp {p#op/s}")
public class VectorAPIBinaryOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIBinaryOpNode> TYPE = NodeClass.create(VectorAPIBinaryOpNode.class);

    private final SimdStamp vectorStamp;
    private final ArithmeticOpTable.BinaryOp<?> op;

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int VMCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int X_ARG_INDEX = 5;
    private static final int Y_ARG_INDEX = 6;
    private static final int MASK_ARG_INDEX = 7;

    protected VectorAPIBinaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.BinaryOp<?> op, SimdConstant constantValue) {
        this(macroParams, vectorStamp, op, constantValue, null);
    }

    protected VectorAPIBinaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.BinaryOp<?> op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = maybeConstantVectorStamp(vectorStamp, constantValue);
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIBinaryOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ArithmeticOpTable.BinaryOp<?> op = improveBinaryOp(null, macroParams.arguments, OPRID_ARG_INDEX, vectorStamp, providers);
        SimdConstant constantValue = improveConstant(null, vectorStamp, op, macroParams.arguments, providers);
        return new VectorAPIBinaryOpNode(macroParams, vectorStamp, op, constantValue);
    }

    private ValueNode vectorX() {
        return getArgument(X_ARG_INDEX);
    }

    private ValueNode vectorY() {
        return getArgument(Y_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(MASK_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vectorX(), vectorY(), mask());
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
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VMCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        ArithmeticOpTable.BinaryOp<?> newOp = improveBinaryOp(op, args, OPRID_ARG_INDEX, newVectorStamp, tool);
        SimdConstant newConstantValue = improveConstant(constantValue, newVectorStamp, newOp, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || (newOp != null && !newOp.equals(op)) || (newConstantValue != null && !newConstantValue.equals(constantValue))) {
            return new VectorAPIBinaryOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newOp, newConstantValue, stateAfter());
        }
        return this;
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newVectorStamp, ArithmeticOpTable.BinaryOp<?> newOp, ValueNode[] args, CoreProviders providers) {
        if (oldConstant != null) {
            return oldConstant;
        }
        if (newVectorStamp == null || newOp == null) {
            return null;
        }
        ValueNode mask = args[MASK_ARG_INDEX];
        if (!mask.isNullConstant()) {
            /* TODO GR-62819: masked constant folding */
            return null;
        }
        SimdConstant xConstant = maybeConstantValue(args[X_ARG_INDEX], providers);
        if (xConstant == null) {
            return null;
        }
        SimdConstant yConstant = maybeConstantValue(args[Y_ARG_INDEX], providers);
        if (yConstant == null) {
            return null;
        }
        ArithmeticOpTable.BinaryOp<?> simdOp = (ArithmeticOpTable.BinaryOp<?>) newVectorStamp.liftScalarOp(newOp);
        return (SimdConstant) simdOp.foldConstant(xConstant, yConstant);
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        RotateDirection rotateDirection = computeRotateDirection(toArgumentArray(), OPRID_ARG_INDEX, vectorStamp);
        if (!speciesStamp.isExactType() || vectorStamp == null || (op == null && rotateDirection == null)) {
            return false;
        }
        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp.getVectorLength();
        if (!mask().isNullConstant() && vectorArch.getSupportedVectorBlendLength(elementStamp, vectorLength) != vectorLength) {
            return false;
        }
        if (rotateDirection != null) {
            return canExpandRotateWithVectorCount(vectorArch, elementStamp, vectorLength);
        }
        if (canExpandSignedLongMinMaxWithCompareBlend(vectorArch, elementStamp, vectorLength)) {
            return true;
        }
        if (canExpandByteMulViaShortOps(vectorArch, elementStamp, vectorLength)) {
            return true;
        }
        if (canExpandShortShiftWithVectorCount(vectorArch, elementStamp, vectorLength)) {
            return true;
        }
        if (canExpandLongArithmeticShiftWithVectorCount(vectorArch, elementStamp, vectorLength)) {
            return true;
        }
        return vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, op) == vectorLength;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        ValueNode x = expanded.get(vectorX());
        ValueNode y = expanded.get(vectorY());
        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp.getVectorLength();
        RotateDirection rotateDirection = computeRotateDirection(toArgumentArray(), OPRID_ARG_INDEX, vectorStamp);
        boolean hasDirectArithmeticSupport = rotateDirection == null && vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, op) == vectorLength;
        ValueNode operation;
        if (rotateDirection != null) {
            operation = expandRotateWithVectorCount(vectorStamp, x, y, rotateDirection);
        } else if (!hasDirectArithmeticSupport && canExpandSignedLongMinMaxWithCompareBlend(vectorArch, elementStamp, vectorLength)) {
            operation = expandSignedLongMinMaxWithCompareBlend(x, y, vectorArch);
        } else if (!hasDirectArithmeticSupport && canExpandByteMulViaShortOps(vectorArch, elementStamp, vectorLength)) {
            operation = expandByteMulViaShortOps(x, y);
        } else if (!hasDirectArithmeticSupport && canExpandShortShiftWithVectorCount(vectorArch, elementStamp, vectorLength)) {
            operation = expandShortShiftWithVectorCount(x, y);
        } else if (!hasDirectArithmeticSupport && canExpandLongArithmeticShiftWithVectorCount(vectorArch, elementStamp, vectorLength)) {
            operation = expandLongArithmeticShiftWithVectorCount(x, y);
        } else if (SimdStamp.isOpmask(x.stamp(NodeView.DEFAULT))) {
            if (SimdStamp.OPMASK_OPS.getAnd().equals(op)) {
                operation = BinaryArithmeticNode.and(x, y);
            } else if (SimdStamp.OPMASK_OPS.getOr().equals(op)) {
                operation = BinaryArithmeticNode.or(x, y);
            } else if (SimdStamp.OPMASK_OPS.getXor().equals(op)) {
                operation = BinaryArithmeticNode.xor(x, y);
            } else {
                throw GraalError.shouldNotReachHere("unexpected operation for binary op mask arithmetic");
            }
        } else if (x.stamp(NodeView.DEFAULT).isIntegerStamp()) {
            operation = BinaryArithmeticNode.binaryIntegerOp(x, y, NodeView.DEFAULT, op);
        } else {
            operation = BinaryArithmeticNode.binaryFloatOp(x, y, NodeView.DEFAULT, op);
        }
        if (!mask().isNullConstant()) {
            ValueNode mask = expanded.get(mask());
            operation = VectorAPIBlendNode.expandBlendHelper(mask, x, operation);
        }
        return operation;
    }

    private boolean canExpandSignedLongMinMaxWithCompareBlend(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!isSignedLongMinMaxOp(elementStamp)) {
            return false;
        }
        return vectorArch.getSupportedVectorComparisonLength(elementStamp, CanonicalCondition.LT, vectorLength) == vectorLength &&
                        vectorArch.getSupportedVectorBlendLength(elementStamp, vectorLength) == vectorLength;
    }

    private boolean isSignedLongMinMaxOp(Stamp elementStamp) {
        return elementStamp instanceof IntegerStamp && PrimitiveStamp.getBits(elementStamp) == Long.SIZE &&
                        (IntegerStamp.OPS.getMax().equals(op) || IntegerStamp.OPS.getMin().equals(op));
    }

    private ValueNode expandSignedLongMinMaxWithCompareBlend(ValueNode x, ValueNode y, VectorArchitecture vectorArch) {
        /*
         * On ISAs without native vector long max/min instructions, lower these operations through a
         * compare-plus-blend sequence.
         */
        ValueNode compare = IntegerStamp.OPS.getMax().equals(op)
                        ? SimdPrimitiveCompareNode.simdCompare(CanonicalCondition.LT, y, x, false, vectorArch)
                        : SimdPrimitiveCompareNode.simdCompare(CanonicalCondition.LT, x, y, false, vectorArch);
        return VectorAPIBlendNode.expandBlendHelper(compare, y, x);
    }

    private boolean canExpandByteMulViaShortOps(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!isByteMulOp(elementStamp)) {
            return false;
        }
        int shortVectorLength = vectorLength / (Short.SIZE / Byte.SIZE);
        IntegerStamp shortStamp = IntegerStamp.create(Short.SIZE);
        return vectorArch.getSupportedVectorArithmeticLength(shortStamp, shortVectorLength, IntegerStamp.OPS.getAnd()) == shortVectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(shortStamp, shortVectorLength, IntegerStamp.OPS.getOr()) == shortVectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(shortStamp, shortVectorLength, IntegerStamp.OPS.getMul()) == shortVectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, shortVectorLength, IntegerStamp.OPS.getUShr()) == shortVectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, shortVectorLength, IntegerStamp.OPS.getShl()) == shortVectorLength;
    }

    private boolean isByteMulOp(Stamp elementStamp) {
        return elementStamp instanceof IntegerStamp && PrimitiveStamp.getBits(elementStamp) == Byte.SIZE && IntegerStamp.OPS.getMul().equals(op);
    }

    private boolean canExpandShortShiftWithVectorCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!isShortShiftOp(elementStamp)) {
            return false;
        }
        int intVectorLength = vectorLength / (Integer.SIZE / Short.SIZE);
        if (intVectorLength <= 1) {
            return false;
        }

        IntegerStamp intStamp = IntegerStamp.create(Integer.SIZE);
        GraalError.guarantee(op instanceof ArithmeticOpTable.ShiftOp<?>, "unexpected short shift op: %s", op);
        ArithmeticOpTable.ShiftOp<?> shiftOp = (ArithmeticOpTable.ShiftOp<?>) op;
        GraalError.guarantee(IntegerStamp.OPS.getShl().equals(shiftOp) || IntegerStamp.OPS.getUShr().equals(shiftOp) || IntegerStamp.OPS.getShr().equals(shiftOp),
                        "unexpected short shift op: %s", shiftOp);
        int andLength = vectorArch.getSupportedVectorArithmeticLength(intStamp, intVectorLength, IntegerStamp.OPS.getAnd());
        int orLength = vectorArch.getSupportedVectorArithmeticLength(intStamp, intVectorLength, IntegerStamp.OPS.getOr());
        int ushrScalarLength = vectorArch.getSupportedVectorShiftWithScalarCount(intStamp, intVectorLength, IntegerStamp.OPS.getUShr());
        int shlScalarLength = vectorArch.getSupportedVectorShiftWithScalarCount(intStamp, intVectorLength, IntegerStamp.OPS.getShl());
        int shiftLength = vectorArch.getSupportedVectorArithmeticLength(intStamp, intVectorLength, shiftOp);
        int shrScalarLength = IntegerStamp.OPS.getShr().equals(shiftOp)
                        ? vectorArch.getSupportedVectorShiftWithScalarCount(intStamp, intVectorLength, IntegerStamp.OPS.getShr())
                        : intVectorLength;
        return andLength == intVectorLength &&
                        orLength == intVectorLength &&
                        shiftLength == intVectorLength &&
                        ushrScalarLength == intVectorLength &&
                        shlScalarLength == intVectorLength &&
                        shrScalarLength == intVectorLength;
    }

    private boolean isShortShiftOp(Stamp elementStamp) {
        return elementStamp instanceof IntegerStamp && PrimitiveStamp.getBits(elementStamp) == Short.SIZE &&
                        (IntegerStamp.OPS.getShl().equals(op) || IntegerStamp.OPS.getShr().equals(op) || IntegerStamp.OPS.getUShr().equals(op));
    }

    /**
     * Expands short vector-count shifts through int operations.
     *
     * Short elements are packed two-per-int lane, so the expansion:
     * <ul>
     * <li>reinterprets values and counts as int vectors,</li>
     * <li>extracts low/high short halves and their per-half counts,</li>
     * <li>applies the requested shift kind (shl/ushr/shr) to each half, and</li>
     * <li>packs the two shifted short halves back and reinterprets to the original stamp.</li>
     * </ul>
     */
    private ValueNode expandShortShiftWithVectorCount(ValueNode valueVector, ValueNode vectorCounts) {
        int intVectorLength = vectorStamp.getVectorLength() / (Integer.SIZE / Short.SIZE);
        SimdStamp intVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Integer.SIZE), intVectorLength);
        GraalError.guarantee(op instanceof ArithmeticOpTable.ShiftOp<?>, "unexpected short shift op: %s", op);
        ArithmeticOpTable.ShiftOp<?> shiftOp = (ArithmeticOpTable.ShiftOp<?>) op;
        GraalError.guarantee(IntegerStamp.OPS.getShl().equals(shiftOp) || IntegerStamp.OPS.getUShr().equals(shiftOp) || IntegerStamp.OPS.getShr().equals(shiftOp),
                        "unexpected short shift op: %s", shiftOp);

        ValueNode packedValues = ReinterpretNode.create(intVectorStamp, valueVector, NodeView.DEFAULT);
        ValueNode packedCounts = ReinterpretNode.create(intVectorStamp, vectorCounts, NodeView.DEFAULT);

        ValueNode lowHalfMask = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forInt(0x0000FFFF), intVectorLength));
        ValueNode countMask = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forInt(Short.SIZE - 1), intVectorLength));
        ValueNode halfShift = ConstantNode.forInt(Short.SIZE);

        ValueNode lowCounts = BinaryArithmeticNode.and(packedCounts, countMask);
        ValueNode highCounts = BinaryArithmeticNode.and(ShiftNode.shiftOp(packedCounts, halfShift, NodeView.DEFAULT, IntegerStamp.OPS.getUShr()), countMask);

        ValueNode lowValues;
        ValueNode highValues;
        if (IntegerStamp.OPS.getShr().equals(op)) {
            ValueNode lowValuesShiftedUp = ShiftNode.shiftOp(packedValues, halfShift, NodeView.DEFAULT, IntegerStamp.OPS.getShl());
            lowValues = ShiftNode.shiftOp(lowValuesShiftedUp, halfShift, NodeView.DEFAULT, IntegerStamp.OPS.getShr());
            highValues = ShiftNode.shiftOp(packedValues, halfShift, NodeView.DEFAULT, IntegerStamp.OPS.getShr());
        } else {
            lowValues = BinaryArithmeticNode.and(packedValues, lowHalfMask);
            highValues = ShiftNode.shiftOp(packedValues, halfShift, NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        }

        ValueNode lowShifted = ShiftNode.shiftOp(lowValues, lowCounts, NodeView.DEFAULT, shiftOp);
        ValueNode highShifted = ShiftNode.shiftOp(highValues, highCounts, NodeView.DEFAULT, shiftOp);
        ValueNode packedResult = BinaryArithmeticNode.or(
                        BinaryArithmeticNode.and(lowShifted, lowHalfMask),
                        ShiftNode.shiftOp(BinaryArithmeticNode.and(highShifted, lowHalfMask), halfShift, NodeView.DEFAULT, IntegerStamp.OPS.getShl()));
        return ReinterpretNode.create(vectorStamp, packedResult, NodeView.DEFAULT);
    }

    private boolean canExpandLongArithmeticShiftWithVectorCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!isLongArithmeticShiftOp(elementStamp)) {
            return false;
        }
        return vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getAnd()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getShl()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getUShr()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getSub()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getOr()) == vectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, IntegerStamp.OPS.getUShr()) == vectorLength;
    }

    private boolean isLongArithmeticShiftOp(Stamp elementStamp) {
        return elementStamp instanceof IntegerStamp && PrimitiveStamp.getBits(elementStamp) == Long.SIZE && IntegerStamp.OPS.getShr().equals(op);
    }

    /**
     * Expands long vector-count arithmetic right shift via logical shift plus synthesized sign
     * fill.
     *
     * For each lane:
     * <ul>
     * <li>count is masked with {@code 63},</li>
     * <li>the base value is shifted logically right,</li>
     * <li>sign-fill bits are built from the lane sign and {@code (64 - count) & 63}, and</li>
     * <li>a non-zero-count mask suppresses sign-fill when effective count is zero.</li>
     * </ul>
     */
    private ValueNode expandLongArithmeticShiftWithVectorCount(ValueNode valueVector, ValueNode vectorCounts) {
        int vectorLength = vectorStamp.getVectorLength();
        ValueNode shiftMask = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forLong(Long.SIZE - 1), vectorLength));
        ValueNode laneBits = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forLong(Long.SIZE), vectorLength));
        ValueNode zero = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forLong(0), vectorLength));
        ValueNode signBitShift = ConstantNode.forInt(Long.SIZE - 1);

        ValueNode maskedCounts = BinaryArithmeticNode.and(vectorCounts, shiftMask);
        ValueNode logicalShift = ShiftNode.shiftOp(valueVector, maskedCounts, NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        ValueNode signBits = ShiftNode.shiftOp(valueVector, signBitShift, NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        ValueNode signMask = BinaryArithmeticNode.sub(zero, signBits, NodeView.DEFAULT);
        ValueNode inverseCounts = BinaryArithmeticNode.and(BinaryArithmeticNode.sub(laneBits, maskedCounts, NodeView.DEFAULT), shiftMask);
        ValueNode nonZeroBits = ShiftNode.shiftOp(
                        BinaryArithmeticNode.or(maskedCounts, BinaryArithmeticNode.sub(zero, maskedCounts, NodeView.DEFAULT)),
                        signBitShift,
                        NodeView.DEFAULT,
                        IntegerStamp.OPS.getUShr());
        ValueNode nonZeroMask = BinaryArithmeticNode.sub(zero, nonZeroBits, NodeView.DEFAULT);
        ValueNode highFill = BinaryArithmeticNode.and(ShiftNode.shiftOp(signMask, inverseCounts, NodeView.DEFAULT, IntegerStamp.OPS.getShl()), nonZeroMask);
        return BinaryArithmeticNode.or(logicalShift, highFill);
    }

    private ValueNode expandByteMulViaShortOps(ValueNode x, ValueNode y) {
        int shortVectorLength = vectorStamp.getVectorLength() / (Short.SIZE / Byte.SIZE);
        SimdStamp shortVectorStamp = SimdStamp.broadcast(IntegerStamp.create(Short.SIZE), shortVectorLength);
        ValueNode xShort = ReinterpretNode.create(shortVectorStamp, x, NodeView.DEFAULT);
        ValueNode yShort = ReinterpretNode.create(shortVectorStamp, y, NodeView.DEFAULT);
        ValueNode lowByteMask = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(Short.SIZE, 0x00FF), shortVectorLength));
        ValueNode shiftAmount = ConstantNode.forInt(Byte.SIZE);

        /*
         * AMD64 has no native packed byte multiply. Split each 16-bit lane into low and high bytes,
         * multiply those byte parts as shorts, and pack the low 8 bits of each product back into a
         * byte vector.
         */
        ValueNode lowProduct = BinaryArithmeticNode.binaryIntegerOp(
                        BinaryArithmeticNode.and(xShort, lowByteMask),
                        BinaryArithmeticNode.and(yShort, lowByteMask),
                        NodeView.DEFAULT,
                        IntegerStamp.OPS.getMul());
        ValueNode highProduct = BinaryArithmeticNode.binaryIntegerOp(
                        ShiftNode.shiftOp(xShort, shiftAmount, NodeView.DEFAULT, IntegerStamp.OPS.getUShr()),
                        ShiftNode.shiftOp(yShort, shiftAmount, NodeView.DEFAULT, IntegerStamp.OPS.getUShr()),
                        NodeView.DEFAULT,
                        IntegerStamp.OPS.getMul());
        ValueNode packedShortResult = BinaryArithmeticNode.or(
                        BinaryArithmeticNode.and(lowProduct, lowByteMask),
                        ShiftNode.shiftOp(highProduct, shiftAmount, NodeView.DEFAULT, IntegerStamp.OPS.getShl()));
        return ReinterpretNode.create(vectorStamp, packedShortResult, NodeView.DEFAULT);
    }

    private static boolean canExpandRotateWithVectorCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!(elementStamp instanceof IntegerStamp) || PrimitiveStamp.getBits(elementStamp) == Byte.SIZE) {
            return false;
        }
        boolean supportsViaShiftOr = vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getShl()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getUShr()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getSub()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getOr()) == vectorLength;
        return supportsViaShiftOr || vectorArch.getSupportedVectorRotateLength(elementStamp, vectorLength) == vectorLength;
    }

    private ValueNode expandRotateWithVectorCount(SimdStamp inputStamp, ValueNode inputVector, ValueNode vectorCount, RotateDirection rotateDirection) {
        int elementBits = PrimitiveStamp.getBits(inputStamp.getComponent(0));
        int vectorLength = inputStamp.getVectorLength();
        ValueNode vectorBits = graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forIntegerBits(elementBits, elementBits), vectorLength));
        ValueNode inverseShift = BinaryArithmeticNode.sub(vectorBits, vectorCount, NodeView.DEFAULT);

        /*
         * We always build rotates as shifts plus or, since there are no rotate nodes in the IR.
         * Backends can still match this form and select native rotate instructions when available.
         */
        ValueNode leftShiftAmount = rotateDirection == RotateDirection.LEFT ? vectorCount : inverseShift;
        ValueNode rightShiftAmount = rotateDirection == RotateDirection.LEFT ? inverseShift : vectorCount;
        ValueNode leftShift = ShiftNode.shiftOp(inputVector, leftShiftAmount, NodeView.DEFAULT, IntegerStamp.OPS.getShl());
        ValueNode rightShift = ShiftNode.shiftOp(inputVector, rightShiftAmount, NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        return BinaryArithmeticNode.or(leftShift, rightShift);
    }
}
