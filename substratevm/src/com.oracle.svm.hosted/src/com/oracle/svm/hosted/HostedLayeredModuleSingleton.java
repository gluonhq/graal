/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.imagelayer.BuildingInitialLayerPredicate;
import com.oracle.svm.core.jdk.LayeredModuleSingleton;
import com.oracle.svm.hosted.imagelayer.SnapshotWriters;
import com.oracle.svm.hosted.imagelayer.SVMImageSingletonWriter;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerSingletonLoader;
import com.oracle.svm.hosted.snapshot.singleton.ModulePackagesData;
import com.oracle.svm.hosted.snapshot.util.SnapshotAdapters;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@AutomaticallyRegisteredImageSingleton(value = LayeredModuleSingleton.class, onlyWith = BuildingInitialLayerPredicate.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = HostedLayeredModuleSingleton.LayeredCallbacks.class)
public class HostedLayeredModuleSingleton extends LayeredModuleSingleton {
    public HostedLayeredModuleSingleton() {
        super();
    }

    public HostedLayeredModuleSingleton(EconomicMap<String, Map<String, Set<String>>> moduleOpenPackages, EconomicMap<String, Map<String, Set<String>>> moduleExportedPackages) {
        super(moduleOpenPackages, moduleExportedPackages);
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static void persistModulePackages(SnapshotStructList.Writer<ModulePackagesData.Writer> modulePackagesBuilder, EconomicMap<String, Map<String, Set<String>>> modulePackages) {
            int i = 0;
            var it = modulePackages.getEntries();
            while (it.advance()) {
                var entryBuilder = modulePackagesBuilder.get(i);
                entryBuilder.setModuleKey(it.getKey());
                Map<String, Set<String>> value = it.getValue();
                var packagesBuilder = entryBuilder.initPackages(value.size());
                int j = 0;
                for (var packageEntry : value.entrySet()) {
                    var packageEntryBuilder = packagesBuilder.get(j);
                    packageEntryBuilder.setPackageKey(packageEntry.getKey());
                    SnapshotWriters.initStringList(packageEntryBuilder::initModules, packageEntry.getValue().stream());
                    j++;
                }
                i++;
            }
        }

        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<HostedLayeredModuleSingleton>() {

                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, HostedLayeredModuleSingleton singleton) {
                    SVMImageSingletonWriter writerImpl = (SVMImageSingletonWriter) writer;
                    var builder = writerImpl.getSnapshotWriter().initLayeredModule();
                    persistModulePackages(builder.initOpenModulePackages(singleton.moduleOpenPackages.size()), singleton.moduleOpenPackages);
                    persistModulePackages(builder.initExportedModulePackages(singleton.moduleExportedPackages.size()), singleton.moduleExportedPackages);
                    return LayeredPersistFlags.CREATE;
                }

                @Override
                public Class<? extends LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                    return SingletonInstantiator.class;
                }
            });
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<HostedLayeredModuleSingleton> {
        private static EconomicMap<String, Map<String, Set<String>>> getModulePackages(SnapshotStructList.Loader<ModulePackagesData.Loader> modulePackagesReader) {
            EconomicMap<String, Map<String, Set<String>>> modulePackages = EconomicMap.create();
            for (int i = 0; i < modulePackagesReader.size(); ++i) {
                var entryReader = modulePackagesReader.get(i);
                var packagesReader = entryReader.getPackages();
                Map<String, Set<String>> packages = new HashMap<>();
                for (int j = 0; j < packagesReader.size(); ++j) {
                    var packageEntryReader = packagesReader.get(j);
                    Set<String> modules = SnapshotAdapters.toCollection(packageEntryReader.getModules(), HashSet::new);
                    packages.put(packageEntryReader.getPackageKey(), modules);
                }
                modulePackages.put(entryReader.getModuleKey(), packages);
            }
            return modulePackages;
        }

        @Override
        public HostedLayeredModuleSingleton createFromLoader(ImageSingletonLoader loader) {
            SVMImageLayerSingletonLoader.ImageSingletonLoaderImpl loaderImpl = (SVMImageLayerSingletonLoader.ImageSingletonLoaderImpl) loader;
            var reader = loaderImpl.getSnapshotLoader().getLayeredModule();

            EconomicMap<String, Map<String, Set<String>>> moduleOpenPackages = getModulePackages(reader.getOpenModulePackages());
            EconomicMap<String, Map<String, Set<String>>> moduleExportedPackages = getModulePackages(reader.getExportedModulePackages());

            return new HostedLayeredModuleSingleton(moduleOpenPackages, moduleExportedPackages);
        }
    }
}
