/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.None;

import java.lang.module.Configuration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.internal.loader.ClassLoaderValue;
import jdk.internal.module.ServicesCatalog;

@SuppressWarnings("unused")
@TargetClass(value = java.lang.ModuleLayer.class)
final class Target_java_lang_ModuleLayer {
    /// [ModuleLayer] declares this field as `final`, but
    /// [ModuleLayerSubstitutionsSupport#patchBootLayer] updates it. Applying [RecomputeFieldValue]
    /// preserves the hosted value while ensuring the alias is not treated as final during analysis.
    @Alias @RecomputeFieldValue(isFinal = false, kind = None) Configuration cf;

    /// See [#nameToModule] for [RecomputeFieldValue] explanation.
    @Alias @RecomputeFieldValue(isFinal = false, kind = None) Map<String, Module> nameToModule;

    @Alias volatile Set<Module> modules;

    @Alias volatile ServicesCatalog servicesCatalog;

    @Substitute
    public static ModuleLayer boot() {
        return RuntimeModuleSupport.singleton().getBootLayer();
    }

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ModuleLayerCLVTransformer.class, isFinal = true) //
    static ClassLoaderValue<List<ModuleLayer>> CLV;
}

final class ModuleLayerSubstitutionsSupport {
    private ModuleLayerSubstitutionsSupport() {
    }

    static Map<String, Module> nameToModule(ModuleLayer layer) {
        return SubstrateUtil.cast(layer, Target_java_lang_ModuleLayer.class).nameToModule;
    }

    /// Patches the boot [ModuleLayer] in place with a rebuilt [Configuration] and module map.
    ///
    /// This is used when runtime boot-layer augmentation must preserve the original
    /// [ModuleLayer#boot] identity while replacing the underlying configuration, name-to-module
    /// map, and their lazy caches.
    static void patchBootLayer(Configuration configuration, Map<String, Module> augmentedNameToModule) {
        Target_java_lang_ModuleLayer target = SubstrateUtil.cast(ModuleLayer.boot(), Target_java_lang_ModuleLayer.class);
        target.cf = configuration;
        target.nameToModule = augmentedNameToModule;
        target.modules = null;
        /*
         * Rebuild the layer catalog lazily from the augmented module map so service binding sees
         * runtime-added provider modules even if the boot-layer catalog was initialized before the
         * augmentation ran.
         */
        target.servicesCatalog = null;
    }
}

final class ModuleLayerCLVTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return originalValue != null ? RuntimeClassLoaderValueSupport.instance().moduleLayerCLV : null;
    }
}
