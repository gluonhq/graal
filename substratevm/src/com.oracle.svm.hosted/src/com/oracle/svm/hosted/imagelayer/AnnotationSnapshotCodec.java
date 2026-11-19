/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationElementData;
import com.oracle.svm.hosted.snapshot.util.SnapshotAdapters;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.annotation.AnnotationValueType;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.ErrorElement;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

final class AnnotationSnapshotCodec {
    private AnnotationSnapshotCodec() {
    }

    static AnnotationValue[] readAnnotations(SnapshotStructList.Loader<PersistedAnnotationData.Loader> reader,
                    Function<String, ResolvedJavaType> typeLookup, Function<String, Class<?>> classLookup) {
        return SnapshotAdapters.toArray(reader, annotation -> readAnnotation(annotation, typeLookup, classLookup), AnnotationValue[]::new);
    }

    private static AnnotationValue readAnnotation(PersistedAnnotationData.Loader annotation, Function<String, ResolvedJavaType> typeLookup,
                    Function<String, Class<?>> classLookup) {
        String typeName = annotation.getTypeName();
        ResolvedJavaType annotationType = typeLookup.apply(typeName);
        Map<String, Object> annotationValuesMap = new HashMap<>();
        annotation.getValues().forEach(value -> annotationValuesMap.put(value.getName(), readAnnotationValue(value, typeLookup, classLookup)));
        return new AnnotationValue(annotationType, annotationValuesMap);
    }

    private static Object readAnnotationValue(PersistedAnnotationElementData.Loader value, Function<String, ResolvedJavaType> typeLookup,
                    Function<String, Class<?>> classLookup) {
        return switch (value.kind()) {
            case STRING -> value.getString();
            case ENUM -> new EnumElement(typeLookup.apply(value.getEnum().getClassName()), value.getEnum().getName());
            case PRIMITIVE -> {
                var primitive = value.getPrimitive();
                long rawValue = primitive.getRawValue();
                char typeChar = (char) primitive.getTypeChar();
                yield switch (JavaKind.fromPrimitiveOrVoidTypeChar(typeChar)) {
                    case Boolean -> rawValue != 0;
                    case Byte -> (byte) rawValue;
                    case Char -> (char) rawValue;
                    case Short -> (short) rawValue;
                    case Int -> (int) rawValue;
                    case Long -> rawValue;
                    case Float -> Float.intBitsToFloat((int) rawValue);
                    case Double -> Double.longBitsToDouble(rawValue);
                    default -> throw AnalysisError.shouldNotReachHere("Unknown annotation value type: " + typeChar);
                };
            }
            case PRIMITIVE_ARRAY -> value.getPrimitiveArray().toArray();
            case CLASS_NAME -> typeLookup.apply(value.getClassName());
            case ANNOTATION -> readAnnotation(value.getAnnotation(), typeLookup, classLookup);
            case MEMBERS -> {
                var members = value.getMembers();
                var memberValues = members.getMemberValues();
                Class<?> membersClass = classLookup.apply(members.getClassName());
                var array = Array.newInstance(membersClass, memberValues.size());
                for (int i = 0; i < memberValues.size(); ++i) {
                    Array.set(array, i, readAnnotationValue(memberValues.get(i), typeLookup, classLookup));
                }
                yield array;
            }
            case NOT_IN_SCHEMA -> throw AnalysisError.shouldNotReachHere("Unknown annotation value kind: " + value.kind());
        };
    }

    static void writeAnnotations(Annotated annotated, IntFunction<SnapshotStructList.Writer<PersistedAnnotationData.Writer>> builder) {
        Map<ResolvedJavaType, AnnotationValue> annotationValues = AnnotationValueSupport.getDeclaredAnnotationValues(annotated);
        var list = builder.apply(annotationValues.size());
        int i = 0;
        for (var entry : annotationValues.entrySet()) {
            ResolvedJavaType annotationClass = entry.getKey();
            PersistedAnnotationData.Writer annotationBuilder = list.get(i++);
            annotationBuilder.setTypeName(annotationClass.toJavaName());
            writeAnnotationElements(entry.getValue(), annotationBuilder::initValues);
        }
    }

    private static void writeAnnotationElements(AnnotationValue annotation,
                    IntFunction<SnapshotStructList.Writer<PersistedAnnotationElementData.Writer>> builder) {
        EconomicMap<String, Object> persistedMembers = EconomicMap.create();
        Map<String, Object> members = annotation.getElements();
        Map<String, ResolvedJavaType> memberTypes = AnnotationValueType.getInstance(annotation.getAnnotationType()).memberTypes();
        if (!members.isEmpty()) {
            for (var entry : members.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof ErrorElement) {
                    /* GR-70978 will add support for error elements */
                    return;
                }
                persistedMembers.put(entry.getKey(), value);
            }
        }
        if (!persistedMembers.isEmpty()) {
            var list = builder.apply(persistedMembers.size());
            MapCursor<String, Object> cursor = persistedMembers.getEntries();
            for (int i = 0; cursor.advance(); i++) {
                var elementBuilder = list.get(i);
                String name = cursor.getKey();
                elementBuilder.setName(name);
                Object value = cursor.getValue();
                writeAnnotationElement(value, memberTypes.get(name), elementBuilder);
            }
        }
    }

    private static void writeAnnotationElement(Object value, ResolvedJavaType memberType, PersistedAnnotationElementData.Writer builder) {
        if (memberType.isArray()) {
            ResolvedJavaType componentType = memberType.getComponentType();
            if (!componentType.isPrimitive()) {
                List<?> list = (List<?>) value;
                var members = builder.initMembers();
                members.setClassName(componentType.toJavaName());
                var memberValues = members.initMemberValues(list.size());
                int i = 0;
                for (Object entry : list) {
                    writeAnnotationElement(entry, componentType, memberValues.get(i++));
                }
            } else {
                SnapshotPrimitiveArrays.write(builder.initPrimitiveArray(), componentType.getJavaKind(), value);
            }
        } else {
            switch (value) {
                case Boolean z -> setAnnotationPrimitiveValue(builder, JavaKind.Boolean, z ? 1L : 0L);
                case Byte z -> setAnnotationPrimitiveValue(builder, JavaKind.Byte, z);
                case Short s -> setAnnotationPrimitiveValue(builder, JavaKind.Short, s);
                case Character c -> setAnnotationPrimitiveValue(builder, JavaKind.Char, c);
                case Integer i -> setAnnotationPrimitiveValue(builder, JavaKind.Int, i);
                case Float f -> setAnnotationPrimitiveValue(builder, JavaKind.Float, Float.floatToRawIntBits(f));
                case Long j -> setAnnotationPrimitiveValue(builder, JavaKind.Long, j);
                case Double d -> setAnnotationPrimitiveValue(builder, JavaKind.Double, Double.doubleToRawLongBits(d));
                case ResolvedJavaType type -> builder.setClassName(type.toJavaName());
                case AnnotationValue innerAnnotation -> writeAnnotationElements(innerAnnotation, builder.initAnnotation()::initValues);
                case String s -> builder.setString(s);
                case EnumElement e -> {
                    var enumBuilder = builder.initEnum();
                    enumBuilder.setClassName(e.enumType.toJavaName());
                    enumBuilder.setName(e.name);
                }
                default -> throw AnalysisError.shouldNotReachHere("Unsupported value for annotation element: " + value + ", " + value.getClass());
            }
        }
    }

    private static void setAnnotationPrimitiveValue(PersistedAnnotationElementData.Writer builder, JavaKind kind, long rawValue) {
        var primitiveValue = builder.initPrimitive();
        primitiveValue.setTypeChar(NumUtil.safeToUByte(kind.getTypeChar()));
        primitiveValue.setRawValue(rawValue);
    }
}
