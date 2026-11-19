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

import java.util.List;
import java.util.Map;

import jdk.graal.compiler.hotspot.Platform;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;

/**
 * Immutable in-memory representation of a recorded compilation shared by the replay codecs.
 *
 * @param request the compilation request that was recorded
 * @param compilerConfiguration the name of the compiler configuration used for recording
 * @param isLibgraal {@code true} if the recorded compilation executed on libgraal
 * @param platform the target platform of the recorded compilation
 * @param properties the saved system properties for the recorded compilation
 * @param linkages the recorded foreign-call linkage metadata
 * @param product the recorded compilation task result
 * @param operations the recorded JVMCI operations and their results
 */
public record RecordedCompilationUnit(HotSpotCompilationRequest request, String compilerConfiguration,
                boolean isLibgraal, Platform platform, Map<String, String> properties, RecordedForeignCallLinkages linkages,
                CompilationTaskProduct product, List<OperationRecorder.RecordedOperation> operations) {
}
