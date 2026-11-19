/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Option.Group;
import com.oracle.truffle.api.TruffleLogger;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Group("engine")
public final class DebugEngineCacheSupport extends AbstractEngineCacheSupport {

    /*
     * In memory cache for tests only. Real implementations need to copy.
     */
    private static final AtomicReference<Object> persistedEngine = new AtomicReference<>();

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Enables tracing for the engine cache debug feature.")//
    public static final OptionKey<Boolean> DebugTraceCache = new OptionKey<>(Boolean.FALSE);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Prepares the engine for caching and stores it a static field instead of writing it to disk.")//
    public static final OptionKey<Boolean> DebugCacheStore = new OptionKey<>(Boolean.FALSE);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Prepares the engine to take the stored engine from the static field instead of reading it from disk.")//
    public static final OptionKey<Boolean> DebugCacheLoad = new OptionKey<>(Boolean.FALSE);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = COMPILE_HELP, usageSyntax = COMPILE_USAGE_SYNTAX)//
    public static final OptionKey<CompilePolicy> DebugCacheCompile = new OptionKey<>(CompilePolicy.hot);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Preinitialize a new context with all languages that support it and that were used during the run (default: true).", usageSyntax = "true|false")//
    public static final OptionKey<Boolean> DebugCachePreinitializeContext = new OptionKey<>(Boolean.TRUE);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = ""//
                    + "If true uses the last tier instead of the first tier compiler. By default the last tier compiler is used (default: true).", usageSyntax = "true|false")//
    public static final OptionKey<Boolean> DebugCacheCompileUseLastTier = new OptionKey<>(Boolean.TRUE);

    @Override
    public OptionDescriptors getEngineOptions() {
        return new DebugEngineCacheSupportOptionDescriptors();
    }

    @Override
    public boolean isStoreEnabled(OptionValues options) {
        return options.get(DebugCacheStore);
    }

    @Override
    protected OptionKey<Boolean> getTraceOption() {
        return DebugTraceCache;
    }

    @Override
    public void onEngineCreated(EngineData e) {
    }

    @Override
    public void onEnginePatch(EngineData e) {
        e.clearEngineLocal(FinalizationResult.class);
    }

    @Override
    public Object tryLoadingCachedEngine(OptionValues options, Function<String, TruffleLogger> loggerFactory) {
        if (!options.get(DebugCacheLoad)) {
            traceLoad(options, loggerFactory, "--engine.DebugCacheLoad is not enabled. Not loading engine.");
            return null;
        }

        // reading the image is simulated using a reference
        Object engine = persistedEngine.getAndSet(null);
        if (engine == null) {
            traceLoad(options, loggerFactory, "No cached debug engine to load in memory found.");
            return null;
        }
        traceLoad(options, loggerFactory, "Loaded debug engine from in memory cache.");
        return engine;
    }

    @Override
    public boolean onEngineClosing(EngineData e) {
        OptionValues options = e.getEngineOptions();
        if (!options.get(DebugCacheStore)) {
            return false;
        }

        trace(e, "Preparing debug engine for storage...");

        CompilePolicy compileMode = options.get(DebugCacheCompile);
        // write image is simulated by setting a reference
        persistedEngine.set(prepareEngine(e, compileMode, options.get(DebugCacheCompileUseLastTier), options.get(DebugCachePreinitializeContext)));

        trace(e, "Stored debug engine in memory.");

        // return true to indicate that the engine should not actually be closed
        return true;
    }

    @Override
    public void onEngineClosed(EngineData e) {
    }

    @Override
    public int getPriority() {
        return 0;
    }

}
