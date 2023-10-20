/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2019, Arm Limited. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import jdk.compiler.graal.core.phases.CommunityCompilerConfiguration;
import jdk.compiler.graal.core.phases.EconomyCompilerConfiguration;
import jdk.compiler.graal.phases.tiers.CompilerConfiguration;
import jdk.compiler.graal.phases.tiers.SuitesCreator;

import com.oracle.svm.core.SubstrateOptions;

public class SubstrateSuitesCreatorProvider {
    private final SuitesCreator suitesCreator;

    private final SuitesCreator firstTierSuitesCreator;

    protected static CompilerConfiguration getHostedCompilerConfiguration() {
        if (SubstrateOptions.useEconomyCompilerConfig()) {
            return new EconomyCompilerConfiguration();
        } else {
            return new CommunityCompilerConfiguration();
        }
    }

    protected SubstrateSuitesCreatorProvider(SuitesCreator suitesCreator, SuitesCreator firstTierSuitesCreator) {
        this.suitesCreator = suitesCreator;
        this.firstTierSuitesCreator = firstTierSuitesCreator;
    }

    public SubstrateSuitesCreatorProvider() {
        this(new SubstrateSuitesCreator(getHostedCompilerConfiguration()), new SubstrateSuitesCreator(new EconomyCompilerConfiguration()));
    }

    public final SuitesCreator getSuitesCreator() {
        return suitesCreator;
    }

    public final SuitesCreator getFirstTierSuitesCreator() {
        return firstTierSuitesCreator;
    }
}
