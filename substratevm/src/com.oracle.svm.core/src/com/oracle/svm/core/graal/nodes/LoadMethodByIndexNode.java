/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.nativeimage.c.function.CodePointer;

import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.word.WordCastNode;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
@NodeIntrinsicFactory
public final class LoadMethodByIndexNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<LoadMethodByIndexNode> TYPE = NodeClass.create(LoadMethodByIndexNode.class);

    @Input protected ValueNode hub;
    @Input protected ValueNode vtableIndex;
    @OptionalInput protected ValueNode interfaceID;

    protected LoadMethodByIndexNode(@InjectedNodeParameter StampProvider stampProvider, ValueNode hub, ValueNode vtableIndex, ValueNode interfaceID) {
        super(TYPE, stampProvider.createMethodStamp());
        this.hub = hub;
        this.vtableIndex = vtableIndex;
        this.interfaceID = interfaceID;
    }

    public ValueNode getHub() {
        return hub;
    }

    public ValueNode getVTableIndex() {
        return vtableIndex;
    }

    public ValueNode getInterfaceID() {
        return interfaceID;
    }

    /**
     * Loads a vtable entry with a raw word stamp (see {@link WordTypes}). This is adequate for
     * immediate calls, but for dispatch/inlining speculations that compare the entry with method
     * constants, it prevents optimizations enabled by {@link MethodOffsetToPointerNode}.
     *
     * @see #intrinsify
     */
    @NodeIntrinsic
    public static native CodePointer loadMethodByIndex(Object hub, int vtableIndex, int interfaceID);

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter StampProvider stampProvider, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode hub, ValueNode vtableIndex, ValueNode interfaceID) {
        ValueNode methodPointer = b.append(new LoadMethodByIndexNode(stampProvider, hub, vtableIndex, interfaceID));
        ValueNode wordResult = b.add(WordCastNode.addressToWord(methodPointer, wordTypes.getWordKind()));
        b.push(JavaKind.Object, wordResult);
        return true;
    }
}
