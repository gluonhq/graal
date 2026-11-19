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
package com.oracle.svm.core.deopt;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.shared.Uninterruptible;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Heap-based representation of a future deoptimization-target stack frame, i.e., the intermediate
 * representation between deoptimization of an optimized frame and stack rewriting.
 */
public abstract class VirtualFrame {
    private final FrameInfoQueryResult frameInfo;
    /**
     * The program counter where execution continuous.
     */
    private DeoptimizedFrame.ReturnAddress returnAddress;

    /**
     * The saved base pointer for the target frame, or null if the architecture does not use base
     * pointers.
     */
    private DeoptimizedFrame.SavedBasePointer savedBasePointer;

    /**
     * The local variables and expression stack value of this frame. Local variables that are unused
     * at the deoptimization point are {@code null}.
     */
    final DeoptimizedFrame.ConstantEntry[] values;

    protected VirtualFrame(FrameInfoQueryResult frameInfo) {
        this.frameInfo = frameInfo;
        this.values = new DeoptimizedFrame.ConstantEntry[frameInfo.getValueInfos().length];
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setReturnAddress(DeoptimizedFrame.ReturnAddress returnAddress) {
        this.returnAddress = returnAddress;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public DeoptimizedFrame.ReturnAddress getReturnAddress() {
        return returnAddress;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setSavedBasePointer(DeoptimizedFrame.SavedBasePointer savedBasePointer) {
        this.savedBasePointer = savedBasePointer;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public DeoptimizedFrame.SavedBasePointer getSavedBasePointer() {
        return savedBasePointer;
    }

    /**
     * The caller frame of this frame, or null if this is the outermost frame.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract VirtualFrame getCaller();

    public abstract void setCaller(VirtualFrame caller);

    /** The deoptimization metadata of this frame's target method. */
    public FrameInfoQueryResult getFrameInfo() {
        return frameInfo;
    }

    /**
     * Returns the value of the local variable or expression stack value with the given index.
     * Expression stack values are after all local variables.
     */
    public JavaConstant getConstant(int index) {
        if (index >= values.length || values[index] == null) {
            return JavaConstant.forIllegal();
        } else {
            return values[index].constant;
        }
    }
}
