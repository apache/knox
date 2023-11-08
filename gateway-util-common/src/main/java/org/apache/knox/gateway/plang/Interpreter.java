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
import java.util.stream.Collectors;

import org.apache.knox.gateway.identityasserter.regex.filter.RegexTemplate;
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
        addSpecialForm(Arity.min(1), "or", args -> args.stream().anyMatch(each -> (boolean)eval(each)));
        addSpecialForm(Arity.min(1), "and", args -> args.stream().allMatch(each -> (boolean)eval(each)));
        addSpecialForm(Arity.between(2, 3), "if", args -> {
            if ((boolean)eval(args.get(0))) {
                return eval(args.get(1));
            } else if (args.size() == 3) {
                return eval(args.get(2));
            }
            return null;
        });
        addFunction("not", Arity.UNARY, args -> !(boolean)args.get(0));
        addFunction("=", Arity.BINARY, args -> equalTo(args.get(0), args.get(1)));
        addFunction("!=", Arity.BINARY, args -> !equalTo(args.get(0), args.get(1)));
        addFunction("<", Arity.BINARY, args ->  ((Number)args.get(0)).doubleValue() < ((Number)args.get(1)).doubleValue());
        addFunction("<=", Arity.BINARY, args ->  ((Number)args.get(0)).doubleValue() <= ((Number)args.get(1)).doubleValue());
        addFunction(">", Arity.BINARY, args ->  ((Number)args.get(0)).doubleValue() > ((Number)args.get(1)).doubleValue());
        addFunction(">=", Arity.BINARY, args ->  ((Number)args.get(0)).doubleValue() >= ((Number)args.get(1)).doubleValue());
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
        addFunction("concat", Arity.min(1), args -> args.stream().map(Object::toString).collect(Collectors.joining()));
        addFunction("substr", Arity.min(2), args ->
                args.size() == 2
                    ? ((String)args.get(0)).substring(((Number)args.get(1)).intValue())
                    : ((String)args.get(0)).substring(((Number)args.get(1)).intValue(), ((Number)args.get(2)).intValue())
        );
        addFunction("strlen", Arity.UNARY, args -> ((String)args.get(0)).length());
        addFunction("starts-with", Arity.BINARY, args -> ((String)args.get(0)).startsWith((String)args.get(1)));
        addFunction("ends-with", Arity.BINARY, args -> ((String)args.get(0)).endsWith((String)args.get(1)));
        addFunction("contains", Arity.BINARY, args -> ((String)args.get(1)).contains((String)args.get(0)));
        addFunction("index-of", Arity.BINARY, args -> ((String)args.get(1)).indexOf((String)args.get(0)));
        addFunction("regex-template", Arity.between(3, 5), args -> {
            String str = (String) args.get(0);
            String regex = (String) args.get(1);
            String template = (String) args.get(2);
            if (args.size() == 3) {
                return new RegexTemplate(regex, template, null, false).apply(str);
            } else {
                boolean useOriginalOnLookupFailure = args.size() >= 5 && (boolean) args.get(4);
                return new RegexTemplate(regex, template, (Map)args.get(3), useOriginalOnLookupFailure).apply(str);
            }
        });
        addFunction("print", Arity.min(1), args -> { // for debugging
            args.forEach(arg -> LOG.info(arg == null ? "null" : arg.toString()));
            return false;
        });
        addFunction("hash", Arity.even(), args -> { // create a hashmap, number of arguments must be an even number
            Map<Object,Object> map = new HashMap<>();
            for (int i = 0; i < args.size() -1; i+=2) {
                map.put(args.get(i), args.get(i +1));
            }
            return map;
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

    private void addSpecialForm(Arity arity, String name, SpecialForm form) {
        specialForms.put(name, parameters -> {
            arity.check(name, parameters);
            return form.call(parameters);
        });
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
