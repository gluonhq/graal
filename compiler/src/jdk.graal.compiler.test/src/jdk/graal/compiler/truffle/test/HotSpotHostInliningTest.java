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
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.EarlyInline;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.Truffle;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.host.HostInliningPhase;
import jdk.graal.compiler.truffle.hotspot.HotSpotHostInliningPhase;
import jdk.graal.compiler.truffle.hotspot.TruffleCommunityCompilerConfiguration;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Regression tests for GR-74052.
 * <p>
 * This verifies the HotSpot-only workaround in {@link HotSpotHostInliningPhase} that force-shallow
 * inlines {@link EarlyInline EarlyInline}-annotated helpers at a {@link BytecodeInterpreterSwitch
 * BytecodeInterpreterSwitch} root even when regular host-inlining heuristics would reject the call.
 * <p>
 * This test becomes obsolete once HotSpot has direct bytecode-handler support and the workaround
 * can be removed as part of GR-72604.
 */
public class HotSpotHostInliningTest extends TruffleCompilerImplTest {

    @Test
    public void testEarlyInlineForceShallowInline() throws Throwable {
        Assume.assumeTrue("HotSpot-only test", getBackend() instanceof HotSpotBackend);

        runTest("testWithoutEarlyInline");
        runTest("testWithEarlyInline");
        runTest("testShallowInline");
    }

    private void runTest(String methodName) throws Throwable {
        // initialize the Truffle runtime to ensure that all intrinsics are applied
        Truffle.getRuntime();

        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        AnnotationValue notInlined = AnnotationValueSupport.getAnnotationValue(method, HostInliningTest.ExpectNotInlined.class);
        OptionValues options = new OptionValues(HostInliningTest.createHostInliningOptions(HostInliningTest.NODE_COST_LIMIT, -1),
                        HostInliningPhase.Options.TruffleHostInliningMaxSubtreeInvokes, 0);
        StructuredGraph graph = parseForCompile(method, options);

        getMethod(methodName).invoke(null, 41);

        try (DebugContext.Scope _ = graph.getDebug().scope("Testing", method, graph)) {
            HighTierContext context = getEagerHighTierContext();
            getInstalledHostInliningPhase(options).apply(graph, context);
            HostInliningTest.assertInvokesFound(graph,
                            notInlined != null ? notInlined.getList("name", String.class) : null,
                            notInlined != null ? notInlined.getList("count", Integer.class) : null);
        }
    }

    private HostInliningPhase getInstalledHostInliningPhase(OptionValues options) {
        HighTier highTier = new TruffleCommunityCompilerConfiguration().createHighTier(options);
        var position = highTier.findPhase(HostInliningPhase.class, true);
        Assert.assertNotNull("Expected HostInliningPhase in production high tier", position);
        BasePhase<? super HighTierContext> phase = position.previous();
        Assert.assertTrue("Expected HotSpotHostInliningPhase in production high tier", phase instanceof HotSpotHostInliningPhase);
        return (HostInliningPhase) phase;
    }

    @Override
    protected InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
    }

    @BytecodeInterpreterSwitch
    @HostInliningTest.ExpectNotInlined(name = {"method"}, count = {1})
    static int testWithoutEarlyInline(int value) {
        return method(value);
    }

    @BytecodeInterpreterSwitch
    static int testWithEarlyInline(int value) {
        return earlyInlineMethod(value);
    }

    @BytecodeInterpreterSwitch
    @HostInliningTest.ExpectNotInlined(name = {"nestedMethod"}, count = {1})
    static int testShallowInline(int value) {
        return earlyInlineNestedMethod(value);
    }

    static int method(int value) {
        return value + 1;
    }

    @EarlyInline
    static int earlyInlineMethod(int value) {
        return value + 1;
    }

    @EarlyInline
    static int earlyInlineNestedMethod(int value) {
        return nestedMethod(value);
    }

    static int nestedMethod(int value) {
        return value + 1;
    }
}
