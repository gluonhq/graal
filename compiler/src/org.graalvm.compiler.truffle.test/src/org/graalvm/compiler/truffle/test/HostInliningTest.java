/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.compiler.phases.TruffleHostInliningPhase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Please keep this test in sync with SubstrateTruffleHostInliningTest.
 */
@RunWith(Parameterized.class)
public class HostInliningTest extends GraalCompilerTest {

    static final int NODE_COST_LIMIT = 500;

    public enum TestRun {
        WITH_CONVERT_TO_GUARD,
        DEFAULT,
    }

    @Parameter // first data value (0) is default
    public /* NOT private */ TestRun run;

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.DEFAULT);
    }

    @Test
    public void test() {
        runTest("testBasicInlining");
        runTest("testDominatedDeopt");
        runTest("testTruffleBoundary");
        runTest("testPropagateDeopt");
        runTest("testPropagateDeoptTwoLevels");
        runTest("testRecursive");
        runTest("testNotExplorable");
        runTest("testBecomesDirectAfterInline");
        runTest("testVirtualCall");
        runTest("testInInterpreter1");
        runTest("testInInterpreter2");
        runTest("testInInterpreter3");
        runTest("testInInterpreter4");
        runTest("testInInterpreter5");
        runTest("testInInterpreter6");
        runTest("testInInterpreter7");
        runTest("testInInterpreter8");
        runTest("testInInterpreter9");
        runTest("testInInterpreter10");
        runTest("testInInterpreter11");
        runTest("testInInterpreter12");
        runTest("testExplorationDepth0");
        runTest("testExplorationDepth1");
        runTest("testExplorationDepth2");
        runTest("testExplorationDepth0Fail");
        runTest("testExplorationDepth1Fail");
        runTest("testExplorationDepth2Fail");
        runTest("testBytecodeSwitchtoBytecodeSwitch");
    }

    @SuppressWarnings("try")
    void runTest(String methodName) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        ExplorationDepth depth = method.getAnnotation(ExplorationDepth.class);
        int explorationDepth = -1;
        if (depth != null) {
            explorationDepth = depth.value();
        }

        NodeCostLimit nodeCostLimit = method.getAnnotation(NodeCostLimit.class);
        OptionValues options = createHostInliningOptions(nodeCostLimit != null ? nodeCostLimit.value() : NODE_COST_LIMIT, explorationDepth);
        StructuredGraph graph = parseForCompile(method, options);
        try {
            // call it so all method are initialized
            getMethod(methodName).invoke(null, 5);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError(e);
        }

        try (DebugContext.Scope ds = graph.getDebug().scope("Testing", method, graph)) {
            HighTierContext context = getEagerHighTierContext();
            CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
            if (run == TestRun.WITH_CONVERT_TO_GUARD) {
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, context);
            }
            new TruffleHostInliningPhase(canonicalizer).apply(graph, context);

            ExpectNotInlined notInlined = method.getAnnotation(ExpectNotInlined.class);
            assertInvokesFound(graph, notInlined != null ? notInlined.value() : null);
        } catch (Throwable e) {
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "error graph");
            throw new AssertionError("Error validating graph " + graph, e);
        }

    }

    static void assertInvokesFound(StructuredGraph graph, String[] notInlined) {
        Set<String> found = new HashSet<>();
        List<Invoke> invokes = new ArrayList<>();
        invokes.addAll(graph.getNodes().filter(InvokeNode.class).snapshot());
        invokes.addAll(graph.getNodes().filter(InvokeWithExceptionNode.class).snapshot());

        for (Invoke invoke : invokes) {
            ResolvedJavaMethod invokedMethod = invoke.getTargetMethod();
            if (notInlined == null) {
                Assert.fail("Unexpected node type found in the graph: " + invoke);
            } else {
                for (String expectedMethodName : notInlined) {
                    if (expectedMethodName.equals(invokedMethod.getName())) {
                        if (found.contains(invokedMethod.getName())) {
                            Assert.fail("Found multiple calls to " + invokedMethod.getName() + " but expected one.");
                        }
                        found.add(invokedMethod.getName());
                    }
                }
                if (!found.contains(invokedMethod.getName())) {
                    Assert.fail("Unexpected invoke found " + invoke + ". Expected one of " + Arrays.toString(notInlined));
                }
            }
        }
        if (notInlined != null) {
            for (String expectedMethodName : notInlined) {
                if (!found.contains(expectedMethodName)) {
                    Assert.fail("Expected not inlinined method with name " + expectedMethodName + " but not found.");
                }
            }
        }
    }

    @Override
    protected InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getDeclaringClass().toJavaName().equals(getClass().getName())) {
            return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
        }
        return null;
    }

    static OptionValues createHostInliningOptions(int bytecodeInterpreterLimit, int explorationDepth) {
        EconomicMap<OptionKey<?>, Object> values = EconomicMap.create();
        values.put(TruffleHostInliningPhase.Options.TruffleHostInlining, true);
        values.put(HighTier.Options.Inline, false);
        values.put(TruffleHostInliningPhase.Options.TruffleHostInliningByteCodeInterpreterBudget, bytecodeInterpreterLimit);
        if (explorationDepth != -1) {
            values.put(TruffleHostInliningPhase.Options.TruffleHostInliningMaxExplorationDepth, explorationDepth);
        }
        OptionValues options = new OptionValues(getInitialOptions(), values);
        return options;
    }

    @BytecodeInterpreterSwitch
    private static int testBasicInlining(int value) {
        trivialMethod(); // inlined
        for (int i = 0; i < value; i++) {
            trivialMethod(); // inlined
        }
        try {
            trivialMethod(); // inlined
        } finally {
            trivialMethod(); // inlined
        }
        try {
            trivialMethod(); // inlined
        } catch (Throwable t) {
            trivialMethod(); // inlined
            throw t;
        }
        trivialMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined({"trivialMethod", "traceTransferToInterpreter"})
    private static int testDominatedDeopt(int value) {
        if (value == 1) {
            CompilerDirectives.transferToInterpreterAndInvalidate(); // inlined
            trivialMethod(); // cutoff
        }
        return value;
    }

    static void trivialMethod() {
    }

    static void otherTrivalMethod() {
    }

    /*
     * Non trivial methods must have a cost >= 30.
     */
    static int nonTrivialMethod(int v) {
        int sum = 0;
        sum += (v / 2 + v / 3 + v + v / 4 + v / 5);
        sum += (v / 6 + v / 7 + v + v / 8 + v / 9);
        return sum;
    }

    static void trivalWithBoundary() {
        truffleBoundary();
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("truffleBoundary")
    private static int testTruffleBoundary(int value) {
        if (value == 1) {
            truffleBoundary(); // cutoff
        }
        return value;
    }

    @TruffleBoundary
    static void truffleBoundary() {
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined({"propagateDeopt", "trivialMethod"})
    private static int testPropagateDeopt(int value) {
        if (value == 1) {
            propagateDeopt(); // inlined
            trivialMethod(); // cutoff
        }
        return value;
    }

    static void propagateDeopt() {
        CompilerDirectives.transferToInterpreterAndInvalidate(); // inlined
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined({"trivialMethod", "propagateDeoptLevelTwo"})
    private static int testPropagateDeoptTwoLevels(int value) {
        if (value == 1) {
            propagateDeoptLevelTwo(); // inlined
            trivialMethod(); // cutoff
        }
        return value;
    }

    static void propagateDeoptLevelTwo() {
        propagateDeopt(); // inlined
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("recursive")
    private static int testRecursive(int value) {
        recursive(value); // inlined
        return value;
    }

    static void recursive(int i) {
        if (i == 0) {
            return;
        }
        recursive(i - 1); // cutoff -> recursive
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("notExplorable")
    private static int testNotExplorable(int value) {
        notExplorable(value); // cutoff -> charAt not explorable
        return value;
    }

    static int notExplorable(int value) {
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testBecomesDirectAfterInline(int value) {
        becomesDirectAfterInline(new B_extends_A());
        becomesDirectAfterInline(new C_extends_A());
        return value;
    }

    static void becomesDirectAfterInline(A a) {
        a.foo(); // inlined
    }

    interface A {
        void foo();
    }

    static final class B_extends_A implements A {
        @Override
        public void foo() {
        }
    }

    static final class C_extends_A implements A {
        @Override
        public void foo() {
        }
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("foo")
    private static int testVirtualCall(int value) {
        A a = value == 42 ? new B_extends_A() : new C_extends_A();
        a.foo(); // virtual -> not inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("trivialMethod")
    private static int testInInterpreter1(int value) {
        otherTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter()) {
            trivialMethod(); // cutoff
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("trivialMethod")
    private static int testInInterpreter2(int value) {
        otherTrivalMethod(); // inlined
        if (value == 24) {
            otherTrivalMethod(); // inlined
            if (CompilerDirectives.inInterpreter()) {
                if (value == 24) {
                    trivialMethod(); // cutoff
                }
            }
            otherTrivalMethod(); // inlined
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("trivialMethod")
    private static int testInInterpreter3(int value) {
        otherTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter() && value == 24) {
            trivialMethod(); // cutoff
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testInInterpreter4(int value) {
        otherTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter()) {
            if (CompilerDirectives.inCompiledCode()) {
                trivialMethod(); // dead
            }
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testInInterpreter5(int value) {
        otherTrivalMethod(); // inlined
        if (!CompilerDirectives.inInterpreter()) {
            trivialMethod(); // dead
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("trivialMethod")
    private static int testInInterpreter6(int value) {
        otherTrivalMethod(); // inlined
        boolean condition = CompilerDirectives.inInterpreter();
        if (condition) {
            trivialMethod(); // cutoff
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("foo")
    private static int testInInterpreter7(int value) {
        testInInterpreter7Impl(new B_extends_A());
        return value;
    }

    static void testInInterpreter7Impl(A a) {
        if (CompilerDirectives.inInterpreter()) {
            a.foo(); // not inlined even if it becomes direct
        }
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("foo")
    static int testInInterpreter8(int value) {
        boolean b = constant();
        A type = b ? new B_extends_A() : new C_extends_A();
        if (CompilerDirectives.inInterpreter()) {
            type.foo();
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    static int testInInterpreter9(int value) {
        boolean b = constant();
        A type = b ? new B_extends_A() : new C_extends_A();
        if (!CompilerDirectives.inInterpreter()) {
            type.foo();
        }
        return value;
    }

    static boolean constant() {
        return true;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("trivialMethod")
    static int testInInterpreter10(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }
        trivialMethod();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("trivialMethod")
    static int testInInterpreter11(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }

        if (value == 5) {
            trivialMethod();
            return 42;
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    static int testInInterpreter12(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            if (value == 5) {
                trivialMethod();
            }
            return 42;
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return -1;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("explorationDepth0")
    @ExplorationDepth(0)
    static int testExplorationDepth0Fail(int value) {
        explorationDepth0();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExplorationDepth(1)
    static int testExplorationDepth0(int value) {
        explorationDepth0();
        return value;
    }

    static void explorationDepth0() {
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("explorationDepth1")
    @ExplorationDepth(1)
    static int testExplorationDepth1Fail(int value) {
        explorationDepth1();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExplorationDepth(2)
    static int testExplorationDepth1(int value) {
        explorationDepth1();
        return value;
    }

    static void explorationDepth1() {
        explorationDepth0();
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("explorationDepth2")
    @ExplorationDepth(2)
    static int testExplorationDepth2Fail(int value) {
        explorationDepth2();
        return value;
    }

    static void explorationDepth2() {
        explorationDepth1();
    }

    @BytecodeInterpreterSwitch
    @ExplorationDepth(3)
    static int testExplorationDepth2(int value) {
        explorationDepth0();
        return value;
    }

    // bytecode dispatches should inline no matter the budget.
    @NodeCostLimit(0)
    @BytecodeInterpreterSwitch
    @ExpectNotInlined({"nonTrivialMethod", "trivalWithBoundary"})
    static int testBytecodeSwitchtoBytecodeSwitch(int value) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            switch (i) {
                case 0:
                    result = 4;
                    break;
                case 1:
                    result = 3;
                    break;
                case 2:
                    result = 6;
                    break;
                default:
                    result = bytecodeSwitchtoBytecodeSwitch1(value);
                    break;
            }
        }
        return result;
    }

    // methods with bytecode switch are always inlined into bytecode switches
    @BytecodeInterpreterSwitch
    static int bytecodeSwitchtoBytecodeSwitch1(int value) {
        switch (value) {
            case 3:
                return 4;
            case 4:
                return 1;
            case 5:
                return 7;
            default:
                return bytecodeSwitchtoBytecodeSwitch3(value);
        }
    }

    // methods with bytecode switch are always inlined into bytecode switches,
    // also transitively.
    @BytecodeInterpreterSwitch
    static int bytecodeSwitchtoBytecodeSwitch3(int value) {
        switch (value) {
            case 6:
                return 4;
            case 7:
                // not inlined, as not trivial (invokes >= 0)
                trivalWithBoundary();
                return 3;
            case 8:
                // this is not inlined its not trivial.
                return nonTrivialMethod(value);
            default:
                /*
                 * this call is still inlined because it is trivial.
                 */
                trivialMethod();
                return -1;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ExpectNotInlined {
        String[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ExplorationDepth {
        int value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface NodeCostLimit {
        int value();
    }

    interface RuntimeCompilable {

        Object call(int argument);

    }

    static class MakeRuntimeCompileReachable extends RootNode {

        final RuntimeCompilable compilable;

        MakeRuntimeCompileReachable(RuntimeCompilable compilable) {
            super(null);
            this.compilable = compilable;
        }

        @Override

        public Object execute(VirtualFrame frame) {
            return compilable.call((int) frame.getArguments()[0]);
        }

    }

}
