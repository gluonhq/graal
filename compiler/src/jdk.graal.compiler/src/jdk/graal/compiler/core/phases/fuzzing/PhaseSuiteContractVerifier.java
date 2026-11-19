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
package jdk.graal.compiler.core.phases.fuzzing;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Optional;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.BasePhase.NotApplicable;
import jdk.graal.compiler.phases.PhaseSuite;

/**
 * Verifies semantic properties of the contracts of phases in a {@link PhaseSuite}.
 *
 * The verifier walks the suite in the same order as {@link PhaseSuite#notApplicableTo(GraphState)}
 * and simulates graph-state changes between phases. This gives it the concrete phase instances and
 * graph states needed to catch contract mismatches in fuzzed phase suites.
 */
public final class PhaseSuiteContractVerifier {

    private static final String NOT_APPLICABLE_TO_NAME = "notApplicableTo";
    private static final MethodType NOT_APPLICABLE_TO_METHOD_TYPE = MethodType.methodType(Optional.class, GraphState.class);

    private PhaseSuiteContractVerifier() {
    }

    /**
     * Verifies semantic properties of the contracts of the phases in {@code phaseSuite}, then
     * returns {@link PhaseSuite#notApplicableTo(GraphState)} for the same suite and graph state.
     */
    public static <C> Optional<NotApplicable> verifiedNotApplicableTo(PhaseSuite<C> phaseSuite, GraphState graphState) {
        verify(phaseSuite, graphState);
        return phaseSuite.notApplicableTo(graphState);
    }

    private static <C> void verify(PhaseSuite<C> phaseSuite, GraphState graphState) {
        GraphState simulationGraphState = graphState.copy();
        for (BasePhase<? super C> phase : phaseSuite.getPhases()) {
            verifyPhase(phase, simulationGraphState);
            if (phase instanceof PhaseSuite) {
                verify((PhaseSuite<? super C>) phase, simulationGraphState);
            }
            try {
                updateGraphStateWithNestedPhases(phase, simulationGraphState);
            } catch (Throwable t) {
                return;
            }
        }
    }

    /**
     * Simulates a phase's graph-state effects while {@link #verify(PhaseSuite, GraphState)} walks a
     * phase suite. For nested suites, {@link BasePhase#updateGraphState(GraphState)} would only
     * apply the suite phase's own state effects. The verifier also needs the effects of the nested
     * suite's child phases, matching the state progression used by
     * {@link PhaseSuite#notApplicableTo(GraphState)}.
     */
    @SuppressWarnings("unchecked")
    private static <C> void updateGraphStateWithNestedPhases(BasePhase<? super C> phase, GraphState graphState) {
        if (phase instanceof PhaseSuite) {
            for (BasePhase<? super C> innerPhase : ((PhaseSuite<C>) phase).getPhases()) {
                updateGraphStateWithNestedPhases(innerPhase, graphState);
            }
        }
        phase.updateGraphState(graphState);
    }

    /**
     * Checks that an overriding {@code notApplicableTo} does not drop superclass constraints. This
     * permits subclasses to add constraints while preserving the constraints imposed by their
     * superclasses.
     */
    private static void verifyPhase(BasePhase<?> phase, GraphState graphState) {
        Class<?> phaseClass = phase.getClass();
        /*
         * Find the notApplicableTo implementation used by the phase and the nearest superclass
         * implementation that it overrides. There is nothing to check if the phase does not override
         * a concrete superclass contract.
         */
        Method notApplicableTo = findGraphStateMethod(phaseClass, NOT_APPLICABLE_TO_NAME);
        Class<?> notApplicableToDeclaringClass = notApplicableTo.getDeclaringClass();
        if (notApplicableToDeclaringClass.equals(BasePhase.class)) {
            return;
        }
        Method inheritedNotApplicableTo = findGraphStateMethod(notApplicableToDeclaringClass.getSuperclass(), NOT_APPLICABLE_TO_NAME);
        Class<?> inheritedNotApplicableToDeclaringClass = inheritedNotApplicableTo.getDeclaringClass();
        if (inheritedNotApplicableToDeclaringClass.equals(BasePhase.class)) {
            return;
        }

        /*
         * Evaluate the actual contract and the overridden superclass contract on the same graph
         * state. The superclass method must be called without virtual lookup.
         */
        Optional<NotApplicable> actual = phase.notApplicableTo(graphState.copy());
        Optional<NotApplicable> inherited = invokeSuperclassNotApplicableTo(phase, inheritedNotApplicableToDeclaringClass, graphState.copy());

        /*
         * Allow the subclass to add constraints, but reject it if it permits a state rejected by the
         * superclass contract.
         */
        if (actual.isEmpty() && inherited.isPresent()) {
            String message = String.format("%s overrides %s from %s in %s. The override says the phase is applicable while the superclass contract says: %s",
                            phase.getClass().getName(),
                            NOT_APPLICABLE_TO_NAME,
                            inheritedNotApplicableToDeclaringClass.getName(),
                            notApplicableToDeclaringClass.getName(),
                            inherited.get());
            throw GraalError.shouldNotReachHere(message);
        }
    }

    /**
     * Finds the most specific {@code name(GraphState)} method for {@code type}.
     */
    private static Method findGraphStateMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name, GraphState.class);
        } catch (NoSuchMethodException e) {
            throw GraalError.shouldNotReachHere(e, "Cannot find method " + type.getName() + "." + name + "(GraphState)");
        }
    }

    /**
     * Calls {@code declaringClass.notApplicableTo} on {@code phase}, bypassing virtual dispatch.
     */
    @SuppressWarnings("unchecked")
    private static Optional<NotApplicable> invokeSuperclassNotApplicableTo(BasePhase<?> phase, Class<?> declaringClass, GraphState graphState) {
        try {
            ensurePhaseModuleReadable(phase.getClass());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(phase.getClass(), MethodHandles.lookup());
            MethodHandle handle = lookup.findSpecial(declaringClass, NOT_APPLICABLE_TO_NAME, NOT_APPLICABLE_TO_METHOD_TYPE, phase.getClass());
            return (Optional<NotApplicable>) handle.bindTo(phase).invoke(graphState);
        } catch (Throwable t) {
            throw new GraalError(t, "Cannot invoke inherited %s.%s on %s", declaringClass.getName(), NOT_APPLICABLE_TO_NAME, phase.getClass().getName());
        }
    }

    /**
     * {@link MethodHandles#privateLookupIn} creates a lookup object for {@code phaseClass}. The JVM
     * requires the verifier's module to read the phase class's module before creating that lookup.
     * Fuzzed suites can contain phases from modules not statically read by
     * {@code jdk.graal.compiler}, so mark the phase module as readable first. This does not open
     * packages or bypass member access checks.
     */
    private static void ensurePhaseModuleReadable(Class<?> phaseClass) {
        Module verifierModule = PhaseSuiteContractVerifier.class.getModule();
        Module phaseModule = phaseClass.getModule();
        if (!verifierModule.canRead(phaseModule)) {
            verifierModule.addReads(phaseModule);
        }
    }
}
