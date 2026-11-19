/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.BaseTypeCache;

import javax.lang.model.type.DeclaredType;

class NativeBridgeTypeCache extends BaseTypeCache {
    final DeclaredType alwaysByLocalReference;
    final DeclaredType alwaysByLocalReferenceRepeated;
    final DeclaredType alwaysByRemoteReference;
    final DeclaredType alwaysByRemoteReferenceRepeated;

    final DeclaredType binaryMarshaller;
    final DeclaredType binaryInput;
    final DeclaredType binaryOutput;

    final DeclaredType byLocalReference;
    final DeclaredType byRemoteReference;
    final DeclaredType byteArrayBinaryOutput;

    final DeclaredType customDispatchAccessor;
    final DeclaredType customDispatchFactory;
    final DeclaredType customReceiverAccessor;

    final DeclaredType foreignException;
    final DeclaredType foreignObject;
    final DeclaredType idempotent;
    final DeclaredType in;
    final DeclaredType isolate;
    final DeclaredType isolateCreateException;
    final DeclaredType isolateDeathException;
    final DeclaredType isolateDeathHandler;
    final DeclaredType marshallerAnnotation;
    final DeclaredType marshallerConfig;
    final DeclaredType mutablePeer;
    final DeclaredType noImplementation;

    final DeclaredType out;

    final DeclaredType peer;
    final DeclaredType receiverMethod;
    final DeclaredType referenceHandles;

    final DeclaredType typeLiteral;

    NativeBridgeTypeCache(AbstractProcessor processor) {
        super(processor);
        this.alwaysByLocalReference = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByLocalReference");
        this.alwaysByLocalReferenceRepeated = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByLocalReferenceRepeated");
        this.alwaysByRemoteReference = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByRemoteReference");
        this.alwaysByRemoteReferenceRepeated = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByRemoteReferenceRepeated");
        this.binaryMarshaller = processor.getDeclaredType("org.graalvm.nativebridge.BinaryMarshaller");
        this.binaryInput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryInput");
        this.binaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput");
        this.byLocalReference = processor.getDeclaredType("org.graalvm.nativebridge.ByLocalReference");
        this.byRemoteReference = processor.getDeclaredType("org.graalvm.nativebridge.ByRemoteReference");
        this.byteArrayBinaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput.ByteArrayBinaryOutput");
        this.customDispatchAccessor = processor.getDeclaredType("org.graalvm.nativebridge.CustomDispatchAccessor");
        this.customReceiverAccessor = processor.getDeclaredType("org.graalvm.nativebridge.CustomReceiverAccessor");
        this.customDispatchFactory = processor.getDeclaredType("org.graalvm.nativebridge.CustomDispatchFactory");
        this.foreignException = processor.getDeclaredType("org.graalvm.nativebridge.ForeignException");
        this.foreignObject = processor.getDeclaredType("org.graalvm.nativebridge.ForeignObject");
        this.idempotent = processor.getDeclaredType("org.graalvm.nativebridge.Idempotent");
        this.in = processor.getDeclaredType("org.graalvm.nativebridge.In");
        this.isolate = processor.getDeclaredType("org.graalvm.nativebridge.Isolate");
        this.isolateCreateException = processor.getDeclaredType("org.graalvm.nativebridge.IsolateCreateException");
        this.isolateDeathException = processor.getDeclaredType("org.graalvm.nativebridge.IsolateDeathException");
        this.isolateDeathHandler = processor.getDeclaredType("org.graalvm.nativebridge.IsolateDeathHandler");
        this.marshallerAnnotation = processor.getDeclaredType("org.graalvm.nativebridge.MarshallerAnnotation");
        this.marshallerConfig = processor.getDeclaredType("org.graalvm.nativebridge.MarshallerConfig");
        this.mutablePeer = processor.getDeclaredType("org.graalvm.nativebridge.MutablePeer");
        this.noImplementation = processor.getDeclaredType("org.graalvm.nativebridge.NoImplementation");
        this.out = processor.getDeclaredType("org.graalvm.nativebridge.Out");
        this.peer = processor.getDeclaredType("org.graalvm.nativebridge.Peer");
        this.receiverMethod = processor.getDeclaredType("org.graalvm.nativebridge.ReceiverMethod");
        this.referenceHandles = processor.getDeclaredType("org.graalvm.nativebridge.ReferenceHandles");
        this.typeLiteral = processor.getDeclaredType("org.graalvm.nativebridge.TypeLiteral");
    }
}
