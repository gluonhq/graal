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

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonKey;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonKeyData;

public final class CapnProtoImageSingletonKeyData {
    public static ImageSingletonKeyData.Writer writer(ImageSingletonKey.Builder delegate) {
        return new ImageSingletonKeyWriterAdapter(delegate);
    }

    public static ImageSingletonKeyData.Loader loader(ImageSingletonKey.Reader delegate) {
        return new ImageSingletonKeyLoaderAdapter(delegate);
    }
}

record ImageSingletonKeyWriterAdapter(ImageSingletonKey.Builder delegate) implements ImageSingletonKeyData.Writer {
    @Override
    public void setKeyClassName(String value) {
        delegate.setKeyClassName(value);
    }

    @Override
    public void setPersistFlag(int value) {
        delegate.setPersistFlag(value);
    }

    @Override
    public void setObjectId(int value) {
        delegate.setObjectId(value);
    }

    @Override
    public void setIsInitialLayerOnly(boolean value) {
        delegate.setIsInitialLayerOnly(value);
    }

    @Override
    public void setConstantId(int value) {
        delegate.setConstantId(value);
    }

    @Override
    public void setKeyStoreId(int value) {
        delegate.setKeyStoreId(value);
    }
}

record ImageSingletonKeyLoaderAdapter(ImageSingletonKey.Reader delegate) implements ImageSingletonKeyData.Loader {
    @Override
    public String getKeyClassName() {
        return delegate.getKeyClassName().toString();
    }

    @Override
    public int getPersistFlag() {
        return delegate.getPersistFlag();
    }

    @Override
    public int getObjectId() {
        return delegate.getObjectId();
    }

    @Override
    public boolean getIsInitialLayerOnly() {
        return delegate.getIsInitialLayerOnly();
    }

    @Override
    public int getConstantId() {
        return delegate.getConstantId();
    }

    @Override
    public int getKeyStoreId() {
        return delegate.getKeyStoreId();
    }
}
