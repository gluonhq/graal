/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestate;

import java.util.BitSet;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractStaticInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestore.ArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedFieldTypeStore;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

public class DefaultAnalysisPolicy extends AnalysisPolicy {

    public DefaultAnalysisPolicy(OptionValues options) {
        super(options);
    }

    @Override
    public boolean isContextSensitiveAnalysis() {
        return false;
    }

    @Override
    public MethodTypeFlow createMethodTypeFlow(PointsToAnalysisMethod method) {
        return new MethodTypeFlow(method);
    }

    @Override
    public boolean needsConstantCache() {
        return false;
    }

    @Override
    public boolean isSummaryObject(AnalysisObject object) {
        /* Context insensitive objects summarize context sensitive objects of the same type. */
        return object.isContextInsensitiveObject();
    }

    @Override
    public boolean isMergingEnabled() {
        // by default no merging is necessary
        return false;
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, TypeState t) {
        // nothing to do
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, AnalysisObject... a) {
        // nothing to do
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, AnalysisObject a) {
        // nothing to do
    }

    @Override
    public boolean isContextSensitiveAllocation(PointsToAnalysis bb, AnalysisType type, AnalysisContext allocationContext) {
        return false;
    }

    @Override
    public AnalysisObject createHeapObject(PointsToAnalysis bb, AnalysisType type, BytecodeLocation allocationSite, AnalysisContext allocationContext) {
        return type.getContextInsensitiveAnalysisObject();
    }

    @Override
    public AnalysisObject createConstantObject(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType) {
        return exactType.getContextInsensitiveAnalysisObject();
    }

    @Override
    public TypeState dynamicNewInstanceState(PointsToAnalysis bb, TypeState currentState, TypeState newState, BytecodeLocation allocationSite, AnalysisContext allocationContext) {
        /* Just return the new type state as there is no allocation context. */
        return newState.forNonNull(bb);
    }

    @Override
    public TypeState cloneState(PointsToAnalysis bb, TypeState currentState, TypeState inputState, BytecodeLocation cloneSite, AnalysisContext allocationContext) {
        return inputState.forNonNull(bb);
    }

    @Override
    public void linkClonedObjects(PointsToAnalysis bb, TypeFlow<?> inputFlow, CloneTypeFlow cloneFlow, BytecodePosition source) {
        /*
         * Nothing to do for the context insensitive analysis. The source and clone flows are
         * identical, thus their elements are modeled by the same array or field flows.
         */
    }

    @Override
    public BytecodeLocation createAllocationSite(PointsToAnalysis bb, int bci, AnalysisMethod method) {
        return BytecodeLocation.create(bci, method);
    }

    @Override
    public FieldTypeStore createFieldTypeStore(AnalysisObject object, AnalysisField field, AnalysisUniverse universe) {
        return new UnifiedFieldTypeStore(field, object, new ContextInsensitiveFieldTypeFlow(field, field.getType(), object));
    }

    @Override
    public ArrayElementsTypeStore createArrayElementsTypeStore(AnalysisObject object, AnalysisUniverse universe) {
        if (object.type().isArray()) {
            if (aliasArrayTypeFlows) {
                /* Alias all array type flows using the elements type flow model of Object type. */
                if (object.type().getComponentType().isJavaLangObject()) {
                    return new UnifiedArrayElementsTypeStore(object);
                }
                return universe.objectType().getArrayClass().getContextInsensitiveAnalysisObject().getArrayElementsTypeStore();
            }
            return new UnifiedArrayElementsTypeStore(object);
        } else {
            return null;
        }
    }

    @Override
    public AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        return new DefaultVirtualInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
    }

    @Override
    public AbstractSpecialInvokeTypeFlow createSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        return new DefaultSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
    }

    @Override
    public AbstractStaticInvokeTypeFlow createStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        return new DefaultStaticInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
    }

    @Override
    public MethodFlowsGraph staticRootMethodGraph(PointsToAnalysis bb, PointsToAnalysisMethod pointsToMethod) {
        return pointsToMethod.getTypeFlow().getOrCreateMethodFlowsGraph(bb, null);
    }

    @Override
    public AnalysisContext allocationContext(PointsToAnalysis bb, MethodFlowsGraph callerGraph) {
        throw AnalysisError.shouldNotReachHere();
    }

    @Override
    public TypeFlow<?> proxy(BytecodePosition source, TypeFlow<?> input) {
        return input;
    }

    @Override
    public boolean addOriginalUse(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> use) {
        /* Adds the use, if not already present, and propagates the type state. */
        return flow.addUse(bb, use, true);
    }

    @Override
    public boolean addOriginalObserver(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> observer) {
        /* Adds the observer, if not already present, and triggers an update. */
        return flow.addObserver(bb, observer, true);
    }

    @Override
    public void linkActualReturn(PointsToAnalysis bb, boolean isStatic, InvokeTypeFlow invoke) {
        /* Link the actual return with the formal return of already linked callees. */
        for (AnalysisMethod callee : invoke.getCallees()) {
            invoke.linkReturn(bb, isStatic, ((PointsToAnalysisMethod) callee).getTypeFlow().getMethodFlowsGraph());
        }
        if (invoke.isSaturated()) {
            InvokeTypeFlow contextInsensitiveInvoke = invoke.getTargetMethod().getContextInsensitiveVirtualInvoke();
            contextInsensitiveInvoke.getActualReturn().addUse(bb, invoke.getActualReturn());
        }
    }

    @Override
    public void registerAsImplementationInvoked(InvokeTypeFlow invoke, MethodFlowsGraph calleeFlows) {
        calleeFlows.getMethod().registerAsImplementationInvoked(invoke);
    }

    @Override
    public TypeState forContextInsensitiveTypeState(PointsToAnalysis bb, TypeState state) {
        /* The type state is already context insensitive. */
        return state;
    }

    @Override
    public SingleTypeState singleTypeState(PointsToAnalysis bb, boolean canBeNull, int properties, AnalysisType type, AnalysisObject... objects) {
        return new SingleTypeState(bb, canBeNull, properties, type);
    }

    @Override
    public MultiTypeState multiTypeState(PointsToAnalysis bb, boolean canBeNull, int properties, BitSet typesBitSet, AnalysisObject... objects) {
        return new MultiTypeState(bb, canBeNull, properties, typesBitSet);
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, SingleTypeState s1, SingleTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();
        if (s1.exactType().equals(s2.exactType())) {
            /*
             * The inputs have the same type, so the result is a SingleTypeState. Check if any of
             * the states has the right null state.
             */
            if (s1.canBeNull() == resultCanBeNull) {
                return s1;
            } else {
                return s2;
            }
        } else {
            /*
             * The inputs have different types, so the result is a MultiTypeState. We know the
             * types, just construct the types bit set.
             */
            BitSet typesBitSet = TypeStateUtils.newBitSet(s1.exactType().getId(), s2.exactType().getId());

            TypeState result = new MultiTypeState(bb, resultCanBeNull, 0, typesBitSet);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        if (s1.containsType(s2.exactType())) {
            /* The type of s2 is already contained in s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        } else {
            BitSet typesBitSet = TypeStateUtils.set(s1.typesBitSet(), s2.exactType().getId());
            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, 0, typesBitSet);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        assert s1.typesCount() >= s2.typesCount();

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.typesBitSet() == s2.typesBitSet()) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /* Speculate that s1 is a superset of s2. */
        if (TypeStateUtils.isSuperset(s1.typesBitSet(), s2.typesBitSet())) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /* Logical OR the type bit sets. */
        BitSet resultTypesBitSet = TypeStateUtils.or(s1.typesBitSet(), s2.typesBitSet());

        MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, 0, resultTypesBitSet);
        assert !result.equals(s1) && !result.equals(s2);
        PointsToStats.registerUnionOperation(bb, s1, s2, result);
        return result;
    }

    @Override
    public TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";

        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* s1 contains s2's type, return s2. */
            return s2.forCanBeNull(bb, resultCanBeNull);
        } else {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }
    }

    @Override
    public TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();

        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.typesBitSet().equals(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.typesBitSet().intersects(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is empty. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is s1.
         */
        if (TypeStateUtils.isSuperset(s2.typesBitSet(), s1.typesBitSet())) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        BitSet resultTypesBitSet = TypeStateUtils.and(s1.typesBitSet(), s2.typesBitSet());
        if (resultTypesBitSet.cardinality() == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (resultTypesBitSet.cardinality() == 1) {
            AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
            return new SingleTypeState(bb, resultCanBeNull, 0, type);
        } else {
            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, 0, resultTypesBitSet);
            assert !result.equals(s1);
            return result;
        }
    }

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* s2 is contained in s1, so remove s2's type from s1. */
            BitSet resultTypesBitSet = TypeStateUtils.clear(s1.typesBitSet(), s2.exactType().getId());
            assert resultTypesBitSet.cardinality() > 0;
            if (resultTypesBitSet.cardinality() == 1) {
                AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
                return new SingleTypeState(bb, resultCanBeNull, 0, type);
            } else {
                return new MultiTypeState(bb, resultCanBeNull, 0, resultTypesBitSet);
            }
        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();

        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.typesBitSet().equals(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is empty set. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.typesBitSet().intersects(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is empty.
         */
        if (TypeStateUtils.isSuperset(s2.typesBitSet(), s1.typesBitSet())) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        BitSet resultTypesBitSet = TypeStateUtils.andNot(s1.typesBitSet(), s2.typesBitSet());
        if (resultTypesBitSet.cardinality() == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (resultTypesBitSet.cardinality() == 1) {
            AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
            return new SingleTypeState(bb, resultCanBeNull, 0, type);
        } else {
            return new MultiTypeState(bb, resultCanBeNull, 0, resultTypesBitSet);
        }
    }

}
