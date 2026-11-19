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
package com.oracle.svm.hosted.snapshot.util;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

/** Persisted primitive array. */
public interface PrimitiveArrayData {
    interface Writer {
        void setBooleanArray(boolean[] value);

        default void setBooleanArray(int length, BooleanElementLoader loader) {
            setArray(length, boolean[]::new, (a, i) -> a[i] = loader.get(i), this::setBooleanArray);
        }

        void setByteArray(byte[] value);

        default void setByteArray(int length, ByteElementLoader loader) {
            setArray(length, byte[]::new, (a, i) -> a[i] = loader.get(i), this::setByteArray);
        }

        void setShortArray(short[] value);

        default void setShortArray(int length, ShortElementLoader loader) {
            setArray(length, short[]::new, (a, i) -> a[i] = loader.get(i), this::setShortArray);
        }

        void setCharArray(char[] value);

        default void setCharArray(int length, CharElementLoader loader) {
            setArray(length, char[]::new, (a, i) -> a[i] = loader.get(i), this::setCharArray);
        }

        void setIntArray(int[] value);

        default void setIntArray(int length, IntElementLoader loader) {
            setArray(length, int[]::new, (a, i) -> a[i] = loader.get(i), this::setIntArray);
        }

        void setFloatArray(float[] value);

        default void setFloatArray(int length, FloatElementLoader loader) {
            setArray(length, float[]::new, (a, i) -> a[i] = loader.get(i), this::setFloatArray);
        }

        void setLongArray(long[] value);

        default void setLongArray(int length, LongElementLoader loader) {
            setArray(length, long[]::new, (a, i) -> a[i] = loader.get(i), this::setLongArray);
        }

        void setDoubleArray(double[] value);

        default void setDoubleArray(int length, DoubleElementLoader loader) {
            setArray(length, double[]::new, (a, i) -> a[i] = loader.get(i), this::setDoubleArray);
        }

        private static <T> void setArray(int length, IntFunction<T> init, ObjIntConsumer<T> setter, Consumer<T> arraySetter) {
            T array = init.apply(length);
            for (int i = 0; i < length; i++) {
                setter.accept(array, i);
            }
            arraySetter.accept(array);
        }
    }

    interface BooleanElementLoader {
        boolean get(int index);
    }

    interface ByteElementLoader {
        byte get(int index);
    }

    interface ShortElementLoader {
        short get(int index);
    }

    interface CharElementLoader {
        char get(int index);
    }

    interface IntElementLoader {
        int get(int index);
    }

    interface FloatElementLoader {
        float get(int index);
    }

    interface LongElementLoader {
        long get(int index);
    }

    interface DoubleElementLoader {
        double get(int index);
    }

    interface Loader {
        Object toArray();
    }
}
