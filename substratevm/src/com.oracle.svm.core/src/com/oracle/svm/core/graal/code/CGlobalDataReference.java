/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.vm.ci.code.site.Reference;

@Platforms(Platform.HOSTED_ONLY.class)
public abstract sealed class CGlobalDataReference extends Reference permits CGlobalDataDirectReference, CGlobalDataIndirectReference {

    private final CGlobalDataInfo dataInfo;

    protected CGlobalDataReference(CGlobalDataInfo dataInfo) {
        this.dataInfo = Objects.requireNonNull(dataInfo);
    }

    public CGlobalDataInfo getDataInfo() {
        return dataInfo;
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        CGlobalDataReference that = (CGlobalDataReference) o;
        return dataInfo.equals(that.dataInfo);
    }

    @Override
    public final int hashCode() {
        return dataInfo.hashCode();
    }
}
