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
package jdk.graal.compiler.truffle.hotspot;

import java.util.Map;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.truffle.host.HostInliningPhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HotSpotHostInliningPhase extends HostInliningPhase {

    public HotSpotHostInliningPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    public static void install(HighTier highTier, OptionValues options) {
        if (!Options.TruffleHostInlining.getValue(options)) {
            return;
        }
        HostInliningPhase phase = new HotSpotHostInliningPhase(CanonicalizerPhase.create());
        insertBeforeInlining(highTier, phase);
    }

    @Override
    protected boolean forceShallowInline(CallTree caller, ResolvedJavaMethod callee, InliningPhaseContext context) {
        if (super.forceShallowInline(caller, callee, context)) {
            return true;
        }

        /*
         * [GR-74052] Shallow inlining methods annotated with EarlyInline is a workaround on HotSpot
         * to mitigate performance penalties from outlined bytecode handlers. Remove this workaround
         * after [GR-72604] got resolved.
         */
        Map<ResolvedJavaType, AnnotationValue> declaredAnnotationValues = AnnotationValueSupport.getDeclaredAnnotationValues(callee);
        return context.isBytecodeSwitch && caller.isRoot() && declaredAnnotationValues.containsKey(context.env.types().CompilerDirectives_EarlyInline);
    }
}
