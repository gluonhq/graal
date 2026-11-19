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
package com.oracle.svm.hosted.snapshot.constant;

import com.oracle.svm.hosted.snapshot.util.PrimitiveArrayData;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

/** Persisted representation of a constant. */
public interface PersistedConstantData {
    enum Kind {
        OBJECT,
        PRIMITIVE_DATA,
        RELOCATABLE
    }

    interface Writer {
        void setObjectOffset(long value);

        void setId(int value);

        void setTypeId(int value);

        void setIdentityHashCode(int value);

        ObjectValue.Writer initObject();

        ObjectValue.Writer getObject();

        PrimitiveArrayData.Writer initPrimitiveData();

        Relocatable.Writer initRelocatable();

        void setParentConstantId(int value);

        void setParentIndex(int value);

        void setIsSimulated(boolean value);
    }

    interface Loader {
        Kind kind();

        boolean isObject();

        int getId();

        int getTypeId();

        long getObjectOffset();

        int getIdentityHashCode();

        int getParentConstantId();

        int getParentIndex();

        boolean getIsSimulated();

        ObjectValue.Loader getObject();

        PrimitiveArrayData.Loader getPrimitiveData();

        Relocatable.Loader getRelocatable();
    }

    interface ObjectValue {
        enum Kind {
            INSTANCE,
            OBJECT_ARRAY
        }

        interface Writer {
            void setInstance();

            void setObjectArray();

            SnapshotStructList.Writer<ConstantReferenceData.Writer> initData(int size);

            RelinkingData.Writer getRelinking();
        }

        interface Loader {
            Kind kind();

            SnapshotStructList.Loader<ConstantReferenceData.Loader> getData();

            RelinkingData.Loader getRelinking();
        }
    }

    interface Relocatable {
        interface Writer {
            void setKey(String value);
        }

        interface Loader {
            String getKey();
        }
    }
}
