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
import org.graalvm.nativebridge.CustomDispatchAccessor;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.GenerateNativeToNativeBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.Idempotent;
import org.graalvm.nativebridge.CustomReceiverAccessor;
import org.graalvm.nativebridge.IsolateDeathException;
import org.graalvm.nativebridge.IsolateDeathHandler;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentDispatch;

@GenerateHotSpotToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateNativeToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
@IsolateDeathHandler(ForeignInstrumentDispatch.AsCancelledPolyglotException.class)
abstract class ForeignInstrumentDispatch extends AbstractInstrumentDispatch {

    @SuppressWarnings("unused")
    ForeignInstrumentDispatch(AbstractPolyglotImpl polyglot) {
        super(polyglot);
    }

    @Override
    public final <T> T lookup(Object receiver, Class<T> type) {
        return null;
    }

    @Override
    @Idempotent
    public abstract @ByRemoteReference(ForeignOptionDescriptors.class) OptionDescriptors getOptions(Object receiver);

    @Override
    @Idempotent
    public abstract @ByRemoteReference(ForeignOptionDescriptors.class) OptionDescriptors getSourceOptions(Object receiver);

    @Override
    public abstract boolean equals(Object impl, @ByRemoteReference(value = ForeignObject.class, useCustomReceiverAccessor = true) Object otherImpl);

    @CustomDispatchAccessor
    static AbstractInstrumentDispatch resolveForeignDelegate(Object instrument) {
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getInstrumentDispatch(instrument);
    }

    @CustomReceiverAccessor
    static Object resolveReceiver(Object instrument) {
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getInstrumentReceiver(instrument);
    }

    static final class AsCancelledPolyglotException {

        private AsCancelledPolyglotException() {
        }

        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            throw IsolateDeathHandlerSupport.createCancelledPolyglotException(((ForeignInstrument) receiver).getForeignEngine(), isolateDeath);
        }
    }
}
