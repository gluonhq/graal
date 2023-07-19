/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.types;

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.Objects;

/**
 * Response to 'setVariable' request.
 */
public class SetVariableResponse extends Response {

    SetVariableResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return new ResponseBody(jsonData.getJSONObject("body"));
    }

    public SetVariableResponse setBody(ResponseBody body) {
        jsonData.put("body", body.jsonData);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        SetVariableResponse other = (SetVariableResponse) obj;
        if (!Objects.equals(this.getBody(), other.getBody())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (this.getRequestSeq() != other.getRequestSeq()) {
            return false;
        }
        if (this.isSuccess() != other.isSuccess()) {
            return false;
        }
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (this.getSeq() != other.getSeq()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + Objects.hashCode(this.getBody());
        hash = 37 * hash + Objects.hashCode(this.getType());
        hash = 37 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 37 * hash + Boolean.hashCode(this.isSuccess());
        hash = 37 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 37 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 37 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static SetVariableResponse create(ResponseBody body, Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("body", body.jsonData);
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new SetVariableResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The new value of the variable.
         */
        public String getValue() {
            return jsonData.getString("value");
        }

        public ResponseBody setValue(String value) {
            jsonData.put("value", value);
            return this;
        }

        /**
         * The type of the new value. Typically shown in the UI when hovering over the value.
         */
        public String getType() {
            return jsonData.optString("type", null);
        }

        public ResponseBody setType(String type) {
            jsonData.putOpt("type", type);
            return this;
        }

        /**
         * If variablesReference is > 0, the new value is structured and its children can be
         * retrieved by passing variablesReference to the VariablesRequest. The value should be less
         * than or equal to 2147483647 (2^31 - 1).
         */
        public Integer getVariablesReference() {
            return jsonData.has("variablesReference") ? jsonData.getInt("variablesReference") : null;
        }

        public ResponseBody setVariablesReference(Integer variablesReference) {
            jsonData.putOpt("variablesReference", variablesReference);
            return this;
        }

        /**
         * The number of named child variables. The client can use this optional information to
         * present the variables in a paged UI and fetch them in chunks. The value should be less
         * than or equal to 2147483647 (2^31 - 1).
         */
        public Integer getNamedVariables() {
            return jsonData.has("namedVariables") ? jsonData.getInt("namedVariables") : null;
        }

        public ResponseBody setNamedVariables(Integer namedVariables) {
            jsonData.putOpt("namedVariables", namedVariables);
            return this;
        }

        /**
         * The number of indexed child variables. The client can use this optional information to
         * present the variables in a paged UI and fetch them in chunks. The value should be less
         * than or equal to 2147483647 (2^31 - 1).
         */
        public Integer getIndexedVariables() {
            return jsonData.has("indexedVariables") ? jsonData.getInt("indexedVariables") : null;
        }

        public ResponseBody setIndexedVariables(Integer indexedVariables) {
            jsonData.putOpt("indexedVariables", indexedVariables);
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            ResponseBody other = (ResponseBody) obj;
            if (!Objects.equals(this.getValue(), other.getValue())) {
                return false;
            }
            if (!Objects.equals(this.getType(), other.getType())) {
                return false;
            }
            if (!Objects.equals(this.getVariablesReference(), other.getVariablesReference())) {
                return false;
            }
            if (!Objects.equals(this.getNamedVariables(), other.getNamedVariables())) {
                return false;
            }
            if (!Objects.equals(this.getIndexedVariables(), other.getIndexedVariables())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.getValue());
            if (this.getType() != null) {
                hash = 59 * hash + Objects.hashCode(this.getType());
            }
            if (this.getVariablesReference() != null) {
                hash = 59 * hash + Integer.hashCode(this.getVariablesReference());
            }
            if (this.getNamedVariables() != null) {
                hash = 59 * hash + Integer.hashCode(this.getNamedVariables());
            }
            if (this.getIndexedVariables() != null) {
                hash = 59 * hash + Integer.hashCode(this.getIndexedVariables());
            }
            return hash;
        }

        public static ResponseBody create(String value) {
            final JSONObject json = new JSONObject();
            json.put("value", value);
            return new ResponseBody(json);
        }
    }
}
