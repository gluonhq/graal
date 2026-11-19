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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Phaser;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

final class SandboxMemoryLimitRetainedSizeChecker implements Callable<SandboxMemoryLimitRetainedSizeChecker.Result> {

    private static final String MEMORY_LIMIT_CHECKER_INVOCATION = "memory-limit-checker-invocation";
    private static final String LOW_MEMORY_TRIGGER = "low-memory-trigger";

    private final SandboxContext context;
    private final long memoryLimitCheckerInvocationNumber; // for logging only

    SandboxMemoryLimitRetainedSizeChecker(SandboxContext context, long memoryLimitCheckerInvocationNumber) {
        this.context = context;
        this.memoryLimitCheckerInvocationNumber = memoryLimitCheckerInvocationNumber;
    }

    @Override
    public Result call() {
        try {
            return computeContextRetainedSizeAndCancelIfNeeded(context, MEMORY_LIMIT_CHECKER_INVOCATION, memoryLimitCheckerInvocationNumber);
        } catch (Throwable e) {
            SandboxInstrument.logAlways(context.getInstrument(), "[%s-%d] Retained size computation failed!", e, MEMORY_LIMIT_CHECKER_INVOCATION, memoryLimitCheckerInvocationNumber);
            throw e;
        }
    }

    static void computeAllContextsRetainedSize(SandboxInstrument instrument, long triggerNumber) {
        List<SandboxContext> contextList = new ArrayList<>();
        Phaser phaser = SandboxLowMemoryListener.lowMemoryListener.phaser;
        try {
            /*
             * The following populate method does not return the complete list but rather gradually
             * populates the supplied list. The reason is that in case of error we need to perform
             * certain operations for the contexts that were already added to the list in the
             * finally block.
             */
            populateContextList(instrument, contextList, triggerNumber);
            for (SandboxContext c : contextList) {
                do {
                    /*
                     * Synchronization place #2 out of 3
                     *
                     * The goal is to coordinate retained size computation in all memory-limited
                     * contexts in serial fashion and when all is done, unstop the world. One of the
                     * other synchronization places is in SandboxWorldUnstopper and the remaining
                     * one is in the finally block of this try-catch-finally construct.
                     * Registrations to the phaser are done in
                     * SandboxPauseExecutionRunnable#initiatePauseAndResume.
                     * 
                     * The coordinated retained size computations are done by repeating the
                     * following three phases.
                     *
                     *
                     * Phase 1: Wait for all participating threads to arrive at the first
                     * synchronization point. This ensures that the global smallestToLimit variable
                     * is not cleared prematurely by some thread. The clearing happens in the next
                     * phase.
                     */
                    phaser.arriveAndAwaitAdvance();
                    /*
                     * Phase 2: Clear the global smallestToLimit variable and wait for all
                     * participating threads to arrive at the second synchronization point.
                     */
                    SandboxLowMemoryListener.lowMemoryListener.clearSmallestToLimit();
                    phaser.arriveAndAwaitAdvance();
                    /*
                     * Phase 3: Update the global smallestToLimit variable with bytesToLimit for the
                     * current context in this thread, if the bytesToLimit for the current context
                     * is smaller than the global variable. Wait for all participating threads to do
                     * arrive at the third synchronization point. When this happens, it means that
                     * all threads had a chance to perform the update. The thread with the current
                     * context with the smallest bytesToLimit value is then allowed to proceed and
                     * the whole process repeats. The context with the smallest bytesToLimit is the
                     * first one to proceed, because it is assumed that it has the highest
                     * probability that it is actually over the limit and so it will be cancelled
                     * and its memory freed alleviating the low memory situation.
                     */
                    long bytesToLimit = getBytesToLimit(c);
                    SandboxLowMemoryListener.lowMemoryListener.updateSmallestToLimit(instrument.id, bytesToLimit);
                    phaser.arriveAndAwaitAdvance();
                } while (instrument.id != SandboxLowMemoryListener.lowMemoryListener.getPhaseInstrumentId());

                computeContextRetainedSizeAndCancelIfNeeded(c, LOW_MEMORY_TRIGGER, triggerNumber);
            }
        } catch (Throwable e) {
            SandboxInstrument.logAlways(instrument, "[%s-%d] Retained size computation failed!", e, LOW_MEMORY_TRIGGER, triggerNumber);
        } finally {
            do {
                /*
                 * Synchronization place #3 out of 3
                 *
                 * The goal is to coordinate retained size computation in all memory-limited
                 * contexts in serial fashion and when all is done, unstop the world. One of the
                 * other synchronization places is in SandboxWorldUnstopper and the remaining one is
                 * in the sandbox context loop in the try block of this try-catch-finally construct.
                 * Registrations to the phaser are done in
                 * SandboxPauseExecutionRunnable#initiatePauseAndResume. This finally block is not
                 * finished until all retained size computation threads reach it. It doesn't do any
                 * updates to the smallestToLimit global variable. It just waits until there are no
                 * threads to perform further retained size computations.
                 */
                phaser.arriveAndAwaitAdvance();
                SandboxLowMemoryListener.lowMemoryListener.clearSmallestToLimit();
                phaser.arriveAndAwaitAdvance();
                phaser.arriveAndAwaitAdvance();
            } while (SandboxLowMemoryListener.lowMemoryListener.getPhaseInstrumentId() != -1);
            phaser.arriveAndDeregister();
            if (instrument.memoryCheckerScheduler != null) {
                instrument.memoryCheckerScheduler.resume();
            }
        }
    }

    private static long getBytesToLimit(SandboxContext c) {
        long bytesToLimit = c.heapMemoryLimit;
        if (c.lastRetainedBytes != 0) {
            bytesToLimit -= c.lastRetainedBytes;
        } else if (c.memoryLimitChecker != null) {
            bytesToLimit -= c.memoryLimitChecker.lastRetainedBytes + c.memoryLimitChecker.lastAllocatedBytes -
                            c.memoryLimitChecker.lastRetainedSizeComputationFinishedRequestAllocatedBytes;
        }
        return bytesToLimit;
    }

    private static Result computeContextRetainedSizeAndCancelIfNeeded(SandboxContext c, String triggerName, long triggerNumber) {
        SandboxInstrument instrument = c.getInstrument();
        boolean contextInvalid = false;
        long retainedSize = -1L;

        long memoryLimit = c.heapMemoryLimit;
        if (!c.isClosed()) {
            try {
                SandboxInstrument.log(c.getInstrument(), c, "[%s-%d] Retained size computation initiated for context.", triggerName, triggerNumber);

                long startTime = System.currentTimeMillis();
                TruffleInstrument.Env instrumentEnv = instrument.environment;
                String cancellationMessage = null;
                if (instrumentEnv != null) {
                    try {
                        long stopAtBytes = c.isTracingEnabled() ? Long.MAX_VALUE : memoryLimit;
                        retainedSize = instrumentEnv.calculateContextHeapSize(c.getTruffleContext(), stopAtBytes, c.retainedSizeComputationCancelled);
                        c.lastRetainedBytes = retainedSize;
                    } catch (CancellationException e) {
                        cancellationMessage = e.getMessage();
                    } catch (UnsupportedOperationException e) {
                        cancellationMessage = "retained size computation is not supported on current Truffle runtime";
                    } finally {
                        c.retainedSizeComputationCancelled.set(false);
                    }
                } else {
                    contextInvalid = true;
                }
                long endTime = System.currentTimeMillis();

                if (cancellationMessage != null) {
                    SandboxInstrument.log(c.getInstrument(), c, "[%s-%d] Retained size computation cancelled for context after %dms. Cancellation message: %s.",
                                    triggerName, triggerNumber, (endTime - startTime), cancellationMessage);
                } else {
                    SandboxInstrument.log(c.getInstrument(), c, "[%s-%d] Retained size of context is %d bytes, computation took %dms.", triggerName, triggerNumber, retainedSize,
                                    (endTime - startTime));
                }
                if (c.isTracingEnabled()) {
                    c.maxHeapMemoryTraced = Math.max(c.maxHeapMemoryTraced, retainedSize);
                }
                if (c.hasMemoryLimit() && retainedSize > memoryLimit) {
                    contextInvalid = true;
                    if (!c.isClosed()) {
                        SandboxInstrument.log(c.getInstrument(), c, "[%s-%d] Retained size checker initiated cancel for context.", triggerName, triggerNumber);
                        startTime = System.currentTimeMillis();
                        TruffleContext truffleContext = c.getTruffleContext();
                        if (!truffleContext.isClosed()) {
                            String message = String.format("Maximum heap memory limit of %s bytes exceeded. Current memory at least %s bytes.",
                                            memoryLimit,
                                            retainedSize);
                            truffleContext.closeResourceExhausted(null, message);
                        }
                        endTime = System.currentTimeMillis();
                        SandboxInstrument.log(c.getInstrument(), c, "[%s-%d] Context cancelled in  %dms.", triggerName, triggerNumber, endTime - startTime);
                    }
                }
            } catch (Throwable e) {
                SandboxInstrument.logAlways(c.getInstrument(), c, "[%s-%d] Retained size computation failed for context!", e, triggerName, triggerNumber);
                throw e;
            }
        } else {
            contextInvalid = true;
        }

        return new Result(retainedSize, contextInvalid);
    }

    private static void populateContextList(SandboxInstrument instrument, List<SandboxContext> contextList, long triggerNumber) {
        if (instrument.memoryCheckerScheduler != null) {
            instrument.memoryCheckerScheduler.pause();
        }
        for (SandboxContext ctx : instrument.getMemoryLimitedSandboxContexts()) {
            boolean exclude = false;
            if (ctx.memoryLimitChecker != null && ctx.memoryLimitChecker.retainedSizeComputationRequested) {
                while (!ctx.memoryLimitChecker.retainedSizeComputationResultFuture.isDone()) {
                    ctx.retainedSizeComputationCancelled.set(true);
                    try {
                        Result result = ctx.memoryLimitChecker.retainedSizeComputationResultFuture.get();
                        if (result.contextInvalid) {
                            exclude = true;
                        }
                    } catch (ExecutionException e) {
                        SandboxInstrument.logAlways(ctx.getInstrument(), ctx, "[%s-%d] Scheduled retained size computation threw an exception for context", e,
                                        LOW_MEMORY_TRIGGER, triggerNumber);
                    } catch (InterruptedException | CancellationException ignored) {
                    }
                }
            } else {
                SandboxInstrument.log(ctx.getInstrument(), ctx, "[%s-%d] Scheduled retained size computation not currently running for context.", LOW_MEMORY_TRIGGER, triggerNumber);
            }
            if (!exclude) {
                /*
                 * We could have set cancelled just after the retained size computation set it to
                 * false. We must make sure cancelled is false before the impending retained size
                 * computation.
                 */
                ctx.retainedSizeComputationCancelled.set(false);
                contextList.add(ctx);
                SandboxInstrument.log(ctx.getInstrument(), ctx, "[%s-%d] Retained size computation requested on low memory trigger for context.", LOW_MEMORY_TRIGGER, triggerNumber);
            } else {
                SandboxInstrument.log(ctx.getInstrument(), ctx, "[%s-%d] Retained size computation on low memory trigger not necessary, context already invalidated.",
                                LOW_MEMORY_TRIGGER, triggerNumber);
            }
        }
        // Contexts with the least bytes to limit first.
        contextList.sort((c1, c2) -> {
            long bytesToLimit1 = getBytesToLimit(c1);
            long bytesToLimit2 = getBytesToLimit(c2);
            return Long.compare(bytesToLimit1, bytesToLimit2);
        });
    }

    static final class Result {
        final long retainedBytes;
        final boolean contextInvalid;

        Result(long retainedBytes, boolean contextInvalid) {
            this.retainedBytes = retainedBytes;
            this.contextInvalid = contextInvalid;
        }
    }
}
