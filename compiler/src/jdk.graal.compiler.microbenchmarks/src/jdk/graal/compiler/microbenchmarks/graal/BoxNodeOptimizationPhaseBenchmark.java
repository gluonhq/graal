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
package jdk.graal.compiler.microbenchmarks.graal;

import static jdk.graal.compiler.nodes.loop.DefaultLoopPolicies.Options.ExactFullUnrollMaxNodes;
import static jdk.graal.compiler.nodes.loop.DefaultLoopPolicies.Options.FullUnrollMaxIterations;
import static jdk.graal.compiler.nodes.loop.DefaultLoopPolicies.Options.FullUnrollMaxNodes;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.microbenchmarks.graal.util.GraalState;
import jdk.graal.compiler.microbenchmarks.graal.util.GraalUtil;
import jdk.graal.compiler.microbenchmarks.graal.util.MethodSpec;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.BoxNodeOptimizationPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Whitebox benchmark for {@link BoxNodeOptimizationPhase} on large, fully-unrolled graphs with many
 * boxing operations of the same primitive values.
 */
@Fork(0)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class BoxNodeOptimizationPhaseBenchmark extends GraalBenchmark {

    static Object sink;

    private abstract static class BoxGraphState {
        final GraalState graal;
        final OptionValues options;
        final DebugContext debug;
        final HighTierContext highTierContext;
        final StructuredGraph originalGraph;

        StructuredGraph graph;
        BoxNodeOptimizationPhase phase;

        @SuppressWarnings({"try", "this-escape"})
        BoxGraphState() {
            graal = new GraalState();
            options = new OptionValues(graal.options,
                            FullUnrollMaxIterations, 1500,
                            FullUnrollMaxNodes, 100000,
                            ExactFullUnrollMaxNodes, 100000,
                            GraalOptions.MaximumDesiredSize, 150000);
            debug = new Builder(options).build();
            PhaseSuite<HighTierContext> graphBuilderSuite = createGraphBuilderSuite();
            highTierContext = new HighTierContext(graal.providers, graphBuilderSuite, OptimisticOptimizations.ALL);
            ResolvedJavaMethod method = graal.metaAccess.lookupJavaMethod(GraalUtil.getMethodFromMethodSpec(getClass()));

            StructuredGraph structuredGraph = null;
            try (DebugContext.Scope scope = debug.scope("BoxGraphState", method)) {
                structuredGraph = buildGraph(method);
                preprocessOriginal(structuredGraph);
                verifyPreconditions(structuredGraph);
            } catch (Throwable t) {
                debug.handle(t);
            }
            if (structuredGraph == null) {
                throw new IllegalStateException("failed to initialize benchmark graph for " + method.format("%H.%n(%p)"));
            }
            originalGraph = structuredGraph;
        }

        private StructuredGraph buildGraph(ResolvedJavaMethod method) {
            StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).method(method).speculationLog(method.getSpeculationLog()).build();
            highTierContext.getGraphBuilderSuite().apply(graph, highTierContext);
            return graph;
        }

        protected void preprocessOriginal(StructuredGraph structuredGraph) {
            new DisableOverflownCountedLoopsPhase().apply(structuredGraph);
            CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
            canonicalizer.apply(structuredGraph, graal.providers);
            long loopCountBeforeUnroll = structuredGraph.getNodes(LoopBeginNode.TYPE).count();
            if (loopCountBeforeUnroll != 1) {
                throw new IllegalStateException("expected one loop before unrolling, found " + loopCountBeforeUnroll);
            }
            new LoopFullUnrollPhase(CanonicalizerPhase.create(), new DefaultLoopPolicies()).apply(structuredGraph, graal.providers);
            canonicalizer.apply(structuredGraph, graal.providers);
            long loopCountAfterUnroll = structuredGraph.getNodes(LoopBeginNode.TYPE).count();
            if (loopCountAfterUnroll != 0) {
                throw new IllegalStateException("expected loop to be fully unrolled, found " + loopCountAfterUnroll + " loops");
            }
        }

        protected abstract long minBoxCount();

        protected void verifyPreconditions(StructuredGraph structuredGraph) {
            long boxCount = structuredGraph.getNodes(BoxNode.TYPE).count();
            if (boxCount < minBoxCount()) {
                throw new IllegalStateException("expected at least " + minBoxCount() + " boxes before BoxNodeOptimizationPhase, found " + boxCount);
            }
        }

        @Setup(Level.Invocation)
        public void beforeInvocation() {
            CompilationAlarm.resetProgressDetection();
            graph = (StructuredGraph) originalGraph.copy(debug);
            phase = new BoxNodeOptimizationPhase(CanonicalizerPhase.create());
        }

        private PhaseSuite<HighTierContext> createGraphBuilderSuite() {
            PhaseSuite<HighTierContext> graphBuilderSuite = graal.backend.getSuites().getDefaultGraphBuilderSuite().copy();
            GraphBuilderPhase originalBuilder = (GraphBuilderPhase) graphBuilderSuite.findPhase(GraphBuilderPhase.class).previous();
            GraphBuilderConfiguration config = originalBuilder.getGraphBuilderConfig().withEagerResolving(true).withUnresolvedIsError(true);
            graphBuilderSuite.findPhase(GraphBuilderPhase.class).set(originalBuilder.copyWithConfig(config));
            return graphBuilderSuite;
        }
    }

    @State(Scope.Thread)
    @MethodSpec(declaringClass = BoxNodeOptimizationPhaseBenchmark.class, name = "snippetManySameValueBoxes")
    public static class ManySameValueBoxes extends BoxGraphState {
        @Override
        protected long minBoxCount() {
            return 8000;
        }
    }

    @State(Scope.Thread)
    @MethodSpec(declaringClass = BoxNodeOptimizationPhaseBenchmark.class, name = "snippetManySameValueBoxClusters")
    public static class ManySameValueBoxClusters extends BoxGraphState {
        @Override
        protected long minBoxCount() {
            return 8000;
        }
    }

    static int snippetManySameValueBoxes(int a) {
        for (int i = 0; i < 1000; i++) {
            Integer box0 = a;
            sink = box0;
            Integer box1 = a;
            sink = box1;
            Integer box2 = a;
            sink = box2;
            Integer box3 = a;
            sink = box3;
            Integer box4 = a;
            sink = box4;
            Integer box5 = a;
            sink = box5;
            Integer box6 = a;
            sink = box6;
            Integer box7 = a;
            sink = box7;
        }
        return 0;
    }

    static int snippetManySameValueBoxClusters(int a) {
        int v0 = a;
        int v1 = a + 1;
        int v2 = a + 2;
        int v3 = a + 3;
        int v4 = a + 4;
        int v5 = a + 5;
        int v6 = a + 6;
        int v7 = a + 7;
        for (int i = 0; i < 1000; i++) {
            Integer box0 = v0;
            sink = box0;
            Integer box1 = v1;
            sink = box1;
            Integer box2 = v2;
            sink = box2;
            Integer box3 = v3;
            sink = box3;
            Integer box4 = v4;
            sink = box4;
            Integer box5 = v5;
            sink = box5;
            Integer box6 = v6;
            sink = box6;
            Integer box7 = v7;
            sink = box7;
        }
        return 0;
    }

    @Benchmark
    public void manySameValueBoxes(ManySameValueBoxes s) {
        s.phase.apply(s.graph, s.graal.providers);
    }

    @Benchmark
    public void manySameValueBoxClusters(ManySameValueBoxClusters s) {
        s.phase.apply(s.graph, s.graal.providers);
    }
}
