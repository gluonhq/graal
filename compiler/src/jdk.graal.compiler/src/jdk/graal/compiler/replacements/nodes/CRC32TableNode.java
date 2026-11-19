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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Materializes CRC lookup tables in the generated code data section.
 * <p>
 * HotSpot C2 references {@code StubRoutines::crc_table_addr()} for this table. The HotSpot table is
 * generated from zlib's CRC32 table algorithm; Graal materializes the same table as a data pointer
 * constant so this standard replacement is not HotSpot-specific.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/java.base/share/native/libzip/zlib/zcrc32.c#L180-L276",
          sha1 = "24756f35956273657effdc1f93a03afdd85b99d3")
// @formatter:on
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class CRC32TableNode extends FloatingNode implements LIRLowerable, Node.ValueNumberable {
    public static final NodeClass<CRC32TableNode> TYPE = NodeClass.create(CRC32TableNode.class);

    private static final int REVERSED_POLYNOMIAL = 0xedb88320;

    public CRC32TableNode() {
        super(TYPE, StampFactory.pointer());
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind kind = gen.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        gen.setResult(this, gen.getLIRGeneratorTool().emitConstant(kind, new ArrayDataPointerConstant(createTable(REVERSED_POLYNOMIAL), 16)));
    }

    private static int[] createTable(int reversedPolynomial) {
        int[] generatedTable = new int[256];
        for (int index = 0; index < generatedTable.length; index++) {
            int r = index;
            for (int i = 0; i < Byte.SIZE; i++) {
                if ((r & 1) != 0) {
                    r = (r >>> 1) ^ reversedPolynomial;
                } else {
                    r >>>= 1;
                }
            }
            generatedTable[index] = r;
        }
        return generatedTable;
    }
}
