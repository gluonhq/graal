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

import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

final class HighPriorityThreadFactory implements ThreadFactory {
    private final Env env;
    private final AtomicLong threadCounter = new AtomicLong();
    private final String baseName;

    /**
     * Creates a {@link ThreadFactory} creating engine bound system threads.
     */
    HighPriorityThreadFactory(Env env, String baseName) {
        Objects.requireNonNull(env, "Env must be non null");
        this.env = env;
        this.baseName = baseName;
    }

    /**
     * Creates a {@link ThreadFactory} creating global non system threads. Ideally it should be used
     * only from the {@link SandboxLowMemoryListener}, see issue GR-40366. Consider to create a new
     * ThreadFactory for the SandboxLowMemoryListener and delete this constructor.
     */
    HighPriorityThreadFactory(String baseName) {
        this.env = null;
        this.baseName = baseName;
    }

    @Override
    public Thread newThread(Runnable r) {
        // The env should be non-null for all threads except of threads used by the
        // SandboxLowMemoryListener, see issue GR-40366. Consider to create a new ThreadFactory for
        // the SandboxLowMemoryListener and keep the env non-null.
        Thread t = env != null ? env.createSystemThread(r) : new Thread(r);
        t.setDaemon(true);
        t.setName(baseName + "-" + threadCounter.incrementAndGet());
        t.setPriority(Thread.MAX_PRIORITY);
        return t;
    }

}
