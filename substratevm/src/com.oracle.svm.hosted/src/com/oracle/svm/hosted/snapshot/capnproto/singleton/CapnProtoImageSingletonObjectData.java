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

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonObject;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonObjectData;

public final class CapnProtoImageSingletonObjectData {
    public static ImageSingletonObjectData.Writer writer(ImageSingletonObject.Builder delegate) {
        return new ImageSingletonObjectWriterAdapter(delegate);
    }

    public static ImageSingletonObjectData.Loader loader(ImageSingletonObject.Reader delegate) {
        return new ImageSingletonObjectLoaderAdapter(delegate);
    }
}

record ImageSingletonObjectWriterAdapter(ImageSingletonObject.Builder delegate) implements ImageSingletonObjectData.Writer {
    @Override
    public void setId(int value) {
        delegate.setId(value);
    }

    @Override
    public void setClassName(String value) {
        delegate.setClassName(value);
    }

    @Override
    public void setSingletonInstantiatorClass(String value) {
        delegate.setSingletonInstantiatorClass(value);
    }

    @Override
    public void setKeyStoreId(int value) {
        delegate.setKeyStoreId(value);
    }
}

record ImageSingletonObjectLoaderAdapter(ImageSingletonObject.Reader delegate) implements ImageSingletonObjectData.Loader {
    @Override
    public int getId() {
        return delegate.getId();
    }

    @Override
    public String getClassName() {
        return delegate.getClassName().toString();
    }

    @Override
    public String getSingletonInstantiatorClass() {
        return delegate.getSingletonInstantiatorClass().toString();
    }

    @Override
    public int getKeyStoreId() {
        return delegate.getKeyStoreId();
    }
}
