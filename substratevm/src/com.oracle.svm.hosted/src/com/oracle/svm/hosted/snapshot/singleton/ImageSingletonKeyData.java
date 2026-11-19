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
package com.oracle.svm.hosted.snapshot.singleton;

/**
 * Persisted representation of an image singleton key.
 * <p>
 * The key describes how the image singleton registry should reconnect a singleton consumer to the
 * persisted object or key-store entry that provides its state.
 */
public interface ImageSingletonKeyData {
    interface Writer {
        void setKeyClassName(String value);

        void setPersistFlag(int value);

        void setObjectId(int value);

        void setIsInitialLayerOnly(boolean value);

        void setConstantId(int value);

        void setKeyStoreId(int value);
    }

    interface Loader {
        String getKeyClassName();

        int getPersistFlag();

        int getObjectId();

        boolean getIsInitialLayerOnly();

        int getConstantId();

        int getKeyStoreId();
    }
}
