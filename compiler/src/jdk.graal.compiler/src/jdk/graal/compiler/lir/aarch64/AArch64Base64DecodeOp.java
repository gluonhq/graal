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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD4_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST3_MULTIPLE_3R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.HalfReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Byte;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.DoubleWord;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ExtendType.UXTW;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d91e3ef51121f605d6060d61571a2266adb74ca0/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L9899-L10200",
          sha1 = "d896b73e2590dca891a5dd4e801e63afacdc3879")
// @formatter:on
@Opcode("AARCH64_BASE64_DECODE_BLOCK")
public final class AArch64Base64DecodeOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64Base64DecodeOp> TYPE = LIRInstructionClass.create(AArch64Base64DecodeOp.class);

    private static final ArrayDataPointerConstant FROM_BASE64_FOR_NOSIMD_DATA = pointerConstant(16, new byte[]{
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 62, (byte) 255, (byte) 255, (byte) 255, 63,
                    52, 53, 54, 55, 56, 57, 58, 59, 60, 61, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
                    15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                    41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
    });

    private static final ArrayDataPointerConstant FROM_BASE64_URL_FOR_NOSIMD_DATA = pointerConstant(16, new byte[]{
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 62, (byte) 255,
                    (byte) 255,
                    52, 53, 54, 55, 56, 57, 58, 59, 60, 61, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
                    15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 63,
                    (byte) 255, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                    41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
    });

    private static final ArrayDataPointerConstant FROM_BASE64_FOR_SIMD_DATA = pointerConstant(16, new byte[]{
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 62, (byte) 255, (byte) 255, (byte) 255, 63,
                    52, 53, 54, 55, 56, 57, 58, 59, 60, 61, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    0, (byte) 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
                    14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255, (byte) 255, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                    40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
    });

    private static final ArrayDataPointerConstant FROM_BASE64_URL_FOR_SIMD_DATA = pointerConstant(16, new byte[]{
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    (byte) 255,
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 62, (byte) 255,
                    (byte) 255,
                    52, 53, 54, 55, 56, 57, 58, 59, 60, 61, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    0, (byte) 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
                    14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                    63, (byte) 255, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                    40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
    });

    @Def({OperandFlag.REG}) private Value resultValue;

    @Alive({OperandFlag.REG}) private Value srcValue;
    @Alive({OperandFlag.REG}) private Value spValue;
    @Alive({OperandFlag.REG}) private Value slValue;
    @Alive({OperandFlag.REG}) private Value dstValue;
    @Alive({OperandFlag.REG}) private Value dpValue;
    @Alive({OperandFlag.REG}) private Value urlValue;

    @Temp({OperandFlag.REG}) private Value[] temps;
    @Temp({OperandFlag.REG}) private Value[] vectorTemps;

    public AArch64Base64DecodeOp(AllocatableValue resultValue, AllocatableValue srcValue, AllocatableValue spValue, AllocatableValue slValue, AllocatableValue dstValue,
                    AllocatableValue dpValue, AllocatableValue urlValue) {
        super(TYPE);
        this.resultValue = resultValue;
        this.srcValue = srcValue;
        this.spValue = spValue;
        this.slValue = slValue;
        this.dstValue = dstValue;
        this.dpValue = dpValue;
        this.urlValue = urlValue;
        this.temps = new Value[]{
                        r6.asValue(),
                        r7.asValue(),
                        r11.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        r15.asValue(),
                        r16.asValue(),
        };
        this.vectorTemps = new Value[]{
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
                        v5.asValue(),
                        v6.asValue(),
                        v7.asValue(),
                        v16.asValue(),
                        v17.asValue(),
                        v18.asValue(),
                        v19.asValue(),
                        v20.asValue(),
                        v21.asValue(),
                        v22.asValue(),
                        v23.asValue(),
                        v24.asValue(),
                        v25.asValue(),
                        v26.asValue(),
                        v27.asValue(),
                        v28.asValue(),
                        v29.asValue(),
                        v30.asValue(),
                        v31.asValue()
        };
    }

    private static void generateBase64DecodeSimdround(AArch64MacroAssembler masm, Register src, Register dst,
                    Register codecL, Register codecH, int size, Label labelExit) {
        try (AArch64MacroAssembler.ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register rscratch2 = scratchReg2.getRegister();
            Register in0 = v16;
            Register in1 = v17;
            Register in2 = v18;
            Register in3 = v19;
            Register out0 = v20;
            Register out1 = v21;
            Register out2 = v22;

            Register decL0 = v23;
            Register decL1 = v24;
            Register decL2 = v25;
            Register decL3 = v26;
            Register decH0 = v28;
            Register decH1 = v29;
            Register decH2 = v30;
            Register decH3 = v31;

            Label labelNoIllegalData = new Label();
            Label labelErrorInLowerHalf = new Label();
            Label labelStoreLegalData = new Label();

            AArch64ASIMDAssembler.ASIMDSize arrangement = size == 16 ? FullReg : HalfReg;

            masm.neon.ld4MultipleVVVV(arrangement, Byte, in0, in1, in2, in3,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD4_MULTIPLE_4R, arrangement, Byte, src, 4 * size));

            // we need unsigned saturating subtract, to make sure all input values
            // in range [0, 63] will have 0U value in the higher half lookup
            masm.neon.uqsubVVV(FullReg, Byte, decH0, in0, v27);
            masm.neon.uqsubVVV(FullReg, Byte, decH1, in1, v27);
            masm.neon.uqsubVVV(FullReg, Byte, decH2, in2, v27);
            masm.neon.uqsubVVV(FullReg, Byte, decH3, in3, v27);

            // lower half lookup
            masm.neon.tblVVV(arrangement, decL0, codecL, 4, in0);
            masm.neon.tblVVV(arrangement, decL1, codecL, 4, in1);
            masm.neon.tblVVV(arrangement, decL2, codecL, 4, in2);
            masm.neon.tblVVV(arrangement, decL3, codecL, 4, in3);

            // higher half lookup
            masm.neon.tbxVVV(arrangement, decH0, codecH, 4, decH0);
            masm.neon.tbxVVV(arrangement, decH1, codecH, 4, decH1);
            masm.neon.tbxVVV(arrangement, decH2, codecH, 4, decH2);
            masm.neon.tbxVVV(arrangement, decH3, codecH, 4, decH3);

            // combine lower and higher
            masm.neon.orrVVV(arrangement, decL0, decL0, decH0);
            masm.neon.orrVVV(arrangement, decL1, decL1, decH1);
            masm.neon.orrVVV(arrangement, decL2, decL2, decH2);
            masm.neon.orrVVV(arrangement, decL3, decL3, decH3);

            // check illegal inputs, value larger than 63 (maximum of 6 bits)
            masm.neon.cmhiVVV(arrangement, Byte, decH0, decL0, v27);
            masm.neon.cmhiVVV(arrangement, Byte, decH1, decL1, v27);
            masm.neon.cmhiVVV(arrangement, Byte, decH2, decL2, v27);
            masm.neon.cmhiVVV(arrangement, Byte, decH3, decL3, v27);
            masm.neon.orrVVV(arrangement, in0, decH0, decH1);
            masm.neon.orrVVV(arrangement, in1, decH2, decH3);
            masm.neon.orrVVV(arrangement, in2, in0, in1);
            masm.neon.umaxvSV(arrangement, Byte, in3, in2);
            masm.neon.umovGX(Byte, rscratch2, in3, 0);

            // get the data to output
            masm.neon.shlVVI(arrangement, Byte, out0, decL0, 2);
            masm.neon.ushrVVI(arrangement, Byte, out1, decL1, 4);
            masm.neon.orrVVV(arrangement, out0, out0, out1);
            masm.neon.shlVVI(arrangement, Byte, out1, decL1, 4);
            masm.neon.ushrVVI(arrangement, Byte, out2, decL2, 2);
            masm.neon.orrVVV(arrangement, out1, out1, out2);
            masm.neon.shlVVI(arrangement, Byte, out2, decL2, 6);
            masm.neon.orrVVV(arrangement, out2, out2, decL3);

            masm.cbz(64, rscratch2, labelNoIllegalData);

            // handle illegal input
            masm.neon.umovGX(DoubleWord, r16, in2, 0);
            if (size == 16) {
                masm.cbnz(64, r16, labelErrorInLowerHalf);

                // illegal input is in higher half, store the lower half now.
                masm.neon.st3MultipleVVV(HalfReg, Byte, out0, out1, out2,
                                AArch64Address.createStructureImmediatePostIndexAddress(ST3_MULTIPLE_3R, HalfReg, Byte, dst, 24));

                masm.neon.umovGX(DoubleWord, r16, in2, 1);
                masm.neon.umovGX(DoubleWord, r11, out0, 1);
                masm.neon.umovGX(DoubleWord, r12, out1, 1);
                masm.neon.umovGX(DoubleWord, r13, out2, 1);
                masm.jmp(labelStoreLegalData);

                masm.bind(labelErrorInLowerHalf);
            }
            masm.neon.umovGX(DoubleWord, r11, out0, 0);
            masm.neon.umovGX(DoubleWord, r12, out1, 0);
            masm.neon.umovGX(DoubleWord, r13, out2, 0);

            masm.bind(labelStoreLegalData);
            masm.tbnz(r16, 5, labelExit); // 0xff indicates illegal input
            masm.str(8, r11, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
            masm.str(8, r12, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
            masm.str(8, r13, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
            masm.lsr(64, r16, r16, 8);
            masm.lsr(64, r11, r11, 8);
            masm.lsr(64, r12, r12, 8);
            masm.lsr(64, r13, r13, 8);
            masm.jmp(labelStoreLegalData);

            masm.bind(labelNoIllegalData);
            masm.neon.st3MultipleVVV(arrangement, Byte, out0, out1, out2,
                            AArch64Address.createStructureImmediatePostIndexAddress(ST3_MULTIPLE_3R, arrangement, Byte, dst, 3 * size));
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register src = asRegister(srcValue);
        Register soff = asRegister(spValue);
        Register send = asRegister(slValue);
        Register dst = asRegister(dstValue);
        Register doff = asRegister(dpValue);
        Register isURL = asRegister(urlValue);

        Register length = send;

        Label labelProcessData = new Label();
        Label labelProcess64B = new Label();
        Label labelProcess32B = new Label();
        Label labelProcess4B = new Label();
        Label labelSIMDEnter = new Label();
        Label labelSIMDExit = new Label();
        Label labelExit = new Label();

        Register simdCodec = r6;
        Register nosimdCodec = r7;

        try (AArch64MacroAssembler.ScratchRegister scratchReg1 = masm.getScratchRegister()) {
            Register rscratch1 = scratchReg1.getRegister();

            masm.add(64, src, src, soff);
            masm.add(64, dst, dst, doff);

            masm.mov(64, doff, dst);

            masm.sub(64, length, send, soff);
            masm.bfm(64, length, zr, 0, 1);

            crb.recordDataReferenceInCode(FROM_BASE64_FOR_NOSIMD_DATA);
            masm.adrpAdd(nosimdCodec);
            masm.cbz(64, isURL, labelProcessData);

            crb.recordDataReferenceInCode(FROM_BASE64_URL_FOR_NOSIMD_DATA);
            masm.adrpAdd(nosimdCodec);

            masm.bind(labelProcessData);
            masm.mov(64, rscratch1, length);
            masm.compare(64, length, 144); // 144 = 80 + 64
            masm.branchConditionally(ConditionFlag.LT, labelProcess4B);

            // In the MIME case, the line length cannot be more than 76
            // bytes (see RFC 2045). This is too short a block for SIMD
            // to be worthwhile, so we use non-SIMD here.
            masm.mov(rscratch1, 79);

            masm.bind(labelProcess4B);
            masm.ldr(32, r14, AArch64Address.createImmediateAddress(32, IMMEDIATE_POST_INDEXED, src, 4));
            masm.ubfx(32, r16, r14, 0, 8);
            masm.ubfx(32, r11, r14, 8, 8);
            masm.ubfx(32, r12, r14, 16, 8);
            masm.ubfx(32, r13, r14, 24, 8);
            // get the de-code
            masm.ldr(8, r16, AArch64Address.createExtendedRegisterOffsetAddress(8, nosimdCodec, r16, true, UXTW));
            masm.ldr(8, r11, AArch64Address.createExtendedRegisterOffsetAddress(8, nosimdCodec, r11, true, UXTW));
            masm.ldr(8, r12, AArch64Address.createExtendedRegisterOffsetAddress(8, nosimdCodec, r12, true, UXTW));
            masm.ldr(8, r13, AArch64Address.createExtendedRegisterOffsetAddress(8, nosimdCodec, r13, true, UXTW));
            // error detection, 255u indicates an illegal input
            masm.orr(32, r14, r16, r11);
            masm.orr(32, r15, r12, r13);
            masm.orr(32, r14, r14, r15);
            masm.tbnz(r14, 7, labelExit);
            // recover the data
            masm.lsl(32, r14, r16, 10);
            masm.bfi(32, r14, r11, 4, 6);
            masm.bfm(32, r14, r12, 2, 5);
            masm.rev16(32, r14, r14);
            masm.bfi(32, r13, r12, 6, 2);
            masm.str(16, r14, AArch64Address.createImmediateAddress(16, IMMEDIATE_POST_INDEXED, dst, 2));
            masm.str(8, r13, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
            // non-simd loop
            masm.subs(32, rscratch1, rscratch1, 4);
            masm.branchConditionally(ConditionFlag.GT, labelProcess4B);

            // if exiting from PreProcess80B, rscratch1 == -1;
            // otherwise, rscratch1 == 0.
            masm.cbz(32, rscratch1, labelExit);
            masm.sub(64, length, length, 80);

            crb.recordDataReferenceInCode(FROM_BASE64_FOR_SIMD_DATA);
            masm.adrpAdd(simdCodec);
            masm.cbz(64, isURL, labelSIMDEnter);

            crb.recordDataReferenceInCode(FROM_BASE64_URL_FOR_SIMD_DATA);
            masm.adrpAdd(simdCodec);

            masm.bind(labelSIMDEnter);
            masm.neon.ld1MultipleVVVV(FullReg, Byte, v0, v1, v2, v3,
                            AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, Byte, simdCodec, 64));
            masm.neon.ld1MultipleVVVV(FullReg, Byte, v4, v5, v6, v7, AArch64Address.createStructureNoOffsetAddress(simdCodec));
            masm.mov(rscratch1, 63L);
            masm.neon.dupVG(FullReg, Byte, v27, rscratch1);

            masm.bind(labelProcess64B);
            masm.compare(64, length, 64);
            masm.branchConditionally(ConditionFlag.LT, labelProcess32B);
            generateBase64DecodeSimdround(masm, src, dst, v0, v4, 16, labelExit);
            masm.sub(64, length, length, 64);
            masm.jmp(labelProcess64B);

            masm.bind(labelProcess32B);
            masm.compare(64, length, 32);
            masm.branchConditionally(ConditionFlag.LT, labelSIMDExit);
            generateBase64DecodeSimdround(masm, src, dst, v0, v4, 8, labelExit);
            masm.sub(64, length, length, 32);
            masm.jmp(labelProcess32B);

            masm.bind(labelSIMDExit);
            masm.cbz(64, length, labelExit);
            masm.mov(32, rscratch1, length);
            masm.jmp(labelProcess4B);

            masm.bind(labelExit);
            masm.sub(64, asRegister(resultValue), dst, doff);
        }
    }
}
