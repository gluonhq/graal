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
package com.oracle.svm.hosted.snapshot.capnproto.singleton;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListWriter;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.ModulePackages;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.Packages;
import com.oracle.svm.hosted.snapshot.singleton.ModulePackagesData;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

public final class CapnProtoModulePackagesData {
    public static ModulePackagesData.Writer writer(ModulePackages.Builder delegate) {
        return new ModulePackagesWriterAdapter(delegate);
    }

    public static ModulePackagesData.Loader loader(ModulePackages.Reader delegate) {
        return new ModulePackagesLoaderAdapter(delegate);
    }
}

record ModulePackagesWriterAdapter(ModulePackages.Builder delegate) implements ModulePackagesData.Writer {
    @Override
    public void setModuleKey(String value) {
        delegate.setModuleKey(value);
    }

    @Override
    public SnapshotStructList.Writer<ModulePackagesData.PackageEntry.Writer> initPackages(int size) {
        return wrapStructListWriter(delegate.initPackages(size), ModulePackageEntryWriterAdapter::new);
    }
}

record ModulePackagesLoaderAdapter(ModulePackages.Reader delegate) implements ModulePackagesData.Loader {
    @Override
    public String getModuleKey() {
        return delegate.getModuleKey().toString();
    }

    @Override
    public SnapshotStructList.Loader<ModulePackagesData.PackageEntry.Loader> getPackages() {
        return wrapStructListLoader(delegate.getPackages(), ModulePackageEntryLoaderAdapter::new);
    }
}

record ModulePackageEntryWriterAdapter(Packages.Builder delegate) implements ModulePackagesData.PackageEntry.Writer {
    @Override
    public void setPackageKey(String value) {
        delegate.setPackageKey(value);
    }

    @Override
    public SnapshotStringList.Writer initModules(int size) {
        return wrapStringListWriter(delegate.initModules(size));
    }
}

record ModulePackageEntryLoaderAdapter(Packages.Reader delegate) implements ModulePackagesData.PackageEntry.Loader {
    @Override
    public String getPackageKey() {
        return delegate.getPackageKey().toString();
    }

    @Override
    public SnapshotStringList.Loader getModules() {
        return wrapStringListLoader(delegate.getModules());
    }
}
