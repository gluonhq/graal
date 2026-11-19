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

package com.oracle.svm.core.jdk;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.shared.util.VMError;

/**
 * Wrapper object for the {@link Target_java_lang_Throwable#backtrace} representation that is used
 * when a raw {@code long[]} alone is not sufficient.
 * <p>
 * The raw array stores either native instruction pointers (for AOT-compiled methods) or encoded
 * Java source references containing a source line number, a source class, and a source method name
 * (for JIT-compiled methods or interpreter frames). A native instruction pointer always occupies a
 * single {@code long} element, while an encoded Java source reference can be encoded in three
 * different shapes:
 *
 * <ol>
 * <li>{@link BacktraceHolder#REFS_IN_IMAGEHEAP_COMPRESSED imageheap compressed}: If the class and
 * method name are non-movable objects (i.e. live in the image heap) and references are
 * {@link BacktraceVisitor#useCompressedReferences() compressed} it takes 2 entries in the
 * {@link #raw long array}.</li>
 *
 * <li>{@link BacktraceHolder#REFS_IN_IMAGEHEAP_UNCOMPRESSED imageheap uncompressed}: If the class
 * and method name are non-movable objects and references are not compressed it takes 3 entries in
 * the {@link #raw long array}.</li>
 *
 * <li>{@link BacktraceHolder#REFS_IN_SIDE_ARRAY moving references}: If the class or method name are
 * movable objects, they are stored in an additional side array of type {@code Object[]} that is
 * tracked by the GC. Indices into that side storage are stored in the {@link #raw long array},
 * which therefore uses 2 entries for this shape as well.</li>
 *
 * </ol>
 * <p>
 * <p>
 * Native instruction pointers and the different shapes of source references can be mixed. The
 * source line number of the source reference is
 * {@linkplain BacktraceVisitor#encodeLineNumberWithKind encoded} in a way that it can be
 * distinguished from a native instruction pointer.
 *
 * <pre>
 *               backtrace content                               |   Number of Java frames
 *               ---------------------------------------------------
 * raw[pos + 0] | native inst. pointer                            |   0..n Java frames
 *              ---------------------------------------------------
 * raw[pos + 1] | encoded src line nr                             |   1st Java frame in shape
 * raw[pos + 2] | class ref                                       |   REFS_IN_IMAGEHEAP_UNCOMPRESSED
 * raw[pos + 3] | method name ref                                 |
 *              ---------------------------------------------------
 * raw[pos + 4] | encoded src line nr                             |   2nd Java frame in shape
 * raw[pos + 5] | (class ref) << 32 | (method name ref)           |   REFS_IN_IMAGEHEAP_COMPRESSED
 *              ---------------------------------------------------
 * raw[pos + 6] | encoded src line nr                             |   3rd Java frame in shape
 * raw[pos + 7] | (class idx_side) << 32 | (method name idx_side) |   REFS_IN_SIDE_ARRAY
 *              ---------------------------------------------------   side array is not visualized.
 *              | ... remaining                                   |
 *              ---------------------------------------------------
 *              | 0                                               |   0 terminated if not all elements are used
 * </pre>
 *
 * @implNote While it is technically possible to mix source references entries of type
 *           {@link BacktraceHolder#REFS_IN_IMAGEHEAP_COMPRESSED imageheap compressed} and
 *           {@link BacktraceHolder#REFS_IN_IMAGEHEAP_UNCOMPRESSED imageheap uncompressed}, it
 *           cannot happen in practice.
 * @see BacktraceVisitor writes the backtrace representation
 * @see BacktraceDecoder decodes the backtrace representation
 *
 */
final class BacktraceHolder {
    static final int KIND_SHIFT = Long.SIZE - 3;

    /* Stored in upper three bits of an entry, must lead with 1 so that the entry is negative. */
    static final int REFS_IN_IMAGEHEAP_UNCOMPRESSED = 0b100;
    static final int REFS_IN_IMAGEHEAP_COMPRESSED = 0b101;
    static final int REFS_IN_SIDE_ARRAY = 0b110;

    final long[] raw;
    final Object[] side;

    BacktraceHolder(long[] raw, Object[] side) {
        this.raw = raw;
        this.side = side;
    }

    /**
     * Returns {@code true} if the holder has entries available at the given position.
     */
    boolean hasNext(int pos) {
        if (pos >= raw.length || raw[pos] == 0) {
            return false;
        }

        VMError.guarantee(pos + slotsForEntry(pos) <= raw.length, "Truncated raw array");
        return true;
    }

    /**
     * Determines how many raw slots the entry at {@code pos} occupies so the next entry can be
     * located.
     *
     * @param pos index of the current entry
     * @return number of raw slots occupied by the entry
     */
    int slotsForEntry(int pos) {
        if (isSourceReference(raw[pos])) {
            return slotsPerSourceReference(getSourceReferenceKind(raw[pos]));
        }
        return slotsPerCodePointer();
    }

    static boolean isSourceReference(long entry) {
        return entry < 0;
    }

    static int getSourceReferenceKind(long entry) {
        assert isSourceReference(entry);
        return decodeSourceReferenceKind(entry);
    }

    static int slotsPerSourceReference(int kind) {
        if (kind == REFS_IN_IMAGEHEAP_COMPRESSED || kind == REFS_IN_SIDE_ARRAY) {
            return 2;
        }
        assert kind == REFS_IN_IMAGEHEAP_UNCOMPRESSED;
        return 3;
    }

    static int slotsPerCodePointer() {
        return 1;
    }

    static int decodeSourceReferenceKind(long entry) {
        int v = (int) (entry >>> KIND_SHIFT);
        assert v == REFS_IN_IMAGEHEAP_COMPRESSED || v == REFS_IN_IMAGEHEAP_UNCOMPRESSED || v == REFS_IN_SIDE_ARRAY;
        return v;
    }

    /**
     * Returns the source line number stored in the source-reference entry that starts at
     * {@code pos}.
     *
     * @param raw the raw backtrace storage containing the encoded source-reference entry
     * @param pos the start position of the source-reference entry in {@code raw}
     * @return the decoded source line number for that entry
     */
    static int readSourceLineNumber(long[] raw, int pos) {
        return (int) raw[pos];
    }

    /**
     * Returns the source class referenced by the source-reference entry that starts at {@code pos}.
     * The class is decoded either directly from {@code raw} or indirectly via {@code side},
     * depending on the entry kind.
     *
     * @param raw the raw backtrace storage containing the encoded source-reference entry
     * @param side auxiliary storage for movable references; required only for
     *            {@link #REFS_IN_SIDE_ARRAY} entries and {@code null} otherwise
     * @param pos the start position of the source-reference entry in {@code raw}
     * @return the decoded source class for that entry
     */
    static Class<?> readSourceClass(long[] raw, Object[] side, int pos) {
        int kind = getSourceReferenceKind(raw[pos]);

        long v = raw[pos + 1];
        if (kind == REFS_IN_IMAGEHEAP_COMPRESSED) {
            UnsignedWord ref = Word.unsigned(v).unsignedShiftRight(32);
            return (Class<?>) ReferenceAccess.singleton().uncompressReference(ref);
        } else if (kind == REFS_IN_IMAGEHEAP_UNCOMPRESSED) {
            return ((Pointer) Word.pointer(v)).toObject(Class.class, true);
        } else {
            assert kind == REFS_IN_SIDE_ARRAY;
            return (Class<?>) side[(int) (v >> 32)];
        }
    }

    /**
     * Returns the source method name referenced by the source-reference entry that starts at
     * {@code pos}. The method name is decoded either directly from {@code raw} or indirectly via
     * {@code side}, depending on the entry kind.
     *
     * @param raw the raw backtrace storage containing the encoded source-reference entry
     * @param side auxiliary storage for movable references; required only for
     *            {@link #REFS_IN_SIDE_ARRAY} entries and {@code null} otherwise
     * @param pos the start position of the source-reference entry in {@code raw}
     * @return the decoded source method name for that entry
     */
    static String readSourceMethodName(long[] raw, Object[] side, int pos) {
        int kind = getSourceReferenceKind(raw[pos]);

        if (kind == REFS_IN_IMAGEHEAP_COMPRESSED) {
            long v = raw[pos + 1];
            UnsignedWord ref = Word.unsigned(v).and(Word.unsigned(0xffff_ffffL));
            return (String) ReferenceAccess.singleton().uncompressReference(ref);
        } else if (kind == REFS_IN_IMAGEHEAP_UNCOMPRESSED) {
            long v = raw[pos + 2];
            return ((Pointer) Word.pointer(v)).toObject(String.class, true);
        } else {
            assert kind == REFS_IN_SIDE_ARRAY;
            long v = raw[pos + 1];
            return (String) side[(int) (v & 0xffff_ffffL)];
        }
    }
}
