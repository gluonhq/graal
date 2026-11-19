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
package com.oracle.svm.hosted.snapshot.capnproto.singleton;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapBoolListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapBoolListWriter;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.LayeredRuntimeMetadataSingleton;
import com.oracle.svm.hosted.snapshot.singleton.LayeredRuntimeMetadataSingletonData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList.Bool;

public final class CapnProtoLayeredRuntimeMetadataSingletonData {
    public static LayeredRuntimeMetadataSingletonData.Writer writer(LayeredRuntimeMetadataSingleton.Builder delegate) {
        return new LayeredRuntimeMetadataSingletonWriterAdapter(delegate);
    }

    public static LayeredRuntimeMetadataSingletonData.Loader loader(LayeredRuntimeMetadataSingleton.Reader delegate) {
        return new LayeredRuntimeMetadataSingletonLoaderAdapter(delegate);
    }
}

record LayeredRuntimeMetadataSingletonWriterAdapter(LayeredRuntimeMetadataSingleton.Builder delegate) implements LayeredRuntimeMetadataSingletonData.Writer {
    @Override
    public SnapshotPrimitiveList.Int.Writer initMethods(int size) {
        return wrapIntListWriter(delegate.initMethods(size));
    }

    @Override
    public Bool.Writer initMethodStates(int size) {
        return wrapBoolListWriter(delegate.initMethodStates(size));
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initFields(int size) {
        return wrapIntListWriter(delegate.initFields(size));
    }

    @Override
    public Bool.Writer initFieldStates(int size) {
        return wrapBoolListWriter(delegate.initFieldStates(size));
    }
}

record LayeredRuntimeMetadataSingletonLoaderAdapter(LayeredRuntimeMetadataSingleton.Reader delegate) implements LayeredRuntimeMetadataSingletonData.Loader {
    @Override
    public SnapshotPrimitiveList.Int.Loader getMethods() {
        return wrapIntListLoader(delegate.getMethods());
    }

    @Override
    public Bool.Loader getMethodStates() {
        return wrapBoolListLoader(delegate.getMethodStates());
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getFields() {
        return wrapIntListLoader(delegate.getFields());
    }

    @Override
    public Bool.Loader getFieldStates() {
        return wrapBoolListLoader(delegate.getFieldStates());
    }
}
