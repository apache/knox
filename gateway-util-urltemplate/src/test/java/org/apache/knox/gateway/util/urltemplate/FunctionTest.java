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
package org.apache.knox.gateway.util.urltemplate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class FunctionTest {

  @Test
  public void testParse() throws Exception {
    Function function;

    function = new Function( null );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( " " );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "variable" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), is( "variable" ) );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( " variable " );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), is( "variable" ) );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "(variable)" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), is( "variable" ) );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( " ( variable ) " );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), is( "variable" ) );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "[literal]" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), contains( "literal" ) );

    function = new Function( " [ literal ] " );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), contains( "literal" ) );

    function = new Function( "()" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "[]" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "$" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "$()" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "$[]" );
    assertThat( function.getFunctionName(), nullValue() );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "$function" );
    assertThat( function.getFunctionName(), is( "function") );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "$function(variable)" );
    assertThat( function.getFunctionName(), is( "function" ) );
    assertThat( function.getParameterName(), is( "variable" ) );
    assertThat( function.getParameterValue(), nullValue() );

    function = new Function( "$function[literal]" );
    assertThat( function.getFunctionName(), is( "function") );
    assertThat( function.getParameterName(), nullValue() );
    assertThat( function.getParameterValue(), contains( "literal" ) );
  }

  @Test
  public void testEvaluate() throws Exception {
    TestResolver resolver = new TestResolver();
    TestEvaluator evaluator = new TestEvaluator();
    List<String> values;

    values = Function.evaluate( "$test-func(test-variable)", resolver, evaluator );
    assertThat( values, contains( "<test-func:resolve(test-variable)>" ) );

    values = Function.evaluate( "$test-func[test-literal]", resolver, evaluator );
    assertThat( values, contains( "<test-func:test-literal>" ) );

    values = Function.evaluate( "test-variable", resolver, evaluator );
    assertThat( values, contains( "resolve(test-variable)" ) );

    values = Function.evaluate( "(test-variable)", resolver, evaluator );
    assertThat( values, contains( "resolve(test-variable)" ) );

    values = Function.evaluate( "[test-literal]", resolver, evaluator );
    assertThat( values, contains( "test-literal" ) );
  }

  class TestResolver implements Resolver {

    @Override
    public List<String> resolve( String name ) {
      return Arrays.asList( "resolve(" + name + ")" );
    }

  }

  class TestEvaluator implements Evaluator {

    @Override
    public List<String> evaluate( String function, List<String> parameters ) {
      List<String> result = new ArrayList<>( parameters.size() );
      for( String parameter : parameters ) {
        result.add( "<" + function + ":" + parameter + ">" );
      }
      return result;
    }

  }

}
