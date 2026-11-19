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
package com.oracle.svm.hosted;

import static java.lang.System.lineSeparator;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.option.HostedOptionValues;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.options.OptionValues;

/**
 * Support class that keeps track of dynamic access calls requiring metadata usage detected during
 * {@link com.oracle.svm.hosted.phases.DynamicAccessDetectionPhase} and outputs them to the
 * image-build output.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class DynamicAccessDetectionSupport {
    private static final String OUTPUT_DIR_NAME = "dynamic-access";

    public record ReportOptions(boolean printToConsole, boolean dumpJsonFiles) {
    }

    // We use a ConcurrentSkipListMap, as opposed to a ConcurrentHashMap, to maintain
    // order of methods by access kind.
    public record MethodsByAccessKind(Map<DynamicAccessMethodLookupSupport.DynamicAccessKind, CallLocationsByMethod> methodsByAccessKind) {
        MethodsByAccessKind() {
            this(new ConcurrentSkipListMap<>());
        }

        public Set<DynamicAccessMethodLookupSupport.DynamicAccessKind> getAccessKinds() {
            return methodsByAccessKind.keySet();
        }

        public CallLocationsByMethod getCallLocationsByMethod(DynamicAccessMethodLookupSupport.DynamicAccessKind accessKind) {
            return methodsByAccessKind.getOrDefault(accessKind, new CallLocationsByMethod());
        }
    }

    // We use a ConcurrentSkipListSet, as opposed to a wrapped ConcurrentHashMap, to maintain
    // order of call locations by method.
    public record CallLocationsByMethod(Map<String, ConcurrentSkipListSet<String>> callLocationsByMethod) {
        CallLocationsByMethod() {
            this(new ConcurrentSkipListMap<>());
        }

        public Set<String> getMethods() {
            return callLocationsByMethod.keySet();
        }

        public ConcurrentSkipListSet<String> getMethodCallLocations(String methodName) {
            return callLocationsByMethod.getOrDefault(methodName, new ConcurrentSkipListSet<>());
        }
    }

    private final EconomicSet<String> sourceEntries;
    private final Map<String, MethodsByAccessKind> callsBySourceEntry;
    private final BuildArtifacts buildArtifacts = BuildArtifacts.singleton();
    private final OptionValues hostedOptionValues = HostedOptionValues.singleton().get();

    private final boolean printToConsole;
    private final boolean dumpJsonFiles;

    public DynamicAccessDetectionSupport(EconomicSet<String> sourceEntries, ReportOptions reportOptions) {
        this.sourceEntries = EconomicSet.create(sourceEntries);
        this.callsBySourceEntry = new ConcurrentSkipListMap<>();
        this.printToConsole = reportOptions.printToConsole();
        this.dumpJsonFiles = reportOptions.dumpJsonFiles();
    }

    public static DynamicAccessDetectionSupport singleton() {
        return ImageSingletons.lookup(DynamicAccessDetectionSupport.class);
    }

    public static boolean isDynamicAccessTrackingEnabled() {
        return ImageSingletons.contains(DynamicAccessDetectionSupport.class);
    }

    public void addCall(String entry, DynamicAccessMethodLookupSupport.DynamicAccessKind accessKind, String call, String callLocation) {
        MethodsByAccessKind entryContent = callsBySourceEntry.computeIfAbsent(entry, _ -> new MethodsByAccessKind());
        CallLocationsByMethod methodCallLocations = entryContent.methodsByAccessKind().computeIfAbsent(accessKind, _ -> new CallLocationsByMethod());
        ConcurrentSkipListSet<String> callLocations = methodCallLocations.callLocationsByMethod().computeIfAbsent(call, _ -> new ConcurrentSkipListSet<>());
        callLocations.add(callLocation);
    }

    public EconomicSet<String> getSourceEntries() {
        return sourceEntries;
    }

    public void reportDynamicAccess() {
        for (String entry : sourceEntries) {
            if (callsBySourceEntry.containsKey(entry)) {
                if (dumpJsonFiles) {
                    dumpReportForEntry(entry);
                }
                if (printToConsole) {
                    printReportForEntry(entry);
                }
            }
        }
    }

    public void clear() {
        callsBySourceEntry.clear();
        sourceEntries.clear();
    }

    public MethodsByAccessKind getMethodsByAccessKind(String entry) {
        return callsBySourceEntry.computeIfAbsent(entry, _ -> new MethodsByAccessKind());
    }

    public static String getEntryName(String path) {
        String fileName = path.substring(path.lastIndexOf(File.separator) + 1);
        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

    private void printReportForEntry(String entry) {
        System.out.println("Dynamic method usage detected in " + entry + ":");
        MethodsByAccessKind methodsByAccessKind = getMethodsByAccessKind(entry);
        for (DynamicAccessMethodLookupSupport.DynamicAccessKind accessKind : methodsByAccessKind.getAccessKinds()) {
            System.out.println("    " + accessKind + " calls detected:");
            CallLocationsByMethod methodCallLocations = methodsByAccessKind.getCallLocationsByMethod(accessKind);
            for (String call : methodCallLocations.getMethods()) {
                System.out.println("        " + call + ":");
                for (String callLocation : methodCallLocations.getMethodCallLocations(call)) {
                    System.out.println("            at " + callLocation);
                }
            }
        }
    }

    private static Path getOrCreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory)) {
                throw new NoSuchFileException(directory.toString(), null,
                                "Failed to retrieve directory: The path exists but is not a directory.");
            }
        } else {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                throw new IOException("Failed to create directory: " + directory, e);
            }
        }
        return directory;
    }

    private void dumpReportForEntry(String entry) {
        try {
            MethodsByAccessKind methodsByAccessKind = getMethodsByAccessKind(entry);
            Path reportDirectory = NativeImageGenerator.generatedFiles(hostedOptionValues).resolve(OUTPUT_DIR_NAME);
            for (DynamicAccessMethodLookupSupport.DynamicAccessKind accessKind : methodsByAccessKind.getAccessKinds()) {
                Path entryDirectory = getOrCreateDirectory(reportDirectory.resolve(getEntryName(entry)));
                Path targetPath = entryDirectory.resolve(accessKind.fileName);
                ReportUtils.report("Dynamic Access Detection Report", targetPath,
                                writer -> generateDynamicAccessReport(writer, accessKind, methodsByAccessKind),
                                false);
                buildArtifacts.add(BuildArtifacts.ArtifactType.BUILD_INFO, targetPath);
            }
        } catch (IOException e) {
            throw UserError.abort("Failed to dump report for entry %s: %s", entry, e.getMessage());
        }
    }

    private static void generateDynamicAccessReport(PrintWriter writer, DynamicAccessMethodLookupSupport.DynamicAccessKind accessKind, MethodsByAccessKind methodsByAccessKind) {
        writer.println("{");
        String methodsJson = methodsByAccessKind.getCallLocationsByMethod(accessKind).getMethods().stream()
                        .map(methodName -> toMethodJson(accessKind, methodName, methodsByAccessKind))
                        .collect(Collectors.joining("," + lineSeparator()));
        writer.println(methodsJson);
        writer.println("}");
    }

    private static String toMethodJson(DynamicAccessMethodLookupSupport.DynamicAccessKind accessKind, String methodName, MethodsByAccessKind methodsByAccessKind) {
        String locationsJson = methodsByAccessKind.getCallLocationsByMethod(accessKind)
                        .getMethodCallLocations(methodName).stream()
                        .map(location -> "    \"" + location + "\"")
                        .collect(Collectors.joining("," + lineSeparator()));
        return "  \"" + methodName + "\": [" + lineSeparator() +
                        locationsJson + lineSeparator() +
                        "  ]";
    }
}
