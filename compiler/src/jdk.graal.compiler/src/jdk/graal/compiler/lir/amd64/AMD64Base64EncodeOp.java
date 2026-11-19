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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMaskedMoveOp.VPMASKMOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTQ_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULTISHIFTQB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPAND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPGTB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULHUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBUSB;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
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
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d91e3ef51121f605d6060d61571a2266adb74ca0/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L1682-L2164",
          sha1 = "50503f806f2d8a0b9d85513ac720a59ea415c18f")
// @formatter:on
@Opcode("AMD64_BASE64_ENCODE_BLOCK")
public final class AMD64Base64EncodeOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64Base64EncodeOp> TYPE = LIRInstructionClass.create(AMD64Base64EncodeOp.class);

    private static final ArrayDataPointerConstant ENCODE_SHUFFLE = pointerConstant(16, new long[]{
                    0x0405030401020001L,
                    0x0a0b090a07080607L,
                    0x10110f100d0e0c0dL,
                    0x1617151613141213L,
                    0x1c1d1b1c191a1819L,
                    0x222321221f201e1fL,
                    0x2829272825262425L,
                    0x2e2f2d2e2b2c2a2bL,
    });

    private static final ArrayDataPointerConstant ENCODE_AVX2_SHUFFLE = pointerConstant(16, new long[]{
                    0x0809070805060405L,
                    0x0e0f0d0e0b0c0a0bL,
                    0x0405030401020001L,
                    0x0a0b090a07080607L,
    });

    private static final ArrayDataPointerConstant ENCODE_AVX2_INPUT_MASK = pointerConstant(16, new long[]{
                    0x8000000000000000L,
                    0x8000000080000000L,
                    0x8000000080000000L,
                    0x8000000080000000L,
    });

    private static final ArrayDataPointerConstant ENCODE_AVX2_LUT = pointerConstant(16, new long[]{
                    0xfcfcfcfcfcfc4741L,
                    0x0000f0edfcfcfcfcL,
                    0xfcfcfcfcfcfc4741L,
                    0x0000f0edfcfcfcfcL,
                    0xfcfcfcfcfcfc4741L,
                    0x000020effcfcfcfcL,
                    0xfcfcfcfcfcfc4741L,
                    0x000020effcfcfcfcL,
    });

    private static final ArrayDataPointerConstant ENCODE_TABLE = pointerConstant(16, new long[]{
                    0x4847464544434241L,
                    0x504f4e4d4c4b4a49L,
                    0x5857565554535251L,
                    0x6665646362615a59L,
                    0x6e6d6c6b6a696867L,
                    0x767574737271706fL,
                    0x333231307a797877L,
                    0x2f2b393837363534L,
                    // URL table
                    0x4847464544434241L,
                    0x504f4e4d4c4b4a49L,
                    0x5857565554535251L,
                    0x6665646362615a59L,
                    0x6e6d6c6b6a696867L,
                    0x767574737271706fL,
                    0x333231307a797877L,
                    0x5f2d393837363534L,
    });

    @Use private Value srcValue;
    @Use private Value spValue;
    @Use private Value slValue;
    @Use private Value dstValue;
    @Use private Value dpValue;
    @Use private Value flagsValue;

    @Temp private Value[] temps;

    public AMD64Base64EncodeOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, AllocatableValue srcValue, AllocatableValue spValue, AllocatableValue slValue,
                    AllocatableValue dstValue, AllocatableValue dpValue, AllocatableValue flagsValue) {
        super(TYPE);
        this.srcValue = srcValue;
        this.spValue = spValue;
        this.slValue = slValue;
        this.dstValue = dstValue;
        this.dpValue = dpValue;
        this.flagsValue = flagsValue;
        GraalError.guarantee(srcValue instanceof RegisterValue srcValueReg && rdi.equals(srcValueReg.getRegister()), "srcValue should be fixed to rdi, but is %s", srcValue);
        GraalError.guarantee(spValue instanceof RegisterValue spValueReg && rsi.equals(spValueReg.getRegister()), "spValue should be fixed to rsi, but is %s", spValue);
        GraalError.guarantee(slValue instanceof RegisterValue slValueReg && rdx.equals(slValueReg.getRegister()), "slValue should be fixed to rdx, but is %s", slValue);
        GraalError.guarantee(dstValue instanceof RegisterValue dstValueReg && rcx.equals(dstValueReg.getRegister()), "dstValue should be fixed to rcx, but is %s", dstValue);
        GraalError.guarantee(dpValue instanceof RegisterValue dpValueReg && r8.equals(dpValueReg.getRegister()), "dpValue should be fixed to r8, but is %s", dpValue);
        GraalError.guarantee(flagsValue instanceof RegisterValue flagsValueReg && r9.equals(flagsValueReg.getRegister()), "flagsValue should be fixed to r9, but is %s", flagsValue);

        if (supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX512_VBMI) || supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX2)) {
            this.temps = AMD64LIRHelper.registersToValues(new Register[]{
                            rax,
                            rcx,
                            rdx,
                            rsi,
                            rdi,
                            r8,
                            r9,
                            r10,
                            r11,
                            // vzeroupper clears upper bits of xmm0-xmm15.
                            xmm0,
                            xmm1,
                            xmm2,
                            xmm3,
                            xmm4,
                            xmm5,
                            xmm6,
                            xmm7,
                            xmm8,
                            xmm9,
                            xmm10,
                            xmm11,
                            xmm12,
                            xmm13,
                            xmm14,
                            xmm15,
            });
        } else {
            this.temps = AMD64LIRHelper.registersToValues(new Register[]{
                            rax,
                            rcx,
                            rdx,
                            rsi,
                            rdi,
                            r8,
                            r9,
                            r10,
                            r11,
            });
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        // Save callee-saved registers before using them
        masm.push(r12);
        masm.push(r13);
        masm.push(r14);
        masm.push(r15);

        // arguments
        Register source = asRegister(srcValue);       // Source Array
        Register startOffset = asRegister(spValue);   // start offset
        Register endOffset = asRegister(slValue);     // end offset
        Register dest = asRegister(dstValue);         // destination array

        Register dp = asRegister(dpValue);            // Position for writing to dest array
        Register isURL = asRegister(flagsValue);      // Base64 or URL character set

        Register length = r14;
        Register encodeTable = r13;
        Register scalarEncodeTable = r11;

        Label labelProcess3 = new Label();
        Label labelExit = new Label();
        Label labelProcessData = new Label();
        Label labelVBMILoop = new Label();
        Label labelNot512 = new Label();
        Label label32ByteLoop = new Label();

        // calculate length from offsets
        masm.movl(length, endOffset);
        masm.sublAndJcc(length, startOffset, ConditionFlag.LessEqual, labelExit, false);

        // Code for 512-bit VBMI encoding. Encodes 48 input bytes into 64
        // output bytes. We read 64 input bytes and ignore the last 16, so be
        // sure not to read past the end of the input buffer.
        if (masm.supports(CPUFeature.AVX512_VBMI)) {
            // Do not overrun input buffer.
            masm.cmplAndJcc(length, 64, ConditionFlag.Below, labelNot512, false);

            masm.shll(isURL, 6); // index into decode table based on isURL
            masm.leaq(encodeTable, recordExternalAddress(crb, ENCODE_TABLE));
            masm.addq(encodeTable, isURL);
            masm.shrl(isURL, 6); // restore isURL

            masm.movq(rax, 0x3036242a1016040aL); // Shifts
            masm.evmovdqu64(xmm3, recordExternalAddress(crb, ENCODE_SHUFFLE));
            masm.evmovdqu64(xmm2, new AMD64Address(encodeTable, 0));
            EVPBROADCASTQ_GPR.emit(masm, ZMM, xmm1, rax);

            masm.align(32);
            masm.bind(labelVBMILoop);

            EVPERMB.emit(masm, ZMM, xmm0, xmm3, new AMD64Address(source, startOffset, Stride.S1));
            masm.subl(length, 48);

            // Put the input bytes into the proper lanes for writing, then
            // encode them.
            EVPMULTISHIFTQB.emit(masm, ZMM, xmm0, xmm1, xmm0);
            EVPERMB.emit(masm, ZMM, xmm0, xmm0, xmm2);

            // Write to destination
            masm.evmovdqu64(new AMD64Address(dest, dp, Stride.S1), xmm0);

            masm.addq(dest, 64);
            masm.addq(source, 48);
            masm.cmplAndJcc(length, 64, ConditionFlag.AboveEqual, labelVBMILoop, true);
            masm.vzeroupper();
        }

        masm.bind(labelNot512);

        if (masm.supports(CPUFeature.AVX2)) {
            /*
             * This AVX2 encoder is based off the paper at: https://dl.acm.org/doi/10.1145/3132709
             *
             * We use AVX2 SIMD instructions to encode 24 bytes into 32 output bytes.
             *
             */
            // Lengths under 32 bytes are done with scalar routine
            masm.cmplAndJcc(length, 31, ConditionFlag.BelowEqual, labelProcess3, false);

            // Set up supporting constant table data
            masm.vmovdqu(xmm9, recordExternalAddress(crb, ENCODE_AVX2_SHUFFLE));
            // 6-bit mask for 2nd and 4th (and multiples) 6-bit values
            masm.movl(rax, 0x0fc0fc00);
            masm.movdl(xmm8, rax);
            masm.vmovdqu(xmm1, recordExternalAddress(crb, ENCODE_AVX2_INPUT_MASK));
            masm.emit(VPBROADCASTD, xmm8, xmm8, YMM);

            // Multiplication constant for "shifting" right by 6 and 10
            // bits
            masm.movl(rax, 0x04000040);

            masm.subl(length, 24);
            masm.movdl(xmm7, rax);
            masm.emit(VPBROADCASTD, xmm7, xmm7, YMM);

            // @formatter:off
            // For the first load, we mask off reading of the first 4
            // bytes into the register. This is so we can get 4 3-byte
            // chunks into each lane of the register, avoiding having to
            // handle end conditions.  We then shuffle these bytes into a
            // specific order so that manipulation is easier.
            //
            // The initial read loads the XMM register like this:
            //
            // Lower 128-bit lane:
            // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
            // | XX | XX | XX | XX | A0 | A1 | A2 | B0 | B1 | B2 | C0 | C1
            // | C2 | D0 | D1 | D2 |
            // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
            //
            // Upper 128-bit lane:
            // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
            // | E0 | E1 | E2 | F0 | F1 | F2 | G0 | G1 | G2 | H0 | H1 | H2
            // | XX | XX | XX | XX |
            // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
            //
            // Where A0 is the first input byte, B0 is the fourth, etc.
            // The alphabetical significance denotes the 3 bytes to be
            // consumed and encoded into 4 bytes.
            //
            // We then shuffle the register so each 32-bit word contains
            // the sequence:
            //    A1 A0 A2 A1, B1, B0, B2, B1, etc.
            // Each of these byte sequences are then manipulated into 4
            // 6-bit values ready for encoding.
            //
            // If we focus on one set of 3-byte chunks, changing the
            // nomenclature such that A0 => a, A1 => b, and A2 => c, we
            // shuffle such that each 24-bit chunk contains:
            //
            // b7 b6 b5 b4 b3 b2 b1 b0 | a7 a6 a5 a4 a3 a2 a1 a0 | c7 c6
            // c5 c4 c3 c2 c1 c0 | b7 b6 b5 b4 b3 b2 b1 b0
            // Explain this step.
            // b3 b2 b1 b0 c5 c4 c3 c2 | c1 c0 d5 d4 d3 d2 d1 d0 | a5 a4
            // a3 a2 a1 a0 b5 b4 | b3 b2 b1 b0 c5 c4 c3 c2
            //
            // W first and off all but bits 4-9 and 16-21 (c5..c0 and
            // a5..a0) and shift them using a vector multiplication
            // operation (vpmulhuw) which effectively shifts c right by 6
            // bits and a right by 10 bits.  We similarly mask bits 10-15
            // (d5..d0) and 22-27 (b5..b0) and shift them left by 8 and 4
            // bits respectively.  This is done using vpmullw.  We end up
            // with 4 6-bit values, thus splitting the 3 input bytes,
            // ready for encoding:
            //    0 0 d5..d0 0 0 c5..c0 0 0 b5..b0 0 0 a5..a0
            //
            // For translation, we recognize that there are 5 distinct
            // ranges of legal Base64 characters as below:
            //
            //   +-------------+-------------+------------+
            //   | 6-bit value | ASCII range |   offset   |
            //   +-------------+-------------+------------+
            //   |    0..25    |    A..Z     |     65     |
            //   |   26..51    |    a..z     |     71     |
            //   |   52..61    |    0..9     |     -4     |
            //   |     62      |   + or -    | -19 or -17 |
            //   |     63      |   / or _    | -16 or 32  |
            //   +-------------+-------------+------------+
            //
            // We note that vpshufb does a parallel lookup in a
            // destination register using the lower 4 bits of bytes from a
            // source register.  If we use a saturated subtraction and
            // subtract 51 from each 6-bit value, bytes from [0,51]
            // saturate to 0, and [52,63] map to a range of [1,12].  We
            // distinguish the [0,25] and [26,51] ranges by assigning a
            // value of 13 for all 6-bit values less than 26.  We end up
            // with:
            //
            //   +-------------+-------------+------------+
            //   | 6-bit value |   Reduced   |   offset   |
            //   +-------------+-------------+------------+
            //   |    0..25    |     13      |     65     |
            //   |   26..51    |      0      |     71     |
            //   |   52..61    |    0..9     |     -4     |
            //   |     62      |     11      | -19 or -17 |
            //   |     63      |     12      | -16 or 32  |
            //   +-------------+-------------+------------+
            //
            // We then use a final vpshufb to add the appropriate offset,
            // translating the bytes.
            //
            // Load input bytes - only 28 bytes.  Mask the first load to
            // not load into the full register.
            // @formatter:on
            VPMASKMOVD.emit(masm, YMM, xmm1, xmm1, new AMD64Address(source, startOffset, Stride.S1, -4));

            // Move 3-byte chunks of input (12 bytes) into 16 bytes,
            // ordering by:
            // 1, 0, 2, 1; 4, 3, 5, 4; etc. This groups 6-bit chunks
            // for easy masking
            masm.vpshufb(xmm1, xmm1, xmm9, YMM);

            masm.addl(startOffset, 24);

            // Load masking register for first and third (and multiples)
            // 6-bit values.
            masm.movl(rax, 0x003f03f0);
            masm.movdl(xmm6, rax);
            masm.emit(VPBROADCASTD, xmm6, xmm6, YMM);
            // Multiplication constant for "shifting" left by 4 and 8 bits
            masm.movl(rax, 0x01000010);
            masm.movdl(xmm5, rax);
            masm.emit(VPBROADCASTD, xmm5, xmm5, YMM);

            // Isolate 6-bit chunks of interest
            masm.emit(VPAND, xmm0, xmm8, xmm1, YMM);

            // Load constants for encoding
            masm.movl(rax, 0x19191919);
            masm.movdl(xmm3, rax);
            masm.emit(VPBROADCASTD, xmm3, xmm3, YMM);
            masm.movl(rax, 0x33333333);
            masm.movdl(xmm4, rax);
            masm.emit(VPBROADCASTD, xmm4, xmm4, YMM);

            // Shift output bytes 0 and 2 into proper lanes
            masm.emit(VPMULHUW, xmm2, xmm0, xmm7, YMM);

            // Mask and shift output bytes 1 and 3 into proper lanes and
            // combine
            masm.emit(VPAND, xmm0, xmm6, xmm1, YMM);
            masm.emit(VPMULLW, xmm0, xmm5, xmm0, YMM);
            masm.emit(VPOR, xmm0, xmm0, xmm2, YMM);

            // Find out which are 0..25. This indicates which input
            // values fall in the range of 'A'-'Z', which require an
            // additional offset (see comments above)
            masm.emit(VPCMPGTB, xmm2, xmm0, xmm3, YMM);
            masm.emit(VPSUBUSB, xmm1, xmm0, xmm4, YMM);
            masm.emit(VPSUBB, xmm1, xmm1, xmm2, YMM);

            // Load the proper lookup table
            masm.leaq(encodeTable, recordExternalAddress(crb, ENCODE_AVX2_LUT));
            masm.movl(r15, isURL);
            masm.shll(r15, 5);
            masm.vmovdqu(xmm2, new AMD64Address(encodeTable, r15, Stride.S1));

            // Shuffle the offsets based on the range calculation done
            // above. This allows us to add the correct offset to the
            // 6-bit value corresponding to the range documented above.
            masm.vpshufb(xmm1, xmm2, xmm1, YMM);
            masm.emit(VPADDB, xmm0, xmm1, xmm0, YMM);

            // Store the encoded bytes
            masm.vmovdqu(new AMD64Address(dest, dp, Stride.S1), xmm0);
            masm.addl(dp, 32);

            masm.cmplAndJcc(length, 31, ConditionFlag.BelowEqual, labelProcess3, false);

            masm.align(32);
            masm.bind(label32ByteLoop);

            // Get next 32 bytes
            masm.vmovdqu(xmm1, new AMD64Address(source, startOffset, Stride.S1, -4));

            masm.subl(length, 24);
            masm.addl(startOffset, 24);

            // This logic is identical to the above, with only constant
            // register loads removed. Shuffle the input, mask off 6-bit
            // chunks, shift them into place, then add the offset to
            // encode.
            masm.vpshufb(xmm1, xmm1, xmm9, YMM);

            masm.emit(VPAND, xmm0, xmm8, xmm1, YMM);
            masm.emit(VPMULHUW, xmm10, xmm0, xmm7, YMM);
            masm.emit(VPAND, xmm0, xmm6, xmm1, YMM);
            masm.emit(VPMULLW, xmm0, xmm5, xmm0, YMM);
            masm.emit(VPOR, xmm0, xmm0, xmm10, YMM);
            masm.emit(VPCMPGTB, xmm10, xmm0, xmm3, YMM);
            masm.emit(VPSUBUSB, xmm1, xmm0, xmm4, YMM);
            masm.emit(VPSUBB, xmm1, xmm1, xmm10, YMM);
            masm.vpshufb(xmm1, xmm2, xmm1, YMM);
            masm.emit(VPADDB, xmm0, xmm1, xmm0, YMM);

            // Store the encoded bytes
            masm.vmovdqu(new AMD64Address(dest, dp, Stride.S1), xmm0);
            masm.addl(dp, 32);

            masm.cmplAndJcc(length, 31, ConditionFlag.Above, label32ByteLoop, true);

            masm.bind(labelProcess3);
            masm.vzeroupper();
        } else {
            masm.bind(labelProcess3);
        }

        masm.cmplAndJcc(length, 3, ConditionFlag.Below, labelExit, false);

        // Load the encoding table based on isURL
        masm.leaq(scalarEncodeTable, recordExternalAddress(crb, ENCODE_TABLE));
        masm.movl(r15, isURL);
        masm.shll(r15, 6);
        masm.addq(scalarEncodeTable, r15);

        masm.bind(labelProcessData);

        // Load 3 bytes
        masm.movzbl(r15, new AMD64Address(source, startOffset, Stride.S1));
        masm.movzbl(r10, new AMD64Address(source, startOffset, Stride.S1, 1));
        masm.movzbl(r13, new AMD64Address(source, startOffset, Stride.S1, 2));

        // Build a 32-bit word with bytes 1, 2, 0, 1
        masm.movl(rax, r10);
        masm.shll(r10, 24);
        masm.orl(rax, r10);

        masm.subl(length, 3);

        masm.shll(r15, 8);
        masm.shll(r13, 16);
        masm.orl(rax, r15);

        masm.addl(startOffset, 3);

        masm.orl(rax, r13);
        // At this point, rax contains | byte1 | byte2 | byte0 | byte1
        // r13 has byte2 << 16 - need low-order 6 bits to translate.
        // This translated byte is the fourth output byte.

        masm.shrl(r13, 16);
        masm.andl(r13, 0x3f);

        // The high-order 6 bits of r15 (byte0) is translated.
        // The translated byte is the first output byte.
        masm.shrl(r15, 10);

        masm.movzbl(r13, new AMD64Address(scalarEncodeTable, r13, Stride.S1));
        masm.movzbl(r15, new AMD64Address(scalarEncodeTable, r15, Stride.S1));

        masm.movb(new AMD64Address(dest, dp, Stride.S1, 3), r13);

        // Extract high-order 4 bits of byte1 and low-order 2 bits of byte0.
        // This translated byte is the second output byte.
        masm.shrl(rax, 4);
        masm.movl(r10, rax);
        masm.andl(rax, 0x3f);

        masm.movb(new AMD64Address(dest, dp, Stride.S1, 0), r15);

        masm.movzbl(rax, new AMD64Address(scalarEncodeTable, rax, Stride.S1));

        // Extract low-order 2 bits of byte1 and high-order 4 bits of byte2.
        // This translated byte is the third output byte.
        masm.shrl(r10, 18);
        masm.andl(r10, 0x3f);
        masm.movzbl(r10, new AMD64Address(scalarEncodeTable, r10, Stride.S1));

        masm.movb(new AMD64Address(dest, dp, Stride.S1, 1), rax);
        masm.movb(new AMD64Address(dest, dp, Stride.S1, 2), r10);

        masm.addl(dp, 4);
        masm.cmplAndJcc(length, 3, ConditionFlag.AboveEqual, labelProcessData, false);

        masm.bind(labelExit);
        masm.pop(r15);
        masm.pop(r14);
        masm.pop(r13);
        masm.pop(r12);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
