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
package com.oracle.svm.hosted.snapshot.capnproto.layer;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListWriter;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListWriter;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListWriter;

import com.oracle.svm.hosted.snapshot.c.CGlobalDataInfoData;
import com.oracle.svm.hosted.snapshot.capnproto.c.CapnProtoCGlobalDataInfoData;
import com.oracle.svm.hosted.snapshot.capnproto.constant.CapnProtoPersistedConstantData;
import com.oracle.svm.hosted.snapshot.capnproto.dynamichub.CapnProtoDynamicHubInfoData;
import com.oracle.svm.hosted.snapshot.capnproto.elements.CapnProtoPersistedAnalysisFieldData;
import com.oracle.svm.hosted.snapshot.capnproto.elements.CapnProtoPersistedAnalysisMethodData;
import com.oracle.svm.hosted.snapshot.capnproto.elements.CapnProtoPersistedAnalysisTypeData;
import com.oracle.svm.hosted.snapshot.capnproto.elements.CapnProtoPersistedHostedMethodData;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot;
import com.oracle.svm.hosted.snapshot.capnproto.singleton.CapnProtoImageSingletonKeyData;
import com.oracle.svm.hosted.snapshot.capnproto.singleton.CapnProtoImageSingletonObjectData;
import com.oracle.svm.hosted.snapshot.capnproto.singleton.CapnProtoKeyStoreInstanceData;
import com.oracle.svm.hosted.snapshot.capnproto.singleton.CapnProtoLayeredModuleData;
import com.oracle.svm.hosted.snapshot.capnproto.singleton.CapnProtoLayeredRuntimeMetadataSingletonData;
import com.oracle.svm.hosted.snapshot.capnproto.singleton.CapnProtoStaticFinalFieldFoldingSingletonData;
import com.oracle.svm.hosted.snapshot.constant.PersistedConstantData;
import com.oracle.svm.hosted.snapshot.dynamichub.DynamicHubInfoData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisFieldData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData;
import com.oracle.svm.hosted.snapshot.elements.PersistedHostedMethodData;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonKeyData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonObjectData;
import com.oracle.svm.hosted.snapshot.singleton.KeyStoreInstanceData;
import com.oracle.svm.hosted.snapshot.singleton.LayeredModuleData;
import com.oracle.svm.hosted.snapshot.singleton.LayeredRuntimeMetadataSingletonData;
import com.oracle.svm.hosted.snapshot.singleton.StaticFinalFieldFoldingSingletonData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shaded.org.capnproto.MessageBuilder;
import com.oracle.svm.shaded.org.capnproto.Serialize;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Path;

public final class CapnProtoSharedLayerSnapshotData {
    public static SharedLayerSnapshotData.Writer writer() {
        return new SnapshotWriterAdapter();
    }

    public static SharedLayerSnapshotData.Loader loader(SharedLayerSnapshot.Reader delegate) {
        return new SnapshotLoaderAdapter(delegate);
    }

    public static void write(Path path, SharedLayerSnapshotData.Writer snapshot) throws IOException {
        SnapshotWriterAdapter writer = (SnapshotWriterAdapter) snapshot;
        try (FileOutputStream outputStream = new FileOutputStream(path.toFile())) {
            Serialize.write(Channels.newChannel(outputStream), writer.messageBuilder);
        }
    }
}

final class SnapshotWriterAdapter implements SharedLayerSnapshotData.Writer {
    final MessageBuilder messageBuilder = new MessageBuilder();
    private final SharedLayerSnapshot.Builder delegate = messageBuilder.initRoot(SharedLayerSnapshot.factory);

    @Override
    public void setStaticPrimitiveFieldsConstantId(int value) {
        delegate.setStaticPrimitiveFieldsConstantId(value);
    }

    @Override
    public void setStaticObjectFieldsConstantId(int value) {
        delegate.setStaticObjectFieldsConstantId(value);
    }

    @Override
    public void setNextTypeId(int value) {
        delegate.setNextTypeId(value);
    }

    @Override
    public void setNextMethodId(int value) {
        delegate.setNextMethodId(value);
    }

    @Override
    public void setNextFieldId(int value) {
        delegate.setNextFieldId(value);
    }

    @Override
    public void setNextConstantId(int value) {
        delegate.setNextConstantId(value);
    }

    @Override
    public void setImageHeapEndOffset(long value) {
        delegate.setImageHeapEndOffset(value);
    }

    @Override
    public void setNodeClassMapLocation(String value) {
        delegate.setNodeClassMapLocation(value);
    }

    @Override
    public void setNextLayerNumber(int value) {
        delegate.setNextLayerNumber(value);
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnalysisTypeData.Writer> initTypes(int size) {
        return wrapStructListWriter(delegate.initTypes(size), CapnProtoPersistedAnalysisTypeData::writer);
    }

    @Override
    public SnapshotStructList.Writer<DynamicHubInfoData.Writer> initDynamicHubInfos(int size) {
        return wrapStructListWriter(delegate.initDynamicHubInfos(size), CapnProtoDynamicHubInfoData::writer);
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnalysisMethodData.Writer> initMethods(int size) {
        return wrapStructListWriter(delegate.initMethods(size), CapnProtoPersistedAnalysisMethodData::writer);
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnalysisFieldData.Writer> initFields(int size) {
        return wrapStructListWriter(delegate.initFields(size), CapnProtoPersistedAnalysisFieldData::writer);
    }

    @Override
    public SnapshotStructList.Writer<CGlobalDataInfoData.Writer> initCGlobals(int size) {
        return wrapStructListWriter(delegate.initCGlobals(size), CapnProtoCGlobalDataInfoData::writer);
    }

    @Override
    public SnapshotStructList.Writer<PersistedHostedMethodData.Writer> initHostedMethods(int size) {
        return wrapStructListWriter(delegate.initHostedMethods(size), CapnProtoPersistedHostedMethodData::writer);
    }

    @Override
    public SnapshotStructList.Writer<PersistedConstantData.Writer> initConstants(int size) {
        return wrapStructListWriter(delegate.initConstants(size), CapnProtoPersistedConstantData::writer);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initConstantsToRelink(int size) {
        return wrapIntListWriter(delegate.initConstantsToRelink(size));
    }

    @Override
    public SnapshotStructList.Writer<ImageSingletonKeyData.Writer> initSingletonKeys(int size) {
        return wrapStructListWriter(delegate.initSingletonKeys(size), CapnProtoImageSingletonKeyData::writer);
    }

    @Override
    public SnapshotStructList.Writer<ImageSingletonObjectData.Writer> initSingletonObjects(int size) {
        return wrapStructListWriter(delegate.initSingletonObjects(size), CapnProtoImageSingletonObjectData::writer);
    }

    @Override
    public SnapshotStructList.Writer<KeyStoreInstanceData.Writer> initKeyStoreInstances(int size) {
        return wrapStructListWriter(delegate.initKeyStoreInstances(size), CapnProtoKeyStoreInstanceData::writer);
    }

    @Override
    public SnapshotStringList.Writer initRegisteredJNILibraries(int size) {
        return wrapStringListWriter(delegate.initRegisteredJNILibraries(size));
    }

    @Override
    public SnapshotStringList.Writer initSharedLayerBootLayerModules(int size) {
        return wrapStringListWriter(delegate.initSharedLayerBootLayerModules(size));
    }

    @Override
    public StaticFinalFieldFoldingSingletonData.Writer initStaticFinalFieldFoldingSingleton() {
        return CapnProtoStaticFinalFieldFoldingSingletonData.writer(delegate.initStaticFinalFieldFoldingSingleton());
    }

    @Override
    public LayeredRuntimeMetadataSingletonData.Writer getLayeredRuntimeMetadataSingleton() {
        return CapnProtoLayeredRuntimeMetadataSingletonData.writer(delegate.getLayeredRuntimeMetadataSingleton());
    }

    @Override
    public LayeredModuleData.Writer initLayeredModule() {
        return CapnProtoLayeredModuleData.writer(delegate.initLayeredModule());
    }
}

record SnapshotLoaderAdapter(SharedLayerSnapshot.Reader delegate) implements SharedLayerSnapshotData.Loader {
    @Override
    public int getNextTypeId() {
        return delegate.getNextTypeId();
    }

    @Override
    public int getNextMethodId() {
        return delegate.getNextMethodId();
    }

    @Override
    public int getNextFieldId() {
        return delegate.getNextFieldId();
    }

    @Override
    public int getNextConstantId() {
        return delegate.getNextConstantId();
    }

    @Override
    public int getStaticPrimitiveFieldsConstantId() {
        return delegate.getStaticPrimitiveFieldsConstantId();
    }

    @Override
    public int getStaticObjectFieldsConstantId() {
        return delegate.getStaticObjectFieldsConstantId();
    }

    @Override
    public long getImageHeapEndOffset() {
        return delegate.getImageHeapEndOffset();
    }

    @Override
    public String getNodeClassMapLocation() {
        return delegate.getNodeClassMapLocation().toString();
    }

    @Override
    public int getNextLayerNumber() {
        return delegate.getNextLayerNumber();
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnalysisTypeData.Loader> getTypes() {
        return wrapStructListLoader(delegate.getTypes(), CapnProtoPersistedAnalysisTypeData::loader);
    }

    @Override
    public SnapshotStructList.Loader<DynamicHubInfoData.Loader> getDynamicHubInfos() {
        return wrapStructListLoader(delegate.getDynamicHubInfos(), CapnProtoDynamicHubInfoData::loader);
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnalysisMethodData.Loader> getMethods() {
        return wrapStructListLoader(delegate.getMethods(), CapnProtoPersistedAnalysisMethodData::loader);
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnalysisFieldData.Loader> getFields() {
        return wrapStructListLoader(delegate.getFields(), CapnProtoPersistedAnalysisFieldData::loader);
    }

    @Override
    public SnapshotStructList.Loader<CGlobalDataInfoData.Loader> getCGlobals() {
        return wrapStructListLoader(delegate.getCGlobals(), CapnProtoCGlobalDataInfoData::loader);
    }

    @Override
    public SnapshotStructList.Loader<PersistedHostedMethodData.Loader> getHostedMethods() {
        return wrapStructListLoader(delegate.getHostedMethods(), CapnProtoPersistedHostedMethodData::loader);
    }

    @Override
    public SnapshotStructList.Loader<PersistedConstantData.Loader> getConstants() {
        return wrapStructListLoader(delegate.getConstants(), CapnProtoPersistedConstantData::loader);
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getConstantsToRelink() {
        return wrapIntListLoader(delegate.getConstantsToRelink());
    }

    @Override
    public SnapshotStructList.Loader<ImageSingletonKeyData.Loader> getSingletonKeys() {
        return wrapStructListLoader(delegate.getSingletonKeys(), CapnProtoImageSingletonKeyData::loader);
    }

    @Override
    public SnapshotStructList.Loader<ImageSingletonObjectData.Loader> getSingletonObjects() {
        return wrapStructListLoader(delegate.getSingletonObjects(), CapnProtoImageSingletonObjectData::loader);
    }

    @Override
    public SnapshotStructList.Loader<KeyStoreInstanceData.Loader> getKeyStoreInstances() {
        return wrapStructListLoader(delegate.getKeyStoreInstances(), CapnProtoKeyStoreInstanceData::loader);
    }

    @Override
    public SnapshotStringList.Loader getRegisteredJNILibraries() {
        return wrapStringListLoader(delegate.getRegisteredJNILibraries());
    }

    @Override
    public SnapshotStringList.Loader getSharedLayerBootLayerModules() {
        return wrapStringListLoader(delegate.getSharedLayerBootLayerModules());
    }

    @Override
    public StaticFinalFieldFoldingSingletonData.Loader getStaticFinalFieldFoldingSingleton() {
        return CapnProtoStaticFinalFieldFoldingSingletonData.loader(delegate.getStaticFinalFieldFoldingSingleton());
    }

    @Override
    public LayeredRuntimeMetadataSingletonData.Loader getLayeredRuntimeMetadataSingleton() {
        return CapnProtoLayeredRuntimeMetadataSingletonData.loader(delegate.getLayeredRuntimeMetadataSingleton());
    }

    @Override
    public LayeredModuleData.Loader getLayeredModule() {
        return CapnProtoLayeredModuleData.loader(delegate.getLayeredModule());
    }
}
