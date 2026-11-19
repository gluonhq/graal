/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueNodeInterface;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Represents a conversion from one value domain into another.
 */
public interface ConvertNode extends ValueNodeInterface {

    /** Returns the value to be converted. */
    ValueNode getValue();

    /**
     * Applies this node's conversion to a constant from the input domain of {@link #getValue()}.
     */
    Constant convert(Constant c, ConstantReflectionProvider constantReflection);

    /**
     * Attempts to map a constant from this node's output domain back into the input domain of
     * {@link #getValue()}. May return {@code null} when no suitable constant is known.
     */
    Constant reverse(Constant c, ConstantReflectionProvider constantReflection);

    /**
     * Checks whether a null check may skip the conversion. This is true only if the conversion does
     * not impact the result of the null check, i.e., isNull(input) === isNull(convert(input)).
     */
    boolean mayNullCheckSkipConversion();

    /**
     * Check whether a conversion is lossless.
     *
     * @return true iff reverse(convert(c)) == c for all c
     */
    boolean isLossless();

    /**
     * Check whether a conversion preserves comparison order.
     *
     * @param op a comparison operator
     * @return true iff (c1 op c2) == (convert(c1) op convert(c2)) for all c1, c2
     */
    default boolean preservesOrder(CanonicalCondition op) {
        return isLossless();
    }

    /**
     * Check whether a conversion preserves comparison order against a particular constant value.
     *
     * @param op a comparison operator
     * @param value
     * @param constantReflection
     * @return true iff (c1 op value) == (convert(c1) op convert(value)) for value and all c1
     */
    default boolean preservesOrder(CanonicalCondition op, Constant value, ConstantReflectionProvider constantReflection) {
        return preservesOrder(op);
    }
}
