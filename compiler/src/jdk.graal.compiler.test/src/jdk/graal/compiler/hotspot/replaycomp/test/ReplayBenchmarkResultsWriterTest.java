/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.hotspot.replaycomp.ReplayBenchmarkResultsWriter;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.json.JsonParser;

public class ReplayBenchmarkResultsWriterTest {
    @Test
    public void writesCompilationAndIterationTotalRecords() throws IOException {
        StringWriter output = new StringWriter();
        try (ReplayBenchmarkResultsWriter writer = new ReplayBenchmarkResultsWriter(output, List.of("PAPI_TOT_INS"))) {
            writer.writeCompilation(new ReplayBenchmarkResultsWriter.CompilationRecord(3, 17, "pkg.Type.method(int)", -1,
                            11L, 12L, 13L, 14, 15, 0x1a2b3c4d, CollectionsUtil.mapOf("PAPI_TOT_INS", 16L)));
            writer.writeIterationTotal(new ReplayBenchmarkResultsWriter.IterationTotalRecord(3, 21L, 22L, 23L, 24, 25, 0x01020304, CollectionsUtil.mapOf("PAPI_TOT_INS", 26L)));
        }

        @SuppressWarnings("unchecked")
        List<Object> records = (List<Object>) new JsonParser(output.toString()).parse();
        Assert.assertEquals(2, records.size());

        EconomicMap<String, Object> compilation = getRecord(records, 0);
        Assert.assertEquals("compilation", compilation.get("type"));
        Assert.assertEquals(3, intValue(compilation.get("iteration")));
        Assert.assertEquals(17, intValue(compilation.get("compile_id")));
        Assert.assertEquals("pkg.Type.method(int)", compilation.get("method_name"));
        Assert.assertEquals(-1, intValue(compilation.get("entry_bci")));
        Assert.assertEquals(11L, longValue(compilation.get("wall_time_ns")));
        Assert.assertEquals(12L, longValue(compilation.get("thread_time_ns")));
        Assert.assertEquals(13L, longValue(compilation.get("allocated_memory")));
        Assert.assertEquals(14, intValue(compilation.get("compiled_bytecodes")));
        Assert.assertEquals(15, intValue(compilation.get("target_code_size")));
        Assert.assertEquals("1a2b3c4d", compilation.get("target_code_hash"));
        Assert.assertEquals(16L, longValue(getMap(compilation, "events").get("PAPI_TOT_INS")));

        EconomicMap<String, Object> iterationTotal = getRecord(records, 1);
        Assert.assertEquals("iteration_total", iterationTotal.get("type"));
        Assert.assertEquals(3, intValue(iterationTotal.get("iteration")));
        Assert.assertEquals(21L, longValue(iterationTotal.get("wall_time_ns")));
        Assert.assertEquals(22L, longValue(iterationTotal.get("thread_time_ns")));
        Assert.assertEquals(23L, longValue(iterationTotal.get("allocated_memory")));
        Assert.assertEquals(24, intValue(iterationTotal.get("compiled_bytecodes")));
        Assert.assertEquals(25, intValue(iterationTotal.get("target_code_size")));
        Assert.assertEquals("01020304", iterationTotal.get("target_code_hash"));
        Assert.assertNull(iterationTotal.get("compile_id"));
        Assert.assertNull(iterationTotal.get("method_name"));
        Assert.assertNull(iterationTotal.get("entry_bci"));
        Assert.assertEquals(26L, longValue(getMap(iterationTotal, "events").get("PAPI_TOT_INS")));
    }

    @SuppressWarnings("unchecked")
    private static EconomicMap<String, Object> getRecord(List<Object> records, int index) {
        return (EconomicMap<String, Object>) records.get(index);
    }

    @SuppressWarnings("unchecked")
    private static EconomicMap<String, Object> getMap(EconomicMap<String, Object> record, String key) {
        return (EconomicMap<String, Object>) record.get(key);
    }

    private static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }
}
