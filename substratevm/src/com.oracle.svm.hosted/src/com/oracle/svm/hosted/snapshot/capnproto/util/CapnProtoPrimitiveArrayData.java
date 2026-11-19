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
package com.oracle.svm.hosted.snapshot.capnproto.util;

import com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveArray;
import com.oracle.svm.hosted.snapshot.util.PrimitiveArrayData;
import com.oracle.svm.shaded.org.capnproto.ListBuilder;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

public final class CapnProtoPrimitiveArrayData {
    public static PrimitiveArrayData.Writer writer(PrimitiveArray.Builder delegate) {
        return new PrimitiveArrayWriterAdapter(delegate);
    }

    public static PrimitiveArrayData.Loader loader(PrimitiveArray.Reader delegate) {
        return new PrimitiveArrayLoaderAdapter(delegate);
    }
}

record PrimitiveArrayWriterAdapter(PrimitiveArray.Builder delegate) implements PrimitiveArrayData.Writer {
    @Override
    public void setBooleanArray(boolean[] value) {
        setArray(value.length, delegate::initZ, (b, i) -> b.set(i, value[i]));
    }

    @Override
    public void setByteArray(byte[] value) {
        setArray(value.length, delegate::initB, (b, i) -> b.set(i, value[i]));
    }

    @Override
    public void setShortArray(short[] value) {
        setArray(value.length, delegate::initS, (b, i) -> b.set(i, value[i]));
    }

    @Override
    public void setCharArray(char[] value) {
        setArray(value.length, delegate::initC, (b, i) -> b.set(i, (short) value[i]));
    }

    @Override
    public void setIntArray(int[] value) {
        setArray(value.length, delegate::initI, (b, i) -> b.set(i, value[i]));
    }

    @Override
    public void setFloatArray(float[] value) {
        setArray(value.length, delegate::initF, (b, i) -> b.set(i, value[i]));
    }

    @Override
    public void setLongArray(long[] value) {
        setArray(value.length, delegate::initJ, (b, i) -> b.set(i, value[i]));
    }

    @Override
    public void setDoubleArray(double[] value) {
        setArray(value.length, delegate::initD, (b, i) -> b.set(i, value[i]));
    }

    private static <T extends ListBuilder> void setArray(int length, IntFunction<T> init, ObjIntConsumer<T> setter) {
        T builder = init.apply(length);
        for (int i = 0; i < length; i++) {
            setter.accept(builder, i);
        }
    }
}

record PrimitiveArrayLoaderAdapter(PrimitiveArray.Reader delegate) implements PrimitiveArrayData.Loader {
    @Override
    public Object toArray() {
        return CapnProtoAdapters.toArray(delegate);
    }
}
