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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD3_MULTIPLE_3R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST4_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.HalfReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Byte;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ExtendType.UXTW;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r3;
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
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d91e3ef51121f605d6060d61571a2266adb74ca0/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L9754-L9897",
          sha1 = "8005cf9d9467368c035596095058ac2ae1cd005d")
// @formatter:on
@Opcode("AARCH64_BASE64_ENCODE_BLOCK")
public final class AArch64Base64EncodeOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64Base64EncodeOp> TYPE = LIRInstructionClass.create(AArch64Base64EncodeOp.class);

    private static final ArrayDataPointerConstant TO_BASE64_DATA = pointerConstant(16, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes());
    private static final ArrayDataPointerConstant TO_BASE64_URL_DATA = pointerConstant(16, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".getBytes());

    @Use({OperandFlag.REG}) private Value srcValue;
    @Alive({OperandFlag.REG}) private Value spValue;
    @Alive({OperandFlag.REG}) private Value slValue;
    @Use({OperandFlag.REG}) private Value dstValue;
    @Alive({OperandFlag.REG}) private Value dpValue;
    @Alive({OperandFlag.REG}) private Value urlValue;

    @Temp({OperandFlag.REG}) private Value[] temps;
    @Temp({OperandFlag.REG}) private Value[] vectorTemps;

    public AArch64Base64EncodeOp(AllocatableValue srcValue, AllocatableValue spValue, AllocatableValue slValue, AllocatableValue dstValue, AllocatableValue dpValue,
                    AllocatableValue urlValue) {
        super(TYPE);
        this.srcValue = srcValue;
        this.spValue = spValue;
        this.slValue = slValue;
        this.dstValue = dstValue;
        this.dpValue = dpValue;
        this.urlValue = urlValue;

        GraalError.guarantee(srcValue instanceof RegisterValue srcValueReg && r0.equals(srcValueReg.getRegister()), "srcValue should be fixed to r0, but is %s", srcValue);
        GraalError.guarantee(dstValue instanceof RegisterValue dstValueReg && r3.equals(dstValueReg.getRegister()), "dstValue should be fixed to r3, but is %s", dstValue);

        this.temps = new Value[]{
                        r0.asValue(), r3.asValue(), // overwritten
                        r6.asValue(), r7.asValue(),
                        r11.asValue(), r12.asValue(), r13.asValue(),
                        r14.asValue(), r15.asValue(), r16.asValue(),
        };
        this.vectorTemps = new Value[]{
                        v0.asValue(), v1.asValue(), v2.asValue(), v3.asValue(),
                        v4.asValue(), v5.asValue(), v6.asValue(),
                        v16.asValue(), v17.asValue(), v18.asValue(), v19.asValue(),
                        v20.asValue(), v21.asValue(), v22.asValue(), v23.asValue(),
        };
    }

    private static void generateBase64EncodeSimdround(AArch64MacroAssembler masm, Register src, Register dst, Register codec, int size) {
        Register in0 = v4;
        Register in1 = v5;
        Register in2 = v6;
        Register out0 = v16;
        Register out1 = v17;
        Register out2 = v18;
        Register out3 = v19;
        Register ind0 = v20;
        Register ind1 = v21;
        Register ind2 = v22;
        Register ind3 = v23;

        AArch64ASIMDAssembler.ASIMDSize arrangement = size == 16 ? FullReg : HalfReg;

        masm.neon.ld3MultipleVVV(arrangement, Byte, in0, in1, in2,
                        AArch64Address.createStructureImmediatePostIndexAddress(LD3_MULTIPLE_3R, arrangement, Byte, src, 3 * size));

        masm.neon.ushrVVI(arrangement, Byte, ind0, in0, 2);

        masm.neon.ushrVVI(arrangement, Byte, ind1, in1, 2);
        masm.neon.shlVVI(arrangement, Byte, in0, in0, 6);
        masm.neon.orrVVV(arrangement, ind1, ind1, in0);
        masm.neon.ushrVVI(arrangement, Byte, ind1, ind1, 2);

        masm.neon.ushrVVI(arrangement, Byte, ind2, in2, 4);
        masm.neon.shlVVI(arrangement, Byte, in1, in1, 4);
        masm.neon.orrVVV(arrangement, ind2, in1, ind2);
        masm.neon.ushrVVI(arrangement, Byte, ind2, ind2, 2);

        masm.neon.shlVVI(arrangement, Byte, ind3, in2, 2);
        masm.neon.ushrVVI(arrangement, Byte, ind3, ind3, 2);

        masm.neon.tblVVV(arrangement, out0, codec, 4, ind0);
        masm.neon.tblVVV(arrangement, out1, codec, 4, ind1);
        masm.neon.tblVVV(arrangement, out2, codec, 4, ind2);
        masm.neon.tblVVV(arrangement, out3, codec, 4, ind3);

        masm.neon.st4MultipleVVVV(arrangement, Byte, out0, out1, out2, out3,
                        AArch64Address.createStructureImmediatePostIndexAddress(ST4_MULTIPLE_4R, arrangement, Byte, dst, 4 * size));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register src = asRegister(srcValue); // source array
        Register soff = asRegister(spValue); // source start offset
        Register send = asRegister(slValue); // source end offset
        Register dst = asRegister(dstValue); // dest array
        Register doff = asRegister(dpValue); // position for writing to dest array
        Register isURL = asRegister(urlValue); // Base64 or URL character set

        // c_rarg6 and c_rarg7 are free to use as temps
        Register codec = r6;
        Register length = r7;

        Register rscratch1 = r16;
        Register rscratch2 = r11;
        Register rscratch3 = r12;
        Register rscratch4 = r13;
        Register rscratch5 = r14;
        Register rscratch6 = r15;

        Label labelProcessData = new Label();
        Label labelProcess48B = new Label();
        Label labelProcess24B = new Label();
        Label labelProcess3B = new Label();
        Label labelSIMDExit = new Label();
        Label labelExit = new Label();

        masm.add(64, src, src, soff);
        masm.add(64, dst, dst, doff);
        masm.sub(64, length, send, soff);

        // load the codec base address
        crb.recordDataReferenceInCode(TO_BASE64_DATA);
        masm.adrpAdd(codec);
        masm.cbz(64, isURL, labelProcessData);
        crb.recordDataReferenceInCode(TO_BASE64_URL_DATA);
        masm.adrpAdd(codec);

        masm.bind(labelProcessData);

        // too short to formup a SIMD loop, roll back
        masm.compare(64, length, 24);
        masm.branchConditionally(ConditionFlag.LT, labelProcess3B);

        masm.neon.ld1MultipleVVVV(FullReg, Byte, v0, v1, v2, v3, AArch64Address.createStructureNoOffsetAddress(codec));

        masm.bind(labelProcess48B);
        masm.compare(64, length, 48);
        masm.branchConditionally(ConditionFlag.LT, labelProcess24B);
        generateBase64EncodeSimdround(masm, src, dst, v0, 16);
        masm.sub(64, length, length, 48);
        masm.jmp(labelProcess48B);

        masm.bind(labelProcess24B);
        masm.compare(64, length, 24);
        masm.branchConditionally(ConditionFlag.LT, labelSIMDExit);
        generateBase64EncodeSimdround(masm, src, dst, v0, 8);
        masm.sub(64, length, length, 24);

        masm.bind(labelSIMDExit);
        masm.cbz(64, length, labelExit);

        masm.bind(labelProcess3B);
        // 3 src bytes, 24 bits
        masm.ldr(8, rscratch1, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, src, 1));
        masm.ldr(8, rscratch2, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, src, 1));
        masm.ldr(8, rscratch3, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, src, 1));
        masm.orr(32, rscratch2, rscratch2, rscratch1, ShiftType.LSL, 8);
        masm.orr(32, rscratch3, rscratch3, rscratch2, ShiftType.LSL, 8);
        // codec index
        masm.ubfm(32, rscratch6, rscratch3, 18, 23);
        masm.ubfm(32, rscratch5, rscratch3, 12, 17);
        masm.ubfm(32, rscratch4, rscratch3, 6, 11);
        masm.and(32, rscratch3, rscratch3, 63);
        // get the code based on the codec
        masm.ldr(8, rscratch6, AArch64Address.createExtendedRegisterOffsetAddress(8, codec, rscratch6, true, UXTW));
        masm.ldr(8, rscratch5, AArch64Address.createExtendedRegisterOffsetAddress(8, codec, rscratch5, true, UXTW));
        masm.ldr(8, rscratch4, AArch64Address.createExtendedRegisterOffsetAddress(8, codec, rscratch4, true, UXTW));
        masm.ldr(8, rscratch3, AArch64Address.createExtendedRegisterOffsetAddress(8, codec, rscratch3, true, UXTW));
        masm.str(8, rscratch6, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
        masm.str(8, rscratch5, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
        masm.str(8, rscratch4, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
        masm.str(8, rscratch3, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, dst, 1));
        masm.sub(64, length, length, 3);
        masm.cbnz(64, length, labelProcess3B);

        masm.bind(labelExit);
    }

}
