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
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEMROp.MOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVD;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm16;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L3074-L3140",
          sha1 = "993d0db47743df12337483bde0ecbc187f5134df")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L7963-L8203",
          sha1 = "48cb921a9c3135b0e4d107500f2ba1a8d128cb75")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/stubRoutines_x86.cpp#L147-L192",
          sha1 = "2bfdfa94c967b9462e15b37e11fda836edf5ef69")
// @formatter:on
@Opcode("AMD64_CRC32C_UPDATE_BYTES")
public final class AMD64CRC32CUpdateBytesOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CRC32CUpdateBytesOp> TYPE = LIRInstructionClass.create(AMD64CRC32CUpdateBytesOp.class);
    private static final int[] CRC32C_PCLMUL_CONSTANTS = {
                    0x35d73a62, 0x0d65762a,
                    0x3babc3e6, 0x1230a27d,
                    0xe55ef1f3, 0x8cfaa965,
    };
    private static final ArrayDataPointerConstant CRC32C_AVX512_TABLE = pointerConstant(16, new int[]{
                    0xb9e02b86, 0x00000000, 0xdcb17aa4, 0x00000000,
                    0x493c7d27, 0x00000000, 0xc1068c50, 0x0000000e,
                    0x06e38d70, 0x00000002, 0x6992cea2, 0x00000000,
                    0x493c7d27, 0x00000000, 0xdd45aab8, 0x00000000,
                    0xdea713f0, 0x00000000, 0x05ec76f0, 0x00000001,
                    0x47db8317, 0x00000000, 0x2ad91c30, 0x00000000,
                    0x0715ce53, 0x00000000, 0xc49f4f67, 0x00000000,
                    0x39d3b296, 0x00000000, 0x083a6eec, 0x00000000,
                    0x9e4addf8, 0x00000000, 0x740eef02, 0x00000000,
                    0xddc0152b, 0x00000000, 0x1c291d04, 0x00000000,
                    0xba4fc28e, 0x00000000, 0x3da6d0cb, 0x00000000,
                    0x493c7d27, 0x00000000, 0xc1068c50, 0x0000000e,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000,
    });
    @Def private AllocatableValue result;
    @Use private AllocatableValue crc;
    @Use private AllocatableValue bufferAddress;
    @Use private AllocatableValue length;

    @Temp private Value[] temps;

    public AMD64CRC32CUpdateBytesOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, AllocatableValue result, AllocatableValue crc, AllocatableValue bufferAddress,
                    AllocatableValue length) {
        super(TYPE);
        this.result = result;
        this.crc = crc;
        this.bufferAddress = bufferAddress;
        this.length = length;
        GraalError.guarantee(result instanceof RegisterValue resultReg && rdi.equals(resultReg.getRegister()), "result should be fixed to rdi, but is %s", result);
        GraalError.guarantee(crc instanceof RegisterValue crcReg && rdi.equals(crcReg.getRegister()), "crc should be fixed to rdi, but is %s", crc);
        GraalError.guarantee(bufferAddress instanceof RegisterValue bufReg && rsi.equals(bufReg.getRegister()), "bufferAddress should be fixed to rsi, but is %s", bufferAddress);
        GraalError.guarantee(length instanceof RegisterValue lenReg && rdx.equals(lenReg.getRegister()), "length should be fixed to rdx, but is %s", length);
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE4_2), "CRC32C update bytes requires SSE4.2 support");
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.CLMUL), "CRC32C update bytes requires CLMUL support");
        if (supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX, CPUFeature.AVX2, CPUFeature.CLMUL, CPUFeature.AVX512F, CPUFeature.AVX512DQ, CPUFeature.AVX512BW,
                        CPUFeature.AVX512VL, CPUFeature.AVX512_VPCLMULQDQ)) {
            this.temps = registersToValues(new Register[]{
                            rax, rcx, rdx, rsi, r9, r10, r11, r12,
                            xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8, xmm10, xmm11, xmm16
            });
        } else {
            this.temps = registersToValues(new Register[]{
                            rax, rcx, rdx, rsi, r9, r10, r11,
                            xmm0, xmm1, xmm2
            });
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        Register crcReg = asRegister(crc);
        Register bufReg = asRegister(bufferAddress);
        Register lenReg = asRegister(length);
        Register tableReg = r9;
        Register posReg = r12;
        Register jReg = r9;

        GraalError.guarantee(masm.supports(CPUFeature.SSE4_2), "CRC32C update bytes requires SSE4.2 support");
        // Graal only emits the CLMUL path. HotSpot's non-CLMUL IPL fallback expects HotSpot's
        // generated CRC32C table layout, so it is deliberately not ported.
        GraalError.guarantee(masm.supports(CPUFeature.CLMUL), "CRC32C update bytes requires CLMUL support");
        GraalError.guarantee(resultReg.equals(crcReg), "result and crc should use the same register, but are %s and %s", resultReg, crcReg);
        Label lContinue = new Label();

        if (masm.supports(CPUFeature.AVX, CPUFeature.AVX2, CPUFeature.CLMUL, CPUFeature.AVX512_VPCLMULQDQ) && masm.supportsFullAVX512()) {
            Label lDoSmall = new Label();
            masm.cmplAndJcc(lenReg, 384, ConditionFlag.LessEqual, lDoSmall, false);
            masm.leaq(tableReg, recordExternalAddress(crb, CRC32C_AVX512_TABLE));
            AMD64CRC32AVX512Helper.emitKernelCRC32Avx512(crb, masm, resultReg, bufReg, lenReg, tableReg, posReg, r11, r10);
            masm.jmp(lContinue);
            masm.bind(lDoSmall);
        }

        emitCRC32CIPLAlg2Alt2(masm, resultReg, bufReg, lenReg, jReg);
        masm.bind(lContinue);
    }

    // Algorithm 2: Pipelined usage of the CRC32 instruction.
    // Input: A buffer I of L bytes.
    // Output: the CRC32C value of the buffer.
    // Notations:
    // Write L = 24N + r, with N = floor (L/24).
    // r = L mod 24 (0 <= r < 24).
    // Consider I as the concatenation of A|B|C|R, where A, B, C, each,
    // N quadwords, and R consists of r bytes.
    // A[j] = I [8j+7:8j], j= 0, 1, ..., N-1
    // B[j] = I [N + 8j+7:N + 8j], j= 0, 1, ..., N-1
    // C[j] = I [2N + 8j+7:2N + 8j], j= 0, 1, ..., N-1
    // if r > 0 R[j] = I [3N +j], j= 0, 1, ...,r-1
    private static void emitCRC32CIPLAlg2Alt2(AMD64MacroAssembler masm, Register crc, Register buf, Register len, Register tmp) {
        Register a = rax;
        Register j = tmp;
        Register k = r10;
        Register l = r11;
        Register y = rcx;

        int[] constOrPreCompConstIndex = CRC32C_PCLMUL_CONSTANTS;

        emitCRC32CProcChunk(masm, 0x800, constOrPreCompConstIndex[0], constOrPreCompConstIndex[1],
                        len, buf, crc,
                        a, j, k,
                        xmm0, xmm1, xmm2,
                        l, y);
        emitCRC32CProcChunk(masm, 0x250, constOrPreCompConstIndex[2], constOrPreCompConstIndex[3],
                        len, buf, crc,
                        a, j, k,
                        xmm0, xmm1, xmm2,
                        l, y);
        emitCRC32CProcChunk(masm, 0x48, constOrPreCompConstIndex[4], constOrPreCompConstIndex[5],
                        len, buf, crc,
                        a, j, k,
                        xmm0, xmm1, xmm2,
                        l, y);

        Label lWordByWord = new Label();
        Label lByteByByteProlog = new Label();
        Label lByteByByte = new Label();
        Label lExit = new Label();

        masm.movl(a, len);
        masm.andl(a, 0x00000007);
        masm.negl(a);
        masm.addl(a, len);
        masm.addq(a, buf);

        masm.cmpqAndJcc(buf, a, ConditionFlag.GreaterEqual, lByteByByteProlog, false);
        masm.align(16);
        masm.bind(lWordByWord);
        masm.crc32q(crc, new AMD64Address(buf, 0));
        masm.addq(buf, 8);
        masm.cmpqAndJcc(buf, a, ConditionFlag.Less, lWordByWord, false);

        masm.bind(lByteByByteProlog);
        masm.andl(len, 0x00000007);
        masm.movl(j, 1);
        masm.cmplAndJcc(j, len, ConditionFlag.Greater, lExit, false);

        masm.bind(lByteByByte);
        masm.crc32b(crc, new AMD64Address(buf, 0));
        masm.incq(buf);
        masm.incl(j);
        masm.cmplAndJcc(j, len, ConditionFlag.LessEqual, lByteByByte, false);

        masm.bind(lExit);
    }

    private static void emitCRC32CPclmul(AMD64MacroAssembler masm,
                    Register wTmp1,
                    Register inOut,
                    int constOrPreCompConstIndex,
                    Register wTmp2,
                    Register tmp1) {
        MOVD.emit(masm, DWORD, wTmp1, inOut);
        masm.movl(tmp1, constOrPreCompConstIndex);
        MOVD.emit(masm, DWORD, wTmp2, tmp1);
        if (masm.supports(CPUFeature.AVX)) {
            masm.vpclmulqdq(wTmp1, wTmp1, wTmp2, 0);
        } else {
            masm.pclmulqdq(wTmp1, wTmp2, 0);
        }
        MOVQ.emit(masm, QWORD, inOut, wTmp1);
    }

    // Recombination Alternative 2: No bit-reflections
    // T1 = (CRC_A * U1) << 1
    // T2 = (CRC_B * U2) << 1
    // C1 = T1 >> 32
    // C2 = T2 >> 32
    // T1 = T1 & 0xFFFFFFFF
    // T2 = T2 & 0xFFFFFFFF
    // T1 = CRC32(0, T1)
    // T2 = CRC32(0, T2)
    // C1 = C1 ^ T1
    // C2 = C2 ^ T2
    // CRC = C1 ^ C2 ^ CRC_C
    private static void emitCRC32CRecAlt2(AMD64MacroAssembler masm,
                    int constOrPreCompConstIndexU1,
                    int constOrPreCompConstIndexU2,
                    Register inOut,
                    Register in1,
                    Register in2,
                    Register wTmp1,
                    Register wTmp2,
                    Register wTmp3,
                    Register tmp1,
                    Register tmp2) {
        emitCRC32CPclmul(masm, wTmp1, inOut, constOrPreCompConstIndexU1, wTmp3, tmp1);
        emitCRC32CPclmul(masm, wTmp2, in1, constOrPreCompConstIndexU2, wTmp3, tmp1);
        masm.shlq(inOut, 1);
        masm.movl(tmp1, inOut);
        masm.shrq(inOut, 32);
        masm.xorl(tmp2, tmp2);
        masm.crc32l(tmp2, tmp1);
        masm.xorl(inOut, tmp2);
        masm.shlq(in1, 1);
        masm.movl(tmp1, in1);
        masm.shrq(in1, 32);
        masm.xorl(tmp2, tmp2);
        masm.crc32l(tmp2, tmp1);
        masm.xorl(in1, tmp2);
        masm.xorl(inOut, in1);
        masm.xorl(inOut, in2);
    }

    // @formatter:off
    // Set N to predefined value.
    // Subtract from a length of a buffer.
    // Execute in a loop:
    // CRC_A = 0xFFFFFFFF, CRC_B = 0, CRC_C = 0
    // for i = 1 to N do
    //  CRC_A = CRC32(CRC_A, A[i])
    //  CRC_B = CRC32(CRC_B, B[i])
    //  CRC_C = CRC32(CRC_C, C[i])
    // end for
    // Recombine.
    // @formatter:on
    private static void emitCRC32CProcChunk(AMD64MacroAssembler masm,
                    int size,
                    int constOrPreCompConstIndexU1,
                    int constOrPreCompConstIndexU2,
                    Register inOut1,
                    Register inOut2,
                    Register inOut3,
                    Register tmp1,
                    Register tmp2,
                    Register tmp3,
                    Register wTmp1,
                    Register wTmp2,
                    Register wTmp3,
                    Register tmp4,
                    Register tmp5) {
        Label lProcessPartitions = new Label();
        Label lProcessPartition = new Label();
        Label lExit = new Label();

        masm.bind(lProcessPartitions);
        masm.cmplAndJcc(inOut1, 3 * size, ConditionFlag.Less, lExit, false);
        masm.xorl(tmp1, tmp1);
        masm.xorl(tmp2, tmp2);
        masm.movq(tmp3, inOut2);
        masm.addq(tmp3, size);

        masm.bind(lProcessPartition);
        masm.crc32q(inOut3, new AMD64Address(inOut2, 0));
        masm.crc32q(tmp1, new AMD64Address(inOut2, size));
        masm.crc32q(tmp2, new AMD64Address(inOut2, size * 2));
        masm.addq(inOut2, 8);
        masm.cmpqAndJcc(inOut2, tmp3, ConditionFlag.Less, lProcessPartition, false);
        emitCRC32CRecAlt2(masm, constOrPreCompConstIndexU1, constOrPreCompConstIndexU2,
                        inOut3, tmp1, tmp2,
                        wTmp1, wTmp2, wTmp3,
                        tmp4, tmp5);
        masm.addq(inOut2, 2 * size);
        masm.subl(inOut1, 3 * size);
        masm.jmp(lProcessPartitions);

        masm.bind(lExit);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
