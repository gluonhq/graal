/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import java.util.Optional;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.BasePhase.NotApplicable;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import org.junit.Assert;
import org.junit.Test;

public class GraphStateTest extends GraalCompilerTest {

    public static int andSnippet(int x, int y) {
        return x & y;
    }

    public static int orSnippet(int x, int y) {
        return x | y;
    }

    @Test
    public void testGraphState() {
        StructuredGraph andGraph = parseForCompile(getResolvedJavaMethod("andSnippet"));
        StructuredGraph orGraph = parseForCompile(getResolvedJavaMethod("orSnippet"));

        GraphState andGraphState = andGraph.getGraphState();
        GraphState orGraphState = orGraph.getGraphState();

        Assert.assertTrue(andGraphState.equals(andGraphState));
        Object obj = Boolean.TRUE; // work around static check that this makes no sense
        Assert.assertFalse(andGraphState.equals(obj));
        Assert.assertTrue(andGraphState.equals(orGraphState));

        Assert.assertEquals(andGraphState.hashCode(), orGraphState.hashCode());

        Assert.assertTrue(andGraphState.toString().equals(orGraphState.toString()));
        Assert.assertTrue(andGraphState.toString(">>>").equals(orGraphState.toString(">>>")));

        String diff = andGraphState.updateFromPreviousToString(orGraphState);
        Assert.assertEquals(0, diff.length());

        Assert.assertFalse(andGraphState.isAfterStages(GraphState.MandatoryStages.ECONOMY.getHighTier()));
        Assert.assertFalse(andGraphState.isAfterStages(GraphState.MandatoryStages.ECONOMY.getMidTier()));
        Assert.assertFalse(andGraphState.isAfterStages(GraphState.MandatoryStages.ECONOMY.getLowTier()));
        Assert.assertFalse(andGraphState.isAfterStages(GraphState.MandatoryStages.getFromName("economy").getHighTier()));
        Assert.assertFalse(andGraphState.isAfterStages(GraphState.MandatoryStages.getFromName("community").getHighTier()));
        Assert.assertFalse(andGraphState.isAfterStages(GraphState.MandatoryStages.getFromName("enterprise").getHighTier()));
        Assert.assertFalse(andGraphState.isAfterStages(GraphState.MandatoryStages.getFromName("").getHighTier()));

        Assert.assertEquals(2, andGraphState.countMissingStages(GraphState.MandatoryStages.ECONOMY.getHighTier()));
        Assert.assertFalse(andGraphState.hasAllMandatoryStages(GraphState.MandatoryStages.ECONOMY));

        Assert.assertFalse(andGraphState.requiresFutureStages());
        Assert.assertTrue(andGraphState.getFutureRequiredStages().isEmpty());
    }

    @Test
    public void testLoweringPhaseContracts() {
        assertApplicable(new HighTierLoweringPhase(CanonicalizerPhase.create()), graphState());

        MidTierLoweringPhase midTierLoweringPhase = new MidTierLoweringPhase(CanonicalizerPhase.create());
        assertNotApplicableDueTo(midTierLoweringPhase, graphState(), StageFlag.GUARD_LOWERING);
        assertApplicable(midTierLoweringPhase, graphState(StageFlag.GUARD_LOWERING));
        assertNotApplicableDueTo(midTierLoweringPhase, graphState(StageFlag.GUARD_LOWERING, StageFlag.FSA), StageFlag.FSA);

        LowTierLoweringPhase lowTierLoweringPhase = new LowTierLoweringPhase(CanonicalizerPhase.create());
        assertNotApplicableDueTo(lowTierLoweringPhase, graphState(), StageFlag.MID_TIER_LOWERING);
        assertNotApplicableDueTo(lowTierLoweringPhase, graphState(StageFlag.FSA), StageFlag.MID_TIER_LOWERING);
        assertNotApplicableDueTo(lowTierLoweringPhase, graphState(StageFlag.MID_TIER_LOWERING), StageFlag.FSA);
        assertApplicable(lowTierLoweringPhase, graphState(StageFlag.MID_TIER_LOWERING, StageFlag.FSA));
    }

    private static GraphState graphState(StageFlag... appliedStages) {
        GraphState graphState = GraphState.defaultGraphState();
        for (StageFlag stage : appliedStages) {
            graphState.setAfterStage(stage);
        }
        return graphState;
    }

    private static void assertApplicable(BasePhase<?> phase, GraphState graphState) {
        Optional<NotApplicable> notApplicable = phase.notApplicableTo(graphState);
        Assert.assertFalse(notApplicable.toString(), notApplicable.isPresent());
    }

    private static void assertNotApplicableDueTo(BasePhase<?> phase, GraphState graphState, StageFlag stage) {
        Optional<NotApplicable> notApplicable = phase.notApplicableTo(graphState);
        Assert.assertTrue("Expected " + phase.getName() + " to be rejected", notApplicable.isPresent());
        Assert.assertTrue(notApplicable.get().reason, notApplicable.get().reason.contains(stage.name()));
    }
}
