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
package jdk.graal.compiler.lir.alloc;

import java.util.ArrayList;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInsertionBuffer;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase;
import jdk.vm.ci.code.TargetDescription;

/**
 * Lowers {@code @UseKill} operands to short-lived variables by inserting moves immediately before
 * the consuming instructions.
 */
public final class UseKillMoveInjectionPhase extends PreAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
        new Optimization(lirGenRes, context.lirGen).apply();
    }

    private static final class Optimization {
        private final LIRGenerationResult lirGenRes;
        private final LIR lir;
        private final LIRGeneratorTool lirGen;

        private Optimization(LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen) {
            this.lirGenRes = lirGenRes;
            this.lir = lirGenRes.getLIR();
            this.lirGen = lirGen;
        }

        private void apply() {
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                rewriteBlock(block);
            }
        }

        private void rewriteBlock(BasicBlock<?> block) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            LIRInsertionBuffer buffer = new LIRInsertionBuffer();

            for (int i = 0; i < instructions.size(); i++) {
                final int insertionIndex = i;
                LIRInstruction op = instructions.get(i);
                op.forEachUseKill((instruction, value, mode, flags) -> {
                    if (!LIRValueUtil.isVariable(value)) {
                        return value;
                    }

                    if (!buffer.initialized()) {
                        buffer.init(instructions);
                    }

                    Variable movedValue = lirGen.newVariable(value.getValueKind());
                    LIRInstruction move = lirGen.getSpillMoveFactory().createMove(movedValue, value);
                    move.setComment(lirGenRes, "UseKillMoveInjection");
                    buffer.append(insertionIndex, move);
                    return movedValue;
                });
            }

            if (buffer.initialized()) {
                buffer.finish();
            }
        }
    }
}
