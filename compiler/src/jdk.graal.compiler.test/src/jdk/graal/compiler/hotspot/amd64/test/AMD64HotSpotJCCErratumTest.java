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
package jdk.graal.compiler.hotspot.amd64.test;

import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;
import jdk.vm.ci.amd64.AMD64;

public class AMD64HotSpotJCCErratumTest extends GraalCompilerTest {

    @Before
    public void checkAMD64() {
        Assume.assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    @Test
    public void testInlineCacheCheckWithJCCErratumMitigation() throws IOException, InterruptedException {
        List<String> currentVmArgs = SubprocessUtil.getVMCommandLine();
        Assume.assumeNotNull(currentVmArgs);
        List<String> vmArgs = SubprocessUtil.withoutDebuggerArguments(currentVmArgs);
        vmArgs.removeIf(arg -> arg.startsWith("-Djdk.graal.") ||
                        arg.startsWith("-XX:CompileCommand=") ||
                        arg.startsWith("-XX:CICompilerCount="));
        vmArgs.add("-XX:+UseJVMCICompiler");
        vmArgs.add("-XX:+BootstrapJVMCI");
        vmArgs.add("-XX:-TieredCompilation");
        vmArgs.add("-Xbatch");
        vmArgs.add("-XX:CICompilerCount=1");
        vmArgs.add("-XX:CompileCommand=quiet");
        vmArgs.add("-XX:CompileCommand=compileonly,java/lang/String.length");
        vmArgs.add("-Djdk.graal.UseBranchesWithin32ByteBoundary=true");
        vmArgs.add("-Djdk.graal.CompilationFailureAction=ExitVM");
        vmArgs.add("-version");

        Subprocess subprocess = SubprocessUtil.java(vmArgs);
        Assert.assertEquals(subprocess.preserveArgfile().toString(), 0, subprocess.exitCode);
    }
}
