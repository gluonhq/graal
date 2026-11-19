/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import com.oracle.svm.hosted.ForeignHostedSupport;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.replacements.nodes.MacroNode;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode.GraphAdder;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode.InvokeFactory;
import jdk.graal.compiler.replacements.nodes.MethodHandleWithExceptionNode;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Similar to {@link MethodHandleWithExceptionNode} but also handles
 * {@link IntrinsicMethod#LINK_TO_NATIVE} by resolving the native entry point argument to the
 * corresponding downcall stub. Since the downcall stub is a Java method, the resulting invoke can
 * be then inlined.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, cyclesRationale = "see MacroNode", size = SIZE_UNKNOWN, sizeRationale = "see MacroNode")
public final class ForeignCapableMethodHandleWithExceptionNode extends MethodHandleWithExceptionNode {
    public static final NodeClass<ForeignCapableMethodHandleWithExceptionNode> TYPE = NodeClass.create(ForeignCapableMethodHandleWithExceptionNode.class);

    public ForeignCapableMethodHandleWithExceptionNode(IntrinsicMethod intrinsicMethod, MacroNode.MacroParams p) {
        super(TYPE, intrinsicMethod, p);
    }

    @Override
    protected <T extends Invoke> T tryResolveTargetInvoke(GraphAdder adder, InvokeFactory<T> factory, MethodHandleAccessProvider methodHandleAccess, ValueNode... argumentsArray) {
        if (intrinsicMethod == IntrinsicMethod.LINK_TO_NATIVE) {
            return getLinkToNativeTarget(adder, factory, methodHandleAccess, targetMethod, bci, returnStamp, argumentsArray);
        }
        return super.tryResolveTargetInvoke(adder, factory, methodHandleAccess, argumentsArray);
    }

    private static ValueNode getNativeEntryPoint(ValueNode[] arguments) {
        return arguments[arguments.length - 1];
    }

    /**
     * Resolves the native entry point constant to the downcall stub method and then builds a direct
     * invoke for that stub.
     */
    static <T extends Invoke> T getLinkToNativeTarget(GraphAdder adder, InvokeFactory<T> factory, MethodHandleAccessProvider methodHandleAccess, ResolvedJavaMethod original, int bci,
                    StampPair returnStamp, ValueNode[] arguments) {
        ValueNode nativeEntryPoint = getNativeEntryPoint(arguments);
        if (nativeEntryPoint.isConstant()) {
            ResolvedJavaMethod downcallStub = ForeignHostedSupport.singleton().resolveDowncallStub(nativeEntryPoint.asJavaConstant());
            return ForeignCapableMethodHandleWithExceptionNode.getTargetInvokeNode(adder, factory, methodHandleAccess, bci, returnStamp, arguments, downcallStub, original);
        }
        return null;
    }

    /** Similar to {@code MethodHandleNode#getTargetInvokeNode}. */
    private static <T extends Invoke> T getTargetInvokeNode(GraphAdder adder, InvokeFactory<T> factory,
                    MethodHandleAccessProvider methodHandleAccess, int bci, StampPair returnStamp, ValueNode[] originalArguments, ResolvedJavaMethod downcallStub, ResolvedJavaMethod original) {
        if (downcallStub == null || !isConsistentInfo(methodHandleAccess, original, downcallStub)) {
            return null;
        }

        /*
         * In lambda forms we erase signature types to avoid resolving issues involving class
         * loaders. When we optimize a method handle invoke to a direct call we must cast the
         * receiver and arguments to their actual types.
         */
        Signature signature = downcallStub.getSignature();
        assert downcallStub.isStatic() : "downcall stub is expected to be static";

        Assumptions assumptions = adder.getAssumptions();

        // Don't mutate the passed in arguments
        ValueNode[] arguments = originalArguments.clone();

        for (int index = 0; index < signature.getParameterCount(false); index++) {
            JavaType parameterType = signature.getParameterType(index, downcallStub.getDeclaringClass());
            MethodHandleNode.maybeCastArgument(adder, arguments, index, parameterType);
        }
        T invoke = createDowncallStubInvokeNode(factory, assumptions, downcallStub, bci, returnStamp, arguments);
        assert invoke != null : "graph has been modified so this must result an invoke";
        return invoke;
    }

    /**
     * Creates the direct invoke for the resolved downcall stub.
     * <p>
     * Forwards every operand to the stub, including the trailing NativeEntryPoint argument because
     * the stub expects that (see {@link DowncallStub#createSignature}).
     */
    private static <T extends Invoke> T createDowncallStubInvokeNode(InvokeFactory<T> factory, Assumptions assumptions,
                    ResolvedJavaMethod downcallStub, int bci, StampPair returnStamp, ValueNode[] arguments) {
        assert downcallStub.isStatic() : "downcall stub is expected to be static";
        JavaType targetReturnType = downcallStub.getSignature().getReturnType(null);

        StampPair targetReturnStamp = StampFactory.forDeclaredType(assumptions, targetReturnType, false);

        MethodCallTargetNode callTarget = new MethodCallTargetNode(InvokeKind.Static, downcallStub, arguments, targetReturnStamp, null);

        /*
         * The call target can have a different return type than the invoker, e.g. the target
         * returns an Object but the invoker void. In this case we need to use the stamp of the
         * invoker. Note: always using the invoker's stamp would be wrong because it's a less
         * concrete type (usually java.lang.Object).
         */
        Stamp stamp = targetReturnStamp.getTrustedStamp();
        if (returnStamp.getTrustedStamp().getStackKind() == JavaKind.Void) {
            stamp = StampFactory.forVoid();
        }
        return factory.create(callTarget, bci, stamp);
    }

    private static boolean isConsistentInfo(MethodHandleAccessProvider methodHandleAccess, ResolvedJavaMethod original, ResolvedJavaMethod target) {
        IntrinsicMethod originalIntrinsicMethod = methodHandleAccess.lookupMethodHandleIntrinsic(original);
        if (originalIntrinsicMethod != IntrinsicMethod.LINK_TO_NATIVE) {
            return false;
        }
        IntrinsicMethod targetIntrinsicMethod = methodHandleAccess.lookupMethodHandleIntrinsic(target);
        Signature originalSignature = original.getSignature();
        Signature targetSignature = target.getSignature();

        boolean invokeThroughMHIntrinsic = targetIntrinsicMethod == null;
        if (!invokeThroughMHIntrinsic) {
            return original.getName().equals(target.getName()) && originalSignature.equals(targetSignature);
        }

        // Linkers have appendix argument which is not passed to callee.
        if (originalSignature.getParameterCount(original.hasReceiver()) != targetSignature.getParameterCount(target.hasReceiver())) {
            return false; // parameter count mismatch
        }
        // LINK_TO_NATIVE is static -> no receiver
        if (target.hasReceiver()) {
            return false;
        }

        assert targetSignature.getParameterCount(false) == originalSignature.getParameterCount(false) : "argument count mismatch";
        int argCount = targetSignature.getParameterCount(false);
        for (int i = 0; i < argCount; i++) {
            if (originalSignature.getParameterKind(i).getStackKind() != targetSignature.getParameterKind(i).getStackKind()) {
                return false;
            }
        }
        // Only check the return type if the symbolic info has non-void return type.
        // I.e. the return value of the resolved method can be dropped.
        return originalSignature.getReturnKind() == JavaKind.Void ||
                        originalSignature.getReturnKind().getStackKind() == targetSignature.getReturnKind().getStackKind();
    }
}
