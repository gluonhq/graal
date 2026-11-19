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
package jdk.graal.compiler.lir.amd64.vector;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSHUFB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftImmOp.VPSLLDQ;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.Arrays;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Byte-wise compress fallback used when native byte-compress instructions are unavailable.
 *
 * All ops in this class expect the predicate mask as a scalar bitmask in a GPR
 * ({@link CompressBytesWithMaskOp}), with one bit per input byte lane. If the mask is still in
 * vector form (vector of {@code 0x00}/{@code 0xFF} bytes), the frontend must first extract the
 * scalar mask with {@code VPMOVMSKB}. Native AVX-512 byte-compress uses dedicated EVEX ops.
 */
public final class AVXByteCompress {

    /** Bytes in one 128-bit lane. */
    private static final int LANE_WIDTH = 16;
    /** Bytes in one half-lane. */
    private static final int HALF_LANE_WIDTH = 8;
    /** Mask bits consumed per half-lane. */
    private static final int HALF_LANE_MASK_BITS = 8;

    /**
     * 256 x 16-byte {@code VPSHUFB} selectors used to compress one 8-byte half-lane. There is one
     * 16-byte selector for each possible 8-bit mask value ({@code 0..255}). The selector packs
     * selected source bytes first and uses {@code 0x80} in remaining slots so {@code VPSHUFB}
     * zero-fills non-selected output bytes.
     */
    private static final ArrayDataPointerConstant HALF_LANE_COMPRESS_SELECTORS = new ArrayDataPointerConstant(buildHalfLaneCompressSelectors(), 32);
    /** 9 x 16-byte selectors to merge the two half-lane compress results in a 128-bit lane. */
    private static final ArrayDataPointerConstant MERGE_SELECTORS_128 = new ArrayDataPointerConstant(buildMergeSelectors128(), 16);
    /**
     * Adds +8 to selector bytes so the upper half-lane can be addressed in the same source lane.
     */
    private static final ArrayDataPointerConstant HIGH_HALF_OFFSET = new ArrayDataPointerConstant(buildFilledBytes(LANE_WIDTH, (byte) HALF_LANE_WIDTH), 16);

    /**
     * Builds the selector table used by the first stage of compression. For each possible 8-bit
     * half-lane mask, this produces one 16-byte {@code VPSHUFB} selector that packs selected source
     * bytes to the front and marks remaining bytes for zero-fill.
     */
    private static byte[] buildHalfLaneCompressSelectors() {
        byte[] table = new byte[(1 << HALF_LANE_MASK_BITS) * LANE_WIDTH];
        Arrays.fill(table, (byte) 0x80);
        for (int mask = 0; mask < (1 << HALF_LANE_MASK_BITS); mask++) {
            int base = mask * LANE_WIDTH;
            int dst = 0;
            for (int src = 0; src < HALF_LANE_WIDTH; src++) {
                if ((mask & (1 << src)) != 0) {
                    table[base + dst++] = (byte) src;
                }
            }
        }
        return table;
    }

    /**
     * Builds the merge-selector table for one 128-bit lane after half-lane compression. Each entry
     * corresponds to the number of selected bytes in the low half and rearranges bytes so that
     * low-half results are followed immediately by high-half results.
     */
    private static byte[] buildMergeSelectors128() {
        byte[] table = new byte[(HALF_LANE_WIDTH + 1) * LANE_WIDTH];
        for (int lowerCount = 0; lowerCount <= HALF_LANE_WIDTH; lowerCount++) {
            int base = lowerCount * LANE_WIDTH;
            for (int i = 0; i < LANE_WIDTH; i++) {
                if (i < lowerCount) {
                    table[base + i] = (byte) i;
                } else {
                    int hiIndex = i - lowerCount;
                    table[base + i] = hiIndex < HALF_LANE_WIDTH ? (byte) (HALF_LANE_WIDTH + hiIndex) : (byte) 0x80;
                }
            }
        }
        return table;
    }

    private static byte[] buildFilledBytes(int size, byte value) {
        byte[] table = new byte[size];
        Arrays.fill(table, value);
        return table;
    }

    /**
     * Byte-wise compress using a pre-computed scalar mask in a GPR.
     *
     * XMM stays register-only. YMM and ZMM assemble their final result through a stack buffer.
     */
    public static final class CompressBytesWithMaskOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<CompressBytesWithMaskOp> TYPE = LIRInstructionClass.create(CompressBytesWithMaskOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Alive({OperandFlag.REG}) protected AllocatableValue source;
        @Alive({OperandFlag.REG}) protected AllocatableValue mask;

        @Temp({OperandFlag.REG}) protected AllocatableValue tableBase;
        @Temp({OperandFlag.REG}) protected AllocatableValue indexScratch;
        @Temp({OperandFlag.REG}) protected AllocatableValue halfCount;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue mergeBufferBase;
        /*
         * Must be stack-backed: YMM and ZMM merge steps store compressed chunks at runtime-computed
         * byte offsets.
         */
        @Temp({OperandFlag.STACK, OperandFlag.ILLEGAL}) protected AllocatableValue mergeBuffer;

        @Temp({OperandFlag.REG}) protected AllocatableValue selectorScratch;
        @Temp({OperandFlag.REG}) protected AllocatableValue compressed;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue tmp;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue highMask;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue appendOffset;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue work;

        public CompressBytesWithMaskOp(LIRGeneratorTool tool, AllocatableValue result, AllocatableValue source, AllocatableValue mask) {
            super(TYPE, AVXKind.getRegisterSize(result));
            this.result = result;
            this.source = source;
            this.mask = mask;

            this.tableBase = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.indexScratch = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
            this.halfCount = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
            if (size == XMM) {
                this.mergeBufferBase = Value.ILLEGAL;
                this.mergeBuffer = Value.ILLEGAL;
            } else if (size == YMM) {
                this.mergeBufferBase = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.mergeBuffer = tool.getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(AMD64Kind.V256_BYTE));
            } else if (size == ZMM) {
                this.mergeBufferBase = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.mergeBuffer = tool.getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(AMD64Kind.V512_BYTE));
            } else {
                throw GraalError.shouldNotReachHereUnexpectedValue(size);
            }
            this.selectorScratch = tool.newVariable(LIRKind.value(AMD64Kind.V128_BYTE));
            this.compressed = tool.newVariable(LIRKind.value(AMD64Kind.V128_BYTE));
            this.tmp = size == XMM ? Value.ILLEGAL : tool.newVariable(LIRKind.value(AMD64Kind.V256_BYTE));
            if (size == ZMM) {
                this.highMask = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.appendOffset = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
                this.work = tool.newVariable(LIRKind.value(AMD64Kind.V256_BYTE));
            } else {
                this.highMask = Value.ILLEGAL;
                this.appendOffset = Value.ILLEGAL;
                this.work = Value.ILLEGAL;
            }
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register resultReg = asRegister(result);
            Register sourceReg = asRegister(source);
            Register maskReg = asRegister(mask);

            if (size == XMM) {
                emit128WithMask(crb, masm, resultReg, sourceReg, maskReg);
                return;
            }

            Register mergeBufferBaseReg = asRegister(mergeBufferBase);
            masm.leaq(mergeBufferBaseReg, (AMD64Address) crb.asAddress(mergeBuffer));
            if (size == YMM) {
                emit256WithMask(crb, masm, resultReg, sourceReg, maskReg, mergeBufferBaseReg);
            } else if (size == ZMM) {
                emit512WithMask(crb, masm, resultReg, sourceReg, maskReg, mergeBufferBaseReg);
            } else {
                throw GraalError.shouldNotReachHereUnexpectedValue(size);
            }
        }

        private void emit128WithMask(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register resultReg, Register sourceReg, Register maskReg) {
            emitCompressLane128(crb, masm, sourceReg, maskReg, 0, resultReg, asRegister(compressed));
        }

        /**
         * Emits byte-wise compress for one 256-bit payload from scalar mask bits.
         *
         * The final 256-bit result is assembled in memory at {@code [mergeBufferBaseReg]} and then
         * loaded into {@code resultReg}.
         */
        private void emit256WithMask(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                        Register resultReg, Register sourceReg,
                        Register maskReg, Register mergeBufferBaseReg) {
            Register halfCountReg = asRegister(halfCount);
            Register compressedReg = asRegister(compressed);
            Register tmpReg = asRegister(tmp);

            /*
             * Compress lower 128-bit lane: split mask into two 8-bit halves, compress each half
             * with a shared 256-entry selector table, then merge according to the lower-half count.
             */
            emitCompressLane128(crb, masm, sourceReg, maskReg, 0, compressedReg, tmpReg);

            /* Initialize merge buffer and store compressed low 128-bit lane. */
            VPXOR.emit(masm, YMM, tmpReg, tmpReg, tmpReg);
            VMOVDQU32.emit(masm, YMM, new AMD64Address(mergeBufferBaseReg), tmpReg);
            VMOVDQU32.emit(masm, XMM, new AMD64Address(mergeBufferBaseReg), compressedReg);

            /*
             * Compress upper 128-bit lane with the same half-lane selector table.
             */
            VEXTRACTI128.emit(masm, YMM, tmpReg, sourceReg, 1);
            emitCompressLane128(crb, masm, tmpReg, maskReg, 2 * HALF_LANE_MASK_BITS, compressedReg, tmpReg);

            /*
             * Store compressed high 128-bit lane behind low-lane payload using runtime popcount.
             */
            masm.movl(halfCountReg, maskReg);
            masm.andl(halfCountReg, 0xFFFF);
            masm.popcntl(halfCountReg, halfCountReg);
            VMOVDQU32.emit(masm, XMM, new AMD64Address(mergeBufferBaseReg, halfCountReg, Stride.S1), compressedReg);

            /* Load merged 32-byte result. */
            VMOVDQU32.emit(masm, YMM, resultReg, new AMD64Address(mergeBufferBaseReg));
        }

        private void emit512WithMask(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                        Register resultReg, Register sourceReg, Register maskReg, Register mergeBufferBaseReg) {
            Register halfCountReg = asRegister(halfCount);
            Register highMaskReg = asRegister(highMask);
            Register appendOffsetReg = asRegister(appendOffset);
            Register workReg = asRegister(work);
            Register compressedReg = asRegister(compressed);
            Register tmpReg = asRegister(tmp);

            /* Split the 64-bit predicate mask into low/high 32-bit halves. */
            masm.movq(highMaskReg, maskReg);
            masm.shrq(highMaskReg, 32);

            /* Clear the 64-byte output buffer once. */
            EVPXORD.emit(masm, YMM, tmpReg, tmpReg, tmpReg);
            VMOVDQU32.emit(masm, YMM, new AMD64Address(mergeBufferBaseReg), tmpReg);
            VMOVDQU32.emit(masm, YMM, new AMD64Address(mergeBufferBaseReg, 32), tmpReg);

            /* Compress lanes 0 and 1 (low 256 bits), store lane 1 behind lane 0. */
            VMOVDQU32.emit(masm, YMM, workReg, sourceReg);
            emitCompressLane128(crb, masm, workReg, maskReg, 0, compressedReg, tmpReg);
            VMOVDQU32.emit(masm, XMM, new AMD64Address(mergeBufferBaseReg), compressedReg);
            masm.movl(appendOffsetReg, maskReg);
            masm.andl(appendOffsetReg, 0xFFFF);
            masm.popcntl(appendOffsetReg, appendOffsetReg);
            VEXTRACTI128.emit(masm, YMM, tmpReg, workReg, 1);
            emitCompressLane128(crb, masm, tmpReg, maskReg, 2 * HALF_LANE_MASK_BITS, compressedReg, tmpReg);
            VMOVDQU32.emit(masm, XMM, new AMD64Address(mergeBufferBaseReg, appendOffsetReg, Stride.S1), compressedReg);

            /* Compute the append offset at the end of the low 256-bit compressed payload. */
            masm.movl(halfCountReg, maskReg);
            masm.shrl(halfCountReg, 16);
            masm.popcntl(halfCountReg, halfCountReg);
            masm.addl(appendOffsetReg, halfCountReg);

            /* Compress lanes 2 and 3 (high 256 bits), append after low 256-bit payload. */
            EVEXTRACTI64X4.emit(masm, ZMM, workReg, sourceReg, 1);
            emitCompressLane128(crb, masm, workReg, highMaskReg, 0, compressedReg, tmpReg);
            VMOVDQU32.emit(masm, XMM, new AMD64Address(mergeBufferBaseReg, appendOffsetReg, Stride.S1), compressedReg);
            masm.movl(halfCountReg, highMaskReg);
            masm.andl(halfCountReg, 0xFFFF);
            masm.popcntl(halfCountReg, halfCountReg);
            masm.addl(appendOffsetReg, halfCountReg);
            VEXTRACTI128.emit(masm, YMM, tmpReg, workReg, 1);
            emitCompressLane128(crb, masm, tmpReg, highMaskReg, 2 * HALF_LANE_MASK_BITS, compressedReg, tmpReg);
            VMOVDQU32.emit(masm, XMM, new AMD64Address(mergeBufferBaseReg, appendOffsetReg, Stride.S1), compressedReg);

            /* Load merged 64-byte result. */
            EVMOVDQU64.emit(masm, ZMM, resultReg, new AMD64Address(mergeBufferBaseReg));
        }

        /**
         * Compresses one 128-bit lane by combining two half-lane shuffles and then merges them
         * based on the selected-byte count of the low half.
         *
         * @param laneMaskShift number of bits to right-shift {@code mask} to align this lane's
         *            low-half mask byte (0 for the low 128-bit lane, 16 for the high 128-bit lane
         *            in a 256-bit vector)
         * @param shiftedHighReg scratch register used for the upper-half shuffle path. This
         *            register may alias {@code source}; if it does, {@code source} is clobbered by
         *            the end of this method.
         */
        private void emitCompressLane128(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                        Register sourceReg, Register maskReg, int laneMaskShift,
                        Register compressedReg, Register shiftedHighReg) {
            Register tableBaseReg = asRegister(tableBase);
            Register indexReg = asRegister(indexScratch);
            Register halfCountReg = asRegister(halfCount);
            Register selectorScratchReg = asRegister(selectorScratch);

            /* Decode low-half mask and compute its selected-byte count. */
            masm.movl(indexReg, maskReg);
            masm.shrl(indexReg, laneMaskShift);
            masm.andl(indexReg, 0xFF);
            masm.popcntl(halfCountReg, indexReg);
            masm.shll(indexReg, 4);

            /* Compress low half-lane with the shared half-lane selector table. */
            masm.leaq(tableBaseReg, (AMD64Address) crb.recordDataReferenceInCode(HALF_LANE_COMPRESS_SELECTORS));
            VPSHUFB.emit(masm, XMM, compressedReg, sourceReg, new AMD64Address(tableBaseReg, indexReg, Stride.S1));

            /* Decode high-half mask and load the corresponding selector. */
            masm.movl(indexReg, maskReg);
            int upperHalfMaskShift = laneMaskShift + HALF_LANE_MASK_BITS;
            masm.shrl(indexReg, upperHalfMaskShift);
            masm.andl(indexReg, 0xFF);
            masm.shll(indexReg, 4);
            VMOVDQU32.emit(masm, XMM, selectorScratchReg, new AMD64Address(tableBaseReg, indexReg, Stride.S1));

            /* Rebase selector to bytes 8..15, compress, shift, and OR with low-half result. */
            masm.leaq(tableBaseReg, (AMD64Address) crb.recordDataReferenceInCode(HIGH_HALF_OFFSET));
            VPADDB.emit(masm, XMM, selectorScratchReg, selectorScratchReg, new AMD64Address(tableBaseReg));
            VPSHUFB.emit(masm, XMM, shiftedHighReg, sourceReg, selectorScratchReg);
            VPSLLDQ.emit(masm, XMM, shiftedHighReg, shiftedHighReg, HALF_LANE_WIDTH);
            VPOR.emit(masm, XMM, compressedReg, compressedReg, shiftedHighReg);

            /* Final in-lane merge is indexed by the low-half selected-byte count. */
            masm.shll(halfCountReg, 4);
            masm.leaq(tableBaseReg, (AMD64Address) crb.recordDataReferenceInCode(MERGE_SELECTORS_128));
            VPSHUFB.emit(masm, XMM, compressedReg, compressedReg, new AMD64Address(tableBaseReg, halfCountReg, Stride.S1));
        }
    }
}
