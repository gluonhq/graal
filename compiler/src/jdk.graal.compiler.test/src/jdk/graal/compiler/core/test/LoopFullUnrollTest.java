/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.common.util.LoopUtility;

import org.junit.Assert;
import org.junit.Test;

public class LoopFullUnrollTest extends GraalCompilerTest {

    public static int testMinToMax(int input) {
        int ret = 2;
        int current = input;
        for (long i = Long.MIN_VALUE; i < Long.MAX_VALUE; i++) {
            ret *= 2 + current;
            current /= 50;
        }
        return ret;
    }

    @Test
    public void runMinToMax() throws Throwable {
        test("testMinToMax", 1);
    }

    public static int testMinTo0(int input) {
        int ret = 2;
        int current = input;
        for (long i = Long.MIN_VALUE; i <= 0; i++) {
            ret *= 2 + current;
            current /= 50;
        }
        return ret;
    }

    @Test
    public void runMinTo0() throws Throwable {
        test("testMinTo0", 1);
    }

    public static int testNegativeTripCount(int input) {
        int ret = 2;
        int current = input;
        for (long i = 0; i <= -20; i++) {
            ret *= 2 + current;
            current /= 50;
        }
        return ret;
    }

    @Test
    public void runNegativeTripCount() throws Throwable {
        test("testNegativeTripCount", 0);
    }

    private void test(String snippet, int loopCount) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope(getClass().getSimpleName(), new DebugDumpScope(snippet))) {
            final StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO, debug);

            new DisableOverflownCountedLoopsPhase().apply(graph);

            CoreProviders context = getProviders();
            new LoopFullUnrollPhase(createCanonicalizerPhase(), new DefaultLoopPolicies()).apply(graph, context);

            assertTrue(graph.getNodes().filter(LoopBeginNode.class).count() == loopCount);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    public static int snippetFlows() {
        int init = Integer.MIN_VALUE;
        int step = -1;
        int limit = 1;
        int phi = init;
        while (Integer.MIN_VALUE - phi < limit) {
            GraalDirectives.sideEffect();
            phi = phi + step;
        }
        return phi;
    }

    @Test
    public void testFlows() {
        test("snippetFlows");
    }

    public static int snippetFlows2() {
        int init = Integer.MAX_VALUE;
        int step = -8;
        int limit = 8184;
        int phi = init;
        while (Integer.MIN_VALUE - phi < limit) {
            GraalDirectives.sideEffect();
            phi = phi + step;
        }
        return phi;
    }

    @Test
    public void testFlows2() {
        test("snippetFlows2");
    }

    /**
     * Returns the only counted loop in this reproducer whose body still contains nested loops.
     *
     * The reproducer has exactly one such counted loop, so the result does not depend on the
     * iteration order of {@link LoopsData#countedLoops()}.
     */
    private static Loop findSingleCountedLoopWithNestedLoops(LoopsData loopsData) {
        Loop match = null;
        for (Loop loop : loopsData.countedLoops()) {
            if (!loop.getCFGLoop().getChildren().isEmpty()) {
                Assert.assertNull("expected only one counted loop with nested loops in the reproducer", match);
                match = loop;
            }
        }
        return match;
    }

    /**
     * Returns the only counted loop in this reproducer.
     */
    private static Loop findSingleCountedLoop(LoopsData loopsData) {
        Assert.assertEquals("expected exactly one counted loop in the reproducer", 1, loopsData.countedLoops().size());
        return loopsData.countedLoops().get(0);
    }

    /**
     * Returns whether {@code loop} is nested below {@code rootLoop}.
     */
    private static boolean isCountedDescendantLoop(Loop loop, Loop rootLoop) {
        for (Loop parent = loop.parent(); parent != null; parent = parent.parent()) {
            if (parent == rootLoop) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the only counted descendant loop nested below {@code rootLoop} in this reproducer.
     */
    private static Loop findSingleCountedDescendantLoop(LoopsData loopsData, Loop rootLoop) {
        Loop match = null;
        for (Loop loop : loopsData.countedLoops()) {
            if (isCountedDescendantLoop(loop, rootLoop)) {
                Assert.assertNull("expected only one counted descendant loop below the reproducer's parent loop", match);
                match = loop;
            }
        }
        return match;
    }

    /**
     * Returns the stable clone-origin key recorded for {@code loopBegin}.
     */
    @SuppressWarnings("deprecation")
    private static long loopBeginCloneOriginKey(LoopBeginNode loopBegin) {
        return ((long) loopBegin.graph().getCompressions() << 32L) | Integer.toUnsignedLong(loopBegin.getId());
    }

    /**
     * Returns the policy's current full-unroll size budget for {@code loop}.
     */
    private static int fullUnrollBudget(Loop loop) {
        OptionValues options = loop.entryPoint().getOptions();
        int globalMax = GraalOptions.MaximumDesiredSize.getValue(options) - loop.loopBegin().graph().getNodeCount();
        Assert.assertTrue("expected the reproducer graph to leave some full-unroll budget", globalMax > 0);
        int maxNodes = loop.counted().isExactTripCount() ? DefaultLoopPolicies.Options.ExactFullUnrollMaxNodes.getValue(options) : DefaultLoopPolicies.Options.FullUnrollMaxNodes.getValue(options);
        for (Node usage : loop.counted().getLimitCheckedIV().valueNode().usages()) {
            if (usage instanceof CompareNode compare && compare.getY().isConstant()) {
                maxNodes += DefaultLoopPolicies.Options.FullUnrollConstantCompareBoost.getValue(options);
            }
        }
        return Math.min(maxNodes, globalMax);
    }

    /**
     * Returns the full-unroll growth estimate before applying any propagated overall clone count.
     */
    private static long baseFullUnrollGrowthEstimate(Loop loop) {
        long maxTrips = loop.counted().constantMaxTripCount().asLong();
        int size = loop.inside().nodes().count();
        size -= 2; // remove the counted if and its non-exit begin
        size -= loop.loopBegin().loopEnds().count();
        Assert.assertTrue("expected a non-negative loop body size for the reproducer", size >= 0);
        return Math.multiplyExact(maxTrips - 1L, size);
    }

    /**
     * Verifies the counted-loop clones identified by {@code cloneOriginKey}.
     */
    private static void assertClonedCountedLoops(LoopsData loopsData, long cloneOriginKey, int expectedCloneCount, int expectedUnrollFactor, boolean expectedShouldFullUnroll) {
        DefaultLoopPolicies policies = new DefaultLoopPolicies();
        int clonedLoops = 0;
        for (Loop loop : loopsData.countedLoops()) {
            if (loop.loopBegin().getClonedFromNodeId() == cloneOriginKey) {
                Assert.assertEquals("unexpected propagated counted descendant clone factor on a cloned counted descendant loop",
                                expectedUnrollFactor,
                                loop.loopBegin().getCountedDescendantCloneFactor());
                Assert.assertEquals("unexpected full-unroll policy result for a cloned counted descendant loop",
                                expectedShouldFullUnroll,
                                policies.shouldFullUnroll(loop));
                clonedLoops++;
            }
        }
        Assert.assertEquals("unexpected number of cloned counted descendant loops",
                        expectedCloneCount,
                        clonedLoops);
    }

    /**
     * Verifies the counted descendant loops nested below {@code rootLoop}.
     */
    private static void assertCountedDescendantLoops(LoopsData loopsData, Loop rootLoop, int expectedCount, int expectedUnrollFactor) {
        int descendantLoops = 0;
        for (Loop loop : loopsData.countedLoops()) {
            if (isCountedDescendantLoop(loop, rootLoop)) {
                Assert.assertEquals("unexpected counted descendant clone factor on a counted descendant loop",
                                expectedUnrollFactor,
                                loop.loopBegin().getCountedDescendantCloneFactor());
                descendantLoops++;
            }
        }
        Assert.assertEquals("unexpected number of counted descendant loops",
                        expectedCount,
                        descendantLoops);
    }

    /**
     * Reproducer adapted from the original GR-59101 report. Fully unrolling the middle counted loop
     * duplicates the innermost counted loop.
     */
    public static int repeatedFullUnrollSnippet(boolean param0) {
        long var15;
        int var1 = 0;
        double[] var4 = new double[12];
        short var9 = 2;
        loop1: while (param0) {
            // The test manually fully unrolls this counted parent loop first.
            for (int i3 = 0; i3 < var4.length; i3 = i3 + 1) {
                // Each parent-loop iteration carries this counted child loop, so the parent full
                // unroll duplicates the i4 loop body once per i3 iteration.
                for (int i4 = 635600372; i4 <= 635600772; i4 = i4 + 4) {
                    var15 = i4;
                    var9 = (short) (var9 - Long.numberOfLeadingZeros(var15));
                }
            }
            if (GraalDirectives.injectBranchProbability(0.01, var1 > 100)) {
                break loop1;
            }
            var1 = GraalDirectives.opaque(var1 + 1);
        }
        return var1 + var9;
    }

    public static int policyUnrollFactorSnippet() {
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            GraalDirectives.sideEffect();
            sum += i;
        }
        return sum;
    }

    @Test
    public void testFullUnrollPolicyAccountsForCountedDescendantCloneFactor() {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope(getClass().getSimpleName(), new DebugDumpScope("policyUnrollFactorSnippet"))) {
            CoreProviders context = getProviders();

            StructuredGraph graph = parseEager("policyUnrollFactorSnippet", AllowAssumptions.NO, debug);
            new DisableOverflownCountedLoopsPhase().apply(graph);
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();

            Loop loop = findSingleCountedLoop(loopsData);
            DefaultLoopPolicies policies = new DefaultLoopPolicies();
            Assert.assertTrue("expected the small counted loop to be fully unrollable before any simulated prior cloning",
                            policies.shouldFullUnroll(loop));

            long baseEstimatedGrowth = baseFullUnrollGrowthEstimate(loop);
            int maxNodes = fullUnrollBudget(loop);
            Assert.assertTrue("expected the reproducer's base full-unroll growth estimate to fit inside the current size budget",
                            baseEstimatedGrowth <= maxNodes);

            // Pick the first simulated ancestor-created clone count that pushes the policy's
            // estimated growth past maxNodes, so the rejection comes from the normal size-budget
            // check instead of from arithmetic overflow.
            int firstTooLargeCloneFactor = Math.toIntExact((maxNodes / baseEstimatedGrowth) + 1L);
            long scaledEstimatedGrowth = Math.multiplyExact(baseEstimatedGrowth, firstTooLargeCloneFactor);
            Assert.assertTrue("expected the chosen counted descendant clone factor to exceed the size budget without relying on arithmetic overflow",
                            scaledEstimatedGrowth > maxNodes);

            loop.loopBegin().setCountedDescendantCloneFactor(firstTooLargeCloneFactor);

            Assert.assertFalse(String.format(
                            "expected the counted descendant clone factor to make the same counted loop too expensive to fully unroll (baseEstimatedGrowth=%d, maxNodes=%d, cloneFactor=%d, scaledEstimatedGrowth=%d)",
                            baseEstimatedGrowth,
                            maxNodes,
                            firstTooLargeCloneFactor,
                            scaledEstimatedGrowth),
                            policies.shouldFullUnroll(loop));
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    @Test
    public void testPartialUnrollPropagatesCountedDescendantCloneFactor() {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope(getClass().getSimpleName(), new DebugDumpScope("repeatedFullUnrollSnippet"))) {
            CoreProviders context = getProviders();
            final int simulatedCloneFactor = 4;
            final int expectedPreMainPostCloneFactor = simulatedCloneFactor * 2;
            final int expectedPartialUnrollCloneFactor = expectedPreMainPostCloneFactor * 2;

            StructuredGraph graph = parseEager("repeatedFullUnrollSnippet", AllowAssumptions.NO, debug);
            new DisableOverflownCountedLoopsPhase().apply(graph);
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();

            Loop loopWithNestedLoops = findSingleCountedLoopWithNestedLoops(loopsData);
            Assert.assertNotNull("expected a counted loop with nested loops in the reproducer", loopWithNestedLoops);
            Loop countedDescendantLoop = findSingleCountedDescendantLoop(loopsData, loopWithNestedLoops);
            Assert.assertNotNull("expected exactly one counted descendant loop below the parent counted loop before partial-unroll setup", countedDescendantLoop);

            countedDescendantLoop.loopBegin().setCountedDescendantCloneFactor(simulatedCloneFactor);

            LoopUtility.preserveCounterStampsForDivAfterUnroll(loopWithNestedLoops);
            LoopTransformations.PreMainPostResult transformedLoops = LoopTransformations.insertPrePostLoops(loopWithNestedLoops);

            LoopsData mainLoopsData = context.getLoopsDataProvider().getLoopsData(graph);
            mainLoopsData.detectCountedLoops();

            assertCountedDescendantLoops(mainLoopsData, mainLoopsData.loop(transformedLoops.getPreLoop()), 1, expectedPreMainPostCloneFactor);
            assertCountedDescendantLoops(mainLoopsData, mainLoopsData.loop(transformedLoops.getMainLoop()), 1, expectedPreMainPostCloneFactor);
            assertCountedDescendantLoops(mainLoopsData, mainLoopsData.loop(transformedLoops.getPostLoop()), 1, expectedPreMainPostCloneFactor);

            Loop mainLoop = mainLoopsData.loop(transformedLoops.getMainLoop());
            Assert.assertNotNull("expected the pre-main-post transformation to produce a counted main loop", mainLoop);

            LoopTransformations.partialUnroll(mainLoop, null);

            LoopsData afterPartialUnrollLoopsData = context.getLoopsDataProvider().getLoopsData(graph);
            afterPartialUnrollLoopsData.detectCountedLoops();

            Loop updatedMainLoop = afterPartialUnrollLoopsData.loop(transformedLoops.getMainLoop());
            Assert.assertNotNull("expected the counted main loop to remain after partial unroll", updatedMainLoop);
            assertCountedDescendantLoops(afterPartialUnrollLoopsData, updatedMainLoop, 2, expectedPartialUnrollCloneFactor);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    @Test
    public void testFullUnrollPropagatesCountedDescendantCloneFactorToClonedLoops() {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope(getClass().getSimpleName(), new DebugDumpScope("repeatedFullUnrollSnippet"))) {
            CoreProviders context = getProviders();
            final int expectedClonedCountedDescendantLoops = 12;
            final int simulatedCloneFactor = 4;
            final int expectedPropagatedCloneFactor = simulatedCloneFactor * expectedClonedCountedDescendantLoops;

            StructuredGraph graph = parseEager("repeatedFullUnrollSnippet", AllowAssumptions.NO, debug);
            new DisableOverflownCountedLoopsPhase().apply(graph);
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();

            Loop loopWithNestedLoops = findSingleCountedLoopWithNestedLoops(loopsData);
            Assert.assertNotNull("expected a counted loop with nested loops in the reproducer", loopWithNestedLoops);
            Loop countedDescendantLoop = findSingleCountedDescendantLoop(loopsData, loopWithNestedLoops);
            Assert.assertNotNull("expected exactly one counted descendant loop below the parent counted loop in the reproducer", countedDescendantLoop);

            countedDescendantLoop.loopBegin().setCountedDescendantCloneFactor(simulatedCloneFactor);
            long countedDescendantCloneOriginKey = loopBeginCloneOriginKey(countedDescendantLoop.loopBegin());

            LoopTransformations.fullUnroll(loopWithNestedLoops, context, createCanonicalizerPhase());

            LoopsData afterFullUnrollLoopsData = context.getLoopsDataProvider().getLoopsData(graph);
            afterFullUnrollLoopsData.detectCountedLoops();

            assertClonedCountedLoops(afterFullUnrollLoopsData,
                            countedDescendantCloneOriginKey,
                            expectedClonedCountedDescendantLoops,
                            expectedPropagatedCloneFactor,
                            false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    @Test
    public void testFullUnrollPhaseRejectsClonedLoopsViaCountedDescendantCloneFactor() {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope(getClass().getSimpleName(), new DebugDumpScope("repeatedFullUnrollSnippet"))) {
            CoreProviders context = getProviders();
            final int expectedClonedCountedDescendantLoops = 12;
            final int expectedPropagatedCloneFactor = expectedClonedCountedDescendantLoops;

            StructuredGraph graph = parseEager("repeatedFullUnrollSnippet", AllowAssumptions.NO, debug);
            new DisableOverflownCountedLoopsPhase().apply(graph);
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();

            Loop loopWithNestedLoops = findSingleCountedLoopWithNestedLoops(loopsData);
            Assert.assertNotNull("expected a counted loop with nested loops before running the full-unroll phase", loopWithNestedLoops);
            Loop countedDescendantLoop = findSingleCountedDescendantLoop(loopsData, loopWithNestedLoops);
            Assert.assertNotNull("expected exactly one counted descendant loop below the parent counted loop before running the phase", countedDescendantLoop);

            long countedDescendantCloneOriginKey = loopBeginCloneOriginKey(countedDescendantLoop.loopBegin());

            new LoopFullUnrollPhase(createCanonicalizerPhase(), new DefaultLoopPolicies()).apply(graph, context);

            LoopsData afterPhaseLoopsData = context.getLoopsDataProvider().getLoopsData(graph);
            afterPhaseLoopsData.detectCountedLoops();

            assertClonedCountedLoops(afterPhaseLoopsData,
                            countedDescendantCloneOriginKey,
                            expectedClonedCountedDescendantLoops,
                            expectedPropagatedCloneFactor,
                            false);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
