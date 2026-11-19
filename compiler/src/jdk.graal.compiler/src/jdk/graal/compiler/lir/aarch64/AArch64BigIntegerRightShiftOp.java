/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.HalfReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Word;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.EQ;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.LT;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L7858-L7970",
          sha1 = "fc22733eed405527d130a836e8810c77c25749e7")
// @formatter:on
public final class AArch64BigIntegerRightShiftOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64BigIntegerRightShiftOp> TYPE = LIRInstructionClass.create(AArch64BigIntegerRightShiftOp.class);

    @Use({OperandFlag.REG}) private Value newArrValue;
    @Use({OperandFlag.REG}) private Value oldArrValue;
    @Use({OperandFlag.REG}) private Value newIdxValue;
    @Use({OperandFlag.REG}) private Value shiftCountValue;
    @Use({OperandFlag.REG}) private Value numIterValue;

    @Temp({OperandFlag.REG}) private Value[] temp;

    public AArch64BigIntegerRightShiftOp(Value newArrValue, Value oldArrValue, Value newIdxValue, Value shiftCountValue, Value numIterValue) {
        super(TYPE);
        GraalError.guarantee(asRegister(newArrValue).equals(r0), "expect newArr at r0, but was %s", newArrValue);
        GraalError.guarantee(asRegister(oldArrValue).equals(r1), "expect oldArr at r1, but was %s", oldArrValue);
        GraalError.guarantee(asRegister(newIdxValue).equals(r2), "expect newIdx at r2, but was %s", newIdxValue);
        GraalError.guarantee(asRegister(shiftCountValue).equals(r3), "expect shiftCount at r3, but was %s", shiftCountValue);
        GraalError.guarantee(asRegister(numIterValue).equals(r4), "expect numIter at r4, but was %s", numIterValue);

        this.newArrValue = newArrValue;
        this.oldArrValue = oldArrValue;
        this.newIdxValue = newIdxValue;
        this.shiftCountValue = shiftCountValue;
        this.numIterValue = numIterValue;
        this.temp = new Value[]{
                        r0.asValue(),
                        r4.asValue(),
                        r11.asValue(),
                        r12.asValue(),
                        r20.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(newArrValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid newArrValue kind: %s", newArrValue);
        GraalError.guarantee(oldArrValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid oldArrValue kind: %s", oldArrValue);
        GraalError.guarantee(newIdxValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid newIdxValue kind: %s", newIdxValue);
        GraalError.guarantee(shiftCountValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid shiftCountValue kind: %s", shiftCountValue);
        GraalError.guarantee(numIterValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid numIterValue kind: %s", numIterValue);

        Register newArr = asRegister(newArrValue);
        Register oldArr = asRegister(oldArrValue);
        Register newIdx = asRegister(newIdxValue);
        Register shiftCount = asRegister(shiftCountValue);
        Register numIter = asRegister(numIterValue);
        Register idx = numIter;

        Register tmp0 = r20;
        Register tmp1 = r11;
        Register tmp2 = r12;

        Register oldElem0 = v0;
        Register oldElem1 = v1;
        Register newElem = v2;
        Register shiftVCount = v3;
        Register shiftVRevCount = v4;

        Label shiftSIMDLoop = new Label();
        Label shiftTwoLoop = new Label();
        Label shiftThree = new Label();
        Label shiftTwo = new Label();
        Label shiftOne = new Label();
        Label exit = new Label();

        try (ScratchRegister sr1 = masm.getScratchRegister();
                        ScratchRegister sr2 = masm.getScratchRegister()) {
            Register rscratch1 = sr1.getRegister();
            Register rscratch2 = sr2.getRegister();

            Register newArrCur = rscratch1;
            Register shiftRevCount = rscratch2;
            Register oldArrCur = r13;
            Register oldArrNext = r14;

            masm.cbz(64, idx, exit);

            masm.add(64, newArr, newArr, newIdx, LSL, 2);

            // left shift count
            masm.mov(shiftRevCount, 32);
            masm.sub(32, shiftRevCount, shiftRevCount, shiftCount);

            // numIter too small to allow a 4-words SIMD loop, rolling back
            masm.compare(64, numIter, 4);
            masm.branchConditionally(LT, shiftThree);

            masm.neon.dupVG(FullReg, Word, shiftVCount, shiftCount);
            masm.neon.dupVG(FullReg, Word, shiftVRevCount, shiftRevCount);
            masm.neon.negVV(FullReg, Word, shiftVCount, shiftVCount);

            masm.bind(shiftSIMDLoop);

            // Calculate the load addresses
            masm.sub(64, idx, idx, 4);
            masm.add(64, oldArrNext, oldArr, idx, LSL, 2);
            masm.add(64, newArrCur, newArr, idx, LSL, 2);
            masm.add(64, oldArrCur, oldArrNext, 4);

            // Load 4 words and process
            masm.neon.ld1MultipleV(FullReg, Word, oldElem0, AArch64Address.createStructureNoOffsetAddress(oldArrCur));
            masm.neon.ld1MultipleV(FullReg, Word, oldElem1, AArch64Address.createStructureNoOffsetAddress(oldArrNext));
            masm.neon.ushlVVV(FullReg, Word, oldElem0, oldElem0, shiftVCount);
            masm.neon.ushlVVV(FullReg, Word, oldElem1, oldElem1, shiftVRevCount);
            masm.neon.orrVVV(FullReg, newElem, oldElem0, oldElem1);
            masm.neon.st1MultipleV(FullReg, Word, newElem, AArch64Address.createStructureNoOffsetAddress(newArrCur));

            masm.compare(64, idx, 4);
            masm.branchConditionally(LT, shiftTwoLoop);
            masm.jmp(shiftSIMDLoop);

            masm.bind(shiftTwoLoop);
            masm.cbz(64, idx, exit);
            masm.compare(64, idx, 1);
            masm.branchConditionally(EQ, shiftOne);

            // Calculate the load addresses
            masm.sub(64, idx, idx, 2);
            masm.add(64, oldArrNext, oldArr, idx, LSL, 2);
            masm.add(64, newArrCur, newArr, idx, LSL, 2);
            masm.add(64, oldArrCur, oldArrNext, 4);

            // Load 2 words and process
            masm.neon.ld1MultipleV(HalfReg, Word, oldElem0, AArch64Address.createStructureNoOffsetAddress(oldArrCur));
            masm.neon.ld1MultipleV(HalfReg, Word, oldElem1, AArch64Address.createStructureNoOffsetAddress(oldArrNext));
            masm.neon.ushlVVV(HalfReg, Word, oldElem0, oldElem0, shiftVCount);
            masm.neon.ushlVVV(HalfReg, Word, oldElem1, oldElem1, shiftVRevCount);
            masm.neon.orrVVV(HalfReg, newElem, oldElem0, oldElem1);
            masm.neon.st1MultipleV(HalfReg, Word, newElem, AArch64Address.createStructureNoOffsetAddress(newArrCur));
            masm.jmp(shiftTwoLoop);

            masm.bind(shiftThree);
            masm.tbz(idx, 1, shiftOne);
            masm.tbz(idx, 0, shiftTwo);
            // HotSpot uses r10/r11/r12 here; r10 is scratch-reserved in Graal, so map that scalar role to r20.
            masm.ldr(32, tmp0, createImmediateAddress(32, IMMEDIATE_UNSIGNED_SCALED, oldArr, 12));
            masm.ldr(32, tmp1, createImmediateAddress(32, IMMEDIATE_UNSIGNED_SCALED, oldArr, 8));
            masm.lsr(32, tmp0, tmp0, shiftCount);
            masm.lsl(32, tmp1, tmp1, shiftRevCount);
            masm.orr(32, tmp2, tmp0, tmp1);
            masm.str(32, tmp2, createImmediateAddress(32, IMMEDIATE_UNSIGNED_SCALED, newArr, 8));

            masm.bind(shiftTwo);
            masm.ldr(32, tmp0, createImmediateAddress(32, IMMEDIATE_UNSIGNED_SCALED, oldArr, 8));
            masm.ldr(32, tmp1, createImmediateAddress(32, IMMEDIATE_UNSIGNED_SCALED, oldArr, 4));
            masm.lsr(32, tmp0, tmp0, shiftCount);
            masm.lsl(32, tmp1, tmp1, shiftRevCount);
            masm.orr(32, tmp2, tmp0, tmp1);
            masm.str(32, tmp2, createImmediateAddress(32, IMMEDIATE_UNSIGNED_SCALED, newArr, 4));

            masm.bind(shiftOne);
            masm.ldp(32, tmp1, tmp0, AArch64Address.createPairBaseRegisterOnlyAddress(32, oldArr));
            masm.lsr(32, tmp0, tmp0, shiftCount);
            masm.lsl(32, tmp1, tmp1, shiftRevCount);
            masm.orr(32, tmp2, tmp0, tmp1);
            masm.str(32, tmp2, createBaseRegisterOnlyAddress(32, newArr));

            masm.bind(exit);
        }
    }
}
