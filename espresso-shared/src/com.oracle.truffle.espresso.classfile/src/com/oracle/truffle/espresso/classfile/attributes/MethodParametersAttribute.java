/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public final class MethodParametersAttribute extends Attribute {

    public static final Symbol<Name> NAME = ParserNames.MethodParameters;

    public static final MethodParametersAttribute EMPTY = new MethodParametersAttribute(NAME, new byte[]{0});

    public static final class Entry {
        private final int nameIndex;
        private final int accessFlags;

        public Entry(int nameIndex, int accessFlags) {
            assert nameIndex >= 0;
            assert accessFlags >= 0;
            this.nameIndex = nameIndex;
            this.accessFlags = accessFlags;
        }

        public int getNameIndex() {
            return nameIndex;
        }

        public int getAccessFlags() {
            return accessFlags;
        }

        public boolean isSame(Entry otherEntry, ConstantPool thisPool, ConstantPool otherPool) {
            return thisPool.isSame(nameIndex, otherEntry.nameIndex, otherPool) && accessFlags == otherEntry.accessFlags;
        }
    }

    /**
     * Raw {@code MethodParameters} attribute contents as stored in the class file:
     * <ul>
     * <li>the first byte is the parameter count</li>
     * <li>each entry is encoded as {@code u2 name_index, u2 access_flags}</li>
     * </ul>
     * A {@code name_index} of {@code 0} denotes an unnamed parameter.
     */
    private final byte[] data;

    public MethodParametersAttribute(Symbol<Name> name, byte[] data) {
        assert name == NAME;
        assert data.length > 0;
        assert Byte.toUnsignedInt(data[0]) * 4 == data.length - 1;
        this.data = data;
    }

    /**
     * Returns the number of {@code MethodParameters} entries stored in this attribute.
     */
    public int entryCount() {
        return Byte.toUnsignedInt(data[0]);
    }

    /**
     * Decodes and returns the entry at {@code index}.
     */
    public Entry entryAt(int index) {
        if (index < 0 || index >= entryCount()) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for list of size " + entryCount() + ".");
        }
        int offset = 1 + index * 4;
        return new Entry(readU2(data, offset), readU2(data, offset + 2));
    }

    @Override
    public boolean isSame(Attribute other, ConstantPool thisPool, ConstantPool otherPool) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass() || getName() != other.getName()) {
            return false;
        }
        MethodParametersAttribute that = (MethodParametersAttribute) other;
        return entriesSameAs(that, thisPool, otherPool);
    }

    private boolean entriesSameAs(MethodParametersAttribute other, ConstantPool thisPool, ConstantPool otherPool) {
        if (entryCount() != other.entryCount()) {
            return false;
        }
        for (int i = 0; i < entryCount(); i++) {
            if (!entryAt(i).isSame(other.entryAt(i), thisPool, otherPool)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public Symbol<Name> getName() {
        return NAME;
    }

    private static int readU2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
