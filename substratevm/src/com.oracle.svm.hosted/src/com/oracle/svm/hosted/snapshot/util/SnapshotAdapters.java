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

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList.Bool;

public final class SnapshotAdapters {
    private SnapshotAdapters() {
    }

    public static void forEach(SnapshotPrimitiveList.Int.Loader reader, IntConsumer action) {
        for (int i = 0; i < reader.size(); i++) {
            action.accept(reader.get(i));
        }
    }

    public static void forEach(SnapshotStringList.Loader reader, Consumer<String> action) {
        for (int i = 0; i < reader.size(); i++) {
            action.accept(reader.get(i));
        }
    }

    public static <T> T[] toArray(SnapshotPrimitiveList.Int.Loader reader, IntFunction<? extends T> mapper, IntFunction<T[]> arrayGenerator) {
        T[] array = arrayGenerator.apply(reader.size());
        for (int i = 0; i < reader.size(); i++) {
            array[i] = mapper.apply(reader.get(i));
        }
        return array;
    }

    public static <R, T> T[] toArray(SnapshotStructList.Loader<R> reader, Function<? super R, ? extends T> mapper, IntFunction<T[]> arrayGenerator) {
        T[] array = arrayGenerator.apply(reader.size());
        for (int i = 0; i < reader.size(); i++) {
            array[i] = mapper.apply(reader.get(i));
        }
        return array;
    }

    public static <T> T[] toArray(SnapshotStringList.Loader reader, Function<String, ? extends T> mapper, IntFunction<T[]> arrayGenerator) {
        T[] array = arrayGenerator.apply(reader.size());
        for (int i = 0; i < reader.size(); i++) {
            array[i] = mapper.apply(reader.get(i));
        }
        return array;
    }

    public static <T, U extends Collection<T>> U toCollection(SnapshotPrimitiveList.Int.Loader reader, IntFunction<? extends T> mapper, Supplier<U> collectionFactory) {
        U collection = collectionFactory.get();
        for (int i = 0; i < reader.size(); i++) {
            collection.add(mapper.apply(reader.get(i)));
        }
        return collection;
    }

    public static <U extends Collection<String>> U toCollection(SnapshotStringList.Loader reader, Supplier<U> collectionFactory) {
        return toCollection(reader, Function.identity(), collectionFactory);
    }

    public static <T, U extends Collection<T>> U toCollection(SnapshotStringList.Loader reader, Function<String, ? extends T> mapper, Supplier<U> collectionFactory) {
        U collection = collectionFactory.get();
        for (int i = 0; i < reader.size(); i++) {
            collection.add(mapper.apply(reader.get(i)));
        }
        return collection;
    }

    public static boolean[] toBooleanArray(Bool.Loader booleanReader) {
        boolean[] booleanArray = new boolean[booleanReader.size()];
        for (int i = 0; i < booleanReader.size(); i++) {
            booleanArray[i] = booleanReader.get(i);
        }
        return booleanArray;
    }

    public static int[] toIntArray(SnapshotPrimitiveList.Int.Loader intReader) {
        int[] intArray = new int[intReader.size()];
        for (int i = 0; i < intReader.size(); i++) {
            intArray[i] = intReader.get(i);
        }
        return intArray;
    }

    public static String[] toStringArray(SnapshotStringList.Loader reader) {
        return toArray(reader, Function.identity(), String[]::new);
    }

    public static <T> T binarySearchUnique(int key, SnapshotStructList.Loader<T> sortedList, ToIntFunction<T> keyExtractor) {
        int low = 0;
        int high = sortedList.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            T value = sortedList.get(mid);
            int candidate = keyExtractor.applyAsInt(value);
            if (candidate < key) {
                low = mid + 1;
            } else if (candidate > key) {
                high = mid - 1;
            } else {
                return value;
            }
        }
        return null;
    }
}
