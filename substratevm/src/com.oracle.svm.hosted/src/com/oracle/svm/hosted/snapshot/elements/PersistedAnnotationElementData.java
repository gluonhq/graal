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
package com.oracle.svm.hosted.snapshot.elements;

import com.oracle.svm.hosted.snapshot.util.PrimitiveArrayData;
import com.oracle.svm.hosted.snapshot.util.PrimitiveValueData;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

/** Persisted annotation value data from the annotation values map. */
public interface PersistedAnnotationElementData {
    enum Kind {
        STRING,
        ENUM,
        PRIMITIVE,
        PRIMITIVE_ARRAY,
        CLASS_NAME,
        ANNOTATION,
        MEMBERS,
        NOT_IN_SCHEMA
    }

    interface Writer {
        void setName(String value);

        void setString(String value);

        EnumValue.Writer initEnum();

        PrimitiveValueData.Writer initPrimitive();

        PrimitiveArrayData.Writer initPrimitiveArray();

        void setClassName(String value);

        PersistedAnnotationData.Writer initAnnotation();

        Members.Writer initMembers();
    }

    interface Loader {
        Kind kind();

        String getName();

        String getString();

        EnumValue.Loader getEnum();

        PrimitiveValueData.Loader getPrimitive();

        PrimitiveArrayData.Loader getPrimitiveArray();

        String getClassName();

        PersistedAnnotationData.Loader getAnnotation();

        Members.Loader getMembers();
    }

    interface EnumValue {
        interface Writer {
            void setClassName(String value);

            void setName(String value);
        }

        interface Loader {
            String getClassName();

            String getName();
        }
    }

    interface Members {
        interface Writer {
            void setClassName(String value);

            SnapshotStructList.Writer<PersistedAnnotationElementData.Writer> initMemberValues(int size);
        }

        interface Loader {
            String getClassName();

            SnapshotStructList.Loader<PersistedAnnotationElementData.Loader> getMemberValues();
        }
    }
}
