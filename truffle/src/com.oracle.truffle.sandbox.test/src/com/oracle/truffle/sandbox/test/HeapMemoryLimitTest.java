/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sandbox.test;

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.api.test.ThreadUtils;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class HeapMemoryLimitTest {
    static Context.Builder maxHeapMemory(Context.Builder builder, String limit) {
        return maxHeapMemory(builder, limit, null, true, null, null);
    }

    @Before
    public void before() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
    }

    static Context.Builder maxHeapMemory(Context.Builder builder, String limit, Double allocatedBytesCheckFactor, boolean allocatedBytesCheckEnabled, Double retainedBytesCheckFactor,
                    Boolean useLowMemoryTrigger) {
        builder.allowExperimentalOptions(true);
        builder.option("sandbox.MaxHeapMemory", limit);
        if (allocatedBytesCheckFactor != null) {
            builder.option("sandbox.AllocatedBytesCheckFactor", String.format(Locale.US, "%.3f", allocatedBytesCheckFactor));
        }
        builder.option("sandbox.AllocatedBytesCheckEnabled", Boolean.toString(allocatedBytesCheckEnabled));
        if (retainedBytesCheckFactor != null) {
            builder.option("sandbox.RetainedBytesCheckFactor", String.format(Locale.US, "%.3f", retainedBytesCheckFactor));
        }
        if (useLowMemoryTrigger != null) {
            builder.option("sandbox.UseLowMemoryTrigger", Boolean.toString(useLowMemoryTrigger));
        }
        return builder;
    }

    private static void assertMemoryLimitExceeded(Context c, PolyglotException e) {
        ResourceLimitsTest.assertResourceExceeded(c, e, "Maximum heap memory limit of");
    }

    @Test
    public void testMemoryLimit() {
        Context.Builder builder = Context.newBuilder();
        /*
         * Isolated context doesn't have PrintStreams in
         * com.oracle.truffle.api.instrumentation.test.InstrumentContext's out and err streams.
         * There are only references to the streams that live on the host side. PrintStream has
         * BufferedWriter inside which in turn has a char buffer of default size 8192 characters.
         * Not having all that makes the isolated context's retained size much smaller, and so the
         * safe limit for all scenarios is 1KB. For non-isolated context we could have used 10KB.
         */
        maxHeapMemory(builder, "1KB");
        try (Context context = builder.build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            try {
                ResourceLimitsTest.evalStatements(context);
                fail();
            } catch (PolyglotException e) {
                assertMemoryLimitExceeded(context, e);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testMemoryLimitThreadCollected() throws IOException, InterruptedException {
        Runnable runnable = () -> {
            Context.Builder builder = Context.newBuilder();
            maxHeapMemory(builder, "1MB");
            try (Context context = builder.build()) {
                Thread t = new Thread(() -> {
                    context.initialize(InstrumentationTestLanguage.ID);
                    context.eval(ResourceLimitsTest.statements(10));
                });
                t.start();
                t.join();
                Reference<Thread> threadRef = new WeakReference<>(t);
                t = null;
                GCUtils.assertGc("Thread with allocated bytes tracking was not collected!", threadRef);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        };
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(HeapMemoryLimitTest.class, runnable).run();
        }
    }

    @Test
    public void testMemoryLimitNonExceeding() {
        Context.Builder builder = Context.newBuilder();
        maxHeapMemory(builder, "1MB");
        try (Context context = builder.build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            context.eval(ResourceLimitsTest.statements(100));
        }
    }

    @Test
    public void testHigherMemoryLimit() throws IOException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        Context.Builder builder = Context.newBuilder();
        maxHeapMemory(builder, "1MB");
        try (Context context = builder.build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            try {
                for (int i = 0; i < 20000; i++) {
                    Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "DEFINE(foobar" + i + ",STATEMENT)", ResourceLimitsTest.class.getSimpleName() + i).build();
                    context.eval(source);
                }
                // Infinite loop so that the context does not finish before memory limit violation
                // is detected.
                ResourceLimitsTest.evalStatements(context);
                fail();
            } catch (PolyglotException e) {
                assertMemoryLimitExceeded(context, e);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testMemoryLimitInnerContext() throws IOException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        Context.Builder builder = Context.newBuilder();
        maxHeapMemory(builder, "1MB");
        try (Context context = builder.build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("CONTEXT(");
                for (int i = 0; i < 20000; i++) {
                    sb.append("DEFINE(foobar").append(i).append(",STATEMENT),");
                }
                /*
                 * Makes sure new bytes are allocated. Even though the retained size is greater than
                 * the limit, most of the allocations are done before the inner context is entered,
                 * because they are immutable function name strings in the define node. Call targets
                 * are also allocated during parsing, and so they don't cause any new allocation
                 * when define nodes are executed. Only allocations are done by the callTargets hash
                 * map, which is not enough to go over the memory limit.
                 */
                sb.append("LOOP(infinity,ALLOCATION))");
                Source source = Source.newBuilder(InstrumentationTestLanguage.ID, sb.toString(), ResourceLimitsTest.class.getSimpleName() + "InnerContext").build();
                context.eval(source);
                fail();
            } catch (PolyglotException e) {
                assertMemoryLimitExceeded(context, e);
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    static class Job implements Callable<Void> {
        private final Engine engine;
        private final int statementCount;
        private final int defineCount;

        Job(Engine engine, int defineCount, int statementCount) {
            this.engine = engine;
            this.defineCount = defineCount;
            this.statementCount = statementCount;
        }

        @Override
        public Void call() throws Exception {
            Context.Builder builder = Context.newBuilder();
            maxHeapMemory(builder, "1MB");

            if (engine != null) {
                builder.engine(engine);
            }

            try (Context context = builder.build()) {
                context.initialize(InstrumentationTestLanguage.ID);
                try {
                    for (int i = 0; i < defineCount; i++) {
                        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "DEFINE(foobar" + i + ",STATEMENT)", ResourceLimitsTest.class.getSimpleName() + i).build();
                        context.eval(source);
                    }
                    context.eval(ResourceLimitsTest.statements(statementCount));
                    if (statementCount == Integer.MAX_VALUE) {
                        fail();
                    }
                } catch (PolyglotException e) {
                    if (statementCount == Integer.MAX_VALUE) {
                        assertMemoryLimitExceeded(context, e);
                    } else {
                        throw e;
                    }
                }
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
            return null;
        }
    }

    @Test
    public void testNonExceedingMemoryLimitInParallelSharedEngine() throws InterruptedException, ExecutionException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        try (Engine engine = Engine.create()) {
            testMemoryLimitInParallel(engine, 20, 1000, 1, 1);
        }
    }

    @Test
    public void testHigherMemoryLimitInParallelSharedEngine() throws InterruptedException, ExecutionException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        try (Engine engine = Engine.create()) {
            testMemoryLimitInParallel(engine, 10, 1000, 20000, Integer.MAX_VALUE);
        }
    }

    @Test
    public void testHigherMemoryLimitInAnotherThread() throws InterruptedException, ExecutionException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        try (Engine engine = Engine.create()) {
            testMemoryLimitInParallel(engine, 1, 1, 20000, Integer.MAX_VALUE);
        }
    }

    @Test
    public void testHigherMemoryLimitInParallelMultiEngine() throws InterruptedException, ExecutionException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        testMemoryLimitInParallel(null, 10, 100, 20000, Integer.MAX_VALUE);
    }

    // We don't run this with virtual threads because of GR-54567
    private static void testMemoryLimitInParallel(Engine engine, int threads, int jobs, int defineCount, int statementCount) throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < jobs; i++) {
            futures.add(executorService.submit(new Job(engine, defineCount, statementCount)));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executorService.shutdownNow();
        assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
    }

    @Test
    public void testMemoryLimitInvalidRetainedBytesCheckFactorCombination() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context.Builder builder1 = Context.newBuilder();
        maxHeapMemory(builder1, "1MB", null, true, 0.4d, true);

        Context.Builder builder2 = Context.newBuilder();
        maxHeapMemory(builder2, "1MB");

        builder1.build().close();
        builder2.build().close();

        Context ctx = builder1.build();
        try {
            try {
                builder2.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid retained bytes check factor '" + String.format(Locale.US, "%.6f", 0.7d) + "' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.RetainedBytesCheckFactor'. " +
                                "To resolve this use the same option value for 'sandbox.RetainedBytesCheckFactor' for all contexts.",
                                e.getMessage());
            }
        } finally {
            ctx.close();
        }

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            Context.Builder builder = builder2.engine(engine);
            try {
                builder.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid retained bytes check factor '" + String.format(Locale.US, "%.6f", 0.7d) + "' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.RetainedBytesCheckFactor'. " +
                                "To resolve this use the same option value for 'sandbox.RetainedBytesCheckFactor' for all contexts.",
                                e.getMessage());
            }
        }
    }

    @Test
    public void testThreadCountSharedEngine() throws ExecutionException, InterruptedException {
        testThreadCount(true);
    }

    @Test
    public void testThreadCountBoundEngine() throws ExecutionException, InterruptedException {
        /*
         * We don't have enough resources to create so many isolates.
         */
        TruffleTestAssumptions.assumeNoIsolateEncapsulation();
        testThreadCount(false);
    }

    /*
     * Test that the number of limit checker threads stays reasonable
     */
    private static void testThreadCount(boolean sharedEngine) throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (Engine engine = Engine.create()) {
            Context.Builder builder = Context.newBuilder();
            if (sharedEngine) {
                builder.engine(engine);
            }
            /*
             * The limits are never exceeded.
             */
            maxHeapMemory(builder, "1MB");
            ResourceLimitsTest.maxCPUTime(builder, "1h", null);
            int contextCount = 1000;
            int jobCount = 100;
            Random random = new Random();
            Context[] contextArray = new Context[contextCount];
            for (int i = 0; i < contextArray.length; i++) {
                contextArray[i] = builder.build();
            }
            /*
             * Join Limit Checker Threads started during context creation, so that we are checking
             * only the threads started due to context activity.
             */
            Thread[] threads = ThreadUtils.getAllThreads();
            for (Thread t : threads) {
                if (t.getName() != null && t.getName().contains("Limit Checker Thread")) {
                    t.join();
                }
            }
            try {
                AtomicInteger maxThreadCount = new AtomicInteger();
                AtomicInteger cnt = new AtomicInteger();
                AtomicLong sum = new AtomicLong();
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < jobCount; i++) {
                    int contextIndex = random.nextInt(contextCount);
                    futures.add(executorService.submit(() -> {
                        contextArray[contextIndex].eval(ResourceLimitsTest.statements(1000000));
                        int[] sandboxThreadCount = new int[1];
                        Thread[] threads1 = ThreadUtils.getAllThreads();
                        for (Thread t : threads1) {
                            if (t.getName() != null && t.getName().contains("Limit Checker Thread")) {
                                sandboxThreadCount[0]++;
                            }
                        }
                        maxThreadCount.getAndUpdate(operand -> Math.max(sandboxThreadCount[0], operand));
                        cnt.incrementAndGet();
                        sum.addAndGet(sandboxThreadCount[0]);
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
                double averageThreadCount = 1.0d * sum.get() / cnt.get();
                if (sharedEngine) {
                    Assert.assertTrue("Maximum limit checker thread count too high " + maxThreadCount.get(), maxThreadCount.get() <= 2);
                    Assert.assertTrue("Average limit checker thread count too high " + averageThreadCount, averageThreadCount < 2.00000001d);
                } else {
                    Assert.assertTrue("Maximum limit checker thread count too high " + maxThreadCount.get(), maxThreadCount.get() <= 2 * jobCount);
                    Assert.assertTrue("Average limit checker thread count too high " + averageThreadCount, averageThreadCount < 2.0d * jobCount + 0.00000001d);
                }
            } finally {
                for (Context c : contextArray) {
                    c.close();
                }
            }
        } finally {
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testMemoryLimitInvalidLowMemoryTriggerEnabledDisabledCombination() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context.Builder builder1 = Context.newBuilder();
        maxHeapMemory(builder1, "1MB");

        Context.Builder builder2 = Context.newBuilder();
        maxHeapMemory(builder2, "1MB", null, true, null, false);

        builder1.build().close();
        builder2.build().close();

        Context ctx = builder1.build();
        try {
            try {
                builder2.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid 'sandbox.UseLowMemoryTrigger' option value 'false' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.UseLowMemoryTrigger'. " +
                                "To resolve this use the same option value for 'sandbox.UseLowMemoryTrigger' for all contexts.",
                                e.getMessage());
            }
        } finally {
            ctx.close();
        }

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            Context.Builder builder = builder2.engine(engine);
            try {
                builder.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid 'sandbox.UseLowMemoryTrigger' option value 'false' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.UseLowMemoryTrigger'. " +
                                "To resolve this use the same option value for 'sandbox.UseLowMemoryTrigger' for all contexts.",
                                e.getMessage());
            }
        }
    }

    @Test
    public void testMemoryLimitInvalidLowMemoryTriggerDisabledEnabledCombination() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context.Builder builder1 = Context.newBuilder();
        maxHeapMemory(builder1, "1MB", null, true, null, false);

        Context.Builder builder2 = Context.newBuilder();
        maxHeapMemory(builder2, "1MB");

        builder1.build().close();
        builder2.build().close();

        Context ctx = builder1.build();
        try {
            try {
                builder2.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid 'sandbox.UseLowMemoryTrigger' option value 'true' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.UseLowMemoryTrigger'. " +
                                "To resolve this use the same option value for 'sandbox.UseLowMemoryTrigger' for all contexts.",
                                e.getMessage());
            }
        } finally {
            ctx.close();
        }

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            Context.Builder builder = builder2.engine(engine);
            try {
                builder.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid 'sandbox.UseLowMemoryTrigger' option value 'true' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.UseLowMemoryTrigger'. " +
                                "To resolve this use the same option value for 'sandbox.UseLowMemoryTrigger' for all contexts.",
                                e.getMessage());
            }
        }
    }

    @Test
    public void testMemoryLimitInvalidLowMemoryTriggerEnabledEnabledCombination() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context.Builder builder1 = Context.newBuilder();
        maxHeapMemory(builder1, "1MB");

        Context.Builder builder2 = Context.newBuilder();
        maxHeapMemory(builder2, "1MB");
        builder2.option("sandbox.ReuseLowMemoryTriggerThreshold", "true");

        builder1.build().close();
        builder2.build().close();

        Context ctx = builder1.build();
        try {
            try {
                builder2.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid 'sandbox.ReuseLowMemoryTriggerThreshold' option value 'true' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.ReuseLowMemoryTriggerThreshold'. " +
                                "To resolve this use the same option value for 'sandbox.ReuseLowMemoryTriggerThreshold' for all contexts.",
                                e.getMessage());
            }
        } finally {
            ctx.close();
        }

        try (Engine engine = Engine.create()) {
            builder1.engine(engine).build().close();
            Context.Builder builder = builder2.engine(engine);
            try {
                builder.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Invalid 'sandbox.ReuseLowMemoryTriggerThreshold' option value 'true' detected. " +
                                "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.ReuseLowMemoryTriggerThreshold'. " +
                                "To resolve this use the same option value for 'sandbox.ReuseLowMemoryTriggerThreshold' for all contexts.",
                                e.getMessage());
            }
        }
    }

    @Test
    public void testLowMemoryTriggerAlreadyInUse() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context.Builder builder1 = Context.newBuilder();
        maxHeapMemory(builder1, "1MB", null, true, null, false);

        Context.Builder builder2 = Context.newBuilder();
        maxHeapMemory(builder2, "1MB");

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getType() == MemoryType.HEAP).collect(Collectors.toList());
        String firstPoolName = null;
        for (MemoryPoolMXBean pool : pools) {
            if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                continue;
            }
            pool.setCollectionUsageThreshold(1);
            pool.setUsageThreshold(1);
            if (firstPoolName == null) {
                firstPoolName = pool.getName();
            }
        }
        try {
            /*
             * Low memory trigger not used => no problem.
             */
            builder1.build().close();

            try {
                builder2.build();
                fail();
            } catch (PolyglotException e) {
                assertEquals("Low memory trigger for heap memory limit cannot be installed. " +
                                "Collection usage threshold or usage threshold of memory pool " + firstPoolName + " is already in use. " +
                                "To resolve this set the option 'sandbox.UseLowMemoryTrigger' to 'false' or the option 'sandbox.ReuseLowMemoryTriggerThreshold' to 'true'.",
                                e.getMessage());
            }
        } finally {
            for (MemoryPoolMXBean pool : pools) {
                if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                    continue;
                }
                assertEquals(pool.getCollectionUsageThreshold(), 1);
                assertEquals(pool.getUsageThreshold(), 1);
                pool.setCollectionUsageThreshold(0);
                pool.setUsageThreshold(0);
            }
        }
    }

    @Test
    public void testLowMemoryTriggerNotSupportedInAOTMode() {
        Assume.assumeTrue(TruffleTestAssumptions.isAOT() || TruffleTestAssumptions.isStrongEncapsulation());

        Context.Builder builder1 = Context.newBuilder();
        maxHeapMemory(builder1, "1MB", null, true, null, true);
        try {
            builder1.build();
            fail();
        } catch (PolyglotException e) {
            assertEquals("Use of the low memory trigger is not supported in the ahead-of-time compilation mode. " +
                            "To resolve this set 'sandbox.UseLowMemoryTrigger' to false.",
                            e.getMessage());
        }
    }

    @Test
    public void testLowMemoryTriggerReuse() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        Context.Builder builder = Context.newBuilder();
        maxHeapMemory(builder, "1MB");
        builder.option("sandbox.ReuseLowMemoryTriggerThreshold", "true");

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getType() == MemoryType.HEAP).collect(Collectors.toList());
        for (MemoryPoolMXBean pool : pools) {
            if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                continue;
            }
            pool.setCollectionUsageThreshold(1);
            pool.setUsageThreshold(1);
        }
        try {
            /*
             * Low memory trigger thresholds reused => no problem.
             */
            builder.build().close();
        } finally {
            for (MemoryPoolMXBean pool : pools) {
                if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                    continue;
                }
                assertEquals(pool.getCollectionUsageThreshold(), 1);
                assertEquals(pool.getUsageThreshold(), 1);
                pool.setCollectionUsageThreshold(0);
                pool.setUsageThreshold(0);
            }
        }

        for (MemoryPoolMXBean pool : pools) {
            if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                continue;
            }
            pool.setCollectionUsageThreshold(1);
        }
        try {
            /*
             * Low memory trigger thresholds reused => no problem.
             */
            builder.build().close();
        } finally {
            for (MemoryPoolMXBean pool : pools) {
                if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                    continue;
                }
                assertEquals(pool.getCollectionUsageThreshold(), 1);
                pool.setCollectionUsageThreshold(0);
                assertEquals(pool.getUsageThreshold(), 0);
            }
        }

        for (MemoryPoolMXBean pool : pools) {
            if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                continue;
            }
            pool.setUsageThreshold(1);
        }
        try {
            /*
             * Low memory trigger thresholds reused => no problem.
             */
            builder.build().close();
        } finally {
            for (MemoryPoolMXBean pool : pools) {
                if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                    continue;
                }
                assertEquals(pool.getUsageThreshold(), 1);
                pool.setUsageThreshold(0);
                assertEquals(pool.getCollectionUsageThreshold(), 0);
            }
        }
    }

    @Test
    public void testMemoryLimitInvalidAllocatedBytesCheckFactor() {
        maxHeapMemory(Context.newBuilder(), "1MB", 0.0d, true, null, null).build().close();
        maxHeapMemory(Context.newBuilder(), "1MB", 0.5d, true, null, null).build().close();
        maxHeapMemory(Context.newBuilder(), "1MB", 1.0d, true, null, null).build().close();
        maxHeapMemory(Context.newBuilder(), "1MB", 5.0d, true, null, null).build().close();
        try {
            maxHeapMemory(Context.newBuilder(), "1MB", -0.01d, true, null, null).build();
            fail();
        } catch (PolyglotException e) {
            assertEquals("Invalid allocated bytes check factor '" + String.format(Locale.US, "%.6f", -0.01d) + "'. Value greater or equal to 0.0 is expected.", e.getMessage());
        }
    }

    @Test
    public void testMemoryLimitInvalidRetainedBytesCheckFactor() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        maxHeapMemory(Context.newBuilder(), "1MB", null, true, 0.4d, true).build().close();
        maxHeapMemory(Context.newBuilder(), "1MB", null, true, 0.0d, true).build().close();
        maxHeapMemory(Context.newBuilder(), "1MB", null, true, 1.0d, true).build().close();
        try {
            maxHeapMemory(Context.newBuilder(), "1MB", null, true, -0.01d, true).build();
            fail();
        } catch (PolyglotException e) {
            assertEquals("Invalid retained bytes check factor '" + String.format(Locale.US, "%.6f", -0.01d) + "'. Value between 0.0 and 1.0 is expected.", e.getMessage());
        }
        try {
            maxHeapMemory(Context.newBuilder(), "1MB", null, true, 1.01d, true).build();
            fail();
        } catch (PolyglotException e) {
            assertEquals("Invalid retained bytes check factor '" + String.format(Locale.US, "%.6f", 1.01d) + "'. Value between 0.0 and 1.0 is expected.", e.getMessage());
        }
    }

    @Test
    public void testMemoryLimitInvalidAllocatedBytesCheckEnabledLowMemoryTriggerEnabled() {
        // Low memory trigger not supported in AOT mode.
        TruffleTestAssumptions.assumeNotAOT();
        TruffleTestAssumptions.assumeWeakEncapsulation();

        maxHeapMemory(Context.newBuilder(), "1MB", null, true, null, true).build().close();
        maxHeapMemory(Context.newBuilder(), "1MB", null, true, null, false).build().close();
        maxHeapMemory(Context.newBuilder(), "1MB", null, false, null, true).build().close();
        try {
            maxHeapMemory(Context.newBuilder(), "1MB", null, false, null, false).build();
            fail();
        } catch (PolyglotException e) {
            assertEquals("AllocatedBytesCheckEnabled and UseLowMemoryTrigger cannot both be false.", e.getMessage());
        }
    }

    @Test
    public void testMemoryLimitErrors() {
        assertSizeOption("sandbox.MaxHeapMemory");
        ResourceLimitsTest.assertTimeUnitOption("sandbox.AllocatedBytesCheckInterval", false);
        ResourceLimitsTest.assertTimeUnitOption("sandbox.RetainedBytesCheckInterval", false);
    }

    private static void assertSizeOption(String optionName) {
        // valid values
        ResourceLimitsTest.newContextBuilder().option(optionName, "0B").build().close();
        ResourceLimitsTest.newContextBuilder().option(optionName, "1B").build().close();
        ResourceLimitsTest.newContextBuilder().option(optionName, "1KB").build().close();
        ResourceLimitsTest.newContextBuilder().option(optionName, "1MB").build().close();
        ResourceLimitsTest.newContextBuilder().option(optionName, "1GB").build().close();
        ResourceLimitsTest.newEngineBuilder().option(optionName, "1B").build().close();
        ResourceLimitsTest.newEngineBuilder().option(optionName, "1KB").build().close();
        ResourceLimitsTest.newEngineBuilder().option(optionName, "1MB").build().close();
        ResourceLimitsTest.newEngineBuilder().option(optionName, "1GB").build().close();
        // Long.MAX_VALUE is valid in B
        ResourceLimitsTest.newContextBuilder().option(optionName, Long.MAX_VALUE + "B").build().close();
        ResourceLimitsTest.newEngineBuilder().option(optionName, Long.MAX_VALUE + "B").build().close();

        // missing unit
        assertFails(() -> ResourceLimitsTest.newContextBuilder().option(optionName, String.valueOf(42)).build(), IllegalArgumentException.class);
        assertFails(() -> ResourceLimitsTest.newEngineBuilder().option(optionName, String.valueOf(42)).build(), IllegalArgumentException.class);
        // negative integer
        assertFails(() -> ResourceLimitsTest.newContextBuilder().option(optionName, String.valueOf(-42)).build(), IllegalArgumentException.class);
        assertFails(() -> ResourceLimitsTest.newEngineBuilder().option(optionName, String.valueOf(-42)).build(), IllegalArgumentException.class);
        // negative integer with unit
        assertFails(() -> ResourceLimitsTest.newContextBuilder().option(optionName, "-42B").build(), IllegalArgumentException.class);
        assertFails(() -> ResourceLimitsTest.newEngineBuilder().option(optionName, "-42B").build(), IllegalArgumentException.class);
        // wrong unit
        assertFails(() -> ResourceLimitsTest.newContextBuilder().option(optionName, "42a").build(), IllegalArgumentException.class);
        assertFails(() -> ResourceLimitsTest.newEngineBuilder().option(optionName, "42a").build(), IllegalArgumentException.class);
        // invalid precision number
        assertFails(() -> ResourceLimitsTest.newContextBuilder().option(optionName, "42.1B").build(), IllegalArgumentException.class);
        assertFails(() -> ResourceLimitsTest.newEngineBuilder().option(optionName, "42.1B").build(), IllegalArgumentException.class);
        // long overflow
        assertFails(() -> ResourceLimitsTest.newContextBuilder().option(optionName, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(1)) + "ms").build(), IllegalArgumentException.class);
        assertFails(() -> ResourceLimitsTest.newEngineBuilder().option(optionName, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(1)) + "ms").build(), IllegalArgumentException.class);
        // long overflow caused by unit
        assertFails(() -> ResourceLimitsTest.newContextBuilder().option(optionName, Long.MAX_VALUE + "KB").build(), IllegalArgumentException.class);
        assertFails(() -> ResourceLimitsTest.newEngineBuilder().option(optionName, Long.MAX_VALUE + "KB").build(), IllegalArgumentException.class);
    }

}
