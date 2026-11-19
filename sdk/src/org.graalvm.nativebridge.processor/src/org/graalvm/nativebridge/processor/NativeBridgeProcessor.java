/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.BaseTypeCache;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractFactoryParser.FactoryDefinitionData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.ServiceDefinitionData;

@SupportedAnnotationTypes({
                HotSpotToNativeServiceParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION,
                HotSpotToNativeFactoryParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION,
                NativeToHotSpotServiceParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION,
                NativeToNativeServiceParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION,
                NativeToNativeFactoryParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION,
                ProcessToProcessServiceParser.GENERATE_FOREIGN_PROCESS_ANNOTATION,
                ProcessToProcessFactoryParser.GENERATE_FOREIGN_PROCESS_ANNOTATION,
                JNIEntryPointParser.JNI_ENTRY_POINT_ANNOTATION
})
public final class NativeBridgeProcessor extends AbstractProcessor {

    private static final Map<String, Function<NativeBridgeProcessor, AbstractBridgeParser>> PARSERS = createParsers();

    private static Map<String, Function<NativeBridgeProcessor, AbstractBridgeParser>> createParsers() {
        Map<String, Function<NativeBridgeProcessor, AbstractBridgeParser>> result = new LinkedHashMap<>();
        result.put(HotSpotToNativeFactoryParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION, HotSpotToNativeFactoryParser::create);
        result.put(NativeToNativeFactoryParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION, NativeToNativeFactoryParser::create);
        result.put(ProcessToProcessFactoryParser.GENERATE_FOREIGN_PROCESS_ANNOTATION, ProcessToProcessFactoryParser::create);
        result.put(HotSpotToNativeServiceParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION, HotSpotToNativeServiceParser::create);
        result.put(NativeToNativeServiceParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION, NativeToNativeServiceParser::create);
        result.put(ProcessToProcessServiceParser.GENERATE_FOREIGN_PROCESS_ANNOTATION, ProcessToProcessServiceParser::create);
        result.put(NativeToHotSpotServiceParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION, NativeToHotSpotServiceParser::create);
        result.put(JNIEntryPointParser.JNI_ENTRY_POINT_ANNOTATION, JNIEntryPointParser::create);
        return Collections.unmodifiableMap(result);
    }

    private CurrentCompilationUnit currentCompilationUnit;
    private final Map<Element, Set<DSLError>> emittedErrors = new HashMap<>();

    public NativeBridgeProcessor() {
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, List<Entry<AbstractBridgeParser, DefinitionData>>> parsed = new HashMap<>();
        for (Entry<String, Function<NativeBridgeProcessor, AbstractBridgeParser>> e : PARSERS.entrySet()) {
            TypeElement annotation = getOptionalTypeElement(e.getKey());
            if (annotation == null) {
                continue;
            }
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            if (!annotatedElements.isEmpty()) {
                AbstractBridgeParser parser = e.getValue().apply(this);
                parse(parser, annotatedElements, parsed);
            }
        }
        verifyCrossParserContracts(parsed);
        Map<TypeElement, List<AbstractBridgeGenerator>> toGenerate = new HashMap<>();
        for (Entry<TypeElement, List<Entry<AbstractBridgeParser, DefinitionData>>> ee : parsed.entrySet()) {
            List<Entry<AbstractBridgeParser, DefinitionData>> definitions = ee.getValue();
            if (!definitions.isEmpty()) {
                List<AbstractBridgeGenerator> generators = new ArrayList<>();
                toGenerate.put(ee.getKey(), generators);
                for (Entry<AbstractBridgeParser, DefinitionData> pe : definitions) {
                    generators.add(pe.getKey().createGenerator(pe.getValue()));
                }
            }
        }
        for (Iterator<Entry<TypeElement, List<AbstractBridgeGenerator>>> it = toGenerate.entrySet().iterator(); it.hasNext();) {
            Entry<TypeElement, List<AbstractBridgeGenerator>> entry = it.next();
            if (hasErrors(entry.getKey())) {
                it.remove();
            } else {
                ExpectError.assertNoErrorExpected(entry.getValue());
            }
        }
        emittedErrors.clear();
        for (List<AbstractBridgeGenerator> generatorsForElement : toGenerate.values()) {
            for (AbstractBridgeGenerator generator : generatorsForElement) {
                List<DefinitionData> otherDefinitions = generatorsForElement.stream().filter((g) -> g != generator).map((g) -> g.getDefinition()).collect(Collectors.toList());
                if (!otherDefinitions.isEmpty()) {
                    generator.configureMultipleDefinitions(otherDefinitions);
                }
            }
        }
        for (Entry<TypeElement, List<AbstractBridgeGenerator>> e : toGenerate.entrySet()) {
            TypeElement annotatedElement = e.getKey();
            List<AbstractBridgeGenerator> generators = e.getValue();
            AbstractBridgeGenerator firstGenerator = generators.get(0);
            BaseTypeCache typeCache = firstGenerator.getTypeCache();
            CompilationUnitFactory f = CompilationUnitFactory.select(generators);
            try (CompilationUnit cu = f.createCompilationUnit(this, annotatedElement)) {
                for (AbstractBridgeGenerator generator : generators) {
                    generator.generateFields(cu.codeBuilder, cu.simpleName);
                }
                for (AbstractBridgeGenerator generator : generators) {
                    generator.generateAPI(cu.codeBuilder, cu.simpleName);
                }
                if (hasCustomDispatch(firstGenerator)) {
                    generateSharedCustomDispatchFactory(cu.codeBuilder, (NativeBridgeTypeCache) typeCache, generators);
                } else if (isService(firstGenerator)) {
                    generateSharedFactory(cu.codeBuilder, (NativeBridgeTypeCache) typeCache, generators);
                }
                for (AbstractBridgeGenerator generator : generators) {
                    generator.generateImpl(cu.codeBuilder, cu.simpleName);
                }
            } catch (IOException ioe) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, ioe.getMessage(), annotatedElement);
            }
        }
        return true;
    }

    private TypeElement getOptionalTypeElement(String className) {
        try {
            return getTypeElement(className);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    void emitError(Element element, AnnotationMirror mirror, String format, Object... params) {
        AbstractBridgeParser parser = currentCompilationUnit.parser();
        Element annotatedElement = currentCompilationUnit.annotatedElement();
        String msg = String.format(format, params);
        Set<DSLError> errorsInElement = emittedErrors.computeIfAbsent(annotatedElement, (e) -> new HashSet<>());
        if (errorsInElement.add(new DSLError(element, msg)) && !ExpectError.isExpectedError(parser, element, msg)) {
            env().getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element, mirror);
        }
    }

    boolean hasErrors(TypeElement annotatedElement) {
        return emittedErrors.get(annotatedElement) != null;
    }

    private void parse(AbstractBridgeParser parser, Set<? extends Element> annotatedElements, Map<TypeElement, List<Entry<AbstractBridgeParser, DefinitionData>>> into) {
        try {
            for (Element element : annotatedElements) {
                currentCompilationUnit = new CurrentCompilationUnit(parser, element);
                DefinitionData data = parser.parse(element);
                if (data == null) {
                    // Parsing error
                    continue;
                }
                into.computeIfAbsent(data.annotatedElement, (k) -> new ArrayList<>()).add(new AbstractMap.SimpleImmutableEntry<>(parser, data));
            }
        } finally {
            currentCompilationUnit = null;
        }
    }

    private void verifyCrossParserContracts(Map<TypeElement, List<Entry<AbstractBridgeParser, DefinitionData>>> parsed) {
        for (Entry<TypeElement, List<Entry<AbstractBridgeParser, DefinitionData>>> definitionsOnElement : parsed.entrySet()) {
            List<Entry<AbstractBridgeParser, DefinitionData>> factoryDefinitions = definitionsOnElement.getValue().stream().//
                            filter((dd) -> dd.getValue() instanceof FactoryDefinitionData).//
                            collect(Collectors.toCollection(LinkedList::new));
            if (factoryDefinitions.size() > 1) {
                Entry<AbstractBridgeParser, DefinitionData> first = factoryDefinitions.removeFirst();
                currentCompilationUnit = new CurrentCompilationUnit(first.getKey(), definitionsOnElement.getKey());
                DeclaredType firstInitialService = ((FactoryDefinitionData) first.getValue()).initialService;
                try {
                    for (Entry<AbstractBridgeParser, DefinitionData> other : factoryDefinitions) {
                        if (!typeUtils().isSameType(firstInitialService, ((FactoryDefinitionData) other.getValue()).initialService)) {
                            emitError(definitionsOnElement.getKey(), null, "All generate factory annotations on a single type must have the same `initialService` value.%n" +
                                            "To fix this, ensure `initialService` has a consistent value across all annotations.");
                        }
                    }
                } finally {
                    currentCompilationUnit = null;
                }
            }
            // Do not generate service if its factory definition is broken
            List<Entry<AbstractBridgeParser, DefinitionData>> serviceDefinitions = definitionsOnElement.getValue().stream().//
                            filter((dd) -> dd.getValue() instanceof ServiceDefinitionData).//
                            collect(Collectors.toCollection(LinkedList::new));
            for (Entry<AbstractBridgeParser, DefinitionData> serviceDefinition : serviceDefinitions) {
                if (hasErrors((TypeElement) ((ServiceDefinitionData) serviceDefinition.getValue()).factory.asElement())) {
                    definitionsOnElement.getValue().remove(serviceDefinition);
                }
            }
        }
    }

    private static boolean hasCustomDispatch(AbstractBridgeGenerator generator) {
        DefinitionData definitionData = generator.getDefinition();
        if (definitionData instanceof ServiceDefinitionData serviceDefinitionData) {
            return serviceDefinitionData.hasCustomDispatch();
        }
        return false;
    }

    private static boolean isService(AbstractBridgeGenerator generator) {
        DefinitionData definitionData = generator.getDefinition();
        return definitionData instanceof ServiceDefinitionData;
    }

    private void generateSharedCustomDispatchFactory(CodeBuilder builder, NativeBridgeTypeCache typeCache, List<AbstractBridgeGenerator> generators) {
        List<AbstractServiceGenerator> generatorsWithCommonFactory = new ArrayList<>();
        for (AbstractBridgeGenerator generator : generators) {
            AbstractServiceGenerator serviceGenerator = (AbstractServiceGenerator) generator;
            if (serviceGenerator.supportsCommonFactory()) {
                generatorsWithCommonFactory.add(serviceGenerator);
            }
        }
        if (!generatorsWithCommonFactory.isEmpty()) {
            builder.lineEnd("");
            Types types = processingEnv.getTypeUtils();
            List<CodeBuilder.Parameter> parameters = new ArrayList<>();
            ServiceDefinitionData primaryDefinition = generatorsWithCommonFactory.getFirst().getDefinition();
            for (VariableElement variable : primaryDefinition.annotatedTypeConstructorParams) {
                parameters.add(CodeBuilder.newParameter(variable.asType(), variable.getSimpleName()));
            }
            DeclaredType peerType = types.getDeclaredType((TypeElement) typeCache.clazz.asElement(), types.getWildcardType(typeCache.peer, null));
            CharSequence peerTypeParameter = "peerType";
            parameters.add(CodeBuilder.newParameter(peerType, peerTypeParameter));
            builder.methodStart(EnumSet.of(Modifier.STATIC), AbstractNativeServiceGenerator.FACTORY_METHOD_NAME, primaryDefinition.annotatedType, parameters, Collections.emptyList());
            builder.indent();
            boolean first = true;
            for (AbstractServiceGenerator generator : generatorsWithCommonFactory) {
                generateIsolateTypeBranch(builder, generator, peerTypeParameter, first);
                first = false;
            }
            generateDefaultBranch(builder, peerTypeParameter, typeCache);
            builder.dedent();
            builder.line("}");
        }
    }

    private static void generateSharedFactory(CodeBuilder builder, NativeBridgeTypeCache typeCache, List<AbstractBridgeGenerator> generators) {
        List<AbstractServiceGenerator> generatorsWithCommonFactory = new ArrayList<>();
        for (AbstractBridgeGenerator generator : generators) {
            AbstractServiceGenerator serviceGenerator = (AbstractServiceGenerator) generator;
            if (serviceGenerator.supportsCommonFactory()) {
                generatorsWithCommonFactory.add(serviceGenerator);
            }
        }
        if (!generatorsWithCommonFactory.isEmpty()) {
            builder.lineEnd("");
            List<CodeBuilder.Parameter> formalParameters = new ArrayList<>();
            List<CharSequence> annotatedTypeConstructorParameters = new ArrayList<>();
            ServiceDefinitionData primaryDefinition = generatorsWithCommonFactory.getFirst().getDefinition();
            for (VariableElement variable : primaryDefinition.annotatedTypeConstructorParams) {
                formalParameters.add(CodeBuilder.newParameter(variable.asType(), variable.getSimpleName()));
                annotatedTypeConstructorParameters.add(variable.getSimpleName());
            }
            CharSequence peerParameter = "peer";
            formalParameters.add(CodeBuilder.newParameter(typeCache.peer, peerParameter));
            builder.methodStart(EnumSet.of(Modifier.STATIC), AbstractNativeServiceGenerator.FACTORY_METHOD_NAME, primaryDefinition.annotatedType, formalParameters, Collections.emptyList());
            builder.indent();
            boolean first = true;
            for (AbstractServiceGenerator generator : generatorsWithCommonFactory) {
                if (first) {
                    builder.lineStart();
                } else {
                    builder.write(" else ");
                }
                CharSequence castedPeerType = Utilities.javaMemberName(Utilities.getTypeName(generator.getDefinition().peerType));
                builder.write("if (").write(peerParameter).write(" instanceof ").write(generator.getDefinition().peerType).space().write(castedPeerType).lineEnd(") {");
                builder.indent();
                List<CharSequence> invokeParameters = new ArrayList<>(annotatedTypeConstructorParameters);
                invokeParameters.add(castedPeerType);
                generator.generateCommonFactoryReturn(builder, invokeParameters);
                builder.dedent();
                builder.lineStart("}");
                first = false;
            }
            generateDefaultBranch(builder, peerParameter, typeCache);
            builder.dedent();
            builder.line("}");
        }
    }

    private static void generateIsolateTypeBranch(CodeBuilder builder, AbstractServiceGenerator generator, CharSequence peerTypeParameter, boolean first) {
        if (first) {
            builder.lineStart();
        } else {
            builder.write(" else ");
        }
        builder.write("if (").write(peerTypeParameter).write(" == ").classLiteral(generator.getDefinition().peerType).lineEnd(") {");
        builder.indent();
        generator.generateCommonCustomDispatchFactoryReturn(builder);
        builder.dedent();
        builder.lineStart("}");
    }

    private static void generateDefaultBranch(CodeBuilder builder, CharSequence typeParameter, BaseTypeCache typeCache) {
        builder.lineEnd(" else {");
        builder.indent();
        CharSequence message = new CodeBuilder(builder).invokeStatic(typeCache.string, "format",
                        "\"Unsupported peer type `%s`.\"", typeParameter).build();
        builder.lineStart("throw ").newInstance(typeCache.illegalArgumentException, message).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    record CurrentCompilationUnit(AbstractBridgeParser parser, Element annotatedElement) {
    }

    record DSLError(Element element, String errorMessage) {
    }

    static final class CompilationUnit implements Closeable {

        private final NativeBridgeProcessor processor;
        private final TypeElement annotatedElement;
        final CodeBuilder codeBuilder;
        final CharSequence simpleName;

        CompilationUnit(NativeBridgeProcessor processor, CodeBuilder builder, TypeElement annotatedElement, CharSequence simpleName) {
            this.processor = processor;
            this.codeBuilder = builder;
            this.annotatedElement = annotatedElement;
            this.simpleName = simpleName;
        }

        @Override
        public void close() throws IOException {
            codeBuilder.dedent();
            codeBuilder.line("}");  // Enclosing class end
            writeSourceFile(codeBuilder.build());
        }

        private void writeSourceFile(String content) throws IOException {
            String sourceFileFQN = String.format("%s.%s",
                            Utilities.getEnclosingPackageElement(annotatedElement).getQualifiedName(), simpleName);
            JavaFileObject sourceFile = processor.env().getFiler().createSourceFile(sourceFileFQN, annotatedElement);
            try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
                out.print(content);
            }
        }
    }

    interface CompilationUnitFactory {

        CompilationUnit createCompilationUnit(NativeBridgeProcessor processor, TypeElement annotatedElement);

        static CompilationUnitFactory select(List<AbstractBridgeGenerator> generators) {
            CompilationUnitFactory factory = generators.get(0).getCompilationUnitFactory();
            for (int i = 1; i < generators.size(); i++) {
                if (generators.get(i).getCompilationUnitFactory().getClass() != factory.getClass()) {
                    throw new AssertionError("Generators sharing the same annotated element must use the same compilation unit factory.");
                }
            }
            return factory;
        }
    }
}
