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
package jdk.graal.compiler.core.aarch64.test;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp.ValueMoveOp;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.jtt.LIRTest;
import jdk.graal.compiler.lir.jtt.LIRTestSpecification;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class AArch64UseKillLIRTest extends LIRTest {
    private static final UseKillSpec USE_KILL_SPEC = new UseKillSpec();

    private boolean sawUseKillOp;
    private boolean sawInjectedMove;
    private String inspectedSequence;

    @Before
    public void checkAArch64() {
        assumeTrue("skipping AArch64 specific test", getTarget().arch instanceof AArch64);
    }

    private static final class UseKillClobberOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<UseKillClobberOp> TYPE = LIRInstructionClass.create(UseKillClobberOp.class);

        @Def({REG}) private AllocatableValue result;
        @UseKill({REG}) private AllocatableValue input;

        private UseKillClobberOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            masm.add(64, asRegister(result), asRegister(input), 1);
            masm.add(64, asRegister(input), asRegister(input), 7);
        }
    }

    private static final class UseKillSpec extends LIRTestSpecification {
        @Override
        public void generate(LIRGeneratorTool gen, Value inputValue) {
            Assert.assertTrue("expected allocatable value but found " + inputValue, inputValue instanceof AllocatableValue);
            AllocatableValue input = (AllocatableValue) inputValue;
            Variable result = gen.newVariable(input.getValueKind());
            gen.append(new UseKillClobberOp(result, input));
            setResult(result);
        }
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static long useKill(LIRTestSpecification spec, long value) {
        return value + 1;
    }

    public static long useKillSnippet(long a) {
        long liveValue = GraalDirectives.opaque(a);
        long useKillValue = useKill(USE_KILL_SPEC, liveValue);
        return useKillValue + liveValue;
    }

    @Test
    public void testUseKillSnippetExecution() {
        InstalledCode code = getCode(getResolvedJavaMethod("useKillSnippet"));
        assertCompiledResult(code, 0L);
        assertCompiledResult(code, 7L);
        assertCompiledResult(code, -19L);
        assertCompiledResult(code, 0x123456789ABCDEFL);
    }

    @Test
    public void testUseKillMoveInjection() {
        resetInspection();
        compile(getResolvedJavaMethod("useKillSnippet"), null);
        Assert.assertTrue("expected to see the UseKill test op", sawUseKillOp);
        Assert.assertTrue("expected a UseKillMoveInjection move before the UseKill op:\n" + inspectedSequence, sawInjectedMove);
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        suites.getPreAllocationOptimizationStage().appendPhase(new CheckPhase());
        return suites;
    }

    private void resetInspection() {
        sawUseKillOp = false;
        sawInjectedMove = false;
        inspectedSequence = null;
    }

    private void assertCompiledResult(InstalledCode code, long input) {
        long expected = useKillSnippet(input);
        long actual = ((Long) executeVarargsSafe(code, input)).longValue();
        Assert.assertEquals(expected, actual);
    }

    private static String describeInstructions(LIRGenerationResult lirGenRes, ArrayList<LIRInstruction> instructions, int opIndex) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, opIndex - 1);
        int end = Math.min(instructions.size(), opIndex + 2);
        for (int i = start; i < end; i++) {
            if (sb.length() != 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(instructions.get(i).toString(lirGenRes));
        }
        return sb.toString();
    }

    private final class CheckPhase extends LIRPhase<PreAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
            int useKillOpCount = 0;
            for (var block : lirGenRes.getLIR().getControlFlowGraph().getBlocks()) {
                ArrayList<LIRInstruction> instructions = lirGenRes.getLIR().getLIRforBlock(block);
                for (int i = 0; i < instructions.size(); i++) {
                    LIRInstruction instruction = instructions.get(i);
                    if (instruction instanceof UseKillClobberOp op) {
                        useKillOpCount++;
                        inspectedSequence = describeInstructions(lirGenRes, instructions, i);

                        Assert.assertTrue("expected a move before the UseKill op:\n" + inspectedSequence, i > 0);
                        LIRInstruction previous = instructions.get(i - 1);
                        Assert.assertTrue("expected a value move before the UseKill op:\n" + inspectedSequence, ValueMoveOp.isValueMoveOp(previous));
                        ValueMoveOp move = ValueMoveOp.asValueMoveOp(previous);
                        Assert.assertEquals("UseKill op should consume the injected move result", move.getResult(), op.input);
                        Assert.assertNotEquals("UseKill rewrite should introduce a distinct split value", move.getInput(), move.getResult());
                        sawInjectedMove = true;
                    }
                }
            }
            Assert.assertEquals("expected exactly one UseKill test op", 1, useKillOpCount);
            sawUseKillOp = useKillOpCount == 1;
        }
    }
}
