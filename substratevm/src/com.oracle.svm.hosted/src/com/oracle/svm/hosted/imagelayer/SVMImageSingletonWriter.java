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
import java.util.Objects;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;

import jdk.graal.compiler.debug.Assertions;

public final class SVMImageSingletonWriter implements ImageSingletonWriter {
    private final EconomicMap<String, Object> keyValueStore = EconomicMap.create();
    private final SharedLayerSnapshotData.Writer snapshotWriter;
    private final HostedUniverse hUniverse;

    SVMImageSingletonWriter(SharedLayerSnapshotData.Writer snapshotWriter, HostedUniverse hUniverse) {
        this.snapshotWriter = snapshotWriter;
        this.hUniverse = hUniverse;
    }

    EconomicMap<String, Object> getKeyValueStore() {
        return keyValueStore;
    }

    public HostedUniverse getHostedUniverse() {
        return hUniverse;
    }

    private static boolean nonNullEntries(List<?> list) {
        return list.stream().filter(Objects::isNull).findAny().isEmpty();
    }

    @Override
    public void writeBoolList(String keyName, List<Boolean> value) {
        assert nonNullEntries(value) : value;
        boolean[] b = new boolean[value.size()];
        for (int i = 0; i < value.size(); i++) {
            b[i] = value.get(i);
        }
        var previous = keyValueStore.put(keyName, b);
        assert previous == null : Assertions.errorMessage(keyName, previous);
    }

    @Override
    public void writeInt(String keyName, int value) {
        var previous = keyValueStore.put(keyName, value);
        assert previous == null : previous;
    }

    @Override
    public void writeIntList(String keyName, List<Integer> value) {
        assert nonNullEntries(value) : value;
        var previous = keyValueStore.put(keyName, value.stream().mapToInt(i -> i).toArray());
        assert previous == null : Assertions.errorMessage(keyName, previous);
    }

    @Override
    public void writeLong(String keyName, long value) {
        var previous = keyValueStore.put(keyName, value);
        assert previous == null : Assertions.errorMessage(keyName, previous);
    }

    @Override
    public void writeString(String keyName, String value) {
        var previous = keyValueStore.put(keyName, value);
        assert previous == null : Assertions.errorMessage(keyName, previous);
    }

    @Override
    public void writeStringList(String keyName, List<String> value) {
        assert nonNullEntries(value) : value;
        var previous = keyValueStore.put(keyName, value.toArray(String[]::new));
        assert previous == null : Assertions.errorMessage(keyName, previous);
    }

    public SharedLayerSnapshotData.Writer getSnapshotWriter() {
        return snapshotWriter;
    }
}
