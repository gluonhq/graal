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
package com.oracle.svm.core.interpreter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes how an interpreter enter stub identifies its target method.
 * <p>
 * The low-level architecture-specific enter-stub code uses this annotation to decide whether it can
 * emit a specialized fast path before falling back to the generic Java helper.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InterpreterEnterStub {
    /**
     * Returns the kind of interpreter enter stub.
     */
    Kind value();

    /**
     * Kinds of interpreter enter stubs.
     */
    enum Kind {
        /**
         * The target method is identified indirectly via its offset in the Entry Stub Table (EST).
         */
        EST_OFFSET,
        /**
         * The target method is passed directly as an {@code InterpreterResolvedJavaMethod}.
         */
        DIRECT,
        /**
         * The target method is resolved via the receiver's vtable and a vtable index.
         */
        VTABLE,
    }
}
