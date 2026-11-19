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

import java.util.Collections;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
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
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConcatNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Intrinsic node for the {@code VectorSupport.broadcastInt} method. This badly named operation is
 * <em>not</em> a broadcast (see {@link VectorAPIFromBitsCoercedNode} for that). This operation
 * applies a bitshift operation to each element of a vector {@code x} with a scalar shift amount
 * {@code s}, producing a result vector:
 * <p/>
 *
 * {@code
 *     result = <OP(x.0, s), OP(x.1, s), ..., OP(x.n, s)>
 * }
 *
 * <p/>
 * A mask is currently not supported. The shift operation is identified by an integer opcode which
 * we map to the corresponding Graal operation.
 */
@NodeInfo(nameTemplate = "VectorAPIBroadcastInt {p#op/s}")
public class VectorAPIBroadcastIntNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIBroadcastIntNode> TYPE = NodeClass.create(VectorAPIBroadcastIntNode.class);

    private final SimdStamp vectorStamp;
    private final ArithmeticOpTable.ShiftOp<?> op;

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int VALUE_ARG_INDEX = 5;
    private static final int SHIFT_ARG_INDEX = 6;
    private static final int MASK_ARG_INDEX = 7;

    protected VectorAPIBroadcastIntNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.ShiftOp<?> op, SimdConstant constantValue) {
        this(macroParams, vectorStamp, op, constantValue, null);
    }

    protected VectorAPIBroadcastIntNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.ShiftOp<?> op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = maybeConstantVectorStamp(vectorStamp, constantValue);
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIBroadcastIntNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ArithmeticOpTable.ShiftOp<?> op = improveShiftOp(null, macroParams.arguments, vectorStamp);
        SimdConstant constantValue = improveConstant(null, vectorStamp, op, macroParams.arguments, providers);
        return new VectorAPIBroadcastIntNode(macroParams, vectorStamp, op, constantValue);
    }

    private static ArithmeticOpTable.ShiftOp<?> improveShiftOp(ArithmeticOpTable.ShiftOp<?> oldOp, ValueNode[] arguments, SimdStamp vectorStamp) {
        if (oldOp != null) {
            return oldOp;
        }
        int opcode = oprIdAsConstantInt(arguments, OPRID_ARG_INDEX, vectorStamp);
        if (opcode == -1) {
            return null;
        }
        if (vectorStamp.isIntegerStamp()) {
            return VectorAPIOperations.lookupIntegerShiftOp(opcode);
        }
        return null;
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
        SimdConstant valueConstant = maybeConstantValue(args[VALUE_ARG_INDEX], providers);
        if (valueConstant == null) {
            return null;
        }
        JavaConstant shiftConstant = args[SHIFT_ARG_INDEX].asJavaConstant();
        if (shiftConstant == null) {
            return null;
        }
        ArithmeticOpTable.ShiftOp<?> simdOp = (ArithmeticOpTable.ShiftOp<?>) newVectorStamp.liftScalarOp(newOp);
        return (SimdConstant) simdOp.foldConstant(valueConstant, shiftConstant);
    }

    private ValueNode getVector() {
        return getArgument(VALUE_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return Collections.singletonList(getVector());
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
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        ArithmeticOpTable.ShiftOp<?> newOp = improveShiftOp(op, args, newVectorStamp);
        SimdConstant newConstantValue = improveConstant(constantValue, vectorStamp, op, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || (newOp != null && !newOp.equals(op)) || (newConstantValue != null && !newConstantValue.equals(constantValue))) {
            return new VectorAPIBroadcastIntNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newOp, newConstantValue, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        RotateDirection rotateDirection = computeRotateDirection(toArgumentArray(), OPRID_ARG_INDEX, vectorStamp);
        if (!((ObjectStamp) stamp).isExactType() || vectorStamp == null || (op == null && rotateDirection == null)) {
            return false;
        }
        if (!getArgument(MASK_ARG_INDEX).isNullConstant()) {
            return false;
        }

        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp().getVectorLength();
        if (rotateDirection != null) {
            return canExpandRotateWithScalarCount(vectorArch, elementStamp, vectorLength);
        }
        boolean supportedDirectly = vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, op) == vectorLength;
        if (supportedDirectly) {
            return true;
        }
        if (canExpandLongArithmeticShiftWithScalarCount(vectorArch, elementStamp, vectorLength)) {
            return true;
        }
        /*
         * Special case for byte shifts on backends without direct full-width byte shift support:
         * first try widen->shift->narrow for the full vector, then fall back to doing the same
         * transform on two half vectors and concatenating the result. AArch64 typically takes the
         * direct path above because it has native byte shifts.
         */
        if (PrimitiveStamp.getBits(elementStamp) != Byte.SIZE) {
            return false;
        }
        IntegerStamp byteStamp = (IntegerStamp) elementStamp;
        if (canExpandByteShiftViaWidening(vectorArch, byteStamp, vectorLength)) {
            return true;
        }
        return canExpandByteShiftViaLaneSplit(vectorArch, byteStamp, vectorLength);
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        ValueNode value = expanded.get(getVector());
        ValueNode shiftAmount = getArgument(SHIFT_ARG_INDEX);
        RotateDirection rotateDirection = computeRotateDirection(toArgumentArray(), OPRID_ARG_INDEX, vectorStamp);
        if (rotateDirection != null) {
            return expandRotateWithScalarCount(vectorStamp, value, shiftAmount, rotateDirection);
        }
        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp().getVectorLength();
        boolean supportedDirectly = vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, op) == vectorLength;
        if (supportedDirectly) {
            return ShiftNode.shiftOp(value, shiftAmount, NodeView.DEFAULT, op);
        } else if (canExpandLongArithmeticShiftWithScalarCount(vectorArch, elementStamp, vectorLength)) {
            return expandLongArithmeticShiftWithScalarCount(value, shiftAmount);
        }
        GraalError.guarantee(PrimitiveStamp.getBits(elementStamp) == Byte.SIZE, "unexpected stamp: %s", elementStamp);
        IntegerStamp byteStamp = (IntegerStamp) elementStamp;
        if (canExpandByteShiftViaWidening(vectorArch, byteStamp, vectorLength)) {
            return emitByteShiftViaWidening(value, shiftAmount, byteStamp);
        }
        if (canExpandByteShiftViaLaneSplit(vectorArch, byteStamp, vectorLength)) {
            return emitByteShiftViaLaneSplit(value, shiftAmount, byteStamp, vectorLength);
        }
        throw GraalError.shouldNotReachHere("byte vector shift cannot be expanded");
    }

    private boolean canExpandByteShiftViaWidening(VectorArchitecture vectorArch, IntegerStamp byteStamp, int vectorLength) {
        IntegerStamp shortStamp = StampFactory.forInteger(Short.SIZE);
        ArithmeticOpTable.IntegerConvertOp<?> extend = (op.equals(byteStamp.getOps().getUShr()) ? byteStamp.getOps().getZeroExtend() : byteStamp.getOps().getSignExtend());
        return vectorArch.getSupportedVectorConvertLength(shortStamp, byteStamp, vectorLength, extend) == vectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, vectorLength, op) == vectorLength &&
                        vectorArch.getSupportedVectorConvertLength(byteStamp, shortStamp, vectorLength, shortStamp.getOps().getNarrow()) == vectorLength;
    }

    private ValueNode emitByteShiftViaWidening(ValueNode value, ValueNode shiftAmount, IntegerStamp byteStamp) {
        ValueNode extendedVector = (op.equals(byteStamp.getOps().getUShr())
                        ? ZeroExtendNode.create(value, Byte.SIZE, Short.SIZE, NodeView.DEFAULT)
                        : SignExtendNode.create(value, Byte.SIZE, Short.SIZE, NodeView.DEFAULT));
        ValueNode shiftedVector = ShiftNode.shiftOp(extendedVector, shiftAmount, NodeView.DEFAULT, op);
        return NarrowNode.create(shiftedVector, Short.SIZE, Byte.SIZE, NodeView.DEFAULT);
    }

    private boolean canExpandByteShiftViaLaneSplit(VectorArchitecture vectorArch, IntegerStamp byteStamp, int vectorLength) {
        if (vectorLength <= 1 || (vectorLength & 1) != 0) {
            return false;
        }
        int halfLength = vectorLength / 2;
        IntegerStamp shortStamp = StampFactory.forInteger(Short.SIZE);
        ArithmeticOpTable.IntegerConvertOp<?> extend = (op.equals(byteStamp.getOps().getUShr()) ? byteStamp.getOps().getZeroExtend() : byteStamp.getOps().getSignExtend());
        return vectorArch.supportsVectorConcat(halfLength * Byte.BYTES) &&
                        vectorArch.getSupportedVectorConvertLength(shortStamp, byteStamp, halfLength, extend) == halfLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, halfLength, op) == halfLength &&
                        vectorArch.getSupportedVectorConvertLength(byteStamp, shortStamp, halfLength, shortStamp.getOps().getNarrow()) == halfLength;
    }

    private ValueNode emitByteShiftViaLaneSplit(ValueNode value, ValueNode shiftAmount, IntegerStamp byteStamp, int vectorLength) {
        int halfLength = vectorLength / 2;
        ValueNode lowBytes = new SimdCutNode(value, 0, halfLength);
        ValueNode highBytes = new SimdCutNode(value, halfLength, halfLength);
        ValueNode lowShifted = emitByteShiftViaWidening(lowBytes, shiftAmount, byteStamp);
        ValueNode highShifted = emitByteShiftViaWidening(highBytes, shiftAmount, byteStamp);
        return new SimdConcatNode(lowShifted, highShifted);
    }

    private boolean canExpandLongArithmeticShiftWithScalarCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!isLongArithmeticShiftOp(elementStamp)) {
            return false;
        }
        return vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getAnd()) == vectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, IntegerStamp.OPS.getShl()) == vectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, IntegerStamp.OPS.getUShr()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getSub()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getOr()) == vectorLength;
    }

    private boolean isLongArithmeticShiftOp(Stamp elementStamp) {
        return elementStamp instanceof IntegerStamp && PrimitiveStamp.getBits(elementStamp) == Long.SIZE && IntegerStamp.OPS.getShr().equals(op);
    }

    /**
     * Expands long scalar-count arithmetic right shift via logical shift plus synthesized sign
     * fill.
     *
     * The scalar count is masked to the lane width, then sign-fill is reconstructed and blended in
     * with a non-zero-count mask so that effective count zero preserves the input.
     */
    private ValueNode expandLongArithmeticShiftWithScalarCount(ValueNode valueVector, ValueNode scalarCount) {
        int vectorLength = vectorStamp.getVectorLength();
        ValueNode maskedCount = BinaryArithmeticNode.and(scalarCount, ConstantNode.forInt(Long.SIZE - 1));
        ValueNode signBitShift = ConstantNode.forInt(Long.SIZE - 1);
        ValueNode logicalShift = ShiftNode.shiftOp(valueVector, maskedCount, NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        ValueNode signBits = ShiftNode.shiftOp(valueVector, signBitShift, NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        ValueNode signMask = BinaryArithmeticNode.sub(graph().addOrUniqueWithInputs(new SimdBroadcastNode(ConstantNode.forLong(0), vectorLength)), signBits, NodeView.DEFAULT);
        ValueNode inverseCount = BinaryArithmeticNode.and(BinaryArithmeticNode.sub(ConstantNode.forInt(Long.SIZE), maskedCount, NodeView.DEFAULT), ConstantNode.forInt(Long.SIZE - 1));
        ValueNode maskedCountAsLong = SignExtendNode.create(maskedCount, Integer.SIZE, Long.SIZE, NodeView.DEFAULT);
        ValueNode nonZeroBits = ShiftNode.shiftOp(
                        BinaryArithmeticNode.or(maskedCountAsLong, BinaryArithmeticNode.sub(ConstantNode.forLong(0), maskedCountAsLong, NodeView.DEFAULT)),
                        signBitShift,
                        NodeView.DEFAULT,
                        IntegerStamp.OPS.getUShr());
        ValueNode nonZeroMaskVector = graph().addOrUniqueWithInputs(new SimdBroadcastNode(BinaryArithmeticNode.sub(ConstantNode.forLong(0), nonZeroBits, NodeView.DEFAULT), vectorLength));
        ValueNode highFill = BinaryArithmeticNode.and(ShiftNode.shiftOp(signMask, inverseCount, NodeView.DEFAULT, IntegerStamp.OPS.getShl()), nonZeroMaskVector);
        return BinaryArithmeticNode.or(logicalShift, highFill);
    }

    private static boolean canExpandRotateWithScalarCount(VectorArchitecture vectorArch, Stamp elementStamp, int vectorLength) {
        if (!(elementStamp instanceof IntegerStamp) || PrimitiveStamp.getBits(elementStamp) == Byte.SIZE) {
            return false;
        }
        boolean supportsViaShiftOr = vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, IntegerStamp.OPS.getShl()) == vectorLength &&
                        vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, IntegerStamp.OPS.getUShr()) == vectorLength &&
                        vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, IntegerStamp.OPS.getOr()) == vectorLength;
        return supportsViaShiftOr || vectorArch.getSupportedVectorRotateLength(elementStamp, vectorLength) == vectorLength;
    }

    private static ValueNode expandRotateWithScalarCount(SimdStamp inputStamp, ValueNode inputVector, ValueNode scalarCount, RotateDirection rotateDirection) {
        int elementBits = PrimitiveStamp.getBits(inputStamp.getComponent(0));
        ValueNode inverseShift = BinaryArithmeticNode.sub(ConstantNode.forInt(elementBits), scalarCount, NodeView.DEFAULT);

        /*
         * Build rotates in the IR as shifts plus or; backends can still match this form and select
         * native rotate instructions when available.
         */
        ValueNode leftShiftAmount = rotateDirection == RotateDirection.LEFT ? scalarCount : inverseShift;
        ValueNode rightShiftAmount = rotateDirection == RotateDirection.LEFT ? inverseShift : scalarCount;
        ValueNode leftShift = ShiftNode.shiftOp(inputVector, leftShiftAmount, NodeView.DEFAULT, IntegerStamp.OPS.getShl());
        ValueNode rightShift = ShiftNode.shiftOp(inputVector, rightShiftAmount, NodeView.DEFAULT, IntegerStamp.OPS.getUShr());
        return BinaryArithmeticNode.or(leftShift, rightShift);
    }
}
