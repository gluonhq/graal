/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.heap;

import java.lang.reflect.Array;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ImageHeapPrimitiveArray extends ImageHeapArray {

    private final Object array;
    private final int length;

    ImageHeapPrimitiveArray(ResolvedJavaType type, JavaConstant object, Object array, int length) {
        this(type, object, array, createIdentityHashCode(object), false, length);
    }

    private ImageHeapPrimitiveArray(ResolvedJavaType type, JavaConstant object, Object arrayObject, int identityHashCode, boolean compressed, int length) {
        super(type, object, identityHashCode, compressed);
        assert type.isArray() && type.getComponentType().isPrimitive();
        this.array = getClone(type.getComponentType().getJavaKind(), arrayObject);
        this.length = length;
    }

    private static Object getClone(JavaKind kind, Object arrayObject) {
        return switch (kind) {
            case Boolean -> ((boolean[]) arrayObject).clone();
            case Byte -> ((byte[]) arrayObject).clone();
            case Short -> ((short[]) arrayObject).clone();
            case Char -> ((char[]) arrayObject).clone();
            case Int -> ((int[]) arrayObject).clone();
            case Long -> ((long[]) arrayObject).clone();
            case Float -> ((float[]) arrayObject).clone();
            case Double -> ((double[]) arrayObject).clone();
            default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
        };
    }

    public Object getArray() {
        return array;
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(ImageHeapArray, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason, Consumer)}.
     */
    @Override
    public Object getElement(int idx) {
        return Array.get(array, idx);
    }

    @Override
    public JavaConstant readElementValue(int idx) {
        return JavaConstant.forBoxedPrimitive(getElement(idx));
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public JavaConstant compress() {
        assert !compressed;
        return new ImageHeapPrimitiveArray(type, hostedObject, array, identityHashCode, true, length);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed;
        return new ImageHeapPrimitiveArray(type, hostedObject, array, identityHashCode, false, length);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapPrimitiveArray) {
            return super.equals(o) && this.array == ((ImageHeapPrimitiveArray) o).array;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + System.identityHashCode(array);
        return result;
    }
}
