/*
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLMUL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.zip.CRC32;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.replacements.nodes.CRC32TableNode;
import jdk.graal.compiler.replacements.nodes.CRC32UpdateBytesNode;
import jdk.vm.ci.amd64.AMD64;
import org.junit.Test;

/**
 * Tests compiled call to {@link CRC32#update(int, int)}.
 */
@SuppressWarnings("javadoc")
public class CRC32SubstitutionsTest extends GraalCompilerTest {

    private void assumeCRC32UpdateBytesIntrinsicSupported() {
        assumeTrue("CRC32 update bytes intrinsic is not supported by target architecture",
                        CRC32UpdateBytesNode.isSupported(getTarget().arch));
    }

    public static long updateSingleByte(int b) {
        CRC32 crc = new CRC32();
        crc.update(b);
        return crc.getValue();
    }

    @Test
    public void testUpdateSingleByteExecution() {
        for (int i = 0; i < 10_000; i++) {
            updateSingleByte(i);
        }
        assertTrue(updateSingleByte(1) >= 0);
    }

    public static long updateDirectByteBuffer(byte[] input) {
        CRC32 crc = new CRC32();
        ByteBuffer direct = ByteBuffer.allocateDirect(input.length);
        direct.put(input);
        direct.flip();
        crc.update(direct);
        return crc.getValue();
    }

    public static long update(byte[] input) {
        CRC32 crc = new CRC32();
        for (byte b : input) {
            crc.update(b);
        }
        return crc.getValue();
    }

    @Test
    public void test1() {
        test("update", "some string".getBytes());
    }

    public static long updateBytes(byte[] input, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(input, offset, length);
        return crc.getValue();
    }

    @Test
    public void test2() {
        byte[] buf = "some string".getBytes();
        int off = 0;
        int len = buf.length;
        test("updateBytes", buf, off, len);
    }

    @Test
    public void testUpdateBytesUsesCRC32StubNode() {
        assumeCRC32UpdateBytesIntrinsicSupported();
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("updateBytes"));
        assertTrue(graph.getNodes().filter(CRC32UpdateBytesNode.class).isNotEmpty());

        byte[] buf = new byte[1024];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) i;
        }
        test("updateBytes", buf, 7, buf.length - 19);
    }

    @Test
    public void testUpdateIntrinsicOwnershipPath() {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("updateSingleByte"));
        assertTrue(graph.getNodes().filter(CRC32TableNode.class).isNotEmpty());
        assertTrue(graph.getNodes().filter(XorNode.class).isNotEmpty());
        assertTrue(graph.getNodes().filter(CRC32UpdateBytesNode.class).isEmpty());
    }

    @Test
    public void testUpdateBytesIntrinsicOwnershipPath() {
        assumeCRC32UpdateBytesIntrinsicSupported();
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("updateBytes"));
        assertTrue(graph.getNodes().filter(CRC32UpdateBytesNode.class).isNotEmpty());
    }

    @Test
    public void testAMD64RequiresCLMUL() {
        assertFalse(CRC32UpdateBytesNode.isSupported(new AMD64(EnumSet.of(SSE2))));
        assertTrue(CRC32UpdateBytesNode.isSupported(new AMD64(EnumSet.of(SSE2, CLMUL))));
    }

    @Test
    public void test3() throws Throwable {
        String classfileName = CRC32SubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32SubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);
        test("updateBytes", buf, 0, buf.length);
        for (int offset = 1; offset < buf.length; offset++) {
            test("updateBytes", buf, offset, buf.length - offset);
        }
    }

    public static long updateByteBuffer(ByteBuffer buffer) {
        CRC32 crc = new CRC32();
        buffer.rewind();
        crc.update(buffer);
        return crc.getValue();
    }

    @Test
    public void test4() throws Throwable {
        String classfileName = CRC32SubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32SubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);

        ByteBuffer directBuf = ByteBuffer.allocateDirect(buf.length);
        directBuf.put(buf);
        ByteBuffer heapBuf = ByteBuffer.wrap(buf);

        test("updateByteBuffer", directBuf);
        test("updateByteBuffer", heapBuf);
    }

}
