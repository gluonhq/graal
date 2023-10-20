/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import com.oracle.svm.core.SubstrateUtil;
import org.graalvm.collections.UnmodifiableEconomicMap;
import jdk.compiler.graal.options.Option;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.query.QueryResultParser;
import jdk.compiler.graal.options.OptionKey;
import jdk.compiler.graal.options.OptionValues;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

/**
 * Cache of pre-computed information for the {@link CAnnotationProcessor}. The cache is helpful to
 * cut substantially the time to build of an svm image, when many CAnnotation are being used. This
 * is the case, namely for Walnut, when the compilation time to generate the information for the C
 * processor is by far the dominant cost.
 *
 * For simplicity, the cache cannot be updated: it can be re-created from scratch, or used. The two
 * options are mutually exclusive, and the re-creation of the cache erase all previously cached
 * information. responsibility for the accuracy of the cached content is up to the end user.
 *
 * A CAP cache is just a directory with a file for each {@link NativeCodeInfo}, where the file is
 * pretty much the output of the "query" program generated by the CAnnotationProcessor.
 *
 */
public final class CAnnotationProcessorCache {
    private static final String FILE_EXTENSION = ".cap";

    public static class Options {
        @Option(help = "Indicate the C Annotation Processor to use previously cached native information when generating C Type information.")//
        public static final HostedOptionKey<Boolean> UseCAPCache = new HostedOptionKey<>(false) {
            @Override
            public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
                if (!values.containsKey(this)) {
                    // If user hasn't specified this option, we should determine optimal default
                    // value.
                    if (!ExitAfterQueryCodeGeneration.getValue() && !ImageSingletons.lookup(Platform.class).getArchitecture().equals(SubstrateUtil.getArchitectureName())) {
                        // If query code generation isn't explicitly requested, and we are running
                        // cross-arch build, CAP cache should be required (since we cannot run query
                        // code).
                        return true;
                    } else {
                        return false;
                    }
                }
                return (Boolean) values.get(this);
            }

            @Override
            public Boolean getValue(OptionValues values) {
                assert checkDescriptorExists();
                return getValueOrDefault(values.getMap());
            }
        };

        @Option(help = "Create a C Annotation Processor Cache. Will erase any previous cache at that same location.")//
        public static final HostedOptionKey<Boolean> NewCAPCache = new HostedOptionKey<>(false);

        @Option(help = "Directory where information generated by the CAnnotation Processor are cached.")//
        public static final HostedOptionKey<String> CAPCacheDir = new HostedOptionKey<>("");

        @Option(help = "Exit image generation after C Annotation Processor Cache creation.")//
        public static final HostedOptionKey<Boolean> ExitAfterCAPCache = new HostedOptionKey<>(false);

        @Option(help = "Output query code for target platform without executing it")//
        public static final HostedOptionKey<Boolean> ExitAfterQueryCodeGeneration = new HostedOptionKey<>(false);

        @Option(help = "Directory where query code for target platform should be output")//
        public static final HostedOptionKey<String> QueryCodeDir = new HostedOptionKey<>("");
    }

    private File cache;
    private File query;

    public CAnnotationProcessorCache() {
        if ((Options.UseCAPCache.getValue() || Options.NewCAPCache.getValue())) {
            if (!Options.ExitAfterQueryCodeGeneration.getValue() &&
                            (Options.CAPCacheDir.getValue() == null || Options.CAPCacheDir.getValue().isEmpty())) {
                throw UserError.abort("Path to C Annotation Processor Cache must be specified using %s when the option %s or %s is used.",
                                SubstrateOptionsParser.commandArgument(Options.CAPCacheDir, ""), SubstrateOptionsParser.commandArgument(Options.UseCAPCache, "+"),
                                SubstrateOptionsParser.commandArgument(Options.NewCAPCache, "+"));
            }
            Path cachePath = FileSystems.getDefault().getPath(Options.CAPCacheDir.getValue()).toAbsolutePath();
            cache = cachePath.toFile();
            if (!cache.exists()) {
                try {
                    cache = Files.createDirectories(cachePath).toFile();
                } catch (IOException e) {
                    throw UserError.abort("Could not create C Annotation Processor Cache directory: %s", e.getMessage());
                }
            } else if (!cache.isDirectory()) {
                throw UserError.abort("Path to C Annotation Processor Cache is not a directory");
            } else if (Options.NewCAPCache.getValue()) {
                clearCache();
            }
        }

        if (Options.QueryCodeDir.hasBeenSet()) {
            Path queryPath = FileSystems.getDefault().getPath(Options.QueryCodeDir.getValue()).toAbsolutePath();
            query = queryPath.toFile();
            if (!query.exists()) {
                try {
                    query = Files.createDirectories(queryPath).toFile();
                } catch (IOException e) {
                    throw UserError.abort("Could not create query code directory: %s", e.getMessage());
                }
            } else if (!query.isDirectory()) {
                throw UserError.abort("Path to query code directory is not a directory");
            }
        } else if (Options.ExitAfterQueryCodeGeneration.hasBeenSet()) {
            throw UserError.abort("Query code directory wasn't specified, use %s option.", SubstrateOptionsParser.commandArgument(Options.QueryCodeDir, "PATH"));
        }
    }

    private static String toPath(NativeCodeInfo nativeCodeInfo) {
        return nativeCodeInfo.getName().replaceAll("\\W", "_").concat(FILE_EXTENSION);
    }

    public void get(NativeLibraries nativeLibs, NativeCodeInfo nativeCodeInfo) {
        File file = new File(cache, toPath(nativeCodeInfo));
        try (FileInputStream fis = new FileInputStream(file)) {
            QueryResultParser.parse(nativeLibs, nativeCodeInfo, fis);
        } catch (IOException e) {
            throw UserError.abort("Could not load CAPCache file. Ensure that options %s and %s are used on the same version of your application. Raw error: %s",
                            Options.UseCAPCache.getName(), Options.NewCAPCache, e.getMessage());
        }
    }

    public void put(NativeCodeInfo nativeCodeInfo, List<String> lines) {
        if (!Options.NewCAPCache.getValue()) {
            return;
        }
        File cachedItem = new File(cache, toPath(nativeCodeInfo));
        try (FileWriter writer = new FileWriter(cachedItem, true)) {
            for (String line : lines) {
                writer.write(line);
                writer.write(Character.LINE_SEPARATOR);
            }
            return;
        } catch (IOException e) {
            throw shouldNotReachHere("Invalid CAnnotation Processor Cache item:" + cachedItem.toString());
        }
    }

    private void clearCache() {
        try {
            final Path cachePath = cache.toPath();
            Files.walkFileTree(cachePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    assert file.toString().endsWith(FILE_EXTENSION);
                    Files.delete(file);
                    return CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(cachePath)) {
                        Files.delete(dir);
                    }
                    return CONTINUE;
                }
            });
        } catch (IOException eio) {
            throw shouldNotReachHere(eio);
        }
    }
}
