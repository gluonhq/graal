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
package jdk.graal.compiler.hotspot.replaycomp;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonWriter;

/**
 * Writes replay benchmark results as a JSON array.
 * <p>
 * Each entry has a {@code type} field whose value is either {@code compilation} or
 * {@code iteration_total}.
 */
public final class ReplayBenchmarkResultsWriter implements AutoCloseable {
    /**
     * Field containing the record kind.
     */
    private static final String FIELD_TYPE = "type";

    /**
     * Field containing the benchmark iteration index.
     */
    private static final String FIELD_ITERATION = "iteration";

    /**
     * Field containing the replay compile identifier.
     */
    private static final String FIELD_COMPILE_ID = "compile_id";

    /**
     * Field containing the replayed method name.
     */
    private static final String FIELD_METHOD_NAME = "method_name";

    /**
     * Field containing the replayed method entry BCI.
     */
    private static final String FIELD_ENTRY_BCI = "entry_bci";

    /**
     * Field containing elapsed wall-clock time in nanoseconds.
     */
    private static final String FIELD_WALL_TIME_NANOS = "wall_time_ns";

    /**
     * Field containing elapsed thread CPU time in nanoseconds.
     */
    private static final String FIELD_THREAD_TIME_NANOS = "thread_time_ns";

    /**
     * Field containing allocated memory.
     */
    private static final String FIELD_ALLOCATED_MEMORY = "allocated_memory";

    /**
     * Field containing the compiled bytecode count.
     */
    private static final String FIELD_COMPILED_BYTECODES = "compiled_bytecodes";

    /**
     * Field containing the generated target code size.
     */
    private static final String FIELD_TARGET_CODE_SIZE = "target_code_size";

    /**
     * Field containing the generated target code hash.
     */
    private static final String FIELD_TARGET_CODE_HASH = "target_code_hash";

    /**
     * Field containing optional event counter values.
     */
    private static final String FIELD_EVENTS = "events";

    /**
     * Type value for per-compilation records.
     */
    private static final String TYPE_COMPILATION = "compilation";

    /**
     * Type value for aggregate iteration records.
     */
    private static final String TYPE_ITERATION_TOTAL = "iteration_total";

    /**
     * Result for one replayed compilation.
     */
    public record CompilationRecord(int iteration, int compileId, String methodName, int entryBci,
                    long wallTimeNanos, long threadTimeNanos, long allocatedMemory, int compiledBytecodes, int targetCodeSize, int targetCodeHash,
                    Map<String, Long> eventValues) {
    }

    /**
     * Aggregate result for one benchmark iteration.
     */
    public record IterationTotalRecord(int iteration,
                    long wallTimeNanos, long threadTimeNanos, long allocatedMemory, int compiledBytecodes, int targetCodeSize, int targetCodeHash,
                    Map<String, Long> eventValues) {
    }

    private final JsonWriter writer;

    private final JsonBuilder.ArrayBuilder records;

    private final List<String> eventNames;

    /**
     * Opens a JSON results writer for {@code path}.
     */
    public ReplayBenchmarkResultsWriter(Path path, List<String> eventNames) throws IOException {
        this(new JsonWriter(path), eventNames);
    }

    /**
     * Creates a JSON results writer backed by {@code writer}.
     */
    public ReplayBenchmarkResultsWriter(Writer writer, List<String> eventNames) throws IOException {
        this(new JsonWriter(writer), eventNames);
    }

    private ReplayBenchmarkResultsWriter(JsonWriter writer, List<String> eventNames) throws IOException {
        this.writer = writer;
        this.records = writer.arrayBuilder();
        this.eventNames = List.copyOf(eventNames);
    }

    /**
     * Writes a {@code compilation} entry.
     */
    public void writeCompilation(CompilationRecord record) throws IOException {
        try (var object = records.nextEntry().object()) {
            object.append(FIELD_TYPE, TYPE_COMPILATION);
            object.append(FIELD_ITERATION, record.iteration());
            object.append(FIELD_COMPILE_ID, record.compileId());
            object.append(FIELD_METHOD_NAME, record.methodName());
            object.append(FIELD_ENTRY_BCI, record.entryBci());
            appendMetrics(object, record.wallTimeNanos(), record.threadTimeNanos(), record.allocatedMemory(), record.compiledBytecodes(), record.targetCodeSize(), record.targetCodeHash());
            appendEvents(object, record.eventValues());
        }
    }

    /**
     * Writes an {@code iteration_total} entry.
     */
    public void writeIterationTotal(IterationTotalRecord record) throws IOException {
        try (var object = records.nextEntry().object()) {
            object.append(FIELD_TYPE, TYPE_ITERATION_TOTAL);
            object.append(FIELD_ITERATION, record.iteration());
            appendMetrics(object, record.wallTimeNanos(), record.threadTimeNanos(), record.allocatedMemory(), record.compiledBytecodes(), record.targetCodeSize(), record.targetCodeHash());
            appendEvents(object, record.eventValues());
        }
    }

    private static void appendMetrics(JsonBuilder.ObjectBuilder object,
                    long wallTimeNanos, long threadTimeNanos, long allocatedMemory, int compiledBytecodes, int targetCodeSize, int targetCodeHash) throws IOException {
        object.append(FIELD_WALL_TIME_NANOS, wallTimeNanos);
        object.append(FIELD_THREAD_TIME_NANOS, threadTimeNanos);
        object.append(FIELD_ALLOCATED_MEMORY, allocatedMemory);
        object.append(FIELD_COMPILED_BYTECODES, compiledBytecodes);
        object.append(FIELD_TARGET_CODE_SIZE, targetCodeSize);
        object.append(FIELD_TARGET_CODE_HASH, String.format("%08x", targetCodeHash));
    }

    private void appendEvents(JsonBuilder.ObjectBuilder object, Map<String, Long> eventValues) throws IOException {
        if (eventNames.isEmpty()) {
            return;
        }
        try (var events = object.append(FIELD_EVENTS).object()) {
            for (String eventName : eventNames) {
                events.append(eventName, eventValues.get(eventName));
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            records.close();
        } finally {
            writer.close();
        }
    }
}
