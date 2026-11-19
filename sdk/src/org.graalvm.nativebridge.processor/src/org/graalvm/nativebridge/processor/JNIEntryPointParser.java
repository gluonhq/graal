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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

final class JNIEntryPointParser extends AbstractBridgeParser {

    static final String JNI_ENTRY_POINT_ANNOTATION = "org.graalvm.jniutils.JNIEntryPoint";

    private JNIEntryPointParser(NativeBridgeProcessor processor, TypeCache typeCache) {
        super(processor, typeCache, typeCache.jniEntryPoint);
    }

    @Override
    DefinitionData parseElement(Element element) {
        ExecutableElement method = (ExecutableElement) element;
        TypeElement enclosingElement = (TypeElement) method.getEnclosingElement();
        return new JNIEntryPointDefinitionData((DeclaredType) enclosingElement.asType());
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new JNIEntryPointGenerator(this, definitionData, getTypeCache());
    }

    @Override
    TypeCache getTypeCache() {
        return (TypeCache) super.getTypeCache();
    }

    @Override
    protected void checkAnnotatedType(Element annotatedElement) {
        if (annotatedElement.getKind() != ElementKind.METHOD && annotatedElement.getKind() != ElementKind.CONSTRUCTOR) {
            AnnotationMirror annotation = processor.getAnnotation(annotatedElement, handledAnnotationType);
            emitError(annotatedElement, annotation, "JNIEntryPoint annotation can be used only on methods or constructors. " +
                            "To resolve this, remove the annotation.");
        }
    }

    static JNIEntryPointParser create(NativeBridgeProcessor processor) {
        return new JNIEntryPointParser(processor, new TypeCache(processor));
    }

    static final class TypeCache extends BaseTypeCache {

        final DeclaredType jniEntryPoint;
        final DeclaredType jniEntryPointProvider;

        TypeCache(NativeBridgeProcessor processor) {
            super(processor);
            this.jniEntryPoint = processor.getDeclaredType(JNI_ENTRY_POINT_ANNOTATION);
            this.jniEntryPointProvider = processor.getDeclaredType("org.graalvm.jniutils.JNIEntryPointProvider");
        }
    }

    static final class JNIEntryPointDefinitionData extends DefinitionData {

        boolean excluded;

        JNIEntryPointDefinitionData(DeclaredType enclosingElement) {
            super(enclosingElement, MarshallerData.NO_MARSHALLER);
        }
    }
}
