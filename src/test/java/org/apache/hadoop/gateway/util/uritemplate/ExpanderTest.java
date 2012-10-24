/**
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
package org.apache.hadoop.gateway.util.uritemplate;

import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ExpanderTest {

  @Ignore( "TODO" )
  @Test
  public void testUrlEncoding() {
  }

  @Test
  public void testBasicExpansion() throws Exception {
    Expander expander = new Expander();
    Parser parser = new Parser();
    Template template;
    Params params;
    URI expanded;

    template = parser.parse( "" );
    params = new Params();
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "" ) ) ;

    template = parser.parse( "/" );
    params = new Params();
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/" ) ) ;

    template = parser.parse( "{path-name}" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-value" ) ) ;

    template = parser.parse( "/{path-name}" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-value" ) ) ;

    template = parser.parse( "{path-name}/" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-value/" ) ) ;

    template = parser.parse( "/{path-name}/" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-value/" ) ) ;

    template = parser.parse( "path-name" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-name" ) ) ;

    template = parser.parse( "/path-name" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-name" ) ) ;

    template = parser.parse( "path-name/" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-name/" ) ) ;

    template = parser.parse( "/path-name/" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-name/" ) ) ;

    template = parser.parse( "?" );
    params = new Params();
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?" ) ) ;

    template = parser.parse( "?query-name={param-name}" );
    params = new Params();
    params.addValue( "param-name", "param-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name=param-value" ) ) ;

    template = parser.parse( "?query-name-1={param-name-1}&query-name-2={param-name-2}" );
    params = new Params();
    params.addValue( "param-name-1", "param-value-1" );
    params.addValue( "param-name-2", "param-value-2" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name-1=param-value-1&query-name-2=param-value-2" ) ) ;

    template = parser.parse( "?query-name=param-value" );
    params = new Params();
    params.addValue( "param-name", "other-param-value" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name=param-value" ) ) ;

    template = parser.parse( "?query-name-1=param-value-1&query-name-2=param-value-2" );
    params = new Params();
    params.addValue( "param-name-1", "other-param-value-1" );
    params.addValue( "param-name-2", "other-param-value-2" );
    expanded = expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name-1=param-value-1&query-name-2=param-value-2" ) ) ;
  }

}
