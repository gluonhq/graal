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
package com.oracle.svm.hosted.imagelayer;

import java.util.List;

import com.oracle.svm.hosted.snapshot.util.PrimitiveArrayData;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

final class SnapshotPrimitiveArrays {
    private SnapshotPrimitiveArrays() {
    }

    /**
     * Persists primitive-array data from either Java collections, host primitive arrays, or guest
     * {@link JavaConstant} arrays.
     */
    static void write(PrimitiveArrayData.Writer builder, JavaKind componentKind, Object array) {
        GuestAccess access = GuestAccess.get();
        if (array instanceof List<?> l) {
            switch (componentKind) {
                case Boolean -> builder.setBooleanArray(l.size(), i -> (boolean) l.get(i));
                case Byte -> builder.setByteArray(l.size(), i -> (byte) l.get(i));
                case Short -> builder.setShortArray(l.size(), i -> (short) l.get(i));
                case Char -> builder.setCharArray(l.size(), i -> (char) l.get(i));
                case Int -> builder.setIntArray(l.size(), i -> (int) l.get(i));
                case Long -> builder.setLongArray(l.size(), i -> (long) l.get(i));
                case Float -> builder.setFloatArray(l.size(), i -> (float) l.get(i));
                case Double -> builder.setDoubleArray(l.size(), i -> (double) l.get(i));
                default -> throw new IllegalArgumentException("Unsupported kind: " + componentKind);
            }
        } else if (array instanceof JavaConstant constant) {
            ConstantReflectionProvider constantReflection = access.getProviders().getConstantReflection();
            Integer length = constantReflection.readArrayLength(constant);
            VMError.guarantee(length != null, "%s is not an array.", constant);
            switch (componentKind) {
                case Boolean -> builder.setBooleanArray(length, i -> constantReflection.readArrayElement(constant, i).asBoolean());
                case Byte -> builder.setByteArray(length, i -> (byte) constantReflection.readArrayElement(constant, i).asInt());
                case Short -> builder.setShortArray(length, i -> (short) constantReflection.readArrayElement(constant, i).asInt());
                case Char -> builder.setCharArray(length, i -> (char) constantReflection.readArrayElement(constant, i).asInt());
                case Int -> builder.setIntArray(length, i -> constantReflection.readArrayElement(constant, i).asInt());
                case Long -> builder.setLongArray(length, i -> constantReflection.readArrayElement(constant, i).asLong());
                case Float -> builder.setFloatArray(length, i -> constantReflection.readArrayElement(constant, i).asFloat());
                case Double -> builder.setDoubleArray(length, i -> constantReflection.readArrayElement(constant, i).asDouble());
                default -> throw new IllegalArgumentException("Unsupported kind: " + componentKind);
            }
        } else {
            assert access.lookupType(componentKind.toJavaClass()).equals(access.lookupType(array.getClass()).getComponentType()) : "%s != %s"
                            .formatted(access.lookupType(componentKind.toJavaClass()), access.lookupType(array.getClass()).getComponentType());
            switch (array) {
                case boolean[] a -> builder.setBooleanArray(a);
                case byte[] a -> builder.setByteArray(a);
                case short[] a -> builder.setShortArray(a);
                case char[] a -> builder.setCharArray(a);
                case int[] a -> builder.setIntArray(a);
                case long[] a -> builder.setLongArray(a);
                case float[] a -> builder.setFloatArray(a);
                case double[] a -> builder.setDoubleArray(a);
                default -> throw new IllegalArgumentException("Unsupported kind: " + componentKind);
            }
        }
    }
}
