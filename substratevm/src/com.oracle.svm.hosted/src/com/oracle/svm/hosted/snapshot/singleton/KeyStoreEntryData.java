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

import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList.Bool;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList.Int;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;

/**
 * Persisted representation of a singleton key-store entry.
 * <p>
 * Each entry stores a string key and one supported primitive or list-shaped value.
 */
public interface KeyStoreEntryData {
    interface Writer {
        void setKey(String value);

        Value.Writer initValue();
    }

    interface Loader {
        String getKey();

        Value.Loader getValue();
    }

    interface Value {
        enum Kind {
            I,
            J,
            STR,
            IL,
            ZL,
            STRL,
            NOT_IN_SCHEMA
        }

        interface Writer {
            void setI(int value);

            void setJ(long value);

            void setStr(String value);

            Int.Writer initIl(int size);

            Bool.Writer initZl(int size);

            SnapshotStringList.Writer initStrl(int size);
        }

        interface Loader {
            Kind kind();

            int getI();

            long getJ();

            String getStr();

            Int.Loader getIl();

            Bool.Loader getZl();

            SnapshotStringList.Loader getStrl();
        }
    }
}
