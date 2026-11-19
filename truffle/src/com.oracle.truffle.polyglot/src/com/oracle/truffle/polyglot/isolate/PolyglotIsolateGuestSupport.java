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
package com.oracle.truffle.polyglot.isolate;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import com.oracle.truffle.api.TruffleContext;
import org.graalvm.nativebridge.HSPeer;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ProcessPeer;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.ThreadScope;

public final class PolyglotIsolateGuestSupport {

    static volatile Lazy lazy;

    private static final PriorityBlockingQueue<TearDownAction> tearDownHooks = new PriorityBlockingQueue<>(7, Comparator.reverseOrder());

    private PolyglotIsolateGuestSupport() {
    }

    static boolean isHost() {
        return lazy == null;
    }

    static boolean isGuest() {
        return lazy != null;
    }

    static long getHostStackOverflowLimit() {
        return lazy.hostStackOverflowLimit.get();
    }

    /**
     * Registers a hook called when isolate is going to be disposed.
     *
     * @param priority the hook priority. The registered hooks are called according to priority
     *            starting with highest priority.
     * @param tearDownHook the hook to be called before isolate is disposed. The given
     *            {@code tearDownHook} is held by a weak reference.
     */
    static void registerTearDownHook(int priority, TearDownHook tearDownHook) {
        Objects.requireNonNull(tearDownHook, "TearDownHook must be non null.");
        tearDownHooks.offer(new TearDownAction(priority, tearDownHook));
    }

    /**
     * Prepares the isolate for shut down by executing registered {@link TearDownHook}s.
     *
     * @see #registerTearDownHook(int, TearDownHook)
     */
    static void tearDown() {
        for (TearDownAction action = tearDownHooks.poll(); action != null; action = tearDownHooks.poll()) {
            action.perform();
        }
        lazy.disposed = true;
    }

    static void registerEngine(Object engineReceiver, GuestEngine guestEngine) {
        lazy.guestEngineByEngineReceiver.put(engineReceiver, guestEngine);
    }

    static void patchEngine(Object originalEngineReceiver, Object newEngineReceiver) {
        GuestEngine guestEngine = lazy.guestEngineByEngineReceiver.remove(originalEngineReceiver);
        lazy.guestEngineByEngineReceiver.put(newEngineReceiver, guestEngine);
    }

    static void registerContext(GuestContext guestContext) {
        Lazy l = lazy;
        cleanUpClosedContexts(l);
        l.guestContextByContextReceiver.put(guestContext.polyglotContextReceiver, guestContext);
    }

    static Object getSource(long sourceHandle) {
        return lazy.sourceCache.unhand(sourceHandle);
    }

    private static void cleanUpClosedContexts(Lazy l) {
        for (Iterator<GuestContext> it = l.contextsToClean.iterator(); it.hasNext();) {
            cleanUpGuestContext(l, it.next());
            it.remove();
        }
        tearDownHooks.removeIf((action) -> action.get() == null);
    }

    static void cleanUpGuestContext(Lazy l, GuestContext guestContext) {
        /*
         * cleanUpClosedContexts can be executed in parallel.
         */
        if (l.guestContextByContextReceiver.remove(guestContext.polyglotContextReceiver) != null) {
            guestContext.dispose();
        }
    }

    static ThreadScope createThreadScope(AbstractPolyglotImpl polyglot) {
        return new HostThreadScope(polyglot, lazy);
    }

    @FunctionalInterface
    interface TearDownHook {
        void tearDown();
    }

    private static final class TearDownAction extends WeakReference<TearDownHook> implements Comparable<TearDownAction> {

        private final int priority;

        TearDownAction(int priority, TearDownHook hook) {
            super(hook);
            this.priority = priority;
        }

        public void perform() {
            TearDownHook hook = get();
            if (hook != null) {
                hook.tearDown();
            }
        }

        @Override
        public int compareTo(TearDownAction o) {
            return Integer.compare(priority, o.priority);
        }
    }

    static final class Lazy implements TearDownHook {

        final AbstractPolyglotImpl polyglot;
        final Platform platform;
        final GuestSourceCache sourceCache;
        final ForeignPolyglotHostServices polyglotHostServices;
        final Map<Object, GuestContext> guestContextByContextReceiver;
        final Map<Object, GuestEngine> guestEngineByEngineReceiver;
        final Set<GuestContext> contextsToClean;
        final ThreadLocal<Long> hostStackOverflowLimit;
        /**
         * Identifies an isolate that is being disposed.
         */
        volatile boolean disposed;

        Lazy(AbstractPolyglotImpl polyglot, ForeignPolyglotHostServices polyglotHostServices) {
            this.polyglot = polyglot;
            platform = Platform.create(polyglotHostServices);
            this.sourceCache = new GuestSourceCache();
            this.polyglotHostServices = polyglotHostServices;
            this.guestContextByContextReceiver = new ConcurrentHashMap<>();
            this.guestEngineByEngineReceiver = new ConcurrentHashMap<>();
            this.contextsToClean = ConcurrentHashMap.newKeySet();
            this.hostStackOverflowLimit = ThreadLocal.withInitial(polyglotHostServices::retrieveHostStackOverflowLimit);
            registerTearDownHook(10, this);
        }

        @Override
        public void tearDown() {
            // Free all held foreign object before closing this isolate.
            for (Peer peer : polyglotHostServices.getPeer().getIsolate().getActivePeers()) {
                peer.release();
            }
        }
    }

    interface Platform {

        boolean isCurrentThreadAttached();

        boolean attachCurrentThread();

        void detachCurrentThread(boolean detachFromHost);

        static Platform create(ForeignPolyglotHostServices forHost) {
            Peer peer = forHost.getPeer();
            if (peer instanceof HSPeer) {
                return new JNIIsolatePlatform();
            } else if (peer instanceof ProcessPeer) {
                return new ProcessIsolatePlatform();
            } else {
                throw new IllegalArgumentException(String.valueOf(peer));
            }
        }
    }

    private static final class HostThreadScope extends ThreadScope {

        private final boolean polyglotThread;
        private final boolean detachFromHost;

        @SuppressWarnings("unused")
        HostThreadScope(AbstractPolyglotImpl impl, PolyglotIsolateGuestSupport.Lazy l) {
            super(impl);
            if (!ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Prevent parsing when JNI C directives are not enabled.
                polyglotThread = false;
                detachFromHost = false;
                return;
            }
            Thread currentThread = Thread.currentThread();
            boolean isPolyglotThread = PolyglotIsolateAccessor.ENGINE.isCurrentThreadPolyglotThread();
            boolean attached = false;
            if (!l.disposed) {
                attached = l.platform.attachCurrentThread();
                if (isPolyglotThread && attached) {
                    /*
                     * There are situations where we need to call from polyglot thread into the host
                     * even when the context is not entered. E.g. writing to the context's err
                     * stream in an UncaughtExceptionHandler.
                     */
                    TruffleContext truffleContext = PolyglotIsolateAccessor.ENGINE.getCurrentCreatorTruffleContext();
                    Object contextReceiver = truffleContext != null ? PolyglotIsolateAccessor.LANGUAGE.getPolyglotContext(truffleContext) : null;
                    l.polyglotHostServices.attachPolyglotThread(contextReceiver != null ? PolyglotIsolateAccessor.ENGINE.getOuterContext(contextReceiver) : null,
                                    CurrentIsolate.getCurrentThread().rawValue(), truffleContext != null, getThreadId(Thread.currentThread()));
                } else {
                    // Compiler thread or stdout (stderr) copier thread. These threads do not
                    // call back to isolate we don't need to register them in the NativeIsolate
                    // and pre-enter them to the host context.
                }
            } else if (isPolyglotThread) {
                // Thread was started during isolate tear down. This may happen for compiler thread
                // but never for a polyglot thread.
                throw new AssertionError("Polyglot thread was created during polyglot isolate tear down.");
            }
            this.polyglotThread = isPolyglotThread;
            this.detachFromHost = attached;
        }

        @Override
        public void close() {
            if (!ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Prevent parsing when JNI C directives are not enabled.
                return;
            }
            PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
            if (l.platform.isCurrentThreadAttached()) {
                if (polyglotThread && detachFromHost) {
                    /*
                     * There are situations where we need to call from polyglot thread into the host
                     * even when the context is not entered. E.g. writing to the context's err
                     * stream in an UncaughtExceptionHandler.
                     */
                    TruffleContext truffleContext = PolyglotIsolateAccessor.ENGINE.getCurrentCreatorTruffleContext();
                    Object contextReceiver = truffleContext != null ? PolyglotIsolateAccessor.LANGUAGE.getPolyglotContext(truffleContext) : null;
                    l.polyglotHostServices.detachPolyglotThread(contextReceiver != null ? PolyglotIsolateAccessor.ENGINE.getOuterContext(contextReceiver) : null, truffleContext != null);
                } else {
                    // Compiler thread or stdout (stderr) copier thread. These threads are not
                    // pre-entered to the host context.
                }
                l.platform.detachCurrentThread(detachFromHost);
            } else if (polyglotThread) {
                // Polyglot thread was started during isolate tear down.
                throw new AssertionError("Should not reach here.");
            }
        }
    }

    @SuppressWarnings("deprecation")
    static long getThreadId(Thread thread) {
        return thread.getId();
    }
}
