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
package com.oracle.svm.hosted.snapshot.capnproto.elements;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedHostedMethod;
import com.oracle.svm.hosted.snapshot.elements.PersistedHostedMethodData;

public final class CapnProtoPersistedHostedMethodData {
    public static PersistedHostedMethodData.Writer writer(PersistedHostedMethod.Builder delegate) {
        return new PersistedHostedMethodWriterAdapter(delegate);
    }

    public static PersistedHostedMethodData.Loader loader(PersistedHostedMethod.Reader delegate) {
        return new PersistedHostedMethodLoaderAdapter(delegate);
    }
}

record PersistedHostedMethodWriterAdapter(PersistedHostedMethod.Builder delegate) implements PersistedHostedMethodData.Writer {
    @Override
    public void setIndex(int value) {
        delegate.setIndex(value);
    }

    @Override
    public void setMethodId(int value) {
        delegate.setMethodId(value);
    }

    @Override
    public void setSymbolName(String value) {
        delegate.setSymbolName(value);
    }

    @Override
    public void setVTableIndex(int value) {
        delegate.setVTableIndex(value);
    }

    @Override
    public void setIsVirtualCallTarget(boolean value) {
        delegate.setIsVirtualCallTarget(value);
    }

    @Override
    public void setInstalledOffset(int value) {
        delegate.setInstalledOffset(value);
    }

    @Override
    public void setHostedMethodName(String value) {
        delegate.setHostedMethodName(value);
    }

    @Override
    public void setHostedMethodUniqueName(String value) {
        delegate.setHostedMethodUniqueName(value);
    }
}

record PersistedHostedMethodLoaderAdapter(PersistedHostedMethod.Reader delegate) implements PersistedHostedMethodData.Loader {
    @Override
    public int getIndex() {
        return delegate.getIndex();
    }

    @Override
    public int getMethodId() {
        return delegate.getMethodId();
    }

    @Override
    public String getSymbolName() {
        return delegate.getSymbolName().toString();
    }

    @Override
    public int getVTableIndex() {
        return delegate.getVTableIndex();
    }

    @Override
    public boolean getIsVirtualCallTarget() {
        return delegate.getIsVirtualCallTarget();
    }

    @Override
    public int getInstalledOffset() {
        return delegate.getInstalledOffset();
    }

    @Override
    public String getHostedMethodName() {
        return delegate.getHostedMethodName().toString();
    }

    @Override
    public String getHostedMethodUniqueName() {
        return delegate.getHostedMethodUniqueName().toString();
    }
}
