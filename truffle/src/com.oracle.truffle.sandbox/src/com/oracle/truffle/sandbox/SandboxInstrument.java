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
package com.oracle.truffle.sandbox;

import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

@Registration(id = SandboxInstrument.ID, name = "Sandbox", website = "https://www.graalvm.org/dev/reference-manual/embed-languages/sandbox-resource-limits/", sandbox = SandboxPolicy.UNTRUSTED)
public final class SandboxInstrument extends TruffleInstrument {

    static final String ID = "sandbox";
    private static final AtomicLong idGenerator = new AtomicLong();

    private static final OptionDescriptors CONTEXT_OPTIONS = new SandboxContextOptionDescriptors();

    private volatile boolean disposed;
    private volatile JoinableThreadPoolExecutor cancelExecutor;
    private volatile JoinableThreadPoolExecutor limitCheckerExecutor;
    private volatile JoinableThreadPoolExecutor pauseInstrumentExecutor;
    private volatile JoinableThreadPoolExecutor retainedSizeCheckerExecutor;

    private static final EconomicSet<WeakReference<SandboxInstrument>> sandboxInstruments = EconomicSet.create(Equivalence.IDENTITY);

    final long id = idGenerator.incrementAndGet();

    private final WeakReference<SandboxInstrument> instrumentWeakReference = new WeakReference<>(this);

    private final EconomicSet<WeakReference<? extends SandboxContext>> sandboxContexts = EconomicSet.create(Equivalence.IDENTITY);
    final ReferenceQueue<SandboxContext> sandboxContextReferenceQueue = new ReferenceQueue<>();

    volatile SandboxPauseExecutionRunnable pauseExecutionRunnable;
    @CompilationFinal volatile SandboxCheckerScheduler memoryCheckerScheduler;
    @CompilationFinal volatile SandboxCheckerScheduler timeCheckerScheduler;
    List<ContextPauseHandleWrapper> pausedSandboxContexts = new ArrayList<>();

    @CompilationFinal volatile Env environment;

    final ContextLocal<SandboxContext> sandboxContext = locals.createContextLocal(this::createContext);
    final ContextThreadLocal<SandboxThreadContext> sandboxThreadContext = locals.createContextThreadLocal(this::createThreadContext);

    @CompilationFinal long maxStatements = -1;
    @CompilationFinal Assumption sameStatementLimit;
    EventBinding<?> statementLimitBinding;
    EventBinding<?> stackFrameLimitBinding;
    EventBinding<?> astDepthLimitBinding;
    EventBinding<?> outputStreamBinding;
    EventBinding<?> errorStreamBinding;
    private boolean contextCleanupAttached;
    private boolean limitsBindingsAttached;
    final Assumption singleThreadPerContext = Truffle.getRuntime().createAssumption("Single thread per context.");
    final Assumption noThreadTimingNeeded = Truffle.getRuntime().createAssumption();
    final Assumption noThreadAllocationTrackingNeeded = Truffle.getRuntime().createAssumption();
    final Assumption noPriorityChangeNeeded = Truffle.getRuntime().createAssumption();
    final Assumption noThreadCountNeeded = Truffle.getRuntime().createAssumption();
    final Assumption noTracingNeeded = Truffle.getRuntime().createAssumption();
    volatile boolean memoryLimitedInstrument = false;

    final List<EventBinding<?>> bindings = new ArrayList<>();

    TruffleLogger logger;

    Boolean maxStatementsIncludeInternal;
    Integer astDepthLimit;

    @Override
    protected void onCreate(Env env) {
        this.environment = env;
        logger = env.getLogger((String) null);
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    protected void onFinalize(Env env) {
        synchronized (this) {
            synchronized (sandboxContexts) {
                for (Iterator<WeakReference<? extends SandboxContext>> sandboxContextRefIt = sandboxContexts.iterator(); sandboxContextRefIt.hasNext();) {
                    SandboxContext sandboxCtx = sandboxContextRefIt.next().get();
                    if (sandboxCtx != null) {
                        if (sandboxCtx.isTracingEnabled()) {
                            sandboxCtx.printLimits();
                        }
                    } else {
                        sandboxContextRefIt.remove();
                    }
                }
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    protected void onDispose(Env env) {
        List<JoinableThreadPoolExecutor> toShutDown = new ArrayList<>();
        synchronized (this) {
            synchronized (sandboxContexts) {
                for (Iterator<WeakReference<? extends SandboxContext>> sandboxContextRefIt = sandboxContexts.iterator(); sandboxContextRefIt.hasNext();) {
                    SandboxContext sandboxCtx = sandboxContextRefIt.next().get();
                    if (sandboxCtx != null) {
                        /*
                         * This guarantees that the currently running retained size computation or
                         * the computation that is about to be started will be cancelled. Since the
                         * corresponding context must be already closed at this point, it is
                         * guaranteed that no further retained size computation will be scheduled.
                         */
                        sandboxCtx.retainedSizeComputationCancelled.set(true);
                    } else {
                        sandboxContextRefIt.remove();
                    }
                }
            }
            for (EventBinding<?> binding : bindings) {
                binding.dispose();
            }
            bindings.clear();
            if (outputStreamBinding != null) {
                outputStreamBinding.dispose();
                outputStreamBinding = null;
            }
            if (errorStreamBinding != null) {
                errorStreamBinding.dispose();
                errorStreamBinding = null;
            }
            if (pauseExecutionRunnable != null) {
                pauseExecutionRunnable.setFinished();
                for (ContextPauseHandleWrapper pausedContext : pausedSandboxContexts) {
                    pausedContext.resume(0);
                }
                pausedSandboxContexts.clear();
                pauseExecutionRunnable = null;
            } else {
                assert pausedSandboxContexts.isEmpty();
            }

            environment = null;
            synchronized (SandboxLowMemoryListener.class) {
                int memoryLimitedInstrumentsCount = 0;
                synchronized (sandboxInstruments) {
                    sandboxInstruments.remove(instrumentWeakReference);
                    for (Iterator<WeakReference<SandboxInstrument>> sandboxInstrumentRefIt = sandboxInstruments.iterator(); sandboxInstrumentRefIt.hasNext();) {
                        WeakReference<SandboxInstrument> sandboxInstrumentRef = sandboxInstrumentRefIt.next();
                        SandboxInstrument sandboxInstrument = sandboxInstrumentRef.get();
                        if (sandboxInstrument == null || sandboxInstrumentRef == instrumentWeakReference) {
                            sandboxInstrumentRefIt.remove();
                        } else if (sandboxInstrument.memoryLimitedInstrument) {
                            memoryLimitedInstrumentsCount++;
                        }
                    }
                }
                if (memoryLimitedInstrumentsCount == 0) {
                    SandboxLowMemoryListener.uninstallLowMemoryListener(this);
                }
            }
            if (cancelExecutor != null) {
                toShutDown.add(cancelExecutor);
                cancelExecutor = null;
            }
            if (limitCheckerExecutor != null) {
                toShutDown.add(limitCheckerExecutor);
                limitCheckerExecutor = null;
            }
            if (pauseInstrumentExecutor != null) {
                toShutDown.add(pauseInstrumentExecutor);
                pauseInstrumentExecutor = null;
            }
            if (retainedSizeCheckerExecutor != null) {
                toShutDown.add(retainedSizeCheckerExecutor);
                retainedSizeCheckerExecutor = null;
            }
            disposed = true;
        }
        boolean interrupted = false;
        for (JoinableThreadPoolExecutor executorService : toShutDown) {
            executorService.shutdown();
            boolean terminated = false;
            while (!terminated) {
                try {
                    executorService.interrupt();
                    if (!executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Failed to terminate threads.");
                    }
                    terminated = true;
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected OptionDescriptors getContextOptionDescriptors() {
        return CONTEXT_OPTIONS;
    }

    private synchronized void lazyInitialize() {
        if (limitsBindingsAttached) {
            // already initialized
            return;
        }
        limitsBindingsAttached = true;

        bindings.add(environment.getInstrumenter().attachThreadsActivationListener(new SandboxActivationListener(SandboxInstrument.this)));
        bindings.add(environment.getInstrumenter().attachThreadsListener(new ThreadsListener() {
            @Override
            public void onThreadInitialized(TruffleContext context, Thread thread) {

            }

            @Override
            public void onThreadDisposed(TruffleContext context, Thread thread) {
                SandboxContext sandboxCtx = getSandboxContext(context);
                SandboxThreadContext threadContext = getThreadContext(context, thread);
                synchronized (sandboxCtx) {
                    sandboxCtx.removeThreadContext(threadContext);
                    sandboxCtx.removeCollectedThreads();
                }
            }
        }, false));
        bindings.add(environment.getInstrumenter().attachContextsListener(new ContextsListener() {

            private void pauseAllocationTracking(TruffleContext context) {
                boolean memoryTrackingNeeded = !noThreadAllocationTrackingNeeded.isValid();
                if (memoryTrackingNeeded) {
                    SandboxThreadContext threadContext = sandboxThreadContext.get(context);
                    assert Thread.currentThread() == threadContext.thread.get();
                    threadContext.pauseAllocationTracking();
                }
            }

            private void resumeAllocationTracking(TruffleContext context) {
                boolean memoryTrackingNeeded = !noThreadAllocationTrackingNeeded.isValid();
                if (memoryTrackingNeeded) {
                    SandboxThreadContext threadContext = sandboxThreadContext.get(context);
                    assert Thread.currentThread() == threadContext.thread.get();
                    threadContext.resumeAllocationTracking();
                }
            }

            @Override
            public void onLanguageContextInitialize(TruffleContext context, LanguageInfo language) {
                pauseAllocationTracking(context);
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                resumeAllocationTracking(context);
            }

            @Override
            public void onLanguageContextInitializeFailed(TruffleContext context, LanguageInfo language) {
                resumeAllocationTracking(context);
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextCreate(TruffleContext context, LanguageInfo language) {
                pauseAllocationTracking(context);
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                resumeAllocationTracking(context);
            }

            @Override
            public void onLanguageContextCreateFailed(TruffleContext context, LanguageInfo language) {
                resumeAllocationTracking(context);
            }

            @Override
            public void onContextCreated(TruffleContext context) {
            }

            @Override
            @TruffleBoundary
            public void onContextClosed(TruffleContext context) {
                SandboxContext sandboxCtx = getSandboxContext(context);
                sandboxCtx.retainedSizeComputationCancelled.set(true);
            }

            @Override
            public void onContextResetLimits(TruffleContext context) {
                sandboxContext.get(context).resetLimits();
            }
        }, false));

        synchronized (sandboxInstruments) {
            sandboxInstruments.add(instrumentWeakReference);
        }
    }

    private SandboxContext getSandboxContext(TruffleContext context) {
        SandboxContext c = this.sandboxContext.get(context);
        assert c != null;
        return c;
    }

    private SandboxThreadContext getThreadContext(TruffleContext context, Thread t) {
        assert environment != null;
        return this.sandboxThreadContext.get(context, t);
    }

    private SandboxThreadContext createThreadContext(TruffleContext context, Thread t) {
        assert environment != null;
        return this.sandboxContext.get(context).createThreadContext(t);
    }

    private SandboxContext createContext(TruffleContext context) {
        assert environment != null;
        TruffleContext parent = context.getParent();
        if (parent != null) {
            // inner contexts inherit limits from the root context
            while (parent.getParent() != null) {
                parent = parent.getParent();
            }
            return getSandboxContext(parent);
        } else {
            validateSandbox(context);
            SandboxContext sandbox = new SandboxContext(this, context);
            initialize(sandbox);
            sandbox.initialized = true;
            return sandbox;
        }
    }

    public static boolean isInterpreterCallStackHeadRoomSupported() {
        Runtime.Version jdkVersion = Runtime.version();
        return TruffleOptions.AOT && jdkVersion.feature() >= 23;
    }

    private void validateSandbox(TruffleContext context) {
        SandboxPolicy sandboxPolicy = environment.getSandboxPolicy();
        if (sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED)) {
            OptionValues optionValues = environment.getOptions(context);
            if (!optionValues.hasBeenSet(SandboxContext.MaxCPUTime)) {
                throw throwSandboxPolicyException(sandboxPolicy, "The sandbox.MaxCPUTime option is not set, but must be set.",
                                "set Builder.option(\"sandbox.MaxCPUTime\", \"<total maximum CPU time>\")");
            }
        }
        if (sandboxPolicy.isStricterOrEqual(SandboxPolicy.UNTRUSTED)) {
            OptionValues optionValues = environment.getOptions(context);
            if (!optionValues.hasBeenSet(SandboxContext.MaxHeapMemory)) {
                throw throwSandboxPolicyException(sandboxPolicy, "The sandbox.MaxHeapMemory option is not set, but must be set.",
                                "set Builder.option(\"sandbox.MaxHeapMemory\", \"<maximum heap memory>\")");
            }
            if (!optionValues.hasBeenSet(SandboxContext.MaxASTDepth)) {
                throw throwSandboxPolicyException(sandboxPolicy, "The sandbox.MaxASTDepth option is not set, but must be set.",
                                "set Builder.option(\"sandbox.MaxASTDepth\", \"<maximum AST depth>\")");
            }
            if (!optionValues.hasBeenSet(SandboxContext.MaxThreads)) {
                throw throwSandboxPolicyException(sandboxPolicy, "The sandbox.MaxThreads option is not set, but must be set.",
                                "set Builder.option(\"sandbox.MaxThreads\", \"<maximum number of threads>\")");
            }
            if (!optionValues.hasBeenSet(SandboxContext.MaxOutputStreamSize)) {
                throw throwSandboxPolicyException(sandboxPolicy, "The sandbox.MaxOutputStreamSize option is not set, but must be set.",
                                "set Builder.option(\"sandbox.MaxOutputStreamSize\", \"<maximum output size>\")");
            }
            if (!optionValues.hasBeenSet(SandboxContext.MaxErrorStreamSize)) {
                throw throwSandboxPolicyException(sandboxPolicy, "The sandbox.MaxErrorStreamSize option is not set, but must be set.",
                                "set Builder.option(\"sandbox.MaxErrorStreamSize\", \"<maximum error output size>\")");
            }
        }
    }

    private static RuntimeException throwSandboxPolicyException(SandboxPolicy sandboxPolicy, String reason, String fix) {
        Objects.requireNonNull(sandboxPolicy);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(fix);
        String message = String.format("The validation for the given sandbox policy %s failed. %s " +
                        "In order to resolve this %s or switch to a less strict sandbox policy using Builder.sandbox(SandboxPolicy).%n" +
                        "You can use Builder.option(\"sandbox.TraceLimits\", \"true\") to estimate an application's optimal sandbox parameters.", sandboxPolicy, reason, fix);
        throw new SandboxException(message, null);
    }

    private synchronized void initialize(SandboxContext context) {
        if (context.hasStatementLimit()) {
            lazyInitialize();

            Assumption sameLimit = this.sameStatementLimit;
            if (sameLimit != null && sameLimit.isValid() && context.maxStatements != this.maxStatements) {
                sameLimit.invalidate();
            } else if (sameLimit == null) {
                this.sameStatementLimit = Truffle.getRuntime().createAssumption("Same statement limit.");
                this.maxStatements = context.maxStatements;
            }

            if (statementLimitBinding == null) {
                Instrumenter instrumenter = environment.getInstrumenter();
                SourceSectionFilter.Builder filter = SourceSectionFilter.newBuilder().tagIs(StatementTag.class);
                filter.includeInternal(context.maxStatementsIncludeInternal);

                statementLimitBinding = instrumenter.attachExecutionEventFactory(filter.build(), new ExecutionEventNodeFactory() {
                    @Override
                    public ExecutionEventNode create(EventContext eventContext) {
                        return new StatementIncrementNode(eventContext, SandboxInstrument.this);
                    }
                });
            }
        }
        if (context.hasCPULimit()) {
            lazyInitialize();
            long timeLimitMillis = context.cpuTimeLimit.toMillis();
            assert timeLimitMillis >= 0; // needs to verified before
            if (timeCheckerScheduler == null) {
                timeCheckerScheduler = new SandboxCheckerScheduler(this, "time");
            }
            SandboxTimeLimitChecker task = new SandboxTimeLimitChecker(timeCheckerScheduler, context);
            context.timeLimitChecker = task;
            noThreadTimingNeeded.invalidate();
            noPriorityChangeNeeded.invalidate();
            timeCheckerScheduler.scheduleChecker(task);
        }

        if (context.hasMemoryLimit() || context.isTracingEnabled()) {
            lazyInitialize();
            if (context.hasMemoryLimit() && context.lowMemoryTriggerEnabled && pauseExecutionRunnable == null) {
                submitInPauseInstrumentExecutor(pauseExecutionRunnable = new SandboxPauseExecutionRunnable(this));
            }
            memoryLimitedInstrument = true;
            SandboxLowMemoryListener listener = context.hasMemoryLimit() && context.lowMemoryTriggerEnabled
                            ? SandboxLowMemoryListener.installLowMemoryListener(context.retainedBytesCheckFactor, context.reuseLowMemoryTriggerThreshold)
                            : null;
            if (context.allocatedBytesCheckEnabled || context.isTracingEnabled()) {
                if (memoryCheckerScheduler == null) {
                    memoryCheckerScheduler = new SandboxCheckerScheduler(this, "memory");
                }
                SandboxMemoryLimitChecker task = new SandboxMemoryLimitChecker(memoryCheckerScheduler, context, this, listener);
                context.memoryLimitChecker = task;
                noPriorityChangeNeeded.invalidate();
                noThreadAllocationTrackingNeeded.invalidate();
                memoryCheckerScheduler.scheduleChecker(task);
            }

            if (listener != null && listener.stopTheWorld) {
                pauseSandboxContext(pauseExecutionRunnable, context, false);
                /*
                 * Here the context is not running any threads yet, so we assume that the pause just
                 * works without actually waiting for it using
                 * ContextPauseHandleWrapper#waitTillPaused.
                 */
                SandboxInstrument.log(this, context, "Execution paused for context.");

            }
        }
        if (context.hasStackFrameLimit()) {
            if (stackFrameLimitBinding == null) {
                Instrumenter instrumenter = environment.getInstrumenter();
                SourceSectionFilter.Builder filter = SourceSectionFilter.newBuilder().tagIs(RootTag.class);
                stackFrameLimitBinding = instrumenter.attachExecutionEventFactory(filter.build(), new ExecutionEventNodeFactory() {
                    @Override
                    public ExecutionEventNode create(EventContext eventContext) {
                        return new FrameCounterNode(eventContext, SandboxInstrument.this);
                    }
                });
            }
        }
        if (context.hasActiveThreadsLimit()) {
            noThreadCountNeeded.invalidate();
            lazyInitialize();
        }
        if (context.hasASTDepthLimit()) {
            if (astDepthLimitBinding == null) {
                Instrumenter instrumenter = environment.getInstrumenter();
                SourceSectionFilter.Builder filter = SourceSectionFilter.newBuilder().tagIs(RootTag.class);
                astDepthLimitBinding = instrumenter.attachExecutionEventFactory(filter.build(), new ExecutionEventNodeFactory() {
                    @Override
                    public ExecutionEventNode create(EventContext eventContext) {
                        checkAstDepth(eventContext, SandboxInstrument.this.sandboxContext.get());
                        return null;
                    }
                });
            }
        }
        if (context.hasOutputStreamLimit()) {
            if (outputStreamBinding == null) {
                Instrumenter instrumenter = environment.getInstrumenter();
                outputStreamBinding = instrumenter.attachOutConsumer(new LimitOutputStream.LimitedStandardOuputStream(this));
            }
        }
        if (context.hasErrorStreamLimit()) {
            if (errorStreamBinding == null) {
                Instrumenter instrumenter = environment.getInstrumenter();
                errorStreamBinding = instrumenter.attachErrConsumer(new LimitOutputStream.LimitedStandardErrorStream(this));
            }
        }

        context.resetLimits();

        synchronized (sandboxContexts) {
            /*
             * Clean references to garbage-collected unclosed contexts from the sandboxContexts set.
             * Typically, context references are removed through the
             * ContextsListener#onContextClosed method. However, we want to clean references to
             * contexts that the embedder may have neglected to close as well.
             */
            WeakReference<? extends SandboxContext> ref;
            while ((ref = (WeakReference<? extends SandboxContext>) sandboxContextReferenceQueue.poll()) != null) {
                sandboxContexts.remove(ref);
            }
            sandboxContexts.add(context.contextWeakReference);
        }
        if (!contextCleanupAttached) {
            contextCleanupAttached = true;
            bindings.add(environment.getInstrumenter().attachContextsListener(new ContextsListener() {

                @Override
                public void onContextClosed(TruffleContext truffleContext) {
                    SandboxContext sandboxCtx = getSandboxContext(truffleContext);
                    if (sandboxCtx.isTracingEnabled()) {
                        sandboxCtx.printLimits();
                    }
                    synchronized (sandboxContexts) {
                        sandboxContexts.remove(sandboxCtx.contextWeakReference);
                    }
                }

                @Override
                public void onContextCreated(TruffleContext truffleContext) {
                }

                @Override
                public void onLanguageContextCreated(TruffleContext truffleContext, LanguageInfo language) {
                }

                @Override
                public void onLanguageContextInitialized(TruffleContext truffleContext, LanguageInfo language) {
                }

                @Override
                public void onLanguageContextFinalized(TruffleContext truffleContext, LanguageInfo language) {
                }

                @Override
                public void onLanguageContextDisposed(TruffleContext truffleContext, LanguageInfo language) {
                }
            }, false));
        }
    }

    void pauseSandboxContext(SandboxPauseExecutionRunnable pauseRunnable, SandboxContext sandboxCtx, boolean cancelRetainedSizeComputation) {
        Future<Void> pauseHandle = sandboxCtx.getTruffleContext().pause();
        if (cancelRetainedSizeComputation) {
            sandboxCtx.retainedSizeComputationCancelled.set(true);
        }
        synchronized (this) {
            if (!pauseRunnable.isFinished()) {
                pausedSandboxContexts.add(new ContextPauseHandleWrapper(this, sandboxCtx, pauseHandle));
            } else {
                sandboxCtx.getTruffleContext().resume(pauseHandle);
            }
        }
    }

    static class DepthVisitor implements NodeVisitor {
        int maxDepth = 0;
        int currentDepth = 1;

        @Override
        public boolean visit(Node node) {
            boolean instrumentable = isInstrumentableNode(node);
            if (instrumentable) {
                currentDepth++;
                this.maxDepth = Math.max(maxDepth, currentDepth);
            }
            NodeUtil.forEachChild(node, this);
            if (instrumentable) {
                currentDepth--;
            }
            return true;
        }

        private static boolean isInstrumentableNode(Node node) {
            if (node instanceof InstrumentableNode.WrapperNode) {
                return false;
            }
            if (node instanceof InstrumentableNode) {
                return ((InstrumentableNode) node).isInstrumentable();
            } else {
                return false;
            }
        }
    }

    private static void checkAstDepth(EventContext eventContext, SandboxContext context) {
        if (context.hasASTDepthLimit()) {
            RootNode rn = eventContext.getInstrumentedNode().getRootNode();
            if (rn == null) {
                throw new AssertionError("No root node found");
            }

            DepthVisitor visitor = new DepthVisitor();
            NodeUtil.forEachChild(rn, visitor);
            int depth = visitor.maxDepth;

            if (context.isTracingEnabled()) {
                context.maxAstDepthTraced = Math.max(context.maxAstDepthTraced, depth);
            }
            if (depth > context.astDepthLimit) {
                String message = String.format("AST depth limit of %s nodes exceeded. AST depth: %s.",
                                context.astDepthLimit, depth);
                submitOrPerformCancel(context, eventContext.getInstrumentedNode(), message);
            }
        }
    }

    Future<SandboxMemoryLimitRetainedSizeChecker.Result> submitInRetainedSizeCheckerExecutor(Callable<SandboxMemoryLimitRetainedSizeChecker.Result> callable) {
        JoinableThreadPoolExecutor executor = retainedSizeCheckerExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = retainedSizeCheckerExecutor;
                if (executor == null && !disposed) {
                    executor = new JoinableThreadPoolExecutor(new HighPriorityThreadFactory(environment, "Sandbox [" + id + "] Retained Size Computation Thread"));
                    executor.setKeepAliveTime(1, TimeUnit.SECONDS);
                    retainedSizeCheckerExecutor = executor;
                }
            }
        }
        return executor != null ? executor.submit(callable) : null;
    }

    private void submitInPauseInstrumentExecutor(Runnable runnable) {
        JoinableThreadPoolExecutor executor = pauseInstrumentExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = pauseInstrumentExecutor;
                if (executor == null && !disposed) {
                    executor = new JoinableThreadPoolExecutor(new HighPriorityThreadFactory(environment, "Sandbox [" + id + "] Pause Instrument Thread"));
                    executor.setKeepAliveTime(1, TimeUnit.SECONDS);
                    pauseInstrumentExecutor = executor;
                }
            }
        }
        if (executor != null) {
            executor.submit(runnable);
        }
    }

    void submitInLimitCheckerExecutor(Runnable runnable) {
        JoinableThreadPoolExecutor executor = limitCheckerExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = limitCheckerExecutor;
                if (executor == null && !disposed) {
                    executor = new JoinableThreadPoolExecutor(new HighPriorityThreadFactory(environment, "Sandbox [" + id + "] Limit Checker Thread"));
                    executor.setKeepAliveTime(1, TimeUnit.SECONDS);
                    limitCheckerExecutor = executor;
                }
            }
        }
        if (executor != null) {
            executor.submit(runnable);
        }
    }

    private Future<?> submitInCancelExecutor(Runnable runnable) {
        JoinableThreadPoolExecutor executor = cancelExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = cancelExecutor;
                if (executor == null && !disposed) {
                    executor = new JoinableThreadPoolExecutor(new HighPriorityThreadFactory(environment, "Sandbox [" + id + "] Cancel Thread"));
                    executor.setKeepAliveTime(10, TimeUnit.SECONDS);
                    cancelExecutor = executor;
                }
            }
        }
        return executor != null ? executor.submit(runnable) : null;
    }

    static FutureTask<?> submitOrPerformCancel(SandboxContext c, Node location, String message) {
        TruffleContext context = c.getTruffleContext();
        if (context.isEntered()) {
            context.closeResourceExhausted(location, message);
            return null;
        } else {
            return submitCancel(c, message);
        }
    }

    static FutureTask<?> submitCancel(SandboxContext c, String message) {
        return (FutureTask<?>) c.getInstrument().submitInCancelExecutor(new Runnable() {
            @Override
            public void run() {
                TruffleContext context = c.getTruffleContext();
                if (!context.isClosed()) {
                    assert !context.isActive() : "context must not be active on the thread we submit the cancel";
                    context.closeResourceExhausted(null, message);
                }
            }
        });
    }

    private abstract static class SandboxContextNode extends ExecutionEventNode {
        final SandboxInstrument limits;
        final EventContext eventContext;

        SandboxContextNode(EventContext context, SandboxInstrument limits) {
            this.limits = limits;
            this.eventContext = context;
        }

        protected SandboxContext getLimitContext() {
            return limits.sandboxContext.get();
        }
    }

    private static final class StatementIncrementNode extends SandboxContextNode {
        @CompilationFinal private boolean seenOverflow;

        StatementIncrementNode(EventContext context, SandboxInstrument limits) {
            super(context, limits);
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            SandboxContext currentContext = getLimitContext();

            long count;
            if (limits.singleThreadPerContext.isValid()) {
                count = --currentContext.statementCounter;
            } else {
                count = currentContext.volatileStatementCounter.decrementAndGet();
            }
            if (count < 0) { // overflowed
                /*
                 * The following if statement could be replaced by just
                 * CompilerDirectives.transferToInterpreterAndInvalidate(), but that would cause a
                 * deoptimization loop if the same code hit the statement limit at the same spot
                 * repeatedly.
                 */
                if (!seenOverflow) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenOverflow = true;
                } else {
                    CompilerDirectives.transferToInterpreter();
                }
                notifyStatementLimitReached(currentContext, currentContext.maxStatements - count, currentContext.maxStatements);
            }
        }

        private void notifyStatementLimitReached(SandboxContext context, long actualCount, long limit) {
            boolean limitReached = false;
            synchronized (context) {
                // reset statement counter
                if (limits.singleThreadPerContext.isValid()) {
                    if (context.statementCounter < 0) {
                        context.statementCounter = limit;
                        limitReached = true;
                    }
                } else {
                    if (context.volatileStatementCounter.get() < 0) {
                        context.volatileStatementCounter.set(limit);
                        limitReached = true;
                    }
                }
            }
            if (limitReached) {
                String message = String.format("Maximum statement limit of %s exceeded. Statements executed %s.",
                                limit, actualCount);
                submitOrPerformCancel(context, eventContext.getInstrumentedNode(), message);
            }
        }
    }

    private static final class FrameCounterNode extends SandboxContextNode {

        FrameCounterNode(EventContext context, SandboxInstrument limits) {
            super(context, limits);
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            int count = --limits.sandboxThreadContext.get().frameCounter;
            boolean tracingEnabled = !limits.noTracingNeeded.isValid();
            if (tracingEnabled) {
                SandboxContext context = getLimitContext();
                if (context.isTracingEnabled()) {
                    context.minStackFramesTraced = Math.min(context.minStackFramesTraced, count);
                }
            }
            if (count < 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SandboxContext currentContext = getLimitContext();
                String message = String.format("Maximum frame limit of %s exceeded. Frames on stack: %s.",
                                currentContext.stackFrameLimit, currentContext.stackFrameLimit - count);
                submitOrPerformCancel(currentContext, eventContext.getInstrumentedNode(), message);
            }
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            limits.sandboxThreadContext.get().frameCounter++;
        }

        @Override
        protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            limits.sandboxThreadContext.get().frameCounter++;
        }
    }

    static void logToAllInstruments(String message, Object... arguments) {
        logToAllInstruments(Level.FINE, message, null, arguments);
    }

    static void logAlwaysToAllInstruments(String message, Throwable e, Object... arguments) {
        logToAllInstruments(Level.WARNING, message, e, arguments);
    }

    static void logToAllInstruments(Level logLevel, String message, Throwable e, Object... arguments) {
        List<SandboxInstrument> memoryLimitedInstruments = new LinkedList<>();
        synchronized (sandboxInstruments) {
            for (Iterator<WeakReference<SandboxInstrument>> sandboxInstrumentRefIt = sandboxInstruments.iterator(); sandboxInstrumentRefIt.hasNext();) {
                SandboxInstrument sandboxInstrument = sandboxInstrumentRefIt.next().get();
                if (sandboxInstrument != null) {
                    if (sandboxInstrument.memoryLimitedInstrument) {
                        memoryLimitedInstruments.add(sandboxInstrument);
                    }
                } else {
                    sandboxInstrumentRefIt.remove();
                }
            }
        }
        for (SandboxInstrument sandboxInstrument : memoryLimitedInstruments) {
            logBoundary(logLevel, sandboxInstrument, null, message, e, arguments);
        }
    }

    static void logAlways(SandboxInstrument instrument, String message, Throwable e, Object... arguments) {
        logBoundary(Level.WARNING, instrument, null, message, e, arguments);
    }

    static void logAlways(SandboxInstrument instrument, SandboxContext c, String message, Throwable e, Object... arguments) {
        logBoundary(Level.WARNING, instrument, c, message, e, arguments);
    }

    static void log(SandboxInstrument instrument, String message, Object... arguments) {
        logBoundary(Level.FINE, instrument, null, message, null, arguments);
    }

    static void log(SandboxInstrument instrument, SandboxContext c, String message, Object... arguments) {
        logBoundary(Level.FINE, instrument, c, message, null, arguments);
    }

    @SuppressWarnings("deprecation")
    @CompilerDirectives.TruffleBoundary
    static void logBoundary(Level logLevel, SandboxInstrument instrument, SandboxContext c, String message, Throwable e, Object... arguments) {
        if (instrument.logger.isLoggable(logLevel)) {
            String prefix = c != null ? String.format("[thread-%d] [instrument-%d] [context-%d] ", getCurrentThreadId(), instrument.id, c.id)
                            : String.format("[thread-%d] [instrument-%d] ", getCurrentThreadId(), instrument.id);
            instrument.logger.log(logLevel, prefix + String.format(message, arguments), e);
        }
    }

    @SuppressWarnings("deprecation")
    static long getThreadId(Thread thread) {
        return thread.getId();
    }

    static long getCurrentThreadId() {
        return getThreadId(Thread.currentThread());
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    static List<SandboxInstrument> getMemoryLimitedSandboxInstruments(boolean pause, long lowMemoryTriggerNumber) {
        List<SandboxInstrument> memoryLimitedInstruments = new ArrayList<>();
        synchronized (sandboxInstruments) {
            for (Iterator<WeakReference<SandboxInstrument>> sandboxInstrumentRefIt = sandboxInstruments.iterator(); sandboxInstrumentRefIt.hasNext();) {
                SandboxInstrument sandboxInstrument = sandboxInstrumentRefIt.next().get();
                if (sandboxInstrument != null) {
                    if (sandboxInstrument.memoryLimitedInstrument) {
                        memoryLimitedInstruments.add(sandboxInstrument);
                    }
                } else {
                    sandboxInstrumentRefIt.remove();
                }
            }
        }
        for (SandboxInstrument sandboxInstrument : memoryLimitedInstruments) {
            synchronized (sandboxInstrument) {
                SandboxPauseExecutionRunnable pauseExecutionRunnable = sandboxInstrument.pauseExecutionRunnable;
                if (pauseExecutionRunnable != null) {
                    if (pause) {
                        pauseExecutionRunnable.initiatePauseAndResume(lowMemoryTriggerNumber);
                    } else {
                        pauseExecutionRunnable.initiateResume(lowMemoryTriggerNumber);
                    }
                }
            }
        }
        return memoryLimitedInstruments;
    }

    synchronized List<SandboxContext> getMemoryLimitedSandboxContexts() {
        /*
         * This method must be synchronized because it needs to wait for sandbox context
         * initialization to finish. We still need to synchronize on sandboxContexts as well,
         * because we cannot synchronize on the instrument during onContextClosed notification,
         * where the sandboxContexts list needs to be updated.
         */
        List<SandboxContext> memoryLimitedContexts = new ArrayList<>();
        synchronized (sandboxContexts) {
            for (Iterator<WeakReference<? extends SandboxContext>> sandboxContextRefIt = sandboxContexts.iterator(); sandboxContextRefIt.hasNext();) {
                SandboxContext sandboxCtx = sandboxContextRefIt.next().get();
                if (sandboxCtx != null) {
                    if (sandboxCtx.hasMemoryLimit()) {
                        memoryLimitedContexts.add(sandboxCtx);
                    }
                } else {
                    sandboxContextRefIt.remove();
                }
            }
        }
        return memoryLimitedContexts;
    }

    private abstract static class LimitOutputStream extends OutputStream {
        private final SandboxInstrument instrument;

        private LimitOutputStream(SandboxInstrument instrument) {
            this.instrument = instrument;
        }

        abstract long getLimit(SandboxContext currentContext);

        abstract AtomicLong getCounter(SandboxContext currentContext);

        abstract String formatErrorMessage(long actualCount, long limit);

        @Override
        public final void write(int b) {
            incrementAndValidateSize(1);
        }

        @Override
        public final void write(byte[] b, int off, int len) {
            incrementAndValidateSize(len);
        }

        private void incrementAndValidateSize(int size) {
            SandboxContext currentContext = instrument.sandboxContext.get();
            AtomicLong counter = getCounter(currentContext);
            if (counter != null) {
                long count = counter.addAndGet(-size);
                if (count < 0) { // overflowed
                    long limit = getLimit(currentContext);
                    submitOrPerformCancel(currentContext, null, formatErrorMessage(limit - count, limit));
                }
            }
        }

        static final class LimitedStandardOuputStream extends LimitOutputStream {

            LimitedStandardOuputStream(SandboxInstrument instrument) {
                super(instrument);
            }

            @Override
            long getLimit(SandboxContext currentContext) {
                return currentContext.outputStreamLimit;
            }

            @Override
            AtomicLong getCounter(SandboxContext currentContext) {
                return currentContext.hasOutputStreamLimit() ? currentContext.volatileOutputSizeCounter : null;
            }

            @Override
            String formatErrorMessage(long actualCount, long limit) {
                return String.format("Maximum output stream size of %d exceeded. Bytes written %d.",
                                limit, actualCount);
            }
        }

        static final class LimitedStandardErrorStream extends LimitOutputStream {

            LimitedStandardErrorStream(SandboxInstrument instrument) {
                super(instrument);
            }

            @Override
            long getLimit(SandboxContext currentContext) {
                return currentContext.errorStreamLimit;
            }

            @Override
            AtomicLong getCounter(SandboxContext currentContext) {
                return currentContext.hasErrorStreamLimit() ? currentContext.volatileErrorSizeCounter : null;
            }

            @Override
            String formatErrorMessage(long actualCount, long limit) {
                return String.format("Maximum error stream size of %d exceeded. Bytes written %d.",
                                limit, actualCount);
            }
        }
    }
}
