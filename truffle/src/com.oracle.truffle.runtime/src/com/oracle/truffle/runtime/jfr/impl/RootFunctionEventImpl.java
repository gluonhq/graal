/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.jfr.RootFunctionEvent;

import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

abstract class RootFunctionEventImpl extends Event implements RootFunctionEvent {

    @Label("Engine Id") @Description("Truffle Engine Unique Id") public long engineId;
    @Label("Id") @Description("Truffle Compilable Unique Id") public long id;
    @Label("Source") @Description("Compiled Source") public String source;
    @Label("Source Hash") @Description("Compiled Source Hash Code") public String sourceHash;
    @Label("Language") @Description("Guest Language") public String language;
    @Label("Root Function") @Description("Root Function") public String rootFunction;
    @Label("AST Size") @Description("AST Node Count") public int astSize;

    RootFunctionEventImpl() {
    }

    RootFunctionEventImpl(long engineId, long id, String source, String sourceHash, String language, String rootFunction, int astSize) {
        this.engineId = engineId;
        this.id = id;
        this.source = source;
        this.sourceHash = sourceHash;
        this.language = language;
        this.rootFunction = rootFunction;
        this.astSize = astSize;
    }

    @Override
    public void setRootFunction(OptimizedCallTarget target) {
        this.engineId = target.engineId();
        this.id = target.id;
        RootNode rootNode = target.getRootNode();
        SourceSection sourceSection = safeSourceSection(target);
        this.source = formatSourceName(sourceSection);
        this.sourceHash = formatSourceHash(sourceSection);
        LanguageInfo languageInfo = rootNode.getLanguageInfo();
        this.language = languageInfo != null ? languageInfo.getId() : "n/a";
        this.rootFunction = safeTargetName(target);
        this.astSize = target.getNonTrivialNodeCount();
    }

    @Override
    public void publish() {
        commit();
    }

    private static SourceSection safeSourceSection(OptimizedCallTarget target) {
        try {
            return target.getRootNode().getSourceSection();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String safeTargetName(OptimizedCallTarget target) {
        try {
            return target.getName();
        } catch (Throwable throwable) {
            return "n/a";
        }
    }

    private static String formatSourceName(SourceSection sourceSection) {
        if (sourceSection == null || sourceSection.getSource() == null) {
            return "n/a";
        }
        return String.format("%s:%d", sourceSection.getSource().getName(), sourceSection.getStartLine());
    }

    private static String formatSourceHash(SourceSection sourceSection) {
        if (sourceSection == null || sourceSection.getSource() == null) {
            return "n/a";
        }
        return String.format("0x%x", sourceSection.getSource().hashCode());
    }

}
