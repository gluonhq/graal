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
package com.oracle.svm.hosted.snapshot.capnproto.dynamichub;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListWriter;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.DynamicHubInfo;
import com.oracle.svm.hosted.snapshot.dynamichub.DispatchSlotInfoData;
import com.oracle.svm.hosted.snapshot.dynamichub.DynamicHubInfoData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

public final class CapnProtoDynamicHubInfoData {
    public static DynamicHubInfoData.Writer writer(DynamicHubInfo.Builder delegate) {
        return new DynamicHubInfoWriterAdapter(delegate);
    }

    public static DynamicHubInfoData.Loader loader(DynamicHubInfo.Reader delegate) {
        return new DynamicHubInfoLoaderAdapter(delegate);
    }
}

record DynamicHubInfoWriterAdapter(DynamicHubInfo.Builder delegate) implements DynamicHubInfoData.Writer {
    @Override
    public void setTypeId(int value) {
        delegate.setTypeId(value);
    }

    @Override
    public void setTypecheckId(int value) {
        delegate.setTypecheckId(value);
    }

    @Override
    public void setNumClassTypes(int value) {
        delegate.setNumClassTypes(value);
    }

    @Override
    public void setNumIterableInterfaceTypes(int value) {
        delegate.setNumIterableInterfaceTypes(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initTypecheckSlotValues(int size) {
        return wrapIntListWriter(delegate.initTypecheckSlotValues(size));
    }

    @Override
    public void setInterfaceId(int value) {
        delegate.setInterfaceId(value);
    }

    @Override
    public void setInstalled(boolean value) {
        delegate.setInstalled(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initLocallyDeclaredSlotsHostedMethodIndexes(int size) {
        return wrapIntListWriter(delegate.initLocallyDeclaredSlotsHostedMethodIndexes(size));
    }

    @Override
    public SnapshotStructList.Writer<DispatchSlotInfoData.Writer> initDispatchTableSlotValues(int size) {
        return wrapStructListWriter(delegate.initDispatchTableSlotValues(size), CapnProtoDispatchSlotInfoData::writer);
    }
}

record DynamicHubInfoLoaderAdapter(DynamicHubInfo.Reader delegate) implements DynamicHubInfoData.Loader {
    @Override
    public int getTypeId() {
        return delegate.getTypeId();
    }

    @Override
    public int getTypecheckId() {
        return delegate.getTypecheckId();
    }

    @Override
    public int getNumClassTypes() {
        return delegate.getNumClassTypes();
    }

    @Override
    public int getNumIterableInterfaceTypes() {
        return delegate.getNumIterableInterfaceTypes();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getTypecheckSlotValues() {
        return wrapIntListLoader(delegate.getTypecheckSlotValues());
    }

    @Override
    public int getInterfaceId() {
        return delegate.getInterfaceId();
    }

    @Override
    public boolean getInstalled() {
        return delegate.getInstalled();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getLocallyDeclaredSlotsHostedMethodIndexes() {
        return wrapIntListLoader(delegate.getLocallyDeclaredSlotsHostedMethodIndexes());
    }

    @Override
    public SnapshotStructList.Loader<DispatchSlotInfoData.Loader> getDispatchTableSlotValues() {
        return wrapStructListLoader(delegate.getDispatchTableSlotValues(), CapnProtoDispatchSlotInfoData::loader);
    }

    @Override
    public boolean hasDispatchTableSlotValues() {
        return delegate.hasDispatchTableSlotValues();
    }
}
