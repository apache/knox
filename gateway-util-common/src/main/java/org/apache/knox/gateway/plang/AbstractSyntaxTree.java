/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.plang;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract Syntax Tree of the code.
 *
 * For example from the following code:
 *      (or true (and false true))
 *
 * The parser generates the following AST:
 *      [or, true, [and, false, true]]
 */
public class AbstractSyntaxTree {
    private final List<AbstractSyntaxTree> children = new ArrayList<>();
    private final String token;

    public AbstractSyntaxTree(String token) {
        this.token = token;
    }

    public void addChild(AbstractSyntaxTree child) {
        children.add(child);
    }

    @Override
    public String toString() {
        return isAtom() ? token : children.toString();
    }

    public String token() {
        return token;
    }

    public boolean isStr() {
        return token != null && token.startsWith("'") && token.endsWith("'");
    }

    public boolean isNumber() {
        boolean result = false;
        if (token != null) {
            try {
                numValue();
                result = true;
            } catch (NumberFormatException e) {
                // NaN
            }
        }
        return result;
    }

    public Number numValue() {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            return Double.parseDouble(token);
        }
    }

    public String strValue() {
        if (!isStr()) {
            throw new InterpreterException("Token: " + token + " is not a String");
        }
        return token.substring(1, token.length() -1);
    }

    public boolean isAtom() {
        return !"(".equals(token);
    }

    public boolean isFunction() {
        return !children.isEmpty();
    }

    public String functionName() {
        return children.get(0).token();
    }

    public List<AbstractSyntaxTree> functionParameters() {
        return children.subList(1, children.size());
    }
}
