/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.RuntimeClassLoading.NoRuntimeClassLoading;
import com.oracle.svm.core.hub.registry.ClassRegistries;

/// `jdk.internal.loader.Loader` mixes class-loading and resource-loading entry points, so the
/// substitutions are gated per method instead of disabling the whole class at once.
@TargetClass(className = "jdk.internal.loader.Loader")
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_internal_loader_Loader {

    @Substitute
    @TargetElement(onlyWith = NoRuntimeClassLoading.class)
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    @Substitute
    @TargetElement(onlyWith = NoRuntimeClassLoading.class)
    protected Class<?> findClass(String moduleName, String name) throws ClassNotFoundException {
        return null;
    }

    @Substitute
    @TargetElement(onlyWith = NoRuntimeClassLoading.class)
    private Class<?> findClassInModuleOrNull(Target_jdk_internal_loader_Loader_LoadedModule loadedModule, String name) {
        return null;
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    protected URL findResource(String mn, String name) {
        Module module = ModuleLayer.boot().findModule(mn).orElse(null);
        return ResourcesHelper.nameToResourceURL(module, name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    public URL findResource(String name) {
        return ResourcesHelper.nameToResourceURL(name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    public Enumeration<URL> findResources(String name) {
        return ResourcesHelper.nameToResourceEnumerationURLs(name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    public URL getResource(String name) {
        return ResourcesHelper.nameToResourceURL(name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    public Enumeration<URL> getResources(String name) throws IOException {
        return ResourcesHelper.nameToResourceEnumerationURLs(name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    private List<URL> findResourcesAsList(String name) {
        return ResourcesHelper.nameToResourceListURLs(name);
    }
}

@TargetClass(className = "jdk.internal.loader.Loader", innerClass = "LoadedModule", onlyWith = NoRuntimeClassLoading.class)
final class Target_jdk_internal_loader_Loader_LoadedModule {
}
