/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.StubCallingConvention;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

/**
 * Foundation for continuation support via {@link SubstrateVirtualThread} or
 * {@linkplain Target_java_lang_Continuation Project Loom}.
 */
public final class Continuation {
    @Fold
    public static boolean isSupported() {
        return SubstrateOptions.SupportContinuations.getValue();
    }

    public static final int YIELDING = -2;
    public static final int YIELD_SUCCESS = 0;

    private final Runnable target;

    public StoredContinuation stored;

    /** Frame pointer to return to when yielding, {@code null} if not executing. */
    private Pointer sp;

    /** While executing, where to return to when yielding, {@code null} if not executing. */
    private CodePointer ip;

    /** While executing, frame pointer of initial frame of continuation, {@code null} otherwise. */
    private Pointer baseSP;

    private boolean done;
    private int overflowCheckState;

    Continuation(Runnable target) {
        this.target = target;
    }

    public Pointer getBaseSP() {
        return baseSP;
    }

    void enter() {
        int stateBefore = StackOverflowCheck.singleton().getState();
        VMError.guarantee(!StackOverflowCheck.singleton().isYellowZoneAvailable());

        boolean isContinue = (stored != null);
        if (isContinue) {
            StackOverflowCheck.singleton().setState(overflowCheckState);
        }
        try {
            enter0(this, isContinue);
        } catch (StackOverflowError e) {
            throw (e == ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR) ? new StackOverflowError() : e;
        } finally {
            overflowCheckState = StackOverflowCheck.singleton().getState();
            StackOverflowCheck.singleton().setState(stateBefore);

            assert sp.isNull() && ip.isNull() && baseSP.isNull();
        }
    }

    /** See {@link #yield0} for what this method does. */
    @StubCallingConvention
    @NeverInline("Keep the frame with the saved registers.")
    private static void enter0(Continuation self, boolean isContinue) {
        self.enter1(isContinue);
    }

    /**
     * @return {@link Object} because we return here via {@link KnownIntrinsics#farReturn} which
     *         passes an object result.
     */
    @NeverInline("Accesses caller stack pointer and return address.")
    @Uninterruptible(reason = "Prevent safepoint checks between copying frames and farReturn.")
    private Object enter1(boolean isContinue) {
        // Note that the frame of this method will remain on the stack, and yielding will ignore it
        // and return past it to our caller.
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer callerIP = KnownIntrinsics.readReturnAddress();
        Pointer currentSP = KnownIntrinsics.readStackPointer();

        assert sp.isNull() && ip.isNull() && baseSP.isNull();
        if (isContinue) {
            assert stored != null;

            int totalSize = StoredContinuationAccess.getFramesSizeInBytes(stored);
            Pointer topSP = currentSP.subtract(totalSize);
            if (!StackOverflowCheck.singleton().isWithinBounds(topSP)) {
                throw ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR;
            }

            // copyFrames() may do something interruptible before uninterruptibly copying frames.
            // Code must not rely on remaining uninterruptible until after frames were copied.
            CodePointer enterIP = ImageSingletons.lookup(ContinuationSupport.class).copyFrames(stored, topSP);

            /*
             * NO CALLS BEYOND THIS POINT! They would overwrite the frames we just copied.
             */

            this.ip = callerIP;
            this.sp = callerSP;
            this.baseSP = currentSP;
            this.stored = null;
            KnownIntrinsics.farReturn(0, topSP, enterIP, false);
            throw VMError.shouldNotReachHere();

        } else {
            assert stored == null;
            this.ip = callerIP;
            this.sp = callerSP;
            this.baseSP = currentSP;

            enter2();
            return null;
        }
    }

    @Uninterruptible(reason = "Not actually, but because caller is uninterruptible.", calleeMustBe = false)
    private void enter2() {
        try {
            target.run();
        } finally {
            finish();
        }
    }

    int tryPreempt(Thread thread) {
        TryPreemptOperation vmOp = new TryPreemptOperation(this, thread);
        vmOp.enqueue();
        return vmOp.preemptStatus;
    }

    int yield() {
        return yield0(this);
    }

    /**
     * The callers can have live values in callee-saved registers which can be destroyed by the
     * context we switch to. By using the stub calling convention, this method saves register values
     * to the stack and restores them upon returning here via {@link KnownIntrinsics#farReturn}.
     */
    @StubCallingConvention
    @NeverInline("Keep the frame with the saved registers.")
    private static Integer yield0(Continuation self) {
        return self.yield1();
    }

    /**
     * @return {@link Integer} because we return here via {@link KnownIntrinsics#farReturn} and pass
     *         boxed 0 as result code.
     */
    @NeverInline("access stack pointer")
    private Integer yield1() {
        Pointer leafSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer leafIP = KnownIntrinsics.readReturnAddress();

        Pointer returnSP = sp;
        CodePointer returnIP = ip;

        int preemptStatus = StoredContinuationAccess.allocateToYield(this, baseSP, leafSP, leafIP);
        if (preemptStatus != 0) {
            return preemptStatus;
        }

        ip = WordFactory.nullPointer();
        sp = WordFactory.nullPointer();
        baseSP = WordFactory.nullPointer();

        KnownIntrinsics.farReturn(0, returnSP, returnIP, false);
        throw VMError.shouldNotReachHere();
    }

    public boolean isStarted() {
        return stored != null || ip.isNonNull();
    }

    public boolean isEmpty() {
        return stored == null && ip.isNull();
    }

    public boolean isDone() {
        return done;
    }

    public void finish() {
        done = true;
        ip = WordFactory.nullPointer();
        sp = WordFactory.nullPointer();
        baseSP = WordFactory.nullPointer();
        assert isEmpty();
    }

    private static final class TryPreemptOperation extends JavaVMOperation {
        int preemptStatus = YIELD_SUCCESS;

        final Continuation cont;
        final Thread thread;

        TryPreemptOperation(Continuation cont, Thread thread) {
            super(VMOperationInfos.get(TryPreemptOperation.class, "Try to preempt continuation", SystemEffect.SAFEPOINT));
            this.cont = cont;
            this.thread = thread;
        }

        @Override
        public void operate() {
            IsolateThread vmThread = PlatformThreads.getIsolateThread(thread);
            Pointer baseSP = cont.baseSP;
            Pointer returnSP = cont.sp;
            CodePointer returnIP = cont.ip;
            preemptStatus = StoredContinuationAccess.allocateToPreempt(cont, baseSP, vmThread);
            if (preemptStatus == 0) {
                cont.sp = WordFactory.nullPointer();
                cont.baseSP = WordFactory.nullPointer();
                cont.ip = WordFactory.nullPointer();
                VMThreads.ActionOnExitSafepointSupport.setSwitchStack(vmThread);
                VMThreads.ActionOnExitSafepointSupport.setSwitchStackTarget(vmThread, returnSP, returnIP);
            }
        }
    }
}
