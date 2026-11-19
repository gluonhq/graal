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
package com.oracle.svm.interpreter;

import java.lang.ref.WeakReference;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.shared.constraints.LoadingConstraintsShared;
import com.oracle.svm.shared.util.SubstrateUtil;

/**
 * Records and check the loading constraints (as described in
 * {@code JVMS - 5.3.4. Loading Constraints}) when runtime class loading is enabled.
 * <p>
 * Note: loading constraints are currently not recorded for AOT classes (GR-74285).
 */
public class CremaLoadingConstraints extends LoadingConstraintsShared<ClassLoader, WeakReference<Object>, DynamicHub> {
    // This must remain strongly reachable.
    private static final Object MARKER = new Object();
    // This must refer to something that is always strongly reachable.
    private static final WeakReference<Object> BOOT_LOADER_STORAGE = new WeakReference<>(MARKER);

    @Override
    public DynamicHub findLoadedClass(Symbol<Type> type, ClassLoader classLoader) {
        return DynamicHub.fromClass(CremaSupport.singleton().findLoadedClass(type, classLoader));
    }

    @Override
    public ClassLoader getDefiningClassLoader(DynamicHub cls) {
        return cls.getClassLoader();
    }

    @Override
    public long getUniqueClassId(DynamicHub cls) {
        return cls.getTypeID();
    }

    @Override
    public WeakReference<Object> toStorage(ClassLoader classLoader) {
        if (classLoader == null) {
            return BOOT_LOADER_STORAGE;
        }
        WeakReference<Object> weakSelf = SubstrateUtil.cast(classLoader, Target_java_lang_ClassLoader.class).weakSelf;
        InterpreterUtil.guarantee(weakSelf != null, "uninitialized weakSelf for: %s", classLoader);
        return weakSelf;
    }

    @Override
    public boolean isSame(WeakReference<Object> storage, ClassLoader classLoader) {
        if (classLoader == null) {
            return storage == BOOT_LOADER_STORAGE;
        }
        return storage.get() == classLoader;
    }

    @Override
    public boolean isAlive(WeakReference<Object> storage) {
        return storage.get() != null;
    }
}
