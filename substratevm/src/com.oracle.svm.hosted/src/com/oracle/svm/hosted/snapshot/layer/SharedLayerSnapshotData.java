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
package com.oracle.svm.hosted.snapshot.layer;

import com.oracle.svm.hosted.snapshot.c.CGlobalDataInfoData;
import com.oracle.svm.hosted.snapshot.constant.PersistedConstantData;
import com.oracle.svm.hosted.snapshot.dynamichub.DynamicHubInfoData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisFieldData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData;
import com.oracle.svm.hosted.snapshot.elements.PersistedHostedMethodData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonKeyData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonObjectData;
import com.oracle.svm.hosted.snapshot.singleton.KeyStoreInstanceData;
import com.oracle.svm.hosted.snapshot.singleton.LayeredModuleData;
import com.oracle.svm.hosted.snapshot.singleton.LayeredRuntimeMetadataSingletonData;
import com.oracle.svm.hosted.snapshot.singleton.StaticFinalFieldFoldingSingletonData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

/** Root view of a shared layer snapshot. */
public interface SharedLayerSnapshotData {
    interface Writer {
        void setStaticPrimitiveFieldsConstantId(int value);

        void setStaticObjectFieldsConstantId(int value);

        void setNextTypeId(int value);

        void setNextMethodId(int value);

        void setNextFieldId(int value);

        void setNextConstantId(int value);

        void setImageHeapEndOffset(long value);

        void setNodeClassMapLocation(String value);

        void setNextLayerNumber(int value);

        SnapshotStructList.Writer<PersistedAnalysisTypeData.Writer> initTypes(int size);

        SnapshotStructList.Writer<DynamicHubInfoData.Writer> initDynamicHubInfos(int size);

        SnapshotStructList.Writer<PersistedAnalysisMethodData.Writer> initMethods(int size);

        SnapshotStructList.Writer<PersistedAnalysisFieldData.Writer> initFields(int size);

        SnapshotStructList.Writer<CGlobalDataInfoData.Writer> initCGlobals(int size);

        SnapshotStructList.Writer<PersistedHostedMethodData.Writer> initHostedMethods(int size);

        SnapshotStructList.Writer<PersistedConstantData.Writer> initConstants(int size);

        SnapshotPrimitiveList.Int.Writer initConstantsToRelink(int size);

        SnapshotStructList.Writer<ImageSingletonKeyData.Writer> initSingletonKeys(int size);

        SnapshotStructList.Writer<ImageSingletonObjectData.Writer> initSingletonObjects(int size);

        SnapshotStructList.Writer<KeyStoreInstanceData.Writer> initKeyStoreInstances(int size);

        SnapshotStringList.Writer initRegisteredJNILibraries(int size);

        SnapshotStringList.Writer initSharedLayerBootLayerModules(int size);

        StaticFinalFieldFoldingSingletonData.Writer initStaticFinalFieldFoldingSingleton();

        LayeredRuntimeMetadataSingletonData.Writer getLayeredRuntimeMetadataSingleton();

        LayeredModuleData.Writer initLayeredModule();
    }

    interface Loader {
        int getNextTypeId();

        int getNextMethodId();

        int getNextFieldId();

        int getNextConstantId();

        int getStaticPrimitiveFieldsConstantId();

        int getStaticObjectFieldsConstantId();

        long getImageHeapEndOffset();

        String getNodeClassMapLocation();

        int getNextLayerNumber();

        SnapshotStructList.Loader<PersistedAnalysisTypeData.Loader> getTypes();

        SnapshotStructList.Loader<DynamicHubInfoData.Loader> getDynamicHubInfos();

        SnapshotStructList.Loader<PersistedAnalysisMethodData.Loader> getMethods();

        SnapshotStructList.Loader<PersistedAnalysisFieldData.Loader> getFields();

        SnapshotStructList.Loader<CGlobalDataInfoData.Loader> getCGlobals();

        SnapshotStructList.Loader<PersistedHostedMethodData.Loader> getHostedMethods();

        SnapshotStructList.Loader<PersistedConstantData.Loader> getConstants();

        SnapshotPrimitiveList.Int.Loader getConstantsToRelink();

        SnapshotStructList.Loader<ImageSingletonKeyData.Loader> getSingletonKeys();

        SnapshotStructList.Loader<ImageSingletonObjectData.Loader> getSingletonObjects();

        SnapshotStructList.Loader<KeyStoreInstanceData.Loader> getKeyStoreInstances();

        SnapshotStringList.Loader getRegisteredJNILibraries();

        SnapshotStringList.Loader getSharedLayerBootLayerModules();

        StaticFinalFieldFoldingSingletonData.Loader getStaticFinalFieldFoldingSingleton();

        LayeredRuntimeMetadataSingletonData.Loader getLayeredRuntimeMetadataSingleton();

        LayeredModuleData.Loader getLayeredModule();
    }
}
