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
package com.oracle.svm.hosted.snapshot.capnproto.c;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListWriter;

import com.oracle.svm.hosted.snapshot.c.CEntryPointLiteralReferenceData;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.CEntryPointLiteralReference;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;

public final class CapnProtoCEntryPointLiteralReferenceData {
    public static CEntryPointLiteralReferenceData.Writer writer(CEntryPointLiteralReference.Builder delegate) {
        return new CEntryPointLiteralReferenceWriterAdapter(delegate);
    }

    public static CEntryPointLiteralReferenceData.Loader loader(CEntryPointLiteralReference.Reader delegate) {
        return new CEntryPointLiteralReferenceLoaderAdapter(delegate);
    }
}

record CEntryPointLiteralReferenceWriterAdapter(CEntryPointLiteralReference.Builder delegate) implements CEntryPointLiteralReferenceData.Writer {
    @Override
    public void setMethodName(String value) {
        delegate.setMethodName(value);
    }

    @Override
    public void setDefiningClass(String value) {
        delegate.setDefiningClass(value);
    }

    @Override
    public SnapshotStringList.Writer initParameterNames(int size) {
        return wrapStringListWriter(delegate.initParameterNames(size));
    }

    @Override
    public SnapshotStringList.Writer getParameterNames() {
        return wrapStringListWriter(delegate.getParameterNames());
    }
}

record CEntryPointLiteralReferenceLoaderAdapter(CEntryPointLiteralReference.Reader delegate) implements CEntryPointLiteralReferenceData.Loader {
    @Override
    public String getMethodName() {
        return delegate.getMethodName().toString();
    }

    @Override
    public String getDefiningClass() {
        return delegate.getDefiningClass().toString();
    }

    @Override
    public SnapshotStringList.Loader getParameterNames() {
        return wrapStringListLoader(delegate.getParameterNames());
    }
}
