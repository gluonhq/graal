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
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.DoubleWord;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Word;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PRE_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
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
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L7497-L7525",
          sha1 = "a07c53e08274ac8379166a2e34b166796cc27921")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L4707-L4763",
          sha1 = "bccd11a5d5bd69d71697d573b68e3199dbf38e12")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L4765-L4876",
          sha1 = "6aea9147fd81e4bff3324267edaeee8f4a1a9a7c")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L4878-L5003",
          sha1 = "0c88cbd27b58a3029062580159d90bc05eef808a")
// @formatter:on
@Opcode("AARCH64_CRC32C_UPDATE_BYTES")
public final class AArch64CRC32CUpdateBytesOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64CRC32CUpdateBytesOp> TYPE = LIRInstructionClass.create(AArch64CRC32CUpdateBytesOp.class);
    private static final int CRC32C_TABLE_OFFSET = 4 * 256 * Integer.BYTES + 8 * Integer.BYTES + 0x50;

    @Def private AllocatableValue resultValue;
    @Use private AllocatableValue crcValue;
    @Use private AllocatableValue bufValue;
    @Use private AllocatableValue lenValue;

    @Temp private Value[] temps;

    public AArch64CRC32CUpdateBytesOp(AllocatableValue resultValue, AllocatableValue crcValue, AllocatableValue bufValue, AllocatableValue lenValue) {
        super(TYPE);
        this.resultValue = resultValue;
        this.crcValue = crcValue;
        this.bufValue = bufValue;
        this.lenValue = lenValue;
        GraalError.guarantee(resultValue instanceof RegisterValue resultReg && r0.equals(resultReg.getRegister()), "result should be fixed to r0, but is %s", resultValue);
        GraalError.guarantee(crcValue instanceof RegisterValue crcReg && r0.equals(crcReg.getRegister()), "crc should be fixed to r0, but is %s", crcValue);
        GraalError.guarantee(bufValue instanceof RegisterValue bufReg && r1.equals(bufReg.getRegister()), "bufferAddress should be fixed to r1, but is %s", bufValue);
        GraalError.guarantee(lenValue instanceof RegisterValue lenReg && r2.equals(lenReg.getRegister()), "length should be fixed to r2, but is %s", lenValue);
        this.temps = new Value[]{
                        r1.asValue(), r2.asValue(),
                        r3.asValue(), r4.asValue(), r5.asValue(), r6.asValue(),
                        v0.asValue(), v1.asValue(), v2.asValue(), v3.asValue(),
                        v4.asValue(), v5.asValue(), v6.asValue(), v7.asValue(),
                        v16.asValue(), v17.asValue(), v18.asValue(), v19.asValue(),
                        v20.asValue(), v21.asValue(), v22.asValue(), v23.asValue(),
                        v24.asValue(), v25.asValue(), v26.asValue(), v27.asValue(),
                        v28.asValue(), v29.asValue(), v30.asValue(), v31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(masm.supports(CPUFeature.CRC32), "CRC32C update bytes requires CRC32 support");

        Register crc = asRegister(crcValue);
        Register buf = asRegister(bufValue);
        Register len = asRegister(lenValue);

        emitKernelCRC32C(crb, masm, crc, buf, len, r3, r4, r5, r6);
    }

    // @formatter:off
    /**
     * @param crc   register containing existing CRC (32-bit)
     * @param buf   register pointing to input byte buffer (byte*)
     * @param len   register containing number of bytes
     * @param tmp0  scratch register
     * @param tmp1  scratch register
     * @param tmp2  scratch register
     * @param tmp3  scratch register
     */
    // @formatter:on
    private static void emitKernelCRC32C(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register crc, Register buf, Register len,
                    Register tmp0, Register tmp1, Register tmp2, Register tmp3) {
        if (masm.supports(CPUFeature.PMULL, CPUFeature.SHA3)) {
            emitKernelCRC32CUsingCryptoPmull(crb, masm, crc, buf, len, tmp0, tmp1, tmp2, tmp3);
        } else {
            emitKernelCRC32CUsingCRC32C(masm, crc, buf, len, tmp0, tmp1, tmp2, tmp3);
        }
    }

    private static void emitKernelCRC32CUsingCryptoPmull(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register crc, Register buf, Register len,
                    Register tmp0, Register tmp1, Register tmp2, Register tmp3) {
        Label crcBy4Loop = new Label();
        Label crcBy1Loop = new Label();
        Label crcLess128 = new Label();
        Label crcBy128Pre = new Label();
        Label crcBy32Loop = new Label();
        Label crcLess32 = new Label();
        Label lExit = new Label();

        masm.subs(64, tmp0, len, 384);
        masm.branchConditionally(ConditionFlag.GE, crcBy128Pre);

        masm.bind(crcLess128);
        masm.subs(64, len, len, 32);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);

        masm.bind(crcLess32);
        masm.adds(64, len, len, 32 - 4);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy32Loop);
        masm.ldp(64, tmp0, tmp1, AArch64Address.createPairBaseRegisterOnlyAddress(64, buf));
        masm.crc32c(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 16));
        masm.crc32c(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 24));
        masm.crc32c(64, crc, crc, tmp2);
        masm.add(64, buf, buf, 32);
        masm.subs(64, len, len, 32);
        masm.crc32c(64, crc, crc, tmp3);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);
        masm.adds(64, zr, len, 32);
        masm.branchConditionally(ConditionFlag.NE, crcLess32);
        masm.jmp(lExit);

        masm.bind(crcBy4Loop);
        masm.ldr(32, tmp0, AArch64Address.createImmediateAddress(32, IMMEDIATE_POST_INDEXED, buf, 4));
        masm.subs(64, len, len, 4);
        masm.crc32c(32, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.LE, lExit);

        masm.bind(crcBy1Loop);
        masm.ldr(8, tmp0, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, buf, 1));
        masm.subs(64, len, len, 1);
        masm.crc32c(8, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy128Pre);
        emitCRC32CommonFoldUsingCryptoPmull(crb, masm, crc, buf, len, tmp0, tmp1);
        masm.mov(64, crc, zr);
        masm.crc32c(64, crc, crc, tmp0);
        masm.crc32c(64, crc, crc, tmp1);
        masm.cbnz(64, len, crcLess128);

        masm.bind(lExit);
    }

    private static void emitCRC32CommonFoldUsingCryptoPmull(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register crc, Register buf,
                    Register len, Register tmp0, Register tmp1) {
        Label crcBy128Loop = new Label();
        int tableOffset = CRC32C_TABLE_OFFSET;

        masm.sub(64, len, len, 256);
        Register table = tmp0;
        AArch64CRC32UpdateBytesOp.emitLoadCRC32TableAddress(crb, masm, table);
        masm.add(64, table, table, tableOffset);

        // Registers v0..v7 are used as data registers.
        // Registers v16..v31 are used as tmp registers.
        masm.sub(64, buf, buf, 0x10);

        masm.fldr(128, v0, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x10), false);
        masm.fldr(128, v1, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x20), false);
        masm.fldr(128, v2, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x30), false);
        masm.fldr(128, v3, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x40), false);
        masm.fldr(128, v4, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x50), false);
        masm.fldr(128, v5, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x60), false);
        masm.fldr(128, v6, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x70), false);
        masm.fldr(128, v7, AArch64Address.createImmediateAddress(128, IMMEDIATE_PRE_INDEXED, buf, 0x80), false);

        masm.neon.moviVI(FullReg, v31, 0);
        masm.neon.insXG(Word, v31, 0, crc);
        masm.neon.eorVVV(FullReg, v0, v0, v31);

        // Register v16 contains constants from the crc table.
        masm.fldr(128, v16, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0));
        masm.jmp(crcBy128Loop);

        masm.align(16);
        masm.bind(crcBy128Loop);
        masm.neon.pmullVVV(DoubleWord, v17, v0, v16);
        masm.neon.pmull2VVV(DoubleWord, v18, v0, v16);
        masm.fldr(128, v0, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x10));
        masm.neon.eor3VVVV(v0, v17, v18, v0);

        masm.neon.pmullVVV(DoubleWord, v19, v1, v16);
        masm.neon.pmull2VVV(DoubleWord, v20, v1, v16);
        masm.fldr(128, v1, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x20));
        masm.neon.eor3VVVV(v1, v19, v20, v1);

        masm.neon.pmullVVV(DoubleWord, v21, v2, v16);
        masm.neon.pmull2VVV(DoubleWord, v22, v2, v16);
        masm.fldr(128, v2, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x30));
        masm.neon.eor3VVVV(v2, v21, v22, v2);

        masm.neon.pmullVVV(DoubleWord, v23, v3, v16);
        masm.neon.pmull2VVV(DoubleWord, v24, v3, v16);
        masm.fldr(128, v3, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x40));
        masm.neon.eor3VVVV(v3, v23, v24, v3);

        masm.neon.pmullVVV(DoubleWord, v25, v4, v16);
        masm.neon.pmull2VVV(DoubleWord, v26, v4, v16);
        masm.fldr(128, v4, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x50));
        masm.neon.eor3VVVV(v4, v25, v26, v4);

        masm.neon.pmullVVV(DoubleWord, v27, v5, v16);
        masm.neon.pmull2VVV(DoubleWord, v28, v5, v16);
        masm.fldr(128, v5, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x60));
        masm.neon.eor3VVVV(v5, v27, v28, v5);

        masm.neon.pmullVVV(DoubleWord, v29, v6, v16);
        masm.neon.pmull2VVV(DoubleWord, v30, v6, v16);
        masm.fldr(128, v6, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x70));
        masm.neon.eor3VVVV(v6, v29, v30, v6);

        // Reuse registers v23, v24.
        // Using them won't block the first instruction of the next iteration.
        masm.neon.pmullVVV(DoubleWord, v23, v7, v16);
        masm.neon.pmull2VVV(DoubleWord, v24, v7, v16);
        masm.fldr(128, v7, AArch64Address.createImmediateAddress(128, IMMEDIATE_PRE_INDEXED, buf, 0x80));
        masm.neon.eor3VVVV(v7, v23, v24, v7);

        masm.subs(64, len, len, 0x80);
        masm.branchConditionally(ConditionFlag.GE, crcBy128Loop);

        // fold into 512 bits
        // Use v31 for constants because v16 can be still in use.
        masm.fldr(128, v31, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x10));
        masm.neon.pmullVVV(DoubleWord, v17, v0, v31);
        masm.neon.pmull2VVV(DoubleWord, v18, v0, v31);
        masm.neon.eor3VVVV(v0, v17, v18, v4);

        masm.neon.pmullVVV(DoubleWord, v19, v1, v31);
        masm.neon.pmull2VVV(DoubleWord, v20, v1, v31);
        masm.neon.eor3VVVV(v1, v19, v20, v5);

        masm.neon.pmullVVV(DoubleWord, v21, v2, v31);
        masm.neon.pmull2VVV(DoubleWord, v22, v2, v31);
        masm.neon.eor3VVVV(v2, v21, v22, v6);

        masm.neon.pmullVVV(DoubleWord, v23, v3, v31);
        masm.neon.pmull2VVV(DoubleWord, v24, v3, v31);
        masm.neon.eor3VVVV(v3, v23, v24, v7);

        // fold into 128 bits
        // Use v17 for constants because v31 can be still in use.
        masm.fldr(128, v17, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x20));
        masm.neon.pmullVVV(DoubleWord, v25, v0, v17);
        masm.neon.pmull2VVV(DoubleWord, v26, v0, v17);
        masm.neon.eor3VVVV(v3, v3, v25, v26);

        // Use v18 for constants because v17 can be still in use.
        masm.fldr(128, v18, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x30));
        masm.neon.pmullVVV(DoubleWord, v27, v1, v18);
        masm.neon.pmull2VVV(DoubleWord, v28, v1, v18);
        masm.neon.eor3VVVV(v3, v3, v27, v28);

        // Use v19 for constants because v18 can be still in use.
        masm.fldr(128, v19, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x40));
        masm.neon.pmullVVV(DoubleWord, v29, v2, v19);
        masm.neon.pmull2VVV(DoubleWord, v30, v2, v19);
        masm.neon.eor3VVVV(v0, v3, v29, v30);

        masm.add(64, len, len, 0x80);
        masm.add(64, buf, buf, 0x10);

        masm.neon.umovGX(DoubleWord, tmp0, v0, 0);
        masm.neon.umovGX(DoubleWord, tmp1, v0, 1);
    }

    private static void emitKernelCRC32CUsingCRC32C(AArch64MacroAssembler masm, Register crc, Register buf, Register len,
                    Register tmp0, Register tmp1, Register tmp2, Register tmp3) {
        Label crcBy64Loop = new Label();
        Label crcBy4Loop = new Label();
        Label crcBy1Loop = new Label();
        Label crcLess64 = new Label();
        Label crcBy64Pre = new Label();
        Label crcBy32Loop = new Label();
        Label crcLess32 = new Label();
        Label lExit = new Label();

        masm.subs(64, len, len, 128);
        masm.branchConditionally(ConditionFlag.GE, crcBy64Pre);

        masm.bind(crcLess64);
        masm.adds(64, len, len, 128 - 32);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);

        masm.bind(crcLess32);
        masm.adds(64, len, len, 32 - 4);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy32Loop);
        masm.ldp(64, tmp0, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, buf, 16));
        masm.subs(64, len, len, 32);
        masm.crc32c(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, buf, 8));
        masm.crc32c(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, buf, 8));
        masm.crc32c(64, crc, crc, tmp2);
        masm.crc32c(64, crc, crc, tmp3);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);
        masm.adds(64, zr, len, 32);
        masm.branchConditionally(ConditionFlag.NE, crcLess32);
        masm.jmp(lExit);

        masm.bind(crcBy4Loop);
        masm.ldr(32, tmp0, AArch64Address.createImmediateAddress(32, IMMEDIATE_POST_INDEXED, buf, 4));
        masm.subs(64, len, len, 4);
        masm.crc32c(32, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.LE, lExit);

        masm.bind(crcBy1Loop);
        masm.ldr(8, tmp0, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, buf, 1));
        masm.subs(64, len, len, 1);
        masm.crc32c(8, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy64Pre);
        masm.sub(64, buf, buf, 8);
        masm.ldp(64, tmp0, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, buf, 8));
        masm.crc32c(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 24));
        masm.crc32c(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 32));
        masm.crc32c(64, crc, crc, tmp2);
        masm.ldr(64, tmp0, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 40));
        masm.crc32c(64, crc, crc, tmp3);
        masm.ldr(64, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 48));
        masm.crc32c(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 56));
        masm.crc32c(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, buf, 64));
        masm.jmp(crcBy64Loop);

        masm.align(64);
        masm.bind(crcBy64Loop);
        masm.subs(64, len, len, 64);
        masm.crc32c(64, crc, crc, tmp2);
        masm.ldr(64, tmp0, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 8));
        masm.crc32c(64, crc, crc, tmp3);
        masm.ldr(64, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 16));
        masm.crc32c(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 24));
        masm.crc32c(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 32));
        masm.crc32c(64, crc, crc, tmp2);
        masm.ldr(64, tmp0, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 40));
        masm.crc32c(64, crc, crc, tmp3);
        masm.ldr(64, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 48));
        masm.crc32c(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 56));
        masm.crc32c(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, buf, 64));
        masm.branchConditionally(ConditionFlag.GE, crcBy64Loop);

        // post-loop
        masm.crc32c(64, crc, crc, tmp2);
        masm.crc32c(64, crc, crc, tmp3);
        masm.sub(64, len, len, 64);
        masm.add(64, buf, buf, 8);
        masm.adds(64, zr, len, 128);
        masm.branchConditionally(ConditionFlag.NE, crcLess64);

        masm.bind(lExit);
    }
}
