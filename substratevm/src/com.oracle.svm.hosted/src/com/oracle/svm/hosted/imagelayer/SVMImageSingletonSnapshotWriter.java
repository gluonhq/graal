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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.UNDEFINED_CONSTANT_ID;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.UNDEFINED_KEY_STORE_ID;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.UNDEFINED_SINGLETON_OBJ_ID;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonKeyData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonObjectData;
import com.oracle.svm.hosted.snapshot.singleton.KeyStoreEntryData;
import com.oracle.svm.hosted.snapshot.singleton.KeyStoreInstanceData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList.Bool;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl.SingletonInfo;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.debug.Assertions;

final class SVMImageSingletonSnapshotWriter {
    private record SingletonPersistInfo(LayeredPersistFlags flags, int singletonId, Class<?> singletonInstantiator, int keyStoreId, EconomicMap<String, Object> keyStore) {
    }

    @SuppressWarnings("unchecked")
    static void writeImageSingletonInfo(List<Entry<Class<?>, SingletonInfo>> layeredImageSingletons, SharedLayerSnapshotData.Writer snapshotWriter, AnalysisUniverse aUniverse,
                    HostedUniverse hUniverse) {
        /*
         * First write the image singleton keys
         */
        SnapshotStructList.Writer<ImageSingletonKeyData.Writer> singletonsBuilder = snapshotWriter.initSingletonKeys(layeredImageSingletons.size());
        Map<Object, SingletonPersistInfo> singletonPersistInfoMap = new IdentityHashMap<>();
        int nextSingletonId = 0;
        int nextKeyStoreId = 0;
        Set<Object> initialLayerSingletons = LayeredImageSingletonSupport.singleton().getSingletonsWithTrait(SingletonLayeredInstallationKind.INITIAL_LAYER_ONLY);
        for (int i = 0; i < layeredImageSingletons.size(); i++) {
            var singletonEntry = layeredImageSingletons.get(i);
            String key = singletonEntry.getKey().getName();
            Object singleton = singletonEntry.getValue().singleton();
            boolean initialLayerOnly = initialLayerSingletons.contains(singleton);
            if (!singletonPersistInfoMap.containsKey(singleton)) {
                var writer = new SVMImageSingletonWriter(snapshotWriter, hUniverse);
                var trait = singletonEntry.getValue().traitMap().getTrait(LayeredCallbacksSingletonTrait.class);
                var action = (SingletonLayeredCallbacks<Object>) trait.get().metadata();
                var flags = action.doPersist(writer, singleton);
                if (initialLayerOnly) {
                    VMError.guarantee(flags == LayeredPersistFlags.FORBIDDEN, "InitialLayer Singleton's persist action must return %s %s", LayeredPersistFlags.FORBIDDEN,
                                    singleton);
                }
                int singletonId = UNDEFINED_SINGLETON_OBJ_ID;
                int keyStoreId = UNDEFINED_KEY_STORE_ID;
                Class<?> singletonInstantiator = null;
                EconomicMap<String, Object> keyValueStore = null;
                if (flags == LayeredPersistFlags.CREATE) {
                    VMError.guarantee(!(singleton instanceof Feature), "Features cannot return %s. Use %s instead. Feature: %s", LayeredPersistFlags.CREATE,
                                    LayeredPersistFlags.CALLBACK_ON_REGISTRATION, singleton);
                    singletonId = nextSingletonId++;
                    singletonInstantiator = action.getSingletonInstantiator();
                    keyValueStore = writer.getKeyValueStore();
                    keyStoreId = nextKeyStoreId++;
                } else if (flags == LayeredPersistFlags.CALLBACK_ON_REGISTRATION) {
                    keyValueStore = writer.getKeyValueStore();
                    keyStoreId = nextKeyStoreId++;
                } else {
                    VMError.guarantee(writer.getKeyValueStore().isEmpty(), "ImageSingletonWriter was used, but the results will not be persisted %s %s", singletonEntry.getKey(), singleton);
                }

                var info = new SingletonPersistInfo(flags, singletonId, singletonInstantiator, keyStoreId, keyValueStore);
                singletonPersistInfoMap.put(singleton, info);
            }
            var info = singletonPersistInfoMap.get(singleton);

            ImageSingletonKeyData.Writer sb = singletonsBuilder.get(i);
            sb.setKeyClassName(key);
            sb.setObjectId(info.singletonId);
            sb.setPersistFlag(info.flags.ordinal());
            int constantId = UNDEFINED_CONSTANT_ID;
            if (initialLayerOnly) {
                ImageHeapConstant imageHeapConstant = (ImageHeapConstant) aUniverse.getSnippetReflection().forObject(singleton);
                constantId = ImageHeapConstant.getConstantID(imageHeapConstant);
            }
            sb.setConstantId(constantId);
            sb.setIsInitialLayerOnly(initialLayerOnly);
            sb.setKeyStoreId(info.keyStoreId);
        }

        /*
         * Next write the singleton objects.
         */
        var sortedBySingletonIds = singletonPersistInfoMap.entrySet().stream()
                        .filter(e -> e.getValue().flags == LayeredPersistFlags.CREATE)
                        .sorted(Comparator.comparingInt(e -> e.getValue().singletonId))
                        .toList();
        SnapshotStructList.Writer<ImageSingletonObjectData.Writer> objectsBuilder = snapshotWriter.initSingletonObjects(sortedBySingletonIds.size());
        for (int i = 0; i < sortedBySingletonIds.size(); i++) {
            var entry = sortedBySingletonIds.get(i);
            var info = entry.getValue();
            assert info.singletonId == i : Assertions.errorMessage(i, info);

            ImageSingletonObjectData.Writer ob = objectsBuilder.get(i);
            ob.setId(info.singletonId);
            ob.setClassName(entry.getKey().getClass().getName());
            ob.setSingletonInstantiatorClass(info.singletonInstantiator().getName());
            ob.setKeyStoreId(info.keyStoreId);
        }

        /* Finally write out the KeyStoreContainers. */
        var sortedByKeyStoreIds = singletonPersistInfoMap.entrySet().stream()
                        .filter(e -> e.getValue().keyStoreId != -1)
                        .sorted(Comparator.comparingInt(e -> e.getValue().keyStoreId))
                        .toList();
        var keyStores = snapshotWriter.initKeyStoreInstances(sortedByKeyStoreIds.size());
        for (int i = 0; i < sortedByKeyStoreIds.size(); i++) {
            var entry = sortedByKeyStoreIds.get(i);
            var info = entry.getValue();
            assert info.keyStoreId == i : Assertions.errorMessage(i, info);

            var keyStoreInstance = keyStores.get(i);
            keyStoreInstance.setId(info.keyStoreId);
            writeImageSingletonKeyStore(keyStoreInstance, info.keyStore);
        }
    }

    private static void writeImageSingletonKeyStore(KeyStoreInstanceData.Writer keyStoreBuilder, EconomicMap<String, Object> keyStore) {
        SnapshotStructList.Writer<KeyStoreEntryData.Writer> entryBuilder = keyStoreBuilder.initKeyStore(keyStore.size());
        MapCursor<String, Object> cursor = keyStore.getEntries();
        for (int i = 0; cursor.advance(); i++) {
            KeyStoreEntryData.Writer b = entryBuilder.get(i);
            b.setKey(cursor.getKey());
            switch (cursor.getValue()) {
                case Integer iv -> b.initValue().setI(iv);
                case Long jv -> b.initValue().setJ(jv);
                case String str -> b.initValue().setStr(str);
                case int[] il -> {
                    SnapshotPrimitiveList.Int.Writer ilb = b.initValue().initIl(il.length);
                    for (int j = 0; j < il.length; j++) {
                        ilb.set(j, il[j]);
                    }
                }
                case String[] strl -> {
                    SnapshotStringList.Writer strlb = b.initValue().initStrl(strl.length);
                    for (int j = 0; j < strl.length; j++) {
                        strlb.set(j, strl[j]);
                    }
                }
                case boolean[] zl -> {
                    Bool.Writer zlb = b.initValue().initZl(zl.length);
                    for (int j = 0; j < zl.length; j++) {
                        zlb.set(j, zl[j]);
                    }
                }
                default -> throw new IllegalStateException("Unexpected type: " + cursor.getValue());
            }
        }
    }
}
