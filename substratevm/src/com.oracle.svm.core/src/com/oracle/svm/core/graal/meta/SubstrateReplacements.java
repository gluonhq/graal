/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static jdk.compiler.graal.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;
import static jdk.compiler.graal.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.api.replacements.SnippetReflectionProvider;
import jdk.compiler.graal.bytecode.BytecodeProvider;
import jdk.compiler.graal.core.common.GraalOptions;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.core.common.type.TypeReference;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.DebugOptions;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.graph.NodeSourcePosition;
import jdk.compiler.graal.nodes.EncodedGraph;
import jdk.compiler.graal.nodes.GraphEncoder;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.StructuredGraph.AllowAssumptions;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.compiler.graal.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.compiler.graal.nodes.graphbuilderconf.IntrinsicContext;
import jdk.compiler.graal.nodes.graphbuilderconf.InvocationPlugin;
import jdk.compiler.graal.nodes.graphbuilderconf.InvocationPlugins;
import jdk.compiler.graal.nodes.graphbuilderconf.ParameterPlugin;
import jdk.compiler.graal.nodes.java.MethodCallTargetNode;
import jdk.compiler.graal.nodes.spi.SnippetParameterInfo;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.util.Providers;
import jdk.compiler.graal.printer.GraalDebugHandlersFactory;
import jdk.compiler.graal.replacements.ConstantBindingParameterPlugin;
import jdk.compiler.graal.replacements.PEGraphDecoder;
import jdk.compiler.graal.replacements.ReplacementsImpl;
import jdk.compiler.graal.word.WordTypes;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.BeforeHeapLayoutAccess;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The replacements implementation for the compiler at runtime. All snippets and method
 * substitutions are pre-compiled at image generation (and not on demand when a snippet is
 * instantiated).
 */
public class SubstrateReplacements extends ReplacementsImpl {

    @Platforms(Platform.HOSTED_ONLY.class)
    public interface GraphMakerFactory {
        GraphMaker create(MetaAccessProvider metaAccess, ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected static class Builder {
        protected final GraphMakerFactory graphMakerFactory;
        protected final Map<ResolvedJavaMethod, StructuredGraph> graphs;
        protected final Deque<Runnable> deferred;
        protected final HashSet<ResolvedJavaMethod> registered;
        protected final Set<ResolvedJavaMethod> delayedInvocationPluginMethods;

        protected Builder(GraphMakerFactory graphMakerFactory) {
            this.graphMakerFactory = graphMakerFactory;
            this.graphs = new HashMap<>();
            this.deferred = new ArrayDeque<>();
            this.registered = new HashSet<>();
            this.delayedInvocationPluginMethods = new HashSet<>();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected class SnippetInlineInvokePlugin implements InlineInvokePlugin {

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            assert b.parsingIntrinsic();
            assert builder != null;
            Class<? extends GraphBuilderPlugin> intrinsifyingPlugin = getIntrinsifyingPlugin(method);
            if (intrinsifyingPlugin != null && GeneratedInvocationPlugin.class.isAssignableFrom(intrinsifyingPlugin)) {
                builder.delayedInvocationPluginMethods.add(method);
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, defaultBytecodeProvider);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)//
    private Builder builder;

    private InvocationPlugins snippetInvocationPlugins;
    private byte[] snippetEncoding;
    private Object[] snippetObjects;
    private NodeClass<?>[] snippetNodeClasses;
    private Map<ResolvedJavaMethod, Integer> snippetStartOffsets;
    private final WordTypes wordTypes;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateReplacements(Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target,
                    WordTypes wordTypes, GraphMakerFactory graphMakerFactory) {
        // Snippets cannot have optimistic assumptions.
        super(new GraalDebugHandlersFactory(snippetReflection), providers, snippetReflection, bytecodeProvider, target);
        this.wordTypes = wordTypes;
        this.builder = new Builder(graphMakerFactory);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerImmutableObjects(BeforeHeapLayoutAccess access) {
        access.registerAsImmutable(this);
        access.registerAsImmutable(snippetEncoding);
        access.registerAsImmutable(snippetObjects);
        access.registerAsImmutable(snippetNodeClasses);
        access.registerAsImmutable(snippetStartOffsets, SubstrateReplacements::isImmutable);
    }

    /**
     * Manual list of mutable classes that are reachable from object graphs that are manually marked
     * as immutable.
     */
    private static boolean isImmutable(Object o) {
        return !(o instanceof SubstrateForeignCallLinkage) && !(o instanceof SubstrateTargetDescription);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Collection<StructuredGraph> getSnippetGraphs(boolean trackNodeSourcePosition, OptionValues options) {
        List<StructuredGraph> result = new ArrayList<>(snippetStartOffsets.size());
        for (ResolvedJavaMethod method : snippetStartOffsets.keySet()) {
            result.add(getSnippet(method, null, null, null, trackNodeSourcePosition, null, options));
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public NodeClass<?>[] getSnippetNodeClasses() {
        return snippetNodeClasses;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Collection<ResolvedJavaMethod> getSnippetMethods() {
        return snippetStartOffsets.keySet();
    }

    @Override
    public void setGraphBuilderPlugins(Plugins plugins) {
        Plugins copy = new Plugins(plugins);
        copy.clearInlineInvokePlugins();
        for (InlineInvokePlugin plugin : plugins.getInlineInvokePlugins()) {
            copy.appendInlineInvokePlugin(plugin == this ? new SnippetInlineInvokePlugin() : plugin);
        }
        super.setGraphBuilderPlugins(copy);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options) {
        Integer startOffset = snippetStartOffsets.get(method);
        if (startOffset == null) {
            throw VMError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)"));
        }

        ParameterPlugin parameterPlugin = null;
        if (args != null) {
            parameterPlugin = new ConstantBindingParameterPlugin(args, providers.getMetaAccess(), snippetReflection);
        }

        OptionValues optionValues = new OptionValues(options, GraalOptions.TraceInlining, GraalOptions.TraceInliningForStubsAndSnippets.getValue(options),
                        DebugOptions.OptimizationLog, null);
        try (DebugContext debug = openSnippetDebugContext("SVMSnippet_", method, optionValues)) {
            StructuredGraph result = new StructuredGraph.Builder(optionValues, debug)
                            .method(method)
                            .trackNodeSourcePosition(trackNodeSourcePosition)
                            .recordInlinedMethods(false)
                            .setIsSubstitution(true)
                            .build();

            EncodedGraph encodedGraph = new EncodedGraph(snippetEncoding, startOffset, snippetObjects, snippetNodeClasses, result);
            PEGraphDecoder graphDecoder = new PEGraphDecoder(ConfigurationValues.getTarget().arch, result, providers, null, snippetInvocationPlugins, new InlineInvokePlugin[0], parameterPlugin, null,
                            null, null, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true, false) {

                private IntrinsicContext intrinsic = new IntrinsicContext(method, null, providers.getReplacements().getDefaultReplacementBytecodeProvider(), INLINE_AFTER_PARSING, false);

                @Override
                protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod lookupMethod, BytecodeProvider intrinsicBytecodeProvider) {
                    if (lookupMethod.equals(method)) {
                        assert !result.trackNodeSourcePosition() || encodedGraph.trackNodeSourcePosition();
                        return encodedGraph;
                    } else {
                        throw VMError.shouldNotReachHere(method.format("%H.%n(%p)"));
                    }
                }

                @Override
                public IntrinsicContext getIntrinsic() {
                    return intrinsic;
                }
            };

            graphDecoder.decode(method);

            assert result.verify();
            return result;
        }
    }

    /**
     * Compiles the snippet and stores the graph.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        assert AnnotationAccess.isAnnotationPresent(method, Snippet.class) : "Snippet must be annotated with @" + Snippet.class.getSimpleName() + " " + method;
        assert method.hasBytecodes() : "Snippet must not be abstract or native";
        assert builder.graphs.get(method) == null : "snippet registered twice: " + method.getName();
        assert builder.registered.add(method) : "snippet registered twice: " + method.getName();

        // Defer the processing until encodeSnippets
        Runnable run = new Runnable() {
            @Override
            public void run() {
                try (DebugContext debug = openSnippetDebugContext("Snippet_", method, options)) {
                    Object[] args = prepareConstantArguments(receiver);
                    StructuredGraph graph = makeGraph(debug, defaultBytecodeProvider, method, args, SnippetParameterInfo.getNonNullParameters(getSnippetParameterInfo(method)), null,
                                    trackNodeSourcePosition, null);

                    // Check if all methods which should be inlined are really inlined.
                    for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                        ResolvedJavaMethod callee = callTarget.targetMethod();
                        if (!builder.delayedInvocationPluginMethods.contains(callee)) {
                            throw shouldNotReachHere("method " + callee.format("%h.%n") + " not inlined in snippet " + method.format("%h.%n") + " (maybe not final?)");
                        }
                    }

                    builder.graphs.put(method, graph);
                }
            }
        };
        builder.deferred.add(run);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Set<ResolvedJavaMethod> getDelayedInvocationPluginMethods() {
        return builder.delayedInvocationPluginMethods;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void encodeSnippets() {
        GraphEncoder encoder = new GraphEncoder(ConfigurationValues.getTarget().arch);
        while (!builder.deferred.isEmpty()) {
            builder.deferred.pop().run();
        }
        for (StructuredGraph graph : builder.graphs.values()) {
            encoder.prepare(graph);
        }
        encoder.finishPrepare();

        snippetStartOffsets = new HashMap<>();
        for (Map.Entry<ResolvedJavaMethod, StructuredGraph> entry : builder.graphs.entrySet()) {
            snippetStartOffsets.put(entry.getKey(), encoder.encode(entry.getValue()));
        }
        snippetEncoding = encoder.getEncoding();
        snippetObjects = encoder.getObjects();
        snippetNodeClasses = encoder.getNodeClasses();

        snippetInvocationPlugins = makeInvocationPlugins(getGraphBuilderPlugins(), builder, Function.identity());

        /* Original graphs are no longer necessary, release memory. */
        builder.graphs.clear();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static InvocationPlugins makeInvocationPlugins(GraphBuilderConfiguration.Plugins plugins, Builder builder, Function<Object, Object> objectReplacer) {
        Map<ResolvedJavaMethod, InvocationPlugin> result = new HashMap<>(builder.delayedInvocationPluginMethods.size());
        for (ResolvedJavaMethod method : builder.delayedInvocationPluginMethods) {
            ResolvedJavaMethod replacedMethod = (ResolvedJavaMethod) objectReplacer.apply(method);
            InvocationPlugin plugin = plugins.getInvocationPlugins().lookupInvocation(replacedMethod, HostedOptionValues.singleton());
            assert plugin != null : "expected invocation plugin for " + replacedMethod;
            result.put(replacedMethod, plugin);
        }
        return new InvocationPlugins(result, null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void copyFrom(SubstrateReplacements copyFrom, Function<Object, Object> objectReplacer) {
        snippetInvocationPlugins = makeInvocationPlugins(getGraphBuilderPlugins(), copyFrom.builder, objectReplacer);

        snippetEncoding = Arrays.copyOf(copyFrom.snippetEncoding, copyFrom.snippetEncoding.length);
        snippetNodeClasses = Arrays.copyOf(copyFrom.snippetNodeClasses, copyFrom.snippetNodeClasses.length);
        snippetObjects = new Object[copyFrom.snippetObjects.length];
        for (int i = 0; i < snippetObjects.length; i++) {
            snippetObjects[i] = objectReplacer.apply(copyFrom.snippetObjects[i]);
        }
        snippetStartOffsets = new HashMap<>(copyFrom.snippetStartOffsets.size());
        for (Map.Entry<ResolvedJavaMethod, Integer> entry : copyFrom.snippetStartOffsets.entrySet()) {
            snippetStartOffsets.put((ResolvedJavaMethod) objectReplacer.apply(entry.getKey()), entry.getValue());
        }
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, OptionValues options) {
        // This override keeps graphBuilderPlugins from being reached during image generation.
        return false;
    }

    @Override
    public StructuredGraph getInlineSubstitution(ResolvedJavaMethod original, int invokeBci, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosiion,
                    AllowAssumptions allowAssumptions,
                    OptionValues options) {
        // This override keeps graphBuilderPlugins from being reached during image generation.
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    protected final GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
        return builder.graphMakerFactory.create(providers.getMetaAccess(), this, substitute, substitutedMethod);
    }

    private static Object[] prepareConstantArguments(Object receiver) {
        if (receiver != null) {
            return new Object[]{receiver};
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getInjectedArgument(Class<T> capability) {
        if (capability.isAssignableFrom(WordTypes.class)) {
            return (T) wordTypes;
        }
        return super.getInjectedArgument(capability);
    }

    @Override
    public Stamp getInjectedStamp(Class<?> type, boolean nonNull) {
        JavaKind kind = JavaKind.fromJavaClass(type);
        if (kind == JavaKind.Object) {
            ResolvedJavaType returnType = providers.getMetaAccess().lookupJavaType(type);
            if (wordTypes.isWord(returnType)) {
                return wordTypes.getWordStamp(returnType);
            } else {
                return StampFactory.object(TypeReference.createWithoutAssumptions(returnType), nonNull);
            }
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        if (method instanceof SharedMethod sharedMethod) {
            return sharedMethod.isSnippet();
        } else {
            return super.isSnippet(method);
        }
    }
}
