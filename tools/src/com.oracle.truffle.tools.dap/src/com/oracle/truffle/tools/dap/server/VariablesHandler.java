/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.dap.types.SetVariableArguments;
import com.oracle.truffle.tools.dap.types.Variable;
import com.oracle.truffle.tools.dap.types.VariablesArguments;

public final class VariablesHandler {

    private static final String FILTER_NAMED = "named";
    private static final String FILTER_INDEXED = "indexed";

    private final ExecutionContext context;

    public VariablesHandler(ExecutionContext context) {
        this.context = context;
    }

    public List<Variable> getVariables(ThreadsHandler.SuspendedThreadInfo info, VariablesArguments args) {
        List<Variable> vars = new ArrayList<>();
        String filter = args.getFilter();
        boolean includeNamed = includeNamedVariables(filter);
        boolean includeIndexed = includeIndexedVariables(filter);
        DebugScope dScope;
        int id = args.getVariablesReference();
        StackFramesHandler.ScopeWrapper scopeWrapper = info.getById(StackFramesHandler.ScopeWrapper.class, id);
        if (scopeWrapper != null) {
            dScope = scopeWrapper.getScope();
            if (includeNamed && scopeWrapper.getReturnValue() != null) {
                vars.add(createVariable(info, scopeWrapper.getReturnValue(), "Return value"));
            }
            if (includeNamed && scopeWrapper.getThisValue() != null) {
                vars.add(createVariable(info, scopeWrapper.getThisValue(), scopeWrapper.getThisValue().getName()));
            }
        } else {
            dScope = info.getById(DebugScope.class, id);
        }
        if (dScope != null) {
            if (includeNamed) {
                for (DebugValue val : dScope.getDeclaredValues()) {
                    if (context.isInspectInternal() || !val.isInternal()) {
                        vars.add(createVariable(info, val, "Unnamed value"));
                    }
                }
            }
        } else {
            DebugValue dValue = info.getById(DebugValue.class, id);
            if (dValue != null) {
                if (includeIndexed && dValue.isArray()) {
                    for (DebugValue val : dValue.getArray()) {
                        if (context.isInspectInternal() || !val.isInternal()) {
                            vars.add(createVariable(info, val, "Unnamed value"));
                        }
                    }
                }
                if (includeNamed) {
                    Collection<DebugValue> properties = dValue.getProperties();
                    if (properties != null) {
                        for (DebugValue val : properties) {
                            if ((context.isInspectInternal() || !val.isInternal()) && (!dValue.isArray() || !isArrayIndexPropertyName(val.getName()))) {
                                vars.add(createVariable(info, val, "Unnamed value"));
                            }
                        }
                    }
                }
            }
        }
        Integer startArg = args.getStart();
        int start = startArg != null ? startArg : 0;
        if (start <= 0) {
            start = 0;
        }
        if (start >= vars.size()) {
            return Collections.emptyList();
        }
        Integer countArg = args.getCount();
        if (countArg == null || countArg <= 0) {
            return start == 0 ? vars : vars.subList(start, vars.size());
        }
        int end = Math.min(vars.size(), start + countArg);
        return start == 0 && end == vars.size() ? vars : vars.subList(start, end);
    }

    public static Variable setVariable(ThreadsHandler.SuspendedThreadInfo info, SetVariableArguments args) throws DebugException {
        DebugValue value = null;
        int id = args.getVariablesReference();
        String name = args.getName();
        StackFramesHandler.ScopeWrapper scopeWrapper = info.getById(StackFramesHandler.ScopeWrapper.class, id);
        DebugStackFrame frame;
        boolean updateReturnValue = false;
        LanguageInfo language = null;
        if (scopeWrapper != null) {
            frame = scopeWrapper.getFrame();
            value = scopeWrapper.getScope().getDeclaredValue(name);
            if (value == null) {
                if ("Return value".equals(name)) {
                    value = scopeWrapper.getReturnValue();
                    updateReturnValue = true;
                    language = frame.getLanguage();
                }
            }
        } else {
            frame = info.getSuspendedEvent().getTopStackFrame();
            DebugScope dScope = info.getById(DebugScope.class, id);
            if (dScope != null) {
                value = dScope.getDeclaredValue(name);
            } else {
                DebugValue dValue = info.getById(DebugValue.class, id);
                if (dValue != null) {
                    value = dValue.getProperty(name);
                    if (value == null && dValue.isArray()) {
                        try {
                            value = dValue.getArray().get(Integer.parseInt(name));
                        } catch (NumberFormatException ex) {
                        }
                    }
                }
            }
        }
        if (value != null && (updateReturnValue || value.isWritable())) {
            DebugValue newValue;
            DebugException dex = null;
            try {
                newValue = getDebugValue(frame, args.getValue());
            } catch (DebugException ex) {
                newValue = null;
                dex = ex;
            }
            if (newValue == null || !newValue.isReadable()) {
                Object newValueObject = getValue(args.getValue());
                if (newValueObject != null) {
                    newValue = value.getSession().createPrimitiveValue(newValueObject, language);
                }
            }
            if (newValue != null && newValue.isReadable()) {
                if (updateReturnValue) {
                    info.getSuspendedEvent().setReturnValue(newValue);
                } else {
                    value.set(newValue);
                    newValue = value;
                }
                return createVariable(info, newValue, "");
            } else {
                throw dex;
            }
        }
        return null;
    }

    static Variable createVariable(ThreadsHandler.SuspendedThreadInfo info, DebugValue val, String defaultName) throws DebugException {
        Collection<DebugValue> properties = val.getProperties();
        boolean isArray = val.isArray();
        int namedVariables = countNamedVariables(properties, isArray);
        int valId = (isArray && !val.getArray().isEmpty()) || namedVariables > 0 ? info.getId(val) : 0;
        Variable var = Variable.create(val.getName() != null ? val.getName() : defaultName,
                        val.isReadable() ? val.toDisplayString() : "<not readable>",
                        valId);
        DebugValue metaObject = val.getMetaObject();
        if (metaObject != null) {
            var.setType(metaObject.getMetaSimpleName());
        }
        if (isArray) {
            var.setIndexedVariables(val.getArray().size());
        }
        if (namedVariables > 0) {
            var.setNamedVariables(namedVariables);
        }
        return var;
    }

    private static boolean includeNamedVariables(String filter) {
        return filter == null || FILTER_NAMED.equals(filter);
    }

    private static boolean includeIndexedVariables(String filter) {
        return filter == null || FILTER_INDEXED.equals(filter);
    }

    private static int countNamedVariables(Collection<DebugValue> properties, boolean array) throws DebugException {
        if (properties == null || properties.isEmpty()) {
            return 0;
        }
        if (!array) {
            return properties.size();
        }
        int count = 0;
        for (DebugValue property : properties) {
            if (!isArrayIndexPropertyName(property.getName())) {
                count++;
            }
        }
        return count;
    }

    private static boolean isArrayIndexPropertyName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        long index = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            index = index * 10 + (c - '0');
            if (index >= 0xffff_ffffL) {
                return false;
            }
        }
        return Long.toString(index).equals(name);
    }

    static DebugValue getDebugValue(DebugStackFrame frame, String value) throws DebugException {
        DebugException dex;
        try {
            return frame.eval(value);
        } catch (DebugException de) {
            dex = de;
        }
        DebugValue receiver = frame.getScope().getReceiver();
        if (receiver != null && value.equals(receiver.getName())) {
            return receiver;
        }
        DebugScope scope = frame.getScope();
        while (scope != null) {
            DebugValue debugValue = scope.getDeclaredValue(value);
            if (debugValue != null) {
                return debugValue;
            }
            scope = scope.getParent();
        }
        throw dex;
    }

    private static Object getValue(String value) {
        String trimmedValue = value.trim();
        if (trimmedValue.length() > 1 && trimmedValue.charAt(0) == '"' && trimmedValue.charAt(trimmedValue.length() - 1) == '"') {
            return trimmedValue.substring(1, trimmedValue.length() - 1);
        }
        if (trimmedValue.equalsIgnoreCase("true") || trimmedValue.equalsIgnoreCase("false")) {
            return Boolean.valueOf(trimmedValue);
        }
        try {
            return Long.valueOf(trimmedValue);
        } catch (NumberFormatException nfe) {
        }
        return null;
    }
}
