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
package com.oracle.svm.core.jdk.localization.substitutions;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "sun.util.cldr.CLDRLocaleProviderAdapter")
@SuppressWarnings("unused")
final class Target_sun_util_cldr_CLDRLocaleProviderAdapter {

    /*
     * This is a memoization cache for irregular CLDR parent locales. The source of truth lives in
     * CLDRBaseLocaleDataMetaInfo, so we can rebuild lazily at runtime. The JDK seeds ROOT, ENGLISH,
     * and US eagerly, so preserve that default behavior.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
    private static Map<Locale, Locale> parentLocalesMap = Util_sun_util_cldr_CLDRLocaleProviderAdapter.createParentLocalesMap();

    /*
     * This is a memoization cache for CLDR language aliases derived from baseMetaInfo. The JDK
     * starts with an empty ConcurrentHashMap and populates it lazily on demand.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
    private static Map<Locale, Locale> langAliasesCache = new ConcurrentHashMap<>();
}

final class Util_sun_util_cldr_CLDRLocaleProviderAdapter {

    private Util_sun_util_cldr_CLDRLocaleProviderAdapter() {
    }

    static Map<Locale, Locale> createParentLocalesMap() {
        Map<Locale, Locale> map = new ConcurrentHashMap<>();
        map.put(Locale.ROOT, Locale.ROOT);
        map.put(Locale.ENGLISH, Locale.ENGLISH);
        map.put(Locale.US, Locale.US);
        return map;
    }
}
