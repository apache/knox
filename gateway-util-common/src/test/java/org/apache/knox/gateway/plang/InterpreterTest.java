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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InterpreterTest {
    Interpreter interpreter = new Interpreter();
    Parser parser = new Parser();

    @Test
    public void testEmpty() {
        assertNull(eval(null));
        assertNull(eval(""));
        assertNull(eval(" "));
    }

    @Test
    public void testBooleans() {
        assertTrue((boolean)eval("true"));
        assertFalse((boolean)eval("false"));
    }

    @Test
    public void testEq() {
        assertTrue((boolean)eval("(= true true)"));
        assertTrue((boolean)eval("(= false false)"));
        assertFalse((boolean)eval("(= true false)"));
        assertFalse((boolean)eval("(= false true)"));
        assertFalse((boolean)eval("(= 'apple' 'orange')"));
        assertTrue((boolean)eval("(= 'apple' 'apple')"));
        assertTrue((boolean)eval("(= 0 0)"));
        assertTrue((boolean)eval("(= -10.33242 -10.33242)"));
    }

    @Test
    public void testNotEq() {
        assertFalse((boolean)eval("(!= true true)"));
        assertFalse((boolean)eval("(!= false false)"));
        assertTrue((boolean)eval("(!= true false)"));
        assertTrue((boolean)eval("(!= false true)"));
        assertTrue((boolean)eval("(!= 'apple' 'orange')"));
        assertFalse((boolean)eval("(!= 'apple' 'apple')"));
        assertFalse((boolean)eval("(!= 0 0)"));
        assertFalse((boolean)eval("(!= -10.33242 -10.33242)"));
    }

    @Test
    public void testCmpDifferentTypes() {
        assertTrue((boolean)eval("(= 1.0 1)"));
        assertFalse((boolean)eval("(!= 1.0 1)"));
        assertTrue((boolean)eval("(!= 1.0 2)"));
        assertFalse((boolean)eval("(= 1.0 2)"));
        assertTrue((boolean)eval("(!= '12' 12)"));
        assertFalse((boolean)eval("(= 12 '12')"));
    }

    @Test
    public void testOr() {
        assertTrue((boolean)eval("(or true true)"));
        assertTrue((boolean)eval("(or true false)"));
        assertTrue((boolean)eval("(or false true)"));
        assertFalse((boolean)eval("(or false false)"));
        assertTrue((boolean)eval("(or false false false true false)"));
        assertFalse((boolean)eval("(or false false false false false)"));
    }

    @Test
    public void testAnd() {
        assertTrue((boolean)eval("(and true true)"));
        assertFalse((boolean)eval("(and false false)"));
        assertFalse((boolean)eval("(and true false)"));
        assertFalse((boolean)eval("(and false true)"));
        assertFalse((boolean)eval("(and true true true false)"));
        assertTrue((boolean)eval("(and true true true true)"));
    }

    @Test
    public void testNot() {
        assertFalse((boolean)eval("(not true)"));
        assertTrue((boolean)eval("(not false)"));
        assertFalse((boolean)eval("(not (not false))"));
        assertTrue((boolean)eval("(not (not true))"));
    }

    @Test
    public void testComplex() {
        assertTrue((boolean)eval("(and (not false) (or (not (or (not true) (not false) )) true))"));
    }

    @Test(expected = TypeException.class)
    public void testTypeError() {
        eval("(size 12)");
    }

    @Test
    public void testStrings() {
        assertEquals("", eval("''"));
        assertEquals(" a b c ", eval("' a b c '"));
    }

    @Test
    public void testMatchString() {
        assertTrue((boolean)eval("(match 'user1' 'user\\d+')"));
        assertTrue((boolean)eval("(match 'user12' 'user\\d+')"));
        assertFalse((boolean)eval("(match 'user12d' 'user\\d+')"));
        assertFalse((boolean)eval("(match 'user' 'user\\d+')"));
        assertFalse((boolean)eval("(match '12' 'user\\d+')"));
        assertTrue((boolean)eval("(match 'hive' 'hive|joe')"));
        assertTrue((boolean)eval("(match 'joe' 'hive|joe')"));
        assertFalse((boolean)eval("(match 'tom' 'hive|joe')"));
        assertFalse((boolean)eval("(match 'hive1' 'hive|joe')"));
        assertFalse((boolean)eval("(match '0joe' 'hive|joe')"));
    }

    @Test
    public void testMatchList() {
        interpreter.addConstant("groups", singletonList("grp1"));
        assertTrue((boolean)eval("(match groups 'grp\\d+')"));
        interpreter.addConstant("groups", singletonList("grp12"));
        assertTrue((boolean)eval("(match groups 'grp\\d+')"));
        interpreter.addConstant("groups", singletonList("grp12d"));
        assertFalse((boolean)eval("(match groups 'grp\\d+')"));
        interpreter.addConstant("groups", singletonList("grp"));
        assertFalse((boolean)eval("(match groups 'grp\\d+')"));
        interpreter.addConstant("groups", singletonList("12"));
        assertFalse((boolean)eval("(match groups 'grp\\d+')"));
        interpreter.addConstant("groups", asList("12", "grp12"));
        assertTrue((boolean)eval("(match groups 'grp\\d+')"));
    }

    @Test
    public void testFuncEmpty() {
        interpreter.addConstant("groups", emptyList());
        assertTrue((boolean)eval("(empty groups)"));
        interpreter.addConstant("groups", singletonList("grp1"));
        assertFalse((boolean)eval("(empty groups)"));
    }

    @Test
    public void testLowerUpper() {
        assertEquals("apple", eval("(lowercase 'APPLE')"));
        assertEquals("APPLE", eval("(uppercase 'apple')"));
    }

    @Test
    public void testShortCircuitConditionals() {
        assertTrue((boolean)eval("(or true (invalid-expression 1 2 3))"));
        assertFalse((boolean)eval("(and false (invalid-expression 1 2 3))"));
    }

    private Object eval(String script) {
        return interpreter.eval(parser.parse(script));
    }
}