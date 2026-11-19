/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.util.Base64;

import org.junit.Test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.replacements.nodes.Base64DecodeBlockNode;
import jdk.graal.compiler.replacements.nodes.Base64EncodeBlockNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotBase64Test extends HotSpotGraalCompilerTest {

    // @formatter:off
    private static final String lipsum = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata ";
    // @formatter:on

    public static byte[] encodeSnippet(byte[] src) {
        return Base64.getEncoder().encode(src);
    }

    public static byte[] decodeSnippet(byte[] src) {
        return Base64.getDecoder().decode(src);
    }

    @Test
    public void testEncode() {
        assumeTrue("Enable test case when Base64 encode intrinsic is available", Base64EncodeBlockNode.isSupported(getArchitecture()));
        test(getResolvedJavaMethod(Base64.Encoder.class, "encode", byte[].class), Base64.getEncoder(), lipsum.getBytes());
    }

    @Test
    public void testDecode() {
        assumeTrue("Enable test case when Base64 decode intrinsic is available", Base64DecodeBlockNode.isSupported(getArchitecture()));
        byte[] input = Base64.getEncoder().encode(lipsum.getBytes());
        test(getResolvedJavaMethod(Base64.Decoder.class, "decode", byte[].class), Base64.getDecoder(), input);
    }

    @Test
    public void testInstallEncodeDecodeIntrinsics() {
        assumeTrue("Enable test case when Base64 encode intrinsic is available", Base64EncodeBlockNode.isSupported(getArchitecture()));
        assumeTrue("Enable test case when Base64 decode intrinsic is available", Base64DecodeBlockNode.isSupported(getArchitecture()));

        ResolvedJavaMethod encodeMethod = getMetaAccess().lookupJavaMethod(
                        getMethod(Base64.Encoder.class, "encode0", byte[].class, int.class, int.class, byte[].class));
        StructuredGraph encodeGraph = parseForCompile(encodeMethod);
        InstalledCode encodeIntrinsic = getCode(encodeMethod, encodeGraph, false, true, getInitialOptions());
        assertNotNull("missing encode intrinsic", encodeIntrinsic);

        ResolvedJavaMethod decodeMethod = getMetaAccess().lookupJavaMethod(
                        getMethod(Base64.Decoder.class, "decode0", byte[].class, int.class, int.class, byte[].class));
        StructuredGraph decodeGraph = parseForCompile(decodeMethod);
        InstalledCode decodeIntrinsic = getCode(decodeMethod, decodeGraph, false, true, getInitialOptions());
        assertNotNull("missing decode intrinsic", decodeIntrinsic);

        byte[] plain = lipsum.getBytes();
        byte[] encoded = encodeSnippet(plain);
        byte[] decoded = decodeSnippet(encoded);
        assertArrayEquals(plain, decoded);

        encodeIntrinsic.invalidate();
        decodeIntrinsic.invalidate();
    }
}
