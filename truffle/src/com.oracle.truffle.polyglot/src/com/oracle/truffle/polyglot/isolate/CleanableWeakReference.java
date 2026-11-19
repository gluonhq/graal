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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A weak reference performing given action when referent becomes weakly reachable and is enqueued
 * into reference queue. The class is not active. The reference queue check is performed anytime a
 * new {@link CleanableWeakReference} is created, or it can be performed explicitly using
 * {@link CleanableWeakReference#clean()}.
 */
abstract class CleanableWeakReference<T> extends WeakReference<T> implements Runnable {

    private static final Set<CleanableWeakReference<?>> cleaners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ReferenceQueue<Object> cleanersQueue = new ReferenceQueue<>();

    CleanableWeakReference(T referent) {
        super(referent, cleanersQueue);
        clean();
        cleaners.add(this);
    }

    /**
     * Invalidates the {@link CleanableWeakReference}. When the reference is invalidated the cleanup
     * action is no more called when the reference enqueued.
     */
    void invalidate() {
        cleaners.remove(this);
    }

    /**
     * Performs an explicit clean up of enqueued {@link CleanableWeakReference}s.
     */
    static void clean() {
        CleanableWeakReference<?> ref;
        while ((ref = (CleanableWeakReference<?>) cleanersQueue.poll()) != null) {
            if (cleaners.remove(ref)) {
                ref.run();
            }
        }
    }
}
