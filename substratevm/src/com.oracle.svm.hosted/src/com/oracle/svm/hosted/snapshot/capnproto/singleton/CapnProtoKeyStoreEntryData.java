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
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.KeyStoreEntry;
import com.oracle.svm.hosted.snapshot.singleton.KeyStoreEntryData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList.Bool;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;

public final class CapnProtoKeyStoreEntryData {
    public static KeyStoreEntryData.Writer writer(KeyStoreEntry.Builder delegate) {
        return new KeyStoreEntryWriterAdapter(delegate);
    }

    public static KeyStoreEntryData.Loader loader(KeyStoreEntry.Reader delegate) {
        return new KeyStoreEntryLoaderAdapter(delegate);
    }
}

record KeyStoreEntryWriterAdapter(KeyStoreEntry.Builder delegate) implements KeyStoreEntryData.Writer {
    @Override
    public void setKey(String value) {
        delegate.setKey(value);
    }

    @Override
    public KeyStoreEntryData.Value.Writer initValue() {
        return new KeyStoreValueWriterAdapter(delegate.initValue());
    }
}

record KeyStoreEntryLoaderAdapter(KeyStoreEntry.Reader delegate) implements KeyStoreEntryData.Loader {
    @Override
    public String getKey() {
        return delegate.getKey().toString();
    }

    @Override
    public KeyStoreEntryData.Value.Loader getValue() {
        return new KeyStoreValueLoaderAdapter(delegate.getValue());
    }
}

record KeyStoreValueWriterAdapter(KeyStoreEntry.Value.Builder delegate) implements KeyStoreEntryData.Value.Writer {
    @Override
    public void setI(int value) {
        delegate.setI(value);
    }

    @Override
    public void setJ(long value) {
        delegate.setJ(value);
    }

    @Override
    public void setStr(String value) {
        delegate.setStr(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initIl(int size) {
        return wrapIntListWriter(delegate.initIl(size));
    }

    @Override
    public Bool.Writer initZl(int size) {
        return wrapBoolListWriter(delegate.initZl(size));
    }

    @Override
    public SnapshotStringList.Writer initStrl(int size) {
        return wrapStringListWriter(delegate.initStrl(size));
    }
}

record KeyStoreValueLoaderAdapter(KeyStoreEntry.Value.Reader delegate) implements KeyStoreEntryData.Value.Loader {
    @Override
    public KeyStoreEntryData.Value.Kind kind() {
        return switch (delegate.which()) {
            case I -> KeyStoreEntryData.Value.Kind.I;
            case J -> KeyStoreEntryData.Value.Kind.J;
            case STR -> KeyStoreEntryData.Value.Kind.STR;
            case IL -> KeyStoreEntryData.Value.Kind.IL;
            case ZL -> KeyStoreEntryData.Value.Kind.ZL;
            case STRL -> KeyStoreEntryData.Value.Kind.STRL;
            case _NOT_IN_SCHEMA -> KeyStoreEntryData.Value.Kind.NOT_IN_SCHEMA;
        };
    }

    @Override
    public int getI() {
        return delegate.getI();
    }

    @Override
    public long getJ() {
        return delegate.getJ();
    }

    @Override
    public String getStr() {
        return delegate.getStr().toString();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getIl() {
        return wrapIntListLoader(delegate.getIl());
    }

    @Override
    public Bool.Loader getZl() {
        return wrapBoolListLoader(delegate.getZl());
    }

    @Override
    public SnapshotStringList.Loader getStrl() {
        return wrapStringListLoader(delegate.getStrl());
    }
}
