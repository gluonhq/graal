/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = AnalyzeJavaHomeAccessSupport.LayeredCallbacks.class)
public final class AnalyzeJavaHomeAccessSupport {
    private static final String JAVA_HOME_USED = "javaHomeUsed";
    private static final String JAVA_HOME_USAGE_LOCATIONS = "javaHomeUsageLocations";

    private boolean javaHomeUsed = false;
    private Set<String> javaHomeUsageLocations = Collections.newSetFromMap(new ConcurrentSkipListMap<>());

    public static AnalyzeJavaHomeAccessSupport singleton() {
        return ImageSingletons.lookup(AnalyzeJavaHomeAccessSupport.class);
    }

    public void setJavaHomeUsed() {
        javaHomeUsed = true;
    }

    public boolean getJavaHomeUsed() {
        return javaHomeUsed;
    }

    public void addJavaHomeUsageLocation(String location) {
        javaHomeUsageLocations.add(location);
    }

    public Set<String> getJavaHomeUsageLocations() {
        return javaHomeUsageLocations;
    }

    public void clearJavaHomeUsageLocations() {
        javaHomeUsageLocations = Collections.newSetFromMap(new ConcurrentSkipListMap<>());
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<AnalyzeJavaHomeAccessSupport>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, AnalyzeJavaHomeAccessSupport singleton) {
                    writer.writeInt(JAVA_HOME_USED, singleton.javaHomeUsed ? 1 : 0);
                    writer.writeStringList(JAVA_HOME_USAGE_LOCATIONS, singleton.javaHomeUsageLocations.stream().toList());
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, AnalyzeJavaHomeAccessSupport singleton) {
                    singleton.javaHomeUsed = loader.readInt(JAVA_HOME_USED) == 1;
                    singleton.javaHomeUsageLocations.addAll(loader.readStringList(JAVA_HOME_USAGE_LOCATIONS));
                }
            });
        }
    }
}
