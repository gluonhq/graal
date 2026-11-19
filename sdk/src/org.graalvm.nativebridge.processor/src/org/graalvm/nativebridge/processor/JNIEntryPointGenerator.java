/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.JNIEntryPointParser.JNIEntryPointDefinitionData;
import org.graalvm.nativebridge.processor.JNIEntryPointParser.TypeCache;
import org.graalvm.nativebridge.processor.NativeBridgeProcessor.CompilationUnit;
import org.graalvm.nativebridge.processor.NativeBridgeProcessor.CompilationUnitFactory;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.EnumSet;
import java.util.List;

final class JNIEntryPointGenerator extends AbstractBridgeGenerator {

    JNIEntryPointGenerator(AbstractBridgeParser parser, DefinitionData definition, TypeCache typeCache) {
        super(parser, definition, typeCache);
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
    }

    @Override
    TypeCache getTypeCache() {
        return (TypeCache) super.getTypeCache();
    }

    @Override
    JNIEntryPointDefinitionData getDefinition() {
        return (JNIEntryPointDefinitionData) super.getDefinition();
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        JNIEntryPointDefinitionData definition = getDefinition();
        if (definition.excluded) {
            return;
        }
        CharSequence annotatedTypeBinaryName = getParser().processor.env().getElementUtils().getBinaryName(definition.annotatedElement);
        builder.lineStart().annotation(getTypeCache().override, null).lineEnd("");
        builder.methodStart(EnumSet.of(Modifier.PUBLIC, Modifier.FINAL), "getClassName", getTypeCache().string, List.of(), List.of());
        builder.indent();
        builder.lineStart("return ").stringLiteral(annotatedTypeBinaryName).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    @Override
    void configureMultipleDefinitions(List<DefinitionData> otherDefinitions) {
        if (!getDefinition().excluded) {
            for (DefinitionData otherDefinition : otherDefinitions) {
                if (otherDefinition instanceof JNIEntryPointDefinitionData jniEntryPointDefinitionData) {
                    jniEntryPointDefinitionData.excluded = true;
                }
            }
        }
    }

    @Override
    CompilationUnitFactory getCompilationUnitFactory() {
        return new CompilationUnitFactory() {
            @Override
            public NativeBridgeProcessor.CompilationUnit createCompilationUnit(NativeBridgeProcessor processor, TypeElement annotatedElement) {
                PackageElement owner = Utilities.getEnclosingPackageElement(annotatedElement);
                CodeBuilder builder = new CodeBuilder(owner, processor.typeUtils(), getTypeCache());
                String name = "";
                for (Element current = annotatedElement; current.getKind().isClass() || current.getKind().isInterface(); current = current.getEnclosingElement()) {
                    String simpleName = current.getSimpleName().toString();
                    if (name.isEmpty()) {
                        name = simpleName;
                    } else {
                        name = simpleName + name;
                    }
                }
                name = name + "JNIProvider";
                builder.classStart(EnumSet.of(Modifier.PUBLIC, Modifier.FINAL), name, null, List.of(getTypeCache().jniEntryPointProvider));
                builder.indent();
                String service = Utilities.getQualifiedName(getTypeCache().jniEntryPointProvider);
                /*
                 * Write a per-provider registration file under META-INF/jni-entry-points/ instead
                 * of directly into META-INF/services/. Each file is named after the provider class
                 * and contains the service interface it implements.
                 *
                 * During archiving, NativeBridgeArchiveParticipant (mx_sdk.py) collects all files
                 * from META-INF/jni-entry-points/ and merges them into the canonical
                 * META-INF/services/ entries.
                 *
                 * The indirection is required for correctness under incremental compilation.
                 */
                processor.createRegistrationFile("jni-entry-points", owner.getQualifiedName() + "." + name, service, annotatedElement);
                return new CompilationUnit(processor, builder, annotatedElement, name);
            }
        };
    }
}
