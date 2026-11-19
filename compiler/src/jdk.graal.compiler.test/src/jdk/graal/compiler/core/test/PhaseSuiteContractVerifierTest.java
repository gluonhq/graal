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
package jdk.graal.compiler.core.test;

import static org.junit.Assert.assertThrows;

import java.util.Optional;

import jdk.graal.compiler.core.phases.fuzzing.PhaseSuiteContractVerifier;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import org.junit.Assert;
import org.junit.Test;

public class PhaseSuiteContractVerifierTest extends GraalCompilerTest {

    @Test
    public void testOverriddenNotApplicableMustRespectSuperclassContract() {
        PhaseSuite<Object> suite = new PhaseSuite<>();
        suite.appendPhase(new BadChildPhase());

        /*
         * BadChildPhase inherits ParentPhase's state update but drops ParentPhase's
         * LOW_TIER_LOWERING constraint. The verifier detects that mismatch and throws instead of
         * returning.
         */
        GraalError error = assertThrows(GraalError.class,
                        () -> PhaseSuiteContractVerifier.verifiedNotApplicableTo(suite, GraphState.defaultGraphState()));
        Assert.assertTrue(error.getMessage(), error.getMessage().contains("superclass contract"));
    }

    @Test
    public void testOverriddenNotApplicableCanAddConstraints() {
        PhaseSuite<Object> suite = new PhaseSuite<>();
        suite.appendPhase(new GoodChildPhase());
        GraphState graphState = GraphState.defaultGraphState();
        graphState.setAfterStage(StageFlag.LOW_TIER_LOWERING);

        /*
         * LOW_TIER_LOWERING is done and ADDRESS_LOWERING is not, satisfying the parent constraint
         * and the child's added constraint. The Optional is empty, meaning all constraints are
         * satisfied and the suite is applicable.
         */
        Assert.assertTrue(PhaseSuiteContractVerifier.verifiedNotApplicableTo(suite, graphState).isEmpty());

        /*
         * ADDRESS_LOWERING is now done, so the child's added constraint is unsatisfied even though
         * the parent constraint still holds. The Optional is present, meaning it contains some
         * unsatisfied constraint and the suite is not applicable.
         */
        graphState.setAfterStage(StageFlag.ADDRESS_LOWERING);
        Assert.assertTrue(PhaseSuiteContractVerifier.verifiedNotApplicableTo(suite, graphState).isPresent());
    }

    private static class ParentPhase extends BasePhase<Object> {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return NotApplicable.unlessRunAfter(this, StageFlag.LOW_TIER_LOWERING, graphState);
        }

        @Override
        public void updateGraphState(GraphState graphState) {
            graphState.setAfterStage(StageFlag.ADDRESS_LOWERING);
        }

        @Override
        protected void run(StructuredGraph graph, Object context) {
        }
    }

    private static final class BadChildPhase extends ParentPhase {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }
    }

    private static final class GoodChildPhase extends ParentPhase {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return NotApplicable.ifAny(super.notApplicableTo(graphState), NotApplicable.ifApplied(this, StageFlag.ADDRESS_LOWERING, graphState));
        }
    }
}
