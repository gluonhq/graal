/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.jfr.impl;

import com.oracle.truffle.runtime.jfr.CompilationStartedEvent;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Unsigned;

@Name("jdk.graal.compiler.truffle.CompilationStarted")
@Category("Truffle Compiler")
@Label("Compilation Started")
@Description("Truffle Compilation Started")
@Enabled(false)
@StackTrace(false)
class CompilationStartedEventImpl extends RootFunctionEventImpl implements CompilationStartedEvent {

    @Label("Tier") @Description("The Tier of the Truffle Compiler") public int tier;
    @Label("Priority") @Description("Compilation Priority") public long priority;
    @Label("Rate") @Description("Compilation Rate") public String rate;
    @Label("Queue Size") @Description("Compilation Queue Size") @Unsigned public int queueSize;
    @Label("Queue Change") @Description("Compilation Queue Size Change") public int queueChange;
    @Label("Queue Load") @Description("Compilation Queue Load") public double queueLoad;
    @Label("Queue Time") @Description("Compilation Queue Time in Microseconds") @Unsigned public long queueTime;
    @Label("Bonuses") @Description("Applied Compilation Priority Bonuses") public String bonuses;

    @Override
    public void setTier(int tier) {
        this.tier = tier;
    }

    @Override
    public void setPriority(long priority) {
        this.priority = priority;
    }

    @Override
    public void setRate(String rate) {
        this.rate = rate;
    }

    @Override
    public void setQueueSize(int size) {
        this.queueSize = size;
    }

    @Override
    public void setQueueChange(int change) {
        this.queueChange = change;
    }

    @Override
    public void setQueueLoad(double load) {
        this.queueLoad = load;
    }

    @Override
    public void setQueueTime(long time) {
        this.queueTime = time;
    }

    @Override
    public void setBonuses(String bonuses) {
        this.bonuses = bonuses;
    }
}
