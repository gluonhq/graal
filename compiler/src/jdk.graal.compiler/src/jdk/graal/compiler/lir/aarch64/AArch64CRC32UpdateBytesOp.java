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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.DoubleWord;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Word;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PRE_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSR;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L7444-L7483",
          sha1 = "469a50035c6ec4c8ee26c64bf65224dc26e11fc6")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L4291-L4705",
          sha1 = "927eb4d871fcc6b3a48d1a9306aef4a6eda114ea")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L4878-L5003",
          sha1 = "0c88cbd27b58a3029062580159d90bc05eef808a")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86/src/hotspot/cpu/aarch64/stubRoutines_aarch64.cpp#L74-L322",
          sha1 = "6d29782b250d6c1703ecddef0755a6ab9a0966d1")
// @formatter:on
@Opcode("AARCH64_CRC32_UPDATE_BYTES")
public final class AArch64CRC32UpdateBytesOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64CRC32UpdateBytesOp> TYPE = LIRInstructionClass.create(AArch64CRC32UpdateBytesOp.class);

    private static final int CRC32_TABLES = 4;
    private static final int CRC32_COLUMN_SIZE = 256;
    private static final int CRC32_TABLE_SIZE = CRC32_TABLES * CRC32_COLUMN_SIZE * Integer.BYTES;
    private static final int CRC32_NEON_TABLE_OFFSET = CRC32_TABLE_SIZE;
    private static final int CRC32_PMULL_TABLE_OFFSET = CRC32_TABLE_SIZE + 8 * Integer.BYTES;

    private static final ArrayDataPointerConstant CRC32_TABLE = pointerConstant(16, new int[]{
                    // Table 0
                    0x00000000, 0x77073096, 0xee0e612c, 0x990951ba, 0x076dc419,
                    0x706af48f, 0xe963a535, 0x9e6495a3, 0x0edb8832, 0x79dcb8a4,
                    0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd, 0xe7b82d07,
                    0x90bf1d91, 0x1db71064, 0x6ab020f2, 0xf3b97148, 0x84be41de,
                    0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7, 0x136c9856,
                    0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f, 0x63066cd9,
                    0xfa0f3d63, 0x8d080df5, 0x3b6e20c8, 0x4c69105e, 0xd56041e4,
                    0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd, 0xa50ab56b,
                    0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940, 0x32d86ce3,
                    0x45df5c75, 0xdcd60dcf, 0xabd13d59, 0x26d930ac, 0x51de003a,
                    0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423, 0xcfba9599,
                    0xb8bda50f, 0x2802b89e, 0x5f058808, 0xc60cd9b2, 0xb10be924,
                    0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d, 0x76dc4190,
                    0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589, 0x06b6b51f,
                    0x9fbfe4a5, 0xe8b8d433, 0x7807c9a2, 0x0f00f934, 0x9609a88e,
                    0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97, 0xe6635c01,
                    0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e, 0x6c0695ed,
                    0x1b01a57b, 0x8208f4c1, 0xf50fc457, 0x65b0d9c6, 0x12b7e950,
                    0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49, 0x8cd37cf3,
                    0xfbd44c65, 0x4db26158, 0x3ab551ce, 0xa3bc0074, 0xd4bb30e2,
                    0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb, 0x4369e96a,
                    0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73, 0x33031de5,
                    0xaa0a4c5f, 0xdd0d7cc9, 0x5005713c, 0x270241aa, 0xbe0b1010,
                    0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409, 0xce61e49f,
                    0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4, 0x59b33d17,
                    0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad, 0xedb88320, 0x9abfb3b6,
                    0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af, 0x04db2615,
                    0x73dc1683, 0xe3630b12, 0x94643b84, 0x0d6d6a3e, 0x7a6a5aa8,
                    0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1, 0xf00f9344,
                    0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d, 0x806567cb,
                    0x196c3671, 0x6e6b06e7, 0xfed41b76, 0x89d32be0, 0x10da7a5a,
                    0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43, 0x60b08ed5,
                    0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252, 0xd1bb67f1,
                    0xa6bc5767, 0x3fb506dd, 0x48b2364b, 0xd80d2bda, 0xaf0a1b4c,
                    0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55, 0x316e8eef,
                    0x4669be79, 0xcb61b38c, 0xbc66831a, 0x256fd2a0, 0x5268e236,
                    0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f, 0xc5ba3bbe,
                    0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7, 0xb5d0cf31,
                    0x2cd99e8b, 0x5bdeae1d, 0x9b64c2b0, 0xec63f226, 0x756aa39c,
                    0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785, 0x05005713,
                    0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38, 0x92d28e9b,
                    0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21, 0x86d3d2d4, 0xf1d4e242,
                    0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b, 0x6fb077e1,
                    0x18b74777, 0x88085ae6, 0xff0f6a70, 0x66063bca, 0x11010b5c,
                    0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45, 0xa00ae278,
                    0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661, 0xd06016f7,
                    0x4969474d, 0x3e6e77db, 0xaed16a4a, 0xd9d65adc, 0x40df0b66,
                    0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f, 0x30b5ffe9,
                    0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6, 0xbad03605,
                    0xcdd70693, 0x54de5729, 0x23d967bf, 0xb3667a2e, 0xc4614ab8,
                    0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1, 0x5a05df1b,
                    0x2d02ef8d,

                    // Table 1
                    0x00000000, 0x191b3141, 0x32366282, 0x2b2d53c3, 0x646cc504,
                    0x7d77f445, 0x565aa786, 0x4f4196c7, 0xc8d98a08, 0xd1c2bb49,
                    0xfaefe88a, 0xe3f4d9cb, 0xacb54f0c, 0xb5ae7e4d, 0x9e832d8e,
                    0x87981ccf, 0x4ac21251, 0x53d92310, 0x78f470d3, 0x61ef4192,
                    0x2eaed755, 0x37b5e614, 0x1c98b5d7, 0x05838496, 0x821b9859,
                    0x9b00a918, 0xb02dfadb, 0xa936cb9a, 0xe6775d5d, 0xff6c6c1c,
                    0xd4413fdf, 0xcd5a0e9e, 0x958424a2, 0x8c9f15e3, 0xa7b24620,
                    0xbea97761, 0xf1e8e1a6, 0xe8f3d0e7, 0xc3de8324, 0xdac5b265,
                    0x5d5daeaa, 0x44469feb, 0x6f6bcc28, 0x7670fd69, 0x39316bae,
                    0x202a5aef, 0x0b07092c, 0x121c386d, 0xdf4636f3, 0xc65d07b2,
                    0xed705471, 0xf46b6530, 0xbb2af3f7, 0xa231c2b6, 0x891c9175,
                    0x9007a034, 0x179fbcfb, 0x0e848dba, 0x25a9de79, 0x3cb2ef38,
                    0x73f379ff, 0x6ae848be, 0x41c51b7d, 0x58de2a3c, 0xf0794f05,
                    0xe9627e44, 0xc24f2d87, 0xdb541cc6, 0x94158a01, 0x8d0ebb40,
                    0xa623e883, 0xbf38d9c2, 0x38a0c50d, 0x21bbf44c, 0x0a96a78f,
                    0x138d96ce, 0x5ccc0009, 0x45d73148, 0x6efa628b, 0x77e153ca,
                    0xbabb5d54, 0xa3a06c15, 0x888d3fd6, 0x91960e97, 0xded79850,
                    0xc7cca911, 0xece1fad2, 0xf5facb93, 0x7262d75c, 0x6b79e61d,
                    0x4054b5de, 0x594f849f, 0x160e1258, 0x0f152319, 0x243870da,
                    0x3d23419b, 0x65fd6ba7, 0x7ce65ae6, 0x57cb0925, 0x4ed03864,
                    0x0191aea3, 0x188a9fe2, 0x33a7cc21, 0x2abcfd60, 0xad24e1af,
                    0xb43fd0ee, 0x9f12832d, 0x8609b26c, 0xc94824ab, 0xd05315ea,
                    0xfb7e4629, 0xe2657768, 0x2f3f79f6, 0x362448b7, 0x1d091b74,
                    0x04122a35, 0x4b53bcf2, 0x52488db3, 0x7965de70, 0x607eef31,
                    0xe7e6f3fe, 0xfefdc2bf, 0xd5d0917c, 0xcccba03d, 0x838a36fa,
                    0x9a9107bb, 0xb1bc5478, 0xa8a76539, 0x3b83984b, 0x2298a90a,
                    0x09b5fac9, 0x10aecb88, 0x5fef5d4f, 0x46f46c0e, 0x6dd93fcd,
                    0x74c20e8c, 0xf35a1243, 0xea412302, 0xc16c70c1, 0xd8774180,
                    0x9736d747, 0x8e2de606, 0xa500b5c5, 0xbc1b8484, 0x71418a1a,
                    0x685abb5b, 0x4377e898, 0x5a6cd9d9, 0x152d4f1e, 0x0c367e5f,
                    0x271b2d9c, 0x3e001cdd, 0xb9980012, 0xa0833153, 0x8bae6290,
                    0x92b553d1, 0xddf4c516, 0xc4eff457, 0xefc2a794, 0xf6d996d5,
                    0xae07bce9, 0xb71c8da8, 0x9c31de6b, 0x852aef2a, 0xca6b79ed,
                    0xd37048ac, 0xf85d1b6f, 0xe1462a2e, 0x66de36e1, 0x7fc507a0,
                    0x54e85463, 0x4df36522, 0x02b2f3e5, 0x1ba9c2a4, 0x30849167,
                    0x299fa026, 0xe4c5aeb8, 0xfdde9ff9, 0xd6f3cc3a, 0xcfe8fd7b,
                    0x80a96bbc, 0x99b25afd, 0xb29f093e, 0xab84387f, 0x2c1c24b0,
                    0x350715f1, 0x1e2a4632, 0x07317773, 0x4870e1b4, 0x516bd0f5,
                    0x7a468336, 0x635db277, 0xcbfad74e, 0xd2e1e60f, 0xf9ccb5cc,
                    0xe0d7848d, 0xaf96124a, 0xb68d230b, 0x9da070c8, 0x84bb4189,
                    0x03235d46, 0x1a386c07, 0x31153fc4, 0x280e0e85, 0x674f9842,
                    0x7e54a903, 0x5579fac0, 0x4c62cb81, 0x8138c51f, 0x9823f45e,
                    0xb30ea79d, 0xaa1596dc, 0xe554001b, 0xfc4f315a, 0xd7626299,
                    0xce7953d8, 0x49e14f17, 0x50fa7e56, 0x7bd72d95, 0x62cc1cd4,
                    0x2d8d8a13, 0x3496bb52, 0x1fbbe891, 0x06a0d9d0, 0x5e7ef3ec,
                    0x4765c2ad, 0x6c48916e, 0x7553a02f, 0x3a1236e8, 0x230907a9,
                    0x0824546a, 0x113f652b, 0x96a779e4, 0x8fbc48a5, 0xa4911b66,
                    0xbd8a2a27, 0xf2cbbce0, 0xebd08da1, 0xc0fdde62, 0xd9e6ef23,
                    0x14bce1bd, 0x0da7d0fc, 0x268a833f, 0x3f91b27e, 0x70d024b9,
                    0x69cb15f8, 0x42e6463b, 0x5bfd777a, 0xdc656bb5, 0xc57e5af4,
                    0xee530937, 0xf7483876, 0xb809aeb1, 0xa1129ff0, 0x8a3fcc33,
                    0x9324fd72,

                    // Table 2
                    0x00000000, 0x01c26a37, 0x0384d46e, 0x0246be59, 0x0709a8dc,
                    0x06cbc2eb, 0x048d7cb2, 0x054f1685, 0x0e1351b8, 0x0fd13b8f,
                    0x0d9785d6, 0x0c55efe1, 0x091af964, 0x08d89353, 0x0a9e2d0a,
                    0x0b5c473d, 0x1c26a370, 0x1de4c947, 0x1fa2771e, 0x1e601d29,
                    0x1b2f0bac, 0x1aed619b, 0x18abdfc2, 0x1969b5f5, 0x1235f2c8,
                    0x13f798ff, 0x11b126a6, 0x10734c91, 0x153c5a14, 0x14fe3023,
                    0x16b88e7a, 0x177ae44d, 0x384d46e0, 0x398f2cd7, 0x3bc9928e,
                    0x3a0bf8b9, 0x3f44ee3c, 0x3e86840b, 0x3cc03a52, 0x3d025065,
                    0x365e1758, 0x379c7d6f, 0x35dac336, 0x3418a901, 0x3157bf84,
                    0x3095d5b3, 0x32d36bea, 0x331101dd, 0x246be590, 0x25a98fa7,
                    0x27ef31fe, 0x262d5bc9, 0x23624d4c, 0x22a0277b, 0x20e69922,
                    0x2124f315, 0x2a78b428, 0x2bbade1f, 0x29fc6046, 0x283e0a71,
                    0x2d711cf4, 0x2cb376c3, 0x2ef5c89a, 0x2f37a2ad, 0x709a8dc0,
                    0x7158e7f7, 0x731e59ae, 0x72dc3399, 0x7793251c, 0x76514f2b,
                    0x7417f172, 0x75d59b45, 0x7e89dc78, 0x7f4bb64f, 0x7d0d0816,
                    0x7ccf6221, 0x798074a4, 0x78421e93, 0x7a04a0ca, 0x7bc6cafd,
                    0x6cbc2eb0, 0x6d7e4487, 0x6f38fade, 0x6efa90e9, 0x6bb5866c,
                    0x6a77ec5b, 0x68315202, 0x69f33835, 0x62af7f08, 0x636d153f,
                    0x612bab66, 0x60e9c151, 0x65a6d7d4, 0x6464bde3, 0x662203ba,
                    0x67e0698d, 0x48d7cb20, 0x4915a117, 0x4b531f4e, 0x4a917579,
                    0x4fde63fc, 0x4e1c09cb, 0x4c5ab792, 0x4d98dda5, 0x46c49a98,
                    0x4706f0af, 0x45404ef6, 0x448224c1, 0x41cd3244, 0x400f5873,
                    0x4249e62a, 0x438b8c1d, 0x54f16850, 0x55330267, 0x5775bc3e,
                    0x56b7d609, 0x53f8c08c, 0x523aaabb, 0x507c14e2, 0x51be7ed5,
                    0x5ae239e8, 0x5b2053df, 0x5966ed86, 0x58a487b1, 0x5deb9134,
                    0x5c29fb03, 0x5e6f455a, 0x5fad2f6d, 0xe1351b80, 0xe0f771b7,
                    0xe2b1cfee, 0xe373a5d9, 0xe63cb35c, 0xe7fed96b, 0xe5b86732,
                    0xe47a0d05, 0xef264a38, 0xeee4200f, 0xeca29e56, 0xed60f461,
                    0xe82fe2e4, 0xe9ed88d3, 0xebab368a, 0xea695cbd, 0xfd13b8f0,
                    0xfcd1d2c7, 0xfe976c9e, 0xff5506a9, 0xfa1a102c, 0xfbd87a1b,
                    0xf99ec442, 0xf85cae75, 0xf300e948, 0xf2c2837f, 0xf0843d26,
                    0xf1465711, 0xf4094194, 0xf5cb2ba3, 0xf78d95fa, 0xf64fffcd,
                    0xd9785d60, 0xd8ba3757, 0xdafc890e, 0xdb3ee339, 0xde71f5bc,
                    0xdfb39f8b, 0xddf521d2, 0xdc374be5, 0xd76b0cd8, 0xd6a966ef,
                    0xd4efd8b6, 0xd52db281, 0xd062a404, 0xd1a0ce33, 0xd3e6706a,
                    0xd2241a5d, 0xc55efe10, 0xc49c9427, 0xc6da2a7e, 0xc7184049,
                    0xc25756cc, 0xc3953cfb, 0xc1d382a2, 0xc011e895, 0xcb4dafa8,
                    0xca8fc59f, 0xc8c97bc6, 0xc90b11f1, 0xcc440774, 0xcd866d43,
                    0xcfc0d31a, 0xce02b92d, 0x91af9640, 0x906dfc77, 0x922b422e,
                    0x93e92819, 0x96a63e9c, 0x976454ab, 0x9522eaf2, 0x94e080c5,
                    0x9fbcc7f8, 0x9e7eadcf, 0x9c381396, 0x9dfa79a1, 0x98b56f24,
                    0x99770513, 0x9b31bb4a, 0x9af3d17d, 0x8d893530, 0x8c4b5f07,
                    0x8e0de15e, 0x8fcf8b69, 0x8a809dec, 0x8b42f7db, 0x89044982,
                    0x88c623b5, 0x839a6488, 0x82580ebf, 0x801eb0e6, 0x81dcdad1,
                    0x8493cc54, 0x8551a663, 0x8717183a, 0x86d5720d, 0xa9e2d0a0,
                    0xa820ba97, 0xaa6604ce, 0xaba46ef9, 0xaeeb787c, 0xaf29124b,
                    0xad6fac12, 0xacadc625, 0xa7f18118, 0xa633eb2f, 0xa4755576,
                    0xa5b73f41, 0xa0f829c4, 0xa13a43f3, 0xa37cfdaa, 0xa2be979d,
                    0xb5c473d0, 0xb40619e7, 0xb640a7be, 0xb782cd89, 0xb2cddb0c,
                    0xb30fb13b, 0xb1490f62, 0xb08b6555, 0xbbd72268, 0xba15485f,
                    0xb853f606, 0xb9919c31, 0xbcde8ab4, 0xbd1ce083, 0xbf5a5eda,
                    0xbe9834ed,

                    // Table 3
                    0x00000000, 0xb8bc6765, 0xaa09c88b, 0x12b5afee, 0x8f629757,
                    0x37def032, 0x256b5fdc, 0x9dd738b9, 0xc5b428ef, 0x7d084f8a,
                    0x6fbde064, 0xd7018701, 0x4ad6bfb8, 0xf26ad8dd, 0xe0df7733,
                    0x58631056, 0x5019579f, 0xe8a530fa, 0xfa109f14, 0x42acf871,
                    0xdf7bc0c8, 0x67c7a7ad, 0x75720843, 0xcdce6f26, 0x95ad7f70,
                    0x2d111815, 0x3fa4b7fb, 0x8718d09e, 0x1acfe827, 0xa2738f42,
                    0xb0c620ac, 0x087a47c9, 0xa032af3e, 0x188ec85b, 0x0a3b67b5,
                    0xb28700d0, 0x2f503869, 0x97ec5f0c, 0x8559f0e2, 0x3de59787,
                    0x658687d1, 0xdd3ae0b4, 0xcf8f4f5a, 0x7733283f, 0xeae41086,
                    0x525877e3, 0x40edd80d, 0xf851bf68, 0xf02bf8a1, 0x48979fc4,
                    0x5a22302a, 0xe29e574f, 0x7f496ff6, 0xc7f50893, 0xd540a77d,
                    0x6dfcc018, 0x359fd04e, 0x8d23b72b, 0x9f9618c5, 0x272a7fa0,
                    0xbafd4719, 0x0241207c, 0x10f48f92, 0xa848e8f7, 0x9b14583d,
                    0x23a83f58, 0x311d90b6, 0x89a1f7d3, 0x1476cf6a, 0xaccaa80f,
                    0xbe7f07e1, 0x06c36084, 0x5ea070d2, 0xe61c17b7, 0xf4a9b859,
                    0x4c15df3c, 0xd1c2e785, 0x697e80e0, 0x7bcb2f0e, 0xc377486b,
                    0xcb0d0fa2, 0x73b168c7, 0x6104c729, 0xd9b8a04c, 0x446f98f5,
                    0xfcd3ff90, 0xee66507e, 0x56da371b, 0x0eb9274d, 0xb6054028,
                    0xa4b0efc6, 0x1c0c88a3, 0x81dbb01a, 0x3967d77f, 0x2bd27891,
                    0x936e1ff4, 0x3b26f703, 0x839a9066, 0x912f3f88, 0x299358ed,
                    0xb4446054, 0x0cf80731, 0x1e4da8df, 0xa6f1cfba, 0xfe92dfec,
                    0x462eb889, 0x549b1767, 0xec277002, 0x71f048bb, 0xc94c2fde,
                    0xdbf98030, 0x6345e755, 0x6b3fa09c, 0xd383c7f9, 0xc1366817,
                    0x798a0f72, 0xe45d37cb, 0x5ce150ae, 0x4e54ff40, 0xf6e89825,
                    0xae8b8873, 0x1637ef16, 0x048240f8, 0xbc3e279d, 0x21e91f24,
                    0x99557841, 0x8be0d7af, 0x335cb0ca, 0xed59b63b, 0x55e5d15e,
                    0x47507eb0, 0xffec19d5, 0x623b216c, 0xda874609, 0xc832e9e7,
                    0x708e8e82, 0x28ed9ed4, 0x9051f9b1, 0x82e4565f, 0x3a58313a,
                    0xa78f0983, 0x1f336ee6, 0x0d86c108, 0xb53aa66d, 0xbd40e1a4,
                    0x05fc86c1, 0x1749292f, 0xaff54e4a, 0x322276f3, 0x8a9e1196,
                    0x982bbe78, 0x2097d91d, 0x78f4c94b, 0xc048ae2e, 0xd2fd01c0,
                    0x6a4166a5, 0xf7965e1c, 0x4f2a3979, 0x5d9f9697, 0xe523f1f2,
                    0x4d6b1905, 0xf5d77e60, 0xe762d18e, 0x5fdeb6eb, 0xc2098e52,
                    0x7ab5e937, 0x680046d9, 0xd0bc21bc, 0x88df31ea, 0x3063568f,
                    0x22d6f961, 0x9a6a9e04, 0x07bda6bd, 0xbf01c1d8, 0xadb46e36,
                    0x15080953, 0x1d724e9a, 0xa5ce29ff, 0xb77b8611, 0x0fc7e174,
                    0x9210d9cd, 0x2aacbea8, 0x38191146, 0x80a57623, 0xd8c66675,
                    0x607a0110, 0x72cfaefe, 0xca73c99b, 0x57a4f122, 0xef189647,
                    0xfdad39a9, 0x45115ecc, 0x764dee06, 0xcef18963, 0xdc44268d,
                    0x64f841e8, 0xf92f7951, 0x41931e34, 0x5326b1da, 0xeb9ad6bf,
                    0xb3f9c6e9, 0x0b45a18c, 0x19f00e62, 0xa14c6907, 0x3c9b51be,
                    0x842736db, 0x96929935, 0x2e2efe50, 0x2654b999, 0x9ee8defc,
                    0x8c5d7112, 0x34e11677, 0xa9362ece, 0x118a49ab, 0x033fe645,
                    0xbb838120, 0xe3e09176, 0x5b5cf613, 0x49e959fd, 0xf1553e98,
                    0x6c820621, 0xd43e6144, 0xc68bceaa, 0x7e37a9cf, 0xd67f4138,
                    0x6ec3265d, 0x7c7689b3, 0xc4caeed6, 0x591dd66f, 0xe1a1b10a,
                    0xf3141ee4, 0x4ba87981, 0x13cb69d7, 0xab770eb2, 0xb9c2a15c,
                    0x017ec639, 0x9ca9fe80, 0x241599e5, 0x36a0360b, 0x8e1c516e,
                    0x866616a7, 0x3eda71c2, 0x2c6fde2c, 0x94d3b949, 0x090481f0,
                    0xb1b8e695, 0xa30d497b, 0x1bb12e1e, 0x43d23e48, 0xfb6e592d,
                    0xe9dbf6c3, 0x516791a6, 0xccb0a91f, 0x740cce7a, 0x66b96194,
                    0xde0506f1,
                    // Constants for Neon CRC232 implementation
                    // k3 = 0x78ED02D5 = x^288 mod poly - bit reversed
                    // k4 = 0xED627DAE = x^256 mod poly - bit reversed
                    0x78ED02D5, 0xED627DAE, // k4:k3
                    0xED78D502, 0x62EDAE7D, // byte swap
                    0x02D578ED, 0x7DAEED62, // word swap
                    0xD502ED78, 0xAE7D62ED, // byte swap of word swap

                    // Constants for CRC-32 crypto pmull implementation
                    0xe88ef372, 0x00000001,
                    0x4a7fe880, 0x00000001,
                    0x54442bd4, 0x00000001,
                    0xc6e41596, 0x00000001,
                    0x3db1ecdc, 0x00000000,
                    0x74359406, 0x00000001,
                    0xf1da05aa, 0x00000000,
                    0x5a546366, 0x00000001,
                    0x751997d0, 0x00000001,
                    0xccaa009e, 0x00000000,

                    // Constants for CRC-32C crypto pmull implementation
                    0x6992cea2, 0x00000000,
                    0x0d3b6092, 0x00000000,
                    0x740eef02, 0x00000000,
                    0x9e4addf8, 0x00000000,
                    0x1c291d04, 0x00000000,
                    0xd82c63da, 0x00000001,
                    0x384aa63a, 0x00000001,
                    0xba4fc28e, 0x00000000,
                    0xf20c0dfe, 0x00000000,
                    0x4cd00bd6, 0x00000001,
    });

    static void emitLoadCRC32TableAddress(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register table) {
        crb.recordDataReferenceInCode(CRC32_TABLE);
        masm.adrpAdd(table);
    }

    @Def private AllocatableValue resultValue;
    @Use private AllocatableValue crcValue;
    @Use private AllocatableValue bufValue;
    @Use private AllocatableValue lenValue;

    @Temp private Value[] temps;

    public AArch64CRC32UpdateBytesOp(AllocatableValue resultValue, AllocatableValue crcValue, AllocatableValue bufValue, AllocatableValue lenValue) {
        super(TYPE);
        this.resultValue = resultValue;
        this.crcValue = crcValue;
        this.bufValue = bufValue;
        this.lenValue = lenValue;
        GraalError.guarantee(resultValue instanceof RegisterValue resultReg && r0.equals(resultReg.getRegister()), "result should be fixed to r0, but is %s", resultValue);
        GraalError.guarantee(crcValue instanceof RegisterValue crcReg && r0.equals(crcReg.getRegister()), "crc should be fixed to r0, but is %s", crcValue);
        GraalError.guarantee(bufValue instanceof RegisterValue bufReg && r1.equals(bufReg.getRegister()), "bufferAddress should be fixed to r1, but is %s", bufValue);
        GraalError.guarantee(lenValue instanceof RegisterValue lenReg && r2.equals(lenReg.getRegister()), "length should be fixed to r2, but is %s", lenValue);
        this.temps = new Value[]{
                        r1.asValue(),
                        r2.asValue(),
                        r3.asValue(),
                        r4.asValue(),
                        r5.asValue(),
                        r6.asValue(),
                        r7.asValue(),
                        v0.asValue(), v1.asValue(), v2.asValue(), v3.asValue(),
                        v4.asValue(), v5.asValue(), v6.asValue(), v7.asValue(),
                        v16.asValue(), v17.asValue(), v18.asValue(), v19.asValue(),
                        v20.asValue(), v21.asValue(), v22.asValue(), v23.asValue(),
                        v24.asValue(), v25.asValue(), v26.asValue(), v27.asValue(),
                        v28.asValue(), v29.asValue(), v30.asValue(), v31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register crc = asRegister(crcValue);
        Register buf = asRegister(bufValue);
        Register len = asRegister(lenValue);
        Register table0 = r3;
        Register table1 = r4;
        Register table2 = r5;
        Register table3 = r6;

        if (masm.supports(CPUFeature.CRC32)) {
            if (masm.supports(CPUFeature.PMULL, CPUFeature.SHA3)) {
                emitKernelCRC32UsingCryptoPmull(crb, masm, crc, buf, len, table0, table1, table2, table3);
            } else {
                emitKernelCRC32UsingCRC32(masm, crc, buf, len, table0, table1, table2, table3);
            }
        } else {
            emitKernelCRC32Fallback(crb, masm, crc, buf, len, table0, table1, table2, table3);
        }
    }

    private static void emitKernelCRC32UsingCryptoPmull(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register crc, Register buf,
                    Register len, Register tmp0, Register tmp1, Register tmp2, Register tmp3) {
        Label crcBy4Loop = new Label();
        Label crcBy1Loop = new Label();
        Label crcLess128 = new Label();
        Label crcBy128Pre = new Label();
        Label crcBy32Loop = new Label();
        Label crcLess32 = new Label();
        Label lExit = new Label();

        masm.subs(64, tmp0, len, 384);
        masm.not(32, crc, crc);
        masm.branchConditionally(ConditionFlag.GE, crcBy128Pre);
        masm.bind(crcLess128);
        masm.subs(64, len, len, 32);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);
        masm.bind(crcLess32);
        masm.adds(64, len, len, 32 - 4);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy32Loop);
        masm.ldp(64, tmp0, tmp1, AArch64Address.createPairBaseRegisterOnlyAddress(64, buf));
        masm.crc32(64, crc, crc, tmp0);
        masm.ldp(64, tmp2, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, buf, 16));
        masm.crc32(64, crc, crc, tmp1);
        masm.add(64, buf, buf, 32);
        masm.crc32(64, crc, crc, tmp2);
        masm.subs(64, len, len, 32);
        masm.crc32(64, crc, crc, tmp3);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);
        masm.adds(64, zr, len, 32);
        masm.branchConditionally(ConditionFlag.NE, crcLess32);
        masm.jmp(lExit);

        masm.bind(crcBy4Loop);
        masm.ldr(32, tmp0, AArch64Address.createImmediateAddress(32, IMMEDIATE_POST_INDEXED, buf, 4));
        masm.subs(64, len, len, 4);
        masm.crc32(32, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.LE, lExit);
        masm.bind(crcBy1Loop);
        masm.ldr(8, tmp0, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, buf, 1));
        masm.subs(64, len, len, 1);
        masm.crc32(8, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy128Pre);
        emitKernelCRC32CommonFoldUsingCryptoPmull(crb, masm, crc, buf, len, tmp0, tmp1);
        masm.mov(64, crc, zr);
        masm.crc32(64, crc, crc, tmp0);
        masm.crc32(64, crc, crc, tmp1);

        masm.cbnz(64, len, crcLess128);

        masm.bind(lExit);
        masm.not(32, crc, crc);
    }

    private static void emitKernelCRC32CommonFoldUsingCryptoPmull(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register crc, Register buf,
                    Register len, Register tmp0, Register tmp1) {
        Label crcBy128Loop = new Label();
        int tableOffset = CRC32_PMULL_TABLE_OFFSET;

        masm.sub(64, len, len, 256);
        Register table = tmp0;
        emitLoadCRC32TableAddress(crb, masm, table);
        masm.add(64, table, table, tableOffset);

        // Registers v0..v7 are used as data registers.
        // Registers v16..v31 are used as tmp registers.
        masm.sub(64, buf, buf, 0x10);
        masm.fldr(128, v0, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x10), false);
        masm.fldr(128, v1, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x20), false);
        masm.fldr(128, v2, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x30), false);
        masm.fldr(128, v3, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x40), false);
        masm.fldr(128, v4, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x50), false);
        masm.fldr(128, v5, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x60), false);
        masm.fldr(128, v6, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x70), false);
        masm.fldr(128, v7, AArch64Address.createImmediateAddress(128, IMMEDIATE_PRE_INDEXED, buf, 0x80), false);

        masm.neon.moviVI(FullReg, v31, 0);
        masm.neon.insXG(Word, v31, 0, crc);
        masm.neon.eorVVV(FullReg, v0, v0, v31);

        // Register v16 contains constants from the crc table.
        masm.fldr(128, v16, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0));
        masm.jmp(crcBy128Loop);

        masm.align(16);
        masm.bind(crcBy128Loop);
        masm.neon.pmullVVV(DoubleWord, v17, v0, v16);
        masm.neon.pmull2VVV(DoubleWord, v18, v0, v16);
        masm.fldr(128, v0, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x10));
        masm.neon.eor3VVVV(v0, v17, v18, v0);

        masm.neon.pmullVVV(DoubleWord, v19, v1, v16);
        masm.neon.pmull2VVV(DoubleWord, v20, v1, v16);
        masm.fldr(128, v1, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x20));
        masm.neon.eor3VVVV(v1, v19, v20, v1);

        masm.neon.pmullVVV(DoubleWord, v21, v2, v16);
        masm.neon.pmull2VVV(DoubleWord, v22, v2, v16);
        masm.fldr(128, v2, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x30));
        masm.neon.eor3VVVV(v2, v21, v22, v2);

        masm.neon.pmullVVV(DoubleWord, v23, v3, v16);
        masm.neon.pmull2VVV(DoubleWord, v24, v3, v16);
        masm.fldr(128, v3, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x40));
        masm.neon.eor3VVVV(v3, v23, v24, v3);

        masm.neon.pmullVVV(DoubleWord, v25, v4, v16);
        masm.neon.pmull2VVV(DoubleWord, v26, v4, v16);
        masm.fldr(128, v4, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x50));
        masm.neon.eor3VVVV(v4, v25, v26, v4);

        masm.neon.pmullVVV(DoubleWord, v27, v5, v16);
        masm.neon.pmull2VVV(DoubleWord, v28, v5, v16);
        masm.fldr(128, v5, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x60));
        masm.neon.eor3VVVV(v5, v27, v28, v5);

        masm.neon.pmullVVV(DoubleWord, v29, v6, v16);
        masm.neon.pmull2VVV(DoubleWord, v30, v6, v16);
        masm.fldr(128, v6, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, buf, 0x70));
        masm.neon.eor3VVVV(v6, v29, v30, v6);

        // Reuse registers v23, v24.
        // Using them won't block the first instruction of the next iteration.
        masm.neon.pmullVVV(DoubleWord, v23, v7, v16);
        masm.neon.pmull2VVV(DoubleWord, v24, v7, v16);
        masm.fldr(128, v7, AArch64Address.createImmediateAddress(128, IMMEDIATE_PRE_INDEXED, buf, 0x80));
        masm.neon.eor3VVVV(v7, v23, v24, v7);

        masm.subs(64, len, len, 0x80);
        masm.branchConditionally(ConditionFlag.GE, crcBy128Loop);

        // fold into 512 bits
        // Use v31 for constants because v16 can be still in use.
        masm.fldr(128, v31, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x10));

        masm.neon.pmullVVV(DoubleWord, v17, v0, v31);
        masm.neon.pmull2VVV(DoubleWord, v18, v0, v31);
        masm.neon.eor3VVVV(v0, v17, v18, v4);

        masm.neon.pmullVVV(DoubleWord, v19, v1, v31);
        masm.neon.pmull2VVV(DoubleWord, v20, v1, v31);
        masm.neon.eor3VVVV(v1, v19, v20, v5);

        masm.neon.pmullVVV(DoubleWord, v21, v2, v31);
        masm.neon.pmull2VVV(DoubleWord, v22, v2, v31);
        masm.neon.eor3VVVV(v2, v21, v22, v6);

        masm.neon.pmullVVV(DoubleWord, v23, v3, v31);
        masm.neon.pmull2VVV(DoubleWord, v24, v3, v31);
        masm.neon.eor3VVVV(v3, v23, v24, v7);

        // fold into 128 bits
        // Use v17 for constants because v31 can be still in use.
        masm.fldr(128, v17, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x20));
        masm.neon.pmullVVV(DoubleWord, v25, v0, v17);
        masm.neon.pmull2VVV(DoubleWord, v26, v0, v17);
        masm.neon.eor3VVVV(v3, v3, v25, v26);

        // Use v18 for constants because v17 can be still in use.
        masm.fldr(128, v18, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x30));
        masm.neon.pmullVVV(DoubleWord, v27, v1, v18);
        masm.neon.pmull2VVV(DoubleWord, v28, v1, v18);
        masm.neon.eor3VVVV(v3, v3, v27, v28);

        // Use v19 for constants because v18 can be still in use.
        masm.fldr(128, v19, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, table, 0x40));
        masm.neon.pmullVVV(DoubleWord, v29, v2, v19);
        masm.neon.pmull2VVV(DoubleWord, v30, v2, v19);
        masm.neon.eor3VVVV(v0, v3, v29, v30);

        masm.add(64, len, len, 0x80);
        masm.add(64, buf, buf, 0x10);

        masm.neon.umovGX(DoubleWord, tmp0, v0, 0);
        masm.neon.umovGX(DoubleWord, tmp1, v0, 1);
    }

    private static void emitKernelCRC32UsingCRC32(AArch64MacroAssembler masm, Register crc, Register buf,
                    Register len, Register tmp0, Register tmp1, Register tmp2, Register tmp3) {
        Label crcBy64Loop = new Label();
        Label crcBy4Loop = new Label();
        Label crcBy1Loop = new Label();
        Label crcLess64 = new Label();
        Label crcBy64Pre = new Label();
        Label crcBy32Loop = new Label();
        Label crcLess32 = new Label();
        Label lExit = new Label();

        masm.not(32, crc, crc);

        masm.subs(64, len, len, 128);
        masm.branchConditionally(ConditionFlag.GE, crcBy64Pre);
        masm.bind(crcLess64);
        masm.adds(64, len, len, 128 - 32);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);
        masm.bind(crcLess32);
        masm.adds(64, len, len, 32 - 4);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy32Loop);
        masm.ldp(64, tmp0, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, buf, 16));
        masm.subs(64, len, len, 32);
        masm.crc32(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, buf, 8));
        masm.crc32(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, buf, 8));
        masm.crc32(64, crc, crc, tmp2);
        masm.crc32(64, crc, crc, tmp3);
        masm.branchConditionally(ConditionFlag.GE, crcBy32Loop);
        masm.adds(64, zr, len, 32);
        masm.branchConditionally(ConditionFlag.NE, crcLess32);
        masm.jmp(lExit);

        masm.bind(crcBy4Loop);
        masm.ldr(32, tmp0, AArch64Address.createImmediateAddress(32, IMMEDIATE_POST_INDEXED, buf, 4));
        masm.subs(64, len, len, 4);
        masm.crc32(32, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GE, crcBy4Loop);
        masm.adds(64, len, len, 4);
        masm.branchConditionally(ConditionFlag.LE, lExit);
        masm.bind(crcBy1Loop);
        masm.ldr(8, tmp0, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, buf, 1));
        masm.subs(64, len, len, 1);
        masm.crc32(8, crc, crc, tmp0);
        masm.branchConditionally(ConditionFlag.GT, crcBy1Loop);
        masm.jmp(lExit);

        masm.bind(crcBy64Pre);
        masm.sub(64, buf, buf, 8);
        masm.ldp(64, tmp0, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, buf, 8));
        masm.crc32(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 24));
        masm.crc32(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 32));
        masm.crc32(64, crc, crc, tmp2);
        masm.ldr(64, tmp0, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 40));
        masm.crc32(64, crc, crc, tmp3);
        masm.ldr(64, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 48));
        masm.crc32(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 56));
        masm.crc32(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, buf, 64));

        masm.jmp(crcBy64Loop);

        masm.align(64);
        masm.bind(crcBy64Loop);
        masm.subs(64, len, len, 64);
        masm.crc32(64, crc, crc, tmp2);
        masm.ldr(64, tmp0, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 8));
        masm.crc32(64, crc, crc, tmp3);
        masm.ldr(64, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 16));
        masm.crc32(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 24));
        masm.crc32(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 32));
        masm.crc32(64, crc, crc, tmp2);
        masm.ldr(64, tmp0, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 40));
        masm.crc32(64, crc, crc, tmp3);
        masm.ldr(64, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 48));
        masm.crc32(64, crc, crc, tmp0);
        masm.ldr(64, tmp2, AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, buf, 56));
        masm.crc32(64, crc, crc, tmp1);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, buf, 64));
        masm.branchConditionally(ConditionFlag.GE, crcBy64Loop);

        // post-loop
        masm.crc32(64, crc, crc, tmp2);
        masm.crc32(64, crc, crc, tmp3);

        masm.sub(64, len, len, 64);
        masm.add(64, buf, buf, 8);
        masm.adds(64, zr, len, 128);
        masm.branchConditionally(ConditionFlag.NE, crcLess64);
        masm.bind(lExit);
        masm.not(32, crc, crc);
    }

    // @formatter:off
    /**
     * Emits code to update CRC-32 with a byte value according to constants in table
     *
     * @param crc   [in, out] Register containing the crc.
     * @param val   [in] Register containing the byte to fold into the CRC.
     * @param table [in] Register containing the table of crc constants.
     *
     * uint32_t crc;
     * val = crc_table[(val ^ crc) & 0xFF];
     * crc = val ^ (crc >> 8);
     */
    // @formatter:on
    private static void emitUpdateByteCRC32(AArch64MacroAssembler masm, Register crc, Register val, Register table) {
        masm.eor(64, val, val, crc);
        masm.and(64, val, val, 0xff);
        masm.ldr(32, val, AArch64Address.createRegisterOffsetAddress(32, table, val, true));
        masm.eor(64, crc, val, crc, LSR, 8);
    }

    // @formatter:off
    /**
     * Emits code to update CRC-32 with a 32-bit value according to tables 0 to 3
     *
     * @param crc    [in, out] Register containing the crc.
     * @param v      [in] Register containing the 32-bit to fold into the CRC.
     * @param tmp    scratch register
     * @param table0 [in] Register containing table 0 of crc constants.
     * @param table1 [in] Register containing table 1 of crc constants.
     * @param table2 [in] Register containing table 2 of crc constants.
     * @param table3 [in] Register containing table 3 of crc constants.
     * @param upper  true if the upper 32 bits of {@code v} are used
     *
     * uint32_t crc;
     *   v = crc ^ v
     *   crc = table3[v&0xff]^table2[(v>>8)&0xff]^table1[(v>>16)&0xff]^table0[v>>24]
     */
    // @formatter:on
    private static void emitUpdateWordCRC32(AArch64MacroAssembler masm, Register crc, Register v, Register tmp,
                    Register table0, Register table1, Register table2, Register table3, boolean upper) {
        masm.eor(64, v, crc, v, upper ? LSR : LSL, upper ? 32 : 0);
        masm.ubfx(64, tmp, v, 0, 8);
        masm.ldr(32, crc, AArch64Address.createRegisterOffsetAddress(32, table3, tmp, true));
        masm.ubfx(64, tmp, v, 8, 8);
        masm.ldr(32, tmp, AArch64Address.createRegisterOffsetAddress(32, table2, tmp, true));
        masm.eor(64, crc, crc, tmp);
        masm.ubfx(64, tmp, v, 16, 8);
        masm.ldr(32, tmp, AArch64Address.createRegisterOffsetAddress(32, table1, tmp, true));
        masm.eor(64, crc, crc, tmp);
        masm.ubfx(64, tmp, v, 24, 8);
        masm.ldr(32, tmp, AArch64Address.createRegisterOffsetAddress(32, table0, tmp, true));
        masm.eor(64, crc, crc, tmp);
    }

    // @formatter:off
    /**
     * @param crc    register containing existing CRC (32-bit)
     * @param buf    register pointing to input byte buffer (byte*)
     * @param len    register containing number of bytes
     * @param table0 register that will contain address of CRC table
     */
    // @formatter:on
    private static void emitKernelCRC32Fallback(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register crc, Register buf, Register len,
                    Register table0, Register table1, Register table2, Register table3) {
        Label lBy16 = new Label();
        Label lBy16Loop = new Label();
        Label lBy4Loop = new Label();
        Label lBy1Loop = new Label();
        Label lFold = new Label();
        Label lExit = new Label();

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register tmp1 = sc1.getRegister();
            Register tmp2 = sc2.getRegister();
            Register tmp3 = r7;
            masm.not(32, crc, crc);

            emitLoadCRC32TableAddress(crb, masm, table0);
            masm.add(64, table1, table0, 1 * 256 * Integer.BYTES);
            masm.add(64, table2, table0, 2 * 256 * Integer.BYTES);
            masm.add(64, table3, table0, 3 * 256 * Integer.BYTES);

            // This local PMULL guard does not exist in HotSpot. If HotSpot selects this fallback
            // because CRC32 is unavailable, and PMULL is also unavailable, the unguarded vector
            // block may emit illegal instructions.
            // Graal exposes the fallback on broader AArch64 targets, so stay on the scalar table
            // path when PMULL is unavailable.
            if (masm.supports(CPUFeature.PMULL)) {
                // Neon code start
                masm.compare(64, len, 64);
                masm.branchConditionally(ConditionFlag.LT, lBy16);
                masm.neon.eorVVV(FullReg, v16, v16, v16);

                masm.add(64, tmp1, table0, CRC32_NEON_TABLE_OFFSET); // Point at the Neon constants

                masm.neon.ld1MultipleVV(FullReg, DoubleWord, v0, v1,
                                AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_2R, FullReg, DoubleWord, buf, 32));
                masm.neon.ld1rV(FullReg, DoubleWord, v4, AArch64Address.createStructureImmediatePostIndexAddress(LD1R, FullReg, DoubleWord, tmp1, 8));
                masm.neon.ld1rV(FullReg, DoubleWord, v5, AArch64Address.createStructureImmediatePostIndexAddress(LD1R, FullReg, DoubleWord, tmp1, 8));
                masm.neon.ld1rV(FullReg, DoubleWord, v6, AArch64Address.createStructureImmediatePostIndexAddress(LD1R, FullReg, DoubleWord, tmp1, 8));
                masm.neon.ld1rV(FullReg, DoubleWord, v7, AArch64Address.createStructureImmediatePostIndexAddress(LD1R, FullReg, DoubleWord, tmp1, 8));
                masm.neon.insXG(Word, v16, 0, crc);

                masm.neon.eorVVV(FullReg, v0, v0, v16);
                masm.sub(64, len, len, 64);

                masm.bind(lFold);
                masm.neon.pmullVVV(ElementSize.Byte, v22, v0, v5);
                masm.neon.pmullVVV(ElementSize.Byte, v20, v0, v7);
                masm.neon.pmullVVV(ElementSize.Byte, v23, v0, v4);
                masm.neon.pmullVVV(ElementSize.Byte, v21, v0, v6);

                masm.neon.pmull2VVV(ElementSize.Byte, v18, v0, v5);
                masm.neon.pmull2VVV(ElementSize.Byte, v16, v0, v7);
                masm.neon.pmull2VVV(ElementSize.Byte, v19, v0, v4);
                masm.neon.pmull2VVV(ElementSize.Byte, v17, v0, v6);

                masm.neon.uzp1VVV(FullReg, HalfWord, v24, v20, v22);
                masm.neon.uzp2VVV(FullReg, HalfWord, v25, v20, v22);
                masm.neon.eorVVV(FullReg, v20, v24, v25);

                masm.neon.uzp1VVV(FullReg, HalfWord, v26, v16, v18);
                masm.neon.uzp2VVV(FullReg, HalfWord, v27, v16, v18);
                masm.neon.eorVVV(FullReg, v16, v26, v27);

                masm.neon.ushll2VVI(HalfWord, v22, v20, 8);
                masm.neon.ushllVVI(HalfWord, v20, v20, 8);

                masm.neon.ushll2VVI(HalfWord, v18, v16, 8);
                masm.neon.ushllVVI(HalfWord, v16, v16, 8);

                masm.neon.eorVVV(FullReg, v22, v23, v22);
                masm.neon.eorVVV(FullReg, v18, v19, v18);
                masm.neon.eorVVV(FullReg, v20, v21, v20);
                masm.neon.eorVVV(FullReg, v16, v17, v16);

                masm.neon.uzp1VVV(FullReg, DoubleWord, v17, v16, v20);
                masm.neon.uzp2VVV(FullReg, DoubleWord, v21, v16, v20);
                masm.neon.eorVVV(FullReg, v17, v17, v21);

                masm.neon.ushll2VVI(Word, v20, v17, 16);
                masm.neon.ushllVVI(Word, v16, v17, 16);

                masm.neon.eorVVV(FullReg, v20, v20, v22);
                masm.neon.eorVVV(FullReg, v16, v16, v18);

                masm.neon.uzp1VVV(FullReg, DoubleWord, v17, v20, v16);
                masm.neon.uzp2VVV(FullReg, DoubleWord, v21, v20, v16);
                masm.neon.eorVVV(FullReg, v28, v17, v21);

                masm.neon.pmullVVV(ElementSize.Byte, v22, v1, v5);
                masm.neon.pmullVVV(ElementSize.Byte, v20, v1, v7);
                masm.neon.pmullVVV(ElementSize.Byte, v23, v1, v4);
                masm.neon.pmullVVV(ElementSize.Byte, v21, v1, v6);

                masm.neon.pmull2VVV(ElementSize.Byte, v18, v1, v5);
                masm.neon.pmull2VVV(ElementSize.Byte, v16, v1, v7);
                masm.neon.pmull2VVV(ElementSize.Byte, v19, v1, v4);
                masm.neon.pmull2VVV(ElementSize.Byte, v17, v1, v6);

                masm.neon.ld1MultipleVV(FullReg, DoubleWord, v0, v1,
                                AArch64Address.createStructureImmediatePostIndexAddress(LD1_MULTIPLE_2R, FullReg, DoubleWord, buf, 32));

                masm.neon.uzp1VVV(FullReg, HalfWord, v24, v20, v22);
                masm.neon.uzp2VVV(FullReg, HalfWord, v25, v20, v22);
                masm.neon.eorVVV(FullReg, v20, v24, v25);

                masm.neon.uzp1VVV(FullReg, HalfWord, v26, v16, v18);
                masm.neon.uzp2VVV(FullReg, HalfWord, v27, v16, v18);
                masm.neon.eorVVV(FullReg, v16, v26, v27);

                masm.neon.ushll2VVI(HalfWord, v22, v20, 8);
                masm.neon.ushllVVI(HalfWord, v20, v20, 8);

                masm.neon.ushll2VVI(HalfWord, v18, v16, 8);
                masm.neon.ushllVVI(HalfWord, v16, v16, 8);

                masm.neon.eorVVV(FullReg, v22, v23, v22);
                masm.neon.eorVVV(FullReg, v18, v19, v18);
                masm.neon.eorVVV(FullReg, v20, v21, v20);
                masm.neon.eorVVV(FullReg, v16, v17, v16);

                masm.neon.uzp1VVV(FullReg, DoubleWord, v17, v16, v20);
                masm.neon.uzp2VVV(FullReg, DoubleWord, v21, v16, v20);
                masm.neon.eorVVV(FullReg, v16, v17, v21);

                masm.neon.ushll2VVI(Word, v20, v16, 16);
                masm.neon.ushllVVI(Word, v16, v16, 16);

                masm.neon.eorVVV(FullReg, v20, v22, v20);
                masm.neon.eorVVV(FullReg, v16, v16, v18);

                masm.neon.uzp1VVV(FullReg, DoubleWord, v17, v20, v16);
                masm.neon.uzp2VVV(FullReg, DoubleWord, v21, v20, v16);
                masm.neon.eorVVV(FullReg, v20, v17, v21);

                masm.neon.shlVVI(FullReg, DoubleWord, v16, v28, 1);
                masm.neon.shlVVI(FullReg, DoubleWord, v17, v20, 1);

                masm.neon.eorVVV(FullReg, v0, v0, v16);
                masm.neon.eorVVV(FullReg, v1, v1, v17);

                masm.subs(64, len, len, 32);
                masm.branchConditionally(ConditionFlag.GE, lFold);

                masm.mov(64, crc, zr);
                masm.neon.umovGX(DoubleWord, tmp1, v0, 0);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, false);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, true);
                masm.neon.umovGX(DoubleWord, tmp1, v0, 1);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, false);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, true);
                masm.neon.umovGX(DoubleWord, tmp1, v1, 0);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, false);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, true);
                masm.neon.umovGX(DoubleWord, tmp1, v1, 1);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, false);
                emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, true);

                masm.add(64, len, len, 32);
                // Neon code end
            }

            masm.bind(lBy16);
            masm.subs(64, len, len, 16);
            masm.branchConditionally(ConditionFlag.GE, lBy16Loop);
            masm.adds(64, len, len, 16 - 4);
            masm.branchConditionally(ConditionFlag.GE, lBy4Loop);
            masm.adds(64, len, len, 4);
            masm.branchConditionally(ConditionFlag.GT, lBy1Loop);
            masm.jmp(lExit);

            masm.bind(lBy4Loop);
            masm.ldr(32, tmp1, AArch64Address.createImmediateAddress(32, IMMEDIATE_POST_INDEXED, buf, 4));
            emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, false);
            masm.subs(64, len, len, 4);
            masm.branchConditionally(ConditionFlag.GE, lBy4Loop);
            masm.adds(64, len, len, 4);
            masm.branchConditionally(ConditionFlag.LE, lExit);

            masm.bind(lBy1Loop);
            masm.subs(64, len, len, 1);
            masm.ldr(8, tmp1, AArch64Address.createImmediateAddress(8, IMMEDIATE_POST_INDEXED, buf, 1));
            emitUpdateByteCRC32(masm, crc, tmp1, table0);
            masm.branchConditionally(ConditionFlag.GT, lBy1Loop);
            masm.jmp(lExit);

            masm.align(64);
            masm.bind(lBy16Loop);
            masm.subs(64, len, len, 16);
            masm.ldp(64, tmp1, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, buf, 16));
            emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, false);
            emitUpdateWordCRC32(masm, crc, tmp1, tmp2, table0, table1, table2, table3, true);
            emitUpdateWordCRC32(masm, crc, tmp3, tmp2, table0, table1, table2, table3, false);
            emitUpdateWordCRC32(masm, crc, tmp3, tmp2, table0, table1, table2, table3, true);
            masm.branchConditionally(ConditionFlag.GE, lBy16Loop);
            masm.adds(64, len, len, 16 - 4);
            masm.branchConditionally(ConditionFlag.GE, lBy4Loop);
            masm.adds(64, len, len, 4);
            masm.branchConditionally(ConditionFlag.GT, lBy1Loop);

            masm.bind(lExit);
            masm.not(32, crc, crc);
        }
    }
}
