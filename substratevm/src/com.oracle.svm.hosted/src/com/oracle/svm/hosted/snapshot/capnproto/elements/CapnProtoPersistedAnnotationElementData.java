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
package com.oracle.svm.hosted.snapshot.capnproto.elements;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnnotationElement;
import com.oracle.svm.hosted.snapshot.capnproto.util.CapnProtoPrimitiveArrayData;
import com.oracle.svm.hosted.snapshot.capnproto.util.CapnProtoPrimitiveValueData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationElementData;
import com.oracle.svm.hosted.snapshot.util.PrimitiveArrayData;
import com.oracle.svm.hosted.snapshot.util.PrimitiveValueData;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

public final class CapnProtoPersistedAnnotationElementData {
    public static PersistedAnnotationElementData.Writer writer(PersistedAnnotationElement.Builder delegate) {
        return new PersistedAnnotationElementWriterAdapter(delegate);
    }

    public static PersistedAnnotationElementData.Loader loader(PersistedAnnotationElement.Reader delegate) {
        return new PersistedAnnotationElementLoaderAdapter(delegate);
    }
}

record PersistedAnnotationElementWriterAdapter(PersistedAnnotationElement.Builder delegate) implements PersistedAnnotationElementData.Writer {
    @Override
    public void setName(String value) {
        delegate.setName(value);
    }

    @Override
    public void setString(String value) {
        delegate.setString(value);
    }

    @Override
    public PersistedAnnotationElementData.EnumValue.Writer initEnum() {
        return new EnumValueWriterAdapter(delegate.initEnum());
    }

    @Override
    public PrimitiveValueData.Writer initPrimitive() {
        return CapnProtoPrimitiveValueData.writer(delegate.initPrimitive());
    }

    @Override
    public PrimitiveArrayData.Writer initPrimitiveArray() {
        return CapnProtoPrimitiveArrayData.writer(delegate.initPrimitiveArray());
    }

    @Override
    public void setClassName(String value) {
        delegate.setClassName(value);
    }

    @Override
    public PersistedAnnotationData.Writer initAnnotation() {
        return CapnProtoPersistedAnnotationData.writer(delegate.initAnnotation());
    }

    @Override
    public PersistedAnnotationElementData.Members.Writer initMembers() {
        return new MembersWriterAdapter(delegate.initMembers());
    }
}

record PersistedAnnotationElementLoaderAdapter(PersistedAnnotationElement.Reader delegate) implements PersistedAnnotationElementData.Loader {
    @Override
    public PersistedAnnotationElementData.Kind kind() {
        return switch (delegate.which()) {
            case STRING -> PersistedAnnotationElementData.Kind.STRING;
            case ENUM -> PersistedAnnotationElementData.Kind.ENUM;
            case PRIMITIVE -> PersistedAnnotationElementData.Kind.PRIMITIVE;
            case PRIMITIVE_ARRAY -> PersistedAnnotationElementData.Kind.PRIMITIVE_ARRAY;
            case CLASS_NAME -> PersistedAnnotationElementData.Kind.CLASS_NAME;
            case ANNOTATION -> PersistedAnnotationElementData.Kind.ANNOTATION;
            case MEMBERS -> PersistedAnnotationElementData.Kind.MEMBERS;
            case _NOT_IN_SCHEMA -> PersistedAnnotationElementData.Kind.NOT_IN_SCHEMA;
        };
    }

    @Override
    public String getName() {
        return delegate.getName().toString();
    }

    @Override
    public String getString() {
        return delegate.getString().toString();
    }

    @Override
    public PersistedAnnotationElementData.EnumValue.Loader getEnum() {
        return new EnumValueLoaderAdapter(delegate.getEnum());
    }

    @Override
    public PrimitiveValueData.Loader getPrimitive() {
        return CapnProtoPrimitiveValueData.loader(delegate.getPrimitive());
    }

    @Override
    public PrimitiveArrayData.Loader getPrimitiveArray() {
        return CapnProtoPrimitiveArrayData.loader(delegate.getPrimitiveArray());
    }

    @Override
    public String getClassName() {
        return delegate.getClassName().toString();
    }

    @Override
    public PersistedAnnotationData.Loader getAnnotation() {
        return CapnProtoPersistedAnnotationData.loader(delegate.getAnnotation());
    }

    @Override
    public PersistedAnnotationElementData.Members.Loader getMembers() {
        return new MembersLoaderAdapter(delegate.getMembers());
    }
}

record EnumValueWriterAdapter(PersistedAnnotationElement.Enum.Builder delegate) implements PersistedAnnotationElementData.EnumValue.Writer {
    @Override
    public void setClassName(String value) {
        delegate.setClassName(value);
    }

    @Override
    public void setName(String value) {
        delegate.setName(value);
    }
}

record EnumValueLoaderAdapter(PersistedAnnotationElement.Enum.Reader delegate) implements PersistedAnnotationElementData.EnumValue.Loader {
    @Override
    public String getClassName() {
        return delegate.getClassName().toString();
    }

    @Override
    public String getName() {
        return delegate.getName().toString();
    }
}

record MembersWriterAdapter(PersistedAnnotationElement.Members.Builder delegate) implements PersistedAnnotationElementData.Members.Writer {
    @Override
    public void setClassName(String value) {
        delegate.setClassName(value);
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnnotationElementData.Writer> initMemberValues(int size) {
        return wrapStructListWriter(delegate.initMemberValues(size), PersistedAnnotationElementWriterAdapter::new);
    }
}

record MembersLoaderAdapter(PersistedAnnotationElement.Members.Reader delegate) implements PersistedAnnotationElementData.Members.Loader {
    @Override
    public String getClassName() {
        return delegate.getClassName().toString();
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnnotationElementData.Loader> getMemberValues() {
        return wrapStructListLoader(delegate.getMemberValues(), PersistedAnnotationElementLoaderAdapter::new);
    }
}
