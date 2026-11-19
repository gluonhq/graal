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

import org.graalvm.nativebridge.ByRemoteReference;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.IsolateDeathHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@GenerateNativeToHotSpotBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
@IsolateDeathHandler(IsolateDeathHandlerSupport.AsCancelledException.class)
abstract class ForeignProcess extends Process implements ForeignObject {

    @Override
    @ByRemoteReference(ForeignProcess.class)
    public abstract Process destroyForcibly();

    @Override
    @ByRemoteReference(ForeignInputStream.class)
    public abstract InputStream getInputStream();

    @Override
    @ByRemoteReference(ForeignInputStream.class)
    public abstract InputStream getErrorStream();

    @Override
    @ByRemoteReference(ForeignOutputStream.class)
    public abstract OutputStream getOutputStream();

    @Override
    public final CompletableFuture<Process> onExit() {
        return super.onExit();
    }

    @Override
    public final ProcessHandle.Info info() {
        return super.info();
    }

    @Override
    public final Stream<ProcessHandle> children() {
        return super.children();
    }

    @Override
    public final Stream<ProcessHandle> descendants() {
        return super.descendants();
    }

    @SuppressWarnings("all")
    public final boolean waitFor(Duration duration) throws InterruptedException {
        return waitFor(TimeUnit.NANOSECONDS.convert(duration), TimeUnit.NANOSECONDS);
    }
}
