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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_128)
public class BigIntegerRightShiftWorkerNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<BigIntegerRightShiftWorkerNode> TYPE = NodeClass.create(BigIntegerRightShiftWorkerNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Int)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("bigIntegerRightShiftWorker", void.class,
                    new Class<?>[]{Pointer.class, Pointer.class, int.class, int.class, int.class}, HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);

    @Input protected ValueNode newArr;
    @Input protected ValueNode oldArr;
    @Input protected ValueNode newIdx;
    @Input protected ValueNode shiftCount;
    @Input protected ValueNode numIter;

    public BigIntegerRightShiftWorkerNode(ValueNode newArr, ValueNode oldArr, ValueNode newIdx, ValueNode shiftCount, ValueNode numIter) {
        this(newArr, oldArr, newIdx, shiftCount, numIter, null);
    }

    public BigIntegerRightShiftWorkerNode(ValueNode newArr, ValueNode oldArr, ValueNode newIdx, ValueNode shiftCount, ValueNode numIter, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.newArr = newArr;
        this.oldArr = oldArr;
        this.newIdx = newIdx;
        this.shiftCount = shiftCount;
        this.numIter = numIter;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{newArr, oldArr, newIdx, shiftCount, numIter};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512F, AMD64.CPUFeature.AVX512_VBMI2);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }

    @NodeIntrinsic
    @GenerateStub(name = "bigIntegerRightShiftWorker", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    public static native void apply(Pointer newArr, Pointer oldArr, int newIdx, int shiftCount, int numIter);

    @NodeIntrinsic
    public static native void apply(Pointer newArr, Pointer oldArr, int newIdx, int shiftCount, int numIter, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitBigIntegerRightShiftWorker(gen.operand(newArr), gen.operand(oldArr), gen.operand(newIdx), gen.operand(shiftCount), gen.operand(numIter));
    }
}
