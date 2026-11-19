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

import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Image-heap model for primitive arrays whose snapshot storage is represented as a guest
 * {@link JavaConstant} array object.
 */
public final class ImageHeapPrimitiveArray extends ImageHeapArray {

    private static final class PrimitiveArrayData extends ConstantData {

        /**
         * A copy of the array represented by the hosted object.
         */
        private final JavaConstant snapshot;
        private final int length;

        private PrimitiveArrayData(AnalysisType type, JavaConstant hostedObject, JavaConstant snapshot, int length, int identityHashCode, int id) {
            super(type, hostedObject, identityHashCode, id);
            this.snapshot = snapshot;
            this.length = length;
            assert type.isArray() && type.getComponentType().isPrimitive() : type;
        }
    }

    /**
     * Creates a detached primitive-array snapshot for arrays that are not backed by a hosted
     * object.
     */
    ImageHeapPrimitiveArray(AnalysisType type, int length) {
        super(new PrimitiveArrayData(type, null,
                        /* Without a hosted object, we need to create a backing primitive array. */
                        GuestAccess.get().createPrimitiveArray(type.getComponentType().getJavaKind(), length),
                        length, -1, -1), false);
    }

    /**
     * Creates a primitive-array snapshot from a hosted object with default metadata fields.
     */
    ImageHeapPrimitiveArray(AnalysisType type, JavaConstant hostedObject, JavaConstant snapshot, int length) {
        this(type, hostedObject, snapshot, length, -1, -1);
    }

    /**
     * Creates a primitive-array snapshot with explicit identity metadata for image-layer
     * persistence.
     */
    public ImageHeapPrimitiveArray(AnalysisType type, JavaConstant hostedObject, JavaConstant snapshot, int length, int identityHashCode, int id) {
        super(new PrimitiveArrayData(type, hostedObject,
                        /* We need a clone of the hosted array so that we have a stable snapshot. */
                        cloneArray(snapshot),
                        length, identityHashCode, id), false);
    }

    private ImageHeapPrimitiveArray(ConstantData constantData, boolean compressed) {
        super(constantData, compressed);
    }

    @Override
    public PrimitiveArrayData getConstantData() {
        return (PrimitiveArrayData) super.getConstantData();
    }

    /**
     * Clones a primitive array guest constant to preserve a stable snapshot copy.
     */
    private static JavaConstant cloneArray(JavaConstant original) {
        return GuestAccess.get().clonePrimitiveArray(original);
    }

    /**
     * Returns the snapshot array constant that stores primitive element values for this model.
     */
    public JavaConstant getArray() {
        return getConstantData().snapshot;
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(ImageHeapArray, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason, Consumer)}.
     */
    @Override
    public Object getElement(int idx) {
        return readElementValue(idx);
    }

    /**
     * Reads a primitive element from the snapshot array as a guest {@link JavaConstant}.
     */
    @Override
    public JavaConstant readElementValue(int idx) {
        return GuestAccess.get().getProviders().getConstantReflection().readArrayElement(getArray(), idx);
    }

    /**
     * Writes a primitive element into the snapshot array after validating the component kind.
     */
    @Override
    public void setElement(int idx, JavaConstant value) {
        if (value.getJavaKind() != constantData.type.getComponentType().getJavaKind()) {
            throw AnalysisError.shouldNotReachHere("Cannot store value of kind " + value.getJavaKind() + " into primitive array of type " + getConstantData().type);
        }
        GuestAccess.get().writeArrayElement(getArray(), idx, value);
    }

    @Override
    public int getLength() {
        return getConstantData().length;
    }

    @Override
    public JavaConstant compress() {
        assert !compressed : this;
        return new ImageHeapPrimitiveArray(constantData, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed : this;
        return new ImageHeapPrimitiveArray(constantData, false);
    }

    /**
     * Produces a cloned primitive-array constant that is detached from any hosted object.
     */
    @Override
    public ImageHeapConstant forObjectClone() {
        assert constantData.type.isCloneableWithAllocation() : "all arrays implement Cloneable";

        PrimitiveArrayData data = getConstantData();
        JavaConstant newArray = cloneArray(data.snapshot);
        /* The new constant is never backed by a hosted object, regardless of the input object. */
        return new ImageHeapPrimitiveArray(new PrimitiveArrayData(data.type, null, newArray, data.length, -1, -1), compressed);
    }
}
