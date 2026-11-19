/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Platforms(Platform.HOSTED_ONLY.class)
public abstract class AMD64CGlobalDataLoadAddressOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CGlobalDataLoadAddressOp> TYPE = LIRInstructionClass.create(AMD64CGlobalDataLoadAddressOp.class);

    @Def(REG) protected AllocatableValue result;

    protected final CGlobalDataInfo dataInfo;
    private final int addend;

    protected AMD64CGlobalDataLoadAddressOp(LIRInstructionClass<? extends AMD64CGlobalDataLoadAddressOp> type, CGlobalDataInfo dataInfo, AllocatableValue result, int addend) {
        super(type);
        assert dataInfo != null;
        this.dataInfo = dataInfo;
        this.result = result;
        this.addend = addend;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        /*
         * We are in an AOT compilation here because runtime compilations always access CGlobalData
         * via the image heap.
         */
        int before = masm.position();
        AMD64Address address = emitLoadAddress(masm, before);
        if (dataInfo.isSymbolReference()) {
            // Pure symbol reference: the data contains the symbol's address, load it
            masm.movq(resultReg, address);
        } else {
            // Data: load its address
            masm.leaq(resultReg, address);
        }
        recordDataPatch(crb, before, address);
        if (addend != 0) {
            masm.addq(resultReg, addend);
        }
    }

    protected abstract void recordDataPatch(CompilationResultBuilder crb, int before, AMD64Address address);

    protected abstract AMD64Address emitLoadAddress(AMD64MacroAssembler masm, int before);
}
