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

import com.oracle.svm.hosted.snapshot.c.CodeLocationData;
import com.oracle.svm.hosted.snapshot.c.LinkingInfoData;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.CGlobalDataInfo;

public final class CapnProtoLinkingInfoData {
    public static LinkingInfoData.Writer writer(CGlobalDataInfo.LinkingInfo.Builder delegate) {
        return new LinkingInfoWriterAdapter(delegate);
    }

    public static LinkingInfoData.Loader loader(CGlobalDataInfo.LinkingInfo.Reader delegate) {
        return new LinkingInfoLoaderAdapter(delegate);
    }
}

record LinkingInfoWriterAdapter(CGlobalDataInfo.LinkingInfo.Builder delegate) implements LinkingInfoData.Writer {
    @Override
    public void setOriginalSymbolName(String value) {
        delegate.setOriginalSymbolName(value);
    }

    @Override
    public CodeLocationData.Writer initCodeLocation() {
        return CapnProtoCodeLocationData.writer(delegate.initCodeLocation());
    }
}

record LinkingInfoLoaderAdapter(CGlobalDataInfo.LinkingInfo.Reader delegate) implements LinkingInfoData.Loader {
    @Override
    public boolean hasOriginalSymbolName() {
        return delegate.hasOriginalSymbolName();
    }

    @Override
    public String getOriginalSymbolName() {
        return delegate.getOriginalSymbolName().toString();
    }

    @Override
    public boolean hasCodeLocation() {
        return delegate.hasCodeLocation();
    }

    @Override
    public CodeLocationData.Loader getCodeLocation() {
        return CapnProtoCodeLocationData.loader(delegate.getCodeLocation());
    }
}
