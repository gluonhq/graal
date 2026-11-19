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
package com.oracle.svm.hosted.snapshot.capnproto.c;

import com.oracle.svm.hosted.snapshot.c.CGlobalDataInfoData;
import com.oracle.svm.hosted.snapshot.c.LinkingInfoData;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.CGlobalDataInfo;

public final class CapnProtoCGlobalDataInfoData {
    public static CGlobalDataInfoData.Writer writer(CGlobalDataInfo.Builder delegate) {
        return new CGlobalDataInfoWriterAdapter(delegate);
    }

    public static CGlobalDataInfoData.Loader loader(CGlobalDataInfo.Reader delegate) {
        return new CGlobalDataInfoLoaderAdapter(delegate);
    }
}

record CGlobalDataInfoWriterAdapter(CGlobalDataInfo.Builder delegate) implements CGlobalDataInfoData.Writer {
    @Override
    public void setLayeredSymbolName(String value) {
        delegate.setLayeredSymbolName(value);
    }

    @Override
    public LinkingInfoData.Writer initLinkingInfo() {
        return CapnProtoLinkingInfoData.writer(delegate.initLinkingInfo());
    }

    @Override
    public void setNonConstant(boolean value) {
        delegate.setNonConstant(value);
    }

    @Override
    public void setIsGlobalSymbol(boolean value) {
        delegate.setIsGlobalSymbol(value);
    }

    @Override
    public void setIsSymbolReference(boolean value) {
        delegate.setIsSymbolReference(value);
    }
}

record CGlobalDataInfoLoaderAdapter(CGlobalDataInfo.Reader delegate) implements CGlobalDataInfoData.Loader {
    @Override
    public String getLayeredSymbolName() {
        return delegate.getLayeredSymbolName().toString();
    }

    @Override
    public LinkingInfoData.Loader getLinkingInfo() {
        return CapnProtoLinkingInfoData.loader(delegate.getLinkingInfo());
    }

    @Override
    public boolean getNonConstant() {
        return delegate.getNonConstant();
    }

    @Override
    public boolean getIsGlobalSymbol() {
        return delegate.getIsGlobalSymbol();
    }

    @Override
    public boolean getIsSymbolReference() {
        return delegate.getIsSymbolReference();
    }
}
