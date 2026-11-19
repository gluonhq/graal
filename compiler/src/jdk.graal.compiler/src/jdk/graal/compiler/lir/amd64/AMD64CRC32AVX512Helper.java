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

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVDQU;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVBROADCASTI32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPCLMULQDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVSHUFI64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPCLMULQDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPAND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMROp.VPBLENDVB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftImmOp.VPSRLDQ;
import static jdk.graal.compiler.core.common.Stride.S1;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm16;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEMROp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;

/**
 * Shared Java port of HotSpot's AVX512 CRC32 kernel. HotSpot calls the same
 * {@code MacroAssembler::kernel_crc32_avx512} routine for CRC32 and CRC32C, passing a
 * variant-specific precomputed constant table.
 */
final class AMD64CRC32AVX512Helper {
    private static final int AVX512_RK_MINUS1_MINUS2_OFFSET = 0;
    private static final int AVX512_RK1_RK2_OFFSET = 16;
    private static final int AVX512_RK3_RK4_OFFSET = 32;
    private static final int AVX512_BARRETT_64_32_OFFSET = 48;
    private static final int AVX512_BARRETT_CONST_OFFSET = 64;
    private static final int AVX512_RK9_RK16_OFFSET = 80;
    private static final int AVX512_RK17_RK20_RK1_RK2_OFFSET = 144;

    private static final ArrayDataPointerConstant CRC_BY128_MASKS_AVX512 = pointerConstant(16, new int[]{
                    0xffffffff, 0xffffffff, 0x00000000, 0x00000000,
    });
    private static final ArrayDataPointerConstant CRC_BY128_MASKS_AVX512_16 = pointerConstant(16, new int[]{
                    0x00000000, 0xffffffff, 0xffffffff, 0xffffffff,
    });
    private static final ArrayDataPointerConstant CRC_BY128_MASKS_AVX512_32 = pointerConstant(16, new int[]{
                    0x80808080, 0x80808080, 0x80808080, 0x80808080,
    });

    private static final ArrayDataPointerConstant SHUF_TABLE_CRC32_AVX512 = pointerConstant(16, new int[]{
                    0x83828100, 0x87868584, 0x8b8a8988, 0x8f8e8d8c,
                    0x03020100, 0x07060504, 0x0b0a0908, 0x000e0d0c,
    });

    private AMD64CRC32AVX512Helper() {
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L7595-L7604",
              sha1 = "46b5a17e60e36cf54714f61a9cdad5e1ac04ec6c")
    // @formatter:on
    private static void fold512BitCRC32Avx512(AMD64MacroAssembler masm, Register xcrc, Register xk, Register xtmp, Register buf, Register pos, int offset) {
        masm.evmovdqu64(xmm3, new AMD64Address(buf, pos, S1, offset));
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xtmp, xcrc, xk, 0x10); // [123:64]
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm2, xcrc, xk, 0x01); // [63:0]
        EVPXORQ.emit(masm, AVXSize.ZMM, xcrc, xtmp, xmm2);
        EVPXORQ.emit(masm, AVXSize.ZMM, xcrc, xcrc, xmm3);
    }

    // @formatter:off
    /**
     * Compute CRC32 using AVX512 instructions
     *
     * @param crc   register containing existing CRC (32-bit)
     * @param buf   register pointing to input byte buffer (byte*)
     * @param len   register containing number of bytes
     * @param table address of crc or crc32c table
     * @param tmp1  scratch register
     * @param tmp2  scratch register
     *
     * This routine is identical for crc32c with the exception of the precomputed constant
     * table which will be passed as the table argument. The calculation steps are the same
     * for both variants.
     */
    // @formatter:on
    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L7763-L7961",
              sha1 = "85ab2d3a4bc4e2c9026c073e98d953101f714c8a")
    // @formatter:on
    static void emitKernelCRC32Avx512(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Register crc, Register buf, Register len, Register table, Register pos, Register tmp1, Register tmp2) {
        Label lLessThan256 = new Label();
        Label lFold256BLoop = new Label();
        Label lFold128BLoop = new Label();
        Label lFold128BRegister = new Label();
        Label lFinalReductionFor128 = new Label();
        Label l16BReductionLoop = new Label();
        Label lGetLastTwoXmms = new Label();
        Label l128Done = new Label();
        Label lBarrett = new Label();
        Label lExit = new Label();

        masm.push(pos);
        masm.subq(rsp, 16 * 2 + 8);

        // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
        // context for the registers used, where all instructions below are using 128-bit mode
        // On EVEX without VL and BW, these instructions will all be AVX.
        masm.movl(pos, 0);

        // check if smaller than 256B
        masm.cmplAndJcc(len, 256, ConditionFlag.Less, lLessThan256, false);

        // load the initial crc value
        MOVD.emit(masm, DWORD, xmm10, crc);

        // receive the initial 64B data, xor the initial crc value
        masm.evmovdqu64(xmm0, new AMD64Address(buf, pos, S1, 0));
        masm.evmovdqu64(xmm4, new AMD64Address(buf, pos, S1, 64));
        EVPXORQ.emit(masm, AVXSize.ZMM, xmm0, xmm0, xmm10);
        // zmm10 has rk3 and rk4
        EVBROADCASTI32X4.emit(masm, AVXSize.ZMM, xmm10, new AMD64Address(table, AVX512_RK3_RK4_OFFSET));

        masm.subl(len, 256);
        masm.cmplAndJcc(len, 256, ConditionFlag.Less, lFold128BLoop, false);

        masm.evmovdqu64(xmm7, new AMD64Address(buf, pos, S1, 128));
        masm.evmovdqu64(xmm8, new AMD64Address(buf, pos, S1, 192));
        // zmm16 has rk-1 and rk-2
        EVBROADCASTI32X4.emit(masm, AVXSize.ZMM, xmm16, new AMD64Address(table, AVX512_RK_MINUS1_MINUS2_OFFSET));
        masm.subl(len, 256);

        masm.bind(lFold256BLoop);
        masm.addl(pos, 256);
        fold512BitCRC32Avx512(masm, xmm0, xmm16, xmm1, buf, pos, 0);
        fold512BitCRC32Avx512(masm, xmm4, xmm16, xmm1, buf, pos, 64);
        fold512BitCRC32Avx512(masm, xmm7, xmm16, xmm1, buf, pos, 128);
        fold512BitCRC32Avx512(masm, xmm8, xmm16, xmm1, buf, pos, 192);
        masm.subl(len, 256);
        masm.jcc(ConditionFlag.GreaterEqual, lFold256BLoop);

        // Fold 256 into 128
        masm.addl(pos, 256);
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm1, xmm0, xmm10, 0x01);
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm2, xmm0, xmm10, 0x10);
        masm.evpternlogq(xmm7, 0x96, xmm1, xmm2); // xor ABC

        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm5, xmm4, xmm10, 0x01);
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm6, xmm4, xmm10, 0x10);
        masm.evpternlogq(xmm8, 0x96, xmm5, xmm6); // xor ABC

        EVMOVDQU64.emit(masm, AVXSize.ZMM, xmm0, xmm7);
        EVMOVDQU64.emit(masm, AVXSize.ZMM, xmm4, xmm8);
        masm.addl(len, 128);
        masm.jmp(lFold128BRegister);

        // at this section of the code, there is 128 * x + y(0 <= y<128) bytes of buffer.The
        // fold_128_B_loop
        // loop will fold 128B at a time until we have 128 + y Bytes of buffer

        // fold 128B at a time.This section of the code folds 8 xmm registers in parallel
        masm.bind(lFold128BLoop);
        masm.addl(pos, 128);
        fold512BitCRC32Avx512(masm, xmm0, xmm10, xmm1, buf, pos, 0);
        fold512BitCRC32Avx512(masm, xmm4, xmm10, xmm1, buf, pos, 64);
        masm.subl(len, 128);
        masm.jcc(ConditionFlag.GreaterEqual, lFold128BLoop);

        masm.addl(pos, 128);

        // at this point, the buffer pointer is pointing at the last y Bytes of the buffer, where
        // 0 <= y < 128
        // the 128B of folded data is in 8 of the xmm registers : xmm0, xmm1, xmm2, xmm3, xmm4,
        // xmm5, xmm6, xmm7
        masm.bind(lFold128BRegister);
        // multiply by rk9-rk16
        masm.evmovdqu64(xmm16, new AMD64Address(table, AVX512_RK9_RK16_OFFSET));
        // multiply by rk17-rk20, rk1,rk2, 0,0
        masm.evmovdqu64(xmm11, new AMD64Address(table, AVX512_RK17_RK20_RK1_RK2_OFFSET));
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm1, xmm0, xmm16, 0x01);
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm2, xmm0, xmm16, 0x10);

        // save last that has no multiplicand
        EVEXTRACTI64X2.emit(masm, AVXSize.ZMM, xmm7, xmm4, 0x3);
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm5, xmm4, xmm11, 0x01);
        EVPCLMULQDQ.emit(masm, AVXSize.ZMM, xmm6, xmm4, xmm11, 0x10);

        // Needed later in reduction loop
        MOVDQU.emit(masm, SS, xmm10, new AMD64Address(table, AVX512_RK1_RK2_OFFSET));
        masm.evpternlogq(xmm1, 0x96, xmm2, xmm5); // xor ABC
        masm.evpternlogq(xmm1, 0x96, xmm6, xmm7); // xor ABC

        // Swap 1,0,3,2 - 01 00 11 10
        EVSHUFI64X2.emit(masm, AVXSize.ZMM, xmm8, xmm1, xmm1, 0x4e);
        EVPXORQ.emit(masm, AVXSize.YMM, xmm8, xmm8, xmm1);
        VEXTRACTI128.emit(masm, AVXSize.YMM, xmm5, xmm8, 0x1);
        EVPXORQ.emit(masm, AVXSize.XMM, xmm7, xmm5, xmm8);

        // instead of 128, we add 128 - 16 to the loop counter to save 1 instruction from the loop
        // instead of a cmp instruction, we use the negative flag with the jl instruction
        masm.addl(len, 128 - 16);
        masm.jcc(ConditionFlag.Less, lFinalReductionFor128);

        masm.bind(l16BReductionLoop);
        masm.vpclmulqdq(xmm8, xmm7, xmm10, 0x01);
        masm.vpclmulqdq(xmm7, xmm7, xmm10, 0x10);
        masm.vpxor(xmm7, xmm7, xmm8, AVXSize.XMM);
        MOVDQU.emit(masm, SS, xmm0, new AMD64Address(buf, pos, S1, 0));
        masm.vpxor(xmm7, xmm7, xmm0, AVXSize.XMM);
        masm.addl(pos, 16);
        masm.subl(len, 16);
        masm.jcc(ConditionFlag.GreaterEqual, l16BReductionLoop);

        masm.bind(lFinalReductionFor128);
        masm.addl(len, 16);
        masm.jcc(ConditionFlag.Equal, l128Done);

        masm.bind(lGetLastTwoXmms);
        MOVDQU.emit(masm, SS, xmm2, xmm7);
        masm.addl(pos, len);
        MOVDQU.emit(masm, SS, xmm1, new AMD64Address(buf, pos, S1, -16));
        masm.subl(pos, len);

        // get rid of the extra data that was loaded before
        // load the shift constant
        masm.leaq(rax, recordExternalAddress(crb, SHUF_TABLE_CRC32_AVX512));
        MOVDQU.emit(masm, SS, xmm0, new AMD64Address(rax, len, S1, 0));
        masm.addl(rax, len);

        masm.vpshufb(xmm7, xmm7, xmm0, AVXSize.XMM);
        // Change mask to 512
        masm.vpxor(xmm0, xmm0, recordExternalAddress(crb, CRC_BY128_MASKS_AVX512_32), AVXSize.XMM);
        masm.vpshufb(xmm2, xmm2, xmm0, AVXSize.XMM);
        VPBLENDVB.emit(masm, AVXSize.XMM, xmm2, xmm0, xmm2, xmm1);
        masm.vpclmulqdq(xmm8, xmm7, xmm10, 0x01);
        masm.vpclmulqdq(xmm7, xmm7, xmm10, 0x10);
        masm.vpxor(xmm7, xmm7, xmm8, AVXSize.XMM);
        masm.vpxor(xmm7, xmm7, xmm2, AVXSize.XMM);

        masm.bind(l128Done);

        // compute crc of a 128-bit value
        MOVDQU.emit(masm, SS, xmm10, new AMD64Address(table, AVX512_BARRETT_64_32_OFFSET));
        MOVDQU.emit(masm, SS, xmm0, xmm7);

        // 64b fold
        VPCLMULQDQ.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm10, 0x00);
        VPSRLDQ.emit(masm, AVXSize.XMM, xmm0, xmm0, 8);
        masm.vpxor(xmm7, xmm7, xmm0, AVXSize.XMM);

        // 32b fold
        MOVDQU.emit(masm, SS, xmm0, xmm7);
        masm.vpslldq(xmm7, xmm7, 4, AVXSize.XMM);
        VPCLMULQDQ.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm10, 0x10);
        masm.vpxor(xmm7, xmm7, xmm0, AVXSize.XMM);
        masm.jmp(lBarrett);

        masm.bind(lLessThan256);
        emitKernelCRC32Avx512ForLessThan256(crb, masm, crc, buf, len, table, pos, tmp1, tmp2, lBarrett, l16BReductionLoop, lGetLastTwoXmms, l128Done, lExit);

        // barrett reduction
        masm.bind(lBarrett);
        VPAND.emit(masm, AVXSize.XMM, xmm7, xmm7, recordExternalAddress(crb, CRC_BY128_MASKS_AVX512_16));
        MOVDQU.emit(masm, SS, xmm1, xmm7);
        MOVDQU.emit(masm, SS, xmm2, xmm7);
        MOVDQU.emit(masm, SS, xmm10, new AMD64Address(table, AVX512_BARRETT_CONST_OFFSET));

        VPCLMULQDQ.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm10, 0x00);
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm2);
        VPAND.emit(masm, AVXSize.XMM, xmm7, xmm7, recordExternalAddress(crb, CRC_BY128_MASKS_AVX512));
        MOVDQU.emit(masm, SS, xmm2, xmm7);
        VPCLMULQDQ.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm10, 0x10);
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm2);
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm1);
        VPEXTRD.emit(masm, AVXSize.XMM, crc, xmm7, 0x2);

        masm.bind(lExit);
        masm.addq(rsp, 16 * 2 + 8);
        masm.pop(pos);
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L7606-L7761",
              sha1 = "2a60368e2d46d7e6cebe037d9f4f6a41007e32be")
    // @formatter:on
    private static void emitKernelCRC32Avx512ForLessThan256(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Register crc, Register buf, Register len, Register table, Register pos, Register tmp1, Register tmp2,
                    Label lBarrett, Label l16BReductionLoop, Label lGetLastTwoXmms, Label l128Done, Label lExit) {
        Label lLessThan32 = new Label();
        Label lExact16Left = new Label();
        Label lLessThan16Left = new Label();
        Label lLessThan8Left = new Label();
        Label lLessThan4Left = new Label();
        Label lLessThan2Left = new Label();
        Label lZeroLeft = new Label();
        Label lOnlyLessThan4 = new Label();
        Label lOnlyLessThan3 = new Label();
        Label lOnlyLessThan2 = new Label();

        // check if there is enough buffer to be able to fold 16B at a time
        masm.cmplAndJcc(len, 32, ConditionFlag.Less, lLessThan32, false);

        // if there is, load the constants
        // rk1 and rk2 in xmm10
        MOVDQU.emit(masm, SS, xmm10, new AMD64Address(table, AVX512_RK1_RK2_OFFSET));
        MOVD.emit(masm, DWORD, xmm0, crc); // get the initial crc value
        MOVDQU.emit(masm, SS, xmm7, new AMD64Address(buf, pos, S1, 0)); // load the plaintext
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm0);

        // update the buffer pointer
        masm.addl(pos, 16);
        // update the counter.subtract 32 instead of 16 to save one instruction from the loop
        masm.subl(len, 32);
        masm.jmp(l16BReductionLoop);

        masm.bind(lLessThan32);
        // mov initial crc to the return value. this is necessary for zero - length buffers.
        masm.movl(rax, crc);
        masm.testlAndJcc(len, len, ConditionFlag.Zero, lExit, false);
        MOVD.emit(masm, DWORD, xmm0, crc); // get the initial crc value

        masm.cmpl(len, 16);
        masm.jcc(ConditionFlag.Equal, lExact16Left);
        masm.jcc(ConditionFlag.Less, lLessThan16Left);

        MOVDQU.emit(masm, SS, xmm7, new AMD64Address(buf, pos, S1, 0)); // load the plaintext
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm0); // xor the initial crc value
        masm.addl(pos, 16);
        masm.subl(len, 16);
        // rk1 and rk2 in xmm10
        MOVDQU.emit(masm, SS, xmm10, new AMD64Address(table, AVX512_RK1_RK2_OFFSET));
        masm.jmp(lGetLastTwoXmms);

        masm.bind(lLessThan16Left);
        // use stack space to load data less than 16 bytes, zero - out the 16B in memory first.
        VPXOR.emit(masm, AVXSize.XMM, xmm1, xmm1, xmm1);
        masm.movq(tmp1, rsp);
        SSEMROp.MOVDQU.emit(masm, SS, new AMD64Address(tmp1, 0), xmm1);

        masm.cmplAndJcc(len, 4, ConditionFlag.Less, lOnlyLessThan4, false);

        // backup the counter value
        masm.movl(tmp2, len);
        masm.cmplAndJcc(len, 8, ConditionFlag.Less, lLessThan8Left, false);

        // load 8 Bytes
        masm.movq(rax, new AMD64Address(buf, pos, S1, 0));
        masm.movq(new AMD64Address(tmp1, 0), rax);
        masm.addq(tmp1, 8);
        masm.subl(len, 8);
        masm.addl(pos, 8);

        masm.bind(lLessThan8Left);
        masm.cmplAndJcc(len, 4, ConditionFlag.Less, lLessThan4Left, false);

        // load 4 Bytes
        masm.movl(rax, new AMD64Address(buf, pos, S1, 0));
        masm.movl(new AMD64Address(tmp1, 0), rax);
        masm.addq(tmp1, 4);
        masm.subl(len, 4);
        masm.addl(pos, 4);

        masm.bind(lLessThan4Left);
        masm.cmplAndJcc(len, 2, ConditionFlag.Less, lLessThan2Left, false);

        // load 2 Bytes
        masm.movw(rax, new AMD64Address(buf, pos, S1, 0));
        masm.movl(new AMD64Address(tmp1, 0), rax);
        masm.addq(tmp1, 2);
        masm.subl(len, 2);
        masm.addl(pos, 2);

        masm.bind(lLessThan2Left);
        masm.cmplAndJcc(len, 1, ConditionFlag.Less, lZeroLeft, false);

        // load 1 Byte
        masm.movb(rax, new AMD64Address(buf, pos, S1, 0));
        masm.movb(new AMD64Address(tmp1, 0), rax);

        masm.bind(lZeroLeft);
        MOVDQU.emit(masm, SS, xmm7, new AMD64Address(rsp, 0));
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm0); // xor the initial crc value

        masm.leaq(rax, recordExternalAddress(crb, SHUF_TABLE_CRC32_AVX512));
        MOVDQU.emit(masm, SS, xmm0, new AMD64Address(rax, tmp2, S1, 0));
        masm.vpshufb(xmm7, xmm7, xmm0, AVXSize.XMM);
        masm.jmp(l128Done);

        masm.bind(lExact16Left);
        MOVDQU.emit(masm, SS, xmm7, new AMD64Address(buf, pos, S1, 0));
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm0); // xor the initial crc value
        masm.jmp(l128Done);

        masm.bind(lOnlyLessThan4);
        masm.cmplAndJcc(len, 3, ConditionFlag.Less, lOnlyLessThan3, false);

        // load 3 Bytes
        masm.movb(rax, new AMD64Address(buf, pos, S1, 0));
        masm.movb(new AMD64Address(tmp1, 0), rax);
        masm.movb(rax, new AMD64Address(buf, pos, S1, 1));
        masm.movb(new AMD64Address(tmp1, 1), rax);
        masm.movb(rax, new AMD64Address(buf, pos, S1, 2));
        masm.movb(new AMD64Address(tmp1, 2), rax);

        MOVDQU.emit(masm, SS, xmm7, new AMD64Address(rsp, 0));
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm0); // xor the initial crc value
        masm.vpslldq(xmm7, xmm7, 5, AVXSize.XMM);
        masm.jmp(lBarrett);

        masm.bind(lOnlyLessThan3);
        masm.cmplAndJcc(len, 2, ConditionFlag.Less, lOnlyLessThan2, false);

        // load 2 Bytes
        masm.movb(rax, new AMD64Address(buf, pos, S1, 0));
        masm.movb(new AMD64Address(tmp1, 0), rax);
        masm.movb(rax, new AMD64Address(buf, pos, S1, 1));
        masm.movb(new AMD64Address(tmp1, 1), rax);

        MOVDQU.emit(masm, SS, xmm7, new AMD64Address(rsp, 0));
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm0); // xor the initial crc value
        masm.vpslldq(xmm7, xmm7, 6, AVXSize.XMM);
        masm.jmp(lBarrett);

        masm.bind(lOnlyLessThan2);
        // load 1 Byte
        masm.movb(rax, new AMD64Address(buf, pos, S1, 0));
        masm.movb(new AMD64Address(tmp1, 0), rax);

        MOVDQU.emit(masm, SS, xmm7, new AMD64Address(rsp, 0));
        VPXOR.emit(masm, AVXSize.XMM, xmm7, xmm7, xmm0); // xor the initial crc value
        masm.vpslldq(xmm7, xmm7, 7, AVXSize.XMM);
    }
}
