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

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.DispatchSlotInfo;
import com.oracle.svm.hosted.snapshot.dynamichub.DispatchSlotInfoData;

public final class CapnProtoDispatchSlotInfoData {
    public static DispatchSlotInfoData.Writer writer(DispatchSlotInfo.Builder delegate) {
        return new DispatchSlotInfoWriterAdapter(delegate);
    }

    public static DispatchSlotInfoData.Loader loader(DispatchSlotInfo.Reader delegate) {
        return new DispatchSlotInfoLoaderAdapter(delegate);
    }
}

record DispatchSlotInfoWriterAdapter(DispatchSlotInfo.Builder delegate) implements DispatchSlotInfoData.Writer {
    @Override
    public void setSlotIndex(int value) {
        delegate.setSlotIndex(value);
    }

    @Override
    public void setResolutionStatus(int value) {
        delegate.setResolutionStatus(value);
    }

    @Override
    public void setDeclaredHostedMethodIndex(int value) {
        delegate.setDeclaredHostedMethodIndex(value);
    }

    @Override
    public void setResolvedHostedMethodIndex(int value) {
        delegate.setResolvedHostedMethodIndex(value);
    }

    @Override
    public void setSlotSymbolName(String value) {
        delegate.setSlotSymbolName(value);
    }
}

record DispatchSlotInfoLoaderAdapter(DispatchSlotInfo.Reader delegate) implements DispatchSlotInfoData.Loader {
    @Override
    public int getSlotIndex() {
        return delegate.getSlotIndex();
    }

    @Override
    public int getResolutionStatus() {
        return delegate.getResolutionStatus();
    }

    @Override
    public int getDeclaredHostedMethodIndex() {
        return delegate.getDeclaredHostedMethodIndex();
    }

    @Override
    public int getResolvedHostedMethodIndex() {
        return delegate.getResolvedHostedMethodIndex();
    }

    @Override
    public String getSlotSymbolName() {
        return delegate.getSlotSymbolName().toString();
    }
}
