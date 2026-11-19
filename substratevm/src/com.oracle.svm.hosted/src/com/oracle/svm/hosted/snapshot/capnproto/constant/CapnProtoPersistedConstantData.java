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

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant;
import com.oracle.svm.hosted.snapshot.capnproto.util.CapnProtoPrimitiveArrayData;
import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.constant.PersistedConstantData;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData;
import com.oracle.svm.hosted.snapshot.util.PrimitiveArrayData;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shaded.org.capnproto.Void;

public final class CapnProtoPersistedConstantData {
    public static PersistedConstantData.Writer writer(PersistedConstant.Builder delegate) {
        return new PersistedConstantWriterAdapter(delegate);
    }

    public static PersistedConstantData.Loader loader(PersistedConstant.Reader delegate) {
        return new PersistedConstantLoaderAdapter(delegate);
    }
}

record PersistedConstantWriterAdapter(PersistedConstant.Builder delegate) implements PersistedConstantData.Writer {
    @Override
    public void setObjectOffset(long value) {
        delegate.setObjectOffset(value);
    }

    @Override
    public void setId(int value) {
        delegate.setId(value);
    }

    @Override
    public void setTypeId(int value) {
        delegate.setTypeId(value);
    }

    @Override
    public void setIdentityHashCode(int value) {
        delegate.setIdentityHashCode(value);
    }

    @Override
    public PersistedConstantData.ObjectValue.Writer initObject() {
        return new ObjectValueWriterAdapter(delegate.initObject());
    }

    @Override
    public PersistedConstantData.ObjectValue.Writer getObject() {
        return new ObjectValueWriterAdapter(delegate.getObject());
    }

    @Override
    public PrimitiveArrayData.Writer initPrimitiveData() {
        return CapnProtoPrimitiveArrayData.writer(delegate.initPrimitiveData());
    }

    @Override
    public PersistedConstantData.Relocatable.Writer initRelocatable() {
        return new RelocatableWriterAdapter(delegate.initRelocatable());
    }

    @Override
    public void setParentConstantId(int value) {
        delegate.setParentConstantId(value);
    }

    @Override
    public void setParentIndex(int value) {
        delegate.setParentIndex(value);
    }

    @Override
    public void setIsSimulated(boolean value) {
        delegate.setIsSimulated(value);
    }
}

record PersistedConstantLoaderAdapter(PersistedConstant.Reader delegate) implements PersistedConstantData.Loader {
    @Override
    public PersistedConstantData.Kind kind() {
        return switch (delegate.which()) {
            case OBJECT -> PersistedConstantData.Kind.OBJECT;
            case PRIMITIVE_DATA -> PersistedConstantData.Kind.PRIMITIVE_DATA;
            case RELOCATABLE -> PersistedConstantData.Kind.RELOCATABLE;
            case _NOT_IN_SCHEMA -> throw new IllegalStateException("Unexpected constant kind");
        };
    }

    @Override
    public boolean isObject() {
        return delegate.isObject();
    }

    @Override
    public int getId() {
        return delegate.getId();
    }

    @Override
    public int getTypeId() {
        return delegate.getTypeId();
    }

    @Override
    public long getObjectOffset() {
        return delegate.getObjectOffset();
    }

    @Override
    public int getIdentityHashCode() {
        return delegate.getIdentityHashCode();
    }

    @Override
    public int getParentConstantId() {
        return delegate.getParentConstantId();
    }

    @Override
    public int getParentIndex() {
        return delegate.getParentIndex();
    }

    @Override
    public boolean getIsSimulated() {
        return delegate.getIsSimulated();
    }

    @Override
    public PersistedConstantData.ObjectValue.Loader getObject() {
        return new ObjectValueLoaderAdapter(delegate.getObject());
    }

    @Override
    public PrimitiveArrayData.Loader getPrimitiveData() {
        return CapnProtoPrimitiveArrayData.loader(delegate.getPrimitiveData());
    }

    @Override
    public PersistedConstantData.Relocatable.Loader getRelocatable() {
        return new RelocatableLoaderAdapter(delegate.getRelocatable());
    }
}

record ObjectValueWriterAdapter(PersistedConstant.Object.Builder delegate) implements PersistedConstantData.ObjectValue.Writer {
    @Override
    public void setInstance() {
        delegate.setInstance(Void.VOID);
    }

    @Override
    public void setObjectArray() {
        delegate.setObjectArray(Void.VOID);
    }

    @Override
    public SnapshotStructList.Writer<ConstantReferenceData.Writer> initData(int size) {
        return wrapStructListWriter(delegate.initData(size), CapnProtoConstantReferenceData::writer);
    }

    @Override
    public RelinkingData.Writer getRelinking() {
        return CapnProtoRelinkingData.writer(delegate.getRelinking());
    }
}

record ObjectValueLoaderAdapter(PersistedConstant.Object.Reader delegate) implements PersistedConstantData.ObjectValue.Loader {
    @Override
    public PersistedConstantData.ObjectValue.Kind kind() {
        return switch (delegate.which()) {
            case INSTANCE -> PersistedConstantData.ObjectValue.Kind.INSTANCE;
            case OBJECT_ARRAY -> PersistedConstantData.ObjectValue.Kind.OBJECT_ARRAY;
            case _NOT_IN_SCHEMA -> throw new IllegalStateException("Unexpected object kind");
        };
    }

    @Override
    public SnapshotStructList.Loader<ConstantReferenceData.Loader> getData() {
        return wrapStructListLoader(delegate.getData(), CapnProtoConstantReferenceData::loader);
    }

    @Override
    public RelinkingData.Loader getRelinking() {
        return CapnProtoRelinkingData.loader(delegate.getRelinking());
    }
}

record RelocatableWriterAdapter(PersistedConstant.Relocatable.Builder delegate) implements PersistedConstantData.Relocatable.Writer {
    @Override
    public void setKey(String value) {
        delegate.setKey(value);
    }
}

record RelocatableLoaderAdapter(PersistedConstant.Relocatable.Reader delegate) implements PersistedConstantData.Relocatable.Loader {
    @Override
    public String getKey() {
        return delegate.getKey().toString();
    }
}
