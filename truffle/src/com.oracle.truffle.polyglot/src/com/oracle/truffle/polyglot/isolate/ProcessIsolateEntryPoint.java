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
package com.oracle.truffle.polyglot.isolate;

import org.graalvm.nativebridge.ProcessIsolateConfig;

import java.nio.file.Path;

/**
 * Entry point for the process-isolated polyglot execution. This class is loaded by a C++ launcher,
 * which then invokes the {@link ProcessIsolateEntryPoint#start(String[])} method. The method
 * expects a path to an UNIX domain socket, opened by the host VM, to be passed as an argument.
 */
public final class ProcessIsolateEntryPoint {

    /**
     * Entry point invoked by the process-isolated polyglot C++ launcher via the JNI API.
     * <p>
     * This method initializes a new polyglot process isolate and begins listening for foreign
     * access requests over a UNIX domain socket specified in the arguments.
     *
     * @param args the arguments passed from the C++ launcher. The first element must be the file
     *            system path to a UNIX domain socket that has been opened by the host JVM for
     *            inter-process communication.
     * @return the process exit code:
     *         <ul>
     *         <li>{@code 0} on success,</li>
     *         <li>{@code 1} if arguments validation fails (e.g., socket path missing),</li>
     *         <li>{@code 2} if the isolate worker setup fails due to an exception.</li>
     *         </ul>
     */
    static int start(String[] args) {
        try {
            if (args.length < 1) {
                return 1;
            }
            Path hostAddress = Path.of(args[0]);
            ProcessIsolateConfig config = ProcessIsolateConfig.newTargetBuilder(hostAddress).threadLocalFactory(
                            () -> PolyglotIsolateAccessor.RUNTIME.createTerminatingThreadLocal(() -> null, (t) -> t.getIsolate().detachCurrentThread())).//
                            build();
            PolyglotIsolateForeignFactoryGen.listen(config);
            return 0;
        } catch (Throwable t) {
            /*
             * If a failure occurs at this point, there is no channel to pass the exception back to
             * the creator process. The only option is to return a non-zero exit code.
             */
            return 2;
        }
    }
}
