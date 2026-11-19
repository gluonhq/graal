/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.impl;

import java.lang.ref.WeakReference;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.constraints.LoadingConstraintsShared;

final class LoadingConstraints extends LoadingConstraintsShared<StaticObject, WeakReference<StaticObject>, Klass> {
    private final EspressoContext context;
    private final WeakReference<StaticObject> bootStorage = new WeakReference<>(StaticObject.NULL);

    LoadingConstraints(EspressoContext context) {
        this.context = context;
    }

    @Override
    public Klass findLoadedClass(Symbol<Type> type, StaticObject loader) {
        return context.getRegistries().findLoadedClass(type, loader);
    }

    @Override
    public StaticObject getDefiningClassLoader(Klass cls) {
        return cls.getDefiningClassLoader();
    }

    @Override
    public long getUniqueClassId(Klass cls) {
        return cls.getId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public WeakReference<StaticObject> toStorage(StaticObject staticObject) {
        if (StaticObject.isNull(staticObject)) {
            return bootStorage;
        }
        return (WeakReference<StaticObject>) context.getMeta().java_lang_ClassLoader_0weakSelf.getHiddenObject(staticObject);
    }

    @Override
    public boolean isSame(WeakReference<StaticObject> loaderRef, StaticObject loader) {
        return loaderRef.get() == loader;
    }

    @Override
    public boolean isAlive(WeakReference<StaticObject> loader) {
        return loader.get() != null;
    }
}
