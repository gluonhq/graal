/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package micro.benchmarks;

import java.math.BigInteger;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/*
 * Benchmarks cost of BigInteger intrinsics:
 *
 *      montgomeryMultiply, montgomerySquare, mulAdd, multiplyToLen, squareToLen,
 *      shiftLeftImplWorker, shiftRightImplWorker
 */
public class BigIntegerBenchmark extends BenchmarkBase {

    @State(Scope.Benchmark)
    public static class ThreadState {
        static final int DATA_SIZE = 100;
        static final int SMALL_SHIFT_MAX_NUM_BITS = 256;

        BigInteger[] data = randomBigInteger(DATA_SIZE);
        BigInteger[] smallShiftData = randomSmallShiftBigInteger(DATA_SIZE);
        BigInteger[] result = new BigInteger[DATA_SIZE];
        int[] shifts = randomShifts(DATA_SIZE);

        static BigInteger[] randomBigInteger(int len) {
            BigInteger[] data = new BigInteger[len];
            Random r = new Random(17);
            for (int i = 0; i < data.length; i++) {
                data[i] = new BigInteger(r.nextInt(16384) + 512, r);
            }
            return data;
        }

        static BigInteger[] randomSmallShiftBigInteger(int len) {
            BigInteger[] data = new BigInteger[len];
            Random r = new Random(1123);
            for (int i = 0; i < data.length; i++) {
                int value = Math.abs(r.nextInt());
                data[i] = new BigInteger(Math.max(SMALL_SHIFT_MAX_NUM_BITS - value % 32, 0), r);
            }
            return data;
        }

        static int[] randomShifts(int len) {
            int[] shifts = new int[len];
            Random r = new Random(1123);
            for (int i = 0; i < shifts.length; i++) {
                shifts[i] = r.nextInt(30) + 1;
            }
            return shifts;
        }
    }

    @Benchmark
    public void bigIntMul(ThreadState state) {
        BigInteger[] data = state.data;
        for (int i = 1; i < data.length; i++) {
            BigInteger[] result = state.result;
            result[i] = data[i - 1].multiply(data[i]);
        }
    }

    @Benchmark
    public void bigIntMulAdd(ThreadState state) {
        BigInteger[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            BigInteger[] result = state.result;
            // Using BigInteger.square() when length is suitable.
            // Using BigInteger.mulAdd() when length is suitable.
            result[i] = data[i].multiply(data[i]);
        }
    }

    @Benchmark
    public void bigIntShiftLeft(ThreadState state) {
        BigInteger[] data = state.data;
        BigInteger[] result = state.result;
        int[] shifts = state.shifts;
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i].shiftLeft(shifts[i]);
        }
    }

    @Benchmark
    public void bigIntShiftRight(ThreadState state) {
        BigInteger[] data = state.data;
        BigInteger[] result = state.result;
        int[] shifts = state.shifts;
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i].shiftRight(shifts[i]);
        }
    }

    @Benchmark
    public void bigIntSmallShiftLeft(ThreadState state) {
        BigInteger[] data = state.smallShiftData;
        BigInteger[] result = state.result;
        int[] shifts = state.shifts;
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i].shiftLeft(shifts[i]);
        }
    }

    @Benchmark
    public void bigIntSmallShiftRight(ThreadState state) {
        BigInteger[] data = state.smallShiftData;
        BigInteger[] result = state.result;
        int[] shifts = state.shifts;
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i].shiftRight(shifts[i]);
        }
    }

    @Benchmark
    public void bigIntMontgomeryMul(ThreadState state) {
        BigInteger[] data = state.data;
        BigInteger exp = BigInteger.valueOf(2);

        for (int i = 0; i < data.length; i++) {
            BigInteger[] result = state.result;
            int rsh = data[i].bitLength() / 2 + 3;
            // The "odd" path.
            // Using BigInteger.montgomeryMultiply().
            // Using BigInteger.montgomerySquare().
            // Using BigInteger.mulAdd() when length is suitable.
            result[i] = data[i].modPow(exp, data[i].shiftRight(rsh).setBit(0));
        }
    }

    @Benchmark
    public void bigIntMontgomerySqr(ThreadState state) {
        BigInteger[] data = state.data;
        BigInteger exp = BigInteger.valueOf(2);

        for (int i = 0; i < data.length; i++) {
            BigInteger[] result = state.result;
            int rsh = data[i].bitLength() / 2 + 3;
            // The "even" path.
            // Using BigInteger.montgomeryMultiply().
            // Using BigInteger.montgomerySquare().
            // Using BigInteger.mulAdd() when length is suitable.
            result[i] = data[i].modPow(exp, data[i].shiftRight(rsh).clearBit(0));
        }
    }
}
