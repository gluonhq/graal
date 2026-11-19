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
package com.oracle.svm.hosted.foreign;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

class ForeignGraphKit extends HostedGraphKit {
    ForeignGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, providers, method);
    }

    public static String signatureToIdentifier(Signature signature) {
        StringBuilder sb = new StringBuilder();
        for (var javaKind : signature.toParameterKinds(false)) {
            sb.append(javaKind.getTypeChar());
        }
        sb.append('_').append(signature.getReturnKind().getTypeChar());
        return sb.toString();
    }

    public ValueNode packArguments(List<ValueNode> arguments) {
        MetaAccessProvider metaAccess = getMetaAccess();
        ValueNode argumentArray = append(new NewArrayNode(metaAccess.lookupJavaType(Object.class), ConstantNode.forInt(arguments.size(), getGraph()), false));
        for (int i = 0; i < arguments.size(); ++i) {
            var argument = arguments.get(i);
            assert argument.getStackKind().equals(JavaKind.Object);
            createStoreIndexed(argumentArray, i, JavaKind.Object, argument);
        }
        return append(new PublishWritesNode(argumentArray));
    }

    ValueNode[] unboxArguments(List<ValueNode> args, Signature targetSignature) {
        assert !args.isEmpty() : "there must be at least the NativeEntryPoint (the last arg)";
        assert args.size() == targetSignature.getParameterCount(false) : args.size() + " " + targetSignature.getParameterCount(false);
        var newArgs = new ValueNode[args.size()];
        for (int i = 0; i < newArgs.length; ++i) {
            ValueNode argument = args.get(i);
            JavaKind targetKind = targetSignature.getParameterKind(i);
            if (argument.getStackKind().isObject() && targetKind.isPrimitive()) {
                argument = createUnboxing(argument, targetKind);
            }
            newArgs[i] = argument;
        }

        return newArgs;
    }

    public List<ValueNode> boxArguments(List<ValueNode> args, MethodType methodType) {
        assert args.size() == methodType.parameterCount() : args.size() + " " + methodType.parameterCount();
        var newArgs = new ArrayList<>(args);
        for (int i = 0; i < newArgs.size(); ++i) {
            ValueNode argument = newArgs.get(i);
            JavaKind kind = JavaKind.fromJavaClass(methodType.parameterType(i));
            ResolvedJavaType boxed = getMetaAccess().lookupJavaType(kind.toBoxedJavaClass());
            argument = createBoxing(argument, kind, boxed);
            newArgs.set(i, argument);
        }
        return newArgs;
    }

    public ValueNode boxAndReturn(ValueNode returnValue, JavaKind returnKind) {
        if (JavaKind.Void.equals(returnKind)) {
            return createReturn(ConstantNode.defaultForKind(JavaKind.Object), JavaKind.Object);
        }
        var boxed = getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass());
        return createReturn(createBoxing(returnValue, returnKind, boxed), JavaKind.Object);
    }

    public ValueNode unbox(ValueNode returnValue, MethodType methodType) {
        JavaKind returnKind = JavaKind.fromJavaClass(methodType.returnType());
        if (JavaKind.Void.equals(returnKind)) {
            return returnValue;
        }
        return createUnboxing(returnValue, returnKind);
    }

    public ValueNode createReturn(ValueNode returnValue, MethodType methodType) {
        JavaKind returnKind = JavaKind.fromJavaClass(methodType.returnType());
        return createReturn(returnValue, returnKind);
    }
}
