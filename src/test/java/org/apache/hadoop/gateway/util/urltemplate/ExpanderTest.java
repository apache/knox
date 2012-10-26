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
package org.apache.hadoop.gateway.util.urltemplate;

import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ExpanderTest {

  @Ignore( "TODO" )
  @Test
  public void testUrlEncoding() {
  }

  @Test
  public void testCompleteUrl() throws URISyntaxException {
    String text;
    Template template;
    Params params;
    URI expanded;

    text = "foo://username:password@example.com:8042/over/there/index.dtb?type=animal&name=narwhal#nose";
    template = Parser.parse( text );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( text ) ) ;
  }

  @Test
  public void testBasicExpansion() throws Exception {
    Template template;
    Params params;
    URI expanded;

    template = Parser.parse( "" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "" ) ) ;

    template = Parser.parse( "/" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/" ) ) ;

    template = Parser.parse( "{path-name}" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-value" ) ) ;

    template = Parser.parse( "/{path-name}" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-value" ) ) ;

    template = Parser.parse( "{path-name}/" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-value/" ) ) ;

    template = Parser.parse( "/{path-name}/" );
    params = new Params();
    params.addValue( "path-name", "path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-value/" ) ) ;

    template = Parser.parse( "path-name" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-name" ) ) ;

    template = Parser.parse( "/path-name" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-name" ) ) ;

    template = Parser.parse( "path-name/" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "path-name/" ) ) ;

    template = Parser.parse( "/path-name/" );
    params = new Params();
    params.addValue( "path-name", "other-path-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "/path-name/" ) ) ;

    template = Parser.parse( "?" );
    params = new Params();
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?" ) ) ;

    template = Parser.parse( "?query-name={param-name}" );
    params = new Params();
    params.addValue( "param-name", "param-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name=param-value" ) ) ;

    template = Parser.parse( "?query-name-1={param-name-1}&query-name-2={param-name-2}" );
    params = new Params();
    params.addValue( "param-name-1", "param-value-1" );
    params.addValue( "param-name-2", "param-value-2" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name-1=param-value-1&query-name-2=param-value-2" ) ) ;

    template = Parser.parse( "?query-name=param-value" );
    params = new Params();
    params.addValue( "param-name", "other-param-value" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name=param-value" ) ) ;

    template = Parser.parse( "?query-name-1=param-value-1&query-name-2=param-value-2" );
    params = new Params();
    params.addValue( "param-name-1", "other-param-value-1" );
    params.addValue( "param-name-2", "other-param-value-2" );
    expanded = Expander.expand( template, params );
    assertThat( expanded.toString(), equalTo( "?query-name-1=param-value-1&query-name-2=param-value-2" ) ) ;
  }

}
