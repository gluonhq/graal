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

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnnotation;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationElementData;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

public final class CapnProtoPersistedAnnotationData {
    public static PersistedAnnotationData.Writer writer(PersistedAnnotation.Builder delegate) {
        return new PersistedAnnotationWriterAdapter(delegate);
    }

    public static PersistedAnnotationData.Loader loader(PersistedAnnotation.Reader delegate) {
        return new PersistedAnnotationLoaderAdapter(delegate);
    }
}

record PersistedAnnotationWriterAdapter(PersistedAnnotation.Builder delegate) implements PersistedAnnotationData.Writer {
    @Override
    public void setTypeName(String value) {
        delegate.setTypeName(value);
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnnotationElementData.Writer> initValues(int size) {
        return wrapStructListWriter(delegate.initValues(size), CapnProtoPersistedAnnotationElementData::writer);
    }
}

record PersistedAnnotationLoaderAdapter(PersistedAnnotation.Reader delegate) implements PersistedAnnotationData.Loader {
    @Override
    public String getTypeName() {
        return delegate.getTypeName().toString();
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnnotationElementData.Loader> getValues() {
        return wrapStructListLoader(delegate.getValues(), CapnProtoPersistedAnnotationElementData::loader);
    }
}
