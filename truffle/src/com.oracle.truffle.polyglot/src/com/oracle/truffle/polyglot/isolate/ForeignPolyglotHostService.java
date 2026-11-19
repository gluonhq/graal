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

import static com.oracle.truffle.polyglot.isolate.PolyglotIsolateGuestSupport.getThreadId;
import static com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.ContextReceiver;
import static com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.EngineReceiver;
import static org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractPolyglotHostService;

import com.oracle.truffle.api.interop.InteropLibrary;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.IsolateDeathException;
import org.graalvm.nativebridge.ReceiverMethod;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;

@GenerateNativeToHotSpotBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
abstract class ForeignPolyglotHostService extends AbstractPolyglotHostService implements ForeignObject {

    ForeignPolyglotHostService() {
        super(PolyglotIsolateHostSupport.getPolyglot());
    }

    @ReceiverMethod("notifyClearExplicitContextStack")
    abstract void notifyClearExplicitContextStackImpl(@ContextReceiver Object contextReceiver);

    @Override
    public final void notifyClearExplicitContextStack(Object contextReceiver) {
        try {
            notifyClearExplicitContextStackImpl(contextReceiver);
        } catch (IsolateDeathException e) {
            // Ignore isolate death
        }
    }

    @ReceiverMethod("notifyContextCancellingOrExiting")
    abstract void notifyContextCancellingOrExitingImpl(@ContextReceiver Object contextReceiver, boolean exit, int exitCode, boolean resourceLimit, String message);

    @Override
    public final void notifyContextCancellingOrExiting(Object contextReceiver, boolean exit, int exitCode, boolean resourceLimit, String message) {
        try {
            notifyContextCancellingOrExitingImpl(contextReceiver, exit, exitCode, resourceLimit, message);
        } catch (IsolateDeathException e) {
            // Ignore isolate death
        }
    }

    @ReceiverMethod("notifyContextClosed")
    abstract void notifyContextClosedImpl(@ContextReceiver Object contextReceiver, boolean cancelIfExecuting, boolean resourceLimit, String message);

    @Override
    public final void notifyContextClosed(Object contextReceiver, boolean cancelIfExecuting, boolean resourceLimit, String message) {
        try {
            notifyContextClosedImpl(contextReceiver, cancelIfExecuting, resourceLimit, message);
        } catch (IsolateDeathException e) {
            // Ignore isolate death
        }
        PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
        if (!l.disposed) {
            GuestContext guestContext = l.guestContextByContextReceiver.get(contextReceiver);
            if (guestContext != null) {
                if (!PolyglotIsolateAccessor.ENGINE.isContextActive(contextReceiver)) {
                    PolyglotIsolateGuestSupport.cleanUpGuestContext(l, guestContext);
                } else {
                    l.contextsToClean.add(guestContext);
                }
            }
        }
    }

    @ReceiverMethod("notifyEngineClosed")
    abstract void notifyEngineClosedImpl(@EngineReceiver Object engineReceiver, boolean cancelIfExecuting);

    @Override
    public final void notifyEngineClosed(Object engineReceiver, boolean cancelIfExecuting) {
        try {
            notifyEngineClosedImpl(engineReceiver, cancelIfExecuting);
        } catch (IsolateDeathException e) {
            // Ignore isolate death
        }
    }

    @Override
    public final RuntimeException hostToGuestException(AbstractHostLanguageService host, Throwable throwable) {
        assert isHostException(throwable);
        return (RuntimeException) throwable;
    }

    private static boolean isHostException(Throwable throwable) {
        InteropLibrary interop = InteropLibrary.getUncached(throwable);
        return interop.isHostObject(throwable) && interop.isException(throwable);
    }

    @Override
    public final void notifyPolyglotThreadStart(Object contextReceiver, Thread threadToStart) {
        PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
        l.polyglotHostServices.notifyPolyglotThreadStart(contextReceiver, getThreadId(threadToStart));
    }
}
