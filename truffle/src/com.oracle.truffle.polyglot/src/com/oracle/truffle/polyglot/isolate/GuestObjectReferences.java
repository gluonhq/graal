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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.interop.HeapIsolationException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.polyglot.isolate.ReferenceUnavailableException.Kind;

/**
 * The {@link GuestObjectReferences} lives in the guest. It dispatches incoming messages from the
 * host (via PolyglotIsolate.guestObjectDispatch()) to the corresponding TruffleObjects.
 */
final class GuestObjectReferences implements ReflectionLibraryDispatch {

    private static final Message MESSAGE_READ_BUFFER = Message.resolveExact(InteropLibrary.class, "readBuffer", Object.class, long.class, byte[].class, int.class, int.class);
    private static final Message MESSAGE_AS_HOST_OBJECT = Message.resolveExact(InteropLibrary.class, "asHostObject", Object.class);
    private static final Message[] MESSAGES_BY_ID = InteropLibrary.getFactory().getMessages().toArray(new Message[0]);
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    /*
     * The ids must be globally unique, because the release request for a particular object
     * reference might come when the HSContext obtained by context handle is no longer the HSContext
     * that owns the object reference (via its HSObjectReferences instance). This is because context
     * handles of disposed contexts are reused. With unique object ids this is fine, because
     * references from a disposed context can be collected by the GC.
     */
    private static final AtomicLong currentReferenceId = new AtomicLong(1);

    final GuestContext guestContext;
    private final Map<Long, TruffleObject> exportById;

    GuestObjectReferences(GuestContext guestContext) {
        this.guestContext = Objects.requireNonNull(guestContext, "GuestContext must be non-null.");
        this.exportById = new ConcurrentHashMap<>();
    }

    @Override
    public void releaseReference(long objectHandle) {
        exportById.remove(objectHandle);
    }

    long registerGuestObject(TruffleObject truffleObject) {
        long id = currentReferenceId.getAndIncrement();
        exportById.put(id, truffleObject);
        return id;
    }

    TruffleObject getObject(long objId) {
        return exportById.get(objId);
    }

    void releaseAllReferences() {
        exportById.clear();
    }

    @Override
    public Object dispatch(long objId, int messageId, Object[] args) throws Exception {
        Message message = MESSAGES_BY_ID[messageId];

        // get the guest object
        TruffleObject receiver = exportById.get(objId);
        if (receiver == null) {
            throw new ReferenceUnavailableException(Kind.GUEST, objId);
        }
        if (messageId == MESSAGE_AS_HOST_OBJECT.getId() && !InteropLibrary.getUncached(receiver).isException(receiver)) {
            throw HeapIsolationException.create();
        }
        if (messageId == MESSAGE_READ_BUFFER.getId()) {
            int byteLength = (Integer) args[3];
            args[1] = new byte[Math.max(byteLength, 0)];
            args[2] = 0;
        }

        // enter the guest object context if needed

        Object contextReceiver = guestContext.polyglotContextReceiver;
        boolean enterNeeded = !PolyglotIsolateAccessor.ENGINE.isContextEntered(contextReceiver);
        Object prev = null;
        if (enterNeeded) {
            prev = PolyglotIsolateAccessor.ENGINE.enterInternalContext(null, contextReceiver);
        }
        try {
            ReflectionLibrary reflectionLibrary = ReflectionLibrary.getFactory().getUncached(receiver);
            Object[] useArgs;
            if (args == null) {
                useArgs = EMPTY_OBJECT_ARRAY;
            } else {
                useArgs = args;
            }
            Object result = reflectionLibrary.send(receiver, message, useArgs);
            if (messageId == MESSAGE_READ_BUFFER.getId()) {
                result = args[1];
            }
            return result;
        } finally {
            if (enterNeeded) {
                PolyglotIsolateAccessor.ENGINE.leaveInternalContext(null, contextReceiver, prev);
            }
        }
    }
}
