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

import java.util.HashMap;

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
    public void testLessThan() {
        assertTrue((boolean)eval("(< 1 10)"));
        assertTrue((boolean)eval("(< 1.21 1.32)"));
        assertFalse((boolean)eval("(< 1 1)"));
        assertFalse((boolean)eval("(< 10 1)"));
        assertFalse((boolean)eval("(< 1.31 1.30)"));
    }

    @Test
    public void testLessEqThan() {
        assertTrue((boolean)eval("(<= 1 10)"));
        assertTrue((boolean)eval("(<= 1.21 1.32)"));
        assertTrue((boolean)eval("(<= 1 1)"));
        assertFalse((boolean)eval("(<= 10 1)"));
        assertFalse((boolean)eval("(<= 1.31 1.30)"));
    }

    @Test
    public void testGreaterThan() {
        assertFalse((boolean)eval("(> 1 10)"));
        assertFalse((boolean)eval("(> 1.21 1.32)"));
        assertFalse((boolean)eval("(> 1 1)"));
        assertTrue((boolean)eval("(> 10 1)"));
        assertTrue((boolean)eval("(> 1.31 1.30)"));
    }

    @Test
    public void testGreaterEqThan() {
        assertFalse((boolean)eval("(>= 1 10)"));
        assertFalse((boolean)eval("(>= 1.21 1.32)"));
        assertTrue((boolean)eval("(>= 1 1)"));
        assertTrue((boolean)eval("(>= 10 1)"));
        assertTrue((boolean)eval("(>= 1.31 1.30)"));
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

    @Test
    public void testIf() {
        assertNull(eval("(if false (invalid-expression))"));
        assertEquals("testStr", eval("(if true 'testStr')"));
    }

    @Test
    public void testIfElse() {
        assertEquals("apple", eval("(if false (invalid-expression) (lowercase 'APPLE'))"));
        assertEquals("orange", eval("(if true (lowercase 'ORANGE') (invalid-expression) )"));
    }

    @Test(expected = ArityException.class)
    public void testIfWrongNumberOfArgs() {
        eval("(if true 1 2 3)");
    }

    @Test
    public void testConcat() {
        assertEquals("asdf", eval("(concat 'asdf')"));
        assertEquals("asdfjkl", eval("(concat 'asdf' 'jkl')"));
        assertEquals("asdfjklqwerty", eval("(concat 'asdf' 'jkl' 'qwerty')"));
        assertEquals("orange APPLE", eval("(concat (lowercase 'ORANGE') ' ' (uppercase 'apple'))"));
        assertEquals("123", eval("(concat 1 2 3)"));
    }

    @Test
    public void testSubStr1() {
        assertEquals("123456789", eval("(substr '123456789' 0)"));
        assertEquals("456789", eval("(substr '123456789' 3)"));
        assertEquals("9", eval("(substr '123456789' 8)"));
        assertEquals("", eval("(substr '123456789' 9)"));
    }

    @Test
    public void testSubStr2() {
        assertEquals("123456789", eval("(substr '123456789' 0 9)"));
        assertEquals("", eval("(substr '123456789' 9 9)"));
        assertEquals("123", eval("(substr '123456789' 0 3)"));
        assertEquals("3", eval("(substr '123456789' 2 3)"));
        assertEquals("345", eval("(substr '123456789' 2 5)"));
    }

    @Test
    public void testStrLen() {
        assertEquals(0, eval("(strlen '')"));
        assertEquals(5, eval("(strlen (uppercase 'apple'))"));
    }

    @Test
    public void testStartsWith() {
        assertTrue((boolean)eval("(starts-with '' '')"));
        assertTrue((boolean)eval("(starts-with 'apple' '')"));
        assertTrue((boolean)eval("(starts-with 'apple' 'ap')"));
        assertTrue((boolean)eval("(starts-with 'apple' 'app')"));
        assertTrue((boolean)eval("(starts-with 'apple' 'appl')"));
        assertTrue((boolean)eval("(starts-with 'apple' 'apple')"));
        assertFalse((boolean)eval("(starts-with 'apple' 'applex')"));
        assertFalse((boolean)eval("(starts-with '' 'a')"));
    }

    @Test
    public void testEndsWith() {
        assertTrue((boolean)eval("(ends-with '' '')"));
        assertTrue((boolean)eval("(ends-with 'apple' '')"));
        assertTrue((boolean)eval("(ends-with 'apple' 'e')"));
        assertTrue((boolean)eval("(ends-with 'apple' 'ple')"));
        assertTrue((boolean)eval("(ends-with 'apple' 'apple' )"));
        assertFalse((boolean)eval("(ends-with 'apple' 'xapple' )"));
        assertFalse((boolean)eval("(ends-with '' 'a')"));
    }

    @Test
    public void testStrIn() {
        assertTrue((boolean)eval("(contains 'ppl' 'apple')"));
        assertTrue((boolean)eval("(contains '' 'apple')"));
        assertTrue((boolean)eval("(contains 'a' 'apple')"));
        assertFalse((boolean)eval("(contains 'x' 'apple')"));
    }

    @Test
    public void testStrIndex() {
        assertEquals(1, eval("(index-of 'ppl' 'apple')"));
        assertEquals(-1, eval("(index-of 'xx' 'apple')"));
    }

    @Test
    public void testRegexpGroup() {
        assertEquals("user.1", eval("(regex-template 'prefix_user-1_suffix' 'prefix_(\\w+)\\-(\\d)_suffix' '{1}.{2}')"));
        assertEquals("123", eval("(regex-template 'usr123' 'usr(\\d+)' '{1}')"));
        assertEquals("usr123", eval("(regex-template 'usr123' 'usr\\d+' '{0}')"));
        assertEquals("{0}", eval("(regex-template 'admin' '\\d+' '{0}')"));
    }

    @Test
    public void testRegexpGroupWithLookup() {
        // See RegexTemplateTest
        String script = "(regex-template 'nobody@us.imaginary.tld' '(.*)@(.*?)\\..*' '{1}_{[2]}' (hash   'us' 'USA'   'ca' 'CANADA'))";
        assertEquals("nobody_USA", eval(script));

        script = "(regex-template 'member@us.apache.org' '(.*)@(.*?)\\..*' 'prefix_{1}:{[2]}_suffix' (hash   'us' 'USA'   'ca' 'CANADA'))";
        assertEquals("prefix_member:USA_suffix", eval(script));

        script = "(regex-template 'member@ca.apache.org' '(.*)@(.*?)\\..*' 'prefix_{1}:{[2]}_suffix' (hash   'us' 'USA'   'ca' 'CANADA'))";
        assertEquals("prefix_member:CANADA_suffix", eval(script));

        script = "(regex-template 'member@uk.apache.org' '(.*)@(.*?)\\..*' 'prefix_{1}:{[2]}_suffix' (hash   'us' 'USA'   'ca' 'CANADA'))";
        assertEquals("prefix_member:_suffix", eval(script));

        script = "(regex-template 'member@uk.apache.org' '(.*)@(.*?)\\..*' 'prefix_{1}:{[2]}_suffix' (hash   'us' 'USA'   'ca' 'CANADA') true)";
        assertEquals("prefix_member:uk_suffix", eval(script));
    }

    @Test
    public void testHashMaps() {
        HashMap<Object, Object> expected = new HashMap<>();
        assertEquals(expected, eval("(hash)"));
        expected.put(1L , 2L);
        assertEquals(expected, eval("(hash   1 2)"));
        expected.put("a", "b");
        assertEquals(expected, eval("(hash   1 2   'a' 'b')"));
        expected.clear();
        expected.put("apple123", true);
        assertEquals(expected, eval("(hash  (lowercase (concat 'Apple' '123'))   (and (< 10 12) (> 10 1)))"));
    }

    @Test(expected = ArityException.class)
    public void testHashMapInvalid() {
        eval("(hash 'key1' 'value1' 'key2')");
    }

    private Object eval(String script) {
        return interpreter.eval(parser.parse(script));
    }
}