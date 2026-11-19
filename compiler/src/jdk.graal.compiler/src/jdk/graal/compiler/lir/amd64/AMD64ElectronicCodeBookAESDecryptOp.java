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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding.EVEX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexAESOp.EVAESDEC;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexAESOp.EVAESDECLAST;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVSHUFI64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.keyShuffleMask;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm16;
import static jdk.vm.ci.amd64.AMD64.xmm17;
import static jdk.vm.ci.amd64.AMD64.xmm18;
import static jdk.vm.ci.amd64.AMD64.xmm19;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm20;
import static jdk.vm.ci.amd64.AMD64.xmm21;
import static jdk.vm.ci.amd64.AMD64.xmm22;
import static jdk.vm.ci.amd64.AMD64.xmm27;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm31;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/fe57c1c22de5cad453d507558fafe224874fb212/src/hotspot/cpu/x86/stubGenerator_x86_64_aes.cpp#L2026-L2235",
          sha1 = "d1ec03cac293d612bfbad8dd4fef108120683948")
// @formatter:on
public final class AMD64ElectronicCodeBookAESDecryptOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64ElectronicCodeBookAESDecryptOp> TYPE = LIRInstructionClass.create(AMD64ElectronicCodeBookAESDecryptOp.class);

    private final int lengthOffset;

    @Use({OperandFlag.REG}) protected Value fromValue;
    @Use({OperandFlag.REG}) protected Value toValue;
    @Use({OperandFlag.REG}) protected Value keyValue;
    @Use({OperandFlag.REG}) protected Value lenValue;

    @Def({OperandFlag.REG}) protected Value resultValue;

    @Temp({OperandFlag.REG}) protected Value[] temps;

    public AMD64ElectronicCodeBookAESDecryptOp(AllocatableValue fromValue,
                    AllocatableValue toValue,
                    AllocatableValue keyValue,
                    AllocatableValue lenValue,
                    AllocatableValue resultValue,
                    int lengthOffset) {
        super(TYPE);
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.keyValue = keyValue;
        this.lenValue = lenValue;
        this.resultValue = resultValue;
        this.lengthOffset = lengthOffset;
        GraalError.guarantee(fromValue instanceof RegisterValue fromValueReg && rdi.equals(fromValueReg.getRegister()), "fromValue should be fixed to rdi, but is %s", fromValue);
        GraalError.guarantee(toValue instanceof RegisterValue toValueReg && rsi.equals(toValueReg.getRegister()), "toValue should be fixed to rsi, but is %s", toValue);
        GraalError.guarantee(keyValue instanceof RegisterValue keyValueReg && rdx.equals(keyValueReg.getRegister()), "keyValue should be fixed to rdx, but is %s", keyValue);
        GraalError.guarantee(lenValue instanceof RegisterValue lenValueReg && rcx.equals(lenValueReg.getRegister()), "lenValue should be fixed to rcx, but is %s", lenValue);
        GraalError.guarantee(resultValue instanceof RegisterValue resultValueReg && rax.equals(resultValueReg.getRegister()), "resultValue should be fixed to rax, but is %s", resultValue);
        this.temps = new Value[]{
                        rcx.asValue(),
                        rbx.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        xmm0.asValue(), xmm1.asValue(), xmm2.asValue(), xmm3.asValue(),
                        xmm4.asValue(), xmm5.asValue(), xmm6.asValue(), xmm7.asValue(),
                        xmm8.asValue(), xmm9.asValue(), xmm10.asValue(), xmm11.asValue(),
                        xmm12.asValue(), xmm13.asValue(), xmm14.asValue(), xmm15.asValue(),
                        xmm16.asValue(), xmm17.asValue(), xmm18.asValue(), xmm19.asValue(),
                        xmm20.asValue(), xmm21.asValue(), xmm22.asValue(), xmm27.asValue(),
                        xmm31.asValue(),
                        k1.asValue(),
        };
    }

    private static void loadKeyBroadcast(AMD64MacroAssembler masm, Register zmmDst, Register key, int offset, Register xmmShufMask) {
        AMD64AESEncryptOp.loadKey(masm, zmmDst, key, offset, xmmShufMask);
        EVSHUFI64X2.emit(masm, ZMM, zmmDst, zmmDst, zmmDst, 0);
    }

    private static void roundDecZMM(AMD64MacroAssembler masm, Register keyReg, int lastStateReg) {
        for (int i = 0; i <= lastStateReg; i++) {
            Register stateReg = asXMMRegister(i);
            EVAESDEC.emit(masm, ZMM, stateReg, stateReg, keyReg);
        }
    }

    private static void lastRoundDecZMM(AMD64MacroAssembler masm, Register keyReg, int lastStateReg) {
        for (int i = 0; i <= lastStateReg; i++) {
            Register stateReg = asXMMRegister(i);
            EVAESDECLAST.emit(masm, ZMM, stateReg, stateReg, keyReg);
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Assembler.AMD64SIMDInstructionEncoding oldEncoding = masm.setTemporaryAvxEncoding(EVEX);

        GraalError.guarantee(fromValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid fromValue kind: %s", fromValue);
        GraalError.guarantee(toValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid toValue kind: %s", toValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);

        Register from = asRegister(fromValue);
        Register to = asRegister(toValue);
        Register key = asRegister(keyValue);
        Register lenReg = asRegister(lenValue);
        Register pos = asRegister(resultValue);
        Register rounds = r12;
        Register bulkRounds = r13;

        Label key192 = new Label();
        Label key256 = new Label();
        Label loopStart = new Label();
        Label noParts = new Label();
        Label loop = new Label();
        Label aes192 = new Label();
        Label aes256 = new Label();
        Label endLoop = new Label();
        Label remainder = new Label();
        Label loop2 = new Label();
        Label last2 = new Label();
        Label end = new Label();
        Label epilogue = new Label();

        masm.push(r13);
        masm.push(r12);

        // HotSpot guards this k1 setup on AVX512VL/BW support. Graal only emits this ECB op when
        // the stronger minFeaturesAMD64() gate is already satisfied, so the guard is redundant
        // here.
        // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge context
        // for the registers used, where all instructions below are using 128-bit mode.
        masm.movl(rax, 0xffff);
        masm.kmovq(k1, rax);

        masm.push(lenReg); // Save
        masm.vzeroupper();
        masm.xorq(pos, pos);

        // Calculate number of rounds i.e. based on key length(128, 192, 256):44 for 10-rounds, 52
        // for 12-rounds, 60 for 14-rounds
        masm.movl(rounds, new AMD64Address(key, lengthOffset));

        // Load Key shuf mask
        masm.movdqu(xmm31, recordExternalAddress(crb, keyShuffleMask));

        // Load and shuffle round keys. The java expanded key ordering is rotated one position in
        // decryption.
        // So the first round key is loaded from 1*16 here and last round key is loaded from 0*16
        loadKeyBroadcast(masm, xmm9, key, 0x10, xmm31);
        loadKeyBroadcast(masm, xmm10, key, 0x20, xmm31);
        loadKeyBroadcast(masm, xmm11, key, 0x30, xmm31);
        loadKeyBroadcast(masm, xmm12, key, 0x40, xmm31);
        loadKeyBroadcast(masm, xmm13, key, 0x50, xmm31);
        loadKeyBroadcast(masm, xmm14, key, 0x60, xmm31);
        loadKeyBroadcast(masm, xmm15, key, 0x70, xmm31);
        loadKeyBroadcast(masm, xmm16, key, 0x80, xmm31);
        loadKeyBroadcast(masm, xmm17, key, 0x90, xmm31);
        loadKeyBroadcast(masm, xmm18, key, 0xa0, xmm31);
        loadKeyBroadcast(masm, xmm27, key, 0x00, xmm31);
        masm.cmplAndJcc(rounds, 52, ConditionFlag.GreaterEqual, key192, false);
        masm.jmp(loopStart);

        masm.bind(key192);
        loadKeyBroadcast(masm, xmm19, key, 0xb0, xmm31);
        loadKeyBroadcast(masm, xmm20, key, 0xc0, xmm31);
        masm.cmplAndJcc(rounds, 60, ConditionFlag.Equal, key256, false);
        masm.jmp(loopStart);

        masm.bind(key256);
        loadKeyBroadcast(masm, xmm21, key, 0xd0, xmm31);
        loadKeyBroadcast(masm, xmm22, key, 0xe0, xmm31);

        masm.bind(loopStart);
        masm.movq(rbx, lenReg);
        // Convert input length to number of blocks
        masm.shrq(lenReg, 4);
        masm.shlq(rbx, 60);
        masm.jcc(ConditionFlag.Equal, noParts);
        masm.addq(lenReg, 1);

        // Check if number of blocks is greater than/ equal to 32
        // If true, blocks then 512 bytes are processed at a time (code marked by label LOOP)
        // If not, 16 bytes are processed (code marked by label REMAINDER)

        masm.bind(noParts);
        masm.movq(rbx, lenReg);
        masm.shrq(lenReg, 5);
        masm.jcc(ConditionFlag.Equal, remainder);
        masm.movl(bulkRounds, lenReg);
        // Compute number of blocks that will be processed as 512 bytes at a time
        // Subtract this from the total number of blocks, which will then be processed by REMAINDER
        // loop.
        masm.shlq(bulkRounds, 5);
        masm.subq(rbx, bulkRounds);

        masm.bind(loop);
        // Move 64 bytes of CT data into a zmm register, as a result 512 bytes of CT loaded in
        // zmm0-7
        masm.evmovdqu64(xmm0, new AMD64Address(from, pos, Stride.S1, 0 * 64));
        masm.evmovdqu64(xmm1, new AMD64Address(from, pos, Stride.S1, 1 * 64));
        masm.evmovdqu64(xmm2, new AMD64Address(from, pos, Stride.S1, 2 * 64));
        masm.evmovdqu64(xmm3, new AMD64Address(from, pos, Stride.S1, 3 * 64));
        masm.evmovdqu64(xmm4, new AMD64Address(from, pos, Stride.S1, 4 * 64));
        masm.evmovdqu64(xmm5, new AMD64Address(from, pos, Stride.S1, 5 * 64));
        masm.evmovdqu64(xmm6, new AMD64Address(from, pos, Stride.S1, 6 * 64));
        masm.evmovdqu64(xmm7, new AMD64Address(from, pos, Stride.S1, 7 * 64));
        // Xor with the first round key
        EVPXORQ.emit(masm, ZMM, xmm0, xmm0, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm1, xmm1, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm2, xmm2, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm3, xmm3, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm4, xmm4, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm5, xmm5, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm6, xmm6, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm7, xmm7, xmm9);
        // 9 rounds of Aesdec
        roundDecZMM(masm, xmm10, 7);
        roundDecZMM(masm, xmm11, 7);
        roundDecZMM(masm, xmm12, 7);
        roundDecZMM(masm, xmm13, 7);
        roundDecZMM(masm, xmm14, 7);
        roundDecZMM(masm, xmm15, 7);
        roundDecZMM(masm, xmm16, 7);
        roundDecZMM(masm, xmm17, 7);
        roundDecZMM(masm, xmm18, 7);
        masm.cmplAndJcc(rounds, 52, ConditionFlag.AboveEqual, aes192, false);
        // Aesdeclast round for keysize = 128
        lastRoundDecZMM(masm, xmm27, 7);
        masm.jmp(endLoop);

        masm.bind(aes192);
        // 2 Additional rounds for keysize = 192
        roundDecZMM(masm, xmm19, 7);
        roundDecZMM(masm, xmm20, 7);
        masm.cmplAndJcc(rounds, 60, ConditionFlag.AboveEqual, aes256, false);
        // Aesdeclast round for keysize = 192
        lastRoundDecZMM(masm, xmm27, 7);
        masm.jmp(endLoop);

        masm.bind(aes256);
        // 2 Additional rounds and Aesdeclast for keysize = 256
        roundDecZMM(masm, xmm21, 7);
        roundDecZMM(masm, xmm22, 7);
        lastRoundDecZMM(masm, xmm27, 7);

        masm.bind(endLoop);
        // Write 512 bytes of PT to the destination
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 0 * 64), xmm0);
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 1 * 64), xmm1);
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 2 * 64), xmm2);
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 3 * 64), xmm3);
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 4 * 64), xmm4);
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 5 * 64), xmm5);
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 6 * 64), xmm6);
        masm.evmovdqu64(new AMD64Address(to, pos, Stride.S1, 7 * 64), xmm7);
        masm.addq(pos, 512);
        masm.decq(lenReg);
        masm.jcc(ConditionFlag.NotEqual, loop);

        masm.bind(remainder);
        masm.vzeroupper();
        masm.cmpqAndJcc(rbx, 0, ConditionFlag.Equal, end, false);

        // Process 16 bytes at a time
        masm.bind(loop2);
        masm.movdqu(xmm1, new AMD64Address(from, pos, Stride.S1, 0));
        VPXOR.encoding(EVEX).emit(masm, XMM, xmm1, xmm1, xmm9);
        // xmm2 contains shuffled key for Aesdeclast operation.
        EVMOVDQU32.emit(masm, YMM, xmm2, xmm27);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm10);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm11);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm12);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm13);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm14);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm15);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm16);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm17);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm18);
        masm.cmplAndJcc(rounds, 52, ConditionFlag.Below, last2, false);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm19);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm20);
        masm.cmplAndJcc(rounds, 60, ConditionFlag.Below, last2, false);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm21);
        EVAESDEC.emit(masm, XMM, xmm1, xmm1, xmm22);
        masm.bind(last2);
        EVAESDECLAST.emit(masm, XMM, xmm1, xmm1, xmm2);

        // Write 16 bytes of PT to destination
        masm.movdqu(new AMD64Address(to, pos, Stride.S1, 0), xmm1);
        masm.addq(pos, 16);
        masm.decq(rbx);
        masm.jcc(ConditionFlag.NotEqual, loop2);

        masm.bind(end);
        // Zero out the round keys
        EVPXORQ.emit(masm, ZMM, xmm8, xmm8, xmm8);
        EVPXORQ.emit(masm, ZMM, xmm9, xmm9, xmm9);
        EVPXORQ.emit(masm, ZMM, xmm10, xmm10, xmm10);
        EVPXORQ.emit(masm, ZMM, xmm11, xmm11, xmm11);
        EVPXORQ.emit(masm, ZMM, xmm12, xmm12, xmm12);
        EVPXORQ.emit(masm, ZMM, xmm13, xmm13, xmm13);
        EVPXORQ.emit(masm, ZMM, xmm14, xmm14, xmm14);
        EVPXORQ.emit(masm, ZMM, xmm15, xmm15, xmm15);
        EVPXORQ.emit(masm, ZMM, xmm16, xmm16, xmm16);
        EVPXORQ.emit(masm, ZMM, xmm17, xmm17, xmm17);
        EVPXORQ.emit(masm, ZMM, xmm18, xmm18, xmm18);
        EVPXORQ.emit(masm, ZMM, xmm27, xmm27, xmm27);
        masm.cmplAndJcc(rounds, 44, ConditionFlag.BelowEqual, epilogue, false);
        EVPXORQ.emit(masm, ZMM, xmm19, xmm19, xmm19);
        EVPXORQ.emit(masm, ZMM, xmm20, xmm20, xmm20);
        masm.cmplAndJcc(rounds, 52, ConditionFlag.BelowEqual, epilogue, false);
        EVPXORQ.emit(masm, ZMM, xmm21, xmm21, xmm21);
        EVPXORQ.emit(masm, ZMM, xmm22, xmm22, xmm22);

        masm.bind(epilogue);
        masm.pop(rax); // return length
        masm.pop(r12);
        masm.pop(r13);
        masm.resetAvxEncoding(oldEncoding);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
