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

import org.graalvm.jniutils.JNI.JavaVM;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.ForeignException;
import org.graalvm.nativebridge.JNIClassCache;
import org.graalvm.word.WordFactory;

final class JNIIsolatePlatform implements PolyglotIsolateGuestSupport.Platform, PolyglotIsolateGuestSupport.TearDownHook {

    private final JavaVM javaVM;

    JNIIsolatePlatform() {
        JNIEnv jniEnv = JNIMethodScope.env();
        this.javaVM = JNIUtil.GetJavaVM(jniEnv);
        PolyglotIsolateGuestSupport.registerTearDownHook(Integer.MIN_VALUE, this);
    }

    @Override
    public boolean isCurrentThreadAttached() {
        return JNIMethodScope.scopeOrNull() != null;
    }

    @Override
    public boolean attachCurrentThread() {
        boolean result = false;
        Thread currentThread = Thread.currentThread();
        JNIEnv threadJniEnv = JNIUtil.GetEnv(javaVM);
        if (threadJniEnv.isNull()) {
            threadJniEnv = JNIUtil.attachCurrentThread(javaVM, currentThread.isDaemon(), currentThread.getName(), WordFactory.nullPointer());
            result = true;
        }
        if (threadJniEnv.isNonNull()) {
            ForeignException.openJNIMethodScope(currentThread.getName(), threadJniEnv);
        } else {
            throw new AssertionError(String.format("Failed to attach thread %s to host VM.", currentThread));
        }
        return result;
    }

    @Override
    public void detachCurrentThread(boolean detachFromHost) {
        JNIMethodScope scope = JNIMethodScope.scopeOrNull();
        if (scope != null) {
            scope.close();
            if (detachFromHost) {
                JNIUtil.DetachCurrentThread(javaVM);
            }
        }
    }

    @Override
    public void tearDown() {
        JNIClassCache.dispose(JNIMethodScope.env());
    }
}
