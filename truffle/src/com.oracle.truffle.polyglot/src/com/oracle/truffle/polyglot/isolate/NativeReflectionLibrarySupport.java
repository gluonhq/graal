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
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JValue;
import org.graalvm.jniutils.JNICalls.JNIMethod;
import org.graalvm.jniutils.JNIEntryPoint;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativebridge.BinaryMarshaller;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.nativebridge.ForeignException;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.HSPeer;
import org.graalvm.nativebridge.JNIClassCache;
import org.graalvm.nativebridge.NativeIsolateThread;
import org.graalvm.nativebridge.NativePeer;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ReferenceHandles;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.util.Objects;

/**
 * Optimized {@link ReflectionLibraryDispatch} implementation that uses JNI for foreign isolate
 * calls.
 */
final class NativeReflectionLibrarySupport {

    private static final int STATIC_BUFFER_SIZE = 4096;
    private static final BinaryMarshaller<Throwable> THROWABLE_MARSHALLER = PolyglotMarshallerConfig.getInstance().lookupMarshaller(Throwable.class);
    private static final Message MESSAGE_AS_HOST_OBJECT = Message.resolveExact(InteropLibrary.class, "asHostObject", Object.class);

    static ReflectionLibraryDispatch createOptimizedHostToGuestDispatch(ForeignContext context, ForeignReflectionLibraryDispatch base) {
        return new NativeHostToGuestDispatchStartPoint(context, base);
    }

    static ReflectionLibraryDispatch createOptimizedGuestToHostDispatch(GuestContext context, ForeignReflectionLibraryDispatch dispatch, long hostStackSpaceHeadroom) {
        return new NativeGuestToHostDispatchStartPoint(context, dispatch, hostStackSpaceHeadroom);
    }

    private static final class NativeHostToGuestDispatchStartPoint implements ForeignObject, ReflectionLibraryDispatch {

        private final ForeignContext context;
        private final ForeignReflectionLibraryDispatch reflectionLibraryService;
        private final NativePeer peer;

        NativeHostToGuestDispatchStartPoint(ForeignContext context, ForeignReflectionLibraryDispatch reflectionLibraryService) {
            this.context = context;
            this.reflectionLibraryService = Objects.requireNonNull(reflectionLibraryService, "reflectionLibraryService must be non-null");
            this.peer = (NativePeer) reflectionLibraryService.getPeer();
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
        public void releaseReference(long handle) {
            reflectionLibraryService.releaseReference(handle);
        }

        @Override
        public Object dispatch(long objectHandle, int messageId, Object[] args) {
            NativeIsolateThread isolateThread = peer.getIsolate().enter();
            boolean hasArgs = args.length > 0;
            byte[] serializedArgs;
            int serializedArgsLength;
            if (hasArgs) {
                BinaryOutput.ByteArrayBinaryOutput out = BinaryOutput.create();
                BinaryProtocol.writeHostTypedValue(out, args, context.getGuestToHostReceiver());
                serializedArgs = out.getArray();
                serializedArgsLength = out.getPosition();
            } else {
                serializedArgs = null;
                serializedArgsLength = 0;
            }
            byte[] serializedRetVal;
            try {
                int bufferSize = serializedArgs == null ? 0 : serializedArgs.length;
                serializedRetVal = guestObjectDispatch(isolateThread.getIsolateThreadId(), peer.getHandle(), objectHandle, messageId, serializedArgs, serializedArgsLength, bufferSize);
            } catch (ForeignException e) {
                throw e.throwOriginalException(isolateThread.getIsolate(), THROWABLE_MARSHALLER);
            } finally {
                isolateThread.leave();
            }
            if (serializedRetVal == null) {
                return null;
            } else {
                BinaryInput in = BinaryInput.create(serializedRetVal);
                boolean success = in.readBoolean();
                if (success && MESSAGE_AS_HOST_OBJECT.getId() == messageId) {
                    // Support asHostObject for exceptions using the slower but fully featured
                    // Throwable marshaller.
                    return THROWABLE_MARSHALLER.read(peer.getIsolate(), in);
                }
                Object result = BinaryProtocol.readHostTypedValue(in, context);
                if (success) {
                    return result;
                } else {
                    throw throwUnchecked(RuntimeException.class, (Throwable) result);
                }
            }
        }

        @SuppressWarnings({"unchecked", "unused"})
        private static <T extends Throwable> T throwUnchecked(Class<T> exceptionClass, Throwable exception) throws T {
            throw (T) exception;
        }

        private native byte[] guestObjectDispatch(long isolateThreadId, long contextId, long objId, int messageId,
                        byte[] serializedArguments, int serializedArgumentsLength, int bufferSize);
    }

    @SuppressWarnings("unused")
    private static final class NativeHostToGuestDispatchEndPoint {

        @CEntryPoint(name = "Java_com_oracle_truffle_polyglot_isolate_NativeReflectionLibrarySupport_00024NativeHostToGuestDispatchStartPoint_guestObjectDispatch", include = PolyglotIsolateGuestFeatureEnabled.class)
        @SuppressWarnings({"unused", "try"})
        static JByteArray guestObjectDispatch(JNIEnv jniEnv, JClass clazz, @CEntryPoint.IsolateThreadContext long isolateId, long objectReferencesHandle,
                        long objectId, int messageId, JNI.JByteArray serializedArgs, int serializedArgsLength, int bufferSize) {
            CCharPointer staticBuffer = StackValue.get(STATIC_BUFFER_SIZE);
            CCharPointer useBuffer = WordFactory.nullPointer();
            PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
            JNIMethodScope scope = ForeignException.openJNIMethodScope("PolyglotIsolate::guestObjectDispatch", jniEnv);
            try (JNIMethodScope s = scope) {
                int useBufferLen;
                if (serializedArgsLength < STATIC_BUFFER_SIZE) {
                    useBuffer = staticBuffer;
                    useBufferLen = STATIC_BUFFER_SIZE;
                } else {
                    useBuffer = UnmanagedMemory.malloc(serializedArgsLength);
                    useBufferLen = serializedArgsLength;
                }
                Object[] args;
                GuestObjectReferences guestObjectReferences = ReferenceHandles.resolve(objectReferencesHandle, GuestObjectReferences.class);
                if (serializedArgsLength != 0) {
                    JNIUtil.GetByteArrayRegion(jniEnv, serializedArgs, 0, serializedArgsLength, useBuffer);
                    BinaryInput in = BinaryInput.create(useBuffer, serializedArgsLength);
                    args = (Object[]) BinaryProtocol.readGuestTypedValue(in, guestObjectReferences.guestContext);
                } else {
                    args = null;
                }
                boolean success = false;
                Object retval;
                try {
                    retval = guestObjectReferences.dispatch(objectId, messageId, args);
                    success = true;
                } catch (Throwable t) {
                    if (BinaryProtocol.isSupportedException(t)) {
                        retval = t;
                    } else {
                        throw t;
                    }
                }
                if (retval == null) {
                    scope.setObjectResult(WordFactory.nullPointer());
                } else {
                    try (BinaryOutput.CCharPointerBinaryOutput out = BinaryOutput.create(useBuffer, useBufferLen, useBuffer != staticBuffer)) {
                        // useBuffer is consumed by CCharPointerOutputStream
                        useBuffer = WordFactory.nullPointer();
                        out.writeBoolean(success);
                        if (success && MESSAGE_AS_HOST_OBJECT.getId() == messageId) {
                            // Support asHostObject for exceptions using the slower but fully
                            // featured Throwable marshaller.
                            THROWABLE_MARSHALLER.write(out, (Throwable) retval);
                        } else if (!success && retval instanceof HSTruffleException hsTruffleException) {
                            /*
                             * Prevent unboxing of the host exception and instead pass its isolate
                             * proxy. The isolate proxy preserves the correct guest stack, while the
                             * host exception lacks guest language frames. For interleaved stacks
                             * such as guest_code, host_code, guest_callback, host_code, we must
                             * also ensure that the exception remains alive while unwinding through
                             * the guest_callback.
                             */
                            BinaryProtocol.writeTruffleExceptionWithoutUnboxing(out, hsTruffleException, guestObjectReferences);
                        } else {
                            BinaryProtocol.writeGuestTypedValue(out, retval, guestObjectReferences);
                        }
                        int resultLen = out.getPosition();
                        JNI.JByteArray retHsArray = resultLen <= bufferSize ? serializedArgs : JNIUtil.NewByteArray(jniEnv, resultLen);
                        JNIUtil.SetByteArrayRegion(jniEnv, retHsArray, 0, resultLen, out.getAddress());
                        scope.setObjectResult(retHsArray);
                    }
                }
            } catch (Throwable t) {
                // Internal and PolyglotEngine exceptions handling
                ForeignException.forThrowable(t, THROWABLE_MARSHALLER).throwUsingJNI(jniEnv);
                scope.setObjectResult(WordFactory.nullPointer());
            } finally {
                if (useBuffer != WordFactory.nullPointer() && useBuffer != staticBuffer) {
                    UnmanagedMemory.free(useBuffer);
                }
            }
            return scope.getObjectResult();
        }
    }

    private static final class NativeGuestToHostDispatchStartPoint implements ForeignObject, ReflectionLibraryDispatch {

        static final class JNIData {

            static JNIData cache;
            final JClass endPointClass;
            private final JNIMethod guestToHostMessageDispatch;

            JNIData(JNIEnv jniEnv) {
                this.endPointClass = JNIClassCache.lookupClass(jniEnv, NativeGuestToHostDispatchEndPoint.class);
                this.guestToHostMessageDispatch = JNIMethod.findMethod(jniEnv, endPointClass, true, "messageDispatch", "(Lcom/oracle/truffle/polyglot/isolate/HostObjectReferences;JI[B)[B");
            }
        }

        private final GuestContext context;
        private final ForeignReflectionLibraryDispatch dispatch;
        private final HSPeer peer;
        private final long hostStackSpaceHeadroom;
        private final JNIData jniMethods;

        NativeGuestToHostDispatchStartPoint(GuestContext context, ForeignReflectionLibraryDispatch dispatch, long hostStackSpaceHeadroom) {
            this.context = Objects.requireNonNull(context, "Context must be non-null");
            this.dispatch = Objects.requireNonNull(dispatch, "Dispatch must be non-null");
            this.peer = (HSPeer) dispatch.getPeer();
            this.hostStackSpaceHeadroom = hostStackSpaceHeadroom;
            JNIData localJNI = JNIData.cache;
            if (localJNI == null) {
                localJNI = JNIData.cache = new JNIData(JNIMethodScope.env());
            }
            this.jniMethods = localJNI;
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
        public void releaseReference(long objectHandle) {
            dispatch.releaseReference(objectHandle);
        }

        @Override
        public Object dispatch(long objectHandle, int messageId, Object[] args) {
            JNIEnv jniEnv = JNIMethodScope.env();
            if (hostStackSpaceHeadroom != 0L && !StackPointerRetriever.fitsOnStack(hostStackSpaceHeadroom, PolyglotIsolateGuestSupport.getHostStackOverflowLimit())) {
                throw new StackOverflowError("Not enough stack space to perform a host call from isolated guest language.");
            }
            CCharPointer staticBuffer = StackValue.get(STATIC_BUFFER_SIZE);
            boolean hasArgs = args.length > 0;
            JByteArray serializedArgs;
            if (hasArgs) {
                try (BinaryOutput.CCharPointerBinaryOutput out = BinaryOutput.create(staticBuffer, STATIC_BUFFER_SIZE, false)) {
                    BinaryProtocol.writeGuestTypedValue(out, args, context.hostToGuestObjectReferences);
                    int len = out.getPosition();
                    serializedArgs = JNIUtil.NewByteArray(jniEnv, len);
                    if (serializedArgs.isNull()) {
                        throw new OutOfMemoryError("Failed to allocate marshalling buffer in the host VM.");
                    }
                    JNIUtil.SetByteArrayRegion(jniEnv, serializedArgs, 0, len, out.getAddress());
                }
            } else {
                serializedArgs = WordFactory.nullPointer();
            }
            JValue dispatchArgs = StackValue.get(4, JNI.JValue.class);
            dispatchArgs.addressOf(0).setJObject(peer.getJObject());
            dispatchArgs.addressOf(1).setLong(objectHandle);
            dispatchArgs.addressOf(2).setInt(messageId);
            dispatchArgs.addressOf(3).setJObject(serializedArgs);
            JByteArray serializedRes;
            try {
                serializedRes = ForeignException.getJNICalls().callStaticJObject(jniEnv, jniMethods.endPointClass, jniMethods.guestToHostMessageDispatch, dispatchArgs);
                /*
                 * Free the arguments JNI local reference explicitly rather than waiting for the
                 * native-to-Java transition, which may be delayed for a long time when a guest code
                 * performs many host interop calls. Each call creates a new local ref, so prompt
                 * deletion prevents unbounded growth of the local ref table and allows the GC to
                 * collect the referenced host objects sooner.
                 */
                if (serializedArgs.isNonNull()) {
                    JNIUtil.DeleteLocalRef(jniEnv, serializedArgs);
                }
            } catch (ForeignException e) {
                throw e.throwOriginalException(null, THROWABLE_MARSHALLER);
            }
            if (serializedRes.isNull()) {
                return null;
            }
            int len = JNIUtil.GetArrayLength(jniEnv, serializedRes);
            CCharPointer useBuffer;
            if (len < STATIC_BUFFER_SIZE) {
                useBuffer = staticBuffer;
            } else {
                useBuffer = UnmanagedMemory.malloc(len);
            }
            try {
                JNIUtil.GetByteArrayRegion(jniEnv, serializedRes, 0, len, useBuffer);
                BinaryInput in = BinaryInput.create(useBuffer, len);
                boolean success = in.readBoolean();
                if (success && MESSAGE_AS_HOST_OBJECT.getId() == messageId) {
                    // Support asHostObject for exceptions using the slower but fully featured
                    // Throwable marshaller.
                    return THROWABLE_MARSHALLER.read(peer.getIsolate(), in);
                }
                Object result = BinaryProtocol.readGuestTypedValue(in, context);
                if (success) {
                    return result;
                } else {
                    throw throwUnchecked(RuntimeException.class, (Throwable) result);
                }
            } finally {
                if (useBuffer != staticBuffer) {
                    UnmanagedMemory.free(useBuffer);
                }
                /*
                 * Free the result JNI local reference explicitly rather than waiting for the
                 * native-to-Java transition, which may be delayed for a long time when a guest code
                 * performs many host interop calls. Each call creates a new local ref, so prompt
                 * deletion prevents unbounded growth of the local ref table and allows the GC to
                 * collect the referenced host objects sooner.
                 */
                JNIUtil.DeleteLocalRef(jniEnv, serializedRes);
            }
        }
    }

    @SuppressWarnings("unused")
    private static final class NativeGuestToHostDispatchEndPoint {

        /*
         * Called via JNI from HSContext.
         */
        @JNIEntryPoint
        static byte[] messageDispatch(HostObjectReferences hostObjectReferences, long objectId, int messageId, byte[] serializedArgs) {
            Object[] args;
            int serializedArgsLength = serializedArgs == null ? 0 : serializedArgs.length;
            if (serializedArgsLength != 0) {
                BinaryInput in = BinaryInput.create(serializedArgs);
                args = (Object[]) BinaryProtocol.readHostTypedValue(in, hostObjectReferences.foreignContext);
            } else {
                args = null;
            }
            boolean success = false;
            Object result;
            try {
                result = hostObjectReferences.dispatch(objectId, messageId, args);
                success = true;
            } catch (Throwable t) {
                if (BinaryProtocol.isSupportedException(t)) {
                    result = t;
                } else {
                    throw throwUnchecked(RuntimeException.class, t);
                }
            }
            if (result == null) {
                return null;
            } else {
                BinaryOutput.ByteArrayBinaryOutput out = serializedArgsLength != 0 ? BinaryOutput.create(serializedArgs) : BinaryOutput.create();
                out.writeBoolean(success);
                if (success && MESSAGE_AS_HOST_OBJECT.getId() == messageId) {
                    // Support asHostObject for exceptions using the slower but fully featured
                    // Throwable marshaller.
                    THROWABLE_MARSHALLER.write(out, (Throwable) result);
                } else {
                    BinaryProtocol.writeHostTypedValue(out, result, hostObjectReferences.foreignContext.getGuestToHostReceiver());
                }
                return out.getArray();
            }
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T throwUnchecked(Class<T> exceptionClass, Throwable exception) throws T {
        throw (T) exception;
    }
}
