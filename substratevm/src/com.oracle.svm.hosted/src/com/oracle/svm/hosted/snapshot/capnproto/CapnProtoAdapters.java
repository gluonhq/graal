/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.snapshot.capnproto;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.ObjIntConsumer;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveArray;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList.Bool;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shaded.org.capnproto.PrimitiveList;
import com.oracle.svm.shaded.org.capnproto.StructBuilder;
import com.oracle.svm.shaded.org.capnproto.StructList;
import com.oracle.svm.shaded.org.capnproto.Text;
import com.oracle.svm.shaded.org.capnproto.TextList;

/** Helpers for converting Cap'n Proto values to Java values. */
public final class CapnProtoAdapters {
    private CapnProtoAdapters() {
    }

    public static <S, T> SnapshotStructList.Loader<T> wrapStructListLoader(StructList.Reader<S> reader, Function<S, T> mapper) {
        return new StructListAdapter<>(reader::size, index -> mapper.apply(reader.get(index)));
    }

    public static <S extends StructBuilder, T> SnapshotStructList.Writer<T> wrapStructListWriter(StructList.Builder<S> builder, Function<S, T> mapper) {
        return new StructListAdapter<>(builder::size, index -> mapper.apply(builder.get(index)));
    }

    private record StructListAdapter<T>(IntSupplier sizeSupplier, IntFunction<T> getFunction) implements SnapshotStructList.Loader<T>, SnapshotStructList.Writer<T> {
        @Override
        public int size() {
            return sizeSupplier.getAsInt();
        }

        @Override
        public T get(int index) {
            return getFunction.apply(index);
        }

        @Override
        public Iterator<T> iterator() {
            return snapshotIterator(sizeSupplier, getFunction);
        }
    }

    public static SnapshotPrimitiveList.Int.Loader wrapIntListLoader(PrimitiveList.Int.Reader reader) {
        return new SnapshotPrimitiveList.Int.Loader() {
            @Override
            public int size() {
                return reader.size();
            }

            @Override
            public int get(int index) {
                return reader.get(index);
            }

            @Override
            public Iterator<Integer> iterator() {
                return snapshotIterator(reader::size, reader::get);
            }
        };
    }

    public static SnapshotPrimitiveList.Int.Writer wrapIntListWriter(PrimitiveList.Int.Builder builder) {
        return new SnapshotPrimitiveList.Int.Writer() {
            @Override
            public int size() {
                return builder.size();
            }

            @Override
            public void set(int index, int value) {
                builder.set(index, value);
            }
        };
    }

    public static Bool.Loader wrapBoolListLoader(PrimitiveList.Boolean.Reader reader) {
        return new Bool.Loader() {
            @Override
            public int size() {
                return reader.size();
            }

            @Override
            public boolean get(int index) {
                return reader.get(index);
            }

            @Override
            public Iterator<Boolean> iterator() {
                return snapshotIterator(reader::size, reader::get);
            }
        };
    }

    public static Bool.Writer wrapBoolListWriter(PrimitiveList.Boolean.Builder builder) {
        return new Bool.Writer() {
            @Override
            public int size() {
                return builder.size();
            }

            @Override
            public void set(int index, boolean value) {
                builder.set(index, value);
            }
        };
    }

    public static SnapshotStringList.Loader wrapStringListLoader(TextList.Reader reader) {
        return new SnapshotStringList.Loader() {
            @Override
            public int size() {
                return reader.size();
            }

            @Override
            public String get(int index) {
                return reader.get(index).toString();
            }

            @Override
            public Iterator<String> iterator() {
                return snapshotIterator(reader::size, this::get);
            }
        };
    }

    public static SnapshotStringList.Writer wrapStringListWriter(TextList.Builder builder) {
        return new SnapshotStringList.Writer() {
            @Override
            public int size() {
                return builder.size();
            }

            @Override
            public void set(int index, String value) {
                builder.set(index, new Text.Reader(value));
            }
        };
    }

    public static Object toArray(PrimitiveArray.Reader reader) {
        return switch (reader.which()) {
            case Z -> toBooleanArray(reader.getZ());
            case B -> toByteArray(reader.getB());
            case S -> toShortArray(reader.getS());
            case C -> toCharArray(reader.getC());
            case I -> toIntArray(reader.getI());
            case F -> toFloatArray(reader.getF());
            case J -> toLongArray(reader.getJ());
            case D -> toDoubleArray(reader.getD());
            case _NOT_IN_SCHEMA -> throw new IllegalStateException("Unexpected primitive array kind");
        };
    }

    private static boolean[] toBooleanArray(PrimitiveList.Boolean.Reader reader) {
        return toArray(reader.size(), boolean[]::new, (a, i) -> a[i] = reader.get(i));
    }

    private static byte[] toByteArray(PrimitiveList.Byte.Reader reader) {
        return toArray(reader.size(), byte[]::new, (a, i) -> a[i] = reader.get(i));
    }

    private static short[] toShortArray(PrimitiveList.Short.Reader reader) {
        return toArray(reader.size(), short[]::new, (a, i) -> a[i] = reader.get(i));
    }

    private static char[] toCharArray(PrimitiveList.Short.Reader reader) {
        return toArray(reader.size(), char[]::new, (a, i) -> a[i] = (char) reader.get(i));
    }

    private static int[] toIntArray(PrimitiveList.Int.Reader reader) {
        return toArray(reader.size(), int[]::new, (a, i) -> a[i] = reader.get(i));
    }

    private static float[] toFloatArray(PrimitiveList.Float.Reader reader) {
        return toArray(reader.size(), float[]::new, (a, i) -> a[i] = reader.get(i));
    }

    private static long[] toLongArray(PrimitiveList.Long.Reader reader) {
        return toArray(reader.size(), long[]::new, (a, i) -> a[i] = reader.get(i));
    }

    private static double[] toDoubleArray(PrimitiveList.Double.Reader reader) {
        return toArray(reader.size(), double[]::new, (a, i) -> a[i] = reader.get(i));
    }

    private static <T> T toArray(int length, IntFunction<T> init, ObjIntConsumer<T> setter) {
        T array = init.apply(length);
        for (int i = 0; i < length; i++) {
            setter.accept(array, i);
        }
        return array;
    }

    private static <T> Iterator<T> snapshotIterator(IntSupplier size, IntFunction<T> get) {
        return new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < size.getAsInt();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return get.apply(index++);
            }
        };
    }
}
