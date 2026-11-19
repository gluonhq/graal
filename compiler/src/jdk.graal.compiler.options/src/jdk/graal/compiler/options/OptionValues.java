/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.options;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;

/**
 * A context for obtaining values for {@link OptionKey}s.
 */
public final class OptionValues {

    private final UnmodifiableEconomicMap<OptionKey<?>, Object> values;

    public boolean containsKey(OptionKey<?> key) {
        return values.containsKey(key);
    }

    /**
     * Please use method {@link #derive(UnmodifiableEconomicMap)} instead.
     */
    public OptionValues(OptionValues initialValues, UnmodifiableEconomicMap<OptionKey<?>, Object> extraPairs) {
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        if (initialValues != null) {
            initMap(map, initialValues.getMap());
        }
        initMap(map, extraPairs);
        this.values = map;
    }

    /**
     * Please use method {@link #derive(OptionKey, Object)} instead.
     */
    public OptionValues(OptionValues initialValues, OptionKey<?> key1, Object value1, Object... extraPairs) {
        this(initialValues, asMap(key1, value1, extraPairs));
    }

    /**
     * Creates a new map suitable for using {@link OptionKey}s as keys.
     */
    public static EconomicMap<OptionKey<?>, Object> newOptionMap() {
        return EconomicMap.create(Equivalence.IDENTITY);
    }

    /**
     * Gets an immutable view of the key/value pairs in this object.
     */
    public UnmodifiableEconomicMap<OptionKey<?>, Object> getMap() {
        return values;
    }

    /**
     * @param key1 first key in map
     * @param value1 first value in map
     * @param extraPairs key/value pairs of the form {@code [key1, value1, key2, value2, ...]}
     * @return a map containing the key/value pairs as entries
     */
    public static EconomicMap<OptionKey<?>, Object> asMap(OptionKey<?> key1, Object value1, Object... extraPairs) {
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        map.put(key1, value1);
        for (int i = 0; i < extraPairs.length; i += 2) {
            OptionKey<?> key = (OptionKey<?>) extraPairs[i];
            Object value = extraPairs[i + 1];
            map.put(key, value);
        }
        return map;
    }

    public OptionValues(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        initMap(map, values);
        this.values = map;
    }

    private static void initMap(EconomicMap<OptionKey<?>, Object> map, UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        UnmodifiableMapCursor<OptionKey<?>, Object> cursor = values.getEntries();
        while (cursor.advance()) {
            cursor.getKey().notifySet();
            map.put(cursor.getKey(), cursor.getValue());
        }
    }

    @Override
    public String toString() {
        return toString(getMap());
    }

    public static String toString(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        Comparator<OptionKey<?>> comparator = Comparator.comparing(OptionKey::getName);
        SortedMap<OptionKey<?>, Object> sorted = new TreeMap<>(comparator);
        UnmodifiableMapCursor<OptionKey<?>, Object> cursor = values.getEntries();
        while (cursor.advance()) {
            sorted.put(cursor.getKey(), cursor.getValue());
        }
        return sorted.toString();
    }

    private static final int PROPERTY_LINE_WIDTH = 80;
    private static final int PROPERTY_HELP_INDENT = 10;

    /**
     * Wraps some given text to one or more lines of a given maximum width.
     *
     * @param text text to wrap
     * @param width maximum width of an output line, exception for words in {@code text} longer than
     *            this value
     * @return {@code text} broken into lines
     */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text.length() > width) {
            String[] chunks = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String chunk : chunks) {
                if (line.length() + chunk.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(chunk);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        } else {
            lines.add(text);
        }
        return lines;
    }

    /**
     * Prints a help message to {@code out} describing the options available via {@code loader}. The
     * key/value for each option is separated by {@code :=} if the option has an entry in this
     * object otherwise {@code =} is used as the separator.
     *
     * @param all if true, all options are printed otherwise only {@linkplain #excludeOptionFromHelp
     *            non-excluded} options are printed.
     */
    public void printHelp(Iterable<OptionDescriptors> loader, PrintStream out, String namePrefix, boolean all) {
        SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
        for (OptionDescriptors opts : loader) {
            for (OptionDescriptor desc : opts) {
                String name = desc.getName();
                OptionDescriptor existing = sortedOptions.put(name, desc);
                assert existing == null || existing == desc : "Option named \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + desc.getLocation();
            }
        }
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            String key = e.getKey();
            OptionDescriptor desc = e.getValue();
            if (all || !excludeOptionFromHelp(desc)) {
                String edition = String.format("[%s edition]", OptionsParser.isEnterpriseOption(desc) ? "enterprise" : "community");
                printHelp(out, namePrefix,
                                key,
                                desc.getOptionKey().getValue(this),
                                desc.getOptionValueType(),
                                containsKey(desc.getOptionKey()) ? ":=" : "=",
                                edition,
                                desc.getHelp());
            }
        }
    }

    private static Object quoteNonNullString(Class<?> valueType, Object value) {
        if (valueType == String.class && value != null) {
            return '"' + String.valueOf(value) + '"';
        }
        return value;
    }

    public static void printHelp(PrintStream out, String namePrefix,
                    String key,
                    Object value,
                    Class<?> valueType,
                    String assign,
                    String edition,
                    List<String> help) {
        String name = namePrefix + key;
        String linePrefix = String.format("%s %s %s %s", name, assign, quoteNonNullString(valueType, value), edition);

        String typeName = valueType.isEnum() ? "String" : valueType.getSimpleName();
        int typeStartPos = PROPERTY_LINE_WIDTH - typeName.length();
        int linePad = typeStartPos - linePrefix.length();
        if (linePad > 0) {
            out.printf("%s%-" + linePad + "s[%s]%n", linePrefix, "", typeName);
        } else {
            out.printf("%s[%s]%n", linePrefix, typeName);
        }

        List<String> helpLines = help;
        if (!helpLines.isEmpty()) {
            String first = helpLines.getFirst();
            if (first.isEmpty()) {
                helpLines = List.of();
            } else {
                List<String> brief = wrap(first, PROPERTY_LINE_WIDTH - PROPERTY_HELP_INDENT);
                if (brief.size() > 1) {
                    brief.addAll(helpLines.subList(1, helpLines.size()));
                    helpLines = brief;
                } else {
                    OptionDescriptor.guarantee(brief.size() == 1 && brief.getFirst().equals(first), "%s", brief);
                }
            }
        }

        for (String line : helpLines) {
            out.printf("%" + PROPERTY_HELP_INDENT + "s%s%n", "", line);
        }
        // print new line after each option
        out.printf("%n");
    }

    private static boolean excludeOptionFromHelp(OptionDescriptor desc) {
        /* Filter out debug options. */
        return desc.getOptionType() == OptionType.Debug;
    }

    /**
     * Derives new option values where the respective keys are set to the respective values. The
     * values are set also if they would be the default value for their respective key.
     */
    public OptionValues derive(UnmodifiableEconomicMap<OptionKey<?>, Object> changedValues) {
        return changedValues.isEmpty() ? this : new OptionValues(this, changedValues);
    }

    /**
     * Derives new option values where the given key is set to the given value.
     * <p>
     * The key is omitted if its current effective value already equals {@code value}. For keys with
     * a computed {@link OptionKey#getValue(OptionValues)} implementation, this means that the
     * derived options preserve the current "unset" state instead of forcing an explicit setting.
     * Callers that need to materialize several interacting option updates at once should use
     * {@link #derive(UnmodifiableEconomicMap)} or chain calls in the desired order.
     */
    public OptionValues derive(OptionKey<?> key, Object value) {
        if (Objects.equals(key.getValue(this), value)) {
            return this;
        }
        return new OptionValues(this, key, value);
    }
}
