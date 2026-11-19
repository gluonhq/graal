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

import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.NativeBridgeSupport;

import java.io.PrintStream;

public final class PolyglotIsolateBridgeSupport implements NativeBridgeSupport {

    private static final String JNI_POLYGLOT_ISOLATE_TRACE_LEVEL_ENV_NAME = "JNI_POLYGLOT_TRACE_LEVEL";

    private volatile Integer traceLevel;

    @Override
    public String getFeatureName() {
        return "TRUFFLE-ISOLATE";
    }

    @Override
    public boolean isTracingEnabled(int level) {
        return traceLevel() >= level;
    }

    @Override
    public void trace(String message) {
        // We cannot use engine logger here.
        // 1) Trace is called before engine is created.
        // 2) Logger is redirected to host, using logger will cause endless recursion.
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(Thread.currentThread().getName()).append(']');
        JNIMethodScope scope = JNIMethodScope.scopeOrNull();
        if (scope != null) {
            sb.append(new String(new char[2 + (scope.depth() * 2)]).replace('\0', ' '));
        }
        sb.append(message);
        PrintStream out = System.err;
        out.println(sb);
    }

    private int traceLevel() {
        if (traceLevel == null) {
            String var = System.getenv(JNI_POLYGLOT_ISOLATE_TRACE_LEVEL_ENV_NAME);
            if (var != null) {
                try {
                    traceLevel = Integer.parseInt(var);
                } catch (NumberFormatException e) {
                    PrintStream out = System.err;
                    out.printf("Invalid value for %s: %s%n", JNI_POLYGLOT_ISOLATE_TRACE_LEVEL_ENV_NAME, e);
                    traceLevel = 0;
                }
            } else {
                traceLevel = 0;
            }
        }
        return traceLevel;
    }

}
