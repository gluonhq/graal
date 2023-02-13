/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.impl.ProcessPropertiesSupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport.NativeLibrary;
import com.oracle.svm.core.snippets.KnownIntrinsics;

@AutomaticallyRegisteredImageSingleton
public final class NativeLibrarySupport {
    // Essentially a revised implementation of the relevant methods in OpenJDK's ClassLoader

    public interface LibraryInitializer {
        boolean isBuiltinLibrary(String name);

        void initialize(NativeLibrary lib);
    }

    public static NativeLibrarySupport singleton() {
        return ImageSingletons.lookup(NativeLibrarySupport.class);
    }

    private final ReentrantLock lock = new ReentrantLock();

    private final List<NativeLibrary> knownLibraries = new CopyOnWriteArrayList<>();

    private final Deque<NativeLibrary> currentLoadContext = new ArrayDeque<>();

    /** The path of the directory containing the native image. */
    private String sysPath;
    /** Paths derived from the {@code java.library.path} system property. */
    private String[] usrPaths;

    private LibraryInitializer libraryInitializer;

    NativeLibrarySupport() {
    }

    @Platforms(HOSTED_ONLY.class)
    public void registerLibraryInitializer(LibraryInitializer initializer) {
        assert this.libraryInitializer == null;
        this.libraryInitializer = initializer;
    }

    @Platforms(HOSTED_ONLY.class)
    public void preregisterUninitializedBuiltinLibrary(String name) {
        knownLibraries.add(PlatformNativeLibrarySupport.singleton().createLibrary(name, true));
    }

    @Platforms(HOSTED_ONLY.class)
    public boolean isPreregisteredBuiltinLibrary(String name) {
        return knownLibraries.stream().anyMatch(l -> l.isBuiltin() && l.getCanonicalIdentifier().equals(name));
    }

    public void loadLibraryAbsolute(File file) {
        if (loadLibrary0(file, false)) {
            return;
        }
        throw new UnsatisfiedLinkError("Can't load library: " + file);
    }

    public void loadLibraryRelative(String name) {
        // Test if this is a built-in library
        if (loadLibrary0(new File(name), true)) {
            return;
        }
        if (usrPaths == null) {
            /*
             * Note that `sysPath` will be `null` if we fail to get the image directory in which
             * case we effectively fall back to using only `usrPaths`.
             */
            sysPath = getImageDirectory();
            String[] tokens = SubstrateUtil.split(System.getProperty("java.library.path", ""), File.pathSeparator);
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].isEmpty()) {
                    tokens[i] = ".";
                }
            }
            usrPaths = tokens;
        }
        String libname = System.mapLibraryName(name);
        if (sysPath != null && loadLibrary0(new File(sysPath, libname), false)) {
            return;
        }
        for (String path : usrPaths) {
            File libpath = new File(path, libname);
            if (loadLibrary0(libpath, false)) {
                return;
            }
            File altpath = Target_jdk_internal_loader_ClassLoaderHelper.mapAlternativeName(libpath);
            if (altpath != null && loadLibrary0(altpath, false)) {
                return;
            }
        }
        throw new UnsatisfiedLinkError("no " + name + " in java.library.path");
    }

    /** Returns the directory containing the native image, or {@code null}. */
    @NeverInline("Reads the return address.")
    private static String getImageDirectory() {
        /*
         * While one might expect code for shared libraries to work for executables as well, this is
         * not necessarily the case. For example, `dladdr` on Linux returns `argv[0]` for
         * executables, which is completely useless when running an executable from `$PATH`, since
         * then `argv[0]` contains only the name of the executable.
         */
        String image = !SubstrateOptions.SharedLibrary.getValue() ? ProcessProperties.getExecutableName()
                        : ImageSingletons.lookup(ProcessPropertiesSupport.class).getObjectFile(KnownIntrinsics.readReturnAddress());
        return image != null ? new File(image).getParent() : null;
    }

    private boolean loadLibrary0(File file, boolean asBuiltin) {
        String canonical;
        try {
            canonical = asBuiltin ? file.getName() : file.getCanonicalPath();
        } catch (IOException e) {
            return false;
        }
        return addLibrary(asBuiltin, canonical, true);
    }

    private boolean addLibrary(boolean asBuiltin, String canonical, boolean initialize) {
        lock.lock();
        try {
            NativeLibrary lib = null;
            for (NativeLibrary known : knownLibraries) {
                if (canonical.equals(known.getCanonicalIdentifier())) {
                    if (known.isLoaded()) {
                        return true;
                    } else {
                        assert known.isBuiltin() : "non-built-in libraries must always have been loaded";
                        assert asBuiltin : "must have tried loading as built-in first";
                        lib = known; // load and initialize below
                        break;
                    }
                }
            }
            if (asBuiltin && lib == null && (libraryInitializer == null || !libraryInitializer.isBuiltinLibrary(canonical))) {
                return false;
            }
            // Libraries can load libraries during initialization, avoid recursion with a stack
            for (NativeLibrary loading : currentLoadContext) {
                if (canonical.equals(loading.getCanonicalIdentifier())) {
                    return true;
                }
            }
            boolean created = false;
            if (lib == null) {
                lib = PlatformNativeLibrarySupport.singleton().createLibrary(canonical, asBuiltin);
                created = true;
            }
            currentLoadContext.push(lib);
            try {
                if (!lib.load()) {
                    return false;
                }
                /*
                 * Initialization of a library must be skipped if it can be initialized at most once
                 * per process and another isolate has already initialized it. However, the library
                 * must be (marked as) loaded above so it cannot be loaded and initialized later.
                 */
                if (initialize && libraryInitializer != null) {
                    libraryInitializer.initialize(lib);
                }
            } finally {
                NativeLibrary top = currentLoadContext.pop();
                assert top == lib;
            }
            if (created) {
                knownLibraries.add(lib);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public PointerBase findSymbol(String name) {
        lock.lock();
        try {
            for (NativeLibrary lib : knownLibraries) {
                PointerBase entry = lib.findSymbol(name);
                if (entry.isNonNull()) {
                    return entry;
                }
            }
            return WordFactory.nullPointer();
        } finally {
            lock.unlock();
        }
    }

    public void registerInitializedBuiltinLibrary(String name) {
        boolean success = addLibrary(true, name, false);
        assert success;
    }
}
