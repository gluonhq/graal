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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.Message;
import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativebridge.BinaryMarshaller;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.nativebridge.ForeignException;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.IsolateDeathException;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ProcessIsolate;
import org.graalvm.nativebridge.ProcessIsolateThread;
import org.graalvm.nativebridge.ProcessPeer;
import org.graalvm.nativebridge.ReferenceHandles;

/**
 * Optimized {@link ReflectionLibraryDispatch} implementation that uses UNIX domain sockets for
 * inter-process communication. This hand-written implementation avoids the need for an
 * argument/result marshaller, which would otherwise perform context lookups for each argument and
 * return value.
 */
final class ProcessReflectionLibrarySupport {

    private static final int DISPATCH_ID = 0;
    private static final Message MESSAGE_AS_HOST_OBJECT = Message.resolveExact(InteropLibrary.class, "asHostObject", Object.class);
    private static final BinaryMarshaller<Throwable> THROWABLE_MARSHALLER = PolyglotMarshallerConfig.getInstance().lookupMarshaller(Throwable.class);

    private ProcessReflectionLibrarySupport() {
    }

    static ReflectionLibraryDispatch createOptimizedHostToGuestDispatch(ForeignContext context, ForeignReflectionLibraryDispatch base) {
        return new HostToGuestStartPoint(context, base);
    }

    static ReflectionLibraryDispatch createOptimizedGuestToHostDispatch(GuestContext context, ForeignReflectionLibraryDispatch dispatch) {
        return new GuestToHostStartPoint(context, dispatch);
    }

    @SuppressWarnings("unused")
    static BinaryOutput dispatch(int messageId, ProcessIsolate processIsolate, BinaryInput binaryInput) throws Throwable {
        if ((messageId & 0xffff) == DISPATCH_ID) {
            return EndPoint.dispatch(binaryInput);
        } else {
            throw new IllegalArgumentException("Unknown message id " + messageId);
        }
    }

    /**
     * Base implementation of the process-based {@link ReflectionLibraryDispatch} used for both
     * guest-to-host and host-to-guest communication.
     * <p>
     * The two directions differ only in how {@code TruffleObject} references are read and written.
     * This class contains the shared logic common to both.
     */
    private abstract static class AbstractStartPoint implements ForeignObject, ReflectionLibraryDispatch {

        private static final int SERVICE_SCOPE = PolyglotIsolateForeignFactoryGen.lookupServiceId(ProcessReflectionLibrarySupport.class) << 16;

        private final ProcessPeer peer;
        private final ReflectionLibraryDispatch delegate;

        AbstractStartPoint(ForeignReflectionLibraryDispatch delegate) {
            this.peer = (ProcessPeer) delegate.getPeer();
            this.delegate = delegate;
        }

        @Override
        public Peer getPeer() {
            return peer;
        }

        @Override
        public void setPeer(Peer newPeer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final void releaseReference(long objectHandle) {
            delegate.releaseReference(objectHandle);
        }

        abstract void writeArgument(BinaryOutput out, Object argument);

        abstract Object readResult(BinaryInput in);

        @Override
        public final Object dispatch(long objectHandle, int messageId, Object[] args) throws Exception {
            ProcessIsolate processIsolate = peer.getIsolate();
            ProcessIsolateThread processIsolateThread = processIsolate.enter();
            try {
                /*
                 * An estimate of the BinaryOutput size. If the message doesn't fit within this
                 * estimate, a larger buffer is allocated and the content is copied into it. The
                 * purpose of the size estimate is to minimize buffer reallocations by using this
                 * hint effectively.
                 */
                int marshalledParametersSizeEstimate = 3 * Integer.BYTES + 2 * Long.BYTES + args.length * Long.BYTES;
                BinaryOutput.ByteArrayBinaryOutput marshalledParametersOutput = BinaryOutput.ByteArrayBinaryOutput.create(marshalledParametersSizeEstimate);
                marshalledParametersOutput.writeInt(SERVICE_SCOPE | DISPATCH_ID);
                marshalledParametersOutput.writeLong(peer.getHandle());
                marshalledParametersOutput.writeLong(objectHandle);
                marshalledParametersOutput.writeInt(messageId);
                marshalledParametersOutput.writeInt(args.length);
                for (int i = 0; i < args.length; i++) {
                    writeArgument(marshalledParametersOutput, args[i]);
                }
                BinaryInput marshalledResult;
                try {
                    marshalledResult = processIsolateThread.sendAndReceive(marshalledParametersOutput);
                } catch (IsolateDeathException isolateDeathException) {
                    IsolateDeathHandlerSupport.AsCancelledException.handleIsolateDeath(this, isolateDeathException);
                    throw new AssertionError("Should not reach here.");
                } catch (ForeignException foreignException) {
                    throw foreignException.throwOriginalException(processIsolate, THROWABLE_MARSHALLER);
                }
                if (MESSAGE_AS_HOST_OBJECT.getId() == messageId) {
                    // Support asHostObject for exceptions using the slower but fully featured
                    // Throwable marshaller.
                    return THROWABLE_MARSHALLER.read(peer.getIsolate(), marshalledResult);
                }
                return readResult(marshalledResult);
            } finally {
                processIsolateThread.leave();
            }
        }
    }

    private static final class HostToGuestStartPoint extends AbstractStartPoint {

        private final ForeignContext context;

        HostToGuestStartPoint(ForeignContext context, ForeignReflectionLibraryDispatch delegate) {
            super(delegate);
            this.context = context;
        }

        @Override
        void writeArgument(BinaryOutput out, Object argument) {
            BinaryProtocol.writeHostTypedValue(out, argument, context.getGuestToHostReceiver());
        }

        @Override
        Object readResult(BinaryInput in) {
            return BinaryProtocol.readHostTypedValue(in, context);
        }
    }

    private static final class GuestToHostStartPoint extends AbstractStartPoint {

        private final GuestContext context;

        GuestToHostStartPoint(GuestContext context, ForeignReflectionLibraryDispatch delegate) {
            super(delegate);
            this.context = context;
        }

        @Override
        void writeArgument(BinaryOutput out, Object argument) {
            BinaryProtocol.writeGuestTypedValue(out, argument, context.hostToGuestObjectReferences);
        }

        @Override
        Object readResult(BinaryInput in) {
            return BinaryProtocol.readGuestTypedValue(in, context);
        }
    }

    private static final class EndPoint {

        static BinaryOutput dispatch(BinaryInput binaryInput) throws Throwable {
            ReflectionLibraryDispatch receiverObject = ReferenceHandles.resolve(binaryInput.readLong(), ReflectionLibraryDispatch.class);
            long objectHandle = binaryInput.readLong();
            int messageId = binaryInput.readInt();
            int argsCount = binaryInput.readInt();
            boolean isHost = PolyglotIsolateGuestSupport.isHost();
            Object[] args = argsCount == 0 ? null : new Object[argsCount];
            if (isHost) {
                ForeignContext foreignContext = ((HostObjectReferences) receiverObject).foreignContext;
                for (int i = 0; i < argsCount; i++) {
                    args[i] = BinaryProtocol.readHostTypedValue(binaryInput, foreignContext);
                }
                Object endPointResult = receiverObject.dispatch(objectHandle, messageId, args);
                BinaryOutput binaryOutput = BinaryOutput.claimBuffer(binaryInput);
                if (MESSAGE_AS_HOST_OBJECT.getId() == messageId) {
                    // Support asHostObject for exceptions using the slower but fully
                    // featured Throwable marshaller.
                    THROWABLE_MARSHALLER.write(binaryOutput, (Throwable) endPointResult);
                } else {
                    BinaryProtocol.writeHostTypedValue(binaryOutput, endPointResult, foreignContext.getGuestToHostReceiver());
                }
                return binaryOutput;
            } else {
                GuestContext guestContext = ((GuestObjectReferences) receiverObject).guestContext;
                for (int i = 0; i < argsCount; i++) {
                    args[i] = BinaryProtocol.readGuestTypedValue(binaryInput, guestContext);
                }
                Object endPointResult = receiverObject.dispatch(objectHandle, messageId, args);
                BinaryOutput binaryOutput = BinaryOutput.claimBuffer(binaryInput);
                BinaryProtocol.writeGuestTypedValue(binaryOutput, endPointResult, guestContext.hostToGuestObjectReferences);
                return binaryOutput;
            }
        }
    }
}
