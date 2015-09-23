/**
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
package org.apache.hadoop.gateway.util;

import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class HttpUtilsTest {

  @Test
  public void testParseQueryString_BugKnox599() {

    Map<String,String[]> map;

    map = HttpUtils.parseQueryString( null );
    assertThat( map, notNullValue() );
    assertThat( map.isEmpty(), is( true ) );

    map = HttpUtils.parseQueryString( "" );
    assertThat( map, notNullValue() );
    assertThat( map.isEmpty(), is( true ) );

    map = HttpUtils.parseQueryString( "test-name=test-value" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).length, is( 1 ) );
    assertThat( map.get( "test-name" )[0], is( "test-value" ) );

    map = HttpUtils.parseQueryString( "test-name-one=test-value-one&test-name-two=two=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 2 ) );
    assertThat( map.containsKey( "test-name-one" ), is( true ) );
    assertThat( map.get( "test-name-one" ).length, is( 1 ) );
    assertThat( map.get( "test-name-one" )[0], is( "test-value-one" ) );
    assertThat( map.containsKey( "test-name-two" ), is( true ) );
    assertThat( map.get( "test-name-two" ).length, is( 1 ) );
    assertThat( map.get( "test-name-two" )[0], is( "test-value-two" ) );

    map = HttpUtils.parseQueryString( "test-name-one=test-value-one?test-name-two=two=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 2 ) );
    assertThat( map.containsKey( "test-name-one" ), is( true ) );
    assertThat( map.get( "test-name-one" ).length, is( 1 ) );
    assertThat( map.get( "test-name-one" )[0], is( "test-value-one" ) );
    assertThat( map.containsKey( "test-name-two" ), is( true ) );
    assertThat( map.get( "test-name-two" ).length, is( 1 ) );
    assertThat( map.get( "test-name-two" )[0], is( "test-value-two" ) );

    map = HttpUtils.parseQueryString( "test-name-one=test-value-one;test-name-two=two=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 2 ) );
    assertThat( map.containsKey( "test-name-one" ), is( true ) );
    assertThat( map.get( "test-name-one" ).length, is( 1 ) );
    assertThat( map.get( "test-name-one" )[0], is( "test-value-one" ) );
    assertThat( map.containsKey( "test-name-two" ), is( true ) );
    assertThat( map.get( "test-name-two" ).length, is( 1 ) );
    assertThat( map.get( "test-name-two" )[0], is( "test-value-two" ) );

    map = HttpUtils.parseQueryString( "test-name=test-value-one?test-name=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).length, is( 2 ) );
    assertThat( map.get( "test-name" )[0], is( "test-value-one" ) );
    assertThat( map.get( "test-name" )[1], is( "test-value-two" ) );

    map = HttpUtils.parseQueryString( "test-name=test-value-one&test-name=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).length, is( 2 ) );
    assertThat( map.get( "test-name" )[0], is( "test-value-one" ) );
    assertThat( map.get( "test-name" )[1], is( "test-value-two" ) );

    map = HttpUtils.parseQueryString( "test-name=test-value-one;test-name=test-value-two" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).length, is( 2 ) );
    assertThat( map.get( "test-name" )[0], is( "test-value-one" ) );
    assertThat( map.get( "test-name" )[1], is( "test-value-two" ) );

    map = HttpUtils.parseQueryString( "test-name=" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).length, is( 1 ) );
    assertThat( map.get( "test-name" )[0], is( "" ) );

    map = HttpUtils.parseQueryString( "test-name" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test-name" ), is( true ) );
    assertThat( map.get( "test-name" ).length, is( 1 ) );
    assertThat( map.get( "test-name" )[0], nullValue() );

    map = HttpUtils.parseQueryString( "=test-value" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "" ), is( true ) );
    assertThat( map.get( "" ).length, is( 1 ) );
    assertThat( map.get( "" )[0], is( "test-value" ) );

    map = HttpUtils.parseQueryString( "=" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "" ), is( true ) );
    assertThat( map.get( "" ).length, is( 1 ) );
    assertThat( map.get( "" )[0], is( "" ) );

    map = HttpUtils.parseQueryString( "==" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "" ), is( true ) );
    assertThat( map.get( "" ).length, is( 2 ) );
    assertThat( map.get( "" )[0], is( "" ) );
    assertThat( map.get( "" )[1], is( "" ) );

    map = HttpUtils.parseQueryString( "&" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( null ), is( true ) );
    assertThat( map.get( null ).length, is( 1 ) );
    assertThat( map.get( null )[0], nullValue() );

    map = HttpUtils.parseQueryString( "?" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( null ), is( true ) );
    assertThat( map.get( null ).length, is( 1 ) );
    assertThat( map.get( null )[0], nullValue() );

    map = HttpUtils.parseQueryString( ";" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( null ), is( true ) );
    assertThat( map.get( null ).length, is( 1 ) );
    assertThat( map.get( null )[0], nullValue() );

    map = HttpUtils.parseQueryString( "&=" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 2 ) );
    assertThat( map.containsKey( "" ), is( true ) );
    assertThat( map.get( "" ).length, is( 1 ) );
    assertThat( map.get( "" )[0], is( "" ) );
    assertThat( map.containsKey( null ), is( true ) );
    assertThat( map.get( null ).length, is( 1 ) );
    assertThat( map.get( null )[0], nullValue() );

    map = HttpUtils.parseQueryString( "=&" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "" ), is( true ) );
    assertThat( map.get( "" ).length, is( 1 ) );
    assertThat( map.get( "" )[0], is( "" ) );

    map = HttpUtils.parseQueryString( "&&" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( null ), is( true ) );
    assertThat( map.get( null ).length, is( 2 ) );
    assertThat( map.get( null )[0], nullValue() );
    assertThat( map.get( null )[1], nullValue() );

    map = HttpUtils.parseQueryString( "test+name=test+value" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test name" ), is( true ) );
    assertThat( map.get( "test name" ).length, is( 1 ) );
    assertThat( map.get( "test name" )[0], is( "test value" ) );

    map = HttpUtils.parseQueryString( "test%26name=test%3Dvalue" );
    assertThat( map, notNullValue() );
    assertThat( map.size(), is( 1 ) );
    assertThat( map.containsKey( "test&name" ), is( true ) );
    assertThat( map.get( "test&name" ).length, is( 1 ) );
    assertThat( map.get( "test&name" )[0], is( "test=value" ) );

  }

}
