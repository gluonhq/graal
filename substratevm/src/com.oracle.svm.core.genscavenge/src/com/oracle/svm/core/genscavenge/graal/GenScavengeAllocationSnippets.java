/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.AllocationSnippets;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatPodNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;

import jdk.vm.ci.meta.JavaKind;

final class GenScavengeAllocationSnippets implements Snippets {
    @Snippet
    public static Object formatObjectSnippet(Word memory, DynamicHub hub, boolean rememberedSet, AllocationSnippets.FillContent fillContents, boolean emitMemoryBarrier,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getPureInstanceSize(layoutEncoding);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, false);
        return alloc().formatObject(objectHeader, size, memory, fillContents, emitMemoryBarrier, false, snippetCounters);
    }

    @Snippet
    public static Object formatArraySnippet(Word memory, DynamicHub hub, int length, boolean rememberedSet, boolean unaligned, AllocationSnippets.FillContent fillContents, int fillStartOffset,
                    boolean emitMemoryBarrier, @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getArraySize(layoutEncoding, length);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        return alloc().formatArray(objectHeader, size, length, memory, fillContents, fillStartOffset,
                        emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
    }

    @Snippet
    public static Object formatPodSnippet(Word memory, DynamicHub hub, int arrayLength, byte[] referenceMap, boolean rememberedSet, boolean unaligned, AllocationSnippets.FillContent fillContents,
                    int fillStartOffset, @ConstantParameter boolean emitMemoryBarrier, @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {

        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        byte[] refMapNonNull = (byte[]) PiNode.piCastNonNull(referenceMap, SnippetAnchorNode.anchor());
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        return alloc().formatPod(objectHeader, hubNonNull, arrayLength, refMapNonNull, memory, fillContents, fillStartOffset,
                        emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
    }

    private static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        return ObjectHeaderImpl.encodeAsObjectHeader(hub, rememberedSet, unaligned);
    }

    @Fold
    static SubstrateAllocationSnippets alloc() {
        return ImageSingletons.lookup(SubstrateAllocationSnippets.class);
    }

    public static class Templates extends SubstrateTemplates {
        private final SubstrateAllocationSnippets.Templates baseTemplates;
        private final SnippetInfo formatObject;
        private final SnippetInfo formatArray;
        private final SnippetInfo formatPod;

        Templates(OptionValues options, Providers providers, SubstrateAllocationSnippets.Templates baseTemplates) {
            super(options, providers);
            this.baseTemplates = baseTemplates;
            formatObject = snippet(GenScavengeAllocationSnippets.class, "formatObjectSnippet");
            formatArray = snippet(GenScavengeAllocationSnippets.class, "formatArraySnippet");
            formatPod = snippet(GenScavengeAllocationSnippets.class, "formatPodSnippet", NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
        }

        public void registerLowering(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            lowerings.put(FormatObjectNode.class, new FormatObjectLowering());
            lowerings.put(FormatArrayNode.class, new FormatArrayLowering());
            lowerings.put(FormatPodNode.class, new FormatPodLowering());
        }

        private class FormatObjectLowering implements NodeLoweringProvider<FormatObjectNode> {
            @Override
            public void lower(FormatObjectNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatObject, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("fillContents", node.getFillContents());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class FormatArrayLowering implements NodeLoweringProvider<FormatArrayNode> {
            @Override
            public void lower(FormatArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("length", node.getLength());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.add("fillContents", node.getFillContents());
                args.add("fillStartOffset", node.getFillStartOffset());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class FormatPodLowering implements NodeLoweringProvider<FormatPodNode> {
            @Override
            public void lower(FormatPodNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatPod, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("arrayLength", node.getArrayLength());
                args.add("referenceMap", node.getReferenceMap());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.add("fillContents", node.getFillContents());
                args.add("fillStartOffset", node.getFillStartOffset());
                args.addConst("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
