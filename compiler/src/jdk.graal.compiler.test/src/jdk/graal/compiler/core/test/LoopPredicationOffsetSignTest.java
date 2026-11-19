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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Regression test for {@code DerivedOffsetInductionVariable#offsetNode}: for an IV of the form
 * {@code i - off}, {@code offsetNode} must report {@code -off} to match the
 * {@code iv = scale * ref + offset} contract on {@code InductionVariable}.
 */
public class LoopPredicationOffsetSignTest extends GraalCompilerTest {

    public static long readSnippet(long[] arr, int start, int n, int off) {
        if (arr.length == 0) {
            return 0;
        }
        long s = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            s += arr[i - off];
        }
        return s;
    }

    public static long graphSnippet(long[] arr, int start, int n, int off) {
        if (arr.length == 0) {
            return 0;
        }
        long s = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            s += arr[i - off];
        }
        return s;
    }

    public static void writeSnippet(long[] arr, int start, int n, int off, long v) {
        if (arr.length == 0) {
            return;
        }
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            arr[i - off] = v;
        }
    }

    public static long reverseSnippet(long[] arr, int start, int n, int off) {
        if (arr.length == 0) {
            return 0;
        }
        long s = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            s += arr[off - i];
        }
        return s;
    }

    private OptionValues opts() {
        return new OptionValues(getInitialOptions(),
                        GraalOptions.SpeculativeGuardMovement, false,
                        GraalOptions.LoopPredication, true,
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.FullUnroll, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopUnswitch, false);
    }

    @Test
    public void testOOBRead() throws InvalidInstalledCodeException {
        long[] arr = new long[128];
        long[] victim = new long[128];
        for (int i = 0; i < victim.length; i++) {
            victim[i] = 0xCAFEBABE00000000L | i;
        }
        InstalledCode code = getCode(getResolvedJavaMethod("readSnippet"), opts());
        Assert.assertTrue(code.isValid());
        try {
            Object r = code.executeVarargs(arr, 100, 104, -30);
            Assert.fail("compiled code read out-of-bounds heap memory at arr[130..133] without a bounds check; returned 0x" + Long.toHexString((Long) r) +
                            " (victim[0]=0x" + Long.toHexString(victim[0]) + ")");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }
    }

    @Test
    public void testOOBWrite() throws InvalidInstalledCodeException {
        long[] arr = new long[128];
        InstalledCode code = getCode(getResolvedJavaMethod("writeSnippet"), opts());
        Assert.assertTrue(code.isValid());
        try {
            code.executeVarargs(arr, 100, 104, -30, 0xDEADBEEFCAFEBABEL);
            Assert.fail("compiled code wrote out-of-bounds heap memory at arr[130..133] without a bounds check");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }
    }

    @Test
    public void testNegativeIndexCrash() throws InvalidInstalledCodeException {
        long[] arr = new long[128];
        InstalledCode code = getCode(getResolvedJavaMethod("readSnippet"), opts());
        Assert.assertTrue(code.isValid());
        try {
            Object r = code.executeVarargs(arr, 0, 4, 50);
            Assert.fail("compiled code performed an unchecked load at arr[-50]; returned " + r);
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }
    }

    @Test
    public void testReverseSubtractionShape() throws InvalidInstalledCodeException {
        long[] arr = new long[128];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i;
        }
        InstalledCode code = getCode(getResolvedJavaMethod("reverseSnippet"), opts());
        Assert.assertTrue(code.isValid());
        Object r = code.executeVarargs(arr, 0, 4, 30);
        Assert.assertEquals(30L + 29L + 28L + 27L, r);
        Assert.assertTrue(code.isValid());
    }

    @Test
    public void testLoopPredicationHoistsBoundsChecks() {
        compile(getResolvedJavaMethod("graphSnippet"), null, opts());
        assertBoundsChecksHoisted(lastCompiledGraph);
    }

    private static void assertBoundsChecksHoisted(StructuredGraph graph) {
        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(graph);
        Assert.assertTrue("Loop expected in compiled graph", cfg.getLoops().size() > 0);

        List<GuardNode> boundsCheckGuards = graph.getNodes(GuardNode.TYPE).stream().filter(n -> n.getReason().equals(DeoptimizationReason.BoundsCheckException)).toList();
        for (GuardNode guard : boundsCheckGuards) {
            CFGLoop<HIRBlock> loop = cfg.getNodeToBlock().get(guard.getAnchor().asNode()).getLoop();
            Assert.assertNull("Bounds check guard should be hoisted out of the loop", loop);
        }

        List<DeoptimizeNode> boundsCheckDeopts = graph.getNodes(DeoptimizeNode.TYPE).stream().filter(n -> n.getReason().equals(DeoptimizationReason.BoundsCheckException)).toList();
        Assert.assertTrue("Expected loop-predicated bounds checks", boundsCheckGuards.size() + boundsCheckDeopts.size() > 0);

        for (DeoptimizeNode deopt : boundsCheckDeopts) {
            CFGLoop<HIRBlock> loop = cfg.getNodeToBlock().get(deopt).getFirstPredecessor().getLoop();
            Assert.assertNull("Bounds check should be hoisted out of the loop", loop);
        }
    }
}
