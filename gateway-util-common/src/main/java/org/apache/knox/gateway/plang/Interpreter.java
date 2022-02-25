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

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Interpreter {
    private static final Logger LOG = LogManager.getLogger(Interpreter.class);
    private final Map<String, SpecialForm> specialForms = new HashMap<>();
    private final Map<String, Func> functions = new HashMap<>();
    private final Map<String, Object> constants = new HashMap<>();

    public interface Func {
        Object call(List<Object> parameters);
    }

    private interface SpecialForm {
        Object call(List<AbstractSyntaxTree> parameters);
    }

    public Interpreter() {
        specialForms.put("or", args -> {
            Arity.min(1).check("or", args);
            return args.stream().anyMatch(each -> (boolean)eval(each));
        });
        specialForms.put("and", args -> {
            Arity.min(1).check("and", args);
            return args.stream().allMatch(each -> (boolean)eval(each));
        });
        addFunction("not", Arity.UNARY, args -> !(boolean)args.get(0));
        addFunction("=", Arity.BINARY, args -> equalTo(args.get(0), args.get(1)));
        addFunction("!=", Arity.BINARY, args -> !equalTo(args.get(0), args.get(1)));
        addFunction("match", Arity.BINARY, args ->
            args.get(0) instanceof String
                ? Pattern.matches((String)args.get(1), (String)args.get(0))
                : ((List<String>)(args.get(0))).stream().anyMatch(each -> Pattern.matches((String)args.get(1), each))
        );
        addFunction("size", Arity.UNARY, args -> ((Collection<?>) args.get(0)).size());
        addFunction("empty", Arity.UNARY, args -> ((Collection<?>) args.get(0)).isEmpty());
        addFunction("username", Arity.UNARY, args -> constants.get("username").equals(args.get(0)));
        addFunction("member", Arity.UNARY, args -> ((List<String>)constants.get("groups")).contains((String)args.get(0)));
        addFunction("lowercase", Arity.UNARY, args -> ((String)args.get(0)).toLowerCase(Locale.getDefault()));
        addFunction("uppercase", Arity.UNARY, args -> ((String)args.get(0)).toUpperCase(Locale.getDefault()));
        addFunction("print", Arity.min(1), args -> { // for debugging
            args.forEach(arg -> LOG.info(arg == null ? "null" : arg.toString()));
            return false;
        });
        constants.put("true", true);
        constants.put("false", false);
    }

    private static boolean equalTo(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number)a).doubleValue(), ((Number)b).doubleValue()) == 0;
        } else {
            return a.equals(b);
        }
    }

    public void addConstant(String name, Object value) {
        constants.put(name, value);
    }

    public void addFunction(String name, Arity arity, Func func) {
        functions.put(name, parameters -> {
            arity.check(name, parameters);
            return func.call(parameters);
        });
    }

    public Object eval(AbstractSyntaxTree ast) {
        try {
            if (ast == null) {
                return null;
            } else if (ast.isAtom()) {
                return ast.isStr() ? ast.strValue() : ast.isNumber() ? ast.numValue() : lookupConstant(ast);
            } else if (ast.isFunction()) {
                SpecialForm specialForm = specialForms.get(ast.functionName());
                if (specialForm != null) {
                    return specialForm.call(ast.functionParameters());
                } else {
                    Func func = functions.get(ast.functionName());
                    if (func == null) {
                        throw new UndefinedSymbolException(ast.functionName(), "function");
                    }
                    return func.call(ast.functionParameters().stream().map(this::eval).collect(toList()));
                }
            } else {
                throw new InterpreterException("Unknown token: " + ast.token());
            }
        } catch (ClassCastException e) {
            throw new TypeException("Type error at: " + ast, e);
        }
    }

    private Object lookupConstant(AbstractSyntaxTree ast) {
        Object var = constants.get(ast.token());
        if (var == null) {
            throw new UndefinedSymbolException(ast.token(), "variable");
        }
        return var;
    }
}
