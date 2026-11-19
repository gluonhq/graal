/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.bytecode;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

import java.io.IOException;
import java.util.EnumSet;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.junit.Assert;
import org.junit.Test;

public class WasmLegacyCatchOSRSuite {
    /**
     * {@code BytecodeOSRMetadata} polls every 1024 backedges once the OSR threshold is reached. Run
     * exactly that many loop iterations so the first OSR-compiled iteration reaches the
     * {@code rethrow} immediately, before another loop backedge can re-establish the legacy catch
     * depth.
     */
    private static final int OSR_TRIGGER_ITERATIONS = 1024;

    @Test
    public void testOSRPreservesActiveLegacyCatchDepthForRethrow() throws IOException, InterruptedException {
        final ByteSequence binaryMain = ByteSequence.create(compileWat("main", """
                        (module
                            (type $t0 (func))
                            (tag $e0 (type $t0))
                            (global $i (mut i32) (i32.const 0))
                            (func (export "_main") (param externref) (result i32)
                                try (result i32)
                                    throw $e0
                                catch $e0
                                    try (result i32)
                                        i32.const %d
                                        global.set $i
                                        loop
                                            global.get $i
                                            i32.const 1
                                            i32.sub
                                            global.set $i
                                            global.get $i
                                            i32.eqz
                                            if
                                                rethrow 3
                                            end
                                            br 0
                                        end
                                        i32.const 42
                                    catch $e0
                                        i32.const 23
                                    end
                                end
                            )
                        )
                        """.formatted(OSR_TRIGGER_ITERATIONS), EnumSet.of(WasmBinaryTools.WabtOption.EXCEPTIONS)));
        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "main").build();
        var eb = Engine.newBuilder().allowExperimentalOptions(true);
        eb.option("wasm.LegacyExceptions", "true");
        eb.option("engine.OSR", "true");
        eb.option("engine.OSRCompilationThreshold", "1");
        eb.option("engine.FirstTierCompilationThreshold", "1000000");
        eb.option("engine.LastTierCompilationThreshold", "1000000");
        eb.option("engine.BackgroundCompilation", "false");
        try (Engine engine = eb.build(); Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).build()) {
            Value mainExports = context.eval(sourceMain).newInstance().getMember("exports");
            Value mainFun = mainExports.getMember("_main");
            // The externref parameter occupies the frame slot directly below the reserved legacy
            // catch area, so a lost active legacy catch depth is likely to surface as a bad
            // rethrow selection rather than silently succeeding.
            Assert.assertEquals(23, mainFun.execute("sentinel").asInt());
        }
    }
}
