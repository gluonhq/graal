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
package com.oracle.svm.core.graal.aarch64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.CGlobalDataIndirectReference;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;

import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Platforms(Platform.HOSTED_ONLY.class)
public final class AArch64CGlobalDataIndirectLoadAddressOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64CGlobalDataIndirectLoadAddressOp> TYPE = LIRInstructionClass.create(AArch64CGlobalDataIndirectLoadAddressOp.class);

    @Def(REG) private AllocatableValue result;
    @Alive(REG) private AllocatableValue runtimeBaseAddress;
    @Temp(REG) private AllocatableValue offset;

    private final CGlobalDataInfo dataInfo;

    AArch64CGlobalDataIndirectLoadAddressOp(CGlobalDataInfo dataInfo, AllocatableValue result, AllocatableValue runtimeBaseAddress, AllocatableValue offset) {
        super(TYPE);
        this.dataInfo = dataInfo;
        this.result = result;
        this.runtimeBaseAddress = runtimeBaseAddress;
        this.offset = offset;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register resultRegister = asRegister(result);
        Register runtimeBaseAddressRegister = asRegister(runtimeBaseAddress);
        Register offsetRegister = asRegister(offset);

        masm.movWithReferenceAnnotation(offsetRegister, 0, new CGlobalDataIndirectReference(dataInfo));
        if (dataInfo.isSymbolReference()) {
            AArch64Address address = AArch64Address.createRegisterOffsetAddress(64, runtimeBaseAddressRegister, offsetRegister, false);
            masm.ldr(64, resultRegister, address);
        } else {
            masm.add(64, resultRegister, runtimeBaseAddressRegister, offsetRegister);
        }
    }
}
