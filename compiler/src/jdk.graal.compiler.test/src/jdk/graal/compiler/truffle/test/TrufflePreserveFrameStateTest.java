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
package jdk.graal.compiler.truffle.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.truffle.nodes.TrufflePreserveFrameStateNode;
import jdk.graal.compiler.truffle.phases.TrufflePreserveFrameStateCleanupPhase;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TrufflePreserveFrameStateTest extends PartialEvaluationTest {

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(plugins.getInvocationPlugins(), TrufflePreserveFrameStateTest.class);
        r.register(new InvocationPlugin("preserveFrameStateMarker") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new TrufflePreserveFrameStateNode());
                return true;
            }
        });
        return plugins;
    }

    @Test
    public void testDominatedMarkersAreRemoved() {
        StructuredGraph graph = parseGraph("snippetIfElse", boolean.class);

        insertEntryMarker(graph);
        insertMarkersBeforeReturns(graph);
        Assert.assertEquals(3L, countMarkers(graph));

        applyCleanup(graph);

        Assert.assertEquals(1L, countMarkers(graph));
    }

    @Test
    public void testIndependentBranchMarkersAreKept() {
        StructuredGraph graph = parseGraph("snippetIfElse", boolean.class);

        insertMarkersBeforeReturns(graph);
        Assert.assertEquals(2L, countMarkers(graph));

        applyCleanup(graph);

        Assert.assertEquals(2L, countMarkers(graph));
    }

    @Test
    public void testEquivalentStateMarkersInBlockAreCollapsed() {
        StructuredGraph graph = parseGraph("snippetStraightLine", int.class);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertNotNull(returnNode);

        insertMarkerBefore(graph, returnNode);
        insertMarkerBefore(graph, returnNode);
        Assert.assertEquals(2L, countMarkers(graph));

        applyCleanup(graph);

        Assert.assertEquals(1L, countMarkers(graph));
    }

    @Test
    public void testDistinctStateMarkersInBlockAreKept() {
        StructuredGraph graph = parseGraph("snippetStraightLine", int.class);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertNotNull(returnNode);

        FrameState originalState = findMarkerState(graph);
        FrameState distinctState = duplicateWithDifferentBci(graph, originalState);

        insertMarkerBefore(graph, returnNode, originalState);
        insertMarkerBefore(graph, returnNode, distinctState);
        Assert.assertEquals(2L, countMarkers(graph));

        applyCleanup(graph);

        Assert.assertEquals(2L, countMarkers(graph));
    }

    @Test
    public void testMarkersAreRemovedAfterFSA() {
        StructuredGraph graph = parseGraph("snippetStraightLine", int.class);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertNotNull(returnNode);

        insertMarkerBefore(graph, returnNode);
        Assert.assertEquals(1L, countMarkers(graph));

        graph.getGraphState().setAfterFSA();
        CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

        Assert.assertEquals(0L, countMarkers(graph));
    }

    @Test
    public void testRedundantMarkerAfterEquivalentStateSplitIsCanonicalized() {
        StructuredGraph graph = parseGraph("snippetStraightLine", int.class);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertNotNull(returnNode);

        FrameState state = findMarkerState(graph);
        StateSplitProxyNode stateSplit = graph.add(new StateSplitProxyNode(state));
        graph.addBeforeFixed(returnNode, stateSplit);
        TrufflePreserveFrameStateNode marker = insertMarkerBefore(graph, returnNode, state);
        Assert.assertSame(stateSplit, marker.predecessor());
        Assert.assertEquals(1L, countMarkers(graph));

        CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

        Assert.assertTrue(stateSplit.isAlive());
        Assert.assertFalse(marker.isAlive());
        Assert.assertEquals(0L, countMarkers(graph));
        Assert.assertSame(stateSplit, returnNode.predecessor());
    }

    @Test
    public void testConvertDeoptimizeToGuardPreservesInlinedFrameState() {
        StructuredGraph graph = parseGraph("callerWithPreservedTransfer");
        Assert.assertEquals(1L, countMarkers(graph));
        TrufflePreserveFrameStateNode marker = graph.getNodes(TrufflePreserveFrameStateNode.TYPE).first();
        Assert.assertNotNull(marker);
        DeoptimizeNode deopt = graph.getNodes(DeoptimizeNode.TYPE).first();
        Assert.assertNotNull(deopt);
        Assert.assertSame(marker, deopt.predecessor());

        ResolvedJavaMethod callee = getResolvedJavaMethod(TrufflePreserveFrameStateTest.class, "calleeWithPreservedTransfer");
        Assert.assertTrue("expected preserved state to include the inlined callee frame", containsFrameForMethod(marker.stateAfter(), callee));

        new ConvertDeoptimizeToGuardPhase(CanonicalizerPhase.create()).apply(graph, getDefaultHighTierContext());

        Assert.assertTrue("expected preserve-frame-state marker to survive deopt-to-guard conversion", marker.isAlive());
        deopt = graph.getNodes(DeoptimizeNode.TYPE).first();
        Assert.assertNotNull("expected deopt to remain after the preserve-frame-state marker", deopt);
        Assert.assertSame("deopt should remain after the preserve-frame-state marker", marker, deopt.predecessor());
        Assert.assertTrue("expected preserved state to include the inlined callee frame", containsFrameForMethod(marker.stateAfter(), callee));
    }

    @Test
    public void testTrufflePreserveFrameStateHere() {
        StructuredGraph graph = partialEval(new PreserveFrameStateHereRootNode());
        Assert.assertEquals(1L, countMarkers(graph));
        TrufflePreserveFrameStateNode marker = graph.getNodes(TrufflePreserveFrameStateNode.TYPE).first();
        Assert.assertNotNull(marker);
        DeoptimizeNode deopt = findTransferToInterpreterInvalidateDeopt(graph);
        Assert.assertNotNull(deopt);
        Assert.assertSame(marker, deopt.predecessor());
    }

    private StructuredGraph parseGraph(String methodName, Class<?>... parameterTypes) {
        return parseForCompile(getResolvedJavaMethod(TrufflePreserveFrameStateTest.class, methodName, parameterTypes));
    }

    private static void applyCleanup(StructuredGraph graph) {
        new TrufflePreserveFrameStateCleanupPhase().apply(graph, null);
    }

    private static long countMarkers(StructuredGraph graph) {
        long count = 0;
        for (Node node : graph.getNodes()) {
            if (node instanceof TrufflePreserveFrameStateNode) {
                count++;
            }
        }
        return count;
    }

    private static void insertEntryMarker(StructuredGraph graph) {
        FixedNode firstFixed = graph.start().next();
        Assert.assertNotNull(firstFixed);
        insertMarkerBefore(graph, firstFixed);
    }

    private static void insertMarkersBeforeReturns(StructuredGraph graph) {
        var returns = graph.getNodes(ReturnNode.TYPE).snapshot();
        Assert.assertFalse(returns.isEmpty());
        for (ReturnNode returnNode : returns) {
            insertMarkerBefore(graph, returnNode);
        }
    }

    private static void insertMarkerBefore(StructuredGraph graph, FixedNode node) {
        insertMarkerBefore(graph, node, findMarkerState(graph));
    }

    private static TrufflePreserveFrameStateNode insertMarkerBefore(StructuredGraph graph, FixedNode node, FrameState state) {
        TrufflePreserveFrameStateNode marker = graph.add(new TrufflePreserveFrameStateNode());
        Assert.assertNotNull(state);
        marker.setStateAfter(state);
        graph.addBeforeFixed(node, marker);
        return marker;
    }

    private static FrameState findMarkerState(StructuredGraph graph) {
        for (FrameState state : graph.getNodes(FrameState.TYPE)) {
            if (state.getCode() != null && state.bci >= 0) {
                return state;
            }
        }
        Assert.fail("expected a parsed frame state");
        return null;
    }

    private static FrameState duplicateWithDifferentBci(StructuredGraph graph, FrameState state) {
        Assert.assertNotNull(state.getCode());
        Assert.assertTrue(state.getCode().getCodeSize() > 1);
        int newBci = state.bci == 0 ? 1 : 0;
        FrameState duplicate = state.duplicateModified(graph, newBci, state.getStackState(), JavaKind.Void, null, null, null);
        Assert.assertNotEquals(FrameState.toSourcePosition(state), FrameState.toSourcePosition(duplicate));
        return duplicate;
    }

    private static boolean containsFrameForMethod(FrameState state, ResolvedJavaMethod method) {
        for (FrameState current = state; current != null; current = current.outerFrameState()) {
            if (method.equals(current.getMethod())) {
                return true;
            }
        }
        return false;
    }

    private static DeoptimizeNode findTransferToInterpreterInvalidateDeopt(StructuredGraph graph) {
        DeoptimizeNode result = null;
        for (DeoptimizeNode deopt : graph.getNodes(DeoptimizeNode.TYPE)) {
            if (deopt.getAction() == DeoptimizationAction.InvalidateReprofile && deopt.getReason() == DeoptimizationReason.TransferToInterpreter) {
                Assert.assertNull("expected a single transfer-to-interpreter-and-invalidate deopt", result);
                result = deopt;
            }
        }
        return result;
    }

    public static int snippetIfElse(boolean cond) {
        if (cond) {
            return 1;
        }
        return 2;
    }

    public static int snippetStraightLine(int value) {
        return value + 1;
    }

    public static void callerWithPreservedTransfer() {
        calleeWithPreservedTransfer();
    }

    @BytecodeParserForceInline
    public static void calleeWithPreservedTransfer() {
        preserveFrameStateMarker();
        GraalDirectives.deoptimizeAndInvalidate();
    }

    public static void preserveFrameStateMarker() {
    }

    static final class PreserveFrameStateHereRootNode extends RootNode {

        PreserveFrameStateHereRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CompilerDirectives.preserveFrameStateHere();
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return 42;
        }
    }
}
