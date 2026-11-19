/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, IBM Inc. All rights reserved.
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

package com.oracle.svm.core.jfr;

import java.util.List;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.jfr.internal.tracing.PlatformTracer;
import jdk.jfr.internal.tracing.Modification;

/**
 * Platform method tracing is not supported at the moment. Keep an API-compatible no-op substitution
 * here so analysis resolves reachable entry points without pulling in the unsupported method tracer
 * implementation.
 *
 * JDK 25 added additional public entry points such as {@code emitTiming()} that are reached from
 * JFR shutdown hooks, so this substitution must track the current public API surface.
 */
@Substitute
@TargetClass(value = PlatformTracer.class)
public final class Target_jdk_jfr_internal_tracing_PlatformTracer {
    @Substitute
    public static byte[] onMethodTrace(@SuppressWarnings("unused") Module module,
                    @SuppressWarnings("unused") ClassLoader classLoader,
                    @SuppressWarnings("unused") String className,
                    @SuppressWarnings("unused") byte[] oldBytecode,
                    @SuppressWarnings("unused") long[] ids,
                    @SuppressWarnings("unused") String[] names,
                    @SuppressWarnings("unused") String[] signatures,
                    @SuppressWarnings("unused") int[] modifications) {
        return null;
    }

    @Substitute
    public static void emitTiming() {
    }

    @Substitute
    public static void addObjectTiming(@SuppressWarnings("unused") long duration) {
    }

    @Substitute
    public static void addTiming(@SuppressWarnings("unused") long id, @SuppressWarnings("unused") long duration) {
    }

    @Substitute
    public static void setFilters(@SuppressWarnings("unused") Modification modification, @SuppressWarnings("unused") List<String> filters) {
    }

    @Substitute
    public static void publishClass(@SuppressWarnings("unused") long classId) {
    }

    @Substitute
    public static void initialize() {
    }
}
