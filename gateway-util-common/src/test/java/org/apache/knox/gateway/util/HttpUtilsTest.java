/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.util;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class HttpUtilsTest {

  @Test
  public void testParseQueryString_BugKnox599() throws Exception {

    Map<String,List<String>> map;

    map = HttpUtils.splitQuery( null );
    assertThat( map, notNullValue() );
    assertThat( map.isEmpty(), is( true ) );

    map = HttpUtils.splitQuery( "" );
    assertThat( map, notNullValue() );
    assertThat( map.isEmpty(), is( true ) );

    map = HttpUtils.splitQuery( "test-name=test-value" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).size(), is( 1 ) );
    assertThat( map.get( "test-name" ).get(0), is( "test-value" ) );

    map = HttpUtils.splitQuery( "test-name-one=test-value-one&test-name-two=two=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 2 ) );
    assertThat( map.containsKey( "test-name-one" ), is( true ) );
    assertThat( map.get( "test-name-one" ).size(), is( 1 ) );
    assertThat( map.get( "test-name-one" ).get(0), is( "test-value-one" ) );
    assertThat( map.containsKey( "test-name-two" ), is( true ) );
    assertThat( map.get( "test-name-two" ).size(), is( 1 ) );
    assertThat( map.get( "test-name-two" ).get(0), is( "two=test-value-two" ) );

    map = HttpUtils.splitQuery( "test-name-one=test-value-one?test-name-two=two=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name-two" ), is( false ) );
    assertThat( map.get( "test-name-one" ).size(), is( 1 ) );
    assertThat( map.get( "test-name-one" ).get(0), is( "test-value-one?test-name-two=two=test-value-two" ) );

    map = HttpUtils.splitQuery( "test-name-one=test-value-one;test-name-two=two=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name-one" ), is( true ) );
    assertThat( map.get( "test-name-one" ).size(), is( 1 ) );
    assertThat( map.get( "test-name-one" ).get(0), is( "test-value-one;test-name-two=two=test-value-two" ) );
    assertThat( map.containsKey( "test-name-two" ), is( false ) );

    map = HttpUtils.splitQuery( "test-name=test-value-one?test-name=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).size(), is( 1 ) );
    assertThat( map.get( "test-name" ).get(0), is( "test-value-one?test-name=test-value-two" ) );

    map = HttpUtils.splitQuery( "test-name=test-value-one&test-name=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).size(), is( 2 ) );
    assertThat( map.get( "test-name" ).get(0), is( "test-value-one" ) );
    assertThat( map.get( "test-name" ).get(1), is( "test-value-two" ) );

    // https://www.w3.org/TR/2014/REC-html5-20141028/forms.html#url-encoded-form-data
    // 4.10.22.6 URL-encoded form data
    // To decode application/x-www-form-urlencoded payloads, the following algorithm should be used
    // This algorithm uses as inputs the payload itself, payload, consisting of a Unicode string
    // using only characters in the range U+0000 to U+007F; a default character encoding encoding;
    // and optionally an isindex flag indicating that the payload is to be processed as if it had
    // been generated for a form containing an isindex control. The output of this algorithm is a
    // sorted list of name-value pairs. If the isindex flag is set and the first control really was
    // an isindex control, then the first name-value pair will have as its name the empty string.
    // Which default character encoding to use can only be determined on a case-by-case basis, but
    // generally the best character encoding to use as a default is the one that was used to encode
    // the page on which the form used to create the payload was itself found. In the absence of a
    // better default, UTF-8 is suggested.
    // The isindex flag is for legacy use only. Forms in conforming HTML documents will not generate
    // payloads that need to be decoded with this flag set.
    //
    // Let strings be the result of strictly splitting the string payload on U+0026 AMPERSAND
    // characters (&).
    map = HttpUtils.splitQuery( "test-name=test-value-one;test-name=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).size(), is( 1 ) );
    assertThat( map.get( "test-name" ).get(0), is( "test-value-one;test-name=test-value-two" ) );

    map = HttpUtils.splitQuery( "test-name=" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).size(), is( 1 ) );
    assertThat( map.get( "test-name" ).get(0), is( "" ) );

    map = HttpUtils.splitQuery( "test-name" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).size(), is( 1 ) );
    assertThat( map.get( "test-name" ).get(0), is("") );

    map = HttpUtils.splitQuery( "=test-value" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "=test-value" ), is( true ) );
    assertThat( map.get( "=test-value" ).size(), is( 1 ) );
    assertThat( map.get( "=test-value" ).get(0), is( "" ) );

    map = HttpUtils.splitQuery( "=" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "=" ), is( true ) );
    assertThat( map.get( "=" ).size(), is( 1 ) );
    assertThat( map.get( "=" ).get(0), is( "" ) );

    map = HttpUtils.splitQuery( "==" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "==" ), is( true ) );
    assertThat( map.get( "==" ).size(), is( 1 ) );
    assertThat( map.get( "==" ).get(0), is( "" ) );

    map = HttpUtils.splitQuery( "&" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 0 ) );
    assertThat( map.containsKey( null ), is( false ) );

    map = HttpUtils.splitQuery( "?" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "?" ), is( true ) );
    assertThat( map.get( "?" ).size(), is( 1 ) );
    assertThat( map.get( "?" ).get(0), is("") );

    map = HttpUtils.splitQuery( ";" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( ";" ), is( true ) );
    assertThat( map.get( ";" ).size(), is( 1 ) );
    assertThat( map.get( ";" ).get(0), is("") );

    map = HttpUtils.splitQuery( "&=" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 2 ) );
    assertThat( map.containsKey( "" ), is( true ) );
    assertThat( map.get( "" ).size(), is( 1 ) );
    assertThat( map.get( "" ).get(0), is( "" ) );
    assertThat( map.containsKey( "=" ), is( true ) );
    assertThat( map.get( "=" ).size(), is( 1 ) );
    assertThat( map.get( "=" ).get(0), is("") );

    map = HttpUtils.splitQuery( "=&" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "=" ), is( true ) );
    assertThat( map.get( "=" ).size(), is( 1 ) );
    assertThat( map.get( "=" ).get(0), is( "" ) );

    map = HttpUtils.splitQuery( "&&" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 0 ) );

    map = HttpUtils.splitQuery( "test+name=test+value" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test name" ), is( true ) );
    assertThat( map.get( "test name" ).size(), is( 1 ) );
    assertThat( map.get( "test name" ).get(0), is( "test value" ) );

    map = HttpUtils.splitQuery("test%26name=test%3Dvalue");
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test&name" ), is( true ) );
    assertThat( map.get( "test&name" ).size(), is( 1 ) );
    assertThat( map.get( "test&name" ).get(0), is( "test=value" ) );

    map = HttpUtils.splitQuery( "qry=Hadoop:service=NameNode,name=NameNodeInfo" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "qry" ), is( true ) );
    assertThat( map.get( "qry" ).size(), is( 1 ) );
    assertThat( map.get( "qry" ).get(0), is( "Hadoop:service=NameNode,name=NameNodeInfo" ) );
  }

}
