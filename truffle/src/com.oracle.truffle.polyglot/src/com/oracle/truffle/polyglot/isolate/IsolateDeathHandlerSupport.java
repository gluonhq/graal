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

import org.graalvm.nativebridge.IsolateDeathException;

import java.io.IOException;

final class IsolateDeathHandlerSupport {

    private IsolateDeathHandlerSupport() {
    }

    static RuntimeException createCancelledPolyglotException(ForeignEngine foreignEngine, IsolateDeathException isolateDeath) {
        Object localEngineReceiver = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getEngineReceiver(foreignEngine.getLocalEngine());
        return PolyglotIsolateAccessor.ENGINE.wrapGuestException(localEngineReceiver, createCancelledException(isolateDeath));
    }

    static RuntimeException createCancelledPolyglotException(ForeignContext foreignContext, IsolateDeathException isolateDeath) {
        Object localContextReceiver = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
        return PolyglotIsolateAccessor.ENGINE.wrapGuestException(localContextReceiver, createCancelledException(isolateDeath));
    }

    static Error createCancelledException(IsolateDeathException isolateDeath) {
        return PolyglotIsolateAccessor.ENGINE.createCancelExecution(null, "Execution cancelled due to polyglot isolate disconnection. Details: " + isolateDeath.getMessage(), false);
    }

    static final class AsCancelledException {

        private AsCancelledException() {
        }

        @SuppressWarnings("unused")
        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            throw createCancelledException(isolateDeath);
        }
    }

    static final class AsCancelledPolyglotException {

        private AsCancelledPolyglotException() {
        }

        @SuppressWarnings("unused")
        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            throw PolyglotIsolateAccessor.ENGINE.wrapGuestException(PolyglotIsolateHostSupport.getPolyglot(), createCancelledException(isolateDeath));
        }
    }

    static final class AsCancelledOrPolyglotException {

        private AsCancelledOrPolyglotException() {
        }

        @SuppressWarnings("unused")
        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            if (PolyglotIsolateGuestSupport.isHost()) {
                // Host
                throw PolyglotIsolateAccessor.ENGINE.wrapGuestException(PolyglotIsolateHostSupport.getPolyglot(), createCancelledException(isolateDeath));
            } else {
                // Guest
                throw createCancelledException(isolateDeath);
            }
        }
    }

    static final class AsIOException {

        private AsIOException() {
        }

        @SuppressWarnings("unused")
        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) throws IOException {
            throw new IOException("Polyglot isolate disconnected.", isolateDeath);
        }
    }

    static final class KeepIsolateDeathException {

        private KeepIsolateDeathException() {
        }

        @SuppressWarnings("unused")
        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            throw isolateDeath;
        }
    }
}
