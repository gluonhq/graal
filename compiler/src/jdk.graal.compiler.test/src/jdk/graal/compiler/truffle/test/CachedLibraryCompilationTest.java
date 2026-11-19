/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.truffle.test.CachedLibraryCompilationTestFactory.FrameNodeGen;
import jdk.graal.compiler.truffle.test.CachedLibraryCompilationTestFactory.GuardNodeGen;
import jdk.graal.compiler.truffle.test.CachedLibraryCompilationTestFactory.NoGuardNodeGen;
import jdk.graal.compiler.truffle.test.CachedLibraryCompilationTestFactory.VarArgsLibraryNodeGen;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("truffle-inlining")
public class CachedLibraryCompilationTest extends PartialEvaluationTest {

    private Context context;

    @Before
    public void setup() {
        cleanup();
        context = Context.newBuilder().allowExperimentalOptions(true).option("engine.CompilationFailureAction", "Throw").option("engine.BackgroundCompilation", "false").option(
                        "compiler.TreatPerformanceWarningsAsErrors", "all").option("compiler.InliningPolicy", "Default").build();
        context.enter();
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    /*
     * Depending on runtime/compiler initialization order, the uncached interop path may retain one
     * helper-only call in the PE graph. Count the semantically relevant dispatch/boundary invokes
     * and ignore that optional helper call.
     */
    private static long countRelevantMethodCalls(StructuredGraph graph) {
        long optionalHelperCalls = 0;
        long relevantCalls = 0;
        for (MethodCallTargetNode callTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (isOptionalHelperCall(callTargetNode)) {
                optionalHelperCalls++;
            } else {
                relevantCalls++;
            }
        }
        Assert.assertTrue("Unexpected optional helper calls: " + describeMethodCalls(graph), optionalHelperCalls <= 1);
        return relevantCalls;
    }

    private static boolean isOptionalHelperCall(MethodCallTargetNode callTargetNode) {
        ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
        String declaringClassName = targetMethod.getDeclaringClass().toJavaName();
        String methodName = targetMethod.getName();
        return declaringClassName.equals(EncapsulatingNodeReference.class.getName()) && methodName.equals("getThreadLocal") ||
                        declaringClassName.equals(LibraryFactory.class.getName()) && methodName.equals("initializeUncached");
    }

    private static String describeMethodCalls(StructuredGraph graph) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (MethodCallTargetNode callTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (!first) {
                builder.append(", ");
            }
            ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
            builder.append(targetMethod.getDeclaringClass().toJavaName()).append('#').append(targetMethod.getName());
            first = false;
        }
        return builder.toString();
    }

    abstract static class GuardNode extends Node {

        static int LIMIT = 0;

        public abstract int execute(Object arg);

        @Specialization(guards = "lib.fitsInInt(arg)", limit = "LIMIT")
        int withGuard(Object arg,
                        @CachedLibrary("arg") InteropLibrary lib) {
            try {
                return lib.asInt(arg);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

    }

    @Test
    public void testUncachedGuard() {
        StructuredGraph graph = partialEval(new RootNode(null) {
            @Child GuardNode node = GuardNodeGen.create();

            @Override
            public Object execute(VirtualFrame frame) {
                node.execute(42);
                return null;
            }
        });
        Assert.assertTrue(describeMethodCalls(graph), countRelevantMethodCalls(graph) == 2);
    }

    abstract static class NoGuardNode extends Node {

        static int LIMIT = 0;

        public abstract int execute(Object arg);

        @Specialization(limit = "LIMIT")
        int noGuard(Object arg,
                        @CachedLibrary("arg") InteropLibrary lib) {
            try {
                return lib.asInt(arg);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Test
    public void testUncachedNoGuard() {
        RootNode testRoot = new RootNode(null) {
            @Child NoGuardNode node = NoGuardNodeGen.create();

            @Override
            public Object execute(VirtualFrame frame) {
                node.execute(42);
                return null;
            }
        };

        StructuredGraph graph = partialEval(testRoot);
        Assert.assertTrue(describeMethodCalls(graph), countRelevantMethodCalls(graph) == 1);
    }

    abstract static class FrameNode extends Node {

        static int LIMIT = 0;

        public abstract int execute(VirtualFrame frame, Object arg);

        @Specialization(limit = "LIMIT")
        int withGuard(@SuppressWarnings("unused") VirtualFrame frame, Object arg,
                        @CachedLibrary("arg") InteropLibrary lib) {
            try {
                return lib.asInt(arg);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

    }

    @Test
    public void testUncachedFrame() {
        RootNode testRoot = new RootNode(null) {
            @Child FrameNode node = FrameNodeGen.create();

            @Override
            public Object execute(VirtualFrame frame) {
                node.execute(frame, 42);
                return null;
            }
        };

        StructuredGraph graph = partialEval(testRoot);
        Assert.assertTrue(describeMethodCalls(graph), countRelevantMethodCalls(graph) == 1);
    }

    @Test
    public void testVarArgsLibraryNode() {
        RootNode testRoot = new RootNode(null) {
            @Child VarArgsLibraryNode node = VarArgsLibraryNodeGen.create();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(42, 11, 31);
            }
        };

        StructuredGraph graph = partialEval(testRoot);
        Assert.assertTrue(describeMethodCalls(graph), countRelevantMethodCalls(graph) == 3);
    }

    abstract static class VarArgsLibraryNode extends Node {

        static int LIMIT = 0;

        protected abstract Object execute(Object arg0, Object... args);

        @Specialization(guards = {"lib1.fitsInByte(arg1)", "lib2.fitsInByte(arg2)"}, limit = "LIMIT")
        int varargs(@SuppressWarnings("unused") Object arg0, Object arg1, Object arg2,
                        @CachedLibrary("arg1") InteropLibrary lib1,
                        @CachedLibrary("arg2") InteropLibrary lib2) {
            try {
                return lib1.asInt(arg1) + lib2.asInt(arg2);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

    }

}
