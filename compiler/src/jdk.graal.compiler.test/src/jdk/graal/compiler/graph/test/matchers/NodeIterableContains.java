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
package jdk.graal.compiler.graph.test.matchers;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;

public class NodeIterableContains<T extends Node> extends TypeSafeDiagnosingMatcher<NodeIterable<T>> {
    private T node;

    public NodeIterableContains(T node) {
        this.node = node;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("is a NodeIterable containing ").appendValue(node);
    }

    public static <T extends Node> NodeIterableContains<T> contains(T node) {
        return new NodeIterableContains<>(node);
    }

    @Override
    protected boolean matchesSafely(NodeIterable<T> iterable, Description mismatchDescription) {
        mismatchDescription.appendText("is a NodeIterable that does not contain ").appendValue(node);
        return iterable.contains(node);
    }
}
