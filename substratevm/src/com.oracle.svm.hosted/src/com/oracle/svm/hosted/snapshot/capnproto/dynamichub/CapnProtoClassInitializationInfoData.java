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
package com.oracle.svm.hosted.snapshot.capnproto.dynamichub;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.ClassInitializationInfo;
import com.oracle.svm.hosted.snapshot.dynamichub.ClassInitializationInfoData;

public final class CapnProtoClassInitializationInfoData {
    public static ClassInitializationInfoData.Writer writer(ClassInitializationInfo.Builder delegate) {
        return new ClassInitializationInfoWriterAdapter(delegate);
    }

    public static ClassInitializationInfoData.Loader loader(ClassInitializationInfo.Reader delegate) {
        return new ClassInitializationInfoLoaderAdapter(delegate);
    }
}

record ClassInitializationInfoWriterAdapter(ClassInitializationInfo.Builder delegate) implements ClassInitializationInfoData.Writer {
    @Override
    public void setIsInitialized(boolean value) {
        delegate.setIsInitialized(value);
    }

    @Override
    public void setIsInErrorState(boolean value) {
        delegate.setIsInErrorState(value);
    }

    @Override
    public void setIsLinked(boolean value) {
        delegate.setIsLinked(value);
    }

    @Override
    public void setHasInitializer(boolean value) {
        delegate.setHasInitializer(value);
    }

    @Override
    public void setIsBuildTimeInitialized(boolean value) {
        delegate.setIsBuildTimeInitialized(value);
    }

    @Override
    public void setIsTracked(boolean value) {
        delegate.setIsTracked(value);
    }

    @Override
    public void setInitializerMethodId(int value) {
        delegate.setInitializerMethodId(value);
    }
}

record ClassInitializationInfoLoaderAdapter(ClassInitializationInfo.Reader delegate) implements ClassInitializationInfoData.Loader {
    @Override
    public boolean getIsInitialized() {
        return delegate.getIsInitialized();
    }

    @Override
    public boolean getIsInErrorState() {
        return delegate.getIsInErrorState();
    }

    @Override
    public boolean getIsLinked() {
        return delegate.getIsLinked();
    }

    @Override
    public boolean getHasInitializer() {
        return delegate.getHasInitializer();
    }

    @Override
    public boolean getIsBuildTimeInitialized() {
        return delegate.getIsBuildTimeInitialized();
    }

    @Override
    public boolean getIsTracked() {
        return delegate.getIsTracked();
    }

    @Override
    public int getInitializerMethodId() {
        return delegate.getInitializerMethodId();
    }
}
