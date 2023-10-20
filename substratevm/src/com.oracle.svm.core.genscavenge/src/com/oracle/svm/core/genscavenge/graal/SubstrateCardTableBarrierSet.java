/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import jdk.compiler.graal.core.common.memory.BarrierType;
import jdk.compiler.graal.nodes.gc.CardTableBarrierSet;

import com.oracle.svm.core.StaticFieldsSupport;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Static fields in SVM are represented as two arrays in the native image heap: one for Object
 * fields and one for all primitive fields (see {@link StaticFieldsSupport}). Therefore, we must
 * emit array write barriers for static fields.
 */
public class SubstrateCardTableBarrierSet extends CardTableBarrierSet {
    public SubstrateCardTableBarrierSet(ResolvedJavaType objectArrayType) {
        super(objectArrayType);
    }

    @Override
    public BarrierType fieldWriteBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        if (field.isStatic() && storageKind == JavaKind.Object) {
            return arrayWriteBarrierType(storageKind);
        }
        return super.fieldWriteBarrierType(field, storageKind);
    }
}
