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

import com.oracle.svm.hosted.methodhandles.SVMMethodHandleWithExceptionPlugin;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.graal.compiler.replacements.nodes.MacroNode;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode.GraphAdder;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode.InvokeFactory;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ForeignCapableMethodHandleWithExceptionPlugin extends SVMMethodHandleWithExceptionPlugin {
    public ForeignCapableMethodHandleWithExceptionPlugin(MethodHandleAccessProvider methodHandleAccess) {
        super(methodHandleAccess, false);
    }

    @Override
    protected boolean canHandleIntrinsicMethod(IntrinsicMethod intrinsicMethod) {
        return intrinsicMethod == IntrinsicMethod.LINK_TO_NATIVE || super.canHandleIntrinsicMethod(intrinsicMethod);
    }

    @Override
    protected MacroInvokable createMethodHandleNode(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args,
                    IntrinsicMethod intrinsicMethod, CallTargetNode.InvokeKind invokeKind, StampPair invokeReturnStamp) {
        return new ForeignCapableMethodHandleWithExceptionNode(intrinsicMethod, MacroNode.MacroParams.of(invokeKind, b.getMethod(), method, b.bci(), invokeReturnStamp, args));
    }

    @Override
    protected <T extends Invoke> T tryResolveTargetInvoke(GraphAdder adder, InvokeFactory<T> factory, IntrinsicMethod intrinsicMethod, ResolvedJavaMethod original, int bci, StampPair returnStamp,
                    ValueNode... arguments) {
        if (intrinsicMethod == IntrinsicMethod.LINK_TO_NATIVE) {
            return ForeignCapableMethodHandleWithExceptionNode.getLinkToNativeTarget(adder, factory, methodHandleAccess, original, bci, returnStamp, arguments);
        }
        return super.tryResolveTargetInvoke(adder, factory, intrinsicMethod, original, bci, returnStamp, arguments);
    }
}
