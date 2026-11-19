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
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEMROp.MOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSERMIOp.PINSRD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVDQA;
import static jdk.graal.compiler.core.common.Stride.S4;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm16;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L3016-L3059",
          sha1 = "f22b58ea19d4d63fff534d39241df8ab020f32b5")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L7374-L7593",
          sha1 = "8ca1f1d508a9db4f9fe15a4bcd592dbf99a85b44")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/stubRoutines_x86.cpp#L52-L145",
          sha1 = "627db4bce4af5618a76b9e402649d374ad8ae402")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/x86/stubRoutines_x86.cpp#L147-L192",
          sha1 = "2bfdfa94c967b9462e15b37e11fda836edf5ef69")
// @formatter:on
@Opcode("AMD64_CRC32_UPDATE_BYTES")
public final class AMD64CRC32UpdateBytesOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CRC32UpdateBytesOp> TYPE = LIRInstructionClass.create(AMD64CRC32UpdateBytesOp.class);
    private static final ArrayDataPointerConstant CRC32_TABLE = pointerConstant(16, new int[]{
                    0x00000000, 0x77073096, 0xee0e612c, 0x990951ba, 0x076dc419, 0x706af48f, 0xe963a535, 0x9e6495a3,
                    0x0edb8832, 0x79dcb8a4, 0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd, 0xe7b82d07, 0x90bf1d91,
                    0x1db71064, 0x6ab020f2, 0xf3b97148, 0x84be41de, 0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7,
                    0x136c9856, 0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f, 0x63066cd9, 0xfa0f3d63, 0x8d080df5,
                    0x3b6e20c8, 0x4c69105e, 0xd56041e4, 0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd, 0xa50ab56b,
                    0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940, 0x32d86ce3, 0x45df5c75, 0xdcd60dcf, 0xabd13d59,
                    0x26d930ac, 0x51de003a, 0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423, 0xcfba9599, 0xb8bda50f,
                    0x2802b89e, 0x5f058808, 0xc60cd9b2, 0xb10be924, 0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d,
                    0x76dc4190, 0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589, 0x06b6b51f, 0x9fbfe4a5, 0xe8b8d433,
                    0x7807c9a2, 0x0f00f934, 0x9609a88e, 0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97, 0xe6635c01,
                    0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e, 0x6c0695ed, 0x1b01a57b, 0x8208f4c1, 0xf50fc457,
                    0x65b0d9c6, 0x12b7e950, 0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49, 0x8cd37cf3, 0xfbd44c65,
                    0x4db26158, 0x3ab551ce, 0xa3bc0074, 0xd4bb30e2, 0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb,
                    0x4369e96a, 0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73, 0x33031de5, 0xaa0a4c5f, 0xdd0d7cc9,
                    0x5005713c, 0x270241aa, 0xbe0b1010, 0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409, 0xce61e49f,
                    0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4, 0x59b33d17, 0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad,
                    0xedb88320, 0x9abfb3b6, 0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af, 0x04db2615, 0x73dc1683,
                    0xe3630b12, 0x94643b84, 0x0d6d6a3e, 0x7a6a5aa8, 0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1,
                    0xf00f9344, 0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d, 0x806567cb, 0x196c3671, 0x6e6b06e7,
                    0xfed41b76, 0x89d32be0, 0x10da7a5a, 0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43, 0x60b08ed5,
                    0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252, 0xd1bb67f1, 0xa6bc5767, 0x3fb506dd, 0x48b2364b,
                    0xd80d2bda, 0xaf0a1b4c, 0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55, 0x316e8eef, 0x4669be79,
                    0xcb61b38c, 0xbc66831a, 0x256fd2a0, 0x5268e236, 0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f,
                    0xc5ba3bbe, 0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7, 0xb5d0cf31, 0x2cd99e8b, 0x5bdeae1d,
                    0x9b64c2b0, 0xec63f226, 0x756aa39c, 0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785, 0x05005713,
                    0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38, 0x92d28e9b, 0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21,
                    0x86d3d2d4, 0xf1d4e242, 0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b, 0x6fb077e1, 0x18b74777,
                    0x88085ae6, 0xff0f6a70, 0x66063bca, 0x11010b5c, 0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45,
                    0xa00ae278, 0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661, 0xd06016f7, 0x4969474d, 0x3e6e77db,
                    0xaed16a4a, 0xd9d65adc, 0x40df0b66, 0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f, 0x30b5ffe9,
                    0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6, 0xbad03605, 0xcdd70693, 0x54de5729, 0x23d967bf,
                    0xb3667a2e, 0xc4614ab8, 0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1, 0x5a05df1b, 0x2d02ef8d
    });

    private static final ArrayDataPointerConstant CRC_BY128_MASKS = pointerConstant(16, new long[]{
                    0xffffffffL,
                    (0xb1e6b092L << 1)
    });
    private static final ArrayDataPointerConstant CRC_BY128_MASKS_16 = pointerConstant(16, new long[]{
                    (0xba8ccbe8L << 1),
                    (0x6655004fL << 1)
    });
    private static final ArrayDataPointerConstant CRC_BY128_MASKS_32 = pointerConstant(16, new long[]{
                    (0xaa2215eaL << 1),
                    (0xe3720acbL << 1)
    });
    private static final ArrayDataPointerConstant CRC32_AVX512_TABLE = pointerConstant(16, new int[]{
                    0xe95c1271, 0x00000000, 0xce3371cb, 0x00000000,
                    0xccaa009e, 0x00000000, 0x751997d0, 0x00000001,
                    0x4a7fe880, 0x00000001, 0xe88ef372, 0x00000001,
                    0xccaa009e, 0x00000000, 0x63cd6124, 0x00000001,
                    0xf7011640, 0x00000001, 0xdb710640, 0x00000001,
                    0xd7cfc6ac, 0x00000001, 0xea89367e, 0x00000001,
                    0x8cb44e58, 0x00000001, 0xdf068dc2, 0x00000000,
                    0xae0b5394, 0x00000000, 0xc7569e54, 0x00000001,
                    0xc6e41596, 0x00000001, 0x54442bd4, 0x00000001,
                    0x74359406, 0x00000001, 0x3db1ecdc, 0x00000000,
                    0x5a546366, 0x00000001, 0xf1da05aa, 0x00000000,
                    0xccaa009e, 0x00000000, 0x751997d0, 0x00000001,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000,
    });
    @Def private AllocatableValue result;
    @Use private AllocatableValue crc;
    @Use private AllocatableValue bufferAddress;
    @Use private AllocatableValue length;

    @Temp private Value[] temps;

    public AMD64CRC32UpdateBytesOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, AllocatableValue result, AllocatableValue crc,
                    AllocatableValue bufferAddress, AllocatableValue length) {
        super(TYPE);
        this.result = result;
        this.crc = crc;
        this.bufferAddress = bufferAddress;
        this.length = length;
        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.CLMUL), "CRC32 update bytes requires CLMUL support");
        GraalError.guarantee(result instanceof RegisterValue resultReg && rdi.equals(resultReg.getRegister()), "result should be fixed to rdi, but is %s", result);
        GraalError.guarantee(crc instanceof RegisterValue crcReg && rdi.equals(crcReg.getRegister()), "crc should be fixed to rdi, but is %s", crc);
        GraalError.guarantee(bufferAddress instanceof RegisterValue bufReg && rsi.equals(bufReg.getRegister()), "bufferAddress should be fixed to rsi, but is %s", bufferAddress);
        GraalError.guarantee(length instanceof RegisterValue lenReg && rdx.equals(lenReg.getRegister()), "length should be fixed to rdx, but is %s", length);
        if (supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX, CPUFeature.AVX2, CPUFeature.CLMUL, CPUFeature.AVX512F, CPUFeature.AVX512DQ, CPUFeature.AVX512BW,
                        CPUFeature.AVX512VL, CPUFeature.AVX512_VPCLMULQDQ)) {
            this.temps = registersToValues(new Register[]{
                            rax, rcx, rdx, rsi, r10, r11, r12,
                            xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8, xmm10, xmm11, xmm16
            });
        } else {
            this.temps = registersToValues(new Register[]{
                            rax, rcx, rdx, rsi, r11,
                            xmm0, xmm1, xmm2, xmm3, xmm4, xmm5
            });
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        Register crcReg = asRegister(crc);
        Register bufReg = asRegister(bufferAddress);
        Register lenReg = asRegister(length);
        Register indexReg = r11;
        Register posReg = r12;
        Register tmpReg = r10;
        Register tableReg = rcx;

        GraalError.guarantee(masm.supports(CPUFeature.CLMUL), "CRC32 update bytes requires CLMUL support");
        GraalError.guarantee(resultReg.equals(crcReg), "result and crc should use the same register, but are %s and %s", resultReg, crcReg);

        if (masm.supports(CPUFeature.AVX, CPUFeature.AVX2, CPUFeature.CLMUL, CPUFeature.AVX512_VPCLMULQDQ) && masm.supportsFullAVX512()) {
            // The constants used in the CRC32 algorithm requires the 1's compliment of the initial
            // crc value. However, the constant table for CRC32-C assumes the original crc value.
            // Account for this difference before calling and after returning.
            masm.leaq(tableReg, recordExternalAddress(crb, CRC32_AVX512_TABLE));
            masm.notl(resultReg);
            AMD64CRC32AVX512Helper.emitKernelCRC32Avx512(crb, masm, resultReg, bufReg, lenReg, tableReg, posReg, indexReg, tmpReg);
            masm.notl(resultReg);
        } else {
            masm.leaq(tableReg, recordExternalAddress(crb, CRC32_TABLE));
            emitKernelCRC32Fold(crb, masm, resultReg, bufReg, lenReg, tableReg, indexReg);
        }
    }

    private static void emitKernelCRC32Fold(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Register crc, Register buf, Register len, Register table, Register tmp) {
        Label lTail = new Label();
        Label lTailRestore = new Label();
        Label lTailLoop = new Label();
        Label lExit = new Label();
        Label lAlignLoop = new Label();
        Label lAligned = new Label();
        Label lFoldTail = new Label();
        Label lFold128B = new Label();
        Label lFold512B = new Label();
        Label lFold512BLoop = new Label();
        Label lFoldTailLoop = new Label();

        // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
        // context for the registers used, where all instructions below are using 128-bit mode
        // On EVEX without VL and BW, these instructions will all be AVX.
        masm.notl(crc);
        masm.cmplAndJcc(len, 16, ConditionFlag.Less, lTail, false);

        // Align buffer to 16 bytes
        masm.movl(tmp, buf);
        masm.andl(tmp, 0xF);
        masm.jccb(ConditionFlag.Zero, lAligned);
        masm.subl(tmp, 16);
        masm.addl(len, tmp);
        masm.emitByte(0x66);
        masm.emitByte(0x66);
        masm.emitByte(0x90);
        masm.bind(lAlignLoop);
        // load byte with sign extension
        masm.movsbl(rax, new AMD64Address(buf, 0));
        updateByteCRC32(masm, crc, rax, table);
        masm.incrementq(buf, 1);
        masm.incrementl(tmp, 1);
        masm.jccb(ConditionFlag.Less, lAlignLoop);

        masm.bind(lAligned);
        // save
        masm.movl(tmp, len);
        masm.shrl(len, 4);
        masm.jcc(ConditionFlag.Zero, lTailRestore);

        // Fold crc into first bytes of vector
        MOVDQA.emit(masm, PD, xmm1, new AMD64Address(buf, 0));
        MOVD.emit(masm, DWORD, rax, xmm1);
        masm.xorl(crc, rax);
        if (masm.supports(CPUFeature.SSE4_1)) {
            PINSRD.emit(masm, PD, xmm1, crc, 0);
        } else {
            masm.pinsrw(xmm1, crc, 0);
            masm.shrl(crc, 16);
            masm.pinsrw(xmm1, crc, 1);
        }
        masm.addq(buf, 16);
        masm.subl(len, 4);
        masm.jcc(ConditionFlag.Less, lFoldTail);

        MOVDQA.emit(masm, PD, xmm2, new AMD64Address(buf, 0));
        MOVDQA.emit(masm, PD, xmm3, new AMD64Address(buf, 16));
        MOVDQA.emit(masm, PD, xmm4, new AMD64Address(buf, 32));
        masm.addq(buf, 48);
        masm.subl(len, 3);
        masm.jcc(ConditionFlag.LessEqual, lFold512B);

        // Fold total 512 bits of polynomial on each iteration,
        // 128 bits per each of 4 parallel streams.
        masm.movdqu(AVXSize.XMM, xmm0, recordExternalAddress(crb, CRC_BY128_MASKS_32));
        masm.align(32);
        masm.bind(lFold512BLoop);
        fold128BitCRC32(masm, xmm1, xmm0, xmm5, buf, 0);
        fold128BitCRC32(masm, xmm2, xmm0, xmm5, buf, 16);
        fold128BitCRC32(masm, xmm3, xmm0, xmm5, buf, 32);
        fold128BitCRC32(masm, xmm4, xmm0, xmm5, buf, 48);
        masm.addq(buf, 64);
        masm.subl(len, 4);
        masm.jcc(ConditionFlag.Greater, lFold512BLoop);

        // Fold 512 bits to 128 bits.
        masm.bind(lFold512B);
        masm.movdqu(AVXSize.XMM, xmm0, recordExternalAddress(crb, CRC_BY128_MASKS_16));
        fold128BitCRC32(masm, xmm1, xmm0, xmm5, xmm2);
        fold128BitCRC32(masm, xmm1, xmm0, xmm5, xmm3);
        fold128BitCRC32(masm, xmm1, xmm0, xmm5, xmm4);

        // Fold the rest of 128 bits data chunks
        masm.bind(lFoldTail);
        masm.addl(len, 3);
        masm.jccb(ConditionFlag.LessEqual, lFold128B);
        masm.movdqu(AVXSize.XMM, xmm0, recordExternalAddress(crb, CRC_BY128_MASKS_16));
        masm.bind(lFoldTailLoop);
        fold128BitCRC32(masm, xmm1, xmm0, xmm5, buf, 0);
        masm.addq(buf, 16);
        masm.decrementl(len, 1);
        masm.jccb(ConditionFlag.Greater, lFoldTailLoop);

        // Fold 128 bits in xmm1 down into 32 bits in crc register.
        masm.bind(lFold128B);
        masm.movdqu(AVXSize.XMM, xmm0, recordExternalAddress(crb, CRC_BY128_MASKS));
        if (masm.supports(CPUFeature.AVX)) {
            masm.vpclmulqdq(xmm2, xmm0, xmm1, 0x1);
            masm.vpand(xmm3, xmm0, xmm2, AVXSize.XMM);
            masm.vpclmulqdq(xmm0, xmm0, xmm3, 0x1);
        } else {
            MOVDQA.emit(masm, PD, xmm2, xmm0);
            masm.pclmulqdq(xmm2, xmm1, 0x1);
            MOVDQA.emit(masm, PD, xmm3, xmm0);
            masm.pand(xmm3, xmm2);
            masm.pclmulqdq(xmm0, xmm3, 0x1);
        }
        masm.psrldq(xmm1, 8);
        masm.psrldq(xmm2, 4);
        masm.pxor(xmm0, xmm1);
        masm.pxor(xmm0, xmm2);

        // 8 8-bit folds to compute 32-bit CRC.
        for (int j = 0; j < 4; j++) {
            fold8BitCRC32(masm, xmm0, table, xmm1, rax);
        }
        // mov 32 bits to general register
        MOVD.emit(masm, DWORD, crc, xmm0);
        for (int j = 0; j < 4; j++) {
            fold8BitCRC32(masm, crc, table, rax);
        }

        masm.bind(lTailRestore);
        // restore
        masm.movl(len, tmp);
        masm.bind(lTail);
        masm.andl(len, 0xF);
        masm.jccb(ConditionFlag.Zero, lExit);

        // Fold the rest of bytes
        masm.emitByte(0x90);
        masm.bind(lTailLoop);
        // load byte with sign extension
        masm.movsbl(rax, new AMD64Address(buf, 0));
        updateByteCRC32(masm, crc, rax, table);
        masm.incrementq(buf, 1);
        masm.decrementl(len, 1);
        masm.jccb(ConditionFlag.Greater, lTailLoop);
        masm.bind(lExit);
        masm.notl(crc);
    }

    private static void fold128BitCRC32(AMD64MacroAssembler masm, Register xcrc, Register xk, Register xtmp, Register buf, int offset) {
        if (masm.supports(CPUFeature.AVX)) {
            masm.vpclmulqdq(xtmp, xk, xcrc, 0x11);
            masm.vpclmulqdq(xcrc, xk, xcrc, 0x0);
            masm.vpxor(xcrc, xcrc, new AMD64Address(buf, offset), AVXSize.XMM);
            masm.pxor(xcrc, xtmp);
        } else {
            MOVDQA.emit(masm, PD, xtmp, xcrc);
            masm.pclmulqdq(xtmp, xk, 0x11);
            masm.pclmulqdq(xcrc, xk, 0x0);
            masm.pxor(xcrc, xtmp);
            masm.movdqu(xtmp, new AMD64Address(buf, offset));
            masm.pxor(xcrc, xtmp);
        }
    }

    private static void fold128BitCRC32(AMD64MacroAssembler masm, Register xcrc, Register xk, Register xtmp, Register xbuf) {
        if (masm.supports(CPUFeature.AVX)) {
            masm.vpclmulqdq(xtmp, xk, xcrc, 0x11);
            masm.vpclmulqdq(xcrc, xk, xcrc, 0x0);
            masm.pxor(xcrc, xbuf);
            masm.pxor(xcrc, xtmp);
        } else {
            MOVDQA.emit(masm, PD, xtmp, xcrc);
            masm.pclmulqdq(xtmp, xk, 0x11);
            masm.pclmulqdq(xcrc, xk, 0x0);
            masm.pxor(xcrc, xbuf);
            masm.pxor(xcrc, xtmp);
        }
    }

    private static void fold8BitCRC32(AMD64MacroAssembler masm, Register xcrc, Register table, Register xtmp, Register tmp) {
        MOVD.emit(masm, DWORD, tmp, xcrc);
        masm.andl(tmp, 0xFF);
        SSEOp.MOVD.emit(masm, DWORD, xtmp, new AMD64Address(table, tmp, S4, 0));
        masm.psrldq(xcrc, 1);
        masm.pxor(xcrc, xtmp);
    }

    private static void fold8BitCRC32(AMD64MacroAssembler masm, Register crc, Register table, Register tmp) {
        masm.movl(tmp, crc);
        masm.andl(tmp, 0xFF);
        masm.shrl(crc, 8);
        masm.xorl(crc, new AMD64Address(table, tmp, S4, 0));
    }

    private static void updateByteCRC32(AMD64MacroAssembler masm, Register crc, Register val, Register table) {
        masm.xorl(val, crc);
        masm.andl(val, 0xFF);
        masm.shrl(crc, 8);
        masm.xorl(crc, new AMD64Address(table, val, S4, 0));
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
