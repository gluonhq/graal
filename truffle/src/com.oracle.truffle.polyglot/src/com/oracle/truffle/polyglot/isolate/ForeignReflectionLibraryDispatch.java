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

import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.GenerateNativeToNativeBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.HSPeer;
import org.graalvm.nativebridge.IsolateDeathHandler;
import org.graalvm.nativebridge.NativePeer;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ProcessPeer;

/**
 * Native bridge DSL definition that generates implementations of {@link ReflectionLibraryDispatch}.
 *
 * @see ReflectionLibraryDispatch
 */
@GenerateHotSpotToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateNativeToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateNativeToHotSpotBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
@IsolateDeathHandler(IsolateDeathHandlerSupport.AsCancelledException.class)
abstract class ForeignReflectionLibraryDispatch implements ForeignObject, ReflectionLibraryDispatch {

    /**
     * This method must be overridden by an optimized implementation tailored to the specific
     * communication technology.
     *
     * @see NativeReflectionLibrarySupport for internal isolate optimization
     * @see ProcessReflectionLibrarySupport for external isolate optimization
     */
    @Override
    public final Object dispatch(long objectHandle, int messageId, Object[] args) {
        throw new UnsupportedOperationException("For performance reasons must be implemented by optimized protocol");
    }

    /**
     * Called by the {@link org.graalvm.nativebridge.ForeignObjectCleaner} infrastructure class when
     * the Truffle object reference becomes weakly reachable. {@code ForeignObjectCleaner} handles
     * {@link org.graalvm.nativebridge.IsolateDeathException} internally, so we must not translate
     * it to the domain specific exception.
     */
    @Override
    @IsolateDeathHandler(IsolateDeathHandlerSupport.KeepIsolateDeathException.class)
    public abstract void releaseReference(long objectHandle);

    /**
     * Creates an optimized {@link ReflectionLibraryDispatch} instance for host-to-guest interop
     * calls. It returns an implementation with a fine-tuned override of
     * {@link #dispatch(long, int, Object[])} for improved performance.
     *
     * @see NativeReflectionLibrarySupport for internal isolate optimization
     * @see ProcessReflectionLibrarySupport for external isolate optimization
     */
    static ReflectionLibraryDispatch optimized(ForeignContext context, ForeignReflectionLibraryDispatch baseDispatch) {
        Peer peer = baseDispatch.getPeer();
        if (peer instanceof NativePeer) {
            return NativeReflectionLibrarySupport.createOptimizedHostToGuestDispatch(context, baseDispatch);
        } else if (peer instanceof ProcessPeer) {
            return ProcessReflectionLibrarySupport.createOptimizedHostToGuestDispatch(context, baseDispatch);
        } else {
            throw new IllegalArgumentException("Unsupported peer " + peer);
        }
    }

    /**
     * Creates an optimized {@link ReflectionLibraryDispatch} instance for guest-to-host interop
     * calls. It returns an implementation with a fine-tuned override of
     * {@link #dispatch(long, int, Object[])} for improved performance.
     *
     * @see NativeReflectionLibrarySupport for internal isolate optimization
     * @see ProcessReflectionLibrarySupport for external isolate optimization
     */
    static ReflectionLibraryDispatch optimized(GuestContext context, ForeignReflectionLibraryDispatch baseDispatch, long hostStackSpaceHeadroom) {
        Peer peer = baseDispatch.getPeer();
        if (peer instanceof HSPeer) {
            return NativeReflectionLibrarySupport.createOptimizedGuestToHostDispatch(context, baseDispatch, hostStackSpaceHeadroom);
        } else if (peer instanceof ProcessPeer) {
            return ProcessReflectionLibrarySupport.createOptimizedGuestToHostDispatch(context, baseDispatch);
        } else {
            throw new IllegalArgumentException("Unsupported peer " + peer);
        }
    }
}
