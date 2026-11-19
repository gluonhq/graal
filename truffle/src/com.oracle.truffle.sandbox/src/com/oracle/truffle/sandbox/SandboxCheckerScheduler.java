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

import java.lang.ref.WeakReference;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * This class executes the {@link SandboxChecker#checkLimit()} method similarly as
 * {@link java.util.concurrent.ScheduledThreadPoolExecutor#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
 * executes the {@link Runnable#run()} method of the passed runnable. However, the scheduled thread
 * pool executor uses thread management that is not suitable for our limit checking purposes. What
 * we want is a dedicated thread for each limit checker type that is woken regularly to check the
 * appropriate limit for the appropriate context and that is exactly what this class does.
 */
final class SandboxCheckerScheduler implements Runnable {

    private final String checkedLimit;
    private final SandboxInstrument instrument;
    private final PriorityQueue<SandboxChecker> queue = new PriorityQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private volatile boolean paused;
    private boolean schedulerTerminated = true;
    private final AtomicLong startCount = new AtomicLong();
    private final AtomicLong terminationCount = new AtomicLong();

    SandboxCheckerScheduler(SandboxInstrument instrument, String checkedLimit) {
        this.instrument = instrument;
        this.checkedLimit = checkedLimit;
    }

    @Override
    public void run() {
        boolean terminatedEmpty = false;
        while (!this.instrument.isDisposed()) {
            try {
                SandboxChecker checker = null;
                boolean checkerInactive = false;
                SandboxContext c = null;
                ReentrantLock l = this.lock;
                l.lockInterruptibly();
                try {
                    while ((checker = queue.peek()) != null && checker.finished) {
                        queue.remove();
                        checker.unschedule();
                    }

                    if (checker == null) {
                        schedulerTerminated = true;
                        terminatedEmpty = true;
                        break;
                    }

                    if (paused) {
                        condition.await();
                        continue;
                    }

                    long now = System.nanoTime();
                    if (now < checker.nextExecution) {
                        long waitTime = checker.nextExecution - now;
                        condition.awaitNanos(waitTime);
                        continue;
                    }

                    queue.remove();

                    c = checker.context.get();
                    if (c == null) {
                        // context gc'ed
                        checker.unschedule();
                        continue;
                    }

                    long changedActiveStatusCount = c.changedActiveStatusCount;
                    if (c.threadCounter.get() == 0 && changedActiveStatusCount == checker.lastChangedActiveStatusCount) {
                        if (checker.shouldUnscheduleOnNextIteration()) {
                            checker.unschedule();
                            checkerInactive = true;
                            continue;
                        } else {
                            checker.unscheduleOnNextIteration(true);
                        }
                    } else {
                        checker.unscheduleOnNextIteration(false);
                    }

                    checker.lastChangedActiveStatusCount = changedActiveStatusCount;
                    checker.lastExecution = now;
                    checker.reschedule();

                    queue.add(checker);
                } finally {
                    l.unlock();
                    // Parfait_ALLOW impossible-redundant-condition
                    if (checker != null && checkerInactive) {
                        checker.logInactiveContext(c);
                    }
                }

                checker.checkCount++;
                if (!checker.checkLimit()) {
                    checker.finished = true;
                }
            } catch (InterruptedException ie) {
                // we don't want to stop this thread when interrupted
            }
        }
        if (terminatedEmpty) {
            long tCnt = terminationCount.incrementAndGet();
            SandboxInstrument.log(instrument, "Multi context %s limit checker termination #%d because there are no active contexts", checkedLimit, tCnt);
        }
    }

    /**
     * Schedule periodic execution of {@link SandboxChecker#checkLimit()} every
     * {@link SandboxChecker#periodNs} nanoseconds. Starts the scheduler if it is not running.
     * 
     * @param checker the checker to schedule.
     * @return <code>true</code> if the checker was scheduled by the call, false if it was already
     *         scheduled.
     */
    @CompilerDirectives.TruffleBoundary
    boolean scheduleChecker(SandboxChecker checker) {
        boolean toRet;
        boolean schedulerIsTerminated = false;
        Lock l = this.lock;
        l.lock();
        try {
            if (!checker.isScheduled()) {
                /*
                 * We can't just ask whether the queue is empty here, we start the scheduler only if
                 * it is terminated and empty queue doesn't guarantee that.
                 */
                schedulerIsTerminated = schedulerTerminated;
                schedulerTerminated = false;
                queue.add(checker);
                checker.setScheduled();
                condition.signal();
                toRet = true;
            } else {
                toRet = false;
            }
        } finally {
            l.unlock();
        }
        if (schedulerIsTerminated) {
            instrument.submitInLimitCheckerExecutor(this);
            long sCnt = startCount.incrementAndGet();
            SandboxInstrument.log(instrument, "Multi context %s limit checker start #%d", checkedLimit, sCnt);
        }
        return toRet;
    }

    /**
     * Pause the whole scheduler, i.e. pause the periodic invocation of
     * {@link SandboxChecker#checkLimit()} of the checkers that are in the queue.
     */
    void pause() {
        /*
         * We don't need a lock, because it is never used in such a way that pause and resume are
         * executed simultaneously.
         */
        paused = true;
    }

    /**
     * Resume the whole scheduler, i.e. continue the periodic invocation of
     * {@link SandboxChecker#checkLimit()} of the checkers that are in the queue.
     */
    void resume() {
        Lock l = this.lock;
        l.lock();
        try {
            paused = false;
            condition.signal();
        } finally {
            l.unlock();
        }
    }

    abstract static class SandboxChecker implements Comparable<SandboxChecker> {
        private final SandboxCheckerScheduler scheduler;
        protected final WeakReference<SandboxContext> context;
        private final long periodNs;
        private long lastExecution;
        private long nextExecution;
        private long checkCount;
        private boolean finished;
        private long lastChangedActiveStatusCount;
        /*
         * This context checker is in the queue of the appropriate checker scheduler and the
         * checkLimit method is executed every periodNs nanoseconds.
         */
        private volatile boolean scheduled;
        /*
         * The context of this checker has been inactive for at least periodNs nanoseconds and this
         * checker is a candidate for removal from the appropriate checker scheduler's queue.
         */
        private volatile boolean unscheduleOnNextIteration;

        SandboxChecker(SandboxCheckerScheduler scheduler, SandboxContext context, long period, TimeUnit timeUnit) {
            this.scheduler = scheduler;
            this.context = new WeakReference<>(context);
            this.lastExecution = System.nanoTime();
            /*
             * SandboxContext limits the period to 1h, so no real danger of overflows.
             */
            long localPeriodNs = TimeUnit.NANOSECONDS.convert(period, timeUnit);
            this.nextExecution = lastExecution + localPeriodNs;
            this.periodNs = localPeriodNs;
        }

        protected abstract boolean checkLimit();

        @SuppressWarnings("unused")
        protected void logCancelPeriodicTask(SandboxContext c, String reason) {

        }

        @SuppressWarnings("unused")
        protected void logReactivatedContext(SandboxContext c) {

        }

        @SuppressWarnings("unused")
        protected void logInactiveContext(SandboxContext c) {

        }

        @Override
        public int compareTo(SandboxChecker o) {
            return Long.compare(this.nextExecution, o.nextExecution);
        }

        private void reschedule() {
            this.nextExecution = this.lastExecution + this.periodNs;
        }

        private void unscheduleOnNextIteration(boolean value) {
            assert scheduler.lock.isHeldByCurrentThread();
            unscheduleOnNextIteration = value;
        }

        private void unschedule() {
            assert scheduler.lock.isHeldByCurrentThread();
            /*
             * The order of the following two volatile assignments is important, opposite order
             * could lead to the SandboxActivationListener reading unscheduleOnNextIteration "false"
             * and scheduled "true" (i.e. needsSchedule() == false) and the context activation not
             * calling the SandboxActivationListener#scheduleChecker method. So we would have an
             * active context and unscheduled checker for it.
             */
            scheduled = false;
            unscheduleOnNextIteration = false;
        }

        private void setScheduled() {
            assert scheduler.lock.isHeldByCurrentThread();
            scheduled = true;
        }

        private boolean shouldUnscheduleOnNextIteration() {
            assert scheduler.lock.isHeldByCurrentThread();
            return unscheduleOnNextIteration;
        }

        boolean needsSchedule() {
            return unscheduleOnNextIteration || !scheduled;
        }

        private boolean isScheduled() {
            assert scheduler.lock.isHeldByCurrentThread();
            return scheduled;
        }

        protected long getCheckCount() {
            return checkCount;
        }
    }
}
