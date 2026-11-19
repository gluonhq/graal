/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.vmaccess;

import static com.oracle.truffle.espresso.vmaccess.EspressoExternalConstantReflectionProvider.safeGetClass;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Objects;

import org.graalvm.polyglot.Value;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * External-JVMCI {@link SnippetReflectionProvider} implementation backed by Espresso interop
 * helpers.
 */
final class EspressoExternalSnippetReflectionProvider implements SnippetReflectionProvider {
    private final EspressoExternalVMAccess access;

    EspressoExternalSnippetReflectionProvider(EspressoExternalVMAccess access) {
        this.access = access;
    }

    /**
     * Converts host objects to guest constants.
     * <p>
     * External JVMCI currently supports only primitive host arrays on this path. Non-array objects
     * are intentionally rejected.
     */
    @Override
    public JavaConstant forObject(Object object) {
        if (object == null) {
            return JavaConstant.NULL_POINTER;
        }
        Class<?> clazz = object.getClass();
        if (clazz.isArray() && clazz.getComponentType().isPrimitive()) {
            JavaKind componentKind = JavaKind.fromJavaClass(clazz.getComponentType());
            Value guestArray = access.invokeJVMCIHelper("toGuestPrimitiveArray", (int) componentKind.getTypeChar(), object);
            return new EspressoExternalObjectConstant(access, guestArray);
        }
        throw JVMCIError.shouldNotReachHere("Cannot create JavaConstant for external JVMCI: " + clazz + ". Only primitive arrays are supported.");
    }

    /**
     * Converts selected guest constants to host objects.
     * <p>
     * This conversion is intentionally narrow: strings and byte arrays are supported for
     * compatibility, while arbitrary guest objects are rejected.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant.isNull() || !constant.getJavaKind().isObject()) {
            return null;
        }
        if ((!(constant instanceof EspressoExternalObjectConstant objConstant))) {
            throw new IllegalArgumentException("Expected an espresso constant got a " + safeGetClass(constant));
        }
        Value value = objConstant.getValue();
        Value metaObject = value.getMetaObject();
        if (access.java_lang_String_class.equals(metaObject)) {
            if (!type.isAssignableFrom(String.class)) {
                return null;
            }
            return (T) value.asString();
        }
        if (access.byte_array_class.equals(metaObject)) {
            if (!type.isAssignableFrom(byte[].class)) {
                return null;
            }
            int size = Math.toIntExact(value.getArraySize());
            byte[] result = new byte[size];
            access.invokeJVMCIHelper("copyByteArray", value, result);
            return (T) result;
        }
        try {
            ResolvedJavaType guestType = access.getProviders().getMetaAccess().lookupJavaType(type);
            if (!guestType.isAssignableFrom(objConstant.getType())) {
                return null;
            }
        } catch (NoClassDefFoundError e) {
            // ignore
        }
        throw JVMCIError.shouldNotReachHere("Cannot extract object of type " + metaObject.getMetaQualifiedName() + " for external JVMCI");
    }

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Class<?> originalClass(ResolvedJavaType type) {
        Objects.requireNonNull(type);
        throw JVMCIError.shouldNotReachHere("Cannot extract class for external JVMCI (" + type + ")");
    }

    @Override
    public Executable originalMethod(ResolvedJavaMethod method) {
        Objects.requireNonNull(method);
        throw JVMCIError.shouldNotReachHere("Cannot extract method for external JVMCI (" + method + ")");
    }

    @Override
    public Field originalField(ResolvedJavaField field) {
        Objects.requireNonNull(field);
        throw JVMCIError.shouldNotReachHere("Cannot extract field for external JVMCI (" + field + ")");
    }
}
