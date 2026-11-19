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
package org.graalvm.profdiff.command;

import org.graalvm.profdiff.args.ArgumentParser;
import org.graalvm.profdiff.args.StringArgument;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.parser.ExperimentParser;
import org.graalvm.profdiff.parser.ExperimentParserError;

/**
 * Compares optimization logs produced by replaying the same recording on two compiler revisions.
 * <p>
 * Compilation units are paired only when they have the same compilation ID, which makes this
 * command useful for isolating the impact of compiler changes on optimization decisions for the
 * same recorded compilations. Compilation fragments are not created, because replaying the same
 * recording yields exactly the same set of compilation units, whose inlining decisions are expected
 * to be similar or equivalent.
 */
public class CompareReplayedCommand implements Command {
    private final ArgumentParser argumentParser;

    private final StringArgument optimizationLogArgument1;

    private final StringArgument optimizationLogArgument2;

    public CompareReplayedCommand() {
        argumentParser = new ArgumentParser();
        optimizationLogArgument1 = argumentParser.addStringArgument(
                        "optimization_log_1", "directory with optimization logs of the first replayed run");
        optimizationLogArgument2 = argumentParser.addStringArgument(
                        "optimization_log_2", "directory with optimization logs of the second replayed run");
    }

    @Override
    public String getName() {
        return "compare-replayed";
    }

    @Override
    public String getDescription() {
        return "compare replay-compilation optimization logs by matching compilation IDs";
    }

    @Override
    public ArgumentParser getArgumentParser() {
        return argumentParser;
    }

    @Override
    public void invoke(Writer writer) throws ExperimentParserError {
        OptionValues optionValues = writer.getOptionValues().withCreateFragments(false);
        Writer compareWriter = Writer.standardOutput(optionValues);
        ExplanationWriter explanationWriter = new ExplanationWriter(compareWriter, false, false, true);
        explanationWriter.explain();

        compareWriter.writeln();
        Experiment experiment1 = ExperimentParser.parseOrPanic(ExperimentId.ONE, null, null, optimizationLogArgument1.getValue(), compareWriter);
        experiment1.writeExperimentSummary(compareWriter);

        compareWriter.writeln();
        Experiment experiment2 = ExperimentParser.parseOrPanic(ExperimentId.TWO, null, null, optimizationLogArgument2.getValue(), compareWriter);
        experiment2.writeExperimentSummary(compareWriter);

        compareWriter.writeln();
        ExperimentMatcher matcher = new ExperimentMatcher(compareWriter);
        matcher.matchCompilationUnitsById(new ExperimentPair(experiment1, experiment2));
    }
}
