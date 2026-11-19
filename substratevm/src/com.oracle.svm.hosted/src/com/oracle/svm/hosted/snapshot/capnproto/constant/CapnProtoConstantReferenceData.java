/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.snapshot.capnproto.constant;

import com.oracle.svm.hosted.snapshot.c.CEntryPointLiteralReferenceData;
import com.oracle.svm.hosted.snapshot.capnproto.c.CapnProtoCEntryPointLiteralReferenceData;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference;
import com.oracle.svm.hosted.snapshot.capnproto.util.CapnProtoPrimitiveValueData;
import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.util.PrimitiveValueData;
import com.oracle.svm.shaded.org.capnproto.Void;

public final class CapnProtoConstantReferenceData {
    public static ConstantReferenceData.Writer writer(ConstantReference.Builder delegate) {
        return new ConstantReferenceWriterAdapter(delegate);
    }

    public static ConstantReferenceData.Loader loader(ConstantReference.Reader delegate) {
        return new ConstantReferenceLoaderAdapter(delegate);
    }
}

record ConstantReferenceWriterAdapter(ConstantReference.Builder delegate) implements ConstantReferenceData.Writer {
    @Override
    public ConstantReferenceData.ObjectConstant.Writer initObjectConstant() {
        return new ObjectConstantWriterAdapter(delegate.initObjectConstant());
    }

    @Override
    public PrimitiveValueData.Writer initPrimitiveValue() {
        return CapnProtoPrimitiveValueData.writer(delegate.initPrimitiveValue());
    }

    @Override
    public void setNullPointer() {
        delegate.setNullPointer(Void.VOID);
    }

    @Override
    public ConstantReferenceData.MethodPointer.Writer initMethodPointer() {
        return new MethodPointerWriterAdapter(delegate.initMethodPointer());
    }

    @Override
    public ConstantReferenceData.MethodOffset.Writer initMethodOffset() {
        return new MethodOffsetWriterAdapter(delegate.initMethodOffset());
    }

    @Override
    public CEntryPointLiteralReferenceData.Writer initCEntryPointLiteralCodePointer() {
        return CapnProtoCEntryPointLiteralReferenceData.writer(delegate.initCEntryPointLiteralCodePointer());
    }

    @Override
    public void setCGlobalDataBasePointer() {
        delegate.setCGlobalDataBasePointer(Void.VOID);
    }

    @Override
    public void setNotMaterialized() {
        delegate.setNotMaterialized(Void.VOID);
    }
}

record ConstantReferenceLoaderAdapter(ConstantReference.Reader delegate) implements ConstantReferenceData.Loader {
    @Override
    public ConstantReferenceData.Kind kind() {
        return switch (delegate.which()) {
            case OBJECT_CONSTANT -> ConstantReferenceData.Kind.OBJECT_CONSTANT;
            case NULL_POINTER -> ConstantReferenceData.Kind.NULL_POINTER;
            case PRIMITIVE_VALUE -> ConstantReferenceData.Kind.PRIMITIVE_VALUE;
            case METHOD_POINTER -> ConstantReferenceData.Kind.METHOD_POINTER;
            case METHOD_OFFSET -> ConstantReferenceData.Kind.METHOD_OFFSET;
            case C_ENTRY_POINT_LITERAL_CODE_POINTER -> ConstantReferenceData.Kind.C_ENTRY_POINT_LITERAL_CODE_POINTER;
            case C_GLOBAL_DATA_BASE_POINTER -> ConstantReferenceData.Kind.C_GLOBAL_DATA_BASE_POINTER;
            case NOT_MATERIALIZED -> ConstantReferenceData.Kind.NOT_MATERIALIZED;
            case _NOT_IN_SCHEMA -> throw new IllegalStateException("Unexpected constant reference kind");
        };
    }

    @Override
    public boolean isObjectConstant() {
        return delegate.isObjectConstant();
    }

    @Override
    public boolean isNotMaterialized() {
        return delegate.isNotMaterialized();
    }

    @Override
    public boolean isMethodPointer() {
        return delegate.isMethodPointer();
    }

    @Override
    public boolean isMethodOffset() {
        return delegate.isMethodOffset();
    }

    @Override
    public boolean isCEntryPointLiteralCodePointer() {
        return delegate.isCEntryPointLiteralCodePointer();
    }

    @Override
    public boolean isCGlobalDataBasePointer() {
        return delegate.isCGlobalDataBasePointer();
    }

    @Override
    public ConstantReferenceData.ObjectConstant.Loader getObjectConstant() {
        return new ObjectConstantLoaderAdapter(delegate.getObjectConstant());
    }

    @Override
    public PrimitiveValueData.Loader getPrimitiveValue() {
        return CapnProtoPrimitiveValueData.loader(delegate.getPrimitiveValue());
    }

    @Override
    public ConstantReferenceData.MethodPointer.Loader getMethodPointer() {
        return new MethodPointerLoaderAdapter(delegate.getMethodPointer());
    }

    @Override
    public ConstantReferenceData.MethodOffset.Loader getMethodOffset() {
        return new MethodOffsetLoaderAdapter(delegate.getMethodOffset());
    }

    @Override
    public CEntryPointLiteralReferenceData.Loader getCEntryPointLiteralCodePointer() {
        return CapnProtoCEntryPointLiteralReferenceData.loader(delegate.getCEntryPointLiteralCodePointer());
    }
}

record ObjectConstantWriterAdapter(ConstantReference.ObjectConstant.Builder delegate) implements ConstantReferenceData.ObjectConstant.Writer {
    @Override
    public void setConstantId(int value) {
        delegate.setConstantId(value);
    }
}

record ObjectConstantLoaderAdapter(ConstantReference.ObjectConstant.Reader delegate) implements ConstantReferenceData.ObjectConstant.Loader {
    @Override
    public int getConstantId() {
        return delegate.getConstantId();
    }
}

record MethodPointerWriterAdapter(ConstantReference.MethodPointer.Builder delegate) implements ConstantReferenceData.MethodPointer.Writer {
    @Override
    public void setMethodId(int value) {
        delegate.setMethodId(value);
    }

    @Override
    public void setPermitsRewriteToPLT(boolean value) {
        delegate.setPermitsRewriteToPLT(value);
    }
}

record MethodPointerLoaderAdapter(ConstantReference.MethodPointer.Reader delegate) implements ConstantReferenceData.MethodPointer.Loader {
    @Override
    public int getMethodId() {
        return delegate.getMethodId();
    }

    @Override
    public boolean getPermitsRewriteToPLT() {
        return delegate.getPermitsRewriteToPLT();
    }
}

record MethodOffsetWriterAdapter(ConstantReference.MethodOffset.Builder delegate) implements ConstantReferenceData.MethodOffset.Writer {
    @Override
    public void setMethodId(int value) {
        delegate.setMethodId(value);
    }
}

record MethodOffsetLoaderAdapter(ConstantReference.MethodOffset.Reader delegate) implements ConstantReferenceData.MethodOffset.Loader {
    @Override
    public int getMethodId() {
        return delegate.getMethodId();
    }
}
