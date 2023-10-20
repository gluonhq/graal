/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.core.common.type.TypeReference;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.java.AbstractNewObjectNode;

import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public final class NewPodInstanceNode extends AbstractNewObjectNode {
    public static final NodeClass<NewPodInstanceNode> TYPE = NodeClass.create(NewPodInstanceNode.class);

    private final ResolvedJavaType knownInstanceType;
    @Input ValueNode hub;
    @Input ValueNode arrayLength;
    @Input ValueNode referenceMap;

    public NewPodInstanceNode(ResolvedJavaType knownInstanceType, ValueNode hub, ValueNode arrayLength, ValueNode referenceMap) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(knownInstanceType)), true, null);
        this.knownInstanceType = knownInstanceType;
        this.hub = hub;
        this.arrayLength = arrayLength;
        this.referenceMap = referenceMap;
    }

    public ResolvedJavaType getKnownInstanceType() {
        return knownInstanceType;
    }

    public ValueNode getHub() {
        return hub;
    }

    public ValueNode getArrayLength() {
        return arrayLength;
    }

    public ValueNode getReferenceMap() {
        return referenceMap;
    }

    @NodeIntrinsic
    public static native Object newPodInstance(@ConstantNodeParameter Class<?> knownInstanceClass, Class<?> runtimeClass, int arrayLength, byte[] referenceMap);
}
