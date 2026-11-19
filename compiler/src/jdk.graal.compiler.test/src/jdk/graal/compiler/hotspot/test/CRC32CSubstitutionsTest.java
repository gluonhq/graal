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
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.zip.Checksum;
import java.util.zip.CRC32C;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.replacements.nodes.CRC32CUpdateBytesNode;
import jdk.vm.ci.amd64.AMD64;
import org.junit.Test;

/**
 * Tests compiled calls to {@link java.util.zip.CRC32C}.
 */
@SuppressWarnings("javadoc")
public class CRC32CSubstitutionsTest extends GraalCompilerTest {

    private void assumeCRC32CUpdateBytesIntrinsicSupported() {
        assumeTrue("CRC32C update bytes intrinsic is not supported by target architecture",
                        CRC32CUpdateBytesNode.isSupported(getTarget().arch));
    }

    public static long updateDirectByteBufferIntrinsic(byte[] input) {
        CRC32C crc = new CRC32C();
        ByteBuffer direct = ByteBuffer.allocateDirect(input.length);
        direct.put(input);
        direct.flip();
        crc.update(direct);
        return crc.getValue();
    }

    public static long updateBytes(byte[] input, int offset, int end) throws Throwable {
        Class<?> crcClass = Class.forName("java.util.zip.CRC32C");
        MethodHandle newMH = MethodHandles.publicLookup().findConstructor(crcClass, MethodType.methodType(void.class));
        Checksum crc = (Checksum) newMH.invoke();
        crc.update(input, offset, end);
        return crc.getValue();
    }

    public static long updateBytesIntrinsic(byte[] input, int offset, int length) {
        CRC32C crc = new CRC32C();
        crc.update(input, offset, length);
        return crc.getValue();
    }

    @Test
    public void test1() throws Throwable {
        String classfileName = CRC32CSubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32CSubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);
        for (int offset = 0; offset < buf.length; offset++) {
            test("updateBytes", buf, offset, buf.length);
        }
    }

    @Test
    public void testUpdateBytesUsesCRC32CStubNode() {
        assumeCRC32CUpdateBytesIntrinsicSupported();
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("updateBytesIntrinsic"));
        assertTrue(graph.getNodes().filter(CRC32CUpdateBytesNode.class).isNotEmpty());
    }

    @Test
    public void testUpdateBytesIntrinsicOwnershipPath() {
        assumeCRC32CUpdateBytesIntrinsicSupported();
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("updateBytesIntrinsic"));
        CRC32CUpdateBytesNode node = graph.getNodes().filter(CRC32CUpdateBytesNode.class).first();
        assertNotNull(node);
        assertTrue(node.getForeignCallArguments().length == 3);

        byte[] buf = new byte[1024];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) i;
        }
        test("updateBytesIntrinsic", buf, 7, buf.length - 19);
    }

    @Test
    public void testAMD64RequiresSSE42AndCLMUL() {
        assertFalse(CRC32CUpdateBytesNode.isSupported(new AMD64(EnumSet.of(SSE2, SSE4_2))));
        assertFalse(CRC32CUpdateBytesNode.isSupported(new AMD64(EnumSet.of(SSE2, CLMUL))));
        assertTrue(CRC32CUpdateBytesNode.isSupported(new AMD64(EnumSet.of(SSE2, SSE4_2, CLMUL))));
    }

    public static long updateByteBuffer(ByteBuffer buffer) throws Throwable {
        Class<?> crcClass = Class.forName("java.util.zip.CRC32C");
        MethodHandle newMH = MethodHandles.publicLookup().findConstructor(crcClass, MethodType.methodType(void.class));
        MethodHandle updateMH = MethodHandles.publicLookup().findVirtual(crcClass, "update", MethodType.methodType(void.class, ByteBuffer.class));
        Checksum crc = (Checksum) newMH.invoke();
        buffer.rewind();
        updateMH.invokeExact(crc, buffer);
        return crc.getValue();
    }

    @Test
    public void test2() throws Throwable {
        String classfileName = CRC32CSubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32CSubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);

        ByteBuffer directBuf = ByteBuffer.allocateDirect(buf.length);
        directBuf.put(buf);
        ByteBuffer heapBuf = ByteBuffer.wrap(buf);

        test("updateByteBuffer", directBuf);
        test("updateByteBuffer", heapBuf);
    }

    @Test
    public void testDirectByteBufferOnlyExecution() throws Throwable {
        String classfileName = CRC32CSubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32CSubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);
        test("updateDirectByteBufferIntrinsic", buf);
    }

}
