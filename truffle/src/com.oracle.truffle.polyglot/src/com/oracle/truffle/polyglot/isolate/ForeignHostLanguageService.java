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

import static org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;

import java.lang.reflect.Type;
import java.util.function.Predicate;

import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.IsolateDeathHandler;
import org.graalvm.nativebridge.MutablePeer;
import org.graalvm.nativebridge.ReceiverMethod;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.HostContext;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.InteropObject;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.TruffleFile;

@GenerateNativeToHotSpotBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
@IsolateDeathHandler(IsolateDeathHandlerSupport.AsCancelledException.class)
@MutablePeer
abstract class ForeignHostLanguageService extends AbstractHostLanguageService implements ForeignObject {

    ForeignHostLanguageService() {
        super(PolyglotIsolateHostSupport.getPolyglot());
    }

    // used by PolyglotValueDispatch, needed by getBindings()
    @Override
    public final boolean isHostProxy(Object obj) {
        if (obj instanceof HSTruffleObject || obj instanceof HSTruffleException) {
            return isHostProxyImpl(obj);
        }
        return false;
    }

    @TruffleBoundary
    @ReceiverMethod("isHostProxy")
    abstract boolean isHostProxyImpl(@InteropObject Object obj);

    // used by TruffleLanguage.Env.lookupHostSymbol(String)
    @Override
    @TruffleBoundary
    @InteropObject
    public abstract Object findStaticClass(@HostContext Object context, String classValue);

    // used by TruffleLanguage.Env.createHostAdapter(Object[])
    @Override
    @TruffleBoundary
    @InteropObject
    public abstract Object createHostAdapter(@HostContext Object context, @InteropObject Object[] types, @InteropObject Object classOverrides);

    // used by TruffleLanguage.Env.addToHostClassPath(TruffleFile)
    @Override
    @TruffleBoundary
    public abstract void addToHostClassPath(@HostContext Object context, @TruffleFile Object truffleFile);

    @Override
    public final void initializeHostContext(Object internalContext, Object context, Object hostAccess, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        /*
         * There is only one host context on the host side - the one that corresponds to the outer
         * context.
         */
        ((GuestHostLanguage.GuestHostLanguageContext) context).internalOuterContext = PolyglotIsolateAccessor.ENGINE.getOuterContext(internalContext);
    }

    @Override
    @TruffleBoundary
    @InteropObject
    public abstract RuntimeException toHostException(@HostContext Object hostContext, Throwable exception);

    @Override
    public final int findNextGuestToHostStackTraceElement(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
        int result = super.findNextGuestToHostStackTraceElement(firstElement, hostStack, nextElementIndex);
        if (result == -1) {
            return result;
        }
        int index = nextElementIndex + result;
        StackTraceElement element = hostStack[index];
        // Ignore host Truffle stack frames to isolate to host boundary
        while (!isIsolateToHostCall(element) && index < hostStack.length) {
            element = hostStack[index++];
        }
        if (index < hostStack.length) {
            return index - nextElementIndex;
        } else {
            return -1;
        }
    }

    @Override
    public final int findNextHostToGuestStackTraceElement(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
        StackTraceElement element = firstElement;
        int index = nextElementIndex;
        if (isHostToIsolateCall(element)) {
            do {
                element = hostStack[index++];
            } while (!isHostToGuest(element) && index < hostStack.length);
        }
        int result = super.findNextHostToGuestStackTraceElement(element, hostStack, index);
        return result + (index - nextElementIndex);
    }

    private static boolean isIsolateToHostCall(StackTraceElement element) {
        return HSTruffleObject.class.getName().equals(element.getClassName()) && "send".equals(element.getMethodName());
    }

    private static boolean isHostToIsolateCall(StackTraceElement element) {
        return NativeTruffleObject.class.getName().equals(element.getClassName()) && "send".equals(element.getMethodName());
    }

    @Override
    public final Object asHostStaticClass(Object context, Class<?> value) {
        // used by TruffleLanguage.Env.asHostSymbol(Class<?>)
        throw unsupported();
    }

    @Override
    public final Object toHostObject(Object context, Object value) {
        // used by TruffleLanguage.Env.asBoxedGuestValue(Object) to box primitive values.
        throw unsupported();
    }

    @Override
    public final Object toGuestValue(Object context, Object hostValue, boolean asValue) {
        if (isGuestPrimitive(hostValue) || hostValue instanceof TruffleObject) {
            return hostValue;
        } else {
            throw unsupported();
        }
    }

    @Override
    public final Object migrateValue(Object hostContext, Object value, Object valueContext) {
        // used by PolyglotContextImpl.migrateValue(Node, Object, PolyglotContextImpl)
        if (InteropLibrary.getUncached(value).isHostObject(value)) {
            throw CompilerDirectives.shouldNotReachHere();
        }
        if (value instanceof TruffleObject) {
            if (valueContext == null) {
                throw CompilerDirectives.shouldNotReachHere();
            } else {
                return null;
            }
        } else {
            assert InteropLibrary.isValidValue(value);
            return value;
        }
    }

    @Override
    @TruffleBoundary
    public abstract boolean allowsPublicAccess();

    // ---------------- below methods not needed in TruffleIsolate

    @Override
    public final Object asHostDynamicClass(Object context, Class<?> value) {
        // method in supertype not used at all
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final Object findDynamicClass(Object context, String classValue) {
        // used by com.oracle.truffle.host.HostLanguage.parse(ParsingRequest) -> not needed
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final <T> T toHostType(Object hostNode, Object targetNode, Object hostContext, Object value, Class<T> targetType, Type genericType) {
        // used by PolyglotToHostNode
        // and by PolyglotValueDispatch.PrimitiveValue.as(Object, Object, Class<T>)
        // not needed by TruffleIsolate
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final Object unboxProxyObject(Object hostValue) {
        // used by PolyglotValueDispatch
        // not needed by TruffleIsolate
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final void pin(Object receiver) {
        // used by PolyglotValueDispatch
        // not needed by TruffleIsolate
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final void release() {
        setPeer(null);
    }

    private static boolean isGuestPrimitive(Object receiver) {
        return receiver instanceof Integer || receiver instanceof Double //
                        || receiver instanceof Long || receiver instanceof Float //
                        || receiver instanceof Boolean || receiver instanceof Character //
                        || receiver instanceof Byte || receiver instanceof Short //
                        || receiver instanceof String;
    }

    private static RuntimeException unsupported() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException("Not supported in a spawned isolate.");
    }
}
