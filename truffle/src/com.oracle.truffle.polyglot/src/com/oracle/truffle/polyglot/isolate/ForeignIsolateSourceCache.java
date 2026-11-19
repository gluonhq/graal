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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.ForeignObjectCleaner;
import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.GenerateNativeToNativeBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.IsolateThread;
import org.graalvm.nativebridge.ReceiverMethod;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;

import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.SourceByValue;

@GenerateHotSpotToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateNativeToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
abstract class ForeignIsolateSourceCache implements ForeignObject, IsolateSourceCache {

    private final Map<Object, Long> sourceHandles;

    ForeignIsolateSourceCache() {
        sourceHandles = Collections.synchronizedMap(new WeakHashMap<>());
    }

    static boolean isSourceRemotelyCacheable(Object source, APIAccess apiAccess) {
        /*
         * NOT remotely cacheable sources are those explicitly set as non-cached sources and all
         * remote sources. The remote sources cannot be mixed with local sources in the
         * sourceHandles map, because the equals method won't work between local and remote sources.
         * There is also no easy way to have a separate cache for the remote sources, because many
         * remote sources can represent the same source and they all have a different receiver
         * object (NativeObject instance), so there is no easy way to solve releasing of such cached
         * objects.
         */
        Object sourceReceiver = apiAccess.getSourceReceiver(source);
        AbstractSourceDispatch sourceDispatch = apiAccess.getSourceDispatch(source);
        return !(sourceReceiver instanceof ForeignObject) && sourceDispatch.isCached(sourceReceiver);
    }

    @Override
    public final long translate(Object source) {
        APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
        if (isSourceRemotelyCacheable(source, apiAccess)) {
            return sourceHandles.computeIfAbsent(source, (s) -> {
                long handle = translateImpl(s);
                /*
                 * There can be many polyglot.Source instances representing the same source, but
                 * they all must have the same receiver (api.Source), so that is what we must base
                 * the releasing mechanism on.
                 */
                Object r = apiAccess.getSourceReceiver(s);
                new ForeignObjectCleaner<>(r, getPeer().getIsolate()) {
                    @Override
                    protected void cleanUp(IsolateThread isolateThread) {
                        release(handle);
                    }
                }.register();
                return handle;
            });
        } else {
            return translateImpl(source);
        }
    }

    @ReceiverMethod("translate")
    abstract long translateImpl(@SourceByValue Object source);

    @Override
    public final Object unhand(long handle) {
        throw new UnsupportedOperationException("Not supported on host.");
    }

    @Override
    public final Set<Object> getCachedSources() {
        synchronized (sourceHandles) {
            return Set.copyOf(sourceHandles.keySet());
        }
    }
}
