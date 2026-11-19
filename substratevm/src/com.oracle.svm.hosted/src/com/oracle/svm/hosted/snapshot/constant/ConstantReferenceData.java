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

import com.oracle.svm.hosted.snapshot.c.CEntryPointLiteralReferenceData;
import com.oracle.svm.hosted.snapshot.util.PrimitiveValueData;

/**
 * Reference to persisted constant data.
 * <p>
 * Constant references are the links inside persisted object payloads. They can point to another
 * persisted constant, encode an immediate primitive value, or describe special low-level values
 * such as method pointers and C global data references.
 */
public interface ConstantReferenceData {
    enum Kind {
        OBJECT_CONSTANT,
        NULL_POINTER,
        PRIMITIVE_VALUE,
        METHOD_POINTER,
        METHOD_OFFSET,
        C_ENTRY_POINT_LITERAL_CODE_POINTER,
        C_GLOBAL_DATA_BASE_POINTER,
        NOT_MATERIALIZED
    }

    interface Writer {
        ObjectConstant.Writer initObjectConstant();

        PrimitiveValueData.Writer initPrimitiveValue();

        void setNullPointer();

        MethodPointer.Writer initMethodPointer();

        MethodOffset.Writer initMethodOffset();

        CEntryPointLiteralReferenceData.Writer initCEntryPointLiteralCodePointer();

        void setCGlobalDataBasePointer();

        void setNotMaterialized();
    }

    interface Loader {
        Kind kind();

        boolean isObjectConstant();

        boolean isNotMaterialized();

        boolean isMethodPointer();

        boolean isMethodOffset();

        boolean isCEntryPointLiteralCodePointer();

        boolean isCGlobalDataBasePointer();

        ObjectConstant.Loader getObjectConstant();

        PrimitiveValueData.Loader getPrimitiveValue();

        MethodPointer.Loader getMethodPointer();

        MethodOffset.Loader getMethodOffset();

        CEntryPointLiteralReferenceData.Loader getCEntryPointLiteralCodePointer();
    }

    interface ObjectConstant {
        interface Writer {
            void setConstantId(int value);
        }

        interface Loader {
            int getConstantId();
        }
    }

    interface MethodPointer {
        interface Writer {
            void setMethodId(int value);

            void setPermitsRewriteToPLT(boolean value);
        }

        interface Loader {
            int getMethodId();

            boolean getPermitsRewriteToPLT();
        }
    }

    interface MethodOffset {
        interface Writer {
            void setMethodId(int value);
        }

        interface Loader {
            int getMethodId();
        }
    }
}
