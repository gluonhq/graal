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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;

final class SandboxLowMemoryListener implements javax.management.NotificationListener {

    private static volatile ThreadPoolExecutor worldUnstopperExecutor;
    static volatile SandboxLowMemoryListener lowMemoryListener;
    private static volatile List<MemoryPoolMXBean> heapPools;
    static Boolean lowMemoryTriggerEnabled;
    static Boolean lowMemoryTriggerReusesSetThreshold;
    Double retainedBytesCheckFactor;
    volatile boolean installed;
    Set<MemoryPoolMXBean> reusedUsageThresholdPools;
    Set<MemoryPoolMXBean> reusedCollectionUsageThresholdPools;
    private final AtomicLong lowMemoryTriggerNumber = new AtomicLong();
    volatile boolean stopTheWorld;

    volatile Phaser phaser;
    private final AtomicLong smallestToLimit = new AtomicLong();
    private final AtomicLong phaseInstrumentId = new AtomicLong();

    void clearSmallestToLimit() {
        smallestToLimit.set(Long.MAX_VALUE);
        phaseInstrumentId.set(-1);
    }

    void updateSmallestToLimit(long instrumentId, long bytesToLimit) {
        smallestToLimit.updateAndGet(operand -> {
            if (bytesToLimit < operand) {
                /*
                 * The following side effect is fine. Even though the update may be restarted, the
                 * final phaseInstrumentId must be the id of one of the instrument with lowest
                 * bytesToLimit.
                 */
                lowMemoryListener.phaseInstrumentId.set(instrumentId);
                return bytesToLimit;
            } else {
                return operand;
            }
        });
    }

    long getPhaseInstrumentId() {
        return phaseInstrumentId.get();
    }

    static SandboxLowMemoryListener installLowMemoryListener(double heapCheckFactor, boolean reuseThresholds) {
        SandboxLowMemoryListener listener;
        synchronized (SandboxLowMemoryListener.class) {
            listener = lowMemoryListener;
            if (listener == null) {
                listener = new SandboxLowMemoryListener();
                lowMemoryListener = listener;
            }
            /*
             * Because of the following check, we always need to synchronize on the
             * SandboxLowMemoryListener class.
             */
            if (listener.retainedBytesCheckFactor != null) {
                if (Math.abs(listener.retainedBytesCheckFactor - heapCheckFactor) > 0.000001d) {
                    throw new SandboxException(
                                    "Invalid retained bytes check factor '" + SandboxContext.formatMemorySizeFactor(heapCheckFactor) + "' detected. " +
                                                    "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.RetainedBytesCheckFactor'. " +
                                                    "To resolve this use the same option value for 'sandbox.RetainedBytesCheckFactor' for all contexts.",
                                    null);
                }
            } else {
                listener.retainedBytesCheckFactor = heapCheckFactor;
            }
            if (!listener.installed) {
                if (reuseThresholds) {
                    listener.reusedUsageThresholdPools = new LinkedHashSet<>();
                    listener.reusedCollectionUsageThresholdPools = new LinkedHashSet<>();
                }
                MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
                List<MemoryPoolMXBean> pools = getHeapPoolMXBeans();
                NotificationEmitter emitter = (NotificationEmitter) mbean;
                for (MemoryPoolMXBean pool : pools) {
                    if (!reuseThresholds && pool.isCollectionUsageThresholdSupported() && pool.isUsageThresholdSupported() &&
                                    (pool.getCollectionUsageThreshold() != 0 || pool.getUsageThreshold() != 0)) {
                        listener.retainedBytesCheckFactor = null;
                        throw new SandboxException("Low memory trigger for heap memory limit cannot be installed. Collection usage threshold or usage threshold of memory pool " + pool.getName() +
                                        " is already in use. To resolve this set the option 'sandbox.UseLowMemoryTrigger' to 'false' or the option 'sandbox.ReuseLowMemoryTriggerThreshold' to 'true'.",
                                        null);
                    }
                }
                emitter.addNotificationListener(listener, null, null);
                for (MemoryPoolMXBean pool : pools) {
                    if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                        SandboxInstrument.logToAllInstruments("Memory pool %s does not support both usage threshold and collection usage threshold.", pool.getName());
                        continue;
                    }
                    long collectionUsageThreshold = (long) Math.floor(listener.retainedBytesCheckFactor * pool.getCollectionUsage().getMax());
                    if (pool.getCollectionUsageThreshold() == 0) {
                        pool.setCollectionUsageThreshold(collectionUsageThreshold);
                    } else {
                        collectionUsageThreshold = pool.getCollectionUsageThreshold();
                        listener.reusedCollectionUsageThresholdPools.add(pool);
                    }
                    SandboxInstrument.logToAllInstruments("Memory pool %s of size %dKB collection usage threshold set to %dKB.", pool.getName(), pool.getCollectionUsage().getMax() / 1024,
                                    collectionUsageThreshold / 1024);
                    long usageThreshold = (long) Math.floor(listener.retainedBytesCheckFactor * pool.getUsage().getMax());
                    if (pool.getUsageThreshold() == 0) {
                        pool.setUsageThreshold(usageThreshold);
                    } else {
                        usageThreshold = pool.getUsageThreshold();
                        listener.reusedUsageThresholdPools.add(pool);
                    }
                    SandboxInstrument.logToAllInstruments("Memory pool %s of size %dKB usage threshold set to %dKB.", pool.getName(), pool.getUsage().getMax() / 1024, usageThreshold / 1024);
                }
                listener.installed = true;
            }
        }
        return listener;
    }

    static void uninstallLowMemoryListener(SandboxInstrument lastInstrument) {
        assert Thread.holdsLock(SandboxLowMemoryListener.class);

        SandboxLowMemoryListener listener = lowMemoryListener;
        if (listener != null && listener.installed) {
            MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
            List<MemoryPoolMXBean> pools = getHeapPoolMXBeans();
            NotificationEmitter emitter = (NotificationEmitter) mbean;
            try {
                emitter.removeNotificationListener(listener, null, null);
            } catch (ListenerNotFoundException lnf) {
                throw new RuntimeException(lnf);
            }
            for (MemoryPoolMXBean pool : pools) {
                if (!pool.isCollectionUsageThresholdSupported() || !pool.isUsageThresholdSupported()) {
                    SandboxInstrument.log(lastInstrument, "Memory pool %s does not support both usage threshold and collection usage threshold.", pool.getName());
                    continue;
                }
                if (listener.reusedCollectionUsageThresholdPools == null || !listener.reusedCollectionUsageThresholdPools.contains(pool)) {
                    pool.setCollectionUsageThreshold(0);
                    SandboxInstrument.log(lastInstrument, "Memory pool %s of size %dKB collection usage threshold unset.", pool.getName(), pool.getCollectionUsage().getMax() / 1024);
                }
                if (listener.reusedUsageThresholdPools == null || !listener.reusedUsageThresholdPools.contains(pool)) {
                    pool.setUsageThreshold(0);
                    SandboxInstrument.log(lastInstrument, "Memory pool %s of size %dKB usage threshold unset.", pool.getName(), pool.getUsage().getMax() / 1024);
                }
            }
            listener.installed = false;
            listener.reusedCollectionUsageThresholdPools = null;
            listener.reusedUsageThresholdPools = null;
            listener.retainedBytesCheckFactor = null;
        }
        lowMemoryTriggerEnabled = null;
        lowMemoryTriggerReusesSetThreshold = null;
    }

    private static List<MemoryPoolMXBean> getHeapPoolMXBeans() {
        List<MemoryPoolMXBean> pools = heapPools;
        if (pools == null) {
            synchronized (SandboxLowMemoryListener.class) {
                pools = heapPools;
                if (pools == null) {
                    pools = ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getType() == MemoryType.HEAP).collect(Collectors.toList());
                    heapPools = pools;
                }
            }
        }
        return pools;
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        String notifType = notification.getType();
        if (notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) || notifType.equals(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {
            long triggerNumber = lowMemoryTriggerNumber.incrementAndGet();
            synchronized (this) {
                if (!stopTheWorld) {
                    /*
                     * Make sure to set the stopTheWorld flag before obtaining the context list. New
                     * contexts (on new engines with new instruments) might be created in parallel
                     * and those are immediately stopped if the stopTheWorld flag is set. By
                     * obtaining the instrument list after the flag is set we make sure there is no
                     * memory limited context that stays unaccounted for.
                     */
                    stopTheWorld = true;
                    phaser = new Phaser(1);

                    long stopStart = System.currentTimeMillis();
                    List<SandboxInstrument> memoryLimitedInstruments = SandboxInstrument.getMemoryLimitedSandboxInstruments(true, triggerNumber);
                    long stopFinish = System.currentTimeMillis();
                    for (SandboxInstrument loggingInstrument : memoryLimitedInstruments) {
                        SandboxInstrument.log(loggingInstrument, "[low-memory-trigger-%d] Pausing all memory limited contexts on %susage threshold.", triggerNumber,
                                        notifType.equals(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED) ? "collection " : "");

                        List<MemoryPoolMXBean> pools = getHeapPoolMXBeans();
                        for (MemoryPoolMXBean pool : pools) {
                            if (pool.isUsageThresholdSupported() && pool.isCollectionUsageThresholdSupported()) {
                                SandboxInstrument.log(loggingInstrument, "[low-memory-trigger-%d] Pool %s usage: %dKB.", triggerNumber, pool.getName(), pool.getUsage().getUsed() / 1024);
                                SandboxInstrument.log(loggingInstrument, "[low-memory-trigger-%d] Pool %s collection usage: %dKB.", triggerNumber, pool.getName(),
                                                pool.getCollectionUsage().getUsed() / 1024);
                                SandboxInstrument.log(loggingInstrument, "[low-memory-trigger-%d] Pool %s usage treshold:  %dKB.", triggerNumber, pool.getName(),
                                                pool.getUsageThreshold() / 1024);
                                SandboxInstrument.log(loggingInstrument, "[low-memory-trigger-%d] Pool %s usage treshold count: %d.", triggerNumber, pool.getName(),
                                                pool.getUsageThresholdCount());
                                SandboxInstrument.log(loggingInstrument, "[low-memory-trigger-%d] Pool %s collection usage treshold:  %dKB.", triggerNumber, pool.getName(),
                                                pool.getCollectionUsageThreshold() / 1024);
                                SandboxInstrument.log(loggingInstrument, "[low-memory-trigger-%d] Pool %s collection usage treshold count: %d.", triggerNumber, pool.getName(),
                                                pool.getCollectionUsageThresholdCount());
                            }
                        }
                    }
                    long logFinish = System.currentTimeMillis();
                    getWorldUnstopperExecutor().submit(new SandboxWorldUnstopper(this, triggerNumber));
                    for (SandboxInstrument loggingInstrument : memoryLimitedInstruments) {
                        SandboxInstrument.log(loggingInstrument,
                                        "[low-memory-trigger-%d] Pause requested for all memory limited contexts on %susage threshold in %dms. Logging took %dms.",
                                        triggerNumber, notifType.equals(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED) ? "collection " : "", stopFinish - stopStart,
                                        logFinish - stopFinish);
                    }
                }
            }
        }
    }

    static ExecutorService getWorldUnstopperExecutor() {
        ThreadPoolExecutor executor = worldUnstopperExecutor;
        if (executor == null) {
            synchronized (SandboxMemoryLimitChecker.class) {
                executor = worldUnstopperExecutor;
                if (executor == null) {
                    executor = (ThreadPoolExecutor) Executors.newCachedThreadPool(
                                    new HighPriorityThreadFactory("Sandbox World Unstopper Thread"));
                    executor.setKeepAliveTime(1, TimeUnit.SECONDS);
                    worldUnstopperExecutor = executor;
                }
            }
        }
        return executor;
    }

}
