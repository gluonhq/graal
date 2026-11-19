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

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataIndirectReference;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;

@Platforms(Platform.HOSTED_ONLY.class)
public final class AMD64CGlobalDataIndirectLoadAddressOp extends AMD64CGlobalDataLoadAddressOp {
    public static final LIRInstructionClass<AMD64CGlobalDataIndirectLoadAddressOp> TYPE = LIRInstructionClass.create(AMD64CGlobalDataIndirectLoadAddressOp.class);

    @Use({REG, STACK}) private AllocatableValue runtimeBaseAddress;

    public AMD64CGlobalDataIndirectLoadAddressOp(CGlobalDataInfo dataInfo, AllocatableValue result, AllocatableValue runtimeBaseAddress) {
        super(TYPE, dataInfo, result, 0);
        this.runtimeBaseAddress = runtimeBaseAddress;
    }

    @Override
    protected AMD64Address emitLoadAddress(AMD64MacroAssembler masm, int position) {
        /*
         * Create a different kind of Reference compared to regular CGlobalData access because we
         * don't want to emit a linker relocation for the address offset, but instead patch it
         * directly.
         */
        var addressAnnotation = new CGlobalDataIndirectReference(dataInfo);
        return new AMD64Address(asRegister(runtimeBaseAddress), Register.None, Stride.S1, 0, addressAnnotation, position);
    }

    @Override
    @SuppressWarnings("unused")
    protected void recordDataPatch(CompilationResultBuilder crb, int position, AMD64Address address) {
        // No op, patch is recorded in the AMD64HostedPatcherFeature directly.
    }
}
