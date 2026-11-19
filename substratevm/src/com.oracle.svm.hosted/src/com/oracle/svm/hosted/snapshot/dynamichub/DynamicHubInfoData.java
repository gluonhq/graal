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
package com.oracle.svm.hosted.snapshot.dynamichub;

import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

/**
 * Persisted representation of dynamic hub metadata.
 * <p>
 * This captures runtime type information that is attached to the hub, such as type-check data and
 * dispatch-table state, so that it can be preserved across image layers.
 */
public interface DynamicHubInfoData {
    interface Writer {
        void setTypeId(int value);

        void setTypecheckId(int value);

        void setNumClassTypes(int value);

        void setNumIterableInterfaceTypes(int value);

        SnapshotPrimitiveList.Int.Writer initTypecheckSlotValues(int size);

        void setInterfaceId(int value);

        void setInstalled(boolean value);

        SnapshotPrimitiveList.Int.Writer initLocallyDeclaredSlotsHostedMethodIndexes(int size);

        SnapshotStructList.Writer<DispatchSlotInfoData.Writer> initDispatchTableSlotValues(int size);
    }

    interface Loader {
        int getTypeId();

        int getTypecheckId();

        int getNumClassTypes();

        int getNumIterableInterfaceTypes();

        SnapshotPrimitiveList.Int.Loader getTypecheckSlotValues();

        int getInterfaceId();

        boolean getInstalled();

        SnapshotPrimitiveList.Int.Loader getLocallyDeclaredSlotsHostedMethodIndexes();

        SnapshotStructList.Loader<DispatchSlotInfoData.Loader> getDispatchTableSlotValues();

        boolean hasDispatchTableSlotValues();
    }
}
