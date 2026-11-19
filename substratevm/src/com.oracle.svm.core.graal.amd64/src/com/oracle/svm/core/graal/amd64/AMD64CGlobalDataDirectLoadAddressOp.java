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
package com.oracle.svm.core.graal.amd64;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.CGlobalDataDirectReference;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;

@Platforms(Platform.HOSTED_ONLY.class)
public sealed class AMD64CGlobalDataDirectLoadAddressOp extends AMD64CGlobalDataLoadAddressOp permits AMD64LoadLayeredMethodOffsetConstantOp {
    public static final LIRInstructionClass<AMD64CGlobalDataDirectLoadAddressOp> TYPE = LIRInstructionClass.create(AMD64CGlobalDataDirectLoadAddressOp.class);

    public AMD64CGlobalDataDirectLoadAddressOp(CGlobalDataInfo dataInfo, AllocatableValue result) {
        this(dataInfo, result, 0);
    }

    AMD64CGlobalDataDirectLoadAddressOp(CGlobalDataInfo dataInfo, AllocatableValue result, int addend) {
        this(TYPE, dataInfo, result, addend);
    }

    protected AMD64CGlobalDataDirectLoadAddressOp(LIRInstructionClass<? extends AMD64CGlobalDataDirectLoadAddressOp> type, CGlobalDataInfo dataInfo, AllocatableValue result, int addend) {
        super(type, dataInfo, result, addend);
    }

    @Override
    protected void recordDataPatch(CompilationResultBuilder crb, int position, AMD64Address address) {
        crb.compilationResult.recordDataPatch(position, new CGlobalDataDirectReference(dataInfo));
    }

    @Override
    protected AMD64Address emitLoadAddress(AMD64MacroAssembler masm, int position) {
        return masm.getPlaceholder(position);
    }
}
