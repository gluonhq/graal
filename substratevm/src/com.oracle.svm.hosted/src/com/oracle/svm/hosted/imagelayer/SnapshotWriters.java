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

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

public final class SnapshotWriters {
    private SnapshotWriters() {
    }

    public static void initInts(IntFunction<SnapshotPrimitiveList.Int.Writer> builderSupplier, IntStream ids) {
        int[] values = ids.toArray();
        SnapshotPrimitiveList.Int.Writer builder = builderSupplier.apply(values.length);
        for (int i = 0; i < values.length; i++) {
            builder.set(i, values[i]);
        }
    }

    public static void initStringList(IntFunction<SnapshotStringList.Writer> builderSupplier, Stream<String> strings) {
        Object[] array = strings.toArray();
        SnapshotStringList.Writer builder = builderSupplier.apply(array.length);
        for (int i = 0; i < array.length; i++) {
            builder.set(i, array[i].toString());
        }
    }

    public static <S, T> void initSortedArray(IntFunction<SnapshotStructList.Writer<S>> init, T[] sortedArray, BiConsumer<T, Supplier<S>> action) {
        SnapshotStructList.Writer<S> builder = init.apply(sortedArray.length);
        Iterator<S> iterator = builder.iterator();
        for (T t : sortedArray) {
            action.accept(t, iterator::next);
        }
        AnalysisError.guarantee(!iterator.hasNext(), "all created struct builders must have been used");
    }
}
