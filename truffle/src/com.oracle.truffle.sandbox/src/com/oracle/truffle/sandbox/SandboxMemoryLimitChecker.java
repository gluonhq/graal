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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.CompilerAsserts;

final class SandboxMemoryLimitChecker extends SandboxCheckerScheduler.SandboxChecker {

    private final SandboxInstrument instrument;
    private final SandboxLowMemoryListener lowMemoryListener;
    private final long memoryLimit;
    private final double allocatedBytesCheckFactor;
    private final long retainedBytesCheckInterval;
    volatile long lastAllocatedBytes;
    volatile long lastRetainedBytes;
    volatile long lastRetainedSizeComputationFinishedRequestAllocatedBytes;
    private long previousRetainedSizeComputationFinishedRequestAllocatedBytes;
    private long lastRetainedSizeComputationRequestAllocatedBytes;
    volatile boolean retainedSizeComputationRequested;
    private long lastRetainedSizeComputationRequestTime;

    volatile Future<SandboxMemoryLimitRetainedSizeChecker.Result> retainedSizeComputationResultFuture;

    SandboxMemoryLimitChecker(SandboxCheckerScheduler scheduler, SandboxContext context, SandboxInstrument instrument, SandboxLowMemoryListener lowMemoryListener) {
        super(scheduler, context, context.allocatedBytesCheckInterval.toMillis(), TimeUnit.MILLISECONDS);
        this.lowMemoryListener = lowMemoryListener;
        this.instrument = instrument;
        this.memoryLimit = context.heapMemoryLimit;
        this.allocatedBytesCheckFactor = context.allocatedBytesCheckFactor;
        this.retainedBytesCheckInterval = context.retainedBytesCheckInterval.toMillis();
    }

    private long getAllocatedBytes(SandboxContext c) {
        long allocatedBytes = c.getAllocatedBytes();
        lastAllocatedBytes = allocatedBytes;
        /*
         * ThreadMXBean#getThreadAllocatedBytes() intermittently returns a wrong number and if that
         * number was too high, we have to reset all places that might have used it.
         */
        if (allocatedBytes < previousRetainedSizeComputationFinishedRequestAllocatedBytes) {
            previousRetainedSizeComputationFinishedRequestAllocatedBytes = 0;
        }
        if (allocatedBytes < lastRetainedSizeComputationFinishedRequestAllocatedBytes) {
            lastRetainedSizeComputationFinishedRequestAllocatedBytes = previousRetainedSizeComputationFinishedRequestAllocatedBytes;
        }
        if (allocatedBytes < lastRetainedSizeComputationRequestAllocatedBytes) {
            lastRetainedSizeComputationRequestAllocatedBytes = lastRetainedSizeComputationFinishedRequestAllocatedBytes;
        }
        return allocatedBytes;
    }

    @Override
    protected boolean checkLimit() {
        SandboxContext c = this.context.get();
        if (getCheckCount() == 1 && c != null) {
            SandboxInstrument.log(instrument, c, "[memory-limit-checker-invocation-%d] Memory limit checker initiated for context.", getCheckCount());
        }
        if (c != null && !c.initialized) {
            /*
             * Initialization holds underlying polyglot context lock and also the correspoding
             * instrument lock, so operation on the context could lead to deadlock.
             */
            return true;
        }
        if (c == null || c.isClosed()) {
            if (c != null) {
                c.retainedSizeComputationCancelled.set(true);
            }
            logCancelPeriodicTask(c, "context closed");
            return false;
        }

        long allocatedBytes = getAllocatedBytes(c);
        if (retainedSizeComputationRequested && retainedSizeComputationResultFuture.isDone()) {
            if (!getComputationResultAndCancelIfNeeded(c)) {
                return false;
            }
        }
        long allocatedBytesFromLastFinishedRetainedSizeComputationRequest = allocatedBytes - lastRetainedSizeComputationFinishedRequestAllocatedBytes;
        if (lastRetainedBytes + allocatedBytesFromLastFinishedRetainedSizeComputationRequest > memoryLimit * allocatedBytesCheckFactor) {
            if (!retainedSizeComputationRequested) {
                if (lowMemoryListener != null) {
                    synchronized (lowMemoryListener) {
                        requestRetainedSizeComputation(c, allocatedBytes);
                    }
                } else {
                    requestRetainedSizeComputation(c, allocatedBytes);
                }
            }
        }

        return true;
    }

    private boolean getComputationResultAndCancelIfNeeded(SandboxContext c) {
        SandboxMemoryLimitRetainedSizeChecker.Result result = new SandboxMemoryLimitRetainedSizeChecker.Result(-1, false);
        do {
            try {
                result = retainedSizeComputationResultFuture.get();
            } catch (ExecutionException e) {
                SandboxInstrument.logAlways(instrument, c, "[memory-limit-checker-invocation-%d] Retained size computation task threw an exception for context!", e, getCheckCount());
            } catch (InterruptedException | CancellationException e) {
            }
        } while (!retainedSizeComputationResultFuture.isDone());
        SandboxInstrument.log(instrument, c, "[memory-limit-checker-invocation-%d] Scheduled retained size computation finished for context.", getCheckCount());
        retainedSizeComputationRequested = false;

        if (result.contextInvalid) {
            logCancelPeriodicTask(c, "retained size computation result");
            return false;
        }

        if (result.retainedBytes >= 0) {
            lastRetainedBytes = result.retainedBytes;
            c.lastRetainedBytes = 0;
            previousRetainedSizeComputationFinishedRequestAllocatedBytes = lastRetainedSizeComputationFinishedRequestAllocatedBytes;
            lastRetainedSizeComputationFinishedRequestAllocatedBytes = lastRetainedSizeComputationRequestAllocatedBytes;
        }

        return true;
    }

    private void requestRetainedSizeComputation(SandboxContext c, long allocatedBytes) {
        if (lowMemoryListener != null && lowMemoryListener.stopTheWorld) {
            return;
        }
        if (System.currentTimeMillis() - lastRetainedSizeComputationRequestTime >= retainedBytesCheckInterval) {
            retainedSizeComputationResultFuture = instrument.submitInRetainedSizeCheckerExecutor(new SandboxMemoryLimitRetainedSizeChecker(c, getCheckCount()));
            if (retainedSizeComputationResultFuture != null) {
                lastRetainedSizeComputationRequestAllocatedBytes = allocatedBytes;
                retainedSizeComputationRequested = true;
                lastRetainedSizeComputationRequestTime = System.currentTimeMillis();
                SandboxInstrument.log(instrument, c, "[memory-limit-checker-invocation-%d] Retained size computation requested on schedule for context.", getCheckCount());
            }
        }
    }

    @Override
    protected void logCancelPeriodicTask(SandboxContext c, String reason) {
        if (c == null) {
            SandboxInstrument.log(instrument, "[memory-limit-checker-invocation-%d] Memory limit checker cancelled for already collected context.", getCheckCount());
        } else {
            SandboxInstrument.log(instrument, c, "[memory-limit-checker-invocation-%d] Memory limit checker cancelled for context. Reason: %s.", getCheckCount(), reason);
        }
    }

    @Override
    protected void logReactivatedContext(SandboxContext c) {
        assert c != null;
        CompilerAsserts.neverPartOfCompilation("Deactivation of a memory limit checker for a particular context must not be execute from compiled code");
        SandboxInstrument.log(instrument, c, "[memory-limit-checker-invocation-%d] Memory limit checker reactivated for active context.", getCheckCount());
    }

    @Override
    protected void logInactiveContext(SandboxContext c) {
        assert c != null;
        CompilerAsserts.neverPartOfCompilation("Deactivation of a memory limit checker for a particular context must not be execute from compiled code");
        SandboxInstrument.log(instrument, c, "[memory-limit-checker-invocation-%d] Memory limit checker deactivated for inactive context.", getCheckCount());
    }
}
