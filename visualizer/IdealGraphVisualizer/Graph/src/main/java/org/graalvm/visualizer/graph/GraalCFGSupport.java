/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.graph;

import static jdk.graal.compiler.graphio.parsing.model.InputEdge.SUCCESSOR_EDGE_TYPE;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_CLASS;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyValues.CLASS_ENDNODE;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Helpers for Graal control-flow edges as surfaced in the standard edge-coloring filter.
 */
public final class GraalCFGSupport {
    private static final String ASSOCIATION_EDGE_TYPE = "Association";
    private static final String CATEGORY_PROPERTY = "category";
    private static final String MERGE_CATEGORY = "merge";
    private static final String END_CATEGORY = "end";
    private static final String CONTROL_SPLIT_CATEGORY = "controlSplit";

    private GraalCFGSupport() {
    }

    /**
     * Returns the connection type to use for CFG navigation.
     * <p>
     * Most edges keep their declared type. The one Graal-specific normalization performed here is
     * that an {@code Association} edge from {@code End} to {@code Merge} is treated as a successor
     * edge so edge-walk navigation can follow the real CFG across end/merge boundaries.
     */
    public static String getEffectiveConnectionType(Connection connection) {
        String type = connection.getType();
        if (SUCCESSOR_EDGE_TYPE.equals(type)) {
            return SUCCESSOR_EDGE_TYPE;
        }
        if (ASSOCIATION_EDGE_TYPE.equals(type) && isEndToMergeAssociation(connection)) {
            return SUCCESSOR_EDGE_TYPE;
        }
        return type;
    }

    /**
     * Determines whether the given connection should participate in CFG navigation.
     */
    public static boolean isControlFlowConnection(Connection connection) {
        return SUCCESSOR_EDGE_TYPE.equals(getEffectiveConnectionType(connection));
    }

    /**
     * Finds the immediate control-flow successors of a figure.
     */
    public static Set<Figure> findControlFlowSuccessors(Figure figure) {
        Set<Figure> successors = new LinkedHashSet<>();
        for (OutputSlot outputSlot : figure.getOutputSlots()) {
            for (Connection connection : outputSlot.getConnections()) {
                if (isControlFlowConnection(connection)) {
                    successors.add(connection.getInputSlot().getFigure());
                }
            }
        }
        return successors;
    }

    /**
     * Finds the immediate control-flow predecessors of a figure.
     */
    public static Set<Figure> findControlFlowPredecessors(Figure figure) {
        Set<Figure> predecessors = new LinkedHashSet<>();
        for (InputSlot inputSlot : figure.getInputSlots()) {
            for (Connection connection : inputSlot.getConnections()) {
                if (isControlFlowConnection(connection)) {
                    predecessors.add(connection.getOutputSlot().getFigure());
                }
            }
        }
        return predecessors;
    }

    /**
     * Finds the union of the immediate control-flow successors of the given frontier.
     */
    public static Set<Figure> findControlFlowSuccessors(Collection<? extends Figure> figures) {
        Set<Figure> successors = new LinkedHashSet<>();
        for (Figure figure : figures) {
            successors.addAll(findControlFlowSuccessors(figure));
        }
        return successors;
    }

    /**
     * Finds the union of the immediate control-flow predecessors of the given frontier.
     */
    public static Set<Figure> findControlFlowPredecessors(Collection<? extends Figure> figures) {
        Set<Figure> predecessors = new LinkedHashSet<>();
        for (Figure figure : figures) {
            predecessors.addAll(findControlFlowPredecessors(figure));
        }
        return predecessors;
    }

    /**
     * Returns whether any figure in the collection participates in control flow at all.
     * <p>
     * This is used to decide whether edge-walk navigation should stay in CFG mode or fall back to
     * non-control-flow edges for figures that have no CFG neighborhood.
     */
    public static boolean hasAnyControlFlowConnections(Collection<? extends Figure> figures) {
        for (Figure figure : figures) {
            if (hasAnyControlFlowConnections(figure)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the union of outgoing non-control-flow neighbors for the given frontier.
     * <p>
     * This is only used as a fallback when the starting selection has no control-flow connections.
     */
    public static Set<Figure> findNonControlFlowSuccessors(Collection<? extends Figure> figures) {
        Set<Figure> successors = new LinkedHashSet<>();
        for (Figure figure : figures) {
            successors.addAll(findNonControlFlowSuccessors(figure));
        }
        return successors;
    }

    /**
     * Finds the union of incoming non-control-flow neighbors for the given frontier.
     * <p>
     * This is only used as a fallback when the starting selection has no control-flow connections.
     */
    public static Set<Figure> findNonControlFlowPredecessors(Collection<? extends Figure> figures) {
        Set<Figure> predecessors = new LinkedHashSet<>();
        for (Figure figure : figures) {
            predecessors.addAll(findNonControlFlowPredecessors(figure));
        }
        return predecessors;
    }

    /**
     * Normalizes a one-step CFG navigation result.
     * <p>
     * The normalization is frontier-aware: it uses both the current selection frontier and the raw
     * next-step targets to decide whether merge / control-split barriers are still blocked, whether
     * targets beyond those blocked barriers should be suppressed, and whether visible barriers
     * should collapse targets that lie behind them.
     *
     * @param frontier the currently selected figures from which navigation starts
     * @param targets the raw one-step navigation targets
     * @param downward {@code true} for successor/downward navigation, {@code false} for
     *            predecessor/upward navigation
     * @param keepBlockedBarriersVisible whether a blocked barrier should remain visible in the
     *            returned target set
     */
    public static Set<Figure> normalizeControlFlowTargets(Collection<? extends Figure> frontier,
                    Collection<? extends Figure> targets,
                    boolean downward,
                    boolean keepBlockedBarriersVisible) {
        Set<Figure> normalizedTargets = new LinkedHashSet<>(sortDistinctFigures(targets));
        List<Figure> blockedBarriers = sortDistinctFigures(findBlockedBarriers(frontier, downward));
        if (!blockedBarriers.isEmpty()) {
            normalizedTargets = suppressTargetsBeyondBarriers(normalizedTargets, blockedBarriers, downward);
            if (keepBlockedBarriersVisible) {
                normalizedTargets.addAll(blockedBarriers);
            }
        }
        return collapseTargetsBehindVisibleBarriers(normalizedTargets, downward);
    }

    /**
     * Finds barrier nodes in the current frontier that should still block navigation in the current
     * direction because another selected node can still reach the barrier's pending side.
     */
    private static Set<Figure> findBlockedBarriers(Collection<? extends Figure> frontier, boolean downward) {
        Set<Figure> blockedBarriers = new LinkedHashSet<>();
        for (Figure figure : sortDistinctFigures(frontier)) {
            if (isBarrierNode(figure, downward) && isBlockedBarrier(figure, frontier, downward)) {
                blockedBarriers.add(figure);
            }
        }
        return blockedBarriers;
    }

    /**
     * Returns whether the barrier should remain blocked for this step.
     */
    private static boolean isBlockedBarrier(Figure barrier, Collection<? extends Figure> frontier, boolean downward) {
        Set<Figure> pendingSide = findBarrierPendingSide(barrier, downward);
        if (pendingSide.isEmpty()) {
            return false;
        }
        for (Figure figure : frontier) {
            if (figure != barrier && canReachAnyPendingSide(figure, pendingSide, downward, barrier)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the side of the barrier that has not yet been fully crossed in the current
     * navigation direction.
     */
    private static Set<Figure> findBarrierPendingSide(Figure barrier, boolean downward) {
        return downward ? findControlFlowPredecessors(barrier) : findControlFlowSuccessors(barrier);
    }

    /**
     * Tests whether {@code start} can still reach any node on the barrier's pending side without
     * traversing the barrier itself.
     */
    private static boolean canReachAnyPendingSide(Figure start, Set<Figure> pendingSide, boolean downward, Figure blockedBarrier) {
        if (pendingSide.contains(start)) {
            return true;
        }
        Set<Figure> visited = new LinkedHashSet<>();
        Deque<Figure> work = new ArrayDeque<>();
        work.add(start);
        visited.add(start);
        while (!work.isEmpty()) {
            Figure current = work.removeFirst();
            Set<Figure> next = downward ? findControlFlowSuccessors(current) : findControlFlowPredecessors(current);
            for (Figure candidate : next) {
                if (candidate == blockedBarrier) {
                    continue;
                }
                if (pendingSide.contains(candidate)) {
                    return true;
                }
                if (visited.add(candidate)) {
                    work.addLast(candidate);
                }
            }
        }
        return false;
    }

    /**
     * Removes targets that lie strictly beyond blocked barriers in the current navigation
     * direction.
     */
    private static Set<Figure> suppressTargetsBeyondBarriers(Collection<? extends Figure> targets,
                    Collection<? extends Figure> blockedBarriers,
                    boolean downward) {
        Set<Figure> normalizedTargets = new LinkedHashSet<>(sortDistinctFigures(targets));
        for (Figure barrier : sortDistinctFigures(blockedBarriers)) {
            Set<Figure> reachable = collectReachableInDirection(barrier, downward);
            reachable.remove(barrier);
            normalizedTargets.removeIf(reachable::contains);
        }
        return normalizedTargets;
    }

    /**
     * When a barrier is itself visible in the normalized target set, removes additional targets
     * that lie behind that barrier so the next selection stays at the frontier.
     */
    private static Set<Figure> collapseTargetsBehindVisibleBarriers(Collection<? extends Figure> targets, boolean downward) {
        List<Figure> orderedTargets = sortDistinctFigures(targets);
        Set<Figure> normalizedTargets = new LinkedHashSet<>(orderedTargets);
        for (Figure barrier : orderedTargets) {
            if (!normalizedTargets.contains(barrier) || !isBarrierNode(barrier, downward)) {
                continue;
            }
            Set<Figure> reachable = collectReachableInDirection(barrier, downward);
            reachable.remove(barrier);
            normalizedTargets.removeIf(candidate -> candidate != barrier && reachable.contains(candidate));
        }
        return new LinkedHashSet<>(sortDistinctFigures(normalizedTargets));
    }

    /**
     * Returns a stable, duplicate-free ordering of figures by node id.
     */
    private static List<Figure> sortDistinctFigures(Collection<? extends Figure> figures) {
        List<Figure> orderedFigures = new ArrayList<>(new LinkedHashSet<>(figures));
        orderedFigures.sort(Comparator.comparingInt(Figure::getId));
        return orderedFigures;
    }

    /**
     * Returns whether the figure has any incoming or outgoing control-flow connection.
     */
    private static boolean hasAnyControlFlowConnections(Figure figure) {
        for (OutputSlot outputSlot : figure.getOutputSlots()) {
            for (Connection connection : outputSlot.getConnections()) {
                if (isControlFlowConnection(connection)) {
                    return true;
                }
            }
        }
        for (InputSlot inputSlot : figure.getInputSlots()) {
            for (Connection connection : inputSlot.getConnections()) {
                if (isControlFlowConnection(connection)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds the immediate outgoing non-control-flow neighbors of a figure.
     */
    private static Set<Figure> findNonControlFlowSuccessors(Figure figure) {
        Set<Figure> successors = new LinkedHashSet<>();
        for (OutputSlot outputSlot : figure.getOutputSlots()) {
            for (Connection connection : outputSlot.getConnections()) {
                if (!isControlFlowConnection(connection)) {
                    successors.add(connection.getInputSlot().getFigure());
                }
            }
        }
        return successors;
    }

    /**
     * Finds the immediate incoming non-control-flow neighbors of a figure.
     */
    private static Set<Figure> findNonControlFlowPredecessors(Figure figure) {
        Set<Figure> predecessors = new LinkedHashSet<>();
        for (InputSlot inputSlot : figure.getInputSlots()) {
            for (Connection connection : inputSlot.getConnections()) {
                if (!isControlFlowConnection(connection)) {
                    predecessors.add(connection.getOutputSlot().getFigure());
                }
            }
        }
        return predecessors;
    }

    /**
     * Collects the transitive control-flow region reachable from {@code start} in the requested
     * navigation direction, including {@code start} itself.
     */
    private static Set<Figure> collectReachableInDirection(Figure start, boolean downward) {
        Set<Figure> visited = new LinkedHashSet<>();
        Deque<Figure> work = new ArrayDeque<>();
        work.add(start);
        visited.add(start);
        while (!work.isEmpty()) {
            Figure current = work.removeFirst();
            Set<Figure> next = downward ? findControlFlowSuccessors(current) : findControlFlowPredecessors(current);
            for (Figure successor : next) {
                if (visited.add(successor)) {
                    work.addLast(successor);
                }
            }
        }
        return visited;
    }

    /**
     * Returns whether the figure should act as a normalization barrier in the requested navigation
     * direction.
     */
    private static boolean isBarrierNode(Figure figure, boolean downward) {
        return downward ? isMergeNode(figure) : isControlSplitNode(figure) || isMergeNode(figure);
    }

    /**
     * Identifies the Graal-specific {@code End -> Merge} association edge that should count as a
     * CFG successor edge.
     */
    private static boolean isEndToMergeAssociation(Connection connection) {
        Figure outputFigure = connection.getOutputSlot().getFigure();
        Figure inputFigure = connection.getInputSlot().getFigure();
        return isEndNode(outputFigure) && isMergeNode(inputFigure);
    }

    /**
     * Returns whether the figure represents an End node.
     */
    private static boolean isEndNode(Figure figure) {
        String category = figure.getProperties().get(CATEGORY_PROPERTY, String.class);
        if (END_CATEGORY.equals(category)) {
            return true;
        }
        return CLASS_ENDNODE.equals(figure.getProperties().get(PROPNAME_CLASS, String.class));
    }

    /**
     * Returns whether the figure represents a Merge node.
     */
    private static boolean isMergeNode(Figure figure) {
        String category = figure.getProperties().get(CATEGORY_PROPERTY, String.class);
        if (MERGE_CATEGORY.equals(category)) {
            return true;
        }
        String nodeClass = figure.getProperties().get(PROPNAME_CLASS, String.class);
        return nodeClass != null && nodeClass.contains("Merge");
    }

    /**
     * Returns whether the figure represents a control split.
     */
    private static boolean isControlSplitNode(Figure figure) {
        String category = figure.getProperties().get(CATEGORY_PROPERTY, String.class);
        return CONTROL_SPLIT_CATEGORY.equals(category);
    }
}
